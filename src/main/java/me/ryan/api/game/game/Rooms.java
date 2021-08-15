package me.legit.api.game.game;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class Rooms implements Handler {

    public Rooms() {
        //TODO -- Closed alpha only needs 1 room and world
        //TODO -- Before open beta, update this to support multiple worlds otherwise rooms will be too crowded
        // Instead of checking room population, probably best to just randomize where the user joins
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

        String clientVersion = ctx.header("X-CP-Client-Version");
        String contentVersion = ctx.header("X-CP-Content-Version");

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Rooms)");
        APICore.getLogger().info("Content version for user " + uid + " is: " + contentVersion + " (" + clientVersion + ")");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            String room = ctx.queryParam("rooms");
            if (room.contains(",")) {
                room = room.split(",")[0];
            }

            JSONObject onlineLocation = new JSONObject();
            onlineLocation.put("world", "CPI");
            onlineLocation.put("language", 1);
            JSONObject zoneId = new JSONObject();
            zoneId.put("name", room);
            zoneId.put("instanceId", "");
            onlineLocation.put("zoneId", zoneId);
            LocalDate today = LocalDate.now(ZoneId.of("GMT"));
            String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
            onlineLocation.put("contentIdentifier", clientVersion + ";" + contentVersion + ";" + todayStr + ";NONE");
            onlineLocation.put("room", room);

            JSONArray result = new JSONArray();
            result.put(onlineLocation);
            ctx.result(result.toString());
        } else {
            APICore.getLogger().severe(uid + " - (document.exists() == false) (Rooms)");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("(document.exists() == false) (Rooms)");

            ctx.status(500);
        }
    }
}
