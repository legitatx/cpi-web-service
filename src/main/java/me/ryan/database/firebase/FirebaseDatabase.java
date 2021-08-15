package me.legit.database.firebase;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import io.sentry.Sentry;
import me.legit.APICore;
import me.legit.database.Database;

import java.io.IOException;
import java.io.InputStream;


public class FirebaseDatabase implements Database {

    private Firestore firestore;

    @Override
    public void setup() {
        APICore.getLogger().info("Initializing Firebase database...");

        try {
            ClassLoader loader = getClass().getClassLoader();
            InputStream serviceAccount = loader.getResourceAsStream("");

            FirebaseOptions options = new FirebaseOptions.Builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .setDatabaseUrl("")
                    .build();

            FirebaseApp.initializeApp(options);
        } catch (IOException ex) {
            APICore.getLogger().severe("An error occurred while attempting to initialize the Firebase database.");
            ex.printStackTrace();
            Sentry.capture(ex);
        }

        firestore = FirestoreClient.getFirestore();
    }

    @Override
    public void disable() {
        APICore.getLogger().info("Disabling Firebase database...");

        try {
            firestore.close();
        } catch (Exception e) {
            e.printStackTrace();
            Sentry.capture(e);
        }
    }

    public Firestore getFirestore() {
        return firestore;
    }
}
