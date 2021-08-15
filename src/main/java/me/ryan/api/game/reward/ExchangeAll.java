package me.legit.api.game.reward;

import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutureCallback;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import io.javalin.http.Context;
import io.javalin.http.Handler;
import io.sentry.Sentry;
import io.sentry.event.UserBuilder;
import me.legit.APICore;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static me.legit.utils.Utilities.generateError;

public class ExchangeAll implements Handler {

    public ExchangeAll() {
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

        APICore.getLogger().info("Successfully verified and decoded ID token for user: " + uid + " (ExchangeAll)");

        Firestore db = APICore.getDatabaseManager().firebase().getFirestore();

        DocumentReference docRef = db.collection("users").document(uid);
        ApiFuture<DocumentSnapshot> future = docRef.get();
        DocumentSnapshot document = future.get();

        if (document.exists()) {
            Map<String, Object> collectibleCurrenciesMap = (Map<String, Object>) document.get("assets.collectibleCurrencies");
            if (collectibleCurrenciesMap != null) {
                APICore.getLogger().info("Successfully fetched collectible currencies data for " + uid + " at: " + document.getReadTime() + " (ExchangeAll)");

                JSONObject collectibleCurrenciesJson = new JSONObject(collectibleCurrenciesMap);
                int coins = APICore.getClaimableRewardService().getCoinsForExchange(collectibleCurrenciesJson);

                List<ApiFuture<String>> transactions = new ArrayList<>();
                ApiFuture<String> coinTransaction = db.runTransaction(transaction -> {
                    Long oldCoins = document.getLong("assets.coins");
                    if (oldCoins != null) {
                        transaction.update(docRef, "assets.coins", oldCoins + coins);
                        return "assets.coins had a value of " + oldCoins + ", updated with a new value of " + oldCoins + " + " + coins + "! (ExchangeAll - UID: " + uid + ")";
                    } else {
                        return "oldCoins == null (this should not happen) -- could not save new coin amount (" + coins + ") for user " + uid + "!";
                    }
                });
                transactions.add(coinTransaction);
                ApiFuture<String> currencyTransaction = db.runTransaction(transaction -> {
                    Long oldCurrency = document.getLong("assets.currency.coins");
                    if (oldCurrency != null) {
                        transaction.update(docRef, "assets.currency.coins", oldCurrency + coins);
                        return "assets.currency.coins had a value of " + oldCurrency + ", updated with a new value of " + oldCurrency + " + " + coins + "! (ExchangeAll - UID: " + uid + ")";
                    } else {
                        transaction.update(docRef, "assets.currency.coins", coins);
                        return "assets.currency.coins was null, setting new value for coins = " + coins;
                    }
                });
                transactions.add(currencyTransaction);

                CompletableFuture<String> resultFuture = new CompletableFuture<>();

                ApiFutures.addCallback(ApiFutures.allAsList(transactions), new ApiFutureCallback<List<String>>() {
                    @Override
                    public void onFailure(Throwable t) {
                        APICore.getLogger().severe("An error was thrown while attempting to exchange all for a user (transactions)!");

                        t.printStackTrace();
                        Sentry.capture(t);

                        ctx.status(500);

                        resultFuture.completeExceptionally(t);
                    }

                    @Override
                    public void onSuccess(List<String> results) {
                        for (String result : results) {
                            APICore.getLogger().info("Did exchange all transaction save new coins or currency value (" + coins + ") for user " + uid + "?: Result - (" + result + ")");
                        }

                        ApiFuture<WriteResult> updateCollectibleCurrencies = docRef.update("assets.collectibleCurrencies", Collections.emptyMap());
                        ApiFutures.addCallback(updateCollectibleCurrencies, new ApiFutureCallback<WriteResult>() {
                            @Override
                            public void onFailure(Throwable t) {
                                APICore.getLogger().severe("An error was thrown while attempting to create new custom equipment for a user (second write operation)!");

                                t.printStackTrace();
                                Sentry.capture(t);

                                ctx.status(500);

                                resultFuture.completeExceptionally(t);
                            }

                            @Override
                            public void onSuccess(WriteResult result) {
                                APICore.getLogger().info("Updated collectible currencies at: " + result.getUpdateTime() + " (ExchangeAll) (UID: " + uid + ")");

                                try {
                                    ApiFuture<DocumentSnapshot> future = docRef.get();
                                    DocumentSnapshot document = future.get();
                                    Map<String, Object> assetsMap = (Map<String, Object>) document.get("assets");
                                    if (assetsMap != null) {
                                        JSONObject assetsJson = new JSONObject(assetsMap);

                                        JSONObject finalResponse = new JSONObject();
                                        finalResponse.put("wsEvents", Collections.emptyList());
                                        finalResponse.put("assets", assetsJson);

                                        resultFuture.complete(finalResponse.toString());
                                    } else {
                                        APICore.getLogger().severe(uid + " - (assetsMap == null) (ExchangeAll)");

                                        Sentry.getContext().addExtra("halted", true);
                                        Sentry.capture("(assetsMap == null) (ExchangeAll)");

                                        ctx.status(500);
                                        resultFuture.complete(new JSONObject().put("status", 500).put("message", "An unknown error occurred while attempting to exchange your assets.").toString());
                                    }
                                } catch (InterruptedException | ExecutionException e) {
                                    APICore.getLogger().severe("An error was thrown while attempting to exchange all for a user (failed to wait for new future)!");

                                    e.printStackTrace();
                                    Sentry.capture(e);

                                    ctx.status(500);

                                    resultFuture.completeExceptionally(e);
                                }
                            }
                        }, MoreExecutors.directExecutor());
                    }
                }, MoreExecutors.directExecutor());

                ctx.result(resultFuture);
            } else {
                APICore.getLogger().severe(uid + " - (collectibleCurrenciesMap == null) (ExchangeAll)");

                Sentry.getContext().addExtra("halted", true);
                Sentry.capture("(collectibleCurrenciesMap == null) (ExchangeAll)");

                ctx.status(500);
            }
        } else {
            APICore.getLogger().severe(uid + " - (document.exists() == false) (ExchangeAll)");

            Sentry.getContext().addExtra("halted", true);
            Sentry.capture("(document.exists() == false) (ExchangeAll)");

            ctx.status(500);
        }
    }
}
