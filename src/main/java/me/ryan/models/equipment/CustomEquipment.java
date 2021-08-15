package me.legit.models.equipment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CustomEquipment {

    private long equipmentId;
    private long dateTimeCreated;
    private int definitionId;
    private List<CustomEquipmentPart> parts;
    private String source;
    private int sourceId;

    public CustomEquipment() {
    }

    public CustomEquipment(long equipmentId, long dateTimeCreated, int definitionId, List<CustomEquipmentPart> parts) {
        this.equipmentId = equipmentId;
        this.dateTimeCreated = dateTimeCreated;
        this.definitionId = definitionId;
        this.parts = parts;
        this.source = "None";
        this.sourceId = 0;
    }

    public CustomEquipment(long equipmentId, long dateTimeCreated, int definitionId, List<CustomEquipmentPart> parts, String source, int sourceId) {
        this.equipmentId = equipmentId;
        this.dateTimeCreated = dateTimeCreated;
        this.definitionId = definitionId;
        this.parts = parts;
        this.source = source;
        this.sourceId = sourceId;
    }

    public long getEquipmentId() {
        return equipmentId;
    }

    public void setEquipmentId(long equipmentId) {
        this.equipmentId = equipmentId;
    }

    public long getDateTimeCreated() {
        return dateTimeCreated;
    }

    public void setDateTimeCreated(long dateTimeCreated) {
        this.dateTimeCreated = dateTimeCreated;
    }

    public int getDefinitionId() {
        return definitionId;
    }

    public void setDefinitionId(int definitionId) {
        this.definitionId = definitionId;
    }

    public List<CustomEquipmentPart> getParts() {
        return parts;
    }

    public void setParts(List<CustomEquipmentPart> parts) {
        this.parts = parts;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public int getSourceId() {
        return sourceId;
    }

    public void setSourceId(int sourceId) {
        this.sourceId = sourceId;
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new HashMap<>();

        map.put("equipmentId", getEquipmentId());
        map.put("dateTimeCreated", getDateTimeCreated());
        map.put("definitionId", getDefinitionId());

        List<Map<String, Object>> parts = new ArrayList<>();
        for (CustomEquipmentPart part : getParts()) {
            Map<String, Object> partMap = new HashMap<>();
            partMap.put("slotIndex", part.getSlotIndex());

            List<Map<String, Object>> customizations = new ArrayList<>();
            for (CustomEquipmentCustomization customization : part.getCustomizations()) {
                Map<String, Object> customizationMap = new HashMap<>();
                customizationMap.put("type", customization.getType().ordinal());
                customizationMap.put("definitionId", customization.getDefinitionId());
                customizationMap.put("index", customization.getIndex());
                customizationMap.put("scale", customization.getScale());
                customizationMap.put("rotation", customization.getRotation());
                customizationMap.put("repeat", customization.isRepeat());
                customizationMap.put("voffset", customization.getVoffset());
                customizationMap.put("uoffset", customization.getUoffset());
                customizations.add(customizationMap);
            }

            partMap.put("customizations", customizations);

            parts.add(partMap);
        }

        map.put("parts", parts);
        map.put("source", getSource());
        map.put("sourceId", getSourceId());

        return map;
    }
}
