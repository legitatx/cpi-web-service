package me.legit.api.game.tutorial;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;
import sun.security.util.BitArray;

import java.util.Base64;
import java.util.Collections;
import java.util.List;

@SuppressWarnings("unchecked")
public class Tutorial implements Handler {

    public Tutorial() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");

        JsonObject object = new JsonParser().parse(ctx.body()).getAsJsonObject();
        int tutorialId = object.get("tutorialID").getAsInt();
        boolean complete = object.get("isComplete").getAsBoolean();

        String encodedToken = ctx.header("Authorization").split("Basic ")[1].split(", GAE")[0];
        String token = new String(Base64.getDecoder().decode(encodedToken)).split(":")[0];

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(token, checkRevoked).get();
        String uid = decodedToken.getUid();

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Tutorial)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            Blob tutorialData = (Blob) document.get("tutorialData");
            APICore.getLogger().info("Successfully fetched tutorial data for " + uid + " at: " + document.getReadTime());

            byte[] tutorialDataBytes = tutorialData.toBytes();
            byte[] array = new byte[tutorialDataBytes.length];
            for (int i = 0; i < array.length; i++) {
                array[i] = tutorialDataBytes[i];
            }
            BitArray bitArray = new BitArray(array.length, array);
            bitArray.set(tutorialId, complete);
            array = bitArray.toByteArray();
            for (int i = 0; i < array.length; i++) {
                tutorialDataBytes[i] = array[i];
            }

            ApiFuture<WriteResult> updateTutorialData = docRef.update("tutorialData", Blob.fromBytes(tutorialDataBytes));
            APICore.getLogger().info("Updated tutorial data at: " + updateTutorialData.get().getUpdateTime());

            ctx.result(generateTutorialResponse(tutorialDataBytes).toString());
        } else {
            APICore.getLogger().severe(uid + " - (document.exists() == false) (Tutorial)");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("(document.exists() == false) (Tutorial)");

            ctx.status(500);
        }
    }

    private JSONObject generateTutorialResponse(byte[] data) {
        JSONObject result = new JSONObject();
        result.put("wsEvents", Collections.emptyList());

        JSONArray tutorialBytes = new JSONArray();
        for (byte tutorialByte : data) {
            tutorialBytes.put(tutorialByte);
        }

        result.put("tutorialBytes", tutorialBytes);

        return result;
    }
}
