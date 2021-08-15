package me.legit.punishment;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import me.legit.APICore;

import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;

public class PunishmentManager {

    private Firestore db;

    public PunishmentManager(Firestore db) {
        this.db = db;
    }

    public PunishmentHistory getPunishmentHistory(String uid) {
        try {
            DocumentSnapshot snapshot = db.collection("punishments").document(uid).get().get();
            PunishmentHistory punishmentHistory = snapshot.toObject(PunishmentHistory.class);
            if (punishmentHistory == null) {
                PunishmentHistory punishment = new PunishmentHistory(0, 0, false, new ArrayList<>());

                ApiFuture<WriteResult> writeResult = snapshot.getReference().set(punishment);
                APICore.getLogger().info("Successfully stored new punishment history in Firebase for user " + uid + " at: " + writeResult.get().getUpdateTime());
                return punishment;
            } else {
                APICore.getLogger().info("Successfully fetched punishment history from Firebase for user " + uid + ": " + punishmentHistory.toString());
                return punishmentHistory;
            }
        } catch (InterruptedException | ExecutionException e) {
            APICore.getLogger().log(Level.SEVERE, "A fatal error occurred while attempting to fetch punishment history for user " + uid + ": ", e);
            e.printStackTrace();
        }
        return null;
    }
}

