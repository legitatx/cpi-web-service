package me.legit.api.auth.mix;

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
import me.legit.utils.CPIEncryptor;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.Base64;

// TODO not fully implemented yet
public class MixSessionDelete implements Handler {

    private JsonParser parser;

    public MixSessionDelete() {
        this.parser = new JsonParser();
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String accessToken = ctx.header("X-Mix-OneIdToken");

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(accessToken, checkRevoked).get();
        String uid = decodedToken.getUid();

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (MixSessionDelete)");

        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(1).async();
        RedisFuture<String> requestSessionKey = redis.get(uid);
        JsonObject keyObject = parser.parse(requestSessionKey.get()).getAsJsonObject();
        String key = keyObject.get("key").getAsString();

        CPIEncryptor encryptor = new CPIEncryptor(Base64.getDecoder().decode(key));
//        String decryptedBody = new String(encryptor.decrypt(ctx.bodyAsBytes()));

        // {"UserId":"{0E6D4D7F-9207-4022-9838-A1277E6DB82D}","Timestamp":1540618609249}

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        ctx.contentType("application/json");

//        JsonObject object = parser.parse(decryptedBody).getAsJsonObject();
//        String userId = object.get("UserId").getAsString();
//        long timestamp = object.get("Timestamp").getAsLong();
//
//        APICore.getLogger().info("MixSessionDelete - " + timestamp + " - " + userId);

        ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateResponse().toString().getBytes())));
    }

    private JSONObject generateSessionError() {
        JSONObject error = new JSONObject();
        error.put("Status", "UNAUTHORIZED_ONEID_TOKEN");
        return error;
    }

    private JSONObject generateResponse() {
        JSONObject response = new JSONObject();
        response.put("Status", "OK");
        return response;
    }
}
