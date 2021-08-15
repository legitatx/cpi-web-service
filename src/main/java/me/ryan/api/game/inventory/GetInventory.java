package me.legit.api.game.inventory;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unchecked")
public class GetInventory implements Handler {

    public GetInventory() {
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (GetInventory)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();

        JSONArray equipmentJson = new JSONArray();

        if (document.exists()) {
            List<Map<String, Object>> equipmentMap = (List<Map<String, Object>>) document.get("equipment");
            if (equipmentMap != null) {
                APICore.getLogger().info("Successfully fetched equipment data for " + uid + " at: " + document.getReadTime() + " (GetInventory)");

                for (Map<String, Object> equipmentElement : equipmentMap) {
                    equipmentJson.put(equipmentElement);
                }
            }
        }

        ctx.result(equipmentJson.toString());
    }
}
