package org.fipp.model;

public record GroupInvitation(
        int id,
        int groupId,
        String groupName,
        int invitedById,
        String inviterUsername,
        int receiverId,
        String status
) {
}
