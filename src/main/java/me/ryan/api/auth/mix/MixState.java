package me.legit.api.auth.mix;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
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
import me.legit.utils.CPIEncryptor;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Collections;
import java.util.List;

public class MixState implements Handler {

    public MixState() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String accessToken = ctx.header("X-Mix-OneIdToken");

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(accessToken, checkRevoked).get();
        String uid = decodedToken.getUid();

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (MixState)");

        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(1).async();
        RedisFuture<String> requestSessionKey = redis.get(uid);
        JsonObject keyObject = new JsonParser().parse(requestSessionKey.get()).getAsJsonObject();
        String key = keyObject.get("key").getAsString();

        CPIEncryptor encryptor = new CPIEncryptor(Base64.getDecoder().decode(key));

        // {"ClientVersion":null,"UserId":"{4A562E6B-40F8-4C3C-8913-35D2E69C22F4}","Timestamp":null}

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

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

        DocumentReference docRef = db.collection("mixData").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            JSONObject mixData = new JSONObject(document.getData());
            mixData.put("Status", "OK");
            mixData.put("PollIntervals", new JSONArray().put(5).put(10).put(30));
            mixData.put("PokeIntervals", new JSONArray().put(30));
            mixData.put("Timestamp", LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
            mixData.put("NotificationSequenceThreshold", 7000);
            mixData.put("NotificationIntervalsJitter", 500);

            ctx.result(new ByteArrayInputStream(encryptor.encrypt(mixData.toString().getBytes())));
        } else {
            APICore.getLogger().severe("Somehow failed to find a mixData document for user with ID " + uid + "! This should never happen.");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("Somehow failed to find a mixData document for user with ID! This should never happen.");

            //return halt(714); should return player not found in client???
            ctx.status(500); // dont want to take chances for now with weird error responses, just return 500 for now, will verify the 714 error code later
        }
    }

    private JSONObject generateSessionError() {
        JSONObject error = new JSONObject();
        error.put("Status", "UNAUTHORIZED_ONEID_TOKEN");
        return error;
    }

    private JSONObject generateStateResponse(String uid, String displayName) {
        JSONObject data = new JSONObject();
        data.put("Status", "OK");

        JSONArray users = new JSONArray();

        JSONObject currentUser = new JSONObject();
        currentUser.put("UserId", uid);
        currentUser.put("HashedUserId", Base64.getEncoder().encodeToString(uid.getBytes()));
        currentUser.put("DisplayName", displayName);
        currentUser.put("FirstName", displayName);
        currentUser.put("Avatar", JSONObject.NULL);
        currentUser.put("Status", "ACTIVE");
        currentUser.put("Nickname", JSONObject.NULL);
        users.put(currentUser);

        data.put("Users", users);

        data.put("UserNickNames", Collections.emptyList());
        data.put("Friendships", Collections.emptyList());
        data.put("OfficialAccounts", Collections.emptyList());
        data.put("ChatThreads", Collections.emptyList());
        data.put("ChatThreadNicknames", Collections.emptyList());
        data.put("ChatThreadUnreadMessageCount", Collections.emptyList());
        data.put("ChatThreadLatestMessageSequenceNumbers", Collections.emptyList());
        data.put("ChatThreadLastSeenMessageSequenceNumbers", Collections.emptyList());
        data.put("FriendshipInvitations", Collections.emptyList());
        data.put("GameStateChatMessages", Collections.emptyList());
        data.put("Alerts", Collections.emptyList());
        data.put("PollIntervals", new JSONArray().put(5).put(10).put(30));
        data.put("PokeIntervals", new JSONArray().put(30));
        data.put("Timestamp", LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        data.put("NotificationSequenceCounter", 0);
        data.put("NotificationSequenceThreshold", 7000);
        data.put("NotificationIntervalsJitter", 500);

        return data;
    }
}

/*
{
  "Status": "OK",
  "Users": [
    {
      "UserId": "{CA6A0C3C-A088-487D-86B3-EAF9475CFA9D}",
      "HashedUserId": "DULc/5UHy76LkZoy8ml/BK3jJBB30wknmIlSgK2n1qM=",
      "DisplayName": "CP0822239787",
      "FirstName": "Jeff",
      "Avatar": null,
      "Status": "ACTIVE",
      "Nickname": null
    }
  ],
  "UserNicknames": [],
  "Friendships": [],
  "OfficialAccounts": [],
  "ChatThreads": [],
  "ChatThreadNicknames": [],
  "ChatThreadUnreadMessageCount": [],
  "ChatThreadLatestMessageSequenceNumbers": [],
  "ChatThreadLastSeenMessageSequenceNumbers": [],
  "FriendshipInvitations": [],
  "GameStateChatMessages": [],
  "Alerts": [],
  "PollIntervals": [
    5,
    10,
    30
  ],
  "PokeIntervals": [
    30
  ],
  "Timestamp": 1539287051041,
  "NotificationSequenceCounter": 0,
  "NotificationSequenceThreshold": 7000,
  "NotificationIntervalsJitter": 500
}
 */
