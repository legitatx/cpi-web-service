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

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;

public class UpdateLayout implements Handler {

    private Gson gson;
    private JsonParser parser;

    public UpdateLayout() {
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (UpdateLayout)");

        long layoutId = Long.parseLong(ctx.pathParam("id"));

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();
        DocumentReference docRef = db.collection("igloos").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            List<Map<String, Object>> layouts = (List<Map<String, Object>>) document.get("layouts");
            if (layouts != null) {
                JsonObject requestBody = parser.parse(ctx.body()).getAsJsonObject();
                JsonObject layoutJson = null;

                for (int i = 0; i < layouts.size(); i++) {
                    layoutJson = parser.parse(gson.toJson(layouts.get(i))).getAsJsonObject();
                    if (Long.valueOf(layoutJson.get("layoutId").getAsString()) == layoutId) {
                        APICore.getLogger().info("Found a match for layout ID");

                        long dateTimeModified = ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli();

                        layoutJson.addProperty("lastModifiedDate", dateTimeModified);
                        if (!requestBody.get("zoneId").isJsonNull()) {
                            layoutJson.addProperty("zoneId", requestBody.get("zoneId").getAsString());
                        }
                        if (!requestBody.get("name").isJsonNull()) {
                            layoutJson.addProperty("name", requestBody.get("name").getAsString());
                        }
                        layoutJson.addProperty("lightingId", requestBody.get("lightingId").getAsInt());
                        layoutJson.addProperty("musicId", requestBody.get("musicId").getAsInt());
                        if (!requestBody.get("decorationsLayout").isJsonNull()) {
                            layoutJson.add("decorationsLayout", requestBody.get("decorationsLayout").getAsJsonArray());
                        }
                        if (!requestBody.get("extraInfo").isJsonNull()) {
                            layoutJson.add("extraInfo", requestBody.get("extraInfo").getAsJsonObject());
                        }

                        layouts.remove(i);
                        APICore.getLogger().info("layoutJson = " + layoutJson.toString());
                        layouts.add(i, gson.fromJson(layoutJson.toString(), Map.class));
                        break;
                    }
                }

                ApiFuture<WriteResult> updateLayouts = docRef.update("layouts", layouts);
                APICore.getLogger().info("Updated layout " + layoutId + " for user " + uid + " at: " + updateLayouts.get().getUpdateTime());
                if (layoutJson != null) {
                    APICore.getLogger().info("layoutJson (2) (not null) = " + layoutJson.toString());

                    layoutJson.addProperty("createdDate", layoutJson.get("createdDate").getAsLong());
                    layoutJson.addProperty("layoutId", Long.valueOf(layoutJson.get("layoutId").getAsString()));

                    SavedSceneLayout sceneLayout = gson.fromJson(layoutJson, SavedSceneLayout.class);
                    ctx.result(sceneLayout.toJson(gson));
                }
            }
        }
    }
}
