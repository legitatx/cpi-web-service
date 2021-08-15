package me.legit.models.quest;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.common.collect.Lists;
import com.google.gson.*;
import me.legit.APICore;
import me.legit.models.reward.Reward;
import me.legit.service.ProgressionService;
import org.json.JSONObject;

import java.io.InputStreamReader;
import java.time.*;
import java.util.*;

@SuppressWarnings({"unchecked", "Duplicates"})
public class QuestManager {

    private Gson gson;

    private JsonObject questsData;

    public QuestManager() {
        GsonBuilder builder = new GsonBuilder().serializeNulls();
        this.gson = builder.create();

        ClassLoader loader = getClass().getClassLoader();
        APICore.getLogger().info("Parsing quests data...");
        this.questsData = new JsonParser().parse(new InputStreamReader(loader.getResourceAsStream("quests.json"))).getAsJsonObject();
    }

    public JSONObject completeNewObjectives(DocumentReference docRef, DocumentSnapshot document, JsonArray data) throws Exception {
        List<Object> objectList = (List<Object>) document.get("quests");
        String questsAsJson = gson.toJson(objectList);

        List<String> completedObjectives = new ArrayList<>();
        for (JsonElement objectives : data) {
            String objective = objectives.getAsString();
            completedObjectives.add(objective);
        }

        JSONObject finalResponse = new JSONObject();

        List<Quest> jsonAsQuestList = Lists.newArrayList(gson.fromJson(questsAsJson, Quest[].class));
        ListIterator<Quest> iterator = jsonAsQuestList.listIterator();
        while (iterator.hasNext()) {
            Quest quest = iterator.next();
            if (quest.getStatus() == QuestStatus.ACTIVE.ordinal()) {
                finalResponse.put("questId", quest.getQuestId());

                quest.setCompletedObjectives(completedObjectives);

                if (quest.getTimesCompleted() == 0) {
                    JsonObject questIdData = getQuestsData().getAsJsonObject(quest.getQuestId());
                    JsonElement rewardElement = questIdData.getAsJsonObject("objectives").getAsJsonObject(completedObjectives.get(completedObjectives.size() - 1)).get("reward");
                    if (!rewardElement.isJsonNull()) {
                        JsonObject rewardObject = rewardElement.getAsJsonObject();
                        if (rewardObject.size() != 0) {
                            Reward reward = APICore.getRewardManager().getReward(rewardObject.toString());
                            APICore.getRewardManager().saveReward(document, reward, finalResponse);
                            finalResponse.put("reward", APICore.getRewardManager().getRewardAsJSON(reward));
                        }
                    }
                }

                iterator.set(quest);
                break;
            }
        }

        String finalQuestData = gson.toJson(jsonAsQuestList);
        finalResponse.put("finalQuestData", finalQuestData);

        List<Object> questsSerialized = Lists.newArrayList(gson.fromJson(finalQuestData, Object[].class));

        ApiFuture<WriteResult> updateQuests = docRef.update("quests", questsSerialized);
        APICore.getLogger().info("Updated quests with new completed objectives for active quest for user " + document.getId() + "  at: " + updateQuests.get().getUpdateTime());

        return finalResponse;
    }

