package me.legit.api.game.consumable;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisStringAsyncCommands;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Map;

public class Use implements Handler {

    public Use() {
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Use)");

        String type = ctx.pathParam("type");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            int partialCount = updateInventory(document, type);

            JSONObject usedConsumable = new JSONObject();
            usedConsumable.put("partialCount", partialCount);
            usedConsumable.put("type", type);

            JSONObject signedResponse = generateSignedResponse(usedConsumable, uid);
            persistSignedResponse(signedResponse, decodedToken, "usedConsumable");

            ctx.result(signedResponse.toString());
        } else {
            APICore.getLogger().severe(uid + " - (document.exists() == false) (Use)");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("(document.exists() == false) (Use)");

            ctx.status(500);
        }
    }

    private int updateInventory(DocumentSnapshot snapshot, String type) throws Exception {
        Map<String, Object> consumableInventoryType = (Map<String, Object>) snapshot.get("consumableInventory.inventoryMap." + type);
        JSONObject typeObject;
        if (consumableInventoryType == null) {
            APICore.getLogger().info("Inventory type is null for type " + type + "... initializing temporary consumable object...");
            typeObject = new JSONObject();
            typeObject.put("itemCount", 0);
            typeObject.put("partialCount", 0);
            typeObject.put("lastPurchaseTimestamp", LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        } else {
            APICore.getLogger().info("Successfully fetched consumable inventory data for " + snapshot.getId() + " at: " + snapshot.getReadTime() + " (Use)");
            typeObject = new JSONObject(consumableInventoryType);
        }
        int partialCount = typeObject.getInt("partialCount");
        if (partialCount > 0) {
            typeObject.put("partialCount", 0);
        } else {
            typeObject.put("itemCount", typeObject.getInt("itemCount") - 1);
            if (typeObject.getInt("itemCount") <= 0) {
                APICore.getLogger().info("Item count for type " + type + " is less than or equal to 0... Removing...");
                typeObject = null;
            }
        }
        if (typeObject == null) {
            if (consumableInventoryType != null) {
                ApiFuture<WriteResult> writeResult = snapshot.getReference().update("consumableInventory.inventoryMap." + type, FieldValue.delete());
                APICore.getLogger().info("Successfully deleted type " + type + " for " + snapshot.getId() + " in consumable inventory at: " + writeResult.get().getUpdateTime());
            }
        } else {
            ApiFuture<WriteResult> writeResult = snapshot.getReference().update("consumableInventory.inventoryMap." + type, typeObject.toMap());
            APICore.getLogger().info("Successfully updated type " + type + " for " + snapshot.getId() + " in consumable inventory at: " + writeResult.get().getUpdateTime() + " - (Data: " + typeObject.toString() + ")");
        }
        return partialCount;
    }

    @SuppressWarnings("Duplicates")
    private JSONObject generateSignedResponse(JSONObject data, String uid) {
        JSONObject finalResponse = new JSONObject();

        finalResponse.put("data", data);
        finalResponse.put("swid", uid);

        LocalDateTime time = LocalDateTime.now().plusMinutes(5);
        ZoneId zoneId = ZoneId.systemDefault();
        long epoch = time.atZone(zoneId).toInstant().toEpochMilli();
        finalResponse.put("goodUntil", epoch);

        byte[] hash = Utilities.getSignatureGenerator().hashString(data.toString(), StandardCharsets.UTF_8).asBytes();
        finalResponse.put("signature", Base64.getEncoder().encodeToString(hash));

        return finalResponse;
    }

    private void persistSignedResponse(JSONObject object, FirebaseToken token, String type) {
        RedisStringAsyncCommands<String, String> redis = APICore.getDatabaseManager().redis().connection().get(2).async();
        RedisFuture<String> storage = redis.set(token.getName() + "-" + type, object.toString());
        storage.thenAccept(string -> APICore.getLogger().info("Successfully stored " + type + " data for user " + token.getName() + " (" + token.getUid() + ") in Redis!"));
    }
}
