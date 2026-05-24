package org.fipp.model;

public record Group(
        int id,
        String name,
        int createdById,
        String creatorUsername,
        String createdAt
) {
}
