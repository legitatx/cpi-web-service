package me.legit.api.game.reward;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
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
import me.legit.models.reward.Reward;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class Add implements Handler {

    public Add() {
        //TODO fix this to work for fishing and consumable rewards as well, not just tube racing
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");

        JsonObject object = new JsonParser().parse(ctx.body()).getAsJsonObject();
        JsonObject data = object.getAsJsonObject("data");
        int sessionId = Integer.valueOf(data.get("sourceId").getAsString());
        long goodUntil = object.get("goodUntil").getAsLong();
        String signature = object.get("signature").getAsString();

        String encodedToken = ctx.header("Authorization").split("Basic ")[1].split(", GAE")[0];
        String token = new String(Base64.getDecoder().decode(encodedToken)).split(":")[0];

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(token, checkRevoked).get();
        String uid = decodedToken.getUid();

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Add)");

        if (!verifyResponse(goodUntil, signature, sessionId)) {
            Sentry.capture("(Failed to verify response) (Add)");
            ctx.status(400);
            ctx.result("{\"message\":\"Invalid URI parameter(s)\",\"code\":10}");
        } else {
            Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

            DocumentReference docRef = db.collection("users").document(uid);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot document = future.get();

            String currentSessionId = ((Long) document.get("currentSessionId")).toString();
            JsonObject collected = data.getAsJsonObject("rewards");
            if (collected.has(currentSessionId)) {
                JsonObject rewardSessionId = collected.getAsJsonObject(currentSessionId);
                Reward reward = APICore.getRewardManager().getReward(rewardSessionId.toString());

                JSONObject finalResponse = new JSONObject();

                CompletableFuture<String> resultFuture = new CompletableFuture<>();

                List<ApiFuture<String>> rewardFutures = APICore.getRewardManager().saveReward(document, reward, finalResponse);
                ApiFutures.addCallback(ApiFutures.allAsList(rewardFutures), new ApiFutureCallback<List<String>>() {
                    @Override
                    public void onFailure(Throwable t) {
                        APICore.getLogger().severe("An error was thrown while attempting to claim a minigame reward for a user!");

                        t.printStackTrace();
                        Sentry.capture(t);

                        ctx.status(500);

                        resultFuture.completeExceptionally(t);
                    }

                    @Override
                    public void onSuccess(List<String> results) {
                        for (String result : results) {
                            APICore.getLogger().info("Successfully completed a transaction while claiming a minigame reward for user " + uid + ": Result - (" + result + ")");
                        }

                        try {
                            ApiFuture<DocumentSnapshot> future = docRef.get();
                            DocumentSnapshot document = future.get();
                            Map<String, Object> assetsMap = (Map<String, Object>) document.get("assets");

                            resultFuture.complete(generateAddResponse(finalResponse, assetsMap).toString());
                        } catch (InterruptedException | ExecutionException e) {
                            APICore.getLogger().severe("An error was thrown while attempting to claim a minigame reward for a user (failed to wait for new future)!");

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

    private boolean verifyResponse(long goodUntilParam, String signatureParam, int sessionId) throws Exception {
        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> requestEarnedRewardsData = redis.get("tubeRaceRewards-" + sessionId);

        JsonObject object = new JsonParser().parse(requestEarnedRewardsData.get()).getAsJsonObject();
        String earnedRewardDataRedis = object.getAsJsonObject("data").toString();
        long goodUntilRedis = object.get("goodUntil").getAsLong();
        String signatureRedis = object.get("signature").getAsString();

        byte[] hash = Utilities.getSignatureGenerator().hashString(earnedRewardDataRedis, StandardCharsets.UTF_8).asBytes();
        String verifiedSignature = Base64.getEncoder().encodeToString(hash);

        boolean signatureFullyVerified = false, goodUntilFullyVerified = false;

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

        return signatureFullyVerified && goodUntilFullyVerified;
    }

    private JSONObject generateAddResponse(JSONObject wsEvents, Map<String, Object> assets) {
        JSONObject finalResponse = new JSONObject();

        if (wsEvents != null && wsEvents.length() > 0) {
            JSONArray wsEventsArray = new JSONArray();
            wsEventsArray.put(wsEvents);
            finalResponse.put("wsEvents", wsEventsArray);
        } else {
            finalResponse.put("wsEvents", Collections.emptyList());
        }

        if (assets != null) {
            finalResponse.put("assets", assets);
        } else {
            finalResponse.put("assets", new JSONObject());
        }

        return finalResponse;
    }
}
