package me.legit.api.game.igloo;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.TimeUnit;

public class Popular implements Handler {

    public Popular() {
        //TODO query igloo rooms by amount of players
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (UpdateData)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        ApiFuture<QuerySnapshot> future = db.collection("igloos").whereEqualTo("visibility", 2).limit(9).get();
        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        JSONArray array = new JSONArray();
        for (DocumentSnapshot document : documents) {
            if (document.get("activeLayout") != null && document.get("activeLayoutId") != null) {
                DocumentSnapshot userSnapshot = db.collection("users").document(document.getId()).get().get();
                array.put(generateResponse(userSnapshot, document.getId()));
            }
        }
        ctx.result(array.toString());
    }

    private JSONObject generateResponse(DocumentSnapshot snapshot, String swid) {
        JSONObject ownerData = new JSONObject();
        JSONObject finalResponse = new JSONObject();

        if (snapshot != null) {
            JSONObject id = new JSONObject();
            id.put("id", swid);
            id.put("type", 1);

            ownerData.put("id", id);
            ownerData.put("name", new JSONObject((Map<String, Object>) snapshot.get("name")).getString("displayName"));

            try {
                Map<String, Object> location = (Map<String, Object>) snapshot.get("onlineLocation");
                if (location != null) {
                    ownerData.put("onlineLocation", new JSONObject(location));
                }
            } catch (ClassCastException e) {
                ownerData.put("onlineLocation", JSONObject.NULL);
            }

            ownerData.put("member", true);

            try {
                List<Map<String, Object>> outfit = (List<Map<String, Object>>) snapshot.get("outfit");
                if (outfit != null) {
                    ownerData.put("outfit", new JSONArray(outfit));
                }
            } catch (ClassCastException e) {
                ownerData.put("outfit", JSONObject.NULL);
            }

            try {
                Map<String, Object> profile = (Map<String, Object>) snapshot.get("profile");
                if (profile != null) {
                    JSONObject profileJson = new JSONObject(profile);
                    profileJson.put("daysOld", Utilities.getDateDifference(snapshot.getCreateTime().toDate(), new Date(), TimeUnit.DAYS));
                    ownerData.put("profile", profileJson);
                }
            } catch (ClassCastException e) {
                ownerData.put("profile", JSONObject.NULL);
            }

            try {
                Map<String, Object> mascotXP = (Map<String, Object>) snapshot.get("assets.mascotXP");
                if (mascotXP != null) {
                    ownerData.put("mascotXP", new JSONObject(mascotXP));
                }
            } catch (ClassCastException e) {
                ownerData.put("mascotXP", JSONObject.NULL);
            }

            try {
                Map<String, Object> zoneId = (Map<String, Object>) snapshot.get("zoneId");
                if (zoneId != null) {
                    ownerData.put("zoneId", new JSONObject(zoneId));
                }
            } catch (ClassCastException e) {
                ownerData.put("zoneId", JSONObject.NULL);
            }
        } else {
            JSONObject id = new JSONObject();
            id.put("id", swid);
            id.put("type", 1);

            ownerData.put("id", id);
            ownerData.put("name", JSONObject.NULL);

            ownerData.put("onlineLocation", JSONObject.NULL);
            ownerData.put("member", false);
            ownerData.put("outfit", JSONObject.NULL);
            ownerData.put("profile", JSONObject.NULL);
            ownerData.put("mascotXP", JSONObject.NULL);
            ownerData.put("zoneId", JSONObject.NULL);
            ownerData.put("iglooPopulation", 0);
        }

        finalResponse.put("ownerData", ownerData);
        //TODO
        finalResponse.put("iglooPopulation", 0);

        return finalResponse;
    }
}
