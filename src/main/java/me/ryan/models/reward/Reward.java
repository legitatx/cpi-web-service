package me.legit.models.reward;

import me.legit.models.equipment.CustomEquipment;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Reward {

    public static class Builder {

        private int coins;
        private Map<String, Integer> currency;
        private Map<String, Integer> mascotXP;
        private Map<String, Integer> collectibleCurrency;
        private List<String> colourPacks;
        private List<Integer> decals;
        private List<Integer> fabrics;
        private List<String> emotePacks;
        private List<Integer> sizzleClips;
        private List<Integer> equipmentTemplates;
        private List<CustomEquipment> equipmentInstances;
        private List<String> lots;
        private Map<Integer, Integer> decorationInstances;
        private Map<Integer, Integer> structureInstances;
        private List<Integer> decorationPurchaseRights;
        private List<Integer> structurePurchaseRights;
        private List<Integer> musicTracks;
        private List<Integer> lighting;
        private List<Integer> durables;
        private List<Integer> tubes;
        private int savedOutfitSlots;
        private int iglooSlots;
        private Map<String, Integer> consumables;
        private List<Integer> partySupplies;

        public Builder withCoins(int coins) {
            this.coins = coins;
            return this;
        }

        public Builder withCurrency(Map<String, Integer> currency) {
            this.currency = currency;
            return this;
        }

        public Builder withMascotXP(Map<String, Integer> mascotXP) {
            this.mascotXP = mascotXP;
            return this;
        }

        public Builder withCollectibleCurrency(Map<String, Integer> collectibleCurrency) {
            this.collectibleCurrency = collectibleCurrency;
            return this;
        }

        public Builder withColourPacks(List<String> colourPacks) {
            this.colourPacks = colourPacks;
            return this;
        }

        public Builder withDecals(List<Integer> decals) {
            this.decals = decals;
            return this;
        }

        public Builder withFabrics(List<Integer> fabrics) {
            this.fabrics = fabrics;
            return this;
        }

        public Builder withEmotePacks(List<String> emotePacks) {
            this.emotePacks = emotePacks;
            return this;
        }

        public Builder withSizzleClips(List<Integer> sizzleClips) {
            this.sizzleClips = sizzleClips;
            return this;
        }

        public Builder withEquipmentTemplates(List<Integer> equipmentTemplates) {
            this.equipmentTemplates = equipmentTemplates;
            return this;
        }

        public Builder withEquipmentInstances(List<CustomEquipment> equipmentInstances) {
            this.equipmentInstances = equipmentInstances;
            return this;
        }

        public Builder withLots(List<String> lots) {
            this.lots = lots;
            return this;
        }

        public Builder withDecorationInstances(Map<Integer, Integer> decorationInstances) {
            this.decorationInstances = decorationInstances;
            return this;
        }

        public Builder withStructureInstances(Map<Integer, Integer> structureInstances) {
            this.structureInstances = structureInstances;
            return this;
        }

        public Builder withDecorationPurchaseRights(List<Integer> decorationPurchaseRights) {
            this.decorationPurchaseRights = decorationPurchaseRights;
            return this;
        }

        public Builder withStructurePurchaseRights(List<Integer> structurePurchaseRights) {
            this.structurePurchaseRights = structurePurchaseRights;
            return this;
        }

        public Builder withMusicTracks(List<Integer> musicTracks) {
            this.musicTracks = musicTracks;
            return this;
        }

        public Builder withLighting(List<Integer> lighting) {
            this.lighting = lighting;
            return this;
        }

        public Builder withDurables(List<Integer> durables) {
            this.durables = durables;
            return this;
        }

        public Builder withTubes(List<Integer> tubes) {
            this.tubes = tubes;
            return this;
        }

        public Builder withSavedOutfitSlots(int savedOutfitSlots) {
            this.savedOutfitSlots = savedOutfitSlots;
            return this;
        }

        public Builder withIglooSlots(int iglooSlots) {
            this.iglooSlots = iglooSlots;
            return this;
        }

        public Builder withConsumables(Map<String, Integer> consumables) {
            this.consumables = consumables;
            return this;
        }

        public Builder withPartySupplies(List<Integer> partySupplies) {
            this.partySupplies = partySupplies;
            return this;
        }

        public Reward build() {
            return new Reward(coins, currency, mascotXP, collectibleCurrency, colourPacks, decals, fabrics, emotePacks, sizzleClips, equipmentTemplates, equipmentInstances, lots, decorationInstances, structureInstances, decorationPurchaseRights, structurePurchaseRights, musicTracks, lighting, durables, tubes, savedOutfitSlots, iglooSlots, consumables, partySupplies);
        }
    }

    private int coins;
    private Map<String, Integer> currency;
    private Map<String, Integer> mascotXP;
    private Map<String, Integer> collectibleCurrency;
    private List<String> colourPacks;
    private List<Integer> decals;
    private List<Integer> fabrics;
    private List<String> emotePacks;
    private List<Integer> sizzleClips;
    private List<Integer> equipmentTemplates;
    private List<CustomEquipment> equipmentInstances;
    private List<String> lots;
    private Map<Integer, Integer> decorationInstances;
    private Map<Integer, Integer> structureInstances;
    private List<Integer> decorationPurchaseRights;
    private List<Integer> structurePurchaseRights;
    private List<Integer> musicTracks;
    private List<Integer> lighting;
    private List<Integer> durables;
    private List<Integer> tubes;
    private int savedOutfitSlots;
    private int iglooSlots;
    private Map<String, Integer> consumables;
    private List<Integer> partySupplies;

    private Reward(int coins, Map<String, Integer> currency, Map<String, Integer> mascotXP, Map<String, Integer> collectibleCurrency, List<String> colourPacks, List<Integer> decals, List<Integer> fabrics, List<String> emotePacks, List<Integer> sizzleClips, List<Integer> equipmentTemplates, List<CustomEquipment> equipmentInstances, List<String> lots, Map<Integer, Integer> decorationInstances, Map<Integer, Integer> structureInstances, List<Integer> decorationPurchaseRights, List<Integer> structurePurchaseRights, List<Integer> musicTracks, List<Integer> lighting, List<Integer> durables, List<Integer> tubes, int savedOutfitSlots, int iglooSlots, Map<String, Integer> consumables, List<Integer> partySupplies) {
        this.coins = coins;
        this.currency = currency;
        this.mascotXP = mascotXP;
        this.collectibleCurrency = collectibleCurrency;
        this.colourPacks = colourPacks;
        this.decals = decals;
        this.fabrics = fabrics;
        this.emotePacks = emotePacks;
        this.sizzleClips = sizzleClips;
        this.equipmentTemplates = equipmentTemplates;
        this.equipmentInstances = equipmentInstances;
        this.lots = lots;
        this.decorationInstances = decorationInstances;
        this.structureInstances = structureInstances;
        this.decorationPurchaseRights = decorationPurchaseRights;
        this.structurePurchaseRights = structurePurchaseRights;
        this.musicTracks = musicTracks;
        this.lighting = lighting;
        this.durables = durables;
        this.tubes = tubes;
        this.savedOutfitSlots = savedOutfitSlots;
        this.iglooSlots = iglooSlots;
        this.consumables = consumables;
        this.partySupplies = partySupplies;
    }

    public int getCoins() {
        return coins;
    }

    public void setCoins(int coins) {
        this.coins = coins;
    }

    public Map<String, Integer> getCurrency() {
        return currency;
    }

    public void setCurrency(Map<String, Integer> currency) {
        this.currency = currency;
    }

    public Map<String, Integer> getMascotXP() {
        return mascotXP;
    }

    public void setMascotXP(Map<String, Integer> mascotXP) {
        this.mascotXP = mascotXP;
    }

    public Map<String, Integer> getCollectibleCurrency() {
        return collectibleCurrency;
    }

    public void setCollectibleCurrency(Map<String, Integer> collectibleCurrency) {
        this.collectibleCurrency = collectibleCurrency;
    }

    public List<String> getColourPacks() {
        return colourPacks;
    }

    public void setColourPacks(List<String> colourPacks) {
        this.colourPacks = colourPacks;
    }

    public List<Integer> getDecals() {
        return decals;
    }

    public void setDecals(List<Integer> decals) {
        this.decals = decals;
    }

    public List<Integer> getFabrics() {
        return fabrics;
    }

    public void setFabrics(List<Integer> fabrics) {
        this.fabrics = fabrics;
    }

    public List<String> getEmotePacks() {
        return emotePacks;
    }

    public void setEmotePacks(List<String> emotePacks) {
        this.emotePacks = emotePacks;
    }

    public List<Integer> getSizzleClips() {
        return sizzleClips;
    }

    public void setSizzleClips(List<Integer> sizzleClips) {
        this.sizzleClips = sizzleClips;
    }

    public List<Integer> getEquipmentTemplates() {
        return equipmentTemplates;
    }

    public void setEquipmentTemplates(List<Integer> equipmentTemplates) {
        this.equipmentTemplates = equipmentTemplates;
    }

    public List<CustomEquipment> getEquipmentInstances() {
        return equipmentInstances;
    }

    public void setEquipmentInstances(List<CustomEquipment> equipmentInstances) {
        this.equipmentInstances = equipmentInstances;
    }

    public List<String> getLots() {
        return lots;
    }

    public void setLots(List<String> lots) {
        this.lots = lots;
    }

    public Map<Integer, Integer> getDecorationInstances() {
        return decorationInstances;
    }

    public void setDecorationInstances(Map<Integer, Integer> decorationInstances) {
        this.decorationInstances = decorationInstances;
    }

    public Map<Integer, Integer> getStructureInstances() {
        return structureInstances;
    }

    public void setStructureInstances(Map<Integer, Integer> structureInstances) {
        this.structureInstances = structureInstances;
    }

    public List<Integer> getDecorationPurchaseRights() {
        return decorationPurchaseRights;
    }

    public void setDecorationPurchaseRights(List<Integer> decorationPurchaseRights) {
        this.decorationPurchaseRights = decorationPurchaseRights;
    }

    public List<Integer> getStructurePurchaseRights() {
        return structurePurchaseRights;
    }

    public void setStructurePurchaseRights(List<Integer> structurePurchaseRights) {
        this.structurePurchaseRights = structurePurchaseRights;
    }

    public List<Integer> getMusicTracks() {
        return musicTracks;
    }

    public void setMusicTracks(List<Integer> musicTracks) {
        this.musicTracks = musicTracks;
    }

    public List<Integer> getLighting() {
        return lighting;
    }

    public void setLighting(List<Integer> lighting) {
        this.lighting = lighting;
    }

    public List<Integer> getDurables() {
        return durables;
    }

    public void setDurables(List<Integer> durables) {
        this.durables = durables;
    }

    public List<Integer> getTubes() {
        return tubes;
    }

    public void setTubes(List<Integer> tubes) {
        this.tubes = tubes;
    }

    public int getSavedOutfitSlots() {
        return savedOutfitSlots;
    }

    public void setSavedOutfitSlots(int savedOutfitSlots) {
        this.savedOutfitSlots = savedOutfitSlots;
    }

    public int getIglooSlots() {
        return iglooSlots;
    }

    public void setIglooSlots(int iglooSlots) {
        this.iglooSlots = iglooSlots;
    }

    public Map<String, Integer> getConsumables() {
        return consumables;
    }

    public void setConsumables(Map<String, Integer> consumables) {
        this.consumables = consumables;
    }

    public List<Integer> getPartySupplies() {
        return partySupplies;
    }

    public void setPartySupplies(List<Integer> partySupplies) {
        this.partySupplies = partySupplies;
    }

    public boolean isEmpty() {
        boolean coinsEmpty = coins == 0;
        boolean currencyEmpty = currency == null || currency.isEmpty();
        boolean mascotXPEmpty = mascotXP == null || mascotXP.isEmpty();
        boolean collectibleCurrencyEmpty = collectibleCurrency == null || collectibleCurrency.isEmpty();
        boolean colourPacksEmpty = colourPacks == null || colourPacks.isEmpty();
        boolean decalsEmpty = decals == null || decals.isEmpty();
        boolean fabricsEmpty = fabrics == null || fabrics.isEmpty();
        boolean emotePacksEmpty = emotePacks == null || emotePacks.isEmpty();
        boolean sizzleClipsEmpty = sizzleClips == null || sizzleClips.isEmpty();
        boolean equipmentTemplatesEmpty = equipmentTemplates == null || equipmentTemplates.isEmpty();
        boolean equipmentInstancesEmpty = equipmentInstances == null || equipmentInstances.isEmpty();
        boolean lotsEmpty = lots == null || lots.isEmpty();
        boolean decorationInstancesEmpty = decorationInstances == null || decorationInstances.isEmpty();
        boolean structureInstancesEmpty = structureInstances == null || structureInstances.isEmpty();
        boolean decorationPurchaseRightsEmpty = decorationPurchaseRights == null || decorationPurchaseRights.isEmpty();
        boolean structurePurchaseRightsEmpty = structurePurchaseRights == null || structurePurchaseRights.isEmpty();
        boolean musicTracksEmpty = musicTracks == null || musicTracks.isEmpty();
        boolean lightingEmpty = lighting == null || lighting.isEmpty();
        boolean durablesEmpty = durables == null || durables.isEmpty();
        boolean tubesEmpty = tubes == null || tubes.isEmpty();
        boolean savedOutfitSlotsEmpty = savedOutfitSlots == 0;
        boolean iglooSlotsEmpty = iglooSlots == 0;
        boolean consumablesEmpty = consumables == null || consumables.isEmpty();
        boolean partySuppliesEmpty = partySupplies == null || partySupplies.isEmpty();

        return coinsEmpty && currencyEmpty && mascotXPEmpty
                && collectibleCurrencyEmpty && colourPacksEmpty && decalsEmpty && fabricsEmpty
                && emotePacksEmpty && sizzleClipsEmpty && equipmentTemplatesEmpty && equipmentInstancesEmpty
                && lotsEmpty && decorationInstancesEmpty && structureInstancesEmpty
                && decorationPurchaseRightsEmpty && structurePurchaseRightsEmpty
                && musicTracksEmpty && lightingEmpty && durablesEmpty
                && tubesEmpty && savedOutfitSlotsEmpty && iglooSlotsEmpty
                && consumablesEmpty && partySuppliesEmpty;
    }

    public void addReward(Reward reward) {
        this.coins = coins + reward.getCoins();

        if (currency != null) {
            if (reward.getCurrency() != null) {
                this.currency = Stream.concat(currency.entrySet().stream(), reward.getCurrency().entrySet().stream()).collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Optional.ofNullable(entry.getValue()).orElse(0),
                        Integer::sum // duplicate keys -> add two keys together
                        )
                );
            }
        } else {
            this.currency = reward.getCurrency();
        }

        if (mascotXP != null) {
            if (reward.getMascotXP() != null) {
                this.mascotXP = Stream.concat(mascotXP.entrySet().stream(), reward.getMascotXP().entrySet().stream()).collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Optional.ofNullable(entry.getValue()).orElse(0),
                        Integer::sum
                        )
                );
            }
        } else {
            this.mascotXP = reward.getMascotXP();
        }

        if (collectibleCurrency != null) {
            if (reward.getCollectibleCurrency() != null) {
                this.collectibleCurrency = Stream.concat(collectibleCurrency.entrySet().stream(), reward.getCollectibleCurrency().entrySet().stream()).collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Optional.ofNullable(entry.getValue()).orElse(0),
                        Integer::sum
                        )
                );
            }
        } else {
            this.currency = reward.getCollectibleCurrency();
        }

        if (colourPacks != null) {
            this.colourPacks = Stream.concat(colourPacks.stream(), Optional.ofNullable(reward.getColourPacks()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.colourPacks = reward.getColourPacks();
        }

        if (decals != null) {
            this.decals = Stream.concat(decals.stream(), Optional.ofNullable(reward.getDecals()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.decals = reward.getDecals();
        }

        if (fabrics != null) {
            this.fabrics = Stream.concat(fabrics.stream(), Optional.ofNullable(reward.getFabrics()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.fabrics = reward.getFabrics();
        }

        if (emotePacks != null) {
            this.emotePacks = Stream.concat(emotePacks.stream(), Optional.ofNullable(reward.getEmotePacks()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.emotePacks = reward.getEmotePacks();
        }

        if (sizzleClips != null) {
            this.sizzleClips = Stream.concat(sizzleClips.stream(), Optional.ofNullable(reward.getSizzleClips()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.sizzleClips = reward.getSizzleClips();
        }

        if (equipmentTemplates != null) {
            this.equipmentTemplates = Stream.concat(equipmentTemplates.stream(), Optional.ofNullable(reward.getEquipmentTemplates()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.equipmentTemplates = reward.getEquipmentTemplates();
        }

        if (equipmentInstances != null) {
            this.equipmentInstances = Stream.concat(equipmentInstances.stream(), Optional.ofNullable(reward.getEquipmentInstances()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.equipmentInstances = reward.getEquipmentInstances();
        }

        if (lots != null) {
            this.lots = Stream.concat(lots.stream(), Optional.ofNullable(reward.getLots()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.lots = reward.getLots();
        }

        if (decorationInstances != null) {
            if (reward.getDecorationInstances() != null) {
                this.decorationInstances = Stream.concat(decorationInstances.entrySet().stream(), reward.getDecorationInstances().entrySet().stream()).collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Optional.ofNullable(entry.getValue()).orElse(0),
                        Integer::sum
                        )
                );
            }
        } else {
            this.decorationInstances = reward.getDecorationInstances();
        }

        if (structureInstances != null) {
            if (reward.getStructureInstances() != null) {
                this.structureInstances = Stream.concat(structureInstances.entrySet().stream(), reward.getStructureInstances().entrySet().stream()).collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Optional.ofNullable(entry.getValue()).orElse(0),
                        Integer::sum
                        )
                );
            }
        } else {
            this.structureInstances = reward.getStructureInstances();
        }

        if (decorationPurchaseRights != null) {
            this.decorationPurchaseRights = Stream.concat(decorationPurchaseRights.stream(), Optional.ofNullable(reward.getDecorationPurchaseRights()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.decorationPurchaseRights = reward.getDecorationPurchaseRights();
        }

        if (structurePurchaseRights != null) {
            this.structurePurchaseRights = Stream.concat(structurePurchaseRights.stream(), Optional.ofNullable(reward.getStructurePurchaseRights()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.structurePurchaseRights = reward.getStructurePurchaseRights();
        }

        if (musicTracks != null) {
            this.musicTracks = Stream.concat(musicTracks.stream(), Optional.ofNullable(reward.getMusicTracks()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.musicTracks = reward.getMusicTracks();
        }

        if (lighting != null) {
            this.lighting = Stream.concat(lighting.stream(), Optional.ofNullable(reward.getLighting()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.lighting = reward.getLighting();
        }

        if (durables != null) {
            this.durables = Stream.concat(durables.stream(), Optional.ofNullable(reward.getDurables()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.durables = reward.getDurables();
        }

        if (tubes != null) {
            this.tubes = Stream.concat(tubes.stream(), Optional.ofNullable(reward.getTubes()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.tubes = reward.getTubes();
        }

        this.savedOutfitSlots = savedOutfitSlots + reward.getSavedOutfitSlots();
        this.iglooSlots = iglooSlots + reward.getIglooSlots();

        if (consumables != null) {
            if (reward.getConsumables() != null) {
                this.consumables = Stream.concat(consumables.entrySet().stream(), reward.getConsumables().entrySet().stream()).collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Optional.ofNullable(entry.getValue()).orElse(0),
                        Integer::sum
                        )
                );
            }
        } else {
            this.consumables = reward.getConsumables();
        }

        if (partySupplies != null) {
            this.partySupplies = Stream.concat(partySupplies.stream(), Optional.ofNullable(reward.getPartySupplies()).orElseGet(Collections::emptyList).stream()).collect(Collectors.toList());
        } else {
            this.partySupplies = reward.getPartySupplies();
        }
    }

    @Override
    public String toString() {
        return "Reward{" +
                "coins=" + coins +
                ", currency=" + currency +
                ", mascotXP=" + mascotXP +
                ", collectibleCurrency=" + collectibleCurrency +
                ", colourPacks=" + colourPacks +
                ", decals=" + decals +
                ", fabrics=" + fabrics +
                ", emotePacks=" + emotePacks +
                ", sizzleClips=" + sizzleClips +
                ", equipmentTemplates=" + equipmentTemplates +
                ", equipmentInstances=" + equipmentInstances +
                ", lots=" + lots +
                ", decorationInstances=" + decorationInstances +
                ", structureInstances=" + structureInstances +
                ", decorationPurchaseRights=" + decorationPurchaseRights +
                ", structurePurchaseRights=" + structurePurchaseRights +
                ", musicTracks=" + musicTracks +
                ", lighting=" + lighting +
                ", durables=" + durables +
                ", tubes=" + tubes +
                ", savedOutfitSlots=" + savedOutfitSlots +
                ", iglooSlots=" + iglooSlots +
                ", consumables=" + consumables +
                ", partySupplies=" + partySupplies +
                '}';
    }
}
