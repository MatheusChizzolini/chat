package org.fipp.database;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;

public class DatabaseInitializer {

    public static void initialize() {
        createDatabaseDirectory();
        createUsersTable();
        createAuthorizedConnectionsTable();
        createDirectMessagesTable();
    }

    private static void createDatabaseDirectory() {
        try {
            Files.createDirectories(Path.of("database"));
        } catch (Exception e) {
            System.out.println("Erro ao criar diretorio do banco de dados: " + e.getMessage());
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

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            System.out.println("Tabela de usuarios criada/checada com sucesso.");
        } catch (Exception e) {
            System.out.println("Erro ao criar tabela de usuarios: " + e.getMessage());
        }
    }

    private static void createAuthorizedConnectionsTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS authorized_connections (
                user_one_id INTEGER NOT NULL,
                user_two_id INTEGER NOT NULL,
                requested_by_id INTEGER NOT NULL,
                requested_to_id INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (user_one_id, user_two_id),
                FOREIGN KEY (user_one_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (user_two_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (requested_by_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (requested_to_id) REFERENCES users(id) ON DELETE CASCADE,
                CHECK (status IN ('pending', 'accepted', 'rejected')),
                CHECK (user_one_id < user_two_id)
            );
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            System.out.println("Tabela de conexoes autorizadas criada/checada com sucesso.");
        } catch (Exception e) {
            System.out.println("Erro ao criar tabela de conexoes autorizadas: " + e.getMessage());
        }
    }

    private static void createDirectMessagesTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS direct_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                sender_id INTEGER NOT NULL,
                receiver_id INTEGER NOT NULL,
                content TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'queued',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                delivered_at TEXT,
                FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
                CHECK (status IN ('pending_authorization', 'queued', 'delivered', 'rejected'))
            );
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            System.out.println("Tabela de mensagens privadas criada/checada com sucesso.");
        } catch (Exception e) {
            System.out.println("Erro ao criar tabela de mensagens privadas: " + e.getMessage());
        }
    }
}
