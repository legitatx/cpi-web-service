package me.legit.api.auth.guest;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.nulabinc.zxcvbn.Strength;
import com.nulabinc.zxcvbn.Zxcvbn;
import emailvalidator4j.EmailValidator;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.sentry.Sentry;
import io.sentry.event.BreadcrumbBuilder;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.utils.ChatFilter;
import me.legit.utils.FirebaseError;
import me.legit.utils.Utilities;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import static me.legit.utils.Utilities.generateError;
import static me.legit.utils.Utilities.generateResponse;

public class Register implements Handler {

    private static OkHttpClient client = new OkHttpClient.Builder().build();
    private static EmailValidator validator = new EmailValidator();

    private JsonParser parser;

    private List<Pattern> patterns;
    private Zxcvbn zxcvbn;

    public Register() {
        this.parser = new JsonParser();
        this.patterns = new ArrayList<>();
        this.zxcvbn = new Zxcvbn();
        patterns.add(Pattern.compile("^\\(?([0-9]{3})\\)?[-.\\s]?([0-9]{3})[-.\\s]?([0-9]{4})$"));
        patterns.add(Pattern.compile("^\\+(?:[0-9] ?){6,14}[0-9]$"));
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        JsonObject object = parser.parse(ctx.body()).getAsJsonObject();
        String username = object.get("profile").getAsJsonObject().get("username").getAsString();
        String email = object.get("profile").getAsJsonObject().get("parentEmail").getAsString();
        String password = object.get("password").getAsString();

        Sentry.getContext().setUser(new UserBuilder().setUsername(username).build());

        ctx.contentType("application/json;charset=utf-8");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        ApiFuture<QuerySnapshot> snapshot = db.collection("users").whereEqualTo("name.username", username).get();
        List<QueryDocumentSnapshot> documents = snapshot.get().getDocuments();
        if (documents.size() > 0 || ChatFilter.isReserved(username)) {
            JSONObject error = generateError("INUSE_VALUE", "profile.username", "The username already exists in the system.");

            Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().withData("error", error.toString()).build());
            Sentry.getContext().recordBreadcrumb(new BreadcrumbBuilder().withData("username", username).build());
            Sentry.capture("A username already existed in the system while attempting to validate a new registration!");

            ctx.result(error.toString());
            return;
        }

        if (password.length() < 6 || password.length() > 24) {
            JSONObject error = generateError("INVALID_VALUE_PASSWORD_SIZE", "password", "The password size is either too small or large.");
            ctx.result(error.toString());
            return;
        } else if (ChatFilter.isCommonPassword(password)) {
            JSONObject error = generateError("INVALID_VALUE_PASSWORD_TOO_COMMON", "password", "The password is too common.");
            ctx.result(error.toString());
            return;
        } else if (patterns.get(0).matcher(password).matches() || patterns.get(1).matcher(password).matches()) {
            JSONObject error = generateError("INVALID_VALUE_PASSWORD_LIKE_PHONE_NUMBER", "password", "The password is too much like a phone number.");
            ctx.result(error.toString());
            return;
        }

        Strength strength = zxcvbn.measure(password);
        if (strength.getScore() < 2) {
            JSONObject error = generateError("INVALID_VALUE_PASSWORD_MISSING_EXPECTED_CHARS", "password", "The password is missing expected characters.");
            ctx.result(error.toString());
            return;
        }

