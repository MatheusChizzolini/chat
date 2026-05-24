package org.fipp.model;

public record DirectMessage(
        int id,
        int senderId,
        int receiverId,
        String senderUsername,
        String receiverUsername,
        String content,
        String createdAt
) {
}
