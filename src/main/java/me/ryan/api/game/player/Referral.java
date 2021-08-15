package me.legit.api.game.player;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.common.collect.Lists;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import me.legit.utils.Utilities;
import org.jetbrains.annotations.NotNull;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Referral implements Handler {

    public Referral() {
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

        String referredByRaw = ctx.body();
        APICore.getLogger().info("New user " + uid + " was referred by " + referredByRaw + "!");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        String referredByUid = Utilities.getPlayerUidFromName(db, referredByRaw);
        if (referredByUid != null) {
            DocumentReference docRef = db.collection("referrals").document(referredByUid);
            ApiFuture<DocumentSnapshot> future = docRef.get();
            DocumentSnapshot document = future.get();
            if (document.exists()) {
                APICore.getLogger().info("Referral document data for referer ( " + referredByUid + " - " + referredByRaw + "): " + document.getData());
                updateReferralData(docRef, referredByUid);
                ctx.status(200);
            } else {
                APICore.getLogger().info("Document did not exist for referer ( " + referredByUid + " - " + referredByRaw + "): Adding new data now...");
                // Method that adds a new referral for the user which was typed in at registeration
                addNewReferral(db, referredByUid, uid);
                // Method that declares the current user was referred to the game by someone
                addNewReferredBy(db, uid, referredByUid);
                ctx.status(200);
            }
        } else {
            // Just return HTTP OK response for now if we somehow couldn't find the user in our registered user list, although this really should NEVER happen
            // There's seriously no point bothering with errors for an endpoint such as this but we can come back to this later if needed
            ctx.status(200);
        }
    }

    private void updateReferralData(DocumentReference document, String uid) throws Exception {
        WriteBatch batch = document.getFirestore().batch();

        batch.update(document, "newUsersReferred", FieldValue.arrayUnion(uid));
        batch.update(document, "needsReward", true);

        ApiFuture<List<WriteResult>> future = batch.commit();

        for (WriteResult result : future.get()) {
            APICore.getLogger().info("Updated referral data for user " + uid + " at: " + result.getUpdateTime());
        }
    }

    private void addNewReferral(Firestore dbInstance, String referer, String referredUser) throws Exception {
        Map<String, Object> docData = new HashMap<>();
        docData.put("newUsersReferred", Lists.newArrayList(referredUser));
        docData.put("needsReward", true);
        docData.put("referredBy", "");
        ApiFuture<WriteResult> future = dbInstance.collection("referrals").document(referer).set(docData);
        APICore.getLogger().info("Added new referral for " + referer + ": " + future.get().getUpdateTime());
    }

    private void addNewReferredBy(Firestore dbInstance, String uid, String referredBy) throws Exception {
        Map<String, Object> docData = new HashMap<>();
        docData.put("newUsersReferred", Lists.newArrayList());
        docData.put("needsReward", true);
        docData.put("referredBy", referredBy);
        ApiFuture<WriteResult> future = dbInstance.collection("referrals").document(uid).set(docData);
        APICore.getLogger().info("Now declared that " + uid + " has been referred by " + referredBy + ": " + future.get().getUpdateTime());
    }
}