    public JSONObject setQuestStatus(DocumentReference docRef, DocumentSnapshot document, String questId, QuestStatus status) throws Exception {
        List<Object> objectList = (List<Object>) document.get("quests");
        String questsAsJson = gson.toJson(objectList);

        JSONObject finalResponse = new JSONObject();

        boolean foundQuest = false;

        List<Quest> jsonAsQuestList = Lists.newArrayList(gson.fromJson(questsAsJson, Quest[].class));
        ListIterator<Quest> iterator = jsonAsQuestList.listIterator();
        while (iterator.hasNext()) {
            Quest quest = iterator.next();
            if (quest.getQuestId().equals(questId)) {
                APICore.getLogger().info("Found quest ID... " + questId + " = " + quest.getQuestId());

                quest.setStatus(status.ordinal());
                foundQuest = true;

                if (status.equals(QuestStatus.ACTIVE)) {
                    JsonObject questIdData = getQuestsData().getAsJsonObject(questId);
                    JsonObject rewardElement = questIdData.getAsJsonObject("startReward");
                    if (!rewardElement.isJsonNull()) {
                        JsonObject rewardObj = rewardElement.getAsJsonObject();
                        if (rewardObj.size() != 0) {
                            Reward reward = APICore.getRewardManager().getReward(rewardObj.toString());
                            APICore.getRewardManager().saveReward(document, reward, finalResponse);
                            finalResponse.put("reward", APICore.getRewardManager().getRewardAsJSON(reward));
                        }
                    }
                } else if (status.equals(QuestStatus.COMPLETED)) {
                    int timesCompleted = quest.getTimesCompleted();
                    if (timesCompleted == 0) {
                        quest.setCompletedTime(ZonedDateTime.now(ZoneOffset.UTC).toInstant().toEpochMilli());

                        JsonObject questIdData = getQuestsData().getAsJsonObject(questId);
                        JsonElement rewardElement = questIdData.get("completeReward");
                        if (!rewardElement.isJsonNull()) {
                            JsonObject rewardObj = rewardElement.getAsJsonObject();
                            if (rewardObj.size() != 0) {
                                Reward reward = APICore.getRewardManager().getReward(rewardObj.toString());
                                APICore.getRewardManager().saveReward(document, reward, finalResponse);
                                finalResponse.put("reward", APICore.getRewardManager().getRewardAsJSON(reward));
                            }
                        }
                    }
                    quest.setTimesCompleted(quest.getTimesCompleted() + 1);
                }

                iterator.set(quest);
            } else if (!quest.getQuestId().equals(questId)) {
                APICore.getLogger().info("Didn't find quest ID... " + questId + " = " + quest.getQuestId());
                if (status.equals(QuestStatus.ACTIVE)) {
                    if (quest.getStatus() == QuestStatus.ACTIVE.ordinal()) {
                        APICore.getLogger().info("Suspended quest ID... " + quest.getQuestId());
                        quest.setStatus(QuestStatus.SUSPENDED.ordinal());
                        iterator.set(quest);
                    } else if (quest.getStatus() == QuestStatus.COMPLETED.ordinal()) {
                        APICore.getLogger().info("Completed quest ID... " + quest.getQuestId());
                        quest.getCompletedObjectives().clear();
                        iterator.set(quest);
                    }
                }
            }
        }

        if (!foundQuest) {
            APICore.getLogger().info("Didn't find any quest with ID " + questId + " after loop, adding...");

            Quest quest = new Quest();
            quest.setQuestId(questId);
            quest.setStatus(status.ordinal());
            quest.setCompletedObjectives(Collections.emptyList());
            quest.setTimesCompleted(0);
            jsonAsQuestList.add(quest);
        }

        String finalQuestData = gson.toJson(jsonAsQuestList);

        finalResponse.put("finalQuestData", finalQuestData);

        List<Object> questsSerialized = Lists.newArrayList(gson.fromJson(finalQuestData, Object[].class));

        ApiFuture<WriteResult> updateQuests = docRef.update("quests", questsSerialized);
        APICore.getLogger().info("Updated quests with new status " + status.name() + " for quest " + questId + " for user " + document.getId() + "  at: " + updateQuests.get().getUpdateTime());

        return finalResponse;
    }

    public List<Quest> getAvailableQuests(List<Quest> quests, Map<String, Long> mascotXP) {
        Set<String> hashSet = new HashSet<>();
        for (Quest quest : quests) {
            hashSet.add(quest.getQuestId());
        }
        List<Quest> list = new ArrayList<>();
        JsonObject knownQuests = getQuestsData();
        for (Map.Entry<String, JsonElement> element : knownQuests.entrySet()) {
            if (!hashSet.contains(element.getKey())) {
                LocalDateTime dateTime = questAvailableDate(element.getValue().getAsJsonObject(), quests, mascotXP);
                if (dateTime != null) {
                    Quest questState2 = new Quest();
                    questState2.setQuestId(element.getKey());
                    questState2.setCompletedObjectives(Collections.emptyList());
                    if (dateTime.isBefore(LocalDateTime.now()) || dateTime.isEqual(LocalDateTime.now())) {
                        questState2.setStatus(QuestStatus.AVAILABLE.ordinal());
                    } else {
                        questState2.setStatus(QuestStatus.LOCKED.ordinal());
                        ZoneId zoneId = ZoneId.systemDefault();
                        long epoch = dateTime.atZone(zoneId).toInstant().toEpochMilli();
                        questState2.setUnlockTime(epoch);
                    }
                    list.add(questState2);
                }
            }
        }
        return list;
    }

    public LocalDateTime questAvailableDate(JsonObject def, List<Quest> userQuests, Map<String, Long> mascotXP) {
        int levelRequirement = def.get("preReqProgressLevel").getAsInt();
        String mascotName = def.get("mascot").getAsString();
        JsonArray preReqQuests = def.get("preReqQuests").getAsJsonArray();
        if (levelRequirement > 0) {
            long xp = 0L;
            xp = mascotXP.getOrDefault(mascotName, xp);
            if (levelRequirement > ProgressionService.GetMascotLevelFromXP(xp)) {
                return null;
            }
        }
        LocalDateTime dateTime = LocalDateTime.now().minusMinutes(1);
        if (preReqQuests.size() > 0) {
            Map<String, LocalDateTime> dictionary = new HashMap<>(userQuests.size());
            for (Quest quest : userQuests) {
                if (quest.getTimesCompleted() > 0) {
                    dictionary.put(quest.getQuestId(), LocalDateTime.ofInstant(Instant.ofEpochMilli(quest.getCompletedTime()), ZoneId.systemDefault()));
                }
            }
            for (JsonElement element : preReqQuests) {
                String name = element.getAsString();
                if (!dictionary.containsKey(name)) {
                    return null;
                }
                long secondsLocked = getQuestsData().get(name).getAsJsonObject().get("timeLockedSeconds").getAsLong();
                if (secondsLocked > 0) {
                    LocalDateTime dateTime2 = dictionary.get(name).plusSeconds(secondsLocked);
                    if (dateTime.isBefore(dateTime2)) {
                        dateTime = dateTime2;
                    }
                }
            }
        }
        return dateTime;
    }

    public JsonObject getQuestsData() {
        return questsData;
    }
}
