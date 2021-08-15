package me.legit.database;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import me.legit.APICore;
import me.legit.database.firebase.FirebaseDatabase;
import me.legit.database.redis.RedisDatabase;
import me.legit.utils.Utilities;

import java.util.Map;

@SuppressWarnings("unchecked")
public class DatabaseManager {

    private Map<Class<? extends Database>, Database> databaseMap = Maps.newHashMap();

    public DatabaseManager() {
        APICore.getLogger().info("Initializing databases...");

        databaseMap.put(FirebaseDatabase.class, new FirebaseDatabase());
        databaseMap.put(RedisDatabase.class, new RedisDatabase());

        databaseMap.values().forEach(Database::setup);
    }

    private <T extends Database> T databaseBy(Class<T> clazz) {
        return (T) databaseMap.get(clazz);
    }

    public FirebaseDatabase firebase() {
        return databaseBy(FirebaseDatabase.class);
    }

    public RedisDatabase redis() {
        return databaseBy(RedisDatabase.class);
    }

    public void disable() {
        Preconditions.checkArgument(Utilities.accessedFrom(APICore.class), "Can't disable Database unless it's from APICore.class");

        APICore.getLogger().info("Disabling databases...");

        databaseMap.values().forEach(Database::disable);
    }
}
