package org.fipp.repository;

import org.fipp.database.DatabaseConnection;
import org.fipp.model.GroupInvitation;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class GroupInvitationRepository {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";

    public static GroupInvitation findByGroupAndReceiver(int groupId, int receiverId) {
        GroupInvitation invitation = null;
        String sql = baseSelect() + "WHERE gi.group_id = ? AND gi.receiver_id = ?;";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, groupId);
            statement.setInt(2, receiverId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    invitation = mapInvitation(resultSet);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao buscar convite de grupo: " + e.getMessage());
        }

        return invitation;
    }

    public static GroupInvitation invite(int groupId, int inviterId, int receiverId) {
        String sql = """
            INSERT INTO group_invitations (group_id, invited_by_id, receiver_id, status)
            VALUES (?, ?, ?, 'pending')
            ON CONFLICT(group_id, receiver_id) DO UPDATE SET
                invited_by_id = excluded.invited_by_id,
                status = 'pending',
                created_at = CURRENT_TIMESTAMP,
                responded_at = NULL;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, groupId);
            statement.setInt(2, inviterId);
            statement.setInt(3, receiverId);
            statement.executeUpdate();
            return findByGroupAndReceiver(groupId, receiverId);
        } catch (Exception e) {
            System.out.println("Erro ao salvar convite de grupo: " + e.getMessage());
            return null;
        }
    }

    public static List<GroupInvitation> findPendingForReceiver(int receiverId) {
        List<GroupInvitation> invitations = new ArrayList<>();
        String sql = baseSelect() + """
            WHERE gi.receiver_id = ? AND gi.status = 'pending'
            ORDER BY gi.created_at;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, receiverId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    invitations.add(mapInvitation(resultSet));
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao listar convites pendentes de grupo: " + e.getMessage());
        }

        return invitations;
    }

    public static boolean accept(GroupInvitation invitation) {
        String updateInvitationSql = """
            UPDATE group_invitations
            SET status = 'accepted',
                responded_at = CURRENT_TIMESTAMP
            WHERE id = ? AND receiver_id = ? AND status = 'pending';
        """;
        String addMemberSql = "INSERT OR IGNORE INTO group_members (group_id, user_id) VALUES (?, ?);";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement updateStatement = connection.prepareStatement(updateInvitationSql);
                 PreparedStatement memberStatement = connection.prepareStatement(addMemberSql)) {
                updateStatement.setInt(1, invitation.id());
                updateStatement.setInt(2, invitation.receiverId());

                if (updateStatement.executeUpdate() == 0) {
                    connection.rollback();
                    return false;
                }

                memberStatement.setInt(1, invitation.groupId());
                memberStatement.setInt(2, invitation.receiverId());
                memberStatement.executeUpdate();
                connection.commit();
                return true;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            System.out.println("Erro ao aceitar convite de grupo: " + e.getMessage());
            return false;
        }
    }

    public static boolean reject(GroupInvitation invitation) {
        String sql = """
            UPDATE group_invitations
            SET status = 'rejected',
                responded_at = CURRENT_TIMESTAMP
            WHERE id = ? AND receiver_id = ? AND status = 'pending';
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, invitation.id());
            statement.setInt(2, invitation.receiverId());
            return statement.executeUpdate() > 0;
        } catch (Exception e) {
            System.out.println("Erro ao recusar convite de grupo: " + e.getMessage());
            return false;
        }
    }

    private static String baseSelect() {
        return """
            SELECT gi.id,
                   gi.group_id,
                   g.name AS group_name,
                   gi.invited_by_id,
                   inviter.username AS inviter_username,
                   gi.receiver_id,
                   gi.status
            FROM group_invitations gi
            JOIN groups g ON g.id = gi.group_id
            JOIN users inviter ON inviter.id = gi.invited_by_id
        """;
    }

    private static GroupInvitation mapInvitation(ResultSet resultSet) throws Exception {
        return new GroupInvitation(
                resultSet.getInt("id"),
                resultSet.getInt("group_id"),
                resultSet.getString("group_name"),
                resultSet.getInt("invited_by_id"),
                resultSet.getString("inviter_username"),
                resultSet.getInt("receiver_id"),
                resultSet.getString("status")
        );
    }
}
