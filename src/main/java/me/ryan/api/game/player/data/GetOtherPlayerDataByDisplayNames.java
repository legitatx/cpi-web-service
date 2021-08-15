package me.legit.api.game.player.data;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.models.ContentIdentifier;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;

import java.util.Base64;
import java.util.List;

public class GetOtherPlayerDataByDisplayNames extends GetOtherPlayerData implements Handler {

    public GetOtherPlayerDataByDisplayNames() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");

        JsonArray names = new JsonParser().parse(ctx.body()).getAsJsonArray();

        String encodedToken = ctx.header("Authorization").split("Basic ")[1].split(", GAE")[0];
        String token = new String(Base64.getDecoder().decode(encodedToken)).split(":")[0];

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(token, checkRevoked).get();
        String uid = decodedToken.getUid();

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (GetOtherPlayerDataByDisplayNames)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        String clientVersion = ctx.header("X-CP-Client-Version");
        String contentVersion = ctx.header("X-CP-Content-Version");
        String subContentVersion = ctx.header("X-CP-Sub-Content-Version");

        JSONArray dataResponses = new JSONArray();

        for (int i = 0; i < names.size(); i++) {
            String playerName = names.get(i).getAsString();

            ApiFuture<QuerySnapshot> future = db.collection("users").whereEqualTo("name.displayName", playerName).limit(1).get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            if (documents.size() > 0) {
                QueryDocumentSnapshot snapshot = documents.get(0);
                checkForDifferentContentIdentifier(snapshot, decodedToken, new ContentIdentifier(clientVersion, contentVersion, subContentVersion));
                DocumentSnapshot iglooSnapshot = db.collection("igloos").document(snapshot.getId()).get().get();
                dataResponses.put(generateResponse(snapshot, iglooSnapshot, playerName));
            } else {
                dataResponses.put(generateResponse(null, null, playerName));
            }
        }

        ctx.result(dataResponses.toString());
    }
}
