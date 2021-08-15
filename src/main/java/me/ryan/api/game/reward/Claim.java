package me.legit.api.game.reward;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("Duplicates")
public class Claim implements Handler {

    public Claim() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");

        String encodedToken = ctx.header("Authorization").split("Basic ")[1].split(", GAE")[0];
        String token = new String(Base64.getDecoder().decode(encodedToken)).split(":")[0];

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(token, checkRevoked).get();
        String uid = decodedToken.getUid();

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Claim)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        int rewardId = Integer.parseInt(ctx.pathParam("id"));

        Sentry.getContext().addExtra("rewardId", rewardId);

        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            List<Integer> claimedRewardIds = (List<Integer>) document.get("claimedRewardIds");
            if (claimedRewardIds != null) {
                if (!claimedRewardIds.contains(rewardId)) {
                    Reward reward = APICore.getClaimableRewardService().getReward(rewardId);
                    if (reward != null) {
                        JSONObject finalResponse = new JSONObject();
                        APICore.getRewardManager().saveReward(document, reward, finalResponse);

                        ApiFuture<WriteResult> updateClaimedRewards = docRef.update("claimedRewardIds", FieldValue.arrayUnion(rewardId));
                        APICore.getLogger().info("Updated claimed rewards at: " + updateClaimedRewards.get().getUpdateTime());

                        ctx.result(generateClaimResponse(finalResponse, reward, document, decodedToken).toString());
                    } else {
                        APICore.getLogger().severe("Somehow reward was null? (UID: " + uid + ") (Reward ID: " + rewardId + ")");
                        Sentry.capture("Somehow reward was null? (1)");

                        ctx.result(generateClaimResponse(null, null, document, decodedToken).toString());
                    }
                } else {
                    APICore.getLogger().info("User " + uid + " tried to claim reward " + rewardId + " twice but successfully failed!");

                    Sentry.capture("User tried to claim a reward twice but successfully failed!");

                    ctx.status(410);
                    ctx.result(generateClaimResponse(null, null, document, decodedToken).toString());
                }
            } else {
                APICore.getLogger().info("claimedRewardIds == null, assuming user does not already have this reward... (" + uid + ")");

                Reward reward = APICore.getClaimableRewardService().getReward(rewardId);
                if (reward != null) {
                    JSONObject finalResponse = new JSONObject();
                    APICore.getRewardManager().saveReward(document, reward, finalResponse);

                    ApiFuture<WriteResult> updateClaimedRewards = docRef.update("claimedRewardIds", FieldValue.arrayUnion(rewardId));
                    APICore.getLogger().info("Updated claimed rewards at: " + updateClaimedRewards.get().getUpdateTime());

                    ctx.result(generateClaimResponse(finalResponse, reward, document, decodedToken).toString());
                } else {
                    APICore.getLogger().severe("Somehow reward was null? (UID: " + uid + ") (Reward ID: " + rewardId + ")");
                    Sentry.capture("Somehow reward was null? (2)");

                    ctx.result(generateClaimResponse(null, null, document, decodedToken).toString());
                }
            }
        } else {
            APICore.getLogger().severe(uid + " - " + "(document.exists() == false) (Claim)");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("(document.exists() == false) (Claim)");

            ctx.status(500);
        }
    }

    private JSONObject generateClaimResponse(JSONObject wsEvents, Reward reward, DocumentSnapshot snapshot, FirebaseToken token) throws Exception {
        JSONObject finalResponse = new JSONObject();

        if (wsEvents != null && wsEvents.length() > 0) {
            JSONObject wsEventsFinal = new JSONObject();

            LocalDateTime time = LocalDateTime.now().plusMinutes(5);
            ZoneId zoneId = ZoneId.systemDefault();
            long epoch = time.atZone(zoneId).toInstant().toEpochMilli();

            byte[] hash = Utilities.getSignatureGenerator().hashString(wsEvents.toString(), StandardCharsets.UTF_8).asBytes();

            wsEventsFinal.put("goodUntil", epoch);
            wsEventsFinal.put("swid", token.getUid());
            wsEventsFinal.put("data", wsEvents);
            wsEventsFinal.put("signature", Base64.getEncoder().encodeToString(hash));

            if (wsEvents.optInt("type") == 3) {
                persistSignedResponse(wsEventsFinal, token);
                saveReward(snapshot, wsEvents.getInt("details"));
            }

            JSONArray wsEventsArray = new JSONArray();
            wsEventsArray.put(wsEventsFinal);

            finalResponse.put("wsEvents", wsEventsArray);
        } else {
            finalResponse.put("wsEvents", Collections.emptyList());
        }

        if (reward != null) {
            JSONObject rewardAsJson = APICore.getRewardManager().getRewardAsJSON(reward);
            finalResponse.put("reward", rewardAsJson);
        } else {
            finalResponse.put("reward", new JSONObject());
        }

        return finalResponse;
    }

    private void persistSignedResponse(JSONObject wsEvents, FirebaseToken token) {
        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> levelUpStorage = redis.set(token.getName() + "-wsLevelUp", wsEvents.toString());
        levelUpStorage.thenAccept(string -> APICore.getLogger().info("Successfully stored level up data for user " + token.getName() + " (" + token.getUid() + ") in Redis!"));
    }

    private void saveReward(DocumentSnapshot snapshot, int newLevel) throws Exception {
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
