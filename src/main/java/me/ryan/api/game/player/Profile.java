package me.legit.api.game.player;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
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

@SuppressWarnings("Duplicates")
public class Profile implements Handler {

    public Profile() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");

        JsonObject object = new JsonParser().parse(ctx.body()).getAsJsonObject();
        int colour = object.get("colour").getAsInt();
        int daysOld = object.get("daysOld").getAsInt();

        String encodedToken = ctx.header("Authorization").split("Basic ")[1].split(", GAE")[0];
        String token = new String(Base64.getDecoder().decode(encodedToken)).split(":")[0];

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(token, checkRevoked).get();
        String uid = decodedToken.getUid();

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Profile)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            ApiFuture<WriteResult> updateColour = docRef.update("profile.colour", colour);
            APICore.getLogger().info("Updated colour at: " + updateColour.get().getUpdateTime());
        }
        JSONObject profileData = generateProfileResponse(new JSONObject().put("colour", colour).put("daysOld", daysOld), uid);
        persistSignedResponse(profileData, decodedToken);

        ctx.result(profileData.toString());
    }

    private JSONObject generateProfileResponse(JSONObject data, String uid) {
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

    private void persistSignedResponse(JSONObject data, FirebaseToken token) {
        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> profileStorage = redis.set(token.getName() + "-profileUpdate", data.toString());
        profileStorage.thenAccept(string -> APICore.getLogger().info("Successfully stored profile update data for user " + token.getName() + " (" + token.getUid() + ") in Redis!"));
    }
}
