package me.legit.api.auth.guest;

import io.javalin.http.Context;
import io.javalin.http.Handler;
import org.jetbrains.annotations.NotNull;

public class APIKey implements Handler {

    @Override
    public void handle(@NotNull Context ctx) throws Exception {
        //TODO Generate API key per IP address -- store in Redis + expire after period of time + add rate limiting

        ctx.header("api-key", "aqnA9p/GHYcMJnHGPld3WUYS6xVbbkaizQIgUOXxUTV1ty0kGcIvQkRHMBiO89jAF+h6Bp6jWCj0KDdy71nsYSAhDpDV/5Y9AYDhQ/2efdB5XCWilW5q2g==");
        ctx.contentType("application/json;charset=utf-8").result("{\"data\":null,\"error\":null}");
    }
}