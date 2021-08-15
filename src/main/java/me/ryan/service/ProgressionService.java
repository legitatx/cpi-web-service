package me.legit.service;

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

public class ProgressionService {

    private Map<String, List<Integer>> mascotLevelXPData;
    private JsonArray progressionUnlockData;

    public ProgressionService() {
        ClassLoader loader = getClass().getClassLoader();
        APICore.getLogger().info("Parsing progression mascot level XP data...");

        this.mascotLevelXPData = new HashMap<>();

        JsonParser parser = new JsonParser();

        JsonArray mascotLevelXPDataJson = parser.parse(new InputStreamReader(loader.getResourceAsStream("ProgressionMascotLevelXP.json"))).getAsJsonArray();
        for (JsonElement element : mascotLevelXPDataJson) {
            JsonObject mascotElement = element.getAsJsonObject();

            String mascot = mascotElement.get("Mascot").getAsString();
            JsonArray levels = mascotElement.get("Levels").getAsJsonArray();

            List<Integer> levelsList = new ArrayList<>();
            for (JsonElement level : levels) {
                levelsList.add(level.getAsInt());
            }

            mascotLevelXPData.put(mascot, levelsList);
        }

        APICore.getLogger().info("Parsing progression unlock data...");

        this.progressionUnlockData = parser.parse(new InputStreamReader(loader.getResourceAsStream("ProgressionUnlock.json"))).getAsJsonArray();
    }

    public int MascotLevel(String mascotName, Map<String, Long> mascotXPDict, int xpOffset) {
        long mascotXP = this.GetMascotXP(mascotName, mascotXPDict, 0);
        long xp = this.addXp(mascotName, xpOffset, mascotXP);
        return ProgressionService.GetMascotLevelFromXP(xp);
    }

    public long GetMascotXP(String mascotName, Map<String, Long> mascotXP, int xpOffset) {
        long currentMascotXp = 0L;
        if (mascotXP.containsKey(mascotName)) {
            currentMascotXp = mascotXP.get(mascotName);
        }
        return this.addXp(mascotName, xpOffset, currentMascotXp);
    }

    public long addXp(String mascot, int newXp, long currentMascotXp) {
        int mascotLevelFromXP = ProgressionService.GetMascotLevelFromXP(currentMascotXp);
        int mascotXpFloor = this.getMascotXpFloor(mascot, mascotLevelFromXP);
        int mascotXpFloor2 = this.getMascotXpFloor(mascot, mascotLevelFromXP + 1);
        long result;
        if (mascotXpFloor >= mascotXpFloor2) {
            result = currentMascotXp;
        } else {
            int num = mascotXpFloor2 - mascotXpFloor;
            long num2 = (long)newXp * 1000000L / (long)num;
            long num3 = currentMascotXp + num2;
            if (ProgressionService.GetMascotLevelFromXP(num3) > ProgressionService.GetMascotLevelFromXP(currentMascotXp))
            {
                long num4 = 1000000L - currentMascotXp % 1000000L;
                int newXp2 = newXp - (int)(num4 * (long)num / 1000000L);
                num3 = (long)(mascotLevelFromXP + 1) * 1000000L;
                result = this.addXp(mascot, newXp2, num3);
            } else {
                result = num3;
            }
        }
        return result;
    }

    public int getLevel(Map<String, Long> mascotXP) {
        int num = 0;
        for (Map.Entry<String, Long> entry : mascotXP.entrySet()) {
            num += this.MascotLevel(entry.getKey(), mascotXP, 0);
        }
        return num;
    }

    public boolean IsMascotMaxLevel(String mascotName, long mascotXP) {
        boolean result;
        List<Integer> list = this.mascotLevelXPData.get(mascotName);
        if (list != null && !list.isEmpty()) {
            int mascotLevelFromXP = ProgressionService.GetMascotLevelFromXP(mascotXP);
            result = (list.size() - 1 <= mascotLevelFromXP);
        } else {
            result = false;
        }
        return result;
    }

    public boolean IsMascotMaxLevel(String mascotName, Map<String, Long> mascotXP) {
        long mascotXPFinal = this.GetMascotXP(mascotName, mascotXP,0);
        return this.IsMascotMaxLevel(mascotName, mascotXPFinal);
    }

    public JsonObject getProgressionUnlockData(int newLevel) {
        return progressionUnlockData.get(newLevel).getAsJsonObject();
    }

    private int getMascotXpFloor(String mascot, int currentLevel) {
        int result;
        if (!this.mascotLevelXPData.containsKey(mascot)) {
            result = 0;
        } else if (this.mascotLevelXPData.get(mascot).size() <= currentLevel) {
            result = 0;
        } else {
            result = this.mascotLevelXPData.get(mascot).get(currentLevel);
        }
        return result;
    }

    public static int GetMascotLevelFromXP(long xp) {
        return (int) (xp / 1000000L);
    }
}
