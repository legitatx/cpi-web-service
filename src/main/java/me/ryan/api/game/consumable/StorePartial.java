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
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class StorePartial implements Handler {

    public StorePartial() {
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (StorePartial)");

        JsonObject object = new JsonParser().parse(ctx.body()).getAsJsonObject();

        JsonObject data = object.getAsJsonObject("data");
        String type = data.get("type").getAsString();
        int partialCount = data.get("partialCount").getAsInt();

        long goodUntil = object.get("goodUntil").getAsLong();
        String signature = object.get("signature").getAsString();
        String swid = object.get("swid").getAsString();

        if (!verifyRequest(decodedToken, swid, goodUntil, signature)) {
            Sentry.capture("(Failed to verify response) (StorePartial)");
            ctx.status(400);
            ctx.result("{\"message\":\"Invalid URI parameter(s)\",\"code\":10}");
        } else {
            Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

            DocumentReference docRef = db.collection("users").document(uid);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot document = future.get();
            if (document.exists()) {
                Map<String, Object> consumableInventoryType = (Map<String, Object>) document.get("consumableInventory.inventoryMap." + type);
                JSONObject typeObject;
                if (consumableInventoryType != null) {
                    APICore.getLogger().info("Successfully fetched consumable inventory data for " + uid + " at: " + document.getReadTime() + " (StorePartial)");

                    typeObject = new JSONObject(consumableInventoryType);
                    typeObject.put("partialCount", partialCount);
                } else {
                    typeObject = new JSONObject();
                    typeObject.put("itemCount", 0);
                    typeObject.put("partialCount", partialCount);
                    typeObject.put("lastPurchaseTimestamp", LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                }

                CompletableFuture<String> resultFuture = new CompletableFuture<>();

                ApiFuture<WriteResult> result = docRef.update("consumableInventory.inventoryMap." + type, typeObject.toMap());
                ApiFutures.addCallback(result, new ApiFutureCallback<WriteResult>() {
                    @Override
                    public void onFailure(Throwable t) {
                        APICore.getLogger().severe("An error was thrown while attempting to store a new partial count for a consumable!");

                        t.printStackTrace();
                        Sentry.capture(t);

                        ctx.status(500);

                        resultFuture.completeExceptionally(t);
                    }

                    @Override
                    public void onSuccess(WriteResult result) {
                        APICore.getLogger().info("Successfully stored partial count in inventory for a consumable item (" + type + ":" + partialCount + ") for user " + uid + " at: " + result.getUpdateTime());
                        try {
                            ApiFuture<DocumentSnapshot> future = docRef.get();
                            DocumentSnapshot document = future.get();
                            Map<String, Object> consumableInventory = (Map<String, Object>) document.get("consumableInventory");

                            if (consumableInventory != null) {
                                JSONObject consumablesJson = new JSONObject(consumableInventory);

                                JSONObject signedResponse = generateSignedResponse(consumablesJson, uid);
                                persistSignedResponse(signedResponse, decodedToken, "partialConsumable");

                                resultFuture.complete(signedResponse.toString());
                            } else {
                                APICore.getLogger().severe(uid + " - (consumableInventory == null) (StorePartial)");

                                Sentry.getContext().addExtra("halted", true);
                                Sentry.capture("(consumableInventory == null)  (StorePartial)");

                                ctx.status(500);

                                resultFuture.complete(new JSONObject().put("status", 500).put("message", "An unknown error occurred while attempting to store a new partial count for a consumable.").toString());
                            }
                        } catch (InterruptedException | ExecutionException e) {
                            APICore.getLogger().severe("An error was thrown while attempting to store a new partial count for a consumable (failed to wait for new future)!");

                            e.printStackTrace();
                            Sentry.capture(e);

                            ctx.status(500);

                            resultFuture.completeExceptionally(e);
                        }
                    }
                }, MoreExecutors.directExecutor());

                ctx.result(resultFuture);
            } else {
                APICore.getLogger().severe(uid + " - (document.exists() == false) (StorePartial)");

                Sentry.getContext().addExtra("halted", true);
                Sentry.capture("(document.exists() == false) (StorePartial)");

                ctx.status(500);
            }
        }
    }

    private boolean verifyRequest(FirebaseToken token, String swidParam, long goodUntilParam, String signatureParam) throws Exception {
        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> partialConsumableData = redis.get(token.getName() + "-partialConsumable");

        JsonObject object = new JsonParser().parse(partialConsumableData.get()).getAsJsonObject();
        String partialConsumableDataRedis = object.getAsJsonObject("data").toString();
        long goodUntilRedis = object.get("goodUntil").getAsLong();
        String signatureRedis = object.get("signature").getAsString();
        String swidRedis = object.get("swid").getAsString();

        byte[] hash = Utilities.getSignatureGenerator().hashString(partialConsumableDataRedis, StandardCharsets.UTF_8).asBytes();
        String verifiedSignature = Base64.getEncoder().encodeToString(hash);

        boolean swidFullyVerified = false, signatureFullyVerified = false, goodUntilFullyVerified = false;

        if (swidRedis.equals(swidParam)) {
            if (token.getUid().equals(swidRedis)) {
                swidFullyVerified = true;
            }
        }

        if (signatureRedis.equals(signatureParam)) {
            if (verifiedSignature.equals(signatureRedis)) {
                signatureFullyVerified = true;
            }
        }

        if (goodUntilRedis <= goodUntilParam) {
            if (System.currentTimeMillis() <= goodUntilRedis) {
                goodUntilFullyVerified = true;
            }
        }

        return swidFullyVerified && signatureFullyVerified && goodUntilFullyVerified;
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
