package org.fipp.database;

import java.sql.Connection;
import java.sql.Statement;
import java.nio.file.Files;
import java.nio.file.Path;

public class DatabaseInitializer {

    public static void initialize() {
        createDatabaseDirectory();
        createUsersTable();
    }

    private static void createDatabaseDirectory() {
        try {
            Files.createDirectories(Path.of("database"));
        } catch (Exception e) {
            System.out.println("Erro ao criar diretório do banco de dados: " + e.getMessage());
        }
    }

    private static void createUsersTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS users (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                full_name TEXT NOT NULL UNIQUE,
                username TEXT NOT NULL UNIQUE,
                email TEXT NOT NULL UNIQUE,
                password TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'offline',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
            );
        """;

        try {
            Connection connection = DatabaseConnection.getConnection();
            Statement statement = connection.createStatement();
            statement.execute(sql);
            System.out.println("Tabela de usuários criada/checada com sucesso.");
        } catch (Exception e) {
            System.out.println("Erro ao criar tabela de usuários: " + e.getMessage());
        }
    }
}
