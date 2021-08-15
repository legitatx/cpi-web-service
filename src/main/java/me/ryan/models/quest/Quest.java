package me.legit.models.quest;

import java.util.List;

public class Quest {

    private String questId;
    private int status;
    private List<String> completedObjectives;
    private int timesCompleted;
    private long unlockTime;
    private long completedTime;

    Quest() {
    }

    public Quest(String questId, int status, List<String> completedObjectives, int timesCompleted, long unlockTime, long completedTime) {
        this.questId = questId;
        this.status = status;
        this.completedObjectives = completedObjectives;
        this.timesCompleted = timesCompleted;
        this.unlockTime = unlockTime;
        this.completedTime = completedTime;
    }

    public String getQuestId() {
        return questId;
    }

    public void setQuestId(String questId) {
        this.questId = questId;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public List<String> getCompletedObjectives() {
        return completedObjectives;
    }

    public void setCompletedObjectives(List<String> completedObjectives) {
        this.completedObjectives = completedObjectives;
    }

    public int getTimesCompleted() {
        return timesCompleted;
    }

    public void setTimesCompleted(int timesCompleted) {
        this.timesCompleted = timesCompleted;
    }

    public long getUnlockTime() {
        return unlockTime;
    }

    public void setUnlockTime(long unlockTime) {
        this.unlockTime = unlockTime;
    }

    public long getCompletedTime() {
        return completedTime;
    }

    public void setCompletedTime(long completedTime) {
        this.completedTime = completedTime;
    }
}
