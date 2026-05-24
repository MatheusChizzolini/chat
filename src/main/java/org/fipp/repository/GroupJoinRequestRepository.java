package org.fipp.repository;

import org.fipp.database.DatabaseConnection;
import org.fipp.model.Group;
import org.fipp.model.GroupJoinRequest;
import org.fipp.model.GroupJoinVote;
import org.fipp.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class GroupJoinRequestRepository {
    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_ACCEPTED = "accepted";
    public static final String STATUS_REJECTED = "rejected";

    public record VoteResult(boolean processed, GroupJoinRequest request) {
    }

    public static GroupJoinRequest findByGroupAndRequester(int groupId, int requesterId) {
        GroupJoinRequest request = null;
        String sql = requestSelect() + "WHERE r.group_id = ? AND r.requester_id = ?;";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, groupId);
            statement.setInt(2, requesterId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    request = mapRequest(resultSet);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao buscar solicitacao de entrada: " + e.getMessage());
        }

        return request;
    }

    public static GroupJoinRequest create(Group group, User requester, List<User> voters) {
        String saveRequestSql = """
            INSERT INTO group_join_requests (group_id, requester_id, status)
            VALUES (?, ?, 'pending')
            ON CONFLICT(group_id, requester_id) DO UPDATE SET
                status = 'pending',
                created_at = CURRENT_TIMESTAMP,
                resolved_at = NULL,
                notified_at = NULL;
        """;
        String deleteVotesSql = "DELETE FROM group_join_votes WHERE request_id = ?;";
        String insertVoteSql = """
            INSERT INTO group_join_votes (request_id, voter_id, status)
            VALUES (?, ?, 'pending');
        """;

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement requestStatement = connection.prepareStatement(saveRequestSql)) {
                requestStatement.setInt(1, group.id());
                requestStatement.setInt(2, requester.id());
                requestStatement.executeUpdate();

                GroupJoinRequest request = findByGroupAndRequester(connection, group.id(), requester.id());
                if (request == null) {
                    connection.rollback();
                    return null;
                }

                try (PreparedStatement deleteStatement = connection.prepareStatement(deleteVotesSql);
                     PreparedStatement insertStatement = connection.prepareStatement(insertVoteSql)) {
                    deleteStatement.setInt(1, request.id());
                    deleteStatement.executeUpdate();

                    for (User voter : voters) {
                        insertStatement.setInt(1, request.id());
                        insertStatement.setInt(2, voter.id());
                        insertStatement.addBatch();
                    }
                    insertStatement.executeBatch();
                }

                connection.commit();
                return request;
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            System.out.println("Erro ao criar solicitacao de entrada: " + e.getMessage());
            return null;
        }
    }

    public static List<GroupJoinVote> findPendingVotesForVoter(int voterId) {
        List<GroupJoinVote> votes = new ArrayList<>();
        String sql = voteSelect() + """
            WHERE v.voter_id = ? AND v.status = 'pending' AND r.status = 'pending'
            ORDER BY r.created_at;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, voterId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    votes.add(mapVote(resultSet));
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao listar votos pendentes de entrada: " + e.getMessage());
        }

        return votes;
    }

    public static List<GroupJoinVote> findVotesForRequest(int requestId) {
        List<GroupJoinVote> votes = new ArrayList<>();
        String sql = voteSelect() + "WHERE v.request_id = ?;";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requestId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    votes.add(mapVote(resultSet));
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao listar votos de entrada: " + e.getMessage());
        }

        return votes;
    }

    public static VoteResult respond(GroupJoinVote vote, boolean accepted) {
        String responseStatus = accepted ? STATUS_ACCEPTED : STATUS_REJECTED;
        String updateVoteSql = """
            UPDATE group_join_votes
            SET status = ?, responded_at = CURRENT_TIMESTAMP
            WHERE request_id = ? AND voter_id = ? AND status = 'pending'
              AND EXISTS (
                  SELECT 1 FROM group_join_requests r
                  WHERE r.id = group_join_votes.request_id AND r.status = 'pending'
              );
        """;

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement statement = connection.prepareStatement(updateVoteSql)) {
                statement.setString(1, responseStatus);
                statement.setInt(2, vote.requestId());
                statement.setInt(3, vote.voterId());

                if (statement.executeUpdate() == 0) {
                    connection.rollback();
                    return new VoteResult(false, null);
                }

                if (!accepted) {
                    rejectRequest(connection, vote.requestId());
                } else if (!hasPendingVotes(connection, vote.requestId())) {
                    acceptRequest(connection, vote.requestId());
                }

                GroupJoinRequest request = findById(connection, vote.requestId());
                connection.commit();
                return new VoteResult(true, request);
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            System.out.println("Erro ao registrar voto de entrada: " + e.getMessage());
            return new VoteResult(false, null);
        }
    }

    public static List<GroupJoinRequest> findUnnotifiedResolvedForRequester(int requesterId) {
        List<GroupJoinRequest> requests = new ArrayList<>();
        String sql = requestSelect() + """
            WHERE r.requester_id = ? AND r.status <> 'pending' AND r.notified_at IS NULL
            ORDER BY r.resolved_at;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requesterId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    requests.add(mapRequest(resultSet));
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao listar resultados de entrada: " + e.getMessage());
        }

        return requests;
    }

    public static void markNotified(int requestId) {
        String sql = """
            UPDATE group_join_requests
            SET notified_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status <> 'pending';
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requestId);
            statement.executeUpdate();
        } catch (Exception e) {
            System.out.println("Erro ao confirmar aviso de entrada: " + e.getMessage());
        }
    }

    private static void rejectRequest(Connection connection, int requestId) throws Exception {
        String rejectRequestSql = """
            UPDATE group_join_requests
            SET status = 'rejected', resolved_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'pending';
        """;
        String rejectPendingVotesSql = """
            UPDATE group_join_votes
            SET status = 'rejected', responded_at = CURRENT_TIMESTAMP
            WHERE request_id = ? AND status = 'pending';
        """;

        try (PreparedStatement requestStatement = connection.prepareStatement(rejectRequestSql);
             PreparedStatement votesStatement = connection.prepareStatement(rejectPendingVotesSql)) {
            requestStatement.setInt(1, requestId);
            requestStatement.executeUpdate();
            votesStatement.setInt(1, requestId);
            votesStatement.executeUpdate();
        }
    }

    private static void acceptRequest(Connection connection, int requestId) throws Exception {
        String acceptRequestSql = """
            UPDATE group_join_requests
            SET status = 'accepted', resolved_at = CURRENT_TIMESTAMP
            WHERE id = ? AND status = 'pending';
        """;
        String addMemberSql = """
            INSERT OR IGNORE INTO group_members (group_id, user_id)
            SELECT group_id, requester_id FROM group_join_requests WHERE id = ?;
        """;

        try (PreparedStatement requestStatement = connection.prepareStatement(acceptRequestSql);
             PreparedStatement memberStatement = connection.prepareStatement(addMemberSql)) {
            requestStatement.setInt(1, requestId);
            requestStatement.executeUpdate();
            memberStatement.setInt(1, requestId);
            memberStatement.executeUpdate();
        }
    }

    private static boolean hasPendingVotes(Connection connection, int requestId) throws Exception {
        String sql = "SELECT 1 FROM group_join_votes WHERE request_id = ? AND status = 'pending';";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requestId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private static GroupJoinRequest findByGroupAndRequester(Connection connection, int groupId, int requesterId)
            throws Exception {
        String sql = requestSelect() + "WHERE r.group_id = ? AND r.requester_id = ?;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, groupId);
            statement.setInt(2, requesterId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapRequest(resultSet) : null;
            }
        }
    }

    private static GroupJoinRequest findById(Connection connection, int requestId) throws Exception {
        String sql = requestSelect() + "WHERE r.id = ?;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, requestId);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? mapRequest(resultSet) : null;
            }
        }
    }

    private static String requestSelect() {
        return """
            SELECT r.id,
                   r.group_id,
                   g.name AS group_name,
                   r.requester_id,
                   requester.username AS requester_username,
                   r.status
            FROM group_join_requests r
            JOIN groups g ON g.id = r.group_id
            JOIN users requester ON requester.id = r.requester_id
        """;
    }

    private static String voteSelect() {
        return """
            SELECT v.request_id,
                   r.group_id,
                   g.name AS group_name,
                   r.requester_id,
                   requester.username AS requester_username,
                   v.voter_id,
                   v.status
            FROM group_join_votes v
            JOIN group_join_requests r ON r.id = v.request_id
            JOIN groups g ON g.id = r.group_id
            JOIN users requester ON requester.id = r.requester_id
        """;
    }

    private static GroupJoinRequest mapRequest(ResultSet resultSet) throws Exception {
        return new GroupJoinRequest(
                resultSet.getInt("id"),
                resultSet.getInt("group_id"),
                resultSet.getString("group_name"),
                resultSet.getInt("requester_id"),
                resultSet.getString("requester_username"),
                resultSet.getString("status")
        );
    }

    private static GroupJoinVote mapVote(ResultSet resultSet) throws Exception {
        return new GroupJoinVote(
                resultSet.getInt("request_id"),
                resultSet.getInt("group_id"),
                resultSet.getString("group_name"),
                resultSet.getInt("requester_id"),
                resultSet.getString("requester_username"),
                resultSet.getInt("voter_id"),
                resultSet.getString("status")
        );
    }
}
