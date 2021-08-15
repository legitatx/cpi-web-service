package me.legit.service;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.firebase.database.utilities.Pair;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.legit.APICore;
import me.legit.models.reward.Reward;
import me.legit.utils.Utilities;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DailySpinService {

    private JsonParser parser;
    private JsonObject dailySpinDefinition;

    public DailySpinService() {
        ClassLoader loader = getClass().getClassLoader();

        APICore.getLogger().info("Parsing daily spin data...");

        this.parser = new JsonParser();

        JsonArray dailySpinDataJson = parser.parse(new InputStreamReader(loader.getResourceAsStream("DailySpin.json"))).getAsJsonArray();
        dailySpinDefinition = dailySpinDataJson.get(0).getAsJsonObject();
    }

    public int getSpinResult(DocumentSnapshot snapshot, JSONObject[] dailySpinUserData, Map<String, Long> mascotXP, Reward[] spinReward, Reward[] chestReward) throws Exception {
        int currentChestId = dailySpinUserData[0].getInt("currentChestId");
        int numChestsReceivedOfCurrentChestId = dailySpinUserData[0].getInt("numChestsReceivedOfCurrentChestId");
        int numPunchesOnCurrentChest = dailySpinUserData[0].getInt("numPunchesOnCurrentChest");

        boolean flag = currentChestId == 0 && numPunchesOnCurrentChest == 0 && numChestsReceivedOfCurrentChestId == 0;
        JsonObject chestDefinitionForId = getChestDefinitionForId(currentChestId);
        JsonObject chestDefinition = (chestDefinitionForId == null) ? dailySpinDefinition.getAsJsonArray("ChestDefinitions").get(0).getAsJsonObject() : chestDefinitionForId;

        numPunchesOnCurrentChest++;
        dailySpinUserData[0].put("numPunchesOnCurrentChest", numPunchesOnCurrentChest);

        int num = -1;

        if (numPunchesOnCurrentChest >= chestDefinition.get("NumPunchesPerChest").getAsInt()) {
            addChestReward(chestReward, dailySpinUserData, chestDefinition);
        }
        if (flag && !APICore.getRewardManager().getReward(dailySpinDefinition.getAsJsonObject("FirstTimeSpinReward").getAsJsonObject("Reward").toString()).isEmpty()) {
            num = dailySpinDefinition.getAsJsonObject("FirstTimeSpinReward").get("SpinOutcomeId").getAsInt();
            spinReward[0].addReward(APICore.getRewardManager().getReward(dailySpinDefinition.getAsJsonObject("FirstTimeSpinReward").getAsJsonObject("Reward").toString()));
        }
        else
        {
            num = addWeightedRandomSpinReward(spinReward, dailySpinUserData[0], chestDefinition);
        }
        if (num == dailySpinDefinition.get("ChestSpinOutcomeId").getAsInt())
        {
            dailySpinUserData[0].put("numSpinsSinceReceivedChest", 0);
            dailySpinUserData[0].put("numSpinsSinceReceivedExtraSpin", dailySpinUserData[0].getInt("numSpinsSinceReceivedExtraSpin") + 1);
            addChestReward(spinReward, dailySpinUserData, chestDefinition);
        }
        else if (num == dailySpinDefinition.get("RespinSpinOutcomeId").getAsInt())
        {
            dailySpinUserData[0].put("numSpinsSinceReceivedExtraSpin", 0);
            if (isChestValidSpinReward(dailySpinUserData[0], chestDefinition))
            {
                dailySpinUserData[0].put("numSpinsSinceReceivedChest", dailySpinUserData[0].getInt("numSpinsSinceReceivedChest") + 1);
            }
        }
        else
        {
            dailySpinUserData[0].put("numSpinsSinceReceivedExtraSpin", dailySpinUserData[0].getInt("numSpinsSinceReceivedExtraSpin") + 1);
            if (isChestValidSpinReward(dailySpinUserData[0], chestDefinition))
            {
                dailySpinUserData[0].put("numSpinsSinceReceivedChest", dailySpinUserData[0].getInt("numSpinsSinceReceivedChest") + 1);
            }
        }
        if (num == dailySpinDefinition.get("ChestSpinOutcomeId").getAsInt() || !chestReward[0].isEmpty())
        {
            dailySpinUserData[0].put("numChestsReceivedOfCurrentChestId", dailySpinUserData[0].getInt("numChestsReceivedOfCurrentChestId") + 1);
            dailySpinUserData[0].put("numPunchesOnCurrentChest", 0);
            if (dailySpinUserData[0].getInt("numChestsReceivedOfCurrentChestId") >= chestDefinition.get("NumChestsToNextLevel").getAsInt())
            {
                JsonObject chestDefinitionForId2 = getChestDefinitionForId(chestDefinition.get("ChestId").getAsInt() + 1);
                if (chestDefinitionForId2 != null)
                {
                    dailySpinUserData[0].put("currentChestId", chestDefinitionForId2.get("ChestId").getAsInt());
                    dailySpinUserData[0].put("numChestsReceivedOfCurrentChestId", 0);
                }
            }
        }
        if (spinReward[0].getMascotXP() != null && !spinReward[0].getMascotXP().isEmpty())
        {
            boolean flag2 = false;
            ProgressionService progressionService = APICore.getProgressionService();
            for (Map.Entry<String, Integer> keyValuePair : spinReward[0].getMascotXP().entrySet())
            {
                if (progressionService.IsMascotMaxLevel(keyValuePair.getKey(), mascotXP))
                {
                    flag2 = true;
                    break;
                }
            }
            if (flag2)
            {
                spinReward[0].getMascotXP().clear();
                spinReward[0].addReward(APICore.getRewardManager().getReward(dailySpinDefinition.getAsJsonObject("DefaultReward").toString()));
            }
        }
        if (num != dailySpinDefinition.get("RespinSpinOutcomeId").getAsInt())
        {
            dailySpinUserData[0].put("timeOfLastSpinInMilliseconds", System.currentTimeMillis());
        }

        ApiFuture<WriteResult> updateDailySpin = snapshot.getReference().update("dailySpinData", dailySpinUserData[0].toMap());
        APICore.getLogger().info("Updated daily spin data for user " + snapshot.getId() + " at: " + updateDailySpin.get().getUpdateTime());

        return num;
    }

    private void addChestReward(Reward[] chestReward, JSONObject[] dailySpinUserData, JsonObject chestDefinition) {
        if (dailySpinUserData[0].getInt("numChestsReceivedOfCurrentChestId") == 0) {
            Reward repeatableReward = APICore.getRewardManager().getReward(getRepeatableChestReward(dailySpinUserData, chestDefinition).get("Reward").getAsJsonObject().toString());
            Reward firstTimeReward = APICore.getRewardManager().getReward(chestDefinition.get("FirstTimeClaimedReward").getAsJsonObject().toString());

            chestReward[0].addReward(repeatableReward);
            chestReward[0].addReward(firstTimeReward);
        } else {
            Reward repeatableReward = APICore.getRewardManager().getReward(getRepeatableChestReward(dailySpinUserData, chestDefinition).get("Reward").getAsJsonObject().toString());
            Reward nonRepeatableReward = APICore.getRewardManager().getReward(getNonRepeatableChestReward(dailySpinUserData, chestDefinition).get("Reward").getAsJsonObject().toString());

            chestReward[0].addReward(repeatableReward);
            chestReward[0].addReward(nonRepeatableReward);
        }
    }

    private int addWeightedRandomSpinReward(Reward[] spinReward, JSONObject dailySpinData, JsonObject chestDefinition) {
        int num = 0;

        List<Pair<Integer, Integer>> list = new ArrayList<>();
        Map<Integer, Reward> dictionary = new HashMap<>();

        int num2 = dailySpinDefinition.get("InitialRespinWeight").getAsInt() + dailySpinDefinition.get("RespinWeightIncreasePerSpin").getAsInt() * dailySpinData.getInt("numSpinsSinceReceivedExtraSpin");
        dictionary.put(dailySpinDefinition.get("RespinSpinOutcomeId").getAsInt(), APICore.getRewardManager().getReward(dailySpinDefinition.getAsJsonObject("RespinReward").toString()));
        list.add(new Pair<>(dailySpinDefinition.get("RespinSpinOutcomeId").getAsInt(), num2));
        num += num2;
        if (isChestValidSpinReward(dailySpinData, chestDefinition)) {
            int num3 = dailySpinDefinition.get("InitialChestWeight").getAsInt() + dailySpinDefinition.get("ChestWeightIncreasePerSpin").getAsInt() * dailySpinData.getInt("numSpinsSinceReceivedChest");
            dictionary.put(dailySpinDefinition.get("ChestSpinOutcomeId").getAsInt(), new Reward.Builder().build());
            list.add(new Pair<>(dailySpinDefinition.get("ChestSpinOutcomeId").getAsInt(), num3));
            num += num3;
        }
        for (JsonElement spinReward2Element : dailySpinDefinition.getAsJsonArray("SpinRewards")) {
            JsonObject spinReward2 = spinReward2Element.getAsJsonObject();

            dictionary.put(spinReward2.get("SpinOutcomeId").getAsInt(), APICore.getRewardManager().getReward(spinReward2.getAsJsonObject("Reward").toString()));
            list.add(new Pair<>(spinReward2.get("SpinOutcomeId").getAsInt(), spinReward2.get("Weight").getAsInt()));
            num += spinReward2.get("Weight").getAsInt();
        }
        int num4 = Utilities.getRandomNumberInRange(0, num);
        int num5 = 0;
        for (Pair<Integer, Integer> keyValuePair : list) {
            num5 += keyValuePair.getSecond();
            if (num5 > num4) {
                spinReward[0].addReward(dictionary.get(keyValuePair.getFirst()));
                return keyValuePair.getFirst();
            }
        }
        return -1;
    }

    private JsonObject getRepeatableChestReward(JSONObject[] dailySpinUserData, JsonObject chestDefinition) {
        JsonArray list = chestDefinition.getAsJsonArray("RepeatableChestRewards");
        JsonArray list2 = filterRewardsAlreadyReceived(list, dailySpinUserData[0].getJSONArray("earnedRepeatableRewardIds"));
        if (list2.size() > 0) {
            dailySpinUserData[0].put("earnedRepeatableRewardIds", new JSONArray());
        } else {
            list = list2;
        }
        dailySpinUserData[0].getJSONArray("earnedRepeatableRewardIds").put(list.get(0).getAsJsonObject().get("RewardId").getAsInt());
        return list.get(0).getAsJsonObject();
    }

    private JsonObject getNonRepeatableChestReward(JSONObject[] dailySpinUserData, JsonObject chestDefinition) {
        JsonArray list = chestDefinition.getAsJsonArray("NonRepeatableChestRewards");
        JsonArray list2 = filterRewardsAlreadyReceived(list, dailySpinUserData[0].getJSONArray("earnedNonRepeatableRewardIds"));
        JsonObject result;
        if (list2.size() > 0) {
            result = getRepeatableChestReward(dailySpinUserData, chestDefinition);
        } else {
            list = list2;
            dailySpinUserData[0].getJSONArray("earnedNonRepeatableRewardIds").put(list.get(0).getAsJsonObject().get("RewardId").getAsInt());
            result = list.get(0).getAsJsonObject();
        }
        return result;
    }

    private JsonArray filterRewardsAlreadyReceived(JsonArray chestRewards, JSONArray receivedRewardIds) {
        JsonArray list = new JsonArray();
        JsonArray receivedRewardIdsGson = parser.parse(receivedRewardIds.toString()).getAsJsonArray();

        for (JsonElement element : chestRewards) {
            JsonObject chestRewardElement = element.getAsJsonObject();
            if (!receivedRewardIdsGson.contains(chestRewardElement.get("RewardId"))) {
                list.add(chestRewardElement);
            }
        }

        return list;
    }

    private JsonObject getChestDefinitionForId(int chestId) {
        JsonArray chestDefinitions = dailySpinDefinition.getAsJsonArray("ChestDefinitions");

        for (JsonElement element : chestDefinitions) {
            JsonObject chestElement = element.getAsJsonObject();
            int chestIdFromElement = chestElement.get("ChestId").getAsInt();
            if (chestIdFromElement == chestId) {
                return chestElement;
            }
        }

        return null;
    }

    private boolean isChestValidSpinReward(JSONObject dailySpinData, JsonObject chestDefinition) {
        return !chestDefinition.get("IsChestSpinNotAllowed").getAsBoolean() && dailySpinData.getInt("numPunchesOnCurrentChest") < chestDefinition.get("NumPunchesPerChest").getAsInt();
    }
}
