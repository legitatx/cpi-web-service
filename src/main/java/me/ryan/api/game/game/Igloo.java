package me.legit.api.game.game;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.*;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.models.igloo.SceneLayout;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class Igloo implements Handler {

    private Gson gson;

    public Igloo() {
        this.gson = new GsonBuilder().serializeNulls().create();
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Igloo)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();
        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            String clientVersion = ctx.header("X-CP-Client-Version");
            String contentVersion = ctx.header("X-CP-Content-Version");

            LocalDate today = LocalDate.now(ZoneId.of("GMT"));
            String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);

            JsonParser parser = new JsonParser();
            JsonObject iglooZone = parser.parse(ctx.body()).getAsJsonObject();

            String iglooZoneName = iglooZone.get("name").getAsString();
            String iglooOwnerId = iglooZone.get("instanceId").getAsString();

            DocumentSnapshot iglooOwnerDoc = db.collection("igloos").document(iglooOwnerId).get().get();
            DocumentSnapshot iglooOwnerUserDoc = db.collection("users").document(iglooOwnerId).get().get();

            JSONObject documentData = new JSONObject(document.getData());
            JSONObject data = generateDataForResponse(document, decodedToken, iglooZoneName, iglooOwnerId, documentData, clientVersion, contentVersion, todayStr);

            JSONObject activeLayout;
            if (iglooOwnerDoc.get("activeLayout") != null) {
                activeLayout = new JSONObject((Map<String, Object>) iglooOwnerDoc.get("activeLayout"));
                activeLayout.put("createdDate", activeLayout.getLong("createdDate"));
                activeLayout.put("lastModifiedDate", activeLayout.getLong("lastModifiedDate"));
                activeLayout.put("layoutId", Long.valueOf(activeLayout.getString("layoutId")));

                SceneLayout sceneLayout = gson.fromJson(activeLayout.toString(), SceneLayout.class);
                activeLayout.put("activeLayout", new JSONObject(gson.toJson(sceneLayout)));
            } else {
                activeLayout = new JSONObject();
                activeLayout.put("zoneId", "DefaultIgloo");
                activeLayout.put("name", "");
                activeLayout.put("lightingId", 0);
                activeLayout.put("musicId", 0);
                activeLayout.put("decorationsLayout", new JSONArray());
                activeLayout.put("extraInfo", new JSONObject());
                activeLayout.put("createdDate", JSONObject.NULL);
                activeLayout.put("lastModifiedDate", JSONObject.NULL);
                activeLayout.put("memberOnly", false);
            }

            data.put("extraLayoutData", activeLayout);

            data.put("roomOwnerName", new JSONObject((Map<String, Object>) iglooOwnerUserDoc.get("name")).getString("displayName"));

            JSONObject iglooZoneId = new JSONObject((Map<String, Object>) document.get("zoneId"));
            if (iglooZoneId.getString("instanceId").equals(iglooOwnerId)) { //TODO && iglooZoneId.getString("name").equals(iglooZoneName)) {
                data.put("roomOwner", true);
            } else {
                data.put("roomOwner", false);
            }

            JSONObject finalResponse = new JSONObject();
            finalResponse.put("data", data);

            finalResponse.put("swid", uid);

            LocalDateTime time = LocalDateTime.now().plusMinutes(5);
            ZoneId zoneId = ZoneId.systemDefault();
            long epoch = time.atZone(zoneId).toInstant().toEpochMilli();
            finalResponse.put("goodUntil", epoch);

            byte[] hash = Utilities.getSignatureGenerator().hashString(data.toString(), StandardCharsets.UTF_8).asBytes();
            finalResponse.put("signature", Base64.getEncoder().encodeToString(hash));

            RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
            RedisFuture<String> joinRoomDataStorage = redis.set(decodedToken.getName() + "-joinRoomData", finalResponse.toString());
            joinRoomDataStorage.thenAccept(string -> APICore.getLogger().info("Successfully stored join room data (igloo) for user " + uid + " in Redis!"));

            JSONObject onlineLocation = new JSONObject();
            onlineLocation.put("world", "Igloo");
            onlineLocation.put("language", 1);
            JSONObject zoneIdLocation = new JSONObject();
            zoneIdLocation.put("name", iglooZoneName);
            zoneIdLocation.put("instanceId", iglooOwnerId);
            onlineLocation.put("zoneId", zoneIdLocation);
            onlineLocation.put("contentIdentifier", clientVersion + ";" + contentVersion + ";" + todayStr + ";NONE");
            onlineLocation.put("room", iglooZoneName);

            ApiFuture<WriteResult> updateOnlineLocation = docRef.update("onlineLocation", onlineLocation.toMap());
            APICore.getLogger().info("Updated online location for " + uid + " at: " + updateOnlineLocation.get().getUpdateTime());

            ApiFuture<WriteResult> updateSessionId = docRef.update("currentSessionId", data.getLong("sessionId"));
            APICore.getLogger().info("Updated session ID for " + uid + " at: " + updateSessionId.get().getUpdateTime());

            ctx.result(finalResponse.toString());
        } else {
            APICore.getLogger().severe("Somehow failed to find a document for user with ID " + uid + "!");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("Somehow failed to find a document for user with ID!");

            // return halt(714); // should return player not found in client???
            ctx.status(500); // dont want to take chances for now with weird error responses, just return 500 for now, will verify the 714 error code later
        }
    }

    private JSONObject generateDataForResponse(DocumentSnapshot snapshot, FirebaseToken token, String iglooRoom, String instanceId, JSONObject documentData, String clientVersion, String contentVersion, String subContentVersion) {
        JSONObject data = new JSONObject();

        data.put("swid", token.getUid());
        data.put("host", "localhost");
        data.put("tcpPort", 12023);
        data.put("httpsPort", 8378);
        data.put("room", "Igloo:en:" + iglooRoom + ":" + instanceId + ":" + clientVersion + ";" + contentVersion + ";" + subContentVersion + ";NONE");

        //TODO store session id in Redis? Might be needed for other APIs in the future
        byte[] array = new byte[8];
        ThreadLocalRandom.current().nextBytes(array);
        data.put("sessionId", Math.abs(new BigInteger(49, ThreadLocalRandom.current()).longValue()));

        data.put("playerRoomData", Worlds.generatePlayerRoomData(documentData, snapshot));

        data.put("earnedRewards", new JSONObject());
        data.put("userName", token.getName());
        data.put("membershipRights", documentData.getJSONObject("membershipRights"));
        data.put("selectedTubeId", documentData.getInt("selectedTubeId"));

        return data;
    }
}
