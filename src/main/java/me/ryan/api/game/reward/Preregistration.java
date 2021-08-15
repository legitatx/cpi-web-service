package me.legit.api.game.reward;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

public class Preregistration implements Handler {

    public Preregistration() {
        //TODO
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");
        ctx.result("{\"wsEvents\":[],\"reward\":{\"coins\":0,\"currency\":null,\"mascotXP\":null,\"collectibleCurrency\":null,\"colourPacks\":null,\"decals\":null,\"fabrics\":null,\"emotePacks\":null,\"sizzleClips\":null,\"equipmentTemplates\":null,\"equipmentInstances\":[],\"lots\":null,\"decorationInstances\":null,\"structureInstances\":null,\"decorationPurchaseRights\":null,\"structurePurchaseRights\":null,\"musicTracks\":null,\"lighting\":null,\"durables\":null,\"tubes\":null,\"savedOutfitSlots\":0,\"iglooSlots\":0,\"consumables\":null,\"partySupplies\":null}}");
    }
}
