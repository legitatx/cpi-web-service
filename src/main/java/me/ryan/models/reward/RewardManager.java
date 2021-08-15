package me.legit.models.reward;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import me.legit.APICore;
import me.legit.models.decoration.DecorationId;
import me.legit.models.decoration.DecorationType;
import me.legit.models.equipment.CustomEquipment;
import me.legit.service.ProgressionService;
import org.json.JSONObject;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class RewardManager {

    private Firestore db;
    private Gson gson;

    public RewardManager(Firestore db) {
        this.db = db;

        GsonBuilder builder = new GsonBuilder();
        builder.registerTypeAdapter(Reward.class, new RewardAdapter());
        this.gson = builder.create();
    }

    public Reward getReward(String json) {
        return gson.fromJson(json, Reward.class);
    }

    public String getReward(Reward reward) {
        return gson.toJson(reward);
    }

    public JSONObject getRewardAsJSON(Reward reward) {
        return new JSONObject(getReward(reward));
    }

    @SuppressWarnings("Duplicates")
    public List<ApiFuture<String>> saveReward(DocumentSnapshot snapshot, Reward reward, JSONObject finalResponse) {
        List<ApiFuture<String>> transactions = new ArrayList<>();
        if (reward.getCoins() > 0) {
            ApiFuture<String> coinTransaction = db.runTransaction(transaction -> {
                Long oldCoins = snapshot.getLong("assets.coins");
                if (oldCoins != null) {
                    transaction.update(snapshot.getReference(), "assets.coins", oldCoins + reward.getCoins());
                    return "assets.coins had a value of " + oldCoins + ", updated with a new value of " + oldCoins + " + " + reward.getCoins() + "!";
                } else {
                    return "oldCoins == null (this should not happen) -- could not save new coin amount (" + reward.getCoins() + ") for user " + snapshot.getId() + "!";
                }
            });
            transactions.add(coinTransaction);
            //APICore.getLogger().info("Did reward transaction save new coins (" + reward.getCoins() + ") for user " + snapshot.getId() + "?: Result - (" + coinTransaction.get() + ")");
        }
        if (reward.getCurrency() != null) {
            for (String key : reward.getCurrency().keySet()) {
                Integer newCurrency = reward.getCurrency().get(key);
                ApiFuture<String> currencyTransaction = db.runTransaction(transaction -> {
                    Long oldCurrency = snapshot.getLong("assets.currency." + key);
                    if (oldCurrency != null) {
                        transaction.update(snapshot.getReference(), "assets.currency." + key, oldCurrency + newCurrency);
                        return "assets.currency." + key + " had a value of " + oldCurrency + ", updated with a new value of " + oldCurrency + " + " + newCurrency + "!";
                    } else {
                        transaction.update(snapshot.getReference(), "assets.currency." + key, newCurrency);
                        return "assets.currency." + key + " was null, setting new value for " + key + " = " + newCurrency;
                    }
                });
                transactions.add(currencyTransaction);
                //APICore.getLogger().info("Did reward transaction save new currency value (" + key + " = " + newCurrency + ") for user " + snapshot.getId() + "?: Result - (" + currencyTransaction.get() + ")");
            }
        } else {
            if (reward.getCoins() > 0) {
                int newCurrency = reward.getCoins();
                String key = "coins";
                ApiFuture<String> currencyTransaction = db.runTransaction(transaction -> {
                    Long oldCurrency = snapshot.getLong("assets.currency." + key);
                    if (oldCurrency != null) {
                        transaction.update(snapshot.getReference(), "assets.currency." + key, oldCurrency + newCurrency);
                        return "assets.currency." + key + " had a value of " + oldCurrency + ", updated with a new value of " + oldCurrency + " + " + newCurrency + "!";
                    } else {
                        transaction.update(snapshot.getReference(), "assets.currency." + key, newCurrency);
                        return "assets.currency." + key + " was null, setting new value for " + key + " = " + newCurrency;
                    }
                });
                transactions.add(currencyTransaction);
                //APICore.getLogger().info("Did reward transaction save new currency value (" + key + " = " + newCurrency + ") for user " + snapshot.getId() + "?: Result - (" + currencyTransaction.get() + ")");
            }
        }
        if (reward.getMascotXP() != null) {
            Map<String, Long> mascotXP = (Map<String, Long>) snapshot.get("assets.mascotXP");
            APICore.getLogger().info("MASCOT XP: " + mascotXP);
            int level = APICore.getProgressionService().getLevel(mascotXP);
            APICore.getLogger().info("LEVEL: " + level);

            for (String key : reward.getMascotXP().keySet()) {
                Integer newMascotXP = reward.getMascotXP().get(key);
                ApiFuture<String> mascotXPTransaction = db.runTransaction(transaction -> {
                    Long oldMascotXP = snapshot.getLong("assets.mascotXP." + key);
                    if (oldMascotXP != null) {
                        long newXpTotal = APICore.getProgressionService().addXp(key, newMascotXP, oldMascotXP);
                        transaction.update(snapshot.getReference(), "assets.mascotXP." + key, newXpTotal);
                        mascotXP.put(key, newXpTotal);
                        return "assets.mascotXP." + key + " had a value of " + oldMascotXP + ", updated with a new value of " + newXpTotal + "!";
                    } else {
                        long newXpTotal = APICore.getProgressionService().addXp(key, newMascotXP, 0L);
                        transaction.update(snapshot.getReference(), "assets.mascotXP." + key, newXpTotal);
                        mascotXP.put(key, newXpTotal);
                        return "assets.mascotXP." + key + " was null, setting new value for " + key + " = " + newXpTotal;
                    }
                });
                transactions.add(mascotXPTransaction);
                //APICore.getLogger().info("Did reward transaction save new mascot XP value (" + key + " = " + newMascotXP + ") for user " + snapshot.getId() + "?: Result - (" + mascotXPTransaction.get() + ")");
            }

            int num = 0;
            APICore.getLogger().info("NUM: " + num);
            for (Map.Entry<String, Long> xp : mascotXP.entrySet()) {
                APICore.getLogger().info(xp.getKey() + ": " + xp.getValue());
                num += ProgressionService.GetMascotLevelFromXP(xp.getValue());
                APICore.getLogger().info("NEW NUM: " + num);
            }
            if (num > level) {
                APICore.getLogger().info("num > level");
                if (finalResponse != null) {
                    JSONObject wsEvents = new JSONObject();
                    wsEvents.put("details", num);
                    wsEvents.put("type", 3);
                    finalResponse.put("wsEvents", wsEvents);
                    APICore.getLogger().info("wsEvents - " + wsEvents.toString());
                }
            } else {
                APICore.getLogger().info("NUM 2 = " + num + ", LEVEL 2 = " + level);
                APICore.getLogger().info("num < level || num == level");
            }
        }
        if (reward.getCollectibleCurrency() != null) {
            for (String key : reward.getCollectibleCurrency().keySet()) {
                Integer newCollectibleCurrency = reward.getCollectibleCurrency().get(key);
                if (newCollectibleCurrency > 0) {
                    ApiFuture<String> collectibleCurrencyTransaction = db.runTransaction(transaction -> {
                        Long oldCollectibleCurrency = snapshot.getLong("assets.collectibleCurrencies." + key);
                        if (oldCollectibleCurrency != null) {
                            transaction.update(snapshot.getReference(), "assets.collectibleCurrencies." + key, oldCollectibleCurrency + newCollectibleCurrency);
                            return "assets.collectibleCurrencies." + key + " had a value of " + oldCollectibleCurrency + ", updated with a new value of " + oldCollectibleCurrency + " + " + newCollectibleCurrency + "!";
                        } else {
                            transaction.update(snapshot.getReference(), "assets.collectibleCurrencies." + key, newCollectibleCurrency);
                            return "assets.collectibleCurrencies." + key + " was null, setting new value for " + key + " = " + newCollectibleCurrency;
                        }
                    });
                    transactions.add(collectibleCurrencyTransaction);
                    //APICore.getLogger().info("Did reward transaction save new collectible currency value (" + key + " = " + newCollectibleCurrency + ") for user " + snapshot.getId() + "?: Result - (" + collectibleCurrencyTransaction.get() + ")");
                } else {
                    APICore.getLogger().info("assets.collectibleCurrencies." + key + " had a value less than or equal to 0, skipping...");
                }
            }
        }
        if (reward.getColourPacks() != null) {
            ApiFuture<String> colourPacksTransaction = db.runTransaction(transaction -> {
                transaction.update(snapshot.getReference(), "assets.colourPacks", FieldValue.arrayUnion(reward.getColourPacks().toArray()));
                return "Updating colour packs...";
            });
            transactions.add(colourPacksTransaction);
        }
        if (reward.getDecals() != null) {
            ApiFuture<String> decalsTransaction = db.runTransaction(transaction -> {
                transaction.update(snapshot.getReference(), "assets.decals", FieldValue.arrayUnion(reward.getDecals().toArray()));
                return "Updating decals...";
            });
            transactions.add(decalsTransaction);
        }
        if (reward.getFabrics() != null) {
            ApiFuture<String> fabricsTransaction = db.runTransaction(transaction -> {
                transaction.update(snapshot.getReference(), "assets.fabrics", FieldValue.arrayUnion(reward.getFabrics().toArray()));
                return "Updating fabrics...";
            });
            transactions.add(fabricsTransaction);
        }
        if (reward.getEmotePacks() != null) {
            ApiFuture<String> emotePacksTransaction = db.runTransaction(transaction -> {
                transaction.update(snapshot.getReference(), "assets.emotePacks", FieldValue.arrayUnion(reward.getEmotePacks().toArray()));
                return "Updating emote packs...";
            });
            transactions.add(emotePacksTransaction);
        }
        if (reward.getSizzleClips() != null) {
            ApiFuture<String> sizzleClipsTransaction = db.runTransaction(transaction -> {
                transaction.update(snapshot.getReference(), "assets.sizzleClips", FieldValue.arrayUnion(reward.getSizzleClips().toArray()));
                return "Updating sizzle clips...";
            });
            transactions.add(sizzleClipsTransaction);
        }
        if (reward.getEquipmentTemplates() != null) {
            ApiFuture<String> equipmentTemplatesTransaction = db.runTransaction(transaction -> {
                transaction.update(snapshot.getReference(), "assets.equipmentTemplates", FieldValue.arrayUnion(reward.getEquipmentTemplates().toArray()));
                return "Updating equipment templates...";
            });
            transactions.add(equipmentTemplatesTransaction);
        }
        if (reward.getEquipmentInstances() != null) {
            byte[] array = new byte[8];
            for (CustomEquipment customEquipment : reward.getEquipmentInstances()) {
                ThreadLocalRandom.current().nextBytes(array);

                customEquipment.setDateTimeCreated(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
                customEquipment.setEquipmentId(new BigInteger(49, ThreadLocalRandom.current()).longValue());

                ApiFuture<String> equipmentInstancesTransaction = db.runTransaction(transaction -> {
                    transaction.update(snapshot.getReference(), "equipment", FieldValue.arrayUnion(customEquipment.toMap()));
                    return "Updating equipment instances...";
                });
                transactions.add(equipmentInstancesTransaction);
            }
        }
        if (reward.getLots() != null) {
            ApiFuture<String> lotsTransaction = db.runTransaction(transaction -> {
                transaction.update(snapshot.getReference(), "assets.lots", FieldValue.arrayUnion(reward.getLots().toArray()));
                return "Updating lots...";
            });
            transactions.add(lotsTransaction);
        }
        if (reward.getDecorationInstances() != null) {
            for (Map.Entry<Integer, Integer> entry : reward.getDecorationInstances().entrySet()) {
                DecorationId decorationId = new DecorationId(entry.getKey(), DecorationType.DECORATION);
                ApiFuture<String> decorationInstanceTransaction = db.runTransaction(transaction -> {
                    Long oldDecorationInstance = snapshot.getLong("decorationInventory." + decorationId.toString());
                    if (oldDecorationInstance != null) {
                        transaction.update(snapshot.getReference(), "decorationInventory." + decorationId.toString(), oldDecorationInstance + entry.getValue());
                        return "decorationInventory." + decorationId.toString() + " had a value of " + oldDecorationInstance + ", updated with a new value of " + oldDecorationInstance + " + " + entry.getValue() + "!";
                    } else {
                        transaction.update(snapshot.getReference(), "decorationInventory." + decorationId.toString(), entry.getValue());
                        return "decorationInventory." + decorationId.toString() + " was null, setting new value for " + decorationId.toString() + " = " + entry.getValue();
                    }
                });
                transactions.add(decorationInstanceTransaction);
            }
        }
        if (reward.getStructureInstances() != null) {
            for (Map.Entry<Integer, Integer> entry : reward.getStructureInstances().entrySet()) {
                DecorationId decorationId = new DecorationId(entry.getKey(), DecorationType.STRUCTURE);
                ApiFuture<String> structureInstanceTransaction = db.runTransaction(transaction -> {
                    Long oldStructureInstance = snapshot.getLong("decorationInventory." + decorationId.toString());
                    if (oldStructureInstance != null) {
                        transaction.update(snapshot.getReference(), "decorationInventory." + decorationId.toString(), oldStructureInstance + entry.getValue());
                        return "decorationInventory." + decorationId.toString() + " had a value of " + oldStructureInstance + ", updated with a new value of " + oldStructureInstance + " + " + entry.getValue() + "!";
                    } else {
                        transaction.update(snapshot.getReference(), "decorationInventory." + decorationId.toString(), entry.getValue());
                        return "decorationInventory." + decorationId.toString() + " was null, setting new value for " + decorationId.toString() + " = " + entry.getValue();
                    }
                });
                transactions.add(structureInstanceTransaction);
            }
        }
        if (reward.getDecorationPurchaseRights() != null) {
            ApiFuture<String> decorationsTransaction = db.runTransaction(transaction -> {
                transaction.update(snapshot.getReference(), "assets.decorations", FieldValue.arrayUnion(reward.getDecorationPurchaseRights().toArray()));
                return "Updating decorations...";
            });
            transactions.add(decorationsTransaction);
        }
        if (reward.getStructurePurchaseRights() != null) {
            ApiFuture<String> structuresTransaction = db.runTransaction(transaction -> {
                transaction.update(snapshot.getReference(), "assets.structures", FieldValue.arrayUnion(reward.getStructurePurchaseRights().toArray()));
                return "Updating structures...";
            });
            transactions.add(structuresTransaction);
        }
        if (reward.getMusicTracks() != null) {
            ApiFuture<String> musicTracksTransaction = db.runTransaction(transaction -> {
                transaction.update(snapshot.getReference(), "assets.musicTracks", FieldValue.arrayUnion(reward.getMusicTracks().toArray()));
                return "Updating music tracks...";
            });
            transactions.add(musicTracksTransaction);
        }
        if (reward.getLighting() != null) {
            ApiFuture<String> lightingTransaction = db.runTransaction(transaction -> {
                transaction.update(snapshot.getReference(), "assets.lighting", FieldValue.arrayUnion(reward.getLighting().toArray()));
                return "Updating lighting...";
            });
            transactions.add(lightingTransaction);
        }
        if (reward.getDurables() != null) {
            ApiFuture<String> durablesTransaction = db.runTransaction(transaction -> {
                transaction.update(snapshot.getReference(), "assets.durables", FieldValue.arrayUnion(reward.getDurables().toArray()));
                return "Updating durables...";
            });
            transactions.add(durablesTransaction);
        }
        if (reward.getTubes() != null) {
            ApiFuture<String> tubesTransaction = db.runTransaction(transaction -> {
                transaction.update(snapshot.getReference(), "assets.tubes", FieldValue.arrayUnion(reward.getTubes().toArray()));
                return "Updating tubes...";
            });
            transactions.add(tubesTransaction);
        }
        if (reward.getSavedOutfitSlots() > 0) {
            ApiFuture<String> savedOutfitSlotsTransaction = db.runTransaction(transaction -> {
                Long oldSavedOutfitSlots = snapshot.getLong("assets.savedOutfitSlots");
                if (oldSavedOutfitSlots != null) {
                    transaction.update(snapshot.getReference(), "assets.savedOutfitSlots", oldSavedOutfitSlots + reward.getSavedOutfitSlots());
                    return "assets.savedOutfitSlots had a value of " + oldSavedOutfitSlots + ", updated with a new value of " + oldSavedOutfitSlots + " + " + reward.getSavedOutfitSlots() + "!";
                } else {
                    return "oldSavedOutfitSlots == null (this should not happen) -- could not save new saved outfit slots amount (" + reward.getSavedOutfitSlots() + ") for user " + snapshot.getId() + "!";
                }
            });
            transactions.add(savedOutfitSlotsTransaction);
        }
        if (reward.getIglooSlots() > 0) {
            ApiFuture<String> iglooSlotsTransaction = db.runTransaction(transaction -> {
                Long oldIglooSlots = snapshot.getLong("assets.iglooSlots");
                if (oldIglooSlots != null) {
                    transaction.update(snapshot.getReference(), "assets.iglooSlots", oldIglooSlots + reward.getIglooSlots());
                    return "assets.iglooSlots had a value of " + oldIglooSlots + ", updated with a new value of " + oldIglooSlots + " + " + reward.getIglooSlots() + "!";
                } else {
                    return "oldIglooSlots == null (this should not happen) -- could not save new igloo slots amount (" + reward.getIglooSlots() + ") for user " + snapshot.getId() + "!";
                }
            });
            transactions.add(iglooSlotsTransaction);
        }
        if (reward.getConsumables() != null) {
            for (String key : reward.getConsumables().keySet()) {
                Integer newConsumableAmount = reward.getConsumables().get(key);
                ApiFuture<String> consumableTransaction = db.runTransaction(transaction -> {
                    Long oldConsumableAmount = snapshot.getLong("consumableInventory.inventoryMap." + key + ".itemCount");
                    if (oldConsumableAmount != null) {
                        transaction.update(snapshot.getReference(), "consumableInventory.inventoryMap." + key + ".itemCount", oldConsumableAmount + newConsumableAmount);
                        return "consumableInventory.inventoryMap." + key + ".itemCount had a value of " + oldConsumableAmount + ", updated with a new value of " + oldConsumableAmount + " + " + newConsumableAmount + "!";
                    } else {
                        transaction.update(snapshot.getReference(), "consumableInventory.inventoryMap." + key + ".itemCount", newConsumableAmount);
                        transaction.update(snapshot.getReference(), "consumableInventory.inventoryMap." + key + ".partialCount", 0);
                        transaction.update(snapshot.getReference(), "consumableInventory.inventoryMap." + key + ".lastPurchaseTimestamp", System.currentTimeMillis());
                        return "consumableInventory.inventoryMap." + key + " was null, setting new values for " + key;
                    }
                });
                transactions.add(consumableTransaction);
            }
        }
        if (reward.getPartySupplies() != null) {
            ApiFuture<String> partySuppliesTransaction = db.runTransaction(transaction -> {
                transaction.update(snapshot.getReference(), "assets.partySupplies", FieldValue.arrayUnion(reward.getPartySupplies().toArray()));
                return "Updating party supplies...";
            });
            transactions.add(partySuppliesTransaction);
        }

        return transactions;
    }
}
