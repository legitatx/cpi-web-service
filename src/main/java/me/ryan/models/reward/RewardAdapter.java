package me.legit.models.reward;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import me.legit.models.equipment.CustomEquipment;
import me.legit.models.equipment.CustomEquipmentCustomization;
import me.legit.models.equipment.CustomEquipmentPart;
import me.legit.models.equipment.EquipmentCustomizationType;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RewardAdapter extends TypeAdapter<Reward> {

    @Override
    public void write(JsonWriter out, Reward reward) throws IOException {
        out.setSerializeNulls(true);

        out.beginObject();
        out.name("coins").value(reward.getCoins());

        out.name("currency");
        out.beginObject();
        out.name("coins").value(reward.getCoins());
        out.endObject();

        out.name("mascotXP");
        if (reward.getMascotXP() == null) {
            out.nullValue();
        } else {
            out.beginObject();
            for (Map.Entry<String, Integer> entry : reward.getMascotXP().entrySet()) {
                out.name(entry.getKey()).value(entry.getValue());
            }
            out.endObject();
        }

        out.name("collectibleCurrency");
        if (reward.getCollectibleCurrency() == null) {
            out.nullValue();
        } else {
            out.beginObject();
            for (Map.Entry<String, Integer> entry : reward.getCollectibleCurrency().entrySet()) {
                out.name(entry.getKey()).value(entry.getValue());
            }
            out.endObject();
        }

        out.name("colourPacks");
        if (reward.getColourPacks() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (String color : reward.getColourPacks()) {
                out.value(color);
            }
            out.endArray();
        }

        out.name("decals");
        if (reward.getDecals() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (Integer decal : reward.getDecals()) {
                out.value(decal);
            }
            out.endArray();
        }

        out.name("fabrics");
        if (reward.getFabrics() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (Integer fabric : reward.getFabrics()) {
                out.value(fabric);
            }
            out.endArray();
        }

        out.name("emotePacks");
        if (reward.getEmotePacks() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (String emote : reward.getEmotePacks()) {
                out.value(emote);
            }
            out.endArray();
        }

        out.name("sizzleClips");
        if (reward.getSizzleClips() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (Integer sizzleClip : reward.getSizzleClips()) {
                out.value(sizzleClip);
            }
            out.endArray();
        }

        out.name("equipmentTemplates");
        if (reward.getEquipmentTemplates() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (Integer equipmentTemplate : reward.getEquipmentTemplates()) {
                out.value(equipmentTemplate);
            }
            out.endArray();
        }

        out.name("equipmentInstances");
        if (reward.getEquipmentInstances() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (CustomEquipment equipment : reward.getEquipmentInstances()) {
                out.beginObject();
                out.name("definitionId").value(equipment.getDefinitionId());
                out.name("parts");
                out.beginArray();
                for (CustomEquipmentPart part : equipment.getParts()) {
                    out.beginObject();
                    out.name("slotIndex").value(part.getSlotIndex());
                    out.name("customizations");
                    out.beginArray();
                    for (CustomEquipmentCustomization customization : part.getCustomizations()) {
                        out.beginObject();
                        out.name("type").value(customization.getType().ordinal());
                        out.name("definitionId").value(customization.getDefinitionId());
                        out.name("index").value(customization.getIndex());
                        out.name("scale").value(customization.getScale());
                        out.name("rotation").value(customization.getRotation());
                        out.name("repeat").value(customization.isRepeat());
                        out.name("uoffset").value(customization.getUoffset());
                        out.name("voffset").value(customization.getVoffset());
                        out.endObject();
                    }
                    out.endArray();
                    out.endObject();
                }
                out.endArray();
                out.name("equipmentId").value(equipment.getEquipmentId());
                out.name("dateTimeCreated").value(equipment.getDateTimeCreated());
                out.name("source").value("None");
                out.name("sourceId").value(0);
                out.endObject();
            }
            out.endArray();
        }

        out.name("lots");
        if (reward.getLots() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (String lot : reward.getLots()) {
                out.value(lot);
            }
            out.endArray();
        }

        out.name("decorationInstances");
        if (reward.getDecorationInstances() == null) {
            out.nullValue();
        } else {
            out.beginObject();
            for (Map.Entry<Integer, Integer> entry : reward.getDecorationInstances().entrySet()) {
                out.name(Integer.toString(entry.getKey())).value(entry.getValue());
            }
            out.endObject();
        }

        out.name("structureInstances");
        if (reward.getStructureInstances() == null) {
            out.nullValue();
        } else {
            out.beginObject();
            for (Map.Entry<Integer, Integer> entry : reward.getStructureInstances().entrySet()) {
                out.name(Integer.toString(entry.getKey())).value(entry.getValue());
            }
            out.endObject();
        }

        out.name("decorationPurchaseRights");
        if (reward.getDecorationPurchaseRights() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (Integer decoration : reward.getDecorationPurchaseRights()) {
                out.value(decoration);
            }
            out.endArray();
        }

        out.name("structurePurchaseRights");
        if (reward.getStructurePurchaseRights() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (Integer structure : reward.getStructurePurchaseRights()) {
                out.value(structure);
            }
            out.endArray();
        }

        out.name("musicTracks");
        if (reward.getMusicTracks() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (Integer musicTrack : reward.getMusicTracks()) {
                out.value(musicTrack);
            }
            out.endArray();
        }

        out.name("lighting");
        if (reward.getLighting() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (Integer lighting : reward.getLighting()) {
                out.value(lighting);
            }
            out.endArray();
        }

        out.name("durables");
        if (reward.getDurables() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (Integer durable : reward.getDurables()) {
                out.value(durable);
            }
            out.endArray();
        }

        out.name("tubes");
        if (reward.getTubes() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (Integer tube : reward.getTubes()) {
                out.value(tube);
            }
            out.endArray();
        }

        out.name("savedOutfitSlots").value(0);
        out.name("iglooSlots").value(0);

        out.name("consumables");
        if (reward.getConsumables() == null) {
            out.nullValue();
        } else {
            out.beginObject();
            for (Map.Entry<String, Integer> entry : reward.getConsumables().entrySet()) {
                out.name(entry.getKey()).value(entry.getValue());
            }
            out.endObject();
        }

        out.name("partySupplies");
        if (reward.getPartySupplies() == null) {
            out.nullValue();
        } else {
            out.beginArray();
            for (Integer partySupply : reward.getPartySupplies()) {
                out.value(partySupply);
            }
            out.endArray();
        }
        out.endObject();
    }

    @Override
    public Reward read(JsonReader in) throws IOException {
        Reward.Builder reward = new Reward.Builder();
        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            switch (name) {
                case "coins":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    reward.withCoins(in.nextInt());
                    break;
                case "currency":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    Map<String, Integer> currency = new HashMap<>();
                    in.beginObject();
                    while (in.hasNext()) {
                        String currencyName = in.nextName();
                        int amount = in.nextInt();
                        currency.put(currencyName, amount);
                    }
                    in.endObject();
                    reward.withCurrency(currency);
                    break;
                case "mascotXP":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    Map<String, Integer> mascotXP = new HashMap<>();
                    in.beginObject();
                    while (in.hasNext()) {
                        String mascotName = in.nextName();
                        int xpAmount = in.nextInt();
                        mascotXP.put(mascotName, xpAmount);
                    }
                    in.endObject();
                    reward.withMascotXP(mascotXP);
                    break;
                case "collectibleCurrency":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    Map<String, Integer> collectibleCurrency = new HashMap<>();
                    in.beginObject();
                    while (in.hasNext()) {
                        String currencyName = in.nextName();
                        int amount = in.nextInt();
                        collectibleCurrency.put(currencyName, amount);
                    }
                    in.endObject();
                    reward.withCollectibleCurrency(collectibleCurrency);
                    break;
                case "colourPacks":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<String> colourPacks = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        colourPacks.add(in.nextString());
                    }
                    in.endArray();
                    reward.withColourPacks(colourPacks);
                    break;
                case "decals":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<Integer> decals = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        decals.add(in.nextInt());
                    }
                    in.endArray();
                    reward.withDecals(decals);
                    break;
                case "fabrics":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<Integer> fabrics = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        fabrics.add(in.nextInt());
                    }
                    in.endArray();
                    reward.withFabrics(fabrics);
                    break;
                case "emotePacks":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<String> emotePacks = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        emotePacks.add(in.nextString());
                    }
                    in.endArray();
                    reward.withEmotePacks(emotePacks);
                    break;
                case "sizzleClips":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<Integer> sizzleClips = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        sizzleClips.add(in.nextInt());
                    }
                    in.endArray();
                    reward.withSizzleClips(sizzleClips);
                    break;
                case "equipmentTemplates":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<Integer> equipmentTemplates = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        equipmentTemplates.add(in.nextInt());
                    }
                    in.endArray();
                    reward.withEquipmentTemplates(equipmentTemplates);
                    break;
                case "equipmentInstances":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<CustomEquipment> equipmentList = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        CustomEquipment equipment = new CustomEquipment();
                        in.beginObject();
                        while (in.hasNext()) {
                            String key = in.nextName();
                            switch (key) {
                                case "definitionId":
                                    equipment.setDefinitionId(in.nextInt());
                                    break;
                                case "parts":
                                    List<CustomEquipmentPart> parts = new ArrayList<>();
                                    in.beginArray();
                                    while (in.hasNext()) {
                                        CustomEquipmentPart equipmentPart = new CustomEquipmentPart();
                                        in.beginObject();
                                        while (in.hasNext()) {
                                            String partKey = in.nextName();
                                            if (partKey.equals("slotIndex")) {
                                                equipmentPart.setSlotIndex(in.nextInt());
                                            } else if (partKey.equals("customizations")) {
                                                List<CustomEquipmentCustomization> customizations = new ArrayList<>();
                                                in.beginArray();
                                                while (in.hasNext()) {
                                                    CustomEquipmentCustomization customization = new CustomEquipmentCustomization();
                                                    in.beginObject();
                                                    while (in.hasNext()) {
                                                        String customizationKey = in.nextName();
                                                        switch (customizationKey) {
                                                            case "type":
                                                                int type = in.nextInt();
                                                                EquipmentCustomizationType customizationType = EquipmentCustomizationType.values()[type];
                                                                customization.setType(customizationType);
                                                                break;
                                                            case "definitionId":
                                                                customization.setDefinitionId(in.nextInt());
                                                                break;
                                                            case "index":
                                                                customization.setIndex(in.nextInt());
                                                                break;
                                                            case "scale":
                                                                customization.setScale((float) in.nextDouble());
                                                                break;
                                                            case "rotation":
                                                                customization.setRotation((float) in.nextDouble());
                                                                break;
                                                            case "repeat":
                                                                customization.setRepeat(in.nextBoolean());
                                                                break;
                                                            case "uoffset":
                                                                customization.setUoffset((float) in.nextDouble());
                                                                break;
                                                            case "voffset":
                                                                customization.setVoffset((float) in.nextDouble());
                                                                break;
                                                        }
                                                    }
                                                    in.endObject();
                                                    customizations.add(customization);
                                                }
                                                in.endArray();
                                                equipmentPart.setCustomizations(customizations);
                                            }
                                        }
                                        in.endObject();
                                        parts.add(equipmentPart);
                                    }
                                    in.endArray();
                                    equipment.setParts(parts);
                                    break;
                                case "equipmentId":
                                    equipment.setEquipmentId(in.nextLong());
                                    break;
                                case "dateTimeCreated":
                                    equipment.setDateTimeCreated(in.nextLong());
                                    break;
                                case "source":
                                    equipment.setSource(in.nextString());
                                    break;
                                case "sourceId":
                                    equipment.setSourceId(in.nextInt());
                                    break;
                            }
                        }
                        in.endObject();
                        equipmentList.add(equipment);
                    }
                    in.endArray();
                    reward.withEquipmentInstances(equipmentList);
                    break;
                case "lots":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<String> lots = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        lots.add(in.nextString());
                    }
                    in.endArray();
                    reward.withLots(lots);
                    break;
                case "decorationInstances":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    Map<Integer, Integer> decorationInstances = new HashMap<>();
                    in.beginObject();
                    while (in.hasNext()) {
                        String key = in.nextName();
                        int value = in.nextInt();
                        decorationInstances.put(Integer.valueOf(key), value);
                    }
                    in.endObject();
                    reward.withDecorationInstances(decorationInstances);
                    break;
                case "structureInstances":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    Map<Integer, Integer> structureInstances = new HashMap<>();
                    in.beginObject();
                    while (in.hasNext()) {
                        String key = in.nextName();
                        int value = in.nextInt();
                        structureInstances.put(Integer.valueOf(key), value);
                    }
                    in.endObject();
                    reward.withStructureInstances(structureInstances);
                    break;
                case "decorationPurchaseRights":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<Integer> decorationPurchaseRights = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        decorationPurchaseRights.add(in.nextInt());
                    }
                    in.endArray();
                    reward.withDecorationPurchaseRights(decorationPurchaseRights);
                    break;
                case "structurePurchaseRights":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<Integer> structurePurchaseRights = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        structurePurchaseRights.add(in.nextInt());
                    }
                    in.endArray();
                    reward.withStructurePurchaseRights(structurePurchaseRights);
                    break;
                case "musicTracks":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<Integer> musicTracks = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        musicTracks.add(in.nextInt());
                    }
                    in.endArray();
                    reward.withMusicTracks(musicTracks);
                    break;
                case "lighting":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<Integer> lighting = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        lighting.add(in.nextInt());
                    }
                    in.endArray();
                    reward.withLighting(lighting);
                    break;
                case "durables":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<Integer> durables = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        durables.add(in.nextInt());
                    }
                    in.endArray();
                    reward.withDurables(durables);
                    break;
                case "tubes":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<Integer> tubes = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        tubes.add(in.nextInt());
                    }
                    in.endArray();
                    reward.withTubes(tubes);
                    break;
                case "savedOutfitSlots":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    reward.withSavedOutfitSlots(in.nextInt());
                    break;
                case "iglooSlots":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    reward.withIglooSlots(in.nextInt());
                    break;
                case "consumables":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    Map<String, Integer> consumables = new HashMap<>();
                    in.beginObject();
                    while (in.hasNext()) {
                        String key = in.nextName();
                        int value = in.nextInt();
                        consumables.put(key, value);
                    }
                    in.endObject();
                    reward.withConsumables(consumables);
                    break;
                case "partySupplies":
                    if (in.peek() == JsonToken.NULL) {
                        in.nextNull();
                        break;
                    }

                    List<Integer> partySupplies = new ArrayList<>();
                    in.beginArray();
                    while (in.hasNext()) {
                        partySupplies.add(in.nextInt());
                    }
                    in.endArray();
                    reward.withPartySupplies(partySupplies);
                    break;
            }
        }
        in.endObject();
        return reward.build();
    }
}
