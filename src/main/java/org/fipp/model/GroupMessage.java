package org.fipp.model;

public record GroupMessage(
        int id,
        int groupId,
        String groupName,
        int senderId,
        int receiverId,
        String senderUsername,
        String receiverUsername,
        String content,
        String createdAt
) {
}
