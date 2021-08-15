package me.legit.api.game.player.data;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.Blob;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.api.game.quest.Progress;
import me.legit.models.quest.Quest;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import sun.security.util.BitArray;

import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Player implements Handler {

    private Gson gson;

    public Player() {
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Player)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();
        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            DocumentSnapshot iglooDoc = db.collection("igloos").document(uid).get().get();

            JSONObject playerData = new JSONObject(document.getData());
            JSONObject iglooData = new JSONObject(iglooDoc.getData());

            Blob tutorialData = (Blob) document.get("tutorialData");
            byte[] tutorialDataBytes = tutorialData.toBytes();
            JSONArray tutorialDataArray = new JSONArray();
            for (byte tutorialByte : tutorialDataBytes) {
                tutorialDataArray.put(tutorialByte);
            }

            ctx.result(generatePlayerRoomData(playerData, iglooData, tutorialDataArray, document).toString());
        } else {
            APICore.getLogger().severe("Somehow failed to find a users document for user with ID " + uid + "! This should never happen.");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("Somehow failed to find a users document for user with ID! This should never happen.");

            //return halt(714); should return player not found in client???
            ctx.status(500); // dont want to take chances for now with weird error responses, just return 500 for now, will verify the 714 error code later
        }
    }

    private JSONObject generatePlayerRoomData(JSONObject data, JSONObject iglooData, JSONArray tutorialData, DocumentSnapshot snapshot) {
        JSONObject playerData = new JSONObject();

        playerData.put("id", data.getJSONObject("id"));
        playerData.put("name", data.getJSONObject("name").getString("displayName"));
        playerData.put("onlineLocation", data.optJSONObject("onlineLocation"));
        playerData.put("outfit", data.getJSONArray("outfit"));

        JSONObject profile = data.getJSONObject("profile");
        profile.put("daysOld", Utilities.getDateDifference(snapshot.getCreateTime().toDate(), new Date(), TimeUnit.DAYS));
        playerData.put("profile", profile);

        playerData.put("mascotXP", data.getJSONObject("assets").getJSONObject("mascotXP"));
        JSONObject zoneId = data.getJSONObject("zoneId");
        if (iglooData.opt("activeLayout") != null && iglooData.opt("activeLayoutId") != null) {
            zoneId.put("name", iglooData.getJSONObject("activeLayout").getString("zoneId"));
        }
        playerData.put("zoneId", zoneId);
        playerData.put("minigameProgress", data.getJSONArray("minigameProgress"));

        String firstQuestData = data.getJSONArray("quests").toString();
        List<Quest> quests = Progress.generateQuestStateResponseBySnapshot(firstQuestData, snapshot, true);
        String finalQuestData = gson.toJson(quests);
        playerData.put("quests", new JSONArray(finalQuestData));

        playerData.put("membershipExpireDate", 0);
        playerData.put("subscriptionVendor", JSONObject.NULL);
        playerData.put("subscriptionProductId", JSONObject.NULL);
        playerData.put("subscriptionPaymentPending", false);
        playerData.put("migrationData", JSONObject.NULL);

        playerData.put("tutorialData", tutorialData);
        playerData.put("breadcrumbs", data.getJSONObject("breadcrumbs"));
        playerData.put("claimedRewardIds", data.getJSONArray("claimedRewardIds"));
        playerData.put("dailySpinData", data.getJSONObject("dailySpinData"));

        JSONObject iglooLayouts = new JSONObject();
        iglooLayouts.put("visibility", iglooData.getInt("visibility"));
        String activeLayoutId = iglooData.optString("activeLayoutId");
        if (!activeLayoutId.isEmpty()) {
            iglooLayouts.put("activeLayoutId", Long.valueOf(iglooData.optString("activeLayoutId")));
        } else {
            iglooLayouts.put("activeLayoutId", JSONObject.NULL);
        }
        JSONArray layouts = iglooData.getJSONArray("layouts");
        for (int i = 0; i < layouts.length(); i++) {
            JSONObject object = layouts.getJSONObject(i);
            JSONObject iglooSummary = new JSONObject();

            iglooSummary.put("layoutId", Long.valueOf(object.getString("layoutId")));
            iglooSummary.put("createdDate", object.getLong("createdDate"));
            iglooSummary.put("lastUpdatedDate", object.getLong("lastModifiedDate"));
            iglooSummary.put("lot", object.getString("zoneId"));
            iglooSummary.put("name", object.optString("name"));
            iglooSummary.put("memberOnly", object.getBoolean("memberOnly"));

            layouts.put(i, iglooSummary);
        }
        iglooLayouts.put("layouts", layouts);
        iglooLayouts.put("activeLayoutServerChangeNotification", 0);
        playerData.put("iglooLayouts", iglooLayouts);

        playerData.put("trialAvailable", true);
        playerData.put("recurring", true);

        return playerData;
    }
}