        if (validator.isValid(email) && (email.contains("@gmail") || email.contains("@yahoo") || email.contains("@outlook") || email.contains("@hotmail") || email.contains("@icloud"))) {
            String tempDisplayName = "CP" + new BigInteger(30, ThreadLocalRandom.current()).longValue();

            UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                    .setEmail(email)
                    .setEmailVerified(false)
                    .setPassword(password)
                    .setDisplayName(tempDisplayName)
                    .setDisabled(false);

            try {
                ApiFuture<UserRecord> userRecordFuture = FirebaseAuth.getInstance().createUserAsync(createRequest);
                UserRecord userRecord = userRecordFuture.get();
                APICore.getLogger().info("Successfully created new user: " + userRecord.getUid());

                ApiFuture<String> customTokenFuture = FirebaseAuth.getInstance().createCustomTokenAsync(userRecord.getUid());
                String customToken = customTokenFuture.get();
                APICore.getLogger().info("Successfully fetched custom token for user " + userRecord.getUid() + ": " + customToken);

                RequestBody body = RequestBody.create(MediaType.parse("application/json"), "{}");
                Request fetchAccessToken = new Request.Builder()
                        .url("https://localhost/auth/v1/user/login/token")
                        .addHeader("Authorization", "Bearer " + customToken)
                        .post(body)
                        .build();

                CompletableFuture<String> future = new CompletableFuture<>();
                client.newCall(fetchAccessToken).enqueue(new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        APICore.getLogger().severe("An error occurred while attempting to fetch access token for a user! - " + "Call: " + call.request().toString());
                        Sentry.getContext().recordBreadcrumb(
                                new BreadcrumbBuilder()
                                        .withData("clientCall", call.request().toString())
                                        .build()
                        );
                        e.printStackTrace();
                        Sentry.capture(e);

                        ctx.status(500);

                        future.completeExceptionally(e);
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        try (ResponseBody responseBody = response.body()) {
                            String accessTokenBody = responseBody.string();
                            if (!response.isSuccessful()) {
                                APICore.getLogger().severe("An error occurred while attempting to fetch access token for a user! - " + "Code: " + response.code() + " - Body: " + accessTokenBody);
                                //TODO add custom error for register, client should be able to detect unknown errors for now

                                Sentry.getContext().recordBreadcrumb(
                                        new BreadcrumbBuilder()
                                                .withData("code", String.valueOf(response.code()))
                                                .withData("body", accessTokenBody)
                                                .build()
                                );

                                Sentry.capture("An error occurred while attempting to fetch access token for a user!");

                                ctx.status(500);

                                future.complete(new JSONObject().put("status", 500).put("message", "An unknown error occurred while attempting to fetch an access token.").toString());
                            } else {
                                JsonParser parser = new JsonParser();
                                JsonObject accessTokenObj = parser.parse(accessTokenBody).getAsJsonObject();

                                if (accessTokenObj.has("success") && accessTokenObj.get("success").getAsBoolean()) {
                                    String accessToken = accessTokenObj.get("access_token").getAsString();
                                    String refreshToken = accessTokenObj.get("refresh_token").getAsString();
                                    String uid = accessTokenObj.get("uid").getAsString();

                                    try {
                                        addGameUserData(userRecord, username, tempDisplayName);
                                        addMixUserData(userRecord, tempDisplayName);
                                    } catch (Exception ex) {
                                        APICore.getLogger().severe("An unknown error occurred when adding new user and Mix data while attempting to fetch access token for a user!");
                                        ex.printStackTrace();

                                        Sentry.getContext().recordBreadcrumb(
                                                new BreadcrumbBuilder()
                                                        .withData("message", "An unknown error occurred when adding new user and Mix data while attempting to fetch access token for a user!")
                                                        .build()
                                        );
                                        Sentry.capture(ex);

                                        ctx.status(500);
                                        future.completeExceptionally(ex);

                                        return;
                                    }

                                    try {
                                        ApiFuture<UserRecord> updateUserFuture = FirebaseAuth.getInstance().updateUserAsync(new UserRecord.UpdateRequest(userRecord.getUid()).setDisabled(true));
                                        UserRecord userRecord = updateUserFuture.get();
                                        APICore.getLogger().info("Successfully disabled new user account until manual verification: " + userRecord.getUid());
                                    } catch (InterruptedException | ExecutionException e) {
                                        APICore.getLogger().severe("An error occurred when disabling user account while attempting to fetch access token for a user!");
                                        e.printStackTrace();

                                        Sentry.getContext().recordBreadcrumb(
                                                new BreadcrumbBuilder()
                                                        .withData("message", "An error occurred when disabling user account while attempting to fetch access token for a user!")
                                                        .build()
                                        );
                                        Sentry.capture(e);

                                        ctx.status(500);
                                        future.completeExceptionally(e);

                                        return;
                                    }

                                    ctx.status(401);
                                    future.complete("{\"message\":\"Failed to authenticate the command call with any providers configured.\",\"code\":3}");
                                    //future.complete(generateResponse(uid, username, email, firstName, accessToken, refreshToken).toString());
                                } else {
                                    APICore.getLogger().severe("An error occurred while attempting to fetch access token for a user! - " + "Code: " + response.code() + " - Body: " + accessTokenBody);
                                    //TODO add custom error for register, client should be able to detect unknown errors for now

                                    Sentry.getContext().recordBreadcrumb(
                                            new BreadcrumbBuilder()
                                                    .withData("code", String.valueOf(response.code()))
                                                    .withData("body", accessTokenBody)
                                                    .build()
                                    );

                                    Sentry.capture("An error occurred while attempting to fetch access token for a user!");

                                    ctx.status(500);
                                    future.complete(new JSONObject().put("status", 500).put("message", "An unknown error occurred while attempting to fetch an access token.").toString());
                                }
                            }
                        }
                    }
                });

