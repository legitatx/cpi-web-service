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
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class MixFriendshipInvitation implements Handler {

    private JsonParser parser;

    public MixFriendshipInvitation() {
        this.parser = new JsonParser();
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        String accessToken = ctx.header("X-Mix-OneIdToken");

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(accessToken, checkRevoked).get();
        String uid = decodedToken.getUid();

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (MixFriendshipInvitation)");

        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(1).async();
        RedisFuture<String> requestSessionKey = redis.get(uid);
        JsonObject keyObject = parser.parse(requestSessionKey.get()).getAsJsonObject();
        String key = keyObject.get("key").getAsString();

        CPIEncryptor encryptor = new CPIEncryptor(Base64.getDecoder().decode(key));
        String decryptedBody = new String(encryptor.decrypt(ctx.bodyAsBytes()));

        // {"InviteeDisplayName":"rockhopper","IsTrusted":false,"UserId":"{F8B8047F-4829-4795-B22C-FA4B4C6CED18}","Timestamp":1545207475347}

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        JsonObject object = parser.parse(decryptedBody).getAsJsonObject();
        String inviteeDisplayName = object.get("InviteeDisplayName").getAsString();
        boolean isTrusted = object.get("IsTrusted").getAsBoolean();
        String userId = object.get("UserId").getAsString();
        long timestamp = object.get("Timestamp").getAsLong();

        APICore.getLogger().info("MixFriendshipInvitation - " + inviteeDisplayName + " - " + isTrusted + " - " + userId + " - " + timestamp);

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        ctx.contentType("application/json");

        DocumentReference docRef = db.collection("mixData").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            ApiFuture<QuerySnapshot> queryFuture = db.collection("mixData").whereEqualTo("DisplayName", inviteeDisplayName).get();
            List<QueryDocumentSnapshot> documents = queryFuture.get().getDocuments();
            APICore.getLogger().info("Document size is " + documents.size());
            if (documents.size() != 1) {
                // There is something seriously wrong, this should never happen! MixSearchName verifies that the username exists before sending a friend invitation
                // There should also never be a user with the same display name, we verify this in many other places in the API
                // If this branch is called, then either someone is messing around with the endpoint or some fatal error occurred

                APICore.getLogger().severe("A fatal error occurred while attempting to generate a response from MixFriendshipInvitation! User not found or found more than 1 display name! (UID: " + uid + " - + Invitee Display Name: " + inviteeDisplayName + ")");

                Sentry.getContext().addTag("inviteeDisplayName", inviteeDisplayName);
                Sentry.capture("A fatal error occurred while attempting to generate a response from MixFriendshipInvitation! User not found or found more than 1 display name!");

                ctx.status(500);

                return;
            }

            QueryDocumentSnapshot inviteeSnapshot = documents.get(0);

            if (friendshipInvitationAlreadyExists(document, inviteeSnapshot)) {
                ctx.status(400);
                ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateFriendshipExistsError().toString().getBytes())));
                return;
            }

            long friendshipInvitationId = new BigInteger(49, ThreadLocalRandom.current()).longValue();

            generateInvitationForInviter(docRef, friendshipInvitationId, decodedToken.getName(), inviteeDisplayName);
            generateInvitationForInvitee(inviteeSnapshot.getReference(), friendshipInvitationId, decodedToken.getName(), inviteeDisplayName);
            persistNewUser(document, inviteeSnapshot);

            generateNotificationForInvitee(friendshipInvitationId, document, inviteeSnapshot);

            ctx.result(new ByteArrayInputStream(encryptor.encrypt(generateSuccessResponse(friendshipInvitationId, document, inviteeSnapshot).toString().getBytes())));
        } else {
            APICore.getLogger().severe(uid + " - (document.exists() == false) (MixFriendshipInvitation)");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture(uid + " - (document.exists() == false) (MixFriendshipInvitation)");

            ctx.status(500);
        }
    }

    private void generateInvitationForInviter(DocumentReference inviterRef, long friendshipInvitationId, String inviter, String invitee) throws Exception {
        Map<String, Object> friendshipInvitationData = new HashMap<>();

        friendshipInvitationData.put("FriendshipInvitationId", friendshipInvitationId);
        friendshipInvitationData.put("FriendDisplayName", invitee);
        friendshipInvitationData.put("IsInviter", true);
        friendshipInvitationData.put("IsTrusted", false);

        ApiFuture<WriteResult> future = inviterRef.update("FriendshipInvitations", FieldValue.arrayUnion(friendshipInvitationData));
        APICore.getLogger().info("Added new friendship invitation for " + invitee + " from " + inviter + " (Inviter) (Friendship Invitation ID = " + friendshipInvitationId + "): " + future.get().getUpdateTime());
    }

    private void generateInvitationForInvitee(DocumentReference inviteeRef, long friendshipInvitationId, String inviter, String invitee) throws Exception {
        Map<String, Object> friendshipInvitationData = new HashMap<>();

        friendshipInvitationData.put("FriendshipInvitationId", friendshipInvitationId);
        friendshipInvitationData.put("FriendDisplayName", inviter);
        friendshipInvitationData.put("IsInviter", false);
        friendshipInvitationData.put("IsTrusted", false);

        ApiFuture<WriteResult> future = inviteeRef.update("FriendshipInvitations", FieldValue.arrayUnion(friendshipInvitationData));
        APICore.getLogger().info("Added new friendship invitation for " + invitee + " from " + inviter + " (Invitee) (Friendship Invitation ID = " + friendshipInvitationId + "): " + future.get().getUpdateTime());
    }

    private void generateNotificationForInvitee(long friendshipInvitationId, DocumentSnapshot inviterSnapshot, DocumentSnapshot inviteeSnapshot) throws Exception {
        Map<String, Object> inviterData = ((List<Map<String, Object>>) inviterSnapshot.get("Users")).get(0);

        String inviterDisplayName = (String) inviterData.get("DisplayName");
        String inviterUid = (String) inviterData.get("UserId");
        String inviterHashedUid = (String) inviterData.get("HashedUserId");

        JSONObject notification = new JSONObject();
        notification.put("Created", LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());


        long notificationId = new BigInteger(49, ThreadLocalRandom.current()).longValue();
        notification.put("NotificationId", notificationId);

        Long inviteeSequenceNumber = inviteeSnapshot.getLong("NotificationSequenceCounter");
        notification.put("SequenceNumber", inviteeSequenceNumber);

        JSONObject invitation = new JSONObject();
        invitation.put("FriendshipInvitationId", friendshipInvitationId);
        invitation.put("FriendDisplayName", inviterDisplayName);
        invitation.put("IsInviter", false);
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

        ApiFuture<WriteResult> inviteeFuture = inviteeSnapshot.getReference().update("CurrentNotification.AddFriendshipInvitation", FieldValue.arrayUnion(notification.toMap()));
        APICore.getLogger().info("Updated current notification with friendship invitation data for the invitee " + inviteeSnapshot.getId() + " (Friendship Invitation ID = " + friendshipInvitationId + "): " + inviteeFuture.get().getUpdateTime());
    }

    private void persistNewUser(DocumentSnapshot inviterSnapshot, DocumentSnapshot inviteeSnapshot) throws Exception {
        Map<String, Object> inviterUser = ((List<Map<String, Object>>) inviterSnapshot.get("Users")).get(0);
        ApiFuture<WriteResult> inviterFuture = inviteeSnapshot.getReference().update("Users", FieldValue.arrayUnion(inviterUser));
        APICore.getLogger().info("Added new user (inviter) to Mix data for the invitee: " + inviterFuture.get().getUpdateTime());

        Map<String, Object> inviteeUser = ((List<Map<String, Object>>) inviteeSnapshot.get("Users")).get(0);
        ApiFuture<WriteResult> inviteeFuture = inviterSnapshot.getReference().update("Users", FieldValue.arrayUnion(inviteeUser));
        APICore.getLogger().info("Added new user (invitee) to Mix data for the inviter: " + inviteeFuture.get().getUpdateTime());
    }

    private boolean friendshipInvitationAlreadyExists(DocumentSnapshot inviterSnapshot, DocumentSnapshot inviteeSnapshot) {
        String inviteeDisplayName = (String) ((List<Map<String, Object>>) inviteeSnapshot.get("Users")).get(0).get("DisplayName");

        boolean exists = false;

        List<Map<String, Object>> friendshipInvitations = (List<Map<String, Object>>) inviterSnapshot.get("FriendshipInvitations");
        for (Map<String, Object> friendshipInvitation : friendshipInvitations) {
            String friendDisplayName = (String) friendshipInvitation.get("FriendDisplayName");
            if (friendDisplayName.toLowerCase().equals(inviteeDisplayName.toLowerCase())) {
                exists = true;
                break;
            }
        }

        return exists;
    }

    private JSONObject generateSuccessResponse(long friendshipInvitationId, DocumentSnapshot inviterSnapshot, DocumentSnapshot inviteeSnapshot) {
        Map<String, Object> inviteeData = ((List<Map<String, Object>>) inviteeSnapshot.get("Users")).get(0);

        String inviteeDisplayName = (String) inviteeData.get("DisplayName");
        String inviteeUid = (String) inviteeData.get("UserId");
        String inviteeHashedUid = (String) inviteeData.get("HashedUserId");

        Long inviterSequenceNumber = inviterSnapshot.getLong("NotificationSequenceCounter");

        JSONObject finalResponse = new JSONObject();
        finalResponse.put("Status", "OK");

        JSONObject notification = new JSONObject();
        notification.put("Created", LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());

        long notificationId = new BigInteger(49, ThreadLocalRandom.current()).longValue();
        notification.put("NotificationId", notificationId);

        notification.put("SequenceNumber", inviterSequenceNumber);

        JSONObject invitation = new JSONObject();
        invitation.put("FriendshipInvitationId", friendshipInvitationId);
        invitation.put("FriendDisplayName", inviteeDisplayName);
        invitation.put("IsInviter", true);
        invitation.put("IsTrusted", false);

        notification.put("Invitation", invitation);

        JSONObject friend = new JSONObject();
        friend.put("UserId", inviteeUid);
        friend.put("HashedUserId", inviteeHashedUid);
        friend.put("DisplayName", inviteeDisplayName);
        friend.put("FirstName", inviteeDisplayName);
        JSONObject avatar = Utilities.generateAvatarObj();
        friend.put("Avatar", avatar);
        friend.put("Status", JSONObject.NULL);
        friend.put("Nickname", JSONObject.NULL);

        notification.put("Friend", friend);

        finalResponse.put("Notification", notification);

        return finalResponse;
    }

    private JSONObject generateSessionError() {
        JSONObject error = new JSONObject();
        error.put("Status", "UNAUTHORIZED_ONEID_TOKEN");
        return error;
    }

    private JSONObject generateFriendshipExistsError() {
        JSONObject error = new JSONObject();
        error.put("Status", "INVITATION_ALREADY_EXISTS");
        error.put("Message", "A friendship invitation already exists for this user.");
        return error;
    }
}
