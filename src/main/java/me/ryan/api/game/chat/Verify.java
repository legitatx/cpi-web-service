package me.legit.api.game.chat;

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
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;

public class Verify implements Handler {

    private static JsonParser parser = new JsonParser();

    public Verify() {
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Verify)");

        String keyId = ctx.header("X-Encryption-Key-Id");
        if (keyId != null) {
            String realIpAddress = ctx.header("CF-Connecting-IP");

            RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
            RedisFuture<String> requestChatEncryptionKey = redis.get("ChatEncryptionKey:" + realIpAddress + ":" + keyId);
            JsonObject keyObject = parser.parse(requestChatEncryptionKey.get()).getAsJsonObject();
            String key = keyObject.get("key").getAsString();

            CPIEncryptor encryptor = new CPIEncryptor(Base64.getDecoder().decode(key));
            String decryptedBody = new String(encryptor.decrypt(ctx.bodyAsBytes()));

                /*
                    [CPI PACKET DUMP] [HTTP REQUEST - BEFORE ENCRYPTION]: {"senderSessionId":2086962657,"message":"Come to Coconut Cove","emotion":0,"moderated":false,"questId":null,"objective":null}
                    [CPI PACKET DUMP] [HTTP RESPONSE - DECRYPTED ENCRYPTION]: {"goodUntil":1543386936615,"swid":"{48737DD3-51C5-4C7C-839D-0DDB44F1017A}","data":{"senderSessionId":2086962657,"message":"Come to Coconut Cove","emotion":0,"moderated":false},"signature":"nCR1mlbHMO/CcU7o6H2Y2+RI1c0="}
                 */

            JsonObject object = new JsonParser().parse(decryptedBody).getAsJsonObject();
            long senderSessionId = object.get("senderSessionId").getAsLong();
            String message = object.get("message").getAsString();
            int emotion = object.get("emotion").getAsInt();

            //TODO Quest ID + objective? Apparently this is used in chat? What?...

            boolean messageModerated = APICore.getChatFilter().isMessageDisallowed(message);
            JSONObject data = new JSONObject();
            if (messageModerated) {
                data.put("senderSessionId", senderSessionId);
                data.put("message", "---");
                data.put("emotion", emotion);
                data.put("moderated", true);

                JSONObject finalResponse = generateVerifyResponse(data, uid);
                RedisFuture<String> chatData = redis.set(decodedToken.getName() + "-chatData", finalResponse.toString());
                chatData.thenAccept(string -> APICore.getLogger().info("Successfully set new chat data for user " + uid + " in Redis (moderated message)!"));

                String rawResponse = finalResponse.toString();

                ctx.result(new ByteArrayInputStream(encryptor.encrypt(rawResponse.getBytes())));
            } else {
                data.put("senderSessionId", senderSessionId);
                data.put("message", message);
                data.put("emotion", emotion);
                data.put("moderated", false);

                JSONObject finalResponse = generateVerifyResponse(data, uid);
                RedisFuture<String> chatData = redis.set(decodedToken.getName() + "-chatData", finalResponse.toString());
                chatData.thenAccept(string -> APICore.getLogger().info("Successfully set new chat data for user " + uid + " in Redis!"));

                String rawResponse = finalResponse.toString();

                ctx.result(new ByteArrayInputStream(encryptor.encrypt(rawResponse.getBytes())));
            }
        } else {
            APICore.getLogger().info("X-Encryption-Key-Id == null, is user attempting to screw around with the server? (UID: " + uid + ") (Verify)");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("X-Encryption-Key-Id == null, is user attempting to screw around with the server? (Verify)");

            ctx.status(400);
            ctx.result("{\"message\":\"Invalid URI parameter(s)\",\"code\":10}");
        }
    }

    @SuppressWarnings("Duplicates")
    private JSONObject generateVerifyResponse(JSONObject data, String uid) {
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
