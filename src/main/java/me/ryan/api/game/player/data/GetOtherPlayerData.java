package me.legit.api.game.player.data;

import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.auth.FirebaseToken;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.EventBuilder;
import me.legit.models.ContentIdentifier;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public abstract class GetOtherPlayerData implements Handler {

    @Override
    public abstract void handle(@NotNull Context ctx) throws Exception;

    void checkForDifferentContentIdentifier(DocumentSnapshot userToSearch, FirebaseToken currentUser, ContentIdentifier contentIdentifier) {
        Map<String, Object> onlineLocation = (Map<String, Object>) userToSearch.get("onlineLocation");
        if (onlineLocation != null) {
            String playerIdentifier = (String) onlineLocation.get("contentIdentifier");
            String thisUserIdentifier = contentIdentifier.getClientVersion() + ";" + contentIdentifier.getContentVersion() + ";" + contentIdentifier.getSubContentVersion() + ";NONE";
            if (!playerIdentifier.equals(thisUserIdentifier)) {
                Sentry.capture(new EventBuilder()
                        .withMessage("Different content identifier(s)")
                        .withTag(userToSearch.getId(), playerIdentifier)
                        .withTag(currentUser.getUid(), thisUserIdentifier)
                );
            }
        }
    }

    JSONObject generateResponse(DocumentSnapshot snapshot, DocumentSnapshot iglooSnapshot, String name) {
        JSONObject object = new JSONObject();

        if (snapshot != null) {
            String snapshotName = snapshot.getString("name.displayName");

            JSONObject id = new JSONObject();
            id.put("id", snapshotName);
            id.put("type", 3);

            object.put("id", id);
            object.put("name", snapshotName);

            try {
                Map<String, Object> location = (Map<String, Object>) snapshot.get("onlineLocation");
                if (location != null) {
                    object.put("onlineLocation", new JSONObject(location));
                }
            } catch (ClassCastException e) {
                object.put("onlineLocation", JSONObject.NULL);
            }

            object.put("member", true);

            try {
                List<Map<String, Object>> outfit = (List<Map<String, Object>>) snapshot.get("outfit");
                if (outfit != null) {
                    object.put("outfit", new JSONArray(outfit));
                }
            } catch (ClassCastException e) {
                object.put("outfit", JSONObject.NULL);
            }

            try {
                Map<String, Object> profile = (Map<String, Object>) snapshot.get("profile");
                if (profile != null) {
                    JSONObject profileJson = new JSONObject(profile);
                    profileJson.put("daysOld", Utilities.getDateDifference(snapshot.getCreateTime().toDate(), new Date(), TimeUnit.DAYS));
                    object.put("profile", profileJson);
                }
            } catch (ClassCastException e) {
                object.put("profile", JSONObject.NULL);
            }

            try {
                Map<String, Object> mascotXP = (Map<String, Object>) snapshot.get("assets.mascotXP");
                if (mascotXP != null) {
                    object.put("mascotXP", new JSONObject(mascotXP));
                }
            } catch (ClassCastException e) {
                object.put("mascotXP", JSONObject.NULL);
            }

            try {
                Map<String, Object> zoneId = (Map<String, Object>) snapshot.get("zoneId");
                if (zoneId != null) {
                    JSONObject iglooData = new JSONObject(iglooSnapshot.getData());
                    if (iglooData.opt("activeLayout") != null && iglooData.opt("activeLayoutId") != null) {
                        zoneId.put("name", iglooData.getJSONObject("activeLayout").getString("zoneId"));
                    }
                    object.put("zoneId", new JSONObject(zoneId));
                }
            } catch (ClassCastException e) {
                object.put("zoneId", JSONObject.NULL);
            }
        } else {
            JSONObject id = new JSONObject();
            id.put("id", name);
            id.put("type", 3);

            object.put("id", id);
            object.put("name", name);

            object.put("onlineLocation", JSONObject.NULL);
            object.put("member", false);
            object.put("outfit", JSONObject.NULL);
            object.put("profile", JSONObject.NULL);
            object.put("mascotXP", JSONObject.NULL);
            object.put("zoneId", JSONObject.NULL);
        }

        return object;
    }
}
