package me.legit.api.game.igloo.layout;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.*;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import org.jetbrains.annotations.NotNull;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class CreateLayout implements Handler {

    private Gson gson;

    public CreateLayout() {
        this.gson = new GsonBuilder().serializeNulls().create();
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (CreateLayout)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();
        DocumentReference docRef = db.collection("igloos").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            List<Map<String, Object>> layouts = (List<Map<String, Object>>) document.get("layouts");
            if (layouts != null) {
                int count = layouts.size();

                if (count >= 3) {
                    Sentry.capture("User tried to create more than 3 igloo layouts!");
                    ctx.status(403);
                    return;
                }

                long layoutId = ThreadLocalRandom.current().nextLong();
                long dateTimeCreated = ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli();

                JsonObject requestBody = new JsonParser().parse(ctx.body()).getAsJsonObject();
                JsonObject savedSceneLayout = new JsonObject();

                savedSceneLayout.addProperty("createdDate", dateTimeCreated);
                if (!requestBody.get("decorationsLayout").isJsonNull()) {
                    savedSceneLayout.add("decorationsLayout", requestBody.get("decorationsLayout"));
                } else {
                    savedSceneLayout.add("decorationsLayout", new JsonArray());
                }
                if (!requestBody.get("extraInfo").isJsonNull()) {
                    savedSceneLayout.add("extraInfo", requestBody.get("extraInfo"));
                } else {
                    savedSceneLayout.add("extraInfo", new JsonObject());
                }
                savedSceneLayout.addProperty("lastModifiedDate", dateTimeCreated);
                savedSceneLayout.addProperty("layoutId", String.valueOf(layoutId));
                savedSceneLayout.addProperty("lightingId", requestBody.get("lightingId").getAsInt());
                savedSceneLayout.addProperty("memberOnly", true);
                savedSceneLayout.addProperty("musicId", requestBody.get("musicId").getAsInt());
                savedSceneLayout.add("name", requestBody.get("name"));
                savedSceneLayout.addProperty("zoneId", requestBody.get("zoneId").getAsString());

                Map layoutMap = gson.fromJson(savedSceneLayout.toString(), Map.class);

                if (count == 0) {
                    ApiFuture<WriteResult> updateLayout = docRef.update("activeLayout", layoutMap);
                    APICore.getLogger().info("Updated active layout for user " + uid + " to " + savedSceneLayout.toString() + " at: " + updateLayout.get().getUpdateTime());
                    ApiFuture<WriteResult> updateLayoutId = docRef.update("activeLayoutId", String.valueOf(layoutId));
                    APICore.getLogger().info("Updated active layout ID for user " + uid + " to " + layoutId + " at: " + updateLayoutId.get().getUpdateTime());
                }

                ApiFuture<WriteResult> updateLayout = docRef.update("layouts", FieldValue.arrayUnion(layoutMap));
                APICore.getLogger().info("Added new layout for user " + uid + " (" + savedSceneLayout.toString() + ") at: " + updateLayout.get().getUpdateTime());

                savedSceneLayout.addProperty("layoutId", Long.valueOf(savedSceneLayout.get("layoutId").getAsString()));
                ctx.result(savedSceneLayout.toString());
            }
        }
    }
}
