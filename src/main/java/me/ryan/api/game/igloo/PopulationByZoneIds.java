package me.legit.api.game.igloo;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import org.jetbrains.annotations.NotNull;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class PopulationByZoneIds implements Handler {

    public PopulationByZoneIds() {
        //TODO room population (redis or smartfox client?)
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (PopulationByZoneIds)");

        JsonArray friendIglooInstances = new JsonParser().parse(ctx.body()).getAsJsonArray();
        JsonArray finalResponse = new JsonArray();
        for (JsonElement friendId : friendIglooInstances) {
            finalResponse.add(generatePopulationResponse(friendId.getAsJsonObject(), clientVersion, contentVersion));
        }
        ctx.result(finalResponse.toString());
    }

    private JsonObject generatePopulationResponse(JsonObject friendObj, String clientVersion, String contentVersion) {
        String zoneName = friendObj.get("name").getAsString();
        String instanceId = friendObj.get("instanceId").getAsString();

        JsonObject finalResponse = new JsonObject();

        //TODO scale population by amount of users in room
        JsonObject identifier = new JsonObject();
        identifier.addProperty("world", "Igloo");
        identifier.addProperty("language", 1);
        JsonObject zoneId = new JsonObject();
        zoneId.addProperty("name", zoneName);
        zoneId.addProperty("instanceId", instanceId);
        identifier.add("zoneId", zoneId);
        LocalDate today = LocalDate.now(ZoneId.of("GMT"));
        String todayStr = today.format(DateTimeFormatter.ISO_LOCAL_DATE);
        identifier.addProperty("contentIdentifier", clientVersion + ";" + contentVersion + ";" + todayStr + ";NONE");
        identifier.addProperty("room", zoneName);
        finalResponse.add("identifier", identifier);
        finalResponse.addProperty("populationScaled", 0);

        return finalResponse;
    }
}
