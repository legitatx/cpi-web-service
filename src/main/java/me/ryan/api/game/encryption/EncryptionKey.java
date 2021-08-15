package me.legit.api.game.encryption;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import me.legit.APICore;
import me.legit.utils.RSAHelper;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.security.PublicKey;
import java.util.Base64;
import java.util.UUID;

public class EncryptionKey implements Handler {

    public EncryptionKey() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");

        String realIpAddress = ctx.header("CF-Connecting-IP");

        JsonObject object = new JsonParser().parse(ctx.body()).getAsJsonObject();
        String exponentAsString = object.get("exponent").getAsString();
        String modulusAsString = object.get("modulus").getAsString();

//            String encodedToken = request.headers("Authorization").split("Basic ")[1].split(", GAE")[0];
//            String token = new String(Base64.getDecoder().decode(encodedToken)).split(":")[0];
//
//            boolean checkRevoked = true;
//            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(token, checkRevoked);
//            String uid = decodedToken.getUid();
//
//            Sentry.getContext().setUser(new UserBuilder().setId(uid).build());
//
//            APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (EncryptionKey)");

        byte[] modulus = Base64.getDecoder().decode(modulusAsString);
        byte[] exponent = Base64.getDecoder().decode(exponentAsString);
        PublicKey key = RSAHelper.getPublicKey(modulus, exponent);

        KeyGenerator generator = KeyGenerator.getInstance("AES");
        generator.init(256);
        SecretKey aesSecret = generator.generateKey();

        byte[] encryptedCipher = APICore.getRsaHelper().encryptBytes(aesSecret.getEncoded(), key);

        UUID keyId = UUID.randomUUID();
        String encryptedSymmetricKey = Base64.getEncoder().encodeToString(encryptedCipher);

        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> chatEncryptionKey = redis.set("ChatEncryptionKey:" + realIpAddress + ":" + keyId.toString(), new JSONObject().put("key", Base64.getEncoder().encodeToString(aesSecret.getEncoded())).toString());
        chatEncryptionKey.thenAccept(string -> APICore.getLogger().info("Successfully set new chat encryption key data for user with IP " + ctx.ip() + " in Redis! (Key ID: " + keyId.toString() + ")"));

        JSONObject responseData = new JSONObject();
        responseData.put("keyId", keyId.toString());
        responseData.put("encryptedSymmetricKey", encryptedSymmetricKey);

        ctx.result(responseData.toString());
    }
}
