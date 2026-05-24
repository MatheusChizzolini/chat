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
        createGroupsTable();
        createGroupMembersTable();
        createGroupInvitationsTable();
        createGroupJoinRequestsTable();
        createGroupJoinVotesTable();
        createGroupMessagesTable();
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
                full_name TEXT NOT NULL,
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

    private static void createGroupsTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS groups (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL COLLATE NOCASE UNIQUE,
                created_by_id INTEGER NOT NULL,
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY (created_by_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            System.out.println("Tabela de grupos criada/checada com sucesso.");
        } catch (Exception e) {
            System.out.println("Erro ao criar tabela de grupos: " + e.getMessage());
        }
    }

    private static void createGroupMembersTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS group_members (
                group_id INTEGER NOT NULL,
                user_id INTEGER NOT NULL,
                joined_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                PRIMARY KEY (group_id, user_id),
                FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
                FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
            );
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            System.out.println("Tabela de membros dos grupos criada/checada com sucesso.");
        } catch (Exception e) {
            System.out.println("Erro ao criar tabela de membros dos grupos: " + e.getMessage());
        }
    }

    private static void createGroupInvitationsTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS group_invitations (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_id INTEGER NOT NULL,
                invited_by_id INTEGER NOT NULL,
                receiver_id INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                responded_at TEXT,
                UNIQUE (group_id, receiver_id),
                FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
                FOREIGN KEY (invited_by_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
                CHECK (status IN ('pending', 'accepted', 'rejected'))
            );
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            System.out.println("Tabela de convites dos grupos criada/checada com sucesso.");
        } catch (Exception e) {
            System.out.println("Erro ao criar tabela de convites dos grupos: " + e.getMessage());
        }
    }

    private static void createGroupJoinRequestsTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS group_join_requests (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_id INTEGER NOT NULL,
                requester_id INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                resolved_at TEXT,
                notified_at TEXT,
                UNIQUE (group_id, requester_id),
                FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
                FOREIGN KEY (requester_id) REFERENCES users(id) ON DELETE CASCADE,
                CHECK (status IN ('pending', 'accepted', 'rejected'))
            );
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            System.out.println("Tabela de solicitacoes de entrada criada/checada com sucesso.");
        } catch (Exception e) {
            System.out.println("Erro ao criar tabela de solicitacoes de entrada: " + e.getMessage());
        }
    }

    private static void createGroupJoinVotesTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS group_join_votes (
                request_id INTEGER NOT NULL,
                voter_id INTEGER NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending',
                responded_at TEXT,
                PRIMARY KEY (request_id, voter_id),
                FOREIGN KEY (request_id) REFERENCES group_join_requests(id) ON DELETE CASCADE,
                FOREIGN KEY (voter_id) REFERENCES users(id) ON DELETE CASCADE,
                CHECK (status IN ('pending', 'accepted', 'rejected'))
            );
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            System.out.println("Tabela de votos para entrada criada/checada com sucesso.");
        } catch (Exception e) {
            System.out.println("Erro ao criar tabela de votos para entrada: " + e.getMessage());
        }
    }

    private static void createGroupMessagesTable() {
        String sql = """
            CREATE TABLE IF NOT EXISTS group_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                group_id INTEGER NOT NULL,
                sender_id INTEGER NOT NULL,
                receiver_id INTEGER NOT NULL,
                content TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'queued',
                created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                delivered_at TEXT,
                FOREIGN KEY (group_id) REFERENCES groups(id) ON DELETE CASCADE,
                FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
                FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE,
                CHECK (status IN ('queued', 'delivered'))
            );
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
            System.out.println("Tabela de mensagens dos grupos criada/checada com sucesso.");
        } catch (Exception e) {
            System.out.println("Erro ao criar tabela de mensagens dos grupos: " + e.getMessage());
        }
    }
}
