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
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.utils.CPIEncryptor;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.util.Base64;

public class MixModerationText implements Handler {

    private JsonParser parser;

    public MixModerationText() {
        this.parser = new JsonParser();
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String accessToken = ctx.header("X-Mix-OneIdToken");

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(accessToken, checkRevoked).get();
        String uid = decodedToken.getUid();

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (MixModerationText)");

        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(1).async();
        RedisFuture<String> requestSessionKey = redis.get(uid);
        JsonObject keyObject = parser.parse(requestSessionKey.get()).getAsJsonObject();
        String key = keyObject.get("key").getAsString();

        CPIEncryptor encryptor = new CPIEncryptor(Base64.getDecoder().decode(key));
        String decryptedBody = new String(encryptor.decrypt(ctx.bodyAsBytes()));

        // {"Text":"jeffstu123","Language":null,"ChatThreadId":null,"ModerationPolicy":"DisplayName","UserId":"{CA6A0C3C-A088-487D-86B3-EAF9475CFA9D}","Timestamp":1539287051582}

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        ctx.contentType("application/json");

        JsonObject object = parser.parse(decryptedBody).getAsJsonObject();
        String text = object.get("Text").getAsString();
        String moderationPolicy = object.get("ModerationPolicy").getAsString();
        String userId = object.get("UserId").getAsString();

        APICore.getLogger().info("MixModerationText - " + text + " - " + moderationPolicy + " - " + userId);

        boolean disallowed = APICore.getChatFilter().isMessageDisallowed(text);
        if (disallowed) {
            Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().withData("error", text).build());
            Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().withData("displayName", text).build());
            Sentry.capture("A username was marked as a swear when trying to set a new Mix display name!");

            ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateResponse("---", true).toString().getBytes())));
        } else {
            ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateResponse(text, false).toString().getBytes())));
        }
    }

    private JSONObject generateSessionError() {
        JSONObject error = new JSONObject();
        error.put("Status", "UNAUTHORIZED_ONEID_TOKEN");
        return error;
    }

    private JSONObject generateResponse(String text, boolean success) {
        JSONObject response = new JSONObject();
        response.put("Status", "OK");
        response.put("Text", text);
        response.put("Moderated", success);
        return response;
    }
}
