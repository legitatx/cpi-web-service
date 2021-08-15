package me.legit.api.game.minigame;

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

public class FishingCast implements Handler {

    public FishingCast() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");

        JsonObject object = new JsonParser().parse(ctx.body()).getAsJsonObject();
        long goodUntil = object.get("goodUntil").getAsLong();
        String signature = object.get("signature").getAsString();
        String swid = object.get("swid").getAsString();

        String encodedToken = ctx.header("Authorization").split("Basic ")[1].split(", GAE")[0];
        String token = new String(Base64.getDecoder().decode(encodedToken)).split(":")[0];

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(token, checkRevoked).get();
        String uid = decodedToken.getUid();

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (FishingCast)");

        if (!verifyResponse(decodedToken, swid, goodUntil, signature)) {
            Sentry.capture("(Failed to verify response) (FishingCast)");
            ctx.status(400);
            ctx.result("{\"message\":\"Invalid URI parameter(s)\",\"code\":10}");
        } else {
            Map<String, String> fishingPrizes = APICore.getMinigameService().getRandomFishingPrizes();

            JSONObject castResponse = generateCastResponse(decodedToken, new JSONObject(fishingPrizes));
            persistSignedResponse(decodedToken, castResponse);

            ctx.result(castResponse.toString());
        }
    }

    private JSONObject generateCastResponse(FirebaseToken token, JSONObject fishingData) {
        JSONObject finalResponse = new JSONObject();

        LocalDateTime time = LocalDateTime.now().plusMinutes(5);
        ZoneId zoneId = ZoneId.systemDefault();
        long epoch = time.atZone(zoneId).toInstant().toEpochMilli();
        finalResponse.put("goodUntil", epoch);

        finalResponse.put("swid", token.getUid());

        JSONObject availablePrizeMap = new JSONObject().put("availablePrizeMap", fishingData);
        finalResponse.put("data", availablePrizeMap);

        byte[] hash = Utilities.getSignatureGenerator().hashString(availablePrizeMap.toString(), StandardCharsets.UTF_8).asBytes();
        finalResponse.put("signature", Base64.getEncoder().encodeToString(hash));

        return finalResponse;
    }

    private void persistSignedResponse(FirebaseToken token, JSONObject data) {
        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> levelUpStorage = redis.set(token.getName() + "-fishingCastData", data.toString());
        levelUpStorage.thenAccept(string -> APICore.getLogger().info("Successfully stored fishing cast data for user " + token.getName() + " (" + token.getUid() + ") in Redis!"));
    }

    private boolean verifyResponse(FirebaseToken token, String swidParam, long goodUntilParam, String signatureParam) throws Exception {
        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> validateStateData = redis.get(token.getName() + "-validateStateData");

        JsonObject object = new JsonParser().parse(validateStateData.get()).getAsJsonObject();

        JsonObject validatedStateDataObj = object.getAsJsonObject("data");
        if (!(validatedStateDataObj.get("equippedItem").getAsString().equals("FishingRodGame"))) {
            return false;
        }

        JsonObject locationObj = validatedStateDataObj.getAsJsonObject("location");
        if (locationObj.get("language").getAsInt() != 1 || !(locationObj.getAsJsonObject("zoneId").get("name").getAsString().equals("Boardwalk")) || !(locationObj.get("room").getAsString().equals("Boardwalk"))) {
            return false;
        }

//        JsonObject positionObj = validatedStateDataObj.getAsJsonObject("position");
//        if (positionObj.get("x").getAsFloat() != 9.037003 || positionObj.get("y").getAsFloat() != -0.07652587 || positionObj.get("z").getAsFloat() != 12.832534) {
//            return false;
//        }

        String validatedStateData = validatedStateDataObj.toString();
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
}
