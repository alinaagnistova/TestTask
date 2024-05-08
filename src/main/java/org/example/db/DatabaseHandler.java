package org.example.db;

import java.util.Objects;

/**
 * Singleton pattern realization
 */
public class DatabaseHandler {
    private static DatabaseManager databaseManager;

    static {
        databaseManager = new DatabaseManager();
    }

    public static DatabaseManager getDatabaseManager() {
        if (Objects.isNull(databaseManager)) databaseManager = new DatabaseManager();
        return databaseManager;
    }
}

