package org.fipp.repository;

import org.fipp.database.DatabaseConnection;
import org.fipp.model.Group;
import org.fipp.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class GroupRepository {
    public static Group create(String name, int creatorId) {
        Group group = null;
        String insertGroupSql = "INSERT INTO groups (name, created_by_id) VALUES (?, ?);";
        String insertMemberSql = "INSERT INTO group_members (group_id, user_id) VALUES (?, ?);";

        try (Connection connection = DatabaseConnection.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement groupStatement = connection.prepareStatement(
                    insertGroupSql,
                    Statement.RETURN_GENERATED_KEYS
            )) {
                groupStatement.setString(1, name);
                groupStatement.setInt(2, creatorId);
                groupStatement.executeUpdate();

                int groupId = findInsertedId(connection, groupStatement);
                try (PreparedStatement memberStatement = connection.prepareStatement(insertMemberSql)) {
                    memberStatement.setInt(1, groupId);
                    memberStatement.setInt(2, creatorId);
                    memberStatement.executeUpdate();
                }

                group = findById(connection, groupId);
                connection.commit();
            } catch (Exception e) {
                connection.rollback();
                throw e;
            }
        } catch (Exception e) {
            System.out.println("Erro ao criar grupo: " + e.getMessage());
        }

        return group;
    }

    public static Group findByName(String name) {
        Group group = null;
        String sql = baseSelect() + "WHERE LOWER(g.name) = LOWER(?);";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, name);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    group = mapGroup(resultSet);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao buscar grupo: " + e.getMessage());
        }

        return group;
    }

    public static List<Group> findAll() {
        List<Group> groups = new ArrayList<>();
        String sql = baseSelect() + "ORDER BY g.name;";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                groups.add(mapGroup(resultSet));
            }
        } catch (Exception e) {
            System.out.println("Erro ao listar grupos: " + e.getMessage());
        }

        return groups;
    }

    public static boolean isMember(int groupId, int userId) {
        boolean member = false;
        String sql = "SELECT 1 FROM group_members WHERE group_id = ? AND user_id = ?;";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, groupId);
            statement.setInt(2, userId);

            try (ResultSet resultSet = statement.executeQuery()) {
                member = resultSet.next();
            }
        } catch (Exception e) {
            System.out.println("Erro ao verificar membro do grupo: " + e.getMessage());
        }

        return member;
    }

    public static boolean addMember(int groupId, int userId) {
        String sql = "INSERT OR IGNORE INTO group_members (group_id, user_id) VALUES (?, ?);";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, groupId);
            statement.setInt(2, userId);
            statement.executeUpdate();
            return isMember(groupId, userId);
        } catch (Exception e) {
            System.out.println("Erro ao adicionar membro ao grupo: " + e.getMessage());
            return false;
        }
    }

    public static boolean removeMember(int groupId, int userId) {
        String sql = "DELETE FROM group_members WHERE group_id = ? AND user_id = ?;";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, groupId);
            statement.setInt(2, userId);
            return statement.executeUpdate() > 0;
        } catch (Exception e) {
            System.out.println("Erro ao remover membro do grupo: " + e.getMessage());
            return false;
        }
    }

    public static List<User> findMembers(int groupId) {
        List<User> members = new ArrayList<>();
        String sql = """
            SELECT u.id, u.full_name, u.username, u.email, u.status
            FROM group_members gm
            JOIN users u ON u.id = gm.user_id
            WHERE gm.group_id = ?
            ORDER BY u.username;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, groupId);

            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    members.add(new User(
                            resultSet.getInt("id"),
                            resultSet.getString("full_name"),
                            resultSet.getString("username"),
                            resultSet.getString("email"),
                            resultSet.getString("status")
                    ));
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao listar membros do grupo: " + e.getMessage());
        }

        return members;
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

    private static Group findById(Connection connection, int groupId) throws Exception {
        Group group = null;
        String sql = baseSelect() + "WHERE g.id = ?;";

        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, groupId);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    group = mapGroup(resultSet);
                }
            }
        }

        return group;
    }

    private static String baseSelect() {
        return """
            SELECT g.id,
                   g.name,
                   g.created_by_id,
                   creator.username AS creator_username,
                   g.created_at
            FROM groups g
            JOIN users creator ON creator.id = g.created_by_id
        """;
    }

    private static Group mapGroup(ResultSet resultSet) throws Exception {
        return new Group(
                resultSet.getInt("id"),
                resultSet.getString("name"),
                resultSet.getInt("created_by_id"),
                resultSet.getString("creator_username"),
                resultSet.getString("created_at")
        );
    }
}
