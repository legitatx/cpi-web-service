package me.legit.api.game.igloo.layout;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.common.collect.Lists;
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
import me.legit.models.quest.Quest;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.Base64;
import java.util.List;
import java.util.Map;

public class GetActiveLayout implements Handler {

    private Gson gson;
    private JsonParser parser;

    public GetActiveLayout() {
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (GetActiveLayout)");

        String iglooOwnerId = ctx.body();

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();
        DocumentReference docRef = db.collection("igloos").document(iglooOwnerId);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            Map<String, Object> activeLayout = (Map<String, Object>) document.get("activeLayout");
            if (activeLayout != null) {
                JsonObject layoutJson = parser.parse(gson.toJson(activeLayout)).getAsJsonObject();
                APICore.getLogger().info("Successfully fetched active igloo layout for user " + uid + " at: " + document.getReadTime() + " (GetDecoration)");

                layoutJson.addProperty("createdDate", layoutJson.get("createdDate").getAsLong());
                layoutJson.addProperty("lastModifiedDate", layoutJson.get("lastModifiedDate").getAsLong());
                layoutJson.addProperty("layoutId", Long.valueOf(layoutJson.get("layoutId").getAsString()));

                SavedSceneLayout sceneLayout = gson.fromJson(layoutJson, SavedSceneLayout.class);
                ctx.result(sceneLayout.toJson(gson));
            }
        }
    }
}
