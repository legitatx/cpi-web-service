package me.legit.models.equipment;

public class CustomEquipmentCustomization {

    public EquipmentCustomizationType type;
    private int definitionId;
    private int index;
    private float scale;
    private float uoffset;
    private float voffset;
    private float rotation;
    private boolean repeat;

    public CustomEquipmentCustomization() {
    }

    public CustomEquipmentCustomization(EquipmentCustomizationType type, int definitionId, int index, float scale, float uoffset, float voffset, float rotation, boolean repeat) {
        this.type = type;
        this.definitionId = definitionId;
        this.index = index;
        this.scale = scale;
        this.uoffset = uoffset;
        this.voffset = voffset;
        this.rotation = rotation;
        this.repeat = repeat;
    }

    public EquipmentCustomizationType getType() {
        return type;
    }

    public void setType(EquipmentCustomizationType type) {
        this.type = type;
    }

    public int getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(int definitionId) {
        this.definitionId = definitionId;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public float getScale() {
        return scale;
    }

    public void setScale(float scale) {
        this.scale = scale;
    }

    public float getUoffset() {
        return uoffset;
    }

    public void setUoffset(float uoffset) {
        this.uoffset = uoffset;
    }

    public float getVoffset() {
        return voffset;
    }

    public void setVoffset(float voffset) {
        this.voffset = voffset;
    }

    public float getRotation() {
        return rotation;
    }

    public void setRotation(float rotation) {
        this.rotation = rotation;
    }

    public boolean isRepeat() {
        return repeat;
    }

    public void setRepeat(boolean repeat) {
        this.repeat = repeat;
    }
}
