package me.legit.punishment;

import java.util.ArrayList;
import java.util.List;

public class PunishmentHistory {

    private int totalKicks;
    private int totalBans;
    private boolean muted;
    private List<PunishmentData> punishments;

    public PunishmentHistory() {
        this.totalKicks = 0;
        this.totalBans = 0;
        this.muted = false;
        this.punishments = new ArrayList<>();
    }

    public PunishmentHistory(int totalKicks, int totalBans, boolean muted, List<PunishmentData> punishments) {
        this.totalKicks = totalKicks;
        this.totalBans = totalBans;
        this.muted = muted;
        this.punishments = punishments;
    }

    public int getTotalKicks() {
        return totalKicks;
    }

    public void setTotalKicks(int totalKicks) {
        this.totalKicks = totalKicks;
    }

    public int getTotalBans() {
        return totalBans;
    }

    public void setTotalBans(int totalBans) {
        this.totalBans = totalBans;
    }

    public boolean isMuted() {
        return muted;
    }

    public void setMuted(boolean muted) {
        this.muted = muted;
    }

    public List<PunishmentData> getPunishments() {
        return punishments;
    }

    public void setPunishments(List<PunishmentData> punishments) {
        this.punishments = punishments;
    }

    @Override
    public String toString() {
        return "PunishmentHistory{" +
                "totalKicks=" + totalKicks +
                ", totalBans=" + totalBans +
                ", muted=" + muted +
                ", punishments=" + punishments +
                '}';
    }
}