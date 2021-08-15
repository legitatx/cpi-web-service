package me.legit.api.game.igloo.decoration;

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
import me.legit.models.decoration.DecorationId;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Base64;
import java.util.Map;

public class GetDecoration implements Handler {

    public GetDecoration() {
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (GetDecoration)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();
        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();

        JSONObject result = new JSONObject();

        if (document.exists()) {
            Map<String, Object> decorationInventoryMap = (Map<String, Object>) document.get("decorationInventory");
            if (decorationInventoryMap != null) {
                APICore.getLogger().info("Successfully fetched decoration inventory for " + uid + " at: " + document.getReadTime() + " (GetDecoration)");

                JSONArray items = new JSONArray();
                for (Map.Entry<String, Object> decorationInventory : decorationInventoryMap.entrySet()) {
                    DecorationId id = DecorationId.fromString(decorationInventory.getKey());
                    Object count = decorationInventory.getValue();

                    JSONObject item = new JSONObject();
                    JSONObject decorationId = new JSONObject();
                    decorationId.put("definitionId", id.getDefinitionId());
                    decorationId.put("type", id.getType().ordinal());
                    if (id.getCustomId() == null) {
                        decorationId.put("customId", JSONObject.NULL);
                    } else {
                        decorationId.put("customId", id.getCustomId());
                    }
                    item.put("decorationId", decorationId);
                    item.put("count", count);

                    items.put(item);
                }
                result.put("items", items);
            }
        }

        ctx.result(result.toString());
    }
}
