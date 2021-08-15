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
import me.legit.punishment.PunishmentData;
import me.legit.punishment.PunishmentHistory;
import me.legit.punishment.PunishmentType;
import me.legit.utils.RSAHelper;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.PublicKey;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class MixSession implements Handler {

    private JsonParser parser;

    public MixSession() {
        this.parser = new JsonParser();
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String accessToken = ctx.header("X-Mix-OneIdToken");

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(accessToken, checkRevoked).get();
        String uid = decodedToken.getUid();

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (MixSession)");

        ctx.contentType("application/json");

        PunishmentHistory history = APICore.getPunishmentManager().getPunishmentHistory(uid);
        if (history != null) {
            JSONObject responseObj = new JSONObject();
            if (history.getTotalBans() >= 3) {
                responseObj.put("Status", "UNAUTHORIZED_BANNED");
                responseObj.put("Type", "Permanent");
                ctx.result(responseObj.toString());
                return;
            } else if (history.getTotalBans() < 3) {
                List<PunishmentData> punishments = history.getPunishments();
                if (punishments != null && punishments.size() > 0) {
                    for (PunishmentData data : punishments) {
                        if (data.getType().equals(PunishmentType.BAN)) {
                            LocalDateTime nowTime = LocalDateTime.now(ZoneOffset.UTC);
                            LocalDateTime expireTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(data.getExpiresIn()), ZoneOffset.UTC);
                            if (nowTime.isBefore(expireTime)) {
                                responseObj.put("Status", "UNAUTHORIZED_BANNED");
                                responseObj.put("Type", "Temporary");
                                responseObj.put("ExpirationDate", expireTime.toString());

                                ctx.result(responseObj.toString());
                                return;
                            }
                        }
                    }
                }
            }
        }

        JsonObject object = parser.parse(ctx.body()).getAsJsonObject();
        String publicKeyModulus = object.get("PublicKeyModulus").getAsString();
        String publicKeyExponent = object.get("PublicKeyExponent").getAsString();
        String userId = object.get("UserId").getAsString();

        byte[] modulus = Base64.getDecoder().decode(publicKeyModulus);
        byte[] exponent = Base64.getDecoder().decode(publicKeyExponent);
        PublicKey key = RSAHelper.getPublicKey(modulus, exponent);

        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey aesSecret = generator.generateKey();

        byte[] encryptedCipher = APICore.getRsaHelper().encryptBytes(aesSecret.getEncoded(), key);

        String encodedKey = Base64.getEncoder().encodeToString(encryptedCipher);

        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(1).async();
        RedisFuture<String> sessionKey = redis.set(uid, new JSONObject().put("key", Base64.getEncoder().encodeToString(aesSecret.getEncoded())).put("pubKeyMod", publicKeyModulus).put("pubKeyExp", publicKeyExponent).toString());
        sessionKey.thenAccept(string -> APICore.getLogger().info("Successfully set new session key data for user " + uid + " in Redis!"));

        JSONObject responseObj = new JSONObject();
        responseObj.put("Status", "OK");
        responseObj.put("EncryptedSymmetricKey", encodedKey);
        responseObj.put("SessionId", ThreadLocalRandom.current().nextLong());
        responseObj.put("HashedUserId", Base64.getEncoder().encodeToString(userId.getBytes()));

        ctx.result(responseObj.toString());
    }
}

            /*
            {
                "PublicKeyModulus": "rbciDcMw592z5ENJozZ/1yZcYzJBpaWfZyo5A0mm8m3iLpBQohBHBUf6NkpMWbKEFfcDo0fd6KD57bnZuep0B+Eg83msGEm4VVnUkvLdZnCXu5XLvNQpSDlofMXQmACAJ1gbqSqDkNQyNRr5SZBjbFbSVIqARRw5q/Te2DQ7pVE=",
                "PublicKeyExponent": "EQ==",
                "SessionGroupId": 1135402822,
                "ProtocolVersion": 3,
                "UserId": "{2BDA9B86-9424-48A2-B98C-83EEAED4F899}",
                "Timestamp": null
            }
             */

//byte[] ciphertext = Convert.FromBase64String(response.EncryptedSymmetricKey);
//byte[] symmetricKey = this.rsaEncryptor.Decrypt(ciphertext, rsaParameters);
