package me.legit.api.game.consumable;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.service.PropsService;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;

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

        String type = ctx.pathParam("type");
        int count = Integer.parseInt(ctx.pathParam("count"));

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            PropsService propsService = APICore.getPropsService();

            if (!propsService.canBuyConsumable(document, type, count)) {
                ctx.status(707); // will return not enough coins error in the client
                return;
            }

            Map<String, Object> consumableInventoryType = (Map<String, Object>) document.get("consumableInventory.inventoryMap." + type);
            JSONObject typeObject;
            if (consumableInventoryType != null) {
                APICore.getLogger().info("Successfully fetched consumable inventory data for " + uid + " at: " + document.getReadTime() + " (Purchase)");

                typeObject = new JSONObject(consumableInventoryType);
                int itemCount = typeObject.getInt("itemCount");
                typeObject.put("itemCount", itemCount + count);
            } else {
                typeObject = new JSONObject();
                typeObject.put("itemCount", count);
                typeObject.put("partialCount", 0);
                typeObject.put("lastPurchaseTimestamp", LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            }

            CompletableFuture<String> resultFuture = new CompletableFuture<>();

            ApiFuture<WriteResult> inventoryUpdate = docRef.update("consumableInventory.inventoryMap." + type, typeObject.toMap());
            ApiFutures.addCallback(inventoryUpdate, new ApiFutureCallback<WriteResult>() {
                @Override
                public void onFailure(Throwable t) {
                    APICore.getLogger().severe("An error was thrown while attempting to purchase a new consumable!");

                    t.printStackTrace();
                    Sentry.capture(t);

                    ctx.status(500);

                    resultFuture.completeExceptionally(t);
                }

                @Override
                public void onSuccess(WriteResult result) {
                    APICore.getLogger().info("Successfully updated inventory with a new consumable item (" + type + ":" + count + ") for user " + uid + " at: " + result.getUpdateTime());

                    List<ApiFuture<String>> consumableCostFutures = propsService.subtractConsumableCost(document, type, count);
                    ApiFutures.addCallback(ApiFutures.allAsList(consumableCostFutures), new ApiFutureCallback<List<String>>() {
                        @Override
                        public void onFailure(Throwable t) {
                            APICore.getLogger().severe("An error was thrown while attempting to purchase a new consumable!");

                            t.printStackTrace();
                            Sentry.capture(t);

                            ctx.status(500);

                            resultFuture.completeExceptionally(t);
                        }

                        @Override
                        public void onSuccess(List<String> results) {
                            for (String result : results) {
                                APICore.getLogger().info("Successfully completed a transaction while purchasing a new consumable item (" + type + ":" + count + ") for user " + uid + " (subtract cost): Result - (" + result + ")");
                            }

                            try {
                                ApiFuture<DocumentSnapshot> future = docRef.get();
                                DocumentSnapshot document = future.get();

                                Map<String, Object> assetsMap = (Map<String, Object>) document.get("assets");
                                Map<String, Object> consumableInventory = (Map<String, Object>) document.get("consumableInventory");

                                if (assetsMap != null && consumableInventory != null) {
                                    JSONObject assetsJson = new JSONObject(assetsMap);
                                    JSONObject consumablesJson = new JSONObject(consumableInventory);

                                    resultFuture.complete(generatePurchaseResponse(decodedToken, assetsJson, consumablesJson).toString());
                                } else {
                                    APICore.getLogger().severe(uid + " - (assetsMap == null || consumableInventory == null) (Purchase)");

                                    Sentry.getContext().addExtra("halted", true);
                                    Sentry.capture("(assetsMap == null || consumableInventory == null)  (Purchase)");

                                    ctx.status(500);

                                    resultFuture.complete(new JSONObject().put("status", 500).put("message", "An unknown error occurred while attempting to purchase a new consumable.").toString());
                                }
                            } catch (InterruptedException | ExecutionException e) {
                                APICore.getLogger().severe("An error was thrown while attempting to purchase a new consumable (failed to wait for new future)!");

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
        }
    }

    private JSONObject generatePurchaseResponse(FirebaseToken token, JSONObject assets, JSONObject consumables) {
        JSONObject finalResponse = new JSONObject();
        finalResponse.put("wsEvents", Collections.emptyList());
        finalResponse.put("assets", assets);

        JSONObject consumableInventorySigned = generateSignedResponse(consumables, token.getUid());
        persistSignedResponse(consumableInventorySigned, token, "consumableInventory");

        finalResponse.put("inventory", consumableInventorySigned);

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
