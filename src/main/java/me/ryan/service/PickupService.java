package me.legit.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.legit.APICore;
import me.legit.models.reward.Reward;

import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PickupService {

    private Map<String, Map<String, JsonObject>> pickupablesMap;
    private Map<String, Map<String, JsonObject>> pickupablesGroupMap;

    public PickupService() {
        ClassLoader loader = getClass().getClassLoader();
        APICore.getLogger().info("Parsing pickupables data...");

        this.pickupablesMap = new HashMap<>();

        JsonObject pickupablesJson = new JsonParser().parse(new InputStreamReader(loader.getResourceAsStream("pickupables.json"))).getAsJsonObject();

        for (Map.Entry<String, JsonElement> entry : pickupablesJson.entrySet()) {
            String roomName = entry.getKey();
            JsonObject roomPickupablesJson = entry.getValue().getAsJsonObject();

            Map<String, JsonObject> roomPickupables = new HashMap<>();
            for (Map.Entry<String, JsonElement> roomEntry : roomPickupablesJson.entrySet()) {
                roomPickupables.put(roomEntry.getKey(), roomEntry.getValue().getAsJsonObject());
            }

            pickupablesMap.put(roomName, roomPickupables);
        }

        APICore.getLogger().info("Parsing pickupable groups data...");

        this.pickupablesGroupMap = new HashMap<>();

        JsonObject pickupableGroupsJson = new JsonParser().parse(new InputStreamReader(loader.getResourceAsStream("pickupablegroups.json"))).getAsJsonObject();

        for (Map.Entry<String, JsonElement> entry : pickupableGroupsJson.entrySet()) {
            String roomName = entry.getKey();
            JsonArray roomPickupableGroupsJson = entry.getValue().getAsJsonArray();

            Map<String, JsonObject> roomPickupables = new HashMap<>();
            for (JsonElement element : roomPickupableGroupsJson) {
                JsonObject roomPickupableGroupObj = element.getAsJsonObject();
                JsonArray roomPickupableGroupArray = roomPickupableGroupObj.getAsJsonArray("group");

                for (JsonElement groupElement : roomPickupableGroupArray) {
                    JsonObject groupElementObj = groupElement.getAsJsonObject();
                    String path = groupElementObj.get("path").getAsString();
                    roomPickupables.put(path, groupElementObj);
                }
            }

            pickupablesGroupMap.put(roomName, roomPickupables);
        }
    }

    public Reward getInRoomReward(String room, List<String> newRewards) {
        for (String name : newRewards) {
            JsonObject pickupable = pickupablesMap.get(room).get(name);

            if (pickupable == null) {
                pickupable = pickupablesGroupMap.get(room).get(name);
            }

            if (pickupable != null) {
                JsonObject rewardObj = pickupable.getAsJsonObject("reward");
                return APICore.getRewardManager().getReward(rewardObj.toString());
            }
        }
        return null;
    }
}
