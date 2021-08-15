package me.legit.models.equipment;

import java.util.List;

public class CustomEquipmentPart {

    private int slotIndex;
    private List<CustomEquipmentCustomization> customizations;

    public CustomEquipmentPart() {
    }

    public CustomEquipmentPart(int slotIndex, List<CustomEquipmentCustomization> customizations) {
        this.slotIndex = slotIndex;
        this.customizations = customizations;
    }

    public int getSlotIndex() {
        return slotIndex;
    }

    public void setSlotIndex(int slotIndex) {
        this.slotIndex = slotIndex;
    }

    public List<CustomEquipmentCustomization> getCustomizations() {
        return customizations;
    }

    public void setCustomizations(List<CustomEquipmentCustomization> customizations) {
        this.customizations = customizations;
    }
}
