package me.legit.punishment;

public class PunishmentData {

    private PunishmentType type;
    private String reason;
    private String punishedBy;
    private long expiresIn;

    public PunishmentData(PunishmentType type, String reason, String punishedBy, long expiresIn) {
        this.type = type;
        this.reason = reason;
        this.punishedBy = punishedBy;
        this.expiresIn = expiresIn;
    }

    public PunishmentType getType() {
        return type;
    }

    public void setType(PunishmentType type) {
        this.type = type;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getPunishedBy() {
        return punishedBy;
    }

    public void setPunishedBy(String punishedBy) {
        this.punishedBy = punishedBy;
    }

    public long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(long expiresIn) {
        this.expiresIn = expiresIn;
    }

    @Override
    public String toString() {
        return "PunishmentData{" +
                "type=" + type +
                ", reason='" + reason + '\'' +
                ", punishedBy='" + punishedBy + '\'' +
                ", expiresIn=" + expiresIn +
                '}';
    }
}
