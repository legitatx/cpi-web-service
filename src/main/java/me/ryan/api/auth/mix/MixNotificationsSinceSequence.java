package me.legit.api.auth.mix;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
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
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class MixNotificationsSinceSequence implements Handler {

    private JsonParser parser;

    public MixNotificationsSinceSequence() {
        this.parser = new JsonParser();
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String accessToken = ctx.header("X-Mix-OneIdToken");

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(accessToken, checkRevoked).get();
        String uid = decodedToken.getUid();

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (MixNotificationsSinceSequence)");

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

        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(1).async();
        RedisFuture<String> requestSessionKey = redis.get(uid);
        JsonObject keyObject = parser.parse(requestSessionKey.get()).getAsJsonObject();
        String key = keyObject.get("key").getAsString();

        CPIEncryptor encryptor = new CPIEncryptor(Base64.getDecoder().decode(key));
        String decryptedBody = new String(encryptor.decrypt(ctx.bodyAsBytes()));

        // {"SequenceNumber":1,"ExcludeNotificationSequenceNumbers":[],"UserId":"{CA6A0C3C-A088-487D-86B3-EAF9475CFA9D}","Timestamp":1539287056162}

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        JsonObject object = parser.parse(decryptedBody).getAsJsonObject();
        int sequenceNumber = object.get("SequenceNumber").getAsInt();

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        ctx.contentType("application/json");

        DocumentReference docRef = db.collection("mixData").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            Map<String, Object> currentNotification = (Map<String, Object>) document.get("CurrentNotification");
            if (currentNotification != null) {
                JSONObject currentNotificationJson = new JSONObject(currentNotification);
                currentNotificationJson.put("Status", "OK");

                docRef.update("CurrentNotification", generateResponseOrNewNotification(sequenceNumber, false).toMap());

                ctx.result(new ByteArrayInputStream(encryptor.encrypt(currentNotificationJson.toString().getBytes())));
            } else {
                ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateResponseOrNewNotification(sequenceNumber, true).toString().getBytes())));
            }
        } else {
            ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateResponseOrNewNotification(sequenceNumber, true).toString().getBytes())));
        }
    }

    private JSONObject generateSessionError() {
        JSONObject error = new JSONObject();
        error.put("Status", "UNAUTHORIZED_ONEID_TOKEN");
        return error;
    }

    private JSONObject generateResponseOrNewNotification(int sequenceNumber, boolean isResponse) {
        JSONObject response = new JSONObject();
        if (isResponse) {
            response.put("Status", "OK");
        }
        response.put("LastNotificationTimestamp", LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        response.put("NotificationSequenceCounter", sequenceNumber);
        response.put("AddChatThread", Collections.emptyList());
        response.put("AddChatThreadMembership", Collections.emptyList());
        response.put("AddChatThreadGagMessage", Collections.emptyList());
        response.put("AddChatThreadMemeMessage", Collections.emptyList());
        response.put("AddChatThreadMemberListChangedMessage", Collections.emptyList());
        response.put("AddChatThreadPhotoMessage", Collections.emptyList());
        response.put("AddChatThreadStickerMessage", Collections.emptyList());
        response.put("AddChatThreadTextMessage", Collections.emptyList());
        response.put("AddChatThreadVideoMessage", Collections.emptyList());
        response.put("AddChatThreadNickname", Collections.emptyList());
        response.put("AddChatThreadGameStateMessage", Collections.emptyList());
        response.put("UpdateChatThreadGameStateMessage", Collections.emptyList());
        response.put("AddChatThreadGameEventMessage", Collections.emptyList());
        response.put("AddFriendship", Collections.emptyList());
        response.put("AddFollowship", Collections.emptyList());
        response.put("AddFriendshipInvitation", Collections.emptyList());
        response.put("AddNickname", Collections.emptyList());
        response.put("AddAlert", Collections.emptyList());
        response.put("ClearAlert", Collections.emptyList());
        response.put("ClearMemberChatHistory", Collections.emptyList());
        response.put("ClearUnreadMessageCount", Collections.emptyList());
        response.put("RemoveChatThreadMembership", Collections.emptyList());
        response.put("RemoveChatThreadNickname", Collections.emptyList());
        response.put("RemoveFriendshipInvitation", Collections.emptyList());
        response.put("RemoveFriendship", Collections.emptyList());
        response.put("RemoveFollowship", Collections.emptyList());
        response.put("RemoveFriendshipTrust", Collections.emptyList());
        response.put("RemoveNickname", Collections.emptyList());
        response.put("SetAvatar", Collections.emptyList());
        response.put("UpdateChatThreadTrustStatus", Collections.emptyList());
        return response;
    }
}

/*
{
        "Status": "OK",
        "LastNotificationTimestamp": null,
        "NotificationSequenceCounter": 0,
        "AddChatThread": [],
        "AddChatThreadMembership": [],
        "AddChatThreadGagMessage": [],
        "AddChatThreadMemeMessage": [],
        "AddChatThreadMemberListChangedMessage": [],
        "AddChatThreadPhotoMessage": [],
        "AddChatThreadStickerMessage": [],
        "AddChatThreadTextMessage": [],
        "AddChatThreadVideoMessage": [],
        "AddChatThreadNickname": [],
        "AddChatThreadGameStateMessage": [],
        "UpdateChatThreadGameStateMessage": [],
        "AddChatThreadGameEventMessage": [],
        "AddFriendship": [],
        "AddFollowship": [],
        "AddFriendshipInvitation": [],
        "AddNickname": [],
        "AddAlert": [],
        "ClearAlert": [],
        "ClearMemberChatHistory": [],
        "ClearUnreadMessageCount": [],
        "RemoveChatThreadMembership": [],
        "RemoveChatThreadNickname": [],
        "RemoveFriendshipInvitation": [],
        "RemoveFriendship": [],
        "RemoveFollowship": [],
        "RemoveFriendshipTrust": [],
        "RemoveNickname": [],
        "SetAvatar": [],
        "UpdateChatThreadTrustStatus": []
        }
*/
