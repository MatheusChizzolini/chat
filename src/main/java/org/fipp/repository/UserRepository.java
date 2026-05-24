package org.fipp.repository;

import org.fipp.database.DatabaseConnection;
import org.fipp.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class UserRepository {
    public static User register(String fullName, String username, String email, String password) {
        User user = null;

        if (existsByUsername(username)) {
            System.out.println("Nome de usuário já utilizado: " + username);
        } else if (existsByEmail(email)) {
            System.out.println("Email já utilizado: " + email);
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
                System.out.println("Erro ao cadastrar usuário: " + e.getMessage());
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
                    user = new User(
                            resultSet.getInt("id"),
                            resultSet.getString("full_name"),
                            resultSet.getString("username"),
                            resultSet.getString("email"),
                            resultSet.getString("status")
                    );
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao buscar usuário: " + e.getMessage());
        }

        return user;
    }

    public static void updateStatus(int userId, String status) {
        String sql = "UPDATE users SET status = ? WHERE id = ?;";

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setInt(2, userId);
            statement.executeUpdate();
        } catch (Exception e) {
            System.out.println("Erro ao atualizar status do usuário: " + e.getMessage());
        }
    }

    private static boolean existsByUsername(String username) {
        return existsByField("username", username);
    }

    private static boolean existsByEmail(String email) {
        return existsByField("email", email);
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
            System.out.println("Erro ao verificar usuário: " + e.getMessage());
        }

        return exists;
    }
}
