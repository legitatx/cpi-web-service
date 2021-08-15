package me.legit.api.auth.mix;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.sentry.Sentry;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.utils.CPIEncryptor;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;

public class MixDisplayName implements Handler {

    //TODO - performance tests on Mix endpoints
    
    private JsonParser parser;

    public MixDisplayName() {
        this.parser = new JsonParser();
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String accessToken = ctx.header("X-Mix-OneIdToken");

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(accessToken, checkRevoked).get();
        String uid = decodedToken.getUid();

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (MixDisplayName)");

        RedisAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(1).async();
        RedisFuture<String> requestSessionKey = redis.get(uid);
        JsonObject keyObject = parser.parse(requestSessionKey.get()).getAsJsonObject();
        String key = keyObject.get("key").getAsString();

        CPIEncryptor encryptor = new CPIEncryptor(Base64.getDecoder().decode(key));
        String decryptedBody = new String(encryptor.decrypt(ctx.bodyAsBytes()));

        // {"DisplayName":"Joesh34","Language":null,"UserId":"{0E6D4D7F-9207-4022-9838-A1277E6DB82D}","Timestamp":1540618508129}

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        JsonObject object = parser.parse(decryptedBody).getAsJsonObject();
        String displayName = object.get("DisplayName").getAsString();
        String userId = object.get("UserId").getAsString();
        long timestamp = object.get("Timestamp").getAsLong();

        APICore.getLogger().info("MixDisplayName - " + displayName + " - " + timestamp + " - " + userId);

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        ctx.contentType("application/json");

        boolean disallowed = APICore.getChatFilter().isMessageDisallowed(displayName);
        if (disallowed) {
            JSONObject error = generateModeratedResponse();

            Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().withData("error", error.toString()).build());
            Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().withData("displayName", displayName).build());
            Sentry.capture("A username was marked as a swear when trying to set a new Mix display name!");

            ctx.status(400);
            ctx.result(new ByteArrayInputStream(encryptor.encrypt(error.toString().getBytes())));
        } else {
            ApiFuture<QuerySnapshot> future = db.collection("users").whereEqualTo("name.proposedDisplayName", displayName).get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            if (documents.size() > 0) {
                JSONObject error = generateExistsResponse();

                Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().withData("error", error.toString()).build());
                Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().withData("displayName", displayName).build());
                Sentry.capture("A username already existed in the system while attempting to set a new Mix display name!");

                ctx.status(400);
                ctx.result(new ByteArrayInputStream(encryptor.encrypt(error.toString().getBytes())));
            } else {
                ApiFuture<WriteResult> updateDisplayName = db.collection("users").document(uid).update(
                        "name.proposedDisplayName", displayName,
                        "name.proposedStatus", "PENDING",
                        "name.moderatedStatusDate", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().toString()
                );
                APICore.getLogger().info("Updated display name for user " + uid + " to " + displayName + " at: " + updateDisplayName.get().getUpdateTime());
                RedisFuture<Long> displayNameUpdate = redis.publish("displayNameUpdate", new JSONObject().put("uid", uid).put("displayName", displayName).toString());
                APICore.getLogger().info("Published display name update to Redis for user " + uid + " at: " + displayNameUpdate.get());
                ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateResponse(displayName).toString().getBytes())));
            }
        }
    }

    private JSONObject generateSessionError() {
        JSONObject error = new JSONObject();
        error.put("Status", "UNAUTHORIZED_ONEID_TOKEN");
        return error;
    }

    private JSONObject generateResponse(String name) {
        JSONObject response = new JSONObject();
        response.put("Status", "OK");
        response.put("DisplayName", name);
        return response;
    }

    private JSONObject generateExistsResponse() {
        JSONObject response = new JSONObject();
        response.put("Status", "DISPLAYNAME_ASSIGNMENT_FAILED");
        response.put("Message", "This username is already taken. Please try using a different name.");
        return response;
    }

    private JSONObject generateModeratedResponse() {
        JSONObject response = new JSONObject();
        response.put("Status", "DISPLAYNAME_MODERATION_FAILED");
        response.put("Message", "This username is not allowed. Please try using a different name.");
        return response;
    }
}
