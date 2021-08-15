package me.legit.api.game.reward;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
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
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@SuppressWarnings("Duplicates")
public class Room implements Handler {

    public Room() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");

        JsonObject object = new JsonParser().parse(ctx.body()).getAsJsonObject();
        JsonObject data = object.getAsJsonObject("data");
        long goodUntil = object.get("goodUntil").getAsLong();
        String signature = object.get("signature").getAsString();
        String swid = object.get("swid").getAsString();

        String encodedToken = ctx.header("Authorization").split("Basic ")[1].split(", GAE")[0];
        String token = new String(Base64.getDecoder().decode(encodedToken)).split(":")[0];

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(token, checkRevoked).get();
        String uid = decodedToken.getUid();

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Room)");

        if (!verifyResponse(decodedToken, swid, goodUntil, signature)) {
            Sentry.capture("(Failed to verify response) (Room)");
            ctx.status(400);
            ctx.result("{\"message\":\"Invalid URI parameter(s)\",\"code\":10}");
        } else {
            Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

            DocumentReference docRef = db.collection("users").document(uid);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot document = future.get();
            if (document.exists()) {
                String roomName = data.get("room").getAsString();
                JsonObject collected = data.getAsJsonObject("collected");

                Map<String, Long> earnedRewardsMap = (Map<String, Long>) document.get("earnedRewards." + roomName);
                if (earnedRewardsMap != null) {
                    List<String> list = new ArrayList<>();

                    for (Map.Entry<String, JsonElement> keyValuePair : collected.entrySet()) {
                        if (!earnedRewardsMap.containsKey(keyValuePair.getKey()) || earnedRewardsMap.get(keyValuePair.getKey()) < keyValuePair.getValue().getAsLong()) {
                            earnedRewardsMap.put(keyValuePair.getKey(), keyValuePair.getValue().getAsLong());
                            list.add(keyValuePair.getKey());
                        }
                    }

                    ApiFuture<WriteResult> updateEarnedRewards = docRef.update("earnedRewards." + roomName, earnedRewardsMap);
                    APICore.getLogger().info("Updated earned rewards for user " + document.getId() + " at: " + updateEarnedRewards.get().getUpdateTime());

                    if (list.size() > 0) {
                        Reward inRoomReward = APICore.getPickupService().getInRoomReward(roomName, list);
                        if (inRoomReward != null) {
                            JSONObject finalResponse = new JSONObject();

                            CompletableFuture<String> resultFuture = new CompletableFuture<>();

                            List<ApiFuture<String>> rewardFutures = APICore.getRewardManager().saveReward(document, inRoomReward, finalResponse);
                            ApiFutures.addCallback(ApiFutures.allAsList(rewardFutures), new ApiFutureCallback<List<String>>() {
                                @Override
                                public void onFailure(Throwable t) {
                                    APICore.getLogger().severe("An error was thrown while attempting to add a new in-room reward for a user!");

                                    t.printStackTrace();
                                    Sentry.capture(t);

                                    ctx.status(500);

                                    resultFuture.completeExceptionally(t);
                                }

                                @Override
                                public void onSuccess(List<String> results) {
                                    for (String result : results) {
                                        APICore.getLogger().info("Successfully completed a transaction while adding a new in-room reward for user " + uid + ": Result - (" + result + ")");
                                    }

                                    try {
                                        ApiFuture<DocumentSnapshot> future = docRef.get();
                                        DocumentSnapshot document = future.get();
                                        Map<String, Object> assetsMap = (Map<String, Object>) document.get("assets");

                                        resultFuture.complete(generateRoomResponse(finalResponse, assetsMap).toString());
                                    } catch (InterruptedException | ExecutionException e) {
                                        APICore.getLogger().severe("An error was thrown while attempting to add a new in-room reward for a user (failed to wait for new future)!");

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
                } else {
                    ctx.result(generateRoomResponse(null, null).toString());
                }
            } else {
                APICore.getLogger().severe(uid + " - (document.exists() == false) (Room)");

                Sentry.getContext().addExtra("halted", true);
                Sentry.capture("(document.exists() == false) (Room)");

                ctx.status(500);
            }
        }
    }

    private boolean verifyResponse(FirebaseToken token, String swidParam, long goodUntilParam, String signatureParam) throws Exception {
        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> requestEarnedRewardsData = redis.get(token.getName() + "-earnedRewardData");

        JsonObject object = new JsonParser().parse(requestEarnedRewardsData.get()).getAsJsonObject();
        String earnedRewardDataRedis = object.getAsJsonObject("data").toString();
        long goodUntilRedis = object.get("goodUntil").getAsLong();
        String signatureRedis = object.get("signature").getAsString();
        String swidRedis = object.get("swid").getAsString();

        byte[] hash = Utilities.getSignatureGenerator().hashString(earnedRewardDataRedis, StandardCharsets.UTF_8).asBytes();
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

    private JSONObject generateRoomResponse(JSONObject wsEvents, Map<String, Object> assets) {
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
