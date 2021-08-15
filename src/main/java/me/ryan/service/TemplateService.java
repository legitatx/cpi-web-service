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

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TemplateService {

    private Map<Integer, Integer> templateData;

    public TemplateService() {
        ClassLoader loader = getClass().getClassLoader();
        APICore.getLogger().info("Parsing template data...");

        this.templateData = new HashMap<>();

        JsonArray templateDataJson = new JsonParser().parse(new InputStreamReader(loader.getResourceAsStream("Templates.json"))).getAsJsonArray();
        for (JsonElement element : templateDataJson) {
            JsonObject templateElement = element.getAsJsonObject();

            int templateDefintionId = templateElement.get("Id").getAsInt();
            int cost = templateElement.get("Cost").getAsInt();

            templateData.put(templateDefintionId, cost);
        }
    }

    public List<ApiFuture<String>> subtractEquipmentCost(DocumentSnapshot snapshot, int templateDefinitionId) throws Exception {
        int templateCost = templateData.get(templateDefinitionId);

        Firestore db = snapshot.getReference().getFirestore();

        List<ApiFuture<String>> transactions = new ArrayList<>();

        ApiFuture<String> coinTransaction = db.runTransaction(transaction -> {
            Long oldCoins = snapshot.getLong("assets.coins");
            if (oldCoins != null) {
                if (oldCoins - templateCost < 0) {
                    transaction.update(snapshot.getReference(), "assets.coins", 0);
                    return "assets.coins had a value of " + oldCoins + " but new coins will be negative (" + oldCoins + " - " + templateCost + "), so updated with a new value of 0";
                } else {
                    transaction.update(snapshot.getReference(), "assets.coins", oldCoins - templateCost);
                    return "assets.coins had a value of " + oldCoins + ", updated with a new value of " + oldCoins + " - " + templateCost + "!";
                }
            } else {
                return "oldCoins == null (this should not happen) -- could not save new coin amount (" + templateCost + ") for user " + snapshot.getId() + "!";
            }
        });

        transactions.add(coinTransaction);

        ApiFuture<String> currencyTransaction = db.runTransaction(transaction -> {
            Long oldCurrencyCoins = snapshot.getLong("assets.currency.coins");
            if (oldCurrencyCoins != null) {
                if (oldCurrencyCoins - templateCost < 0) {
                    transaction.update(snapshot.getReference(), "assets.currency.coins", 0);
                    return "assets.currency.coins had a value of " + oldCurrencyCoins + " but new coins will be negative (" + oldCurrencyCoins + " - " + templateCost + "), so updated with a new value of 0";
                } else {
                    transaction.update(snapshot.getReference(), "assets.currency.coins", oldCurrencyCoins - templateCost);
                    return "assets.currency.coins had a value of " + oldCurrencyCoins + ", updated with a new value of " + oldCurrencyCoins + " - " + templateCost + "!";
                }
            } else {
                transaction.update(snapshot.getReference(), "assets.currency.coins", templateCost);
                return "assets.currency.coins was null, setting new value for coins = " + templateCost;
            }
        });

        transactions.add(currencyTransaction);

        return transactions;
    }
}

