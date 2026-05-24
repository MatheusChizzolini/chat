package org.fipp.server;

import java.util.List;

public record ChatCommand(
        Type type,
        String rawText,
        String normalizedText,
        String receiverUsername,
        String groupName,
        List<String> memberUsernames,
        String content
) {
    public enum Type {
        EXIT,
        LOGOUT,
        STATUS,
        HELP,
        LIST_USERS,
        LIST_GROUPS,
        CREATE_GROUP,
        INVITE_TO_GROUP,
        REQUEST_GROUP_ENTRY,
        LEAVE_GROUP,
        GROUP_MESSAGE,
        DIRECT_MESSAGE,
        INVALID
    }
}
