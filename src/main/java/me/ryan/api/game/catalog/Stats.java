package me.legit.api.game.catalog;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

public class Stats implements Handler {

    public Stats() {
        //TODO
    }

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        ctx.contentType("application/json");
        ctx.result("{\"themes\":[]}");
    }
}
