package me.legit.api.game.breadcrumb;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class RemoveBreadcrumbs implements Handler {

    public RemoveBreadcrumbs() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");

        JSONArray breadcrumbsToRemoveJson = new JSONArray(ctx.body());

        String encodedToken = ctx.header("Authorization").split("Basic ")[1].split(", GAE")[0];
        String token = new String(Base64.getDecoder().decode(encodedToken)).split(":")[0];

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(token, checkRevoked).get();
        String uid = decodedToken.getUid();

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (RemoveBreadcrumbs)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            List<Map<String, Object>> breadcrumbMap = (List<Map<String, Object>>) document.get("breadcrumbs.breadcrumbs");

            if (breadcrumbMap != null) {
                JSONArray breadcrumbsFromDatabaseJson = new JSONArray();
                for (Map<String, Object> equipmentElement : breadcrumbMap) {
                    breadcrumbsFromDatabaseJson.put(equipmentElement);
                }

                for (int i = 0; i < breadcrumbsToRemoveJson.length(); i++) {
                    JSONObject obj = breadcrumbsToRemoveJson.getJSONObject(i);
                    int breadcrumbType = obj.getInt("breadcrumbType");
                    String id = obj.getString("id");

                    for (int j = 0; j < breadcrumbsFromDatabaseJson.length(); j++) {
                        JSONObject obj1 = breadcrumbsFromDatabaseJson.getJSONObject(i);
                        int breadcrumbType1 = obj1.getInt("breadcrumbType");
                        String id1 = obj1.getString("id");

                        if (breadcrumbType == breadcrumbType1 && id.equals(id1)) {
                            breadcrumbsFromDatabaseJson.remove(i);
                        }
                    }
                }

                List<Map<String, Object>> finalMap = new ArrayList<>();
                for (int i = 0; i < breadcrumbsFromDatabaseJson.length(); i++) {
                    JSONObject obj = breadcrumbsFromDatabaseJson.getJSONObject(i);
                    finalMap.add(obj.toMap());
                }
                ApiFuture<WriteResult> updateBreadcrumbs = docRef.update("breadcrumbs.breadcrumbs", finalMap);
                APICore.getLogger().info("Updated breadcrumbs at: " + updateBreadcrumbs.get().getUpdateTime());

                ctx.result(generateBreadcrumbResponse(finalMap.size()).toString());
            } else {
                APICore.getLogger().severe(uid + " - (breadcrumbMap == null) (RemoveBreadcrumbs)");
                Sentry.capture("(breadcrumbMap == null) (RemoveBreadcrumbs)");
                ctx.result(generateBreadcrumbResponse(0).toString());
            }
        } else {
            APICore.getLogger().severe(uid + " - (document.exists() == false) (RemoveBreadcrumbs)");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("(document.exists() == false) (RemoveBreadcrumbs)");

            ctx.status(500);
        }
    }

    private JSONObject generateBreadcrumbResponse(int count) {
        JSONObject result = new JSONObject();
        result.put("breadcrumbCount", count);
        return result;
    }
}
