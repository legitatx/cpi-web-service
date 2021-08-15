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
import me.legit.models.decoration.DecorationId;
import me.legit.models.decoration.DecorationType;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DecorationService {

    private Map<Integer, JsonObject> decorations;
    private Map<Integer, JsonObject> structures;

    public DecorationService() {
        ClassLoader loader = getClass().getClassLoader();

        this.decorations = new HashMap<>();
        this.structures = new HashMap<>();

        APICore.getLogger().info("Parsing decorations data...");

        JsonArray decorationsJson = new JsonParser().parse(new InputStreamReader(loader.getResourceAsStream("Decorations.json"))).getAsJsonArray();
        for (JsonElement decorationElement : decorationsJson) {
            JsonObject decorationObj = decorationElement.getAsJsonObject();
            decorations.put(decorationObj.get("Id").getAsInt(), decorationObj);
        }

        APICore.getLogger().info("Parsing structures data...");

        JsonArray structuresJson = new JsonParser().parse(new InputStreamReader(loader.getResourceAsStream("Structures.json"))).getAsJsonArray();
        for (JsonElement structureElement : structuresJson) {
            JsonObject structureObj = structureElement.getAsJsonObject();
            structures.put(structureObj.get("Id").getAsInt(), structureObj);
        }
    }

    public List<ApiFuture<String>> subtractDecorationCost(DocumentSnapshot snapshot, DecorationId decorationId, int count) {
        int cost = 0;
        DecorationType type = decorationId.getType();
        if (!type.equals(DecorationType.DECORATION)) {
            if (type.equals(DecorationType.STRUCTURE)) {
                if (structures.containsKey(decorationId.getDefinitionId())) {
                    cost = structures.get(decorationId.getDefinitionId()).get("Cost").getAsInt();
                }
            }
        } else {
            if (decorations.containsKey(decorationId.getDefinitionId())) {
                cost = decorations.get(decorationId.getDefinitionId()).get("Cost").getAsInt();
            }
        }

        List<ApiFuture<String>> transactions = new ArrayList<>();
        Firestore db = snapshot.getReference().getFirestore();
        WriteBatch batch = db.batch();

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

        return transactions;
    }
}
