package me.legit.api.game.player.data;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import java.util.Base64;

public class GetOnlinePlayersBySwids implements Handler {

    public GetOnlinePlayersBySwids() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");

        JsonArray swids = new JsonParser().parse(ctx.body()).getAsJsonArray();

        String encodedToken = ctx.header("Authorization").split("Basic ")[1].split(", GAE")[0];
        String token = new String(Base64.getDecoder().decode(encodedToken)).split(":")[0];

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(token, checkRevoked).get();
        String uid = decodedToken.getUid();

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (GetOnlinePlayersBySwids)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        JSONArray online = new JSONArray();

        for (int i = 0; i < swids.size(); i++) {
            String swid = swids.get(i).getAsString();

            DocumentReference docRef = db.collection("users").document(swid);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot document = future.get();

            if (document.exists() && document.get("onlineLocation") != null) {
                online.put(swid);
            }
        }

        ctx.result(online.toString());
    }
}