                ctx.result(future);
            } catch (ExecutionException e) {
                if (e.getCause() instanceof FirebaseAuthException) {
                    FirebaseAuthException authError = (FirebaseAuthException) e.getCause();
                    if (authError.getErrorCode().contains("EMAIL_EXISTS")) {
                        JSONObject error = generateError("SERVICE_ERROR", "profile.parentEmail", "This email address already exists.");

                        Sentry.getContext().addExtra("halted", true);
                        Sentry.capture("An email address already existed upon registering a user!");

                        ctx.status(400);
                        ctx.result(error.toString());
                    }
                }
            }
        } else {
            JSONObject error = generateError("SERVICE_ERROR", "profile.parentEmail", "An invalid email address was entered.");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("An invalid email address was entered upon registering a user!");

            ctx.status(400);
            ctx.result(error.toString());
        }
    }

    private void addGameUserData(UserRecord record, String username, String tempDisplayName) throws Exception {
        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();
        DocumentReference ref = db.collection("users").document(record.getUid());
        DocumentReference igloosRef = db.collection("igloos").document(record.getUid());

        Map<String, Object> docData = new HashMap<>();
        Map<String, Object> igloosData = new HashMap<>();

        Map<String, Object> idObject = new HashMap<>();
        idObject.put("id", record.getUid());
        idObject.put("type", 1);
        docData.put("id", idObject);

        Map<String, Object> displayName = new HashMap<>();
        displayName.put("displayName", tempDisplayName);
        displayName.put("proposedDisplayName", null);
        displayName.put("proposedStatus", "NONE");
        displayName.put("moderatedStatusDate", ZonedDateTime.now(ZoneOffset.UTC).toLocalDateTime().toString());
        displayName.put("username", username);
        docData.put("name", displayName);

        docData.put("onlineLocation", null);
        docData.put("member", true);
        docData.put("outfit", Collections.emptyList());

        Map<String, Object> profileObject = new HashMap<>();
        profileObject.put("colour", 0);
        profileObject.put("daysOld", 0);
        docData.put("profile", profileObject);

        Map<String, Object> zoneIdObject = new HashMap<>();
        zoneIdObject.put("name", "DefaultIgloo");
        zoneIdObject.put("instanceId", record.getUid());
        docData.put("zoneId", zoneIdObject);

        docData.put("minigameProgress", Collections.emptyList());

        List<Object> questsList = new ArrayList<>();

        Map<String, Object> quest1Object = new HashMap<>();
        quest1Object.put("questId", "RHC001Q001TreasureQuest");
        quest1Object.put("status", 0);
        quest1Object.put("completedObjectives", Collections.emptyList());
        quest1Object.put("timesCompleted", 0);
        quest1Object.put("unlockTime", null);
        quest1Object.put("completedTime", null);

        Map<String, Object> quest2Object = new HashMap<>();
        quest2Object.put("questId", "RKC001Q001Drop");
        quest2Object.put("status", 0);
        quest2Object.put("completedObjectives", Collections.emptyList());
        quest2Object.put("timesCompleted", 0);
        quest2Object.put("unlockTime", null);
        quest2Object.put("completedTime", null);

        Map<String, Object> quest3Object = new HashMap<>();
        quest3Object.put("questId", "DJC001Q001Plan");
        quest3Object.put("status", 0);
        quest3Object.put("completedObjectives", Collections.emptyList());
        quest3Object.put("timesCompleted", 0);
        quest3Object.put("unlockTime", null);
        quest3Object.put("completedTime", null);

        Map<String, Object> quest4Object = new HashMap<>();
        quest4Object.put("questId", "AAC001Q001LeakyShip");
        quest4Object.put("status", 0);
        quest4Object.put("completedObjectives", Collections.emptyList());
        quest4Object.put("timesCompleted", 0);
        quest4Object.put("unlockTime", null);
        quest4Object.put("completedTime", null);

        Collections.addAll(questsList, quest1Object, quest2Object, quest3Object, quest4Object);

        docData.put("quests", questsList);

        byte[] tutorialData = new byte[64];
        docData.put("tutorialData", Blob.fromBytes(tutorialData));

        Map<String, Object> breadcrumbsObject = new HashMap<>();
        breadcrumbsObject.put("breadcrumbs", Collections.emptyList());
        docData.put("breadcrumbs", breadcrumbsObject);

        docData.put("claimedRewardIds", Collections.emptyList());

        Map<String, Object> dailySpinDataObject = new HashMap<>();
        dailySpinDataObject.put("earnedRepeatableRewardIds", Collections.emptyList());
        dailySpinDataObject.put("earnedNonRepeatableRewardIds", Collections.emptyList());
        dailySpinDataObject.put("numSpinsSinceReceivedChest", 0);
        dailySpinDataObject.put("numSpinsSinceReceivedExtraSpin", 0);
        dailySpinDataObject.put("timeOfLastSpinInMilliseconds", 0);
        dailySpinDataObject.put("currentChestId", 0);
        dailySpinDataObject.put("numPunchesOnCurrentChest", 0);
        dailySpinDataObject.put("numChestsReceivedOfCurrentChestId", 0);
        docData.put("dailySpinData", dailySpinDataObject);

        docData.put("decorationInventory", Collections.emptyMap());
        docData.put("equipment", Collections.emptyList());

        igloosData.put("visibility", 2);
        igloosData.put("activeLayoutId", null);
        igloosData.put("layouts", Collections.emptyList());
        igloosData.put("activeLayout", null);

        Map<String, Object> consumableInventoryObject = new HashMap<>();
        consumableInventoryObject.put("inventoryMap", Collections.emptyMap());
        docData.put("consumableInventory", consumableInventoryObject);

        Map<String, Object> assetsObject = new HashMap<>();
        assetsObject.put("currency", Collections.emptyMap());
        assetsObject.put("mascotXP", Collections.emptyMap());
        assetsObject.put("collectibleCurrencies", Collections.emptyMap());
        assetsObject.put("colourPacks", Collections.emptyList());
        assetsObject.put("decals", Collections.emptyList());
        assetsObject.put("fabrics", Collections.emptyList());
        assetsObject.put("emotePacks", Collections.emptyList());
        assetsObject.put("sizzleClips", Collections.emptyList());
        assetsObject.put("equipmentTemplates", Collections.emptyList());
        assetsObject.put("lots", Collections.emptyList());
        assetsObject.put("decorations", Collections.emptyList());
        assetsObject.put("structures", Collections.emptyList());
        assetsObject.put("musicTracks", Collections.emptyList());
        assetsObject.put("lighting", Collections.emptyList());
        assetsObject.put("durables", Collections.emptyList());
        assetsObject.put("tubes", Collections.emptyList());
        assetsObject.put("savedOutfitSlots", 0);
        assetsObject.put("iglooSlots", 0);
        assetsObject.put("coins", 0);
        assetsObject.put("partySupplies", Collections.emptyList());
        docData.put("assets", assetsObject);

        Map<String, Object> earnedRewardsMap = new HashMap<>();
        earnedRewardsMap.put("Beach", Collections.emptyMap());
        earnedRewardsMap.put("Boardwalk", Collections.emptyMap());
        earnedRewardsMap.put("BoxDimension", Collections.emptyMap());
        earnedRewardsMap.put("DefaultIgloo", Collections.emptyMap());
        earnedRewardsMap.put("Diving", Collections.emptyMap());
        earnedRewardsMap.put("ForestIgloo", Collections.emptyMap());
        earnedRewardsMap.put("HerbertBase", Collections.emptyMap());
        earnedRewardsMap.put("IglooIsland", Collections.emptyMap());
        earnedRewardsMap.put("MtBlizzard", Collections.emptyMap());
        earnedRewardsMap.put("MtBlizzardSummit", Collections.emptyMap());
        earnedRewardsMap.put("Town", Collections.emptyMap());

        docData.put("earnedRewards", earnedRewardsMap);
        docData.put("selectedTubeId", 0);
        docData.put("dailyTaskProgress", Collections.emptyList());

        Map<String, Object> membershipRightsObject = new HashMap<>();
        membershipRightsObject.put("swid", record.getUid());
        membershipRightsObject.put("member", true);
        membershipRightsObject.put("recurring", true);
        membershipRightsObject.put("recurDate", LocalDateTime.now().plusYears(10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        membershipRightsObject.put("expireDate", LocalDateTime.now().plusYears(10).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        membershipRightsObject.put("allAccessAllowed", true);
        membershipRightsObject.put("paymentPending", false);
        membershipRightsObject.put("trialAvailable", false);
        membershipRightsObject.put("trialPeriod", false);
        membershipRightsObject.put("numRenewals", 1);
        membershipRightsObject.put("autoRenewing", false);
        docData.put("membershipRights", membershipRightsObject);

        docData.put("verified", false);

        //TODO ROOM TRANSIENT DATA -- ON DURABLE EQUIP, SET VALUE FROM DURABLES.JSON FROM CLIENT FILES

        ApiFuture<WriteResult> addedDocRef = ref.set(docData);
        APICore.getLogger().info("Successfully added default game data for new registered user " + record.getUid() + " at: " + addedDocRef.get().getUpdateTime());

        ApiFuture<WriteResult> addedIglooDocRef = igloosRef.set(igloosData);
        APICore.getLogger().info("Successfully added default igloo data for new registered user " + record.getUid() + " at: " + addedIglooDocRef.get().getUpdateTime());
    }

    private void addMixUserData(UserRecord record, String tempDisplayName) throws Exception {
        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();
        DocumentReference ref = db.collection("mixData").document(record.getUid());

        Map<String, Object> docData = new HashMap<>();

        List<Map<String, Object>> users = new ArrayList<>();
        Map<String, Object> currentUser = new HashMap<>();
        currentUser.put("UserId", record.getUid());
        currentUser.put("HashedUserId", Base64.getEncoder().encodeToString(Utilities.getSignatureGenerator().hashString(record.getUid(), StandardCharsets.UTF_8).asBytes()));
        currentUser.put("DisplayName", tempDisplayName);
        currentUser.put("FirstName", tempDisplayName);
        currentUser.put("Avatar", null);
        currentUser.put("Status", "ACTIVE");
        currentUser.put("Nickname", null);
        users.add(currentUser);

        docData.put("Users", users);

        docData.put("UserNickNames", Collections.emptyList());
        docData.put("Friendships", Collections.emptyList());
        docData.put("OfficialAccounts", Collections.emptyList());
        docData.put("ChatThreads", Collections.emptyList());
        docData.put("ChatThreadNicknames", Collections.emptyList());
        docData.put("ChatThreadUnreadMessageCount", Collections.emptyList());
        docData.put("ChatThreadLatestMessageSequenceNumbers", Collections.emptyList());
        docData.put("ChatThreadLastSeenMessageSequenceNumbers", Collections.emptyList());
        docData.put("FriendshipInvitations", Collections.emptyList());
        docData.put("GameStateChatMessages", Collections.emptyList());
        docData.put("Alerts", Collections.emptyList());
        docData.put("DisplayName", tempDisplayName);

        Map<String, Object> currentNotification = new HashMap<>();
        currentNotification.put("LastNotificationTimestamp", null);
        currentNotification.put("NotificationSequenceCounter", 1);
        currentNotification.put("AddChatThread", Collections.emptyList());
        currentNotification.put("AddChatThreadMembership", Collections.emptyList());
        currentNotification.put("AddChatThreadGagMessage", Collections.emptyList());
        currentNotification.put("AddChatThreadMemeMessage", Collections.emptyList());
        currentNotification.put("AddChatThreadMemberListChangedMessage", Collections.emptyList());
        currentNotification.put("AddChatThreadPhotoMessage", Collections.emptyList());
        currentNotification.put("AddChatThreadStickerMessage", Collections.emptyList());
        currentNotification.put("AddChatThreadTextMessage", Collections.emptyList());
        currentNotification.put("AddChatThreadVideoMessage", Collections.emptyList());
        currentNotification.put("AddChatThreadNickname", Collections.emptyList());
        currentNotification.put("AddChatThreadGameStateMessage", Collections.emptyList());
        currentNotification.put("UpdateChatThreadGameStateMessage", Collections.emptyList());
        currentNotification.put("AddChatThreadGameEventMessage", Collections.emptyList());
        currentNotification.put("AddFriendship", Collections.emptyList());
        currentNotification.put("AddFollowship", Collections.emptyList());
        currentNotification.put("AddFriendshipInvitation", Collections.emptyList());
        currentNotification.put("AddNickname", Collections.emptyList());
        currentNotification.put("AddAlert", Collections.emptyList());
        currentNotification.put("ClearAlert", Collections.emptyList());
        currentNotification.put("ClearMemberChatHistory", Collections.emptyList());
        currentNotification.put("ClearUnreadMessageCount", Collections.emptyList());
        currentNotification.put("RemoveChatThreadMembership", Collections.emptyList());
        currentNotification.put("RemoveChatThreadNickname", Collections.emptyList());
        currentNotification.put("RemoveFriendshipInvitation", Collections.emptyList());
        currentNotification.put("RemoveFriendship", Collections.emptyList());
        currentNotification.put("RemoveFollowship", Collections.emptyList());
        currentNotification.put("RemoveFriendshipTrust", Collections.emptyList());
        currentNotification.put("RemoveNickname", Collections.emptyList());
        currentNotification.put("SetAvatar", Collections.emptyList());
        currentNotification.put("UpdateChatThreadTrustStatus", Collections.emptyList());

        docData.put("CurrentNotification", currentNotification);
        docData.put("NotificationSequenceCounter", 1);

        ApiFuture<WriteResult> addedDocRef = ref.set(docData);
        APICore.getLogger().info("Successfully added default Mix data for new registered user " + record.getUid() + " at: " + addedDocRef.get().getUpdateTime());
    }
}

/*
{
	"data": null,
	"error": {
		"keyCategory": "ACTIONABLE_INPUT",
		"conversationId": null,
		"correlationId": "694f1906-b495-4f4b-aa0e-da96cb448327",
		"errors": [{
			"code": "INVALID_VALUE",
			"category": "ACTIONABLE_INPUT",
			"inputName": "profile.parentEmail",
			"errorId": "e105c2e7-7cf5-4256-bedb-b8807a7835c0",
			"timestamp": "2018-11-03T22:50:20.869-0700",
			"data": null,
			"developerMessage": "The email field is invalid"
		}]
	}
}
 */
