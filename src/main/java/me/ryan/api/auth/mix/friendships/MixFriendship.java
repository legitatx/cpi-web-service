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
import me.legit.utils.Utilities;
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

public class MixFriendship implements Handler {

    private JsonParser parser;

    public MixFriendship() {
        this.parser = new JsonParser();
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String accessToken = ctx.header("X-Mix-OneIdToken");

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(accessToken, checkRevoked).get();
        String uid = decodedToken.getUid();

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (MixFriendship)");

        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(1).async();
        RedisFuture<String> requestSessionKey = redis.get(uid);
        JsonObject keyObject = parser.parse(requestSessionKey.get()).getAsJsonObject();
        String key = keyObject.get("key").getAsString();

        CPIEncryptor encryptor = new CPIEncryptor(Base64.getDecoder().decode(key));
        String decryptedBody = new String(encryptor.decrypt(ctx.bodyAsBytes()));

        // {"InvitationId":3069588178362554467,"IsTrusted":false,"UserId":"{F8B8047F-4829-4795-B22C-FA4B4C6CED18}","Timestamp":1545253196586}

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        JsonObject object = parser.parse(decryptedBody).getAsJsonObject();
        long invitationId = object.get("InvitationId").getAsLong();
        String userId = object.get("UserId").getAsString();
        long timestamp = object.get("Timestamp").getAsLong();

        APICore.getLogger().info("MixFriendship - " + invitationId + " - " + userId + " - " + timestamp);

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        ctx.contentType("application/json");

        DocumentReference docRef = db.collection("mixData").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            List<Map<String, Object>> friendshipInvitations = (List<Map<String, Object>>) document.get("FriendshipInvitations");

            boolean exists = false;

            QueryDocumentSnapshot friendSnapshot = null;

            for (int i = 0; i < friendshipInvitations.size(); i++) {
                Map<String, Object> friendshipInvitation = friendshipInvitations.get(i);
                Long friendshipInvitationId = (Long) friendshipInvitation.get("FriendshipInvitationId");
                if (invitationId == friendshipInvitationId) {
                    String friendDisplayName = (String) friendshipInvitation.get("FriendDisplayName");

                    ApiFuture<QuerySnapshot> queryFuture = db.collection("mixData").whereEqualTo("DisplayName", friendDisplayName).get();
                    List<QueryDocumentSnapshot> documents = queryFuture.get().getDocuments();

                    if (documents.size() != 1) {
                        // There is something seriously wrong, this should never happen! MixSearchName verifies that the username exists before sending a friend invitation
                        // There should also never be a user with the same display name, we verify this in many other places in the API
                        // If this branch is called, then either someone is messing around with the endpoint or some fatal error occurred

                        APICore.getLogger().severe("A fatal error occurred while attempting to generate a response from MixFriendshipInvitationDelete! User not found or found more than 1 display name! (UID: " + uid + " - + Friend Display Name: " + friendDisplayName + ")");

                        Sentry.getContext().addTag("friendDisplayName", friendDisplayName);
                        Sentry.capture("A fatal error occurred while attempting to generate a response from MixFriendshipInvitationDelete! User not found or found more than 1 display name!");

                        ctx.status(500);

                        break;
                    } else {
                        friendSnapshot = documents.get(0);

                        exists = true;
                        friendshipInvitations.remove(i);

                        addFriendshipForAccepter(document, friendSnapshot, friendshipInvitations);
                        generateNotificationForUser(document, friendSnapshot, invitationId);

                        addFriendshipForInviter(friendSnapshot, document, invitationId);
                        generateNotificationForUser(friendSnapshot, document, invitationId);

                        break;
                    }
                }
            }

            if (exists) {
                ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateSuccessResponse(friendSnapshot, document, invitationId).toString().getBytes())));
            } else {
                // this should never happen but just in case
                ctx.status(400);
                ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateFriendshipNotExistsError().toString().getBytes())));
            }
        } else {
            APICore.getLogger().severe(uid + " - (document.exists() == false) (MixFriendship)");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture(uid + " - (document.exists() == false) (MixFriendship)");

            ctx.status(500);
        }
    }

    private JSONObject generateSuccessResponse(DocumentSnapshot inviterSnapshot, DocumentSnapshot snapshot, long friendshipInvitationId) {
        Map<String, Object> inviterData = ((List<Map<String, Object>>) inviterSnapshot.get("Users")).get(0);

        String inviterDisplayName = (String) inviterData.get("DisplayName");
        String inviterUid = (String) inviterData.get("UserId");
        String inviterHashedUid = (String) inviterData.get("HashedUserId");

        Long inviteeSequenceNumber = snapshot.getLong("NotificationSequenceCounter");

        JSONObject finalResponse = new JSONObject();
        finalResponse.put("Status", "OK");

        JSONObject notification = new JSONObject();
        notification.put("Created", LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

        long notificationId = new BigInteger(49, ThreadLocalRandom.current()).longValue();
        notification.put("NotificationId", notificationId);

        notification.put("SequenceNumber", inviteeSequenceNumber);

        JSONObject invitation = new JSONObject();
        invitation.put("FriendshipInvitationId", friendshipInvitationId);
        invitation.put("FriendDisplayName", inviterDisplayName);
        invitation.put("IsInviter", true);
        invitation.put("IsTrusted", false);

        notification.put("Invitation", invitation);

        JSONObject friend = new JSONObject();
        friend.put("UserId", inviterUid);
        friend.put("HashedUserId", inviterHashedUid);
        friend.put("DisplayName", inviterDisplayName);
        friend.put("FirstName", inviterDisplayName);
        JSONObject avatar = Utilities.generateAvatarObj();
        friend.put("Avatar", avatar);
        friend.put("Status", JSONObject.NULL);
        friend.put("Nickname", JSONObject.NULL);

        notification.put("Friend", friend);

        notification.put("FriendshipInvitationId", friendshipInvitationId);
        notification.put("IsTrusted", false);

        finalResponse.put("Notification", notification);

        return finalResponse;
    }

    private void addFriendshipForAccepter(DocumentSnapshot snapshot, DocumentSnapshot friendSnapshot, List<Map<String, Object>> friendshipInvitations) throws Exception {
        ApiFuture<WriteResult> fiFuture = snapshot.getReference().update("FriendshipInvitations", friendshipInvitations);
        APICore.getLogger().info("Removed friendship invitation for requester " + snapshot.getId() + ": " + fiFuture.get().getUpdateTime());

        Map<String, Object> friendData = ((List<Map<String, Object>>) friendSnapshot.get("Users")).get(0);

        String friendUid = (String) friendData.get("UserId");

        JSONObject friendship = new JSONObject();
        friendship.put("FriendUserId", friendUid);
        friendship.put("IsTrusted", false);

        ApiFuture<WriteResult> newFriendFuture = snapshot.getReference().update("Friendships", FieldValue.arrayUnion(friendship.toMap()));
        APICore.getLogger().info("Added new friendship for requester " + snapshot.getId() + " (Friend UID = " + friendUid + "): " + newFriendFuture.get().getUpdateTime());
    }

    private void addFriendshipForInviter(DocumentSnapshot snapshot, DocumentSnapshot friendSnapshot, long invitationId) throws Exception {
        List<Map<String, Object>> friendshipInvitations = (List<Map<String, Object>>) snapshot.get("FriendshipInvitations");

        for (int i = 0; i < friendshipInvitations.size(); i++) {
            Map<String, Object> friendshipInvitation = friendshipInvitations.get(i);
            Long friendshipInvitationId = (Long) friendshipInvitation.get("FriendshipInvitationId");
            if (invitationId == friendshipInvitationId) {
                friendshipInvitations.remove(i);
                break;
            }
        }

        ApiFuture<WriteResult> fiFuture = snapshot.getReference().update("FriendshipInvitations", friendshipInvitations);
        APICore.getLogger().info("Removed friendship invitation for inviter " + snapshot.getId() + " (Friendship Invitation ID = " + invitationId + "): " + fiFuture.get().getUpdateTime());

        Map<String, Object> friendData = ((List<Map<String, Object>>) friendSnapshot.get("Users")).get(0);

        String friendUid = (String) friendData.get("UserId");

        JSONObject friendship = new JSONObject();
        friendship.put("FriendUserId", friendUid);
        friendship.put("IsTrusted", false);

        ApiFuture<WriteResult> newFriendFuture = snapshot.getReference().update("Friendships", FieldValue.arrayUnion(friendship.toMap()));
        APICore.getLogger().info("Added new friendship for inviter " + snapshot.getId() + " (Friend UID = " + friendUid + "): " + newFriendFuture.get().getUpdateTime());
    }

    private void generateNotificationForUser(DocumentSnapshot snapshot, DocumentSnapshot friendSnapshot, long friendshipInvitationId) throws Exception {
        Map<String, Object> friendData = ((List<Map<String, Object>>) friendSnapshot.get("Users")).get(0);

        String friendDisplayName = (String) friendData.get("DisplayName");
        String friendUid = (String) friendData.get("UserId");
        String friendHashedUid = (String) friendData.get("HashedUserId");

        Long invitedSequenceNumber = snapshot.getLong("NotificationSequenceCounter");

        JSONObject notification = new JSONObject();
        notification.put("Created", LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

        long notificationId = new BigInteger(49, ThreadLocalRandom.current()).longValue();

        notification.put("NotificationId", notificationId);
        notification.put("SequenceNumber", invitedSequenceNumber);

        JSONObject friend = new JSONObject();
        friend.put("UserId", friendUid);
        friend.put("HashedUserId", friendHashedUid);
        friend.put("DisplayName", friendDisplayName);
        friend.put("FirstName", friendDisplayName);
        JSONObject avatar = Utilities.generateAvatarObj();
        friend.put("Avatar", avatar);
        friend.put("Status", JSONObject.NULL);
        friend.put("Nickname", JSONObject.NULL);

        notification.put("Friend", friend);
        notification.put("FriendshipInvitationId", friendshipInvitationId);
        notification.put("IsTrusted", false);

        ApiFuture<WriteResult> inviterFuture = snapshot.getReference().update("CurrentNotification.AddFriendship", FieldValue.arrayUnion(notification.toMap()));
        APICore.getLogger().info("Updated current notification with add friendship data for user " + snapshot.getId() + " (Friend ID: " + friendUid + ") (Friendship Invitation ID: " + friendshipInvitationId + "): " + inviterFuture.get().getUpdateTime());
    }

    private JSONObject generateSessionError() {
        //TODO CUSTOM SESSION ERRORS FOR MIX API
        JSONObject error = new JSONObject();
        error.put("Status", "UNAUTHORIZED_ONEID_TOKEN");
        return error;
    }

    private JSONObject generateFriendshipNotExistsError() {
        JSONObject error = new JSONObject();
        error.put("Status", "INVITATION_DOES_NOT_EXIST");
        error.put("Message", "A friendship invitation with this ID does not exist.");
        return error;
    }
}
