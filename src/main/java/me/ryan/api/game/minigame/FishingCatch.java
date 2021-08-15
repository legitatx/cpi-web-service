package me.legit.api.game.minigame;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.models.reward.Reward;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public class FishingCatch implements Handler {

    private JsonParser parser;

    public FishingCatch() {
        this.parser = new JsonParser();
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");

        JsonObject object = new JsonParser().parse(ctx.body()).getAsJsonObject();

        JsonObject fishingCastResult = object.getAsJsonObject("fishingCastResult");

        long goodUntil = fishingCastResult.get("goodUntil").getAsLong();
        String signature = fishingCastResult.get("signature").getAsString();
        String swid = fishingCastResult.get("swid").getAsString();

        String winningRewardName = object.get("winningRewardName").getAsString();

        String encodedToken = ctx.header("Authorization").split("Basic ")[1].split(", GAE")[0];
        String token = new String(Base64.getDecoder().decode(encodedToken)).split(":")[0];

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(token, checkRevoked).get();
        String uid = decodedToken.getUid();

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (FishingCatch)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            if (!verifyResponse(decodedToken, swid, goodUntil, signature)) {
                Sentry.capture("(Failed to verify response) (FishingCatch)");
                ctx.status(400);
                ctx.result("{\"message\":\"Invalid URI parameter(s)\",\"code\":10}");
            } else {
                JSONObject finalResponse = new JSONObject();

                Reward winningReward = APICore.getMinigameService().getFishingReward(winningRewardName);
                APICore.getRewardManager().saveReward(document, winningReward, finalResponse);

                JSONObject wsEvents = finalResponse.optJSONObject("wsEvents");

                RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
                RedisFuture<String> requestJoinRoomData = redis.get(decodedToken.getName() + "-joinRoomData");
                long sessionId = parser.parse(requestJoinRoomData.get()).getAsJsonObject().get("data").getAsJsonObject().get("sessionId").getAsLong();

                ctx.result(generateCatchResponse(wsEvents, winningReward, winningRewardName, sessionId, decodedToken, document).toString());
            }
        } else {
            APICore.getLogger().severe(uid + " - " + "(document.exists() == false) (FishingCatch)");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("(document.exists() == false) (FishingCatch)");

            ctx.status(500);
        }
    }

    private JSONObject generateCatchResponse(JSONObject wsEventsLvl, Reward reward, String winningRewardName, long sessionId, FirebaseToken token, DocumentSnapshot snapshot) throws Exception {
        JSONObject finalResponse = new JSONObject();

        JSONArray wsEventsArray = new JSONArray();

        LocalDateTime time = LocalDateTime.now().plusMinutes(5);
        ZoneId zoneId = ZoneId.systemDefault();
        long epoch = time.atZone(zoneId).toInstant().toEpochMilli();

        /*
        "data": {
			"type": 1,
			"details": "Medium Fish 2"
		},
         */

        //TODO persist fishing later when work begins on daily challenges

        JSONObject wsEventsFishing = new JSONObject();

        wsEventsFishing.put("goodUntil", epoch);
        wsEventsFishing.put("swid", token.getUid());

        JSONObject data = new JSONObject();
        data.put("type", 1);
        data.put("details", winningRewardName);

        wsEventsFishing.put("data", data);

        byte[] fishingHash = Utilities.getSignatureGenerator().hashString(data.toString(), StandardCharsets.UTF_8).asBytes();
        wsEventsFishing.put("signature", Base64.getEncoder().encodeToString(fishingHash));

        wsEventsArray.put(wsEventsFishing);

        if (wsEventsLvl != null && wsEventsLvl.length() > 0) {
            JSONObject wsEventsLevelFinal = new JSONObject();

            byte[] levelHash = Utilities.getSignatureGenerator().hashString(wsEventsLvl.toString(), StandardCharsets.UTF_8).asBytes();

            wsEventsLevelFinal.put("goodUntil", epoch);
            wsEventsLevelFinal.put("swid", token.getUid());
            wsEventsLevelFinal.put("data", wsEventsLvl);
            wsEventsLevelFinal.put("signature", Base64.getEncoder().encodeToString(levelHash));

            if (wsEventsLvl.optInt("type") == 3) {
                persistSignedResponse(wsEventsLevelFinal, token, "wsLevelUp");
                saveReward(snapshot, wsEventsLvl.getInt("details"));
            }

            wsEventsArray.put(wsEventsLevelFinal);
        }

        finalResponse.put("wsEvents", wsEventsArray);

        JSONObject rewardsResponseObject = new JSONObject();
        rewardsResponseObject.put("goodUntil", epoch);
        rewardsResponseObject.put("swid", token.getUid());

        JSONObject rewardsData = new JSONObject();
        rewardsData.put("source", 5);
        rewardsData.put("sourceId", "fishing");
        JSONObject rewards = new JSONObject();
        JSONObject sessionIdObj = new JSONObject();
        sessionIdObj.put(String.valueOf(sessionId), APICore.getRewardManager().getRewardAsJSON(reward));
        rewards.put("rewards", sessionIdObj);
        rewardsData.put("rewards", rewards);

        byte[] rewardsHash = Utilities.getSignatureGenerator().hashString(rewardsData.toString(), StandardCharsets.UTF_8).asBytes();

        rewardsResponseObject.put("data", rewardsData);
        rewardsResponseObject.put("signature", Base64.getEncoder().encodeToString(rewardsHash));

        finalResponse.put("rewards", rewardsResponseObject);

        persistSignedResponse(rewardsResponseObject, token, "broadcastReward");

        return finalResponse;
    }

    private boolean verifyResponse(FirebaseToken token, String swidParam, long goodUntilParam, String signatureParam) throws Exception {
        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> fishingCastData = redis.get(token.getName() + "-fishingCastData");

        JsonObject object = new JsonParser().parse(fishingCastData.get()).getAsJsonObject();
        String validatedStateData = object.getAsJsonObject("data").toString();
        long goodUntilRedis = object.get("goodUntil").getAsLong();
        String signatureRedis = object.get("signature").getAsString();
        String swidRedis = object.get("swid").getAsString();

        byte[] hash = Utilities.getSignatureGenerator().hashString(validatedStateData, StandardCharsets.UTF_8).asBytes();
        String verifiedSignature = Base64.getEncoder().encodeToString(hash);

        boolean swidFullyVerified = false, signatureFullyVerified = false, goodUntilFullyVerified = false;

        Sentry.getContext().addTag("original", signatureParam + " - " + goodUntilParam + " - " + swidParam);
        Sentry.getContext().addTag("redis", verifiedSignature + " - " + goodUntilRedis + " - " + swidRedis);

        if (swidRedis.equals(swidParam)) {
            Sentry.getContext().addTag("swid", "Match");
            if (token.getUid().equals(swidRedis)) {
                Sentry.getContext().addTag("swid", "Fully verified");
                swidFullyVerified = true;
            } else {
                Sentry.getContext().addTag("swid", "Not fully verified");
            }
        } else {
            Sentry.getContext().addTag("swid", "No match");
        }

        if (signatureRedis.equals(signatureParam)) {
            Sentry.getContext().addTag("signature", "Match");
            if (verifiedSignature.equals(signatureRedis)) {
                Sentry.getContext().addTag("signature", "Fully verified");
                signatureFullyVerified = true;
            } else {
                Sentry.getContext().addTag("signature", "Not fully verified");
            }
        } else {
            Sentry.getContext().addTag("signature", "No match");
        }

        if (goodUntilRedis <= goodUntilParam) {
            Sentry.getContext().addTag("goodUntil", "Match");
            if (System.currentTimeMillis() <= goodUntilRedis) {
                Sentry.getContext().addTag("goodUntil", "Fully verified");
                goodUntilFullyVerified = true;
            } else {
                Sentry.getContext().addTag("goodUntil", "Not fully verified");
            }
        } else {
            Sentry.getContext().addTag("goodUntil", "No match");
        }

        return swidFullyVerified && signatureFullyVerified && goodUntilFullyVerified;
    }

    private void persistSignedResponse(JSONObject object, FirebaseToken token, String type) {
        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> storage = redis.set(token.getName() + "-" + type, object.toString());
        storage.thenAccept(string -> APICore.getLogger().info("Successfully stored " + type + " data for user " + token.getName() + " (" + token.getUid() + ") in Redis!"));
    }

    private void saveReward(DocumentSnapshot snapshot, int newLevel) {
        JsonObject levelReward = APICore.getProgressionService().getProgressionUnlockData(newLevel);

        APICore.getLogger().info("Level reward: " + levelReward.toString());

        // colour packs always null in level up rewards

        JsonArray decalsArray = levelReward.get("decals").getAsJsonArray();
        List<Integer> decals = new ArrayList<>();
        for (JsonElement decalElement : decalsArray) {
            Integer decal = decalElement.getAsInt();
            decals.add(decal);
        }

        JsonArray decorationPurchaseRightsArray = levelReward.get("decorationPurchaseRights").getAsJsonArray();
        List<Integer> decorationPurchaseRights = new ArrayList<>();
        for (JsonElement dprElement : decorationPurchaseRightsArray) {
            Integer dpr = dprElement.getAsInt();
            decorationPurchaseRights.add(dpr);
        }

        JsonArray durablesArray = levelReward.get("durables").getAsJsonArray();
        List<Integer> durables = new ArrayList<>();
        for (JsonElement durableElement : durablesArray) {
            Integer durable = durableElement.getAsInt();
            durables.add(durable);
        }

        JsonArray emotePacksArray = levelReward.get("emotePacks").getAsJsonArray();
        List<String> emotePacks = new ArrayList<>();
        for (JsonElement emoteElement : emotePacksArray) {
            String emotePack = emoteElement.getAsString();
            emotePacks.add(emotePack);
        }

        JsonArray equipmentTemplatesArray = levelReward.get("equipmentTemplates").getAsJsonArray();
        List<Integer> equipmentTemplates = new ArrayList<>();
        for (JsonElement equipmentElement : equipmentTemplatesArray) {
            Integer equipmentTemplate = equipmentElement.getAsInt();
            equipmentTemplates.add(equipmentTemplate);
        }

        JsonArray fabricsArray = levelReward.get("fabrics").getAsJsonArray();
        List<Integer> fabrics = new ArrayList<>();
        for (JsonElement fabricElement : fabricsArray) {
            Integer fabric = fabricElement.getAsInt();
            fabrics.add(fabric);
        }

        Integer iglooSlots = levelReward.get("iglooSlots").getAsInt();

        JsonArray lightingArray = levelReward.get("lighting").getAsJsonArray();
        List<Integer> lightings = new ArrayList<>();
        for (JsonElement lightingElement : lightingArray) {
            Integer lighting = lightingElement.getAsInt();
            lightings.add(lighting);
        }

        JsonArray lotsArray = levelReward.get("lots").getAsJsonArray();
        List<String> lots = new ArrayList<>();
        for (JsonElement lotElement : lotsArray) {
            String lot = lotElement.getAsString();
            lots.add(lot);
        }

        JsonArray musicTracksArray = levelReward.get("musicTracks").getAsJsonArray();
        List<Integer> musicTracks = new ArrayList<>();
        for (JsonElement musicTrackElement : musicTracksArray) {
            Integer musicTrack = musicTrackElement.getAsInt();
            musicTracks.add(musicTrack);
        }

        JsonArray partySuppliesArray = levelReward.get("partySupplies").getAsJsonArray();
        List<Integer> partySupplies = new ArrayList<>();
        for (JsonElement partySupplyElement : partySuppliesArray) {
            Integer partySupply = partySupplyElement.getAsInt();
            partySupplies.add(partySupply);
        }

        Integer savedOutfitSlots = levelReward.get("savedOutfitSlots").getAsInt();

        // sizzle clips always null in level up reward

        JsonArray structurePurchaseRightsArray = levelReward.get("structurePurchaseRights").getAsJsonArray();
        List<Integer> structurePurchaseRights = new ArrayList<>();
        for (JsonElement sprElement : structurePurchaseRightsArray) {
            Integer spr = sprElement.getAsInt();
            structurePurchaseRights.add(spr);
        }

        JsonArray tubesArray = levelReward.get("tubes").getAsJsonArray();
        List<Integer> tubes = new ArrayList<>();
        for (JsonElement tubeElement : tubesArray) {
            Integer tube = tubeElement.getAsInt();
            tubes.add(tube);
        }

        Reward reward = new Reward.Builder()
                .withDecals(decals)
                .withDecorationPurchaseRights(decorationPurchaseRights)
                .withDurables(durables)
                .withEmotePacks(emotePacks)
                .withEquipmentTemplates(equipmentTemplates)
                .withFabrics(fabrics)
                .withIglooSlots(iglooSlots)
                .withLighting(lightings)
                .withLots(lots)
                .withMusicTracks(musicTracks)
                .withPartySupplies(partySupplies)
                .withSavedOutfitSlots(savedOutfitSlots)
                .withStructurePurchaseRights(structurePurchaseRights)
                .withTubes(tubes)
                .build();

        APICore.getRewardManager().saveReward(snapshot, reward, null);
    }
}
