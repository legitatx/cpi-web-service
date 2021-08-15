package me.legit.api.game.igloo.decoration;

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
import me.legit.models.decoration.DecorationId;
import me.legit.models.decoration.DecorationInventoryItem;
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

public class PurchaseDecoration implements Handler {

    public PurchaseDecoration() {
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (PurchaseDecoration)");

        String id = ctx.pathParam("id");
        int count = Integer.parseInt(ctx.pathParam("count"));

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();
        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            Map<String, Object> decorationInventoryMap = (Map<String, Object>) document.get("decorationInventory");
            if (decorationInventoryMap != null) {
                APICore.getLogger().info("Successfully fetched decoration inventory for " + uid + " at: " + document.getReadTime() + " (GetDecoration)");

                JSONObject decorationObj = new JSONObject(decorationInventoryMap);
                if (decorationObj.has(id)) {
                    decorationObj.put(id, decorationObj.getInt(id) + count);
                } else {
                    decorationObj.put(id, count);
                }

                ApiFuture<WriteResult> updateDecorations = docRef.update("decorationInventory", decorationObj.toMap());
                APICore.getLogger().info("Purchased decoration " + id + " for user " + uid + " at: " + updateDecorations.get().getUpdateTime());

                CompletableFuture<String> resultFuture = new CompletableFuture<>();

                DecorationId decorationId = DecorationId.fromString(id);
                List<ApiFuture<String>> decorationFutures = APICore.getDecorationService().subtractDecorationCost(document, decorationId, count);
                ApiFutures.addCallback(ApiFutures.allAsList(decorationFutures), new ApiFutureCallback<List<String>>() {
                    @Override
                    public void onFailure(Throwable t) {
                        APICore.getLogger().severe("An error was thrown while attempting to purchase a new igloo decoration!");

                        t.printStackTrace();
                        Sentry.capture(t);

                        ctx.status(500);

                        resultFuture.completeExceptionally(t);
                    }

                    @Override
                    public void onSuccess(List<String> results) {
                        for (String result : results) {
                            APICore.getLogger().info("Successfully completed a transaction while purchasing a new igloo decoration for user " + uid + " (subtract cost): Result - (" + result + ")");
                        }

                        try {
                            ApiFuture<DocumentSnapshot> future = docRef.get();
                            DocumentSnapshot document = future.get();

                            Map<String, Object> assetsMap = (Map<String, Object>) document.get("assets");

                            if (assetsMap != null) {
                                JSONObject assetsJson = new JSONObject(assetsMap);

                                resultFuture.complete(generatePurchaseResponse(decodedToken, decorationId, assetsJson).toString());
                            } else {
                                APICore.getLogger().severe(uid + " - (assetsMap == null) (PurchaseDecoration)");

                                Sentry.getContext().addExtra("halted", true);
                                Sentry.capture("(assetsMap == null)  (PurchaseDecoration)");

                                ctx.status(500);

                                resultFuture.complete(new JSONObject().put("status", 500).put("message", "An unknown error occurred while attempting to purchase an igloo decoration.").toString());
                            }
                        } catch (InterruptedException | ExecutionException e) {
                            APICore.getLogger().severe("An error was thrown while attempting to purchase a new igloo decoration for a user (failed to wait for new future)!");

                            e.printStackTrace();
                            Sentry.capture(e);

                            ctx.status(500);

                            resultFuture.completeExceptionally(e);
                        }
                    }
                }, MoreExecutors.directExecutor());

                ctx.result(resultFuture);
            }
        }
    }

    private JSONObject generatePurchaseResponse(FirebaseToken token, DecorationId decorationId, JSONObject assets) {
        JSONObject finalResponse = new JSONObject();

        JSONObject decorationIdObj = new JSONObject();
        decorationIdObj.put("definitionId", decorationId.getDefinitionId());
        decorationIdObj.put("type", decorationId.getType().ordinal());
        if (decorationId.getCustomId() == null) {
            decorationIdObj.put("customId", JSONObject.NULL);
        } else {
            decorationIdObj.put("customId", decorationId.getCustomId());
        }

        JSONObject wsEvent = new JSONObject();
        wsEvent.put("type", 6);
        JSONObject details = new JSONObject();
        details.put("definitionId", decorationId.getDefinitionId());
        details.put("type", decorationId.getType().ordinal());
        if (decorationId.getCustomId() == null) {
            details.put("customId", JSONObject.NULL);
            details.put("customized", false);
        } else {
            details.put("customId", decorationId.getCustomId());
            details.put("customized", true);
        }
        details.put("decorationId", decorationIdObj);
        wsEvent.put("details", details);

        finalResponse.put("wsEvents", new JSONArray().put(generateSignedResponse(wsEvent, token.getUid())));
        finalResponse.put("decorationId", decorationIdObj);
        finalResponse.put("assets", assets);

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
}
