package me.legit.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.legit.APICore;
import me.legit.models.reward.Reward;
import me.legit.utils.Utilities;

import java.io.InputStreamReader;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;

public class MinigameService {

    private JsonArray fishingLootTableRewardsJson;

    private Map<String, Integer> lootTableBuckets;
    private Map<String, Map.Entry<Integer, String>> lootTableRewards;

    public MinigameService() {
        ClassLoader loader = getClass().getClassLoader();

        APICore.getLogger().info("Parsing minigame data...");

        this.lootTableBuckets = new HashMap<>();

        APICore.getLogger().info("Parsing fishing loot table buckets data...");

        JsonArray fishingLootTableBucketsJson = new JsonParser().parse(new InputStreamReader(loader.getResourceAsStream("FishingLootTableBuckets.json"))).getAsJsonArray();
        for (JsonElement element : fishingLootTableBucketsJson) {
            JsonObject lootTableBucketElement = element.getAsJsonObject();

            String bucketName = lootTableBucketElement.get("BucketName").getAsString();
            int weight = lootTableBucketElement.get("Weight").getAsInt();

            lootTableBuckets.put(bucketName, weight);
        }

        APICore.getLogger().info("Parsing fishing loot table rewards data...");

        this.lootTableRewards = new HashMap<>();

        this.fishingLootTableRewardsJson = new JsonParser().parse(new InputStreamReader(loader.getResourceAsStream("FishingLootTableRewards.json"))).getAsJsonArray();
        for (JsonElement element : fishingLootTableRewardsJson) {
            JsonObject lootTableRewardElement = element.getAsJsonObject();

            String id = lootTableRewardElement.get("Id").getAsString();
            int weight = lootTableRewardElement.get("Weight").getAsInt();
            String bucketName = lootTableRewardElement.get("Bucket").getAsString();

            lootTableRewards.put(id, new AbstractMap.SimpleEntry<>(weight, bucketName));
        }
    }

    public Map<String, String> getRandomFishingPrizes() {
        Map<String, String> dictionary = new HashMap<>();
        Map<String, Integer> dictionary2 = new HashMap<>();

        for (Map.Entry<Integer, String> entry : lootTableRewards.values()) {
            int rewardWeight = entry.getKey();
            String bucketName = entry.getValue();

            int bucketWeight = lootTableBuckets.get(bucketName);
            if (bucketWeight != 0) {
                if (dictionary2.containsKey(bucketName)) {
                    dictionary2.put(bucketName, dictionary2.get(bucketName) + rewardWeight);
                } else {
                    dictionary2.put(bucketName, rewardWeight);
                }
            }
        }
        for (Map.Entry<String, Integer> keyValuePair : dictionary2.entrySet()) {
            int num = Utilities.getRandomNumberInRange(0, keyValuePair.getValue());
            int num2 = 0;
            for (Map.Entry<String, Map.Entry<Integer, String>> entry : lootTableRewards.entrySet()) {
                int rewardWeight = entry.getValue().getKey();
                String bucketName = entry.getValue().getValue();
                if (rewardWeight != 0) {
                    if (bucketName.equals(keyValuePair.getKey())) {
                        num2 += rewardWeight;
                        if (num2 > num) {
                            String rewardId = entry.getKey();
                            dictionary.put(keyValuePair.getKey(), rewardId);
                        }
                    }
                }
            }
        }
        return dictionary;
    }

    public Reward getFishingReward(String winningRewardName) {
        for (JsonElement element : fishingLootTableRewardsJson) {
            JsonObject object = element.getAsJsonObject();
            String key = object.get("Id").getAsString();
            if (key.equals(winningRewardName)) {
                return APICore.getRewardManager().getReward(object.getAsJsonObject("Reward").toString());
            }
        }
        return new Reward.Builder().build();
    }
}
