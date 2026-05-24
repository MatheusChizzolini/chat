package org.fipp.model;

public record GroupJoinVote(
        int requestId,
        int groupId,
        String groupName,
        int requesterId,
        String requesterUsername,
        int voterId,
        String status
) {
}
