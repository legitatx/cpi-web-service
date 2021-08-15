package me.legit.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.legit.APICore;
import me.legit.models.reward.Reward;
import org.json.JSONObject;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

public class ClaimableRewardService {

    private Map<Integer, JsonObject> claimableRewards;
    private Map<String, Double> collectibleExchangeRates;

    public ClaimableRewardService() {
        ClassLoader loader = getClass().getClassLoader();

        APICore.getLogger().info("Parsing claimable reward data...");

        this.claimableRewards = new HashMap<>();

        JsonArray claimableRewardJson = new JsonParser().parse(new InputStreamReader(loader.getResourceAsStream("ClaimableReward.json"))).getAsJsonArray();
        for (JsonElement element : claimableRewardJson) {
            JsonObject rewardElement = element.getAsJsonObject();

            int claimableRewardId = rewardElement.get("Id").getAsInt();
            JsonObject reward = rewardElement.get("Reward").getAsJsonObject();

            claimableRewards.put(claimableRewardId, reward);
        }

        APICore.getLogger().info("Parsing collectible definition data...");

        this.collectibleExchangeRates = new HashMap<>();

        JsonArray collectibleDefinitionJson = new JsonParser().parse(new InputStreamReader(loader.getResourceAsStream("CollectibleDefinition.json"))).getAsJsonArray();
        for (JsonElement element : collectibleDefinitionJson) {
            JsonObject collectibleElement = element.getAsJsonObject();

            String collectibleType = collectibleElement.get("CollectibleType").getAsString();
            double exchangeRate = collectibleElement.get("ExchangeRate").getAsDouble();

            collectibleExchangeRates.put(collectibleType, exchangeRate);
        }
    }

    public Reward getReward(int rewardId) {
        JsonObject rewardJson = claimableRewards.get(rewardId);
        if (rewardJson != null) {
            Reward reward = APICore.getRewardManager().getReward(rewardJson.toString());
//            if (rewardId == 34) {
//                reward.setCoins(500);
//            }
            return reward;
        } else {
            return null;
        }
    }

    public int getCoinsForExchange(JSONObject collectibleCurrencies) {
        int num = 0;

        for (String key : collectibleCurrencies.keySet()) {
            double exchangeRate = collectibleExchangeRates.get(key);
            float amount = (float) collectibleCurrencies.getInt(key);

            num += (int) Math.ceil(exchangeRate * amount);
        }

        return num;
    }
}
