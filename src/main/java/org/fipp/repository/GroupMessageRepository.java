package org.fipp.repository;

import org.fipp.database.DatabaseConnection;
import org.fipp.model.GroupMessage;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class GroupMessageRepository {
    public static final String STATUS_QUEUED = "queued";

    public static GroupMessage save(int groupId, int senderId, int receiverId, String content) {
        GroupMessage message = null;
        String sql = """
            INSERT INTO group_messages (group_id, sender_id, receiver_id, content, status)
            VALUES (?, ?, ?, ?, 'queued');
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            statement.setInt(1, groupId);
            statement.setInt(2, senderId);
            statement.setInt(3, receiverId);
            statement.setString(4, content);
            statement.executeUpdate();

            int messageId = findInsertedId(connection, statement);
            message = findById(messageId);
        } catch (Exception e) {
            System.out.println("Erro ao salvar mensagem de grupo: " + e.getMessage());
        }

        return message;
    }

    public static List<GroupMessage> findQueuedForReceiver(int receiverId) {
        List<GroupMessage> messages = new ArrayList<>();
        String sql = baseSelect() + """
            WHERE gm.receiver_id = ? AND gm.status = 'queued'
            ORDER BY gm.created_at, gm.id;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, receiverId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    messages.add(mapGroupMessage(resultSet));
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao buscar mensagens pendentes de grupo: " + e.getMessage());
        }

        return messages;
    }

    public static void markDelivered(int messageId) {
        String sql = """
            UPDATE group_messages
            SET status = 'delivered',
                delivered_at = CURRENT_TIMESTAMP
            WHERE id = ?;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, messageId);
            statement.executeUpdate();
        } catch (Exception e) {
            System.out.println("Erro ao marcar mensagem de grupo como entregue: " + e.getMessage());
        }
    }

    private static int findInsertedId(Connection connection, PreparedStatement statement) throws Exception {
        try (ResultSet generatedKeys = statement.getGeneratedKeys()) {
            if (generatedKeys.next()) {
                return generatedKeys.getInt(1);
            }
        }

        try (Statement idStatement = connection.createStatement();
             ResultSet resultSet = idStatement.executeQuery("SELECT last_insert_rowid();")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private static GroupMessage findById(int messageId) {
        GroupMessage message = null;
        String sql = baseSelect() + "WHERE gm.id = ?;";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, messageId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    message = mapGroupMessage(resultSet);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao buscar mensagem de grupo: " + e.getMessage());
        }

        return message;
    }

    private static String baseSelect() {
        return """
            SELECT gm.id,
                   gm.group_id,
                   g.name AS group_name,
                   gm.sender_id,
                   gm.receiver_id,
                   sender.username AS sender_username,
                   receiver.username AS receiver_username,
                   gm.content,
                   gm.created_at
            FROM group_messages gm
            JOIN groups g ON g.id = gm.group_id
            JOIN users sender ON sender.id = gm.sender_id
            JOIN users receiver ON receiver.id = gm.receiver_id
        """;
    }

    private static GroupMessage mapGroupMessage(ResultSet resultSet) throws Exception {
        return new GroupMessage(
                resultSet.getInt("id"),
                resultSet.getInt("group_id"),
                resultSet.getString("group_name"),
                resultSet.getInt("sender_id"),
                resultSet.getInt("receiver_id"),
                resultSet.getString("sender_username"),
                resultSet.getString("receiver_username"),
                resultSet.getString("content"),
                resultSet.getString("created_at")
        );
    }
}
