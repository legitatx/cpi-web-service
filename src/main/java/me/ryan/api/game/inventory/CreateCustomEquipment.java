package me.legit.api.game.inventory;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.*;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;

@SuppressWarnings("Duplicates")
public class CreateCustomEquipment implements Handler {

    public CreateCustomEquipment() {
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");

        JSONObject object = new JSONObject(ctx.body());

        String encodedToken = ctx.header("Authorization").split("Basic ")[1].split(", GAE")[0];
        String token = new String(Base64.getDecoder().decode(encodedToken)).split(":")[0];

        boolean checkRevoked = true;
        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdTokenAsync(token, checkRevoked).get();
        String uid = decodedToken.getUid();

        Sentry.getContext().setUser(new UserBuilder().setId(uid).build());

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (CreateCustomEquipment)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            long equipmentId = new BigInteger(49, ThreadLocalRandom.current()).longValue();
            long dateTimeCreated = LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

            object.put("equipmentId", equipmentId);
            object.put("dateTimeCreated", dateTimeCreated);
            object.put("source", "None");
            object.put("sourceId", 0);

            ApiFuture<WriteResult> updateEquipment = docRef.update("equipment", FieldValue.arrayUnion(object.toMap()));
            APICore.getLogger().info("Updated equipment at: " + updateEquipment.get().getUpdateTime());

            List<ApiFuture<String>> batch = APICore.getTemplateService().subtractEquipmentCost(document, object.getInt("definitionId"));

            CompletableFuture<String> resultFuture = new CompletableFuture<>();

            ApiFutures.addCallback(ApiFutures.allAsList(batch), new ApiFutureCallback<List<String>>() {
                @Override
                public void onFailure(Throwable t) {
                    APICore.getLogger().severe("An error was thrown while attempting to create new custom equipment for a user!");

                    t.printStackTrace();
                    Sentry.capture(t);

                    ctx.status(500);

                    resultFuture.completeExceptionally(t);
                }

                @Override
                public void onSuccess(List<String> results) {
                    for (String result : results) {
                        APICore.getLogger().info("Successfully completed a transaction while creating new equipment for user " + uid + "?: Result - (" + result + ")");
                    }

                    try {
                        ApiFuture<DocumentSnapshot> future = docRef.get();
                        DocumentSnapshot document = future.get();
                        Map<String, Object> assets = (Map<String, Object>) document.get("assets");
                        JSONObject assetsJson = new JSONObject(assets);

                        resultFuture.complete(generateEquipmentResponse(object, uid, equipmentId, assetsJson).toString());
                    } catch (InterruptedException | ExecutionException e) {
                        APICore.getLogger().severe("An error was thrown while attempting to create new custom equipment for a user (failed to wait for new future)!");

                        e.printStackTrace();
                        Sentry.capture(e);

                        ctx.status(500);

                        resultFuture.completeExceptionally(e);
                    }
                }
            }, MoreExecutors.directExecutor());

            ctx.result(resultFuture);
        } else {
            APICore.getLogger().severe(uid + " - (document.exists() == false) (CreateCustomEquipment)");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("document.exists() == false) (CreateCustomEquipment)");

            ctx.status(500);
        }
    }

    private JSONObject generateEquipmentResponse(JSONObject newEquipment, String uid, long equipmentId, JSONObject assets) {
        JSONObject finalResponse = new JSONObject();

        JSONArray wsEventsArray = new JSONArray();
        JSONObject wsEvents = new JSONObject();

        JSONObject data = new JSONObject();
        data.put("type", 4);
        data.put("details", newEquipment);
        wsEvents.put("data", data);

        wsEvents.put("swid", uid);

        LocalDateTime time = LocalDateTime.now().plusMinutes(5);
        ZoneId zoneId = ZoneId.systemDefault();
        long epoch = time.atZone(zoneId).toInstant().toEpochMilli();
        wsEvents.put("goodUntil", epoch);

        byte[] hash = Utilities.getSignatureGenerator().hashString(newEquipment.toString(), StandardCharsets.UTF_8).asBytes();
        wsEvents.put("signature", Base64.getEncoder().encodeToString(hash));

        wsEventsArray.put(wsEvents);

        finalResponse.put("wsEvents", wsEventsArray);
        finalResponse.put("equipmentId", equipmentId);
        finalResponse.put("assets", assets);

        return finalResponse;
    }
}
