package me.legit.api.game.igloo.layout;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.models.igloo.SavedSceneLayout;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.Base64;
import java.util.List;
import java.util.Map;

public class GetLayout implements Handler {

    private Gson gson;
    private JsonParser parser;

    public GetLayout() {
        this.gson = new GsonBuilder().serializeNulls().create();
        this.parser = new JsonParser();
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (GetLayout)");

        long layoutId = Long.parseLong(ctx.pathParam("id"));

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();
        DocumentReference docRef = db.collection("igloos").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            List<Map<String, Object>> layouts = (List<Map<String, Object>>) document.get("layouts");
            if (layouts != null) {
                for (Map<String, Object> layout : layouts) {
                    JsonObject layoutJson = parser.parse(gson.toJson(layout)).getAsJsonObject();
                    if (layoutJson.get("layoutId").getAsLong() == layoutId) {
                        APICore.getLogger().info("Successfully fetched layout needed (" + layoutId + ") for " + uid + " at: " + document.getReadTime() + " (GetDecoration)");

                        layoutJson.addProperty("createdDate", layoutJson.get("createdDate").getAsLong());
                        layoutJson.addProperty("lastModifiedDate", layoutJson.get("lastModifiedDate").getAsLong());
                        layoutJson.addProperty("layoutId", Long.valueOf(layoutJson.get("layoutId").getAsString()));

                        SavedSceneLayout sceneLayout = gson.fromJson(layoutJson, SavedSceneLayout.class);
                        ctx.result(sceneLayout.toJson(gson));

                        break;
                    }
                }
            }
        }
    }
}
