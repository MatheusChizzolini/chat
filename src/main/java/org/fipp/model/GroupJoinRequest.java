package org.fipp.model;

public record GroupJoinRequest(
        int id,
        int groupId,
        String groupName,
        int requesterId,
        String requesterUsername,
        String status
) {
}
