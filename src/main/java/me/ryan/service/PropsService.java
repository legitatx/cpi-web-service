package me.legit.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.legit.APICore;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class PropsService {

    private JsonArray propDefinitions;

    public PropsService() {
        ClassLoader loader = getClass().getClassLoader();

        APICore.getLogger().info("Parsing props data...");

        this.propDefinitions = new JsonParser().parse(new InputStreamReader(loader.getResourceAsStream("Props.json"))).getAsJsonArray();
    }

    public boolean canBuyConsumable(DocumentSnapshot snapshot, String consumableId, int count) {
        for (JsonElement propDefinition : propDefinitions) {
            JsonObject prop = propDefinition.getAsJsonObject();
            if (consumableId.equals(prop.get("NameOnServer").getAsString())) {
                Long oldCoins = snapshot.getLong("assets.coins");
                if (oldCoins == null) {
                    oldCoins = 0L;
                }
                int cost = prop.get("Cost").getAsInt();
                int newCost = cost * count;
                return oldCoins - newCost >= 0;
            }
        }
        return false;
    }

    public List<ApiFuture<String>> subtractConsumableCost(DocumentSnapshot snapshot, String consumableId, int count) {
        List<ApiFuture<String>> transactions = new ArrayList<>();
        for (JsonElement propDefinition : propDefinitions) {
            JsonObject prop = propDefinition.getAsJsonObject();
            if (consumableId.equals(prop.get("NameOnServer").getAsString())) {
                Firestore db = snapshot.getReference().getFirestore();
                WriteBatch batch = db.batch();

                int cost = prop.get("Cost").getAsInt();
                int newCost = cost * count;

                ApiFuture<String> coinTransaction = db.runTransaction(transaction -> {
                    Long oldCoins = snapshot.getLong("assets.coins");
                    if (oldCoins != null) {
                        if (oldCoins - newCost < 0) {
                            transaction.update(snapshot.getReference(), "assets.coins", 0);
                            return "assets.coins had a value of " + oldCoins + " but new coins will be negative (" + oldCoins + " - " + newCost + "), so updated with a new value of 0";
                        } else {
                            transaction.update(snapshot.getReference(), "assets.coins", oldCoins - newCost);
                            return "assets.coins had a value of " + oldCoins + ", updated with a new value of " + oldCoins + " - " + newCost + "!";
                        }
                    } else {
                        return "oldCoins == null (this should not happen) -- could not save new coin amount (" + newCost + ") for user " + snapshot.getId() + "!";
                    }
                });
                transactions.add(coinTransaction);

                ApiFuture<String> currencyTransaction = db.runTransaction(transaction -> {
                    Long oldCurrencyCoins = snapshot.getLong("assets.currency.coins");
                    if (oldCurrencyCoins != null) {
                        if (oldCurrencyCoins - newCost < 0) {
                            transaction.update(snapshot.getReference(), "assets.currency.coins", 0);
                            return "assets.currency.coins had a value of " + oldCurrencyCoins + " but new coins will be negative (" + oldCurrencyCoins + " - " + newCost + "), so updated with a new value of 0";
                        } else {
                            transaction.update(snapshot.getReference(), "assets.currency.coins", oldCurrencyCoins - newCost);
                            return "assets.currency.coins had a value of " + oldCurrencyCoins + ", updated with a new value of " + oldCurrencyCoins + " - " + newCost + "!";
                        }
                    } else {
                        batch.update(snapshot.getReference(), "assets.currency.coins", newCost);
                        return "assets.currency.coins was null, setting new value for coins = " + newCost;
                    }
                });
                transactions.add(currencyTransaction);
            }
        }
        return transactions;
    }
}
