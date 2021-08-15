package me.legit.api.game.reward;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.Base64;
import java.util.Map;

public class CalculateExchangeAll implements Handler {

    public CalculateExchangeAll() {
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (CalculateExchangeAll)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();

        if (document.exists()) {
            Map<String, Object> collectibleCurrenciesMap = (Map<String, Object>) document.get("assets.collectibleCurrencies");
            if (collectibleCurrenciesMap != null) {
                APICore.getLogger().info("Successfully fetched collectible currencies data for " + uid + " at: " + document.getReadTime() + " (CalculateExchangeAll)");

                JSONObject collectibleCurrenciesJson = new JSONObject(collectibleCurrenciesMap);
                int coins = APICore.getClaimableRewardService().getCoinsForExchange(collectibleCurrenciesJson);

                ctx.result(new JSONObject().put("coins", coins).toString());
            } else {
                APICore.getLogger().severe(uid + " - (collectibleCurrenciesMap == null) (CalculateExchangeAll)");
                Sentry.capture("(collectibleCurrenciesMap == null) (CalculateExchangeAll)");

                ctx.result(new JSONObject().put("coins", 0).toString());
            }
        } else {
            APICore.getLogger().severe(uid + " - (document.exists() == false) (CalculateExchangeAll)");
            Sentry.capture("(document.exists() == false) (CalculateExchangeAll)");

            Sentry.getContext().addExtra("halted", true);
            ctx.status(500);
        }
    }
}
