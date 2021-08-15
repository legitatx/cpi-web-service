package me.legit.api.game.store;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.models.decoration.DecorationId;
import me.legit.models.decoration.DecorationInventoryItem;
import me.legit.models.reward.Reward;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Purchase implements Handler {

    public Purchase() {
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Purchase)");

        int type = Integer.parseInt(ctx.pathParam("type"));
        int count = Integer.parseInt(ctx.pathParam("count"));

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            JSONObject finalResponse = new JSONObject();
            Reward reward = APICore.getDisneyStoreService().getDisneyStoreItemReward(type, count);

            CompletableFuture<String> resultFuture = new CompletableFuture<>();

            List<ApiFuture<String>> rewardFutures = APICore.getRewardManager().saveReward(document, reward, finalResponse);
            ApiFutures.addCallback(ApiFutures.allAsList(rewardFutures), new ApiFutureCallback<List<String>>() {
                @Override
                public void onFailure(Throwable t) {
                    APICore.getLogger().severe("An error was thrown while attempting to purchase a new Disney Store item!");

                    t.printStackTrace();
                    Sentry.capture(t);

                    ctx.status(500);

                    resultFuture.completeExceptionally(t);
                }

                @Override
                public void onSuccess(List<String> results) {
                    for (String result : results) {
                        APICore.getLogger().info("Successfully completed a transaction while purchasing a new Disney Store item for user " + uid + ": Result - (" + result + ")");
                    }

                    List<ApiFuture<String>> disneyStoreFutures = APICore.getDisneyStoreService().subtractDisneyStoreItemCost(document, type, count);
                    ApiFutures.addCallback(ApiFutures.allAsList(disneyStoreFutures), new ApiFutureCallback<List<String>>() {
                        @Override
                        public void onFailure(Throwable t) {
                            APICore.getLogger().severe("An error was thrown while attempting to purchase a new Disney Store item!");

                            t.printStackTrace();
                            Sentry.capture(t);

                            ctx.status(500);

                            resultFuture.completeExceptionally(t);
                        }

                        @Override
                        public void onSuccess(List<String> results) {
                            for (String result : results) {
                                APICore.getLogger().info("Successfully completed a transaction while purchasing a new Disney Store item for user " + uid + " (subtract cost): Result - (" + result + ")");
                            }

                            try {
                                ApiFuture<DocumentSnapshot> future = docRef.get();
                                DocumentSnapshot document = future.get();

                                Map<String, Object> assetsMap = (Map<String, Object>) document.get("assets");
                                Map<String, Object> consumableInventory = (Map<String, Object>) document.get("consumableInventory");
                                Map<String, Number> decorationInventory = (Map<String, Number>) document.get("decorationInventory");

                                if (assetsMap != null && consumableInventory != null && decorationInventory != null) {
                                    JSONObject assetsJson = new JSONObject(assetsMap);
                                    JSONObject consumablesJson = new JSONObject(consumableInventory);
                                    List<DecorationInventoryItem> decorationInventoryItems = new ArrayList<>();
                                    for (Map.Entry<String, Number> keyValuePair : decorationInventory.entrySet()) {
                                        decorationInventoryItems.add(new DecorationInventoryItem(DecorationId.fromString(keyValuePair.getKey()), keyValuePair.getValue()));
                                    }

                                    resultFuture.complete(generatePurchaseResponse(decodedToken, assetsJson, consumablesJson, decorationInventoryItems).toString());
                                } else {
                                    APICore.getLogger().severe(uid + " - (assetsMap == null || consumableInventory == null || decorationInventory == null) (Purchase)");

                                    Sentry.getContext().addExtra("halted", true);
                                    Sentry.capture("(assetsMap == null || consumableInventory == null || decorationInventory == null)  (Purchase)");

                                    ctx.status(500);

                                    resultFuture.complete(new JSONObject().put("status", 500).put("message", "An unknown error occurred while attempting to purchase an item from the Disney Store.").toString());
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                APICore.getLogger().severe("An error was thrown while attempting to purchase a new Disney Store item for a user (failed to wait for new future)!");

                                e.printStackTrace();
                                Sentry.capture(e);

                                ctx.status(500);

                                resultFuture.completeExceptionally(e);
                            }
                        }
                    }, MoreExecutors.directExecutor());
                }
            }, MoreExecutors.directExecutor());
            ctx.result(resultFuture);
        } else {
            APICore.getLogger().severe(uid + " - (document.exists() == false) (Purchase)");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("(document.exists() == false) (Purchase)");

            ctx.status(500);
        }
    }

    private JSONObject generatePurchaseResponse(FirebaseToken token, JSONObject assets, JSONObject consumables, List<DecorationInventoryItem> decorationInventory) {
        JSONObject finalResponse = new JSONObject();
        finalResponse.put("wsEvents", Collections.emptyList());
        finalResponse.put("assets", assets);

        JSONObject consumableInventorySigned = generateSignedResponse(consumables, token.getUid());
        persistSignedResponse(consumableInventorySigned, token, "consumableInventory");

        finalResponse.put("inventory", consumableInventorySigned);

        JSONArray decorationInventoryItems = new JSONArray();
        for (DecorationInventoryItem item : decorationInventory) {
            JSONObject itemJson = new JSONObject();
            itemJson.put("definitionId", item.getDecorationId().getDefinitionId());
            itemJson.put("type", item.getDecorationId().getType().ordinal());
            itemJson.put("customId", item.getDecorationId().getCustomId() != null ? item.getDecorationId().getCustomId() : JSONObject.NULL);

            decorationInventoryItems.put(itemJson);
        }

        JSONObject decorationInventoryJson = new JSONObject();
        decorationInventoryJson.put("items", decorationInventoryItems);

        finalResponse.put("decorationInventory", generateSignedResponse(decorationInventoryJson, token.getUid()));

        return finalResponse;
    }

    @SuppressWarnings("Duplicates")
    private JSONObject generateSignedResponse(JSONObject data, String uid) {
        JSONObject finalResponse = new JSONObject();

        finalResponse.put("data", data);
        finalResponse.put("swid", uid);

        LocalDateTime time = LocalDateTime.now().plusMinutes(5);
        ZoneId zoneId = ZoneId.systemDefault();
        long epoch = time.atZone(zoneId).toInstant().toEpochMilli();
        finalResponse.put("goodUntil", epoch);

        byte[] hash = Utilities.getSignatureGenerator().hashString(data.toString(), StandardCharsets.UTF_8).asBytes();
        finalResponse.put("signature", Base64.getEncoder().encodeToString(hash));

        return finalResponse;
    }

    private void persistSignedResponse(JSONObject object, FirebaseToken token, String type) {
        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> storage = redis.set(token.getName() + "-" + type, object.toString());
        storage.thenAccept(string -> APICore.getLogger().info("Successfully stored " + type + " data for user " + token.getName() + " (" + token.getUid() + ") in Redis!"));
    }
}
