package org.fipp.server;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

public class ChatCommandParser {
    public static ChatCommand parse(String rawText) {
        String normalizedText = normalize(rawText);

        if (rawText == null || normalizedText.equals("sair")) {
            return simple(ChatCommand.Type.EXIT, rawText, normalizedText);
        }

        return switch (normalizedText) {
            case "logout" -> simple(ChatCommand.Type.LOGOUT, rawText, normalizedText);
            case "status" -> simple(ChatCommand.Type.STATUS, rawText, normalizedText);
            case "ajuda" -> simple(ChatCommand.Type.HELP, rawText, normalizedText);
            case "listausuarios" -> simple(ChatCommand.Type.LIST_USERS, rawText, normalizedText);
            case "listagrupos" -> simple(ChatCommand.Type.LIST_GROUPS, rawText, normalizedText);
            default -> parseParameterizedCommand(rawText, normalizedText);
        };
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value.trim().toLowerCase(), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    private static ChatCommand parseParameterizedCommand(String rawText, String normalizedText) {
        if (normalizedText.equals("novogrupo") || normalizedText.startsWith("novogrupo ")) {
            String groupName = rawText.trim().substring("novogrupo".length()).trim();
            return new ChatCommand(ChatCommand.Type.CREATE_GROUP, rawText, normalizedText, null, groupName, null, null);
        }

        if (normalizedText.equals("inserir") || normalizedText.startsWith("inserir ")) {
            return parseGroupInvitation(rawText, normalizedText);
        }

        if (normalizedText.equals("entrar") || normalizedText.startsWith("entrar ")) {
            return parseGroupReferenceCommand(ChatCommand.Type.REQUEST_GROUP_ENTRY, rawText, normalizedText, "entrar");
        }

        if (normalizedText.startsWith("sair ")) {
            return parseGroupReferenceCommand(ChatCommand.Type.LEAVE_GROUP, rawText, normalizedText, "sair");
        }

        if (rawText != null && rawText.trim().startsWith("&")) {
            return parseGroupMessage(rawText, normalizedText);
        }

        return parseDirectMessage(rawText, normalizedText);
    }

    private static ChatCommand parseGroupInvitation(String rawText, String normalizedText) {
        String parameters = rawText.trim().substring("inserir".length()).trim();
        int memberSeparatorIndex = parameters.indexOf('@');

        if (!parameters.startsWith("&") || memberSeparatorIndex <= 1) {
            return new ChatCommand(ChatCommand.Type.INVITE_TO_GROUP, rawText, normalizedText, null, null, List.of(), null);
        }

        String groupName = parameters.substring(1, memberSeparatorIndex).trim();
        String memberText = parameters.substring(memberSeparatorIndex + 1).trim();
        List<String> memberUsernames = parseUsernames(memberText);

        return new ChatCommand(
                ChatCommand.Type.INVITE_TO_GROUP,
                rawText,
                normalizedText,
                null,
                groupName,
                memberUsernames,
                null
        );
    }

    private static ChatCommand parseGroupReferenceCommand(
            ChatCommand.Type type,
            String rawText,
            String normalizedText,
            String prefix
    ) {
        String parameters = rawText.trim().substring(prefix.length()).trim();
        String groupName = parameters.startsWith("&") ? parameters.substring(1).trim() : null;

        return new ChatCommand(type, rawText, normalizedText, null, groupName, null, null);
    }

    private static ChatCommand parseGroupMessage(String rawText, String normalizedText) {
        String messageText = rawText.trim();
        int messageSeparatorIndex = messageText.indexOf(':');

        if (messageSeparatorIndex <= 1) {
            return new ChatCommand(ChatCommand.Type.GROUP_MESSAGE, rawText, normalizedText, null, null, List.of(), null);
        }

        String recipientsText = messageText.substring(1, messageSeparatorIndex).trim();
        String content = messageText.substring(messageSeparatorIndex + 1).trim();
        int memberSeparatorIndex = recipientsText.indexOf('@');

        if (memberSeparatorIndex < 0) {
            return new ChatCommand(
                    ChatCommand.Type.GROUP_MESSAGE,
                    rawText,
                    normalizedText,
                    null,
                    recipientsText,
                    null,
                    content
            );
        }

        String groupName = recipientsText.substring(0, memberSeparatorIndex).trim();
        List<String> memberUsernames = parseUsernames(recipientsText.substring(memberSeparatorIndex + 1));
        return new ChatCommand(
                ChatCommand.Type.GROUP_MESSAGE,
                rawText,
                normalizedText,
                null,
                groupName,
                memberUsernames,
                content
        );
    }

    private static ChatCommand parseDirectMessage(String rawText, String normalizedText) {
        if (rawText == null || !rawText.startsWith("@")) {
            return simple(ChatCommand.Type.INVALID, rawText, normalizedText);
        }

        int separatorIndex = rawText.indexOf(':');
        if (separatorIndex <= 1) {
            return simple(ChatCommand.Type.INVALID, rawText, normalizedText);
        }

        String receiverUsername = rawText.substring(1, separatorIndex).trim();
        String content = rawText.substring(separatorIndex + 1).trim();
        return new ChatCommand(
                ChatCommand.Type.DIRECT_MESSAGE,
                rawText,
                normalizedText,
                receiverUsername,
                null,
                null,
                content
        );
    }

    private static ChatCommand simple(ChatCommand.Type type, String rawText, String normalizedText) {
        return new ChatCommand(type, rawText, normalizedText, null, null, null, null);
    }

    private static List<String> parseUsernames(String text) {
        List<String> usernames = new ArrayList<>();

        for (String member : text.split(",")) {
            String username = member.trim();
            if (username.startsWith("@")) {
                username = username.substring(1).trim();
            }
            if (!username.isBlank()) {
                usernames.add(username);
            }
        }

        return usernames;
    }
}
