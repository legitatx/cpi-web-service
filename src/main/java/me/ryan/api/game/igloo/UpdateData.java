package me.legit.api.game.igloo;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.models.igloo.SavedSceneLayout;
import me.legit.models.igloo.SceneLayout;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class UpdateData implements Handler {

    private Gson gson;
    private JsonParser parser;

    public UpdateData() {
        this.gson = new GsonBuilder().serializeNulls().create();
        this.parser = new JsonParser();
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (UpdateData)");

        JsonObject requestBody = parser.parse(ctx.body()).getAsJsonObject();
        Long activeLayoutId = requestBody.get("activeLayoutId").getAsLong();

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();
        DocumentReference docRef = db.collection("igloos").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            Integer visibility = null;
            JsonObject activeLayout = null;
            if (!requestBody.get("visibility").isJsonNull()) {
                visibility = requestBody.get("visibility").getAsInt();
            }
            if (!requestBody.get("activeLayoutId").isJsonNull()) {
                List<Map<String, Object>> layouts = (List<Map<String, Object>>) document.get("layouts");
                if (layouts != null) {
                    for (Map<String, Object> layout : layouts) {
                        JsonObject layoutJson = parser.parse(gson.toJson(layout)).getAsJsonObject();
                        if (Long.valueOf(layoutJson.get("layoutId").getAsString()).equals(activeLayoutId)) {
                            activeLayout = layoutJson;
                        }
                    }
                }
            }
            WriteBatch batch = db.batch();
            if (visibility != null) {
                batch.update(docRef, "visibility", visibility);
            }
            if (activeLayout != null) {
                batch.update(docRef, "activeLayout", gson.fromJson(activeLayout.toString(), Map.class));
                batch.update(docRef, "activeLayoutId", String.valueOf(activeLayoutId));
            }

            CompletableFuture<String> resultFuture = new CompletableFuture<>();

            ApiFuture<List<WriteResult>> writeResults = batch.commit();
            ApiFutures.addCallback(writeResults, new ApiFutureCallback<List<WriteResult>>() {
                @Override
                public void onFailure(Throwable t) {
                    APICore.getLogger().severe("An error was thrown while attempting to update igloo data!");

                    t.printStackTrace();
                    Sentry.capture(t);

                    ctx.status(500);

                    resultFuture.completeExceptionally(t);
                }

                @Override
                public void onSuccess(List<WriteResult> results) {
                    for (WriteResult result : results) {
                        APICore.getLogger().info("Successfully completed a document update while updating igloo data for user " + uid + ": " + result.getUpdateTime());
                    }

                    try {
                        ApiFuture<DocumentSnapshot> future = docRef.get();
                        DocumentSnapshot document = future.get();

                        Number visibility = (Number) document.get("visibility");
                        String activeLayoutId = (String) document.get("activeLayoutId");
                        Map<String, Object> activeLayout = (Map<String, Object>) document.get("activeLayout");

                        if (visibility != null && activeLayoutId != null && activeLayout != null) {
                            JsonObject layoutJson = parser.parse(gson.toJson(activeLayout)).getAsJsonObject();

                            JsonObject data = new JsonObject();
                            data.addProperty("visibility", visibility);
                            data.addProperty("activeLayoutId", Long.valueOf(activeLayoutId));

                            layoutJson.addProperty("createdDate", layoutJson.get("createdDate").getAsLong());
                            layoutJson.addProperty("lastModifiedDate", layoutJson.get("lastModifiedDate").getAsLong());
                            layoutJson.addProperty("layoutId", Long.valueOf(layoutJson.get("layoutId").getAsString()));

                            SceneLayout sceneLayout = gson.fromJson(layoutJson, SceneLayout.class);
                            data.add("activeLayout", parser.parse(gson.toJson(sceneLayout)));

                            JsonObject signedIglooData = generateSignedResponse(data, uid);
                            persistSignedResponse(signedIglooData, decodedToken, "iglooUpdate");

                            resultFuture.complete(signedIglooData.toString());
                        } else {
                            APICore.getLogger().severe(uid + " - (visibility == null || activeLayoutId == null || activeLayout == null) (UpdateData)");

                            Sentry.getContext().addExtra("halted", true);
                            Sentry.capture("(visibility == null || activeLayoutId == null || activeLayout == null)  (UpdateData)");

                            ctx.status(500);

                            resultFuture.complete(new JSONObject().put("status", 500).put("message", "An unknown error occurred while attempting to update igloo data.").toString());
                        }
                    } catch (InterruptedException | ExecutionException e) {
                        APICore.getLogger().severe("An error was thrown while attempting to update igloo data for a user (failed to wait for new future)!");

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

    @SuppressWarnings("Duplicates")
    private JsonObject generateSignedResponse(JsonObject data, String uid) {
        JsonObject finalResponse = new JsonObject();

        finalResponse.add("data", data);
        finalResponse.addProperty("swid", uid);

        LocalDateTime time = LocalDateTime.now().plusMinutes(5);
        ZoneId zoneId = ZoneId.systemDefault();
        long epoch = time.atZone(zoneId).toInstant().toEpochMilli();
        finalResponse.addProperty("goodUntil", epoch);

        byte[] hash = Utilities.getSignatureGenerator().hashString(data.toString(), StandardCharsets.UTF_8).asBytes();
        finalResponse.addProperty("signature", Base64.getEncoder().encodeToString(hash));

        return finalResponse;
    }

    private void persistSignedResponse(JsonObject object, FirebaseToken token, String type) {
        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> storage = redis.set(token.getName() + "-" + type, object.toString());
        storage.thenAccept(string -> APICore.getLogger().info("Successfully stored " + type + " data for user " + token.getName() + " (" + token.getUid() + ") in Redis!"));
    }
}
