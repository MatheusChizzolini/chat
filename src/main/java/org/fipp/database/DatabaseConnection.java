package org.fipp.database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseConnection {
    private static final String URL = "jdbc:sqlite:database/chat.db";

    public static Connection getConnection() throws SQLException {
        Connection connection = DriverManager.getConnection(URL);
        connection.createStatement().execute("PRAGMA foreign_keys = ON;");
        return connection;
    }
}
