package org.fipp.repository;

import org.fipp.database.DatabaseConnection;
import org.fipp.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class AuthorizedConnectionRepository {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";

    public static String findStatusBetween(int firstUserId, int secondUserId) {
        UserPair pair = UserPair.from(firstUserId, secondUserId);
        String status = null;
        String sql = """
            SELECT status
            FROM authorized_connections
            WHERE user_one_id = ? AND user_two_id = ?;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, pair.userOneId());
            statement.setInt(2, pair.userTwoId());

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    status = resultSet.getString("status");
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao buscar autorizacao de conversa: " + e.getMessage());
        }

        return status;
    }

    public static void requestConnection(int requesterId, int receiverId) {
        if (existsBetween(requesterId, receiverId)) {
            updateRequest(requesterId, receiverId, STATUS_PENDING);
        } else {
            insertRequest(requesterId, receiverId);
        }
    }

    public static boolean accept(int requesterId, int receiverId) {
        return updateRequest(requesterId, receiverId, STATUS_ACCEPTED);
    }

    public static boolean reject(int requesterId, int receiverId) {
        return updateRequest(requesterId, receiverId, STATUS_REJECTED);
    }

    public static boolean hasPendingRequest(int requesterId, int receiverId) {
        boolean hasPendingRequest = false;
        String sql = """
            SELECT 1
            FROM authorized_connections
            WHERE requested_by_id = ?
              AND requested_to_id = ?
              AND status = 'pending'
            LIMIT 1;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requesterId);
            statement.setInt(2, receiverId);

            try (ResultSet resultSet = statement.executeQuery()) {
                hasPendingRequest = resultSet.next();
            }
        } catch (Exception e) {
            System.out.println("Erro ao verificar solicitacao privada pendente: " + e.getMessage());
        }

        return hasPendingRequest;
    }

    public static List<User> findPendingRequestersForReceiver(int receiverId) {
        List<User> requesters = new ArrayList<>();
        String sql = """
            SELECT u.id, u.full_name, u.username, u.email, u.status
            FROM authorized_connections ac
            JOIN users u ON u.id = ac.requested_by_id
            WHERE ac.requested_to_id = ? AND ac.status = 'pending'
            ORDER BY ac.created_at;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, receiverId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    requesters.add(new User(
                            resultSet.getInt("id"),
                            resultSet.getString("full_name"),
                            resultSet.getString("username"),
                            resultSet.getString("email"),
                            resultSet.getString("status")
                    ));
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao listar solicitacoes privadas pendentes: " + e.getMessage());
        }

        return requesters;
    }

    private static boolean existsBetween(int firstUserId, int secondUserId) {
        return findStatusBetween(firstUserId, secondUserId) != null;
    }

    private static void insertRequest(int requesterId, int receiverId) {
        UserPair pair = UserPair.from(requesterId, receiverId);
        String sql = """
            INSERT INTO authorized_connections (
                user_one_id,
                user_two_id,
                requested_by_id,
                requested_to_id,
                status
            )
            VALUES (?, ?, ?, ?, 'pending');
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, pair.userOneId());
            statement.setInt(2, pair.userTwoId());
            statement.setInt(3, requesterId);
            statement.setInt(4, receiverId);
            statement.executeUpdate();
        } catch (Exception e) {
            System.out.println("Erro ao solicitar conversa privada: " + e.getMessage());
        }
    }

    private static boolean updateRequest(int requesterId, int receiverId, String status) {
        UserPair pair = UserPair.from(requesterId, receiverId);
        boolean updated = false;
        String sql = """
            UPDATE authorized_connections
            SET requested_by_id = ?,
                requested_to_id = ?,
                status = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE user_one_id = ? AND user_two_id = ?;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requesterId);
            statement.setInt(2, receiverId);
            statement.setString(3, status);
            statement.setInt(4, pair.userOneId());
            statement.setInt(5, pair.userTwoId());
            updated = statement.executeUpdate() > 0;
        } catch (Exception e) {
            System.out.println("Erro ao atualizar autorizacao de conversa: " + e.getMessage());
        }

        return updated;
    }

    private record UserPair(int userOneId, int userTwoId) {
        private static UserPair from(int firstUserId, int secondUserId) {
            if (firstUserId < secondUserId) {
                return new UserPair(firstUserId, secondUserId);
            }

            return new UserPair(secondUserId, firstUserId);
        }
    }
}
