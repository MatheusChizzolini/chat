package org.fipp.model;

public record User(
        int id,
        String fullName,
        String username,
        String email,
        String status
) {
}
