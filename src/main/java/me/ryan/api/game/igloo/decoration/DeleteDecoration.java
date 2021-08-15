package me.legit.api.game.igloo.decoration;

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

import java.util.Base64;
import java.util.Map;

public class DeleteDecoration implements Handler {

    public DeleteDecoration() {
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (DeleteDecoration)");

        String decorationId = ctx.pathParam("id");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();
        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            Map<String, Object> decorationInventoryMap = (Map<String, Object>) document.get("decorationInventory");
            if (decorationInventoryMap != null) {
                decorationInventoryMap.remove(decorationId);

                ApiFuture<WriteResult> updateDecorations = docRef.update("decorationInventory", decorationInventoryMap);
                APICore.getLogger().info("Deleted decoration " + decorationId + " for user " + uid + " at: " + updateDecorations.get().getUpdateTime());
            }
        }
    }
}
