package me.legit.api.auth.mix;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
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
import java.util.Collections;
import java.util.List;

public class MixValidateName implements Handler {

    private JsonParser parser;

    public MixValidateName() {
        this.parser = new JsonParser();
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String accessToken = ctx.header("X-Mix-OneIdToken");

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(accessToken, checkRevoked).get();
        String uid = decodedToken.getUid();

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (MixValidateName)");

        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(1).async();
        RedisFuture<String> requestSessionKey = redis.get(uid);
        JsonObject keyObject = parser.parse(requestSessionKey.get()).getAsJsonObject();
        String key = keyObject.get("key").getAsString();

        CPIEncryptor encryptor = new CPIEncryptor(Base64.getDecoder().decode(key));
        String decryptedBody = new String(encryptor.decrypt(ctx.bodyAsBytes()));

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        ctx.contentType("application/json");

        JsonObject object = parser.parse(decryptedBody).getAsJsonObject();
        String displayName = object.get("DisplayName").getAsString();
        String userId = object.get("UserId").getAsString();
        long timestamp = object.get("Timestamp").getAsLong();

        APICore.getLogger().info("MixValidateName - " + displayName + " - " + timestamp + " - " + userId);

        boolean disallowed = APICore.getChatFilter().isMessageDisallowed(displayName);
        if (disallowed) {
            ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateResponse("INVALID").toString().getBytes())));
        } else {
            ApiFuture<QuerySnapshot> future = APICore.getDatabaseManager().firebase().getFirestore().collection("users").whereEqualTo("name.proposedDisplayName", displayName).get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            if (documents.size() > 0) {
                ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateResponse("IN_USE").toString().getBytes())));
            } else {
                ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateResponse("VALID").toString().getBytes())));
            }
        }
    }

    private JSONObject generateSessionError() {
        JSONObject error = new JSONObject();
        error.put("Status", "UNAUTHORIZED_ONEID_TOKEN");
        return error;
    }

    private JSONObject generateResponse(String status) {
        JSONObject response = new JSONObject();
        response.put("Status", "OK");
        response.put("DisplayNameStatus", status);
        response.put("DisplayNames", Collections.emptyList());
        return response;
    }
}
