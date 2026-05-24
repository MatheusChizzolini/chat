package org.fipp.repository;

import org.fipp.database.DatabaseConnection;
import org.fipp.model.DirectMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DirectMessageRepository {
    public static final String STATUS_PENDING_AUTHORIZATION = "pending_authorization";
    public static final String STATUS_QUEUED = "queued";
    public static final String STATUS_DELIVERED = "delivered";
    public static final String STATUS_REJECTED = "rejected";

    public static DirectMessage save(int senderId, int receiverId, String content, String status) {
        DirectMessage message = null;
        String sql = """
            INSERT INTO direct_messages (sender_id, receiver_id, content, status, delivered_at)
            VALUES (?, ?, ?, ?, CASE WHEN ? = 'delivered' THEN CURRENT_TIMESTAMP ELSE NULL END);
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, senderId);
            statement.setInt(2, receiverId);
            statement.setString(3, content);
            statement.setString(4, status);
            statement.setString(5, status);
            statement.executeUpdate();

            try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    message = findById(generatedKeys.getInt(1));
                }
            }

            if (message == null) {
                try (Statement keyStatement = connection.createStatement();
                     ResultSet resultSet = keyStatement.executeQuery("SELECT last_insert_rowid();")) {
                    if (resultSet.next()) {
                        message = findById(resultSet.getInt(1));
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao salvar mensagem privada: " + e.getMessage());
        }

        return message;
    }

    public static List<DirectMessage> findQueuedMessagesForReceiver(int receiverId) {
        String sql = baseSelect() + """
            WHERE dm.receiver_id = ? AND dm.status = 'queued'
            ORDER BY dm.created_at;
        """;

        return findMessagesByReceiverAndStatus(receiverId, sql);
    }

    public static List<DirectMessage> findPendingAuthorizationMessages(int senderId, int receiverId) {
        List<DirectMessage> messages = new ArrayList<>();
        String sql = baseSelect() + """
            WHERE dm.sender_id = ?
              AND dm.receiver_id = ?
              AND dm.status = 'pending_authorization'
            ORDER BY dm.created_at;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, senderId);
            statement.setInt(2, receiverId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    messages.add(mapDirectMessage(resultSet));
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao buscar mensagens privadas pendentes de autorizacao: " + e.getMessage());
        }

        return messages;
    }

    public static void markDelivered(int messageId) {
        String sql = """
            UPDATE direct_messages
            SET status = 'delivered',
                delivered_at = CURRENT_TIMESTAMP
            WHERE id = ?;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, messageId);
            statement.executeUpdate();
        } catch (Exception e) {
            System.out.println("Erro ao marcar mensagem como entregue: " + e.getMessage());
        }
    }

    public static int rejectPendingAuthorizationMessages(int senderId, int receiverId) {
        int rejectedMessages = 0;
        String sql = """
            UPDATE direct_messages
            SET status = 'rejected'
            WHERE sender_id = ?
              AND receiver_id = ?
              AND status = 'pending_authorization';
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, senderId);
            statement.setInt(2, receiverId);
            rejectedMessages = statement.executeUpdate();
        } catch (Exception e) {
            System.out.println("Erro ao rejeitar mensagens privadas pendentes: " + e.getMessage());
        }

        return rejectedMessages;
    }

    private static DirectMessage findById(int messageId) {
        DirectMessage message = null;
        String sql = baseSelect() + "WHERE dm.id = ?;";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, messageId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    message = mapDirectMessage(resultSet);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao buscar mensagem privada: " + e.getMessage());
        }

        return message;
    }

    private static List<DirectMessage> findMessagesByReceiverAndStatus(int receiverId, String sql) {
        List<DirectMessage> messages = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, receiverId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    messages.add(mapDirectMessage(resultSet));
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao buscar mensagens privadas pendentes: " + e.getMessage());
        }

        return messages;
    }

    private static String baseSelect() {
        return """
            SELECT dm.id,
                   dm.sender_id,
                   dm.receiver_id,
                   sender.username AS sender_username,
                   receiver.username AS receiver_username,
                   dm.content,
                   dm.created_at
            FROM direct_messages dm
            JOIN users sender ON sender.id = dm.sender_id
            JOIN users receiver ON receiver.id = dm.receiver_id
        """;
    }

    private static DirectMessage mapDirectMessage(ResultSet resultSet) throws Exception {
        return new DirectMessage(
                resultSet.getInt("id"),
                resultSet.getInt("sender_id"),
                resultSet.getInt("receiver_id"),
                resultSet.getString("sender_username"),
                resultSet.getString("receiver_username"),
                resultSet.getString("content"),
                resultSet.getString("created_at")
        );
    }
}
