package me.legit.api.game.quest;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.common.collect.Lists;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.*;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.models.quest.Quest;
import me.legit.models.quest.QuestStatus;
import me.legit.models.reward.Reward;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@SuppressWarnings({"unchecked", "Duplicates"})
public class Progress implements Handler {

    private static Gson gson = new GsonBuilder().serializeNulls().create();

    public Progress() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");

        JsonObject object = new JsonParser().parse(ctx.body()).getAsJsonObject();
        JsonArray data = object.getAsJsonArray("data");
        long goodUntil = object.get("goodUntil").getAsLong();
        String signature = object.get("signature").getAsString();
        String swid = object.get("swid").getAsString();

        String encodedToken = ctx.header("Authorization").split("Basic ")[1].split(", GAE")[0];
        String token = new String(Base64.getDecoder().decode(encodedToken)).split(":")[0];

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(token, checkRevoked).get();
        String uid = decodedToken.getUid();

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Progress)");

        if (!verifyResponse(decodedToken, swid, goodUntil, signature)) {
            Sentry.capture("(Failed to verify response) (Progress)");
            ctx.status(400);
            ctx.result("{\"message\":\"Invalid URI parameter(s)\",\"code\":10}");
        } else {
            Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

            DocumentReference docRef = db.collection("users").document(uid);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot document = future.get();
            if (document.exists()) {
                JSONObject questData = APICore.getQuestManager().completeNewObjectives(docRef, document, data);

                String questId = questData.getString("questId");
                String finalQuestData = questData.getString("finalQuestData");
                JSONObject reward = questData.optJSONObject("reward");
                JSONObject wsEvents = questData.optJSONObject("wsEvents");

                JSONObject questResponse = generateQuestResponse(wsEvents, questId, finalQuestData, decodedToken, reward, docRef);

                if (wsEvents != null) {
                    RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
                    RedisFuture<String> wsLevelUpStorage = redis.set(decodedToken.getName() + "-wsLevelUp", questResponse.toString());
                    wsLevelUpStorage.thenAccept(string -> APICore.getLogger().info("Successfully stored level up data for user " + uid + " in Redis!"));

                    if (wsEvents.optInt("type") == 3) {
                        saveReward(document, wsEvents.getInt("details"));
                    }
                }

                ctx.result(questResponse.toString());
            } else {
                APICore.getLogger().severe(uid + " - (document.exists() == false) (Progress)");

                Sentry.getContext().addExtra("halted", true);
                Sentry.capture(uid + " - (document.exists() == false) (Progress)");

                ctx.status(500);
            }
        }
    }

    private boolean verifyResponse(FirebaseToken token, String swidParam, long goodUntilParam, String signatureParam) throws Exception {
        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> requestQuestData = redis.get(token.getName() + "-questData");

        JsonObject object = new JsonParser().parse(requestQuestData.get()).getAsJsonObject();
        String questsDataRedis = object.getAsJsonArray("data").toString();
        long goodUntilRedis = object.get("goodUntil").getAsLong();
        String signatureRedis = object.get("signature").getAsString();
        String swidRedis = object.get("swid").getAsString();

        byte[] hash = Utilities.getSignatureGenerator().hashString(questsDataRedis, StandardCharsets.UTF_8).asBytes();
        String verifiedSignature = Base64.getEncoder().encodeToString(hash);

        Sentry.getContext().addTag("original", signatureParam + " - " + goodUntilParam + " - " + swidParam);
        Sentry.getContext().addTag("redis", verifiedSignature + " - " + goodUntilRedis + " - " + swidRedis);

        boolean swidFullyVerified = false, signatureFullyVerified = false, goodUntilFullyVerified = false;

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

    private JSONObject generateQuestResponse(JSONObject wsEvents, String questId, String questData, FirebaseToken token, JSONObject rewardJson, DocumentReference reference) throws Exception {
        JSONObject finalResponse = new JSONObject();

        if (wsEvents != null) {
            JSONObject wsEventsFinal = new JSONObject();

            LocalDateTime time = LocalDateTime.now().plusMinutes(5);
            ZoneId zoneId = ZoneId.systemDefault();
            long epoch = time.atZone(zoneId).toInstant().toEpochMilli();

            byte[] hash = Utilities.getSignatureGenerator().hashString(wsEvents.toString(), StandardCharsets.UTF_8).asBytes();

            wsEventsFinal.put("goodUntil", epoch);
            wsEventsFinal.put("swid", token.getUid());
            wsEventsFinal.put("data", wsEvents);
            wsEventsFinal.put("signature", Base64.getEncoder().encodeToString(hash));

            JSONArray wsEventsArray = new JSONArray();
            wsEventsArray.put(wsEventsFinal);

            finalResponse.put("wsEvents", wsEventsArray);
        } else {
            finalResponse.put("wsEvents", Collections.emptyList());
        }

        finalResponse.put("questId", questId);

        if (rewardJson != null) {
            finalResponse.put("reward", rewardJson);
        }

        JSONObject questStateCollection = new JSONObject();

        LocalDateTime time = LocalDateTime.now().plusMinutes(5);
        ZoneId zoneId = ZoneId.systemDefault();
        long epoch = time.atZone(zoneId).toInstant().toEpochMilli();
        questStateCollection.put("goodUntil", epoch);

        questStateCollection.put("swid", token.getUid());

        List<Quest> quests = generateQuestStateResponse(questData, reference, false);
        String finalQuestData = gson.toJson(quests);
        JSONArray data = new JSONArray(finalQuestData);
        questStateCollection.put("data", data);

        byte[] hash = Utilities.getSignatureGenerator().hashString(data.toString(), StandardCharsets.UTF_8).asBytes();
        questStateCollection.put("signature", Base64.getEncoder().encodeToString(hash));

        finalResponse.put("questStateCollection", questStateCollection);

        return finalResponse;
    }

    private List<Quest> generateQuestStateResponse(String finalQuestData, DocumentReference reference, boolean includeComplete) throws Exception {
        List<Quest> questStateCollection = new ArrayList<>();
        List<Quest> questStateCollection2 = new ArrayList<>();
        List<Quest> jsonAsQuestList = Lists.newArrayList(gson.fromJson(finalQuestData, Quest[].class));

        for (Quest quest : jsonAsQuestList) {
            questStateCollection2.add(quest);
            if (includeComplete || quest.getStatus() != QuestStatus.COMPLETED.ordinal()) {
                questStateCollection.add(quest);
            }
        }

        ApiFuture<DocumentSnapshot> future2 = reference.get();
        DocumentSnapshot document2 = future2.get();
        Map<String, Long> mascotXP = (Map<String, Long>) document2.get("assets.mascotXP");
        questStateCollection.addAll(APICore.getQuestManager().getAvailableQuests(questStateCollection2, mascotXP));

        return questStateCollection;
    }

    public static List<Quest> generateQuestStateResponseBySnapshot(String finalQuestData, DocumentSnapshot snapshot, boolean includeComplete) {
        List<Quest> questStateCollection = new ArrayList<>();
        List<Quest> questStateCollection2 = new ArrayList<>();
        List<Quest> jsonAsQuestList = Lists.newArrayList(gson.fromJson(finalQuestData, Quest[].class));

        for (Quest quest : jsonAsQuestList) {
            questStateCollection2.add(quest);
            if (includeComplete || quest.getStatus() != QuestStatus.COMPLETED.ordinal()) {
                questStateCollection.add(quest);
            }
        }

        Map<String, Long> mascotXP = (Map<String, Long>) snapshot.get("assets.mascotXP");
        questStateCollection.addAll(APICore.getQuestManager().getAvailableQuests(questStateCollection2, mascotXP));

        return questStateCollection;
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
