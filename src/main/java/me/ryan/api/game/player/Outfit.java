package me.legit.api.game.player;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@SuppressWarnings("Duplicates")
public class Outfit implements Handler {

    public Outfit() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        APICore.getLogger().info("Called Outfit v1 call!");

        ctx.contentType("application/json");

        JsonArray array = new JsonParser().parse(ctx.body()).getAsJsonObject().getAsJsonArray("parts");
        APICore.getLogger().info(array.toString());

        APICore.getLogger().info("Fetched Array");

        String encodedToken = ctx.header("Authorization").split("Basic ")[1].split(", GAE")[0];
        String token = new String(Base64.getDecoder().decode(encodedToken)).split(":")[0];

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(token, checkRevoked).get();
        String uid = decodedToken.getUid();

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Outfit)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            JSONArray equipmentJson = new JSONArray();
            List<Map<String, Object>> equipmentMap = (List<Map<String, Object>>) document.get("equipment");
            if (equipmentMap != null) {
                APICore.getLogger().info("Successfully fetched equipment data for " + uid + " at: " + document.getReadTime() + " (Outfit)");
                for (Map<String, Object> equipmentElement : equipmentMap) {
                    equipmentJson.put(equipmentElement);
                }

                APICore.getLogger().info("EQUIPMENT JSON: " + equipmentJson.toString());

                JSONArray finalParts = new JSONArray();

                for (JsonElement element : array) {
                    APICore.getLogger().info("CALLED 1");
                    long num = element.getAsLong();
                    APICore.getLogger().info("NUM: " + num);
                    for (int i = 0; i < equipmentJson.length(); i++) {
                        APICore.getLogger().info("ITERATING");
                        JSONObject obj = equipmentJson.getJSONObject(i);
                        APICore.getLogger().info("CALLED 2: " + obj.toString());
                        if (obj.getLong("equipmentId") == num) {
                            APICore.getLogger().info("CALLED 3");
                            finalParts.put(obj);
                            break;
                        }
                    }
                }

                APICore.getLogger().info("FINAL PARTS: " + finalParts.toString());
                APICore.getLogger().info("FINAL PARTS (LIST): " + finalParts.toList().toString());

                ApiFuture<WriteResult> updateOutfit = docRef.update("outfit", finalParts.toList());
                APICore.getLogger().info("Updated outfit at: " + updateOutfit.get().getUpdateTime());

                JSONObject outfitResponse = generateOutfitResponse(finalParts, uid);
                persistSignedResponse(outfitResponse, decodedToken);
                ctx.result(outfitResponse.toString());
            } else {
                APICore.getLogger().info(uid + " - (equipment == null) (Outfit)");
                Sentry.capture("(equipment == null) (Outfit)");

                JSONObject outfitResponse = generateOutfitResponse(new JSONArray(), uid);
                persistSignedResponse(outfitResponse, decodedToken);
                ctx.result(outfitResponse.toString());
            }
        } else {
            APICore.getLogger().severe(uid + " - (document.exists() == false) (Outfit)");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("(document.exists() == false) (Outfit)");

            ctx.status(500);
        }
    }

    private JSONObject generateOutfitResponse(JSONArray data, String uid) {
        JSONObject finalResponse = new JSONObject();

        JSONObject parts = new JSONObject();
        parts.put("parts", data);

        finalResponse.put("data", parts);
        finalResponse.put("swid", uid);

        LocalDateTime time = LocalDateTime.now().plusMinutes(5);
        ZoneId zoneId = ZoneId.systemDefault();
        long epoch = time.atZone(zoneId).toInstant().toEpochMilli();
        finalResponse.put("goodUntil", epoch);

        byte[] hash = Utilities.getSignatureGenerator().hashString(parts.toString(), StandardCharsets.UTF_8).asBytes();
        finalResponse.put("signature", Base64.getEncoder().encodeToString(hash));

        return finalResponse;
    }

    private void persistSignedResponse(JSONObject data, FirebaseToken token) {
        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> profileStorage = redis.set(token.getName() + "-outfitUpdate", data.toString());
        profileStorage.thenAccept(string -> APICore.getLogger().info("Successfully stored outfit update data for user " + token.getName() + " (" + token.getUid() + ") in Redis!"));
    }
}
