package me.legit.api.game.game;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.api.game.quest.Progress;
import me.legit.models.quest.Quest;
import me.legit.punishment.PunishmentHistory;
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
import java.util.Date;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Worlds implements Handler {

    private static Gson gson = new GsonBuilder().serializeNulls().create();

    public Worlds() {
        //TODO
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Worlds)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();
        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            String roomName = ctx.pathParam("room");

            String clientVersion = ctx.header("X-CP-Client-Version");
            String contentVersion = ctx.header("X-CP-Content-Version");

            JSONObject documentData = new JSONObject(document.getData());
            JSONObject data = generateDataForResponse(document, decodedToken, roomName, documentData, clientVersion, contentVersion);

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
            joinRoomDataStorage.thenAccept(string -> APICore.getLogger().info("Successfully stored join room data for user " + uid + " in Redis!"));

            JSONObject onlineLocation = new JSONObject();
            onlineLocation.put("world", "CPI");
            onlineLocation.put("language", 1);
            JSONObject zoneIdLocation = new JSONObject();
            zoneIdLocation.put("name", roomName);
            zoneIdLocation.put("instanceId", "");
            onlineLocation.put("zoneId", zoneIdLocation);
            LocalDate today = LocalDate.now(ZoneId.of("GMT"));
            String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
            onlineLocation.put("contentIdentifier", clientVersion + ";" + contentVersion + ";" + todayStr + ";NONE");
            onlineLocation.put("room", roomName);

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

    private JSONObject generateDataForResponse(DocumentSnapshot snapshot, FirebaseToken token, String room, JSONObject documentData, String clientVersion, String contentVersion) {
        JSONObject data = new JSONObject();

        data.put("swid", token.getUid());
        data.put("host", "localhost");
        data.put("tcpPort", 12023);
        data.put("httpsPort", 8378);
        LocalDate today = LocalDate.now(ZoneId.of("GMT"));
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        data.put("room", "CPI:en:" + room + "::" + clientVersion + ";" + contentVersion + ";" + todayStr + ";NONE");

        //TODO store session id in Redis? Might be needed for other APIs in the future
        data.put("sessionId", Math.abs(new BigInteger(49, ThreadLocalRandom.current()).longValue()));

        data.put("playerRoomData", generatePlayerRoomData(documentData, snapshot));

        data.put("earnedRewards", documentData.getJSONObject("earnedRewards").getJSONObject(room));
        data.put("userName", documentData.getJSONObject("name").getString("displayName"));
        data.put("membershipRights", documentData.getJSONObject("membershipRights"));
        data.put("selectedTubeId", documentData.getInt("selectedTubeId"));
        data.put("extraLayoutData", JSONObject.NULL);

        data.put("roomOwnerName", JSONObject.NULL);
        data.put("roomOwner", false);

        return data;
    }

    static JSONObject generatePlayerRoomData(JSONObject data, DocumentSnapshot snapshot) {
        JSONObject playerRoomData = new JSONObject();

        JSONObject parts = new JSONObject();
        parts.put("parts", data.getJSONArray("outfit"));
        playerRoomData.put("outfit", parts);

        String firstQuestData = data.getJSONArray("quests").toString();
        List<Quest> quests = Progress.generateQuestStateResponseBySnapshot(firstQuestData, snapshot, true);
        String finalQuestData = gson.toJson(quests);
        playerRoomData.put("quests", new JSONArray(finalQuestData));

        playerRoomData.put("consumableInventory", data.getJSONObject("consumableInventory"));
        playerRoomData.put("assets", data.getJSONObject("assets"));
        playerRoomData.put("dailyTaskProgress", data.getJSONArray("dailyTaskProgress"));

        JSONObject profile = data.getJSONObject("profile");
        profile.put("daysOld", Utilities.getDateDifference(snapshot.getCreateTime().toDate(), new Date(), TimeUnit.DAYS));
        playerRoomData.put("profile", profile);

        playerRoomData.put("member", true);

        PunishmentHistory history = APICore.getPunishmentManager().getPunishmentHistory(snapshot.getId());
        if (history.isMuted()) {
            playerRoomData.put("muted", true);
        } else {
            playerRoomData.put("muted", false);
        }

        playerRoomData.put("verified", data.getBoolean("verified"));

        return playerRoomData;
    }
}
