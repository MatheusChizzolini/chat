package org.fipp.repository;

import org.fipp.database.DatabaseConnection;
import org.fipp.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class UserRepository {
    public static User register(String fullName, String username, String email, String password) {
        User user = null;
        if (existsByFullName(fullName)) {
            System.out.println("Nome completo ja utilizado: " + fullName);
        } else if (existsByUsername(username)) {
            System.out.println("Login ja utilizado: " + username);
        } else if (existsByEmail(email)) {
            System.out.println("Email ja utilizado: " + email);
        } else {
            String sql = """
                INSERT INTO users (full_name, username, email, password, status)
                VALUES (?, ?, ?, ?, 'offline');
            """;

            try (Connection connection = DatabaseConnection.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, fullName);
                statement.setString(2, username);
                statement.setString(3, email);
                statement.setString(4, password);
                statement.executeUpdate();
                user = findByUsernameAndPassword(username, password);
            } catch (Exception e) {
                System.out.println("Erro ao cadastrar usuario: " + e.getMessage());
            }
        }

        return user;
    }

    public static User findByUsernameAndPassword(String username, String password) {
        User user = null;
        String sql = """
            SELECT id, full_name, username, email, status
            FROM users
            WHERE username = ? AND password = ?;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            statement.setString(2, password);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    user = mapUser(resultSet);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao buscar usuario: " + e.getMessage());
        }

        return user;
    }

    public static User findByUsername(String username) {
        User user = null;
        String sql = """
            SELECT id, full_name, username, email, status
            FROM users
            WHERE LOWER(username) = LOWER(?);
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    user = mapUser(resultSet);
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao buscar usuario por login: " + e.getMessage());
        }

        return user;
    }

    public static List<User> findOnlineUsers() {
        List<User> users = new ArrayList<>();
        String sql = """
            SELECT id, full_name, username, email, status
            FROM users
            WHERE status = 'online'
            ORDER BY username;
        """;

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                users.add(mapUser(resultSet));
            }
        } catch (Exception e) {
            System.out.println("Erro ao listar usuarios online: " + e.getMessage());
        }

        return users;
    }

    public static String findPasswordByEmail(String email) {
        String password = null;
        String sql = "SELECT password FROM users WHERE LOWER(email) = LOWER(?);";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, email);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    password = resultSet.getString("password");
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao recuperar senha: " + e.getMessage());
        }

        return password;
    }

    public static void updateStatus(int userId, String status) {
        String sql = "UPDATE users SET status = ? WHERE id = ?;";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setInt(2, userId);
            statement.executeUpdate();
        } catch (Exception e) {
            System.out.println("Erro ao atualizar status do usuario: " + e.getMessage());
        }
    }

    private static boolean existsByUsername(String username) {
        return existsByField("username", username);
    }

    private static boolean existsByEmail(String email) {
        return existsByField("email", email);
    }

    private static boolean existsByFullName(String fullName) {
        return existsByField("full_name", fullName);
    }

    private static boolean existsByField(String fieldName, String value) {
        boolean exists = false;
        String sql = "SELECT 1 FROM users WHERE LOWER(" + fieldName + ") = LOWER(?) LIMIT 1;";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);

            try (ResultSet resultSet = statement.executeQuery()) {
                exists = resultSet.next();
            }
        } catch (Exception e) {
            System.out.println("Erro ao verificar usuario: " + e.getMessage());
        }

        return exists;
    }

    private static User mapUser(ResultSet resultSet) throws Exception {
        return new User(
                resultSet.getInt("id"),
                resultSet.getString("full_name"),
                resultSet.getString("username"),
                resultSet.getString("email"),
                resultSet.getString("status")
        );
    }
}
