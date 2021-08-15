package me.legit.models.decoration;

public class DecorationId {

    private int definitionId;
    private DecorationType type;
    private Long customId;

    public DecorationId() {
    }

    public DecorationId(int definitionId, DecorationType type) {
        this.definitionId = definitionId;
        this.type = type;
        this.customId = null;
    }

    public DecorationId(int definitionId, DecorationType type, Long customId) {
        this.definitionId = definitionId;
        this.type = type;
        this.customId = customId;
    }

    public int getDefinitionId() {
        return definitionId;
    }

    public DecorationType getType() {
        return type;
    }

    public Long getCustomId() {
        return customId;
    }

    @Override
    public String toString() {
        String builder = String.valueOf(
                this.definitionId) +
                ":" +
                (this.type.ordinal()) +
                ((this.customId == null) ? "" : (":" + this.customId));
        return builder;
    }

    public static DecorationId fromString(String str) {
        String[] array = str.split(":");
        Long num = null;
        if (array.length > 2) {
            num = new Long(Integer.valueOf(array[2]));
        }
        return new DecorationId(Integer.parseInt(array[0]), DecorationType.values()[Integer.parseInt(array[1])], num);
    }
}
