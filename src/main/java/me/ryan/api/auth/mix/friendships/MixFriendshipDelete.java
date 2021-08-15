package me.legit.api.auth.mix.friendships;

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
import me.legit.utils.CPIEncryptor;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class MixFriendshipDelete implements Handler {

    private JsonParser parser;

    public MixFriendshipDelete() {
        this.parser = new JsonParser();
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String accessToken = ctx.header("X-Mix-OneIdToken");

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(accessToken, checkRevoked).get();
        String uid = decodedToken.getUid();

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (MixFriendshipDelete)");

        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(1).async();
        RedisFuture<String> requestSessionKey = redis.get(uid);
        JsonObject keyObject = parser.parse(requestSessionKey.get()).getAsJsonObject();
        String key = keyObject.get("key").getAsString();

        CPIEncryptor encryptor = new CPIEncryptor(Base64.getDecoder().decode(key));
        String decryptedBody = new String(encryptor.decrypt(ctx.bodyAsBytes()));

        // {"FriendUserId":"{259C7B5A-8C33-44C0-A73D-03092D4B909E}","UserId":"{F8B8047F-4829-4795-B22C-FA4B4C6CED18}","Timestamp":1545207448195}

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        JsonObject object = parser.parse(decryptedBody).getAsJsonObject();
        String friendUserId = object.get("FriendUserId").getAsString();
        String userId = object.get("UserId").getAsString();
        long timestamp = object.get("Timestamp").getAsLong();

        APICore.getLogger().info("MixFriendship - " + friendUserId + " - " + userId + " - " + timestamp);

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        ctx.contentType("application/json");

        DocumentReference docRef = db.collection("mixData").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            DocumentReference friendRef = db.collection("mixData").document(friendUserId);
            ApiFuture<DocumentSnapshot> friendFuture = friendRef.get();
            DocumentSnapshot friendDocument = friendFuture.get();

            if (friendDocument.exists()) {
                removeFriendshipForUser(document, friendUserId);
                removeUserFromMixData(document, friendUserId);

                removeFriendshipForUser(friendDocument, uid);
                removeUserFromMixData(friendDocument, uid);

                generateNotificationForUser(friendDocument, uid);
                generateNotificationForUser(document, friendUserId);

                ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateSuccessResponse(document, friendUserId).toString().getBytes())));
            } else {
                // should never happen but just in case
                ctx.status(400);
                ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateFriendshipNotExistsError().toString().getBytes())));
            }
        } else {
            APICore.getLogger().severe(uid + " - (document.exists() == false) (MixFriendshipDelete)");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture(uid + " - (document.exists() == false) (MixFriendshipDelete)");

            ctx.status(500);
        }
    }

    private JSONObject generateSuccessResponse(DocumentSnapshot snapshot, String friendUserId) {
        Long inviterSequenceNumber = snapshot.getLong("NotificationSequenceCounter");

        JSONObject finalResponse = new JSONObject();
        finalResponse.put("Status", "OK");

        JSONObject notification = new JSONObject();
        notification.put("Created", LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

        long notificationId = new BigInteger(49, ThreadLocalRandom.current()).longValue();

        notification.put("NotificationId", notificationId);
        notification.put("SequenceNumber", inviterSequenceNumber);
        notification.put("FriendUserId", friendUserId);

        finalResponse.put("Notification", notification);

        return finalResponse;
    }

    private void generateNotificationForUser(DocumentSnapshot snapshot, String friendUserId) throws Exception {
        Long invitedSequenceNumber = snapshot.getLong("NotificationSequenceCounter");

        JSONObject notification = new JSONObject();
        notification.put("Created", LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

        long notificationId = new BigInteger(49, ThreadLocalRandom.current()).longValue();

        notification.put("NotificationId", notificationId);
        notification.put("SequenceNumber", invitedSequenceNumber);
        notification.put("FriendUserId", friendUserId);

        ApiFuture<WriteResult> inviteeFuture = snapshot.getReference().update("CurrentNotification.RemoveFriendship", FieldValue.arrayUnion(notification.toMap()));
        APICore.getLogger().info("Updated current notification with remove friendship data for user " + snapshot.getId() + " (Friendship User ID = " + friendUserId + "): " + inviteeFuture.get().getUpdateTime());
    }

    private void removeFriendshipForUser(DocumentSnapshot snapshot, String friendUserId) throws Exception {
        List<Map<String, Object>> friendships = (List<Map<String, Object>>) snapshot.get("Friendships");

        for (int i = 0; i < friendships.size(); i++) {
            Map<String, Object> friendship = friendships.get(i);
            String friendUid = (String) friendship.get("FriendUserId");
            if (friendUid.equals(friendUserId)) {
                friendships.remove(i);
                break;
            }
        }

        ApiFuture<WriteResult> removingFriendFuture = snapshot.getReference().update("Friendships", friendships);
        APICore.getLogger().info("Removed friendship for user " + snapshot.getId() + " (Friend UID = " + friendUserId + "): " + removingFriendFuture.get().getUpdateTime());
    }

    private void removeUserFromMixData(DocumentSnapshot snapshot, String friendUserId) throws Exception {
        List<Map<String, Object>> users = (List<Map<String, Object>>) snapshot.get("Users");

        for (int i = 0; i < users.size(); i++) {
            Map<String, Object> user = users.get(i);
            String friendUid = (String) user.get("UserId");
            if (friendUserId.toLowerCase().equals(friendUid.toLowerCase())) {
                users.remove(i);
                break;
            }
        }

        ApiFuture<WriteResult> requesterFuture = snapshot.getReference().update("Users", users);
        APICore.getLogger().info("Removed user " + friendUserId + " from mixData Users list for user " + snapshot.getId() + ": " + requesterFuture.get().getUpdateTime());
    }

    private JSONObject generateSessionError() {
        JSONObject error = new JSONObject();
        error.put("Status", "UNAUTHORIZED_ONEID_TOKEN");
        return error;
    }

    private JSONObject generateFriendshipNotExistsError() {
        JSONObject error = new JSONObject();
        error.put("Status", "FRIEND_DOES_NOT_EXIST");
        error.put("Message", "A friendship by a user with this SWID does not exist.");
        return error;
    }
}
