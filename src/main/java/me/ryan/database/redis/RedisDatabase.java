package me.legit.database.redis;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import me.legit.APICore;
import me.legit.database.Database;

import java.util.HashMap;
import java.util.Map;

public class RedisDatabase implements Database {

    private RedisClient client;
    private Map<Integer, StatefulRedisConnection<String, String>> connection;

    @Override
    public void setup() {
        APICore.getLogger().info("Initializing Redis database...");

        client = RedisClient.create();

        connection = new HashMap<>();

        // API CACHE (SESSION KEYS)
        connection.put(1, client.connect(RedisURI.builder()
                .withHost("localhost")
                .withPort(6379)
                .withPassword("")
                .withDatabase(0)
                .build()
        ));

        // GAME CACHE (JOIN ROOM DATA, QUEST DATA)
        connection.put(2, client.connect(RedisURI.builder()
                .withHost("localhost")
                .withPort(6379)
                .withPassword("")
                .withDatabase(1)
                .build()
        ));

        /* TODO RATE LIMIT
        connection.put(3, client.connect(RedisURI.builder().withDatabase(3).build())); */
    }

    @Override
    public void disable() {
        APICore.getLogger().info("Disabling Redis database...");

        for (StatefulRedisConnection<String, String> connections : connection.values()) {
            connections.close();
        }
        client.shutdown();
    }

    public RedisClient client() {
        return client;
    }

    public Map<Integer, StatefulRedisConnection<String, String>> connection() {
        return connection;
    }

}
