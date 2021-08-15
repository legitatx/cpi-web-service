package me.legit.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.cloud.firestore.WriteResult;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.legit.APICore;
import me.legit.models.reward.Reward;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DisneyStoreService {

    private JsonArray disneyStoreFranchises;

    public DisneyStoreService() {
        ClassLoader loader = getClass().getClassLoader();

        APICore.getLogger().info("Parsing Disney Store franchise data...");

        this.disneyStoreFranchises = new JsonParser().parse(new InputStreamReader(loader.getResourceAsStream("DisneyStoreFranchises.json"))).getAsJsonArray();
    }

    public Reward getDisneyStoreItemReward(int itemId, int count) {
        for (JsonElement franchise : disneyStoreFranchises) {
            JsonObject franchiseObject = franchise.getAsJsonObject();
            JsonArray items = franchiseObject.getAsJsonArray("Items");
            if (items != null) {
                for (JsonElement item : items) {
                    JsonObject itemObject = item.getAsJsonObject();
                    if (itemObject.get("Id").getAsInt() == itemId) {
                        Reward reward = APICore.getRewardManager().getReward(itemObject.get("Reward").getAsJsonObject().toString());
                        if (count > 1) {
                            if (reward.getConsumables() != null && !reward.getConsumables().isEmpty()) {
                                for (Map.Entry<String, Integer> keyValuePair : reward.getConsumables().entrySet()) {
                                    reward.getConsumables().replace(keyValuePair.getKey(), keyValuePair.getValue(), keyValuePair.getValue() * count);
                                }
                            }
                            if (reward.getDecorationInstances() != null && !reward.getDecorationInstances().isEmpty()) {
                                for (Map.Entry<Integer, Integer> keyValuePair : reward.getDecorationInstances().entrySet()) {
                                    reward.getDecorationInstances().replace(keyValuePair.getKey(), keyValuePair.getValue(), keyValuePair.getValue() * count);
                                }
                            }
                            if (reward.getStructureInstances() != null && !reward.getStructureInstances().isEmpty()) {
                                for (Map.Entry<Integer, Integer> keyValuePair : reward.getStructureInstances().entrySet()) {
                                    reward.getStructureInstances().replace(keyValuePair.getKey(), keyValuePair.getValue(), keyValuePair.getValue() * count);
                                }
                            }
                        }
                        return reward;
                    }
                }
            }
        }
        return new Reward.Builder().build();
    }

    public List<ApiFuture<String>> subtractDisneyStoreItemCost(DocumentSnapshot snapshot, int itemId, int count) {
        List<ApiFuture<String>> transactions = new ArrayList<>();
        for (JsonElement franchise : disneyStoreFranchises) {
            JsonObject franchiseObject = franchise.getAsJsonObject();
            JsonArray items = franchiseObject.getAsJsonArray("Items");
            if (items != null) {
                for (JsonElement item : items) {
                    JsonObject itemObject = item.getAsJsonObject();
                    if (itemObject.get("Id").getAsInt() == itemId) {
                        Firestore db = snapshot.getReference().getFirestore();
                        WriteBatch batch = db.batch();

                        int cost = itemObject.get("Cost").getAsInt();
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
            }
        }
        return transactions;
    }
}
