package me.legit.api.game.reward;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.models.reward.Reward;
import org.jetbrains.annotations.NotNull;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class ClaimServerAdded implements Handler {

    public ClaimServerAdded() {
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (Referral)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        // First check if user needs a reward from a referral
        DocumentReference docRef = db.collection("referrals").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();
        if (document.exists()) {
            APICore.getLogger().info("Referral document data for referer (" + uid + "): " + document.getData());
            Boolean needsReward = document.getBoolean("needsReward");
            if (needsReward != null && needsReward) {
                Map<String, Integer> consumables = new HashMap<>();
                consumables.put("GlowStickRainbowNonMember", 3);
                consumables.put("HotChocolateGroupNonMember", 3);
                consumables.put("PizzaGroupNonMember", 3);
                consumables.put("SuperFireworkNonMember", 3);

                Reward referralReward = new Reward.Builder()
                        .withConsumables(consumables)
                        .build();

                ApiFuture<WriteResult> result = docRef.update("needsReward", false);
                APICore.getLogger().info("Updated needs reward at (" + uid + "): " + result.get().getUpdateTime());

                JSONObject finalResponse = new JSONObject();

                APICore.getRewardManager().saveReward(document, referralReward, finalResponse);

                ctx.result(generateReferralRewardResponse(uid, referralReward, finalResponse).toString());
            } else {
                ctx.result("{\"wsEvents\":[],\"claimedRewards\":[]}");
            }
        } else {
            ctx.result("{\"wsEvents\":[],\"claimedRewards\":[]}");
        }
    }

    @SuppressWarnings("Duplicates")
    private JSONObject generateReferralRewardResponse(String uid, Reward reward, JSONObject finalResponse) {
        JSONObject object = new JSONObject();

        JSONObject wsEvents = finalResponse.optJSONObject("wsEvents");
        if (wsEvents != null) {
            JSONArray wsEventsArray = new JSONArray();
            wsEventsArray.put(wsEvents);
            object.put("wsEvents", wsEventsArray);
        } else {
            object.put("wsEvents", Collections.emptyList());
        }

        JSONArray claimedRewards = new JSONArray();
        JSONObject rewardObj = new JSONObject();

        JSONObject rewardId = new JSONObject();
        rewardId.put("definitionId", 0);
        rewardId.put("instanceId", uid);

        rewardObj.put("rewardId", rewardId);
        rewardObj.put("reward", APICore.getRewardManager().getReward(reward));
        claimedRewards.put(rewardObj);

        object.put("claimedRewards", claimedRewards);

        return object;
    }
}
