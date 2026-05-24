package org.fipp.server;

import java.text.Normalizer;

public record ChatCommand(
        Type type,
        String rawText,
        String normalizedText,
        String receiverUsername,
        String content
) {
    public enum Type {
        EXIT,
        LOGOUT,
        STATUS,
        HELP,
        LIST_USERS,
        LIST_GROUPS,
        DIRECT_MESSAGE,
        INVALID
    }

    public static ChatCommand parse(String rawText) {
        String normalizedText = normalize(rawText);

        if (rawText == null || normalizedText.equals("sair")) {
            return simple(Type.EXIT, rawText, normalizedText);
        }

        return switch (normalizedText) {
            case "logout" -> simple(Type.LOGOUT, rawText, normalizedText);
            case "status" -> simple(Type.STATUS, rawText, normalizedText);
            case "ajuda" -> simple(Type.HELP, rawText, normalizedText);
            case "listausuarios" -> simple(Type.LIST_USERS, rawText, normalizedText);
            case "listagrupos" -> simple(Type.LIST_GROUPS, rawText, normalizedText);
            default -> parseMessage(rawText, normalizedText);
        };
    }

    public static String normalize(String value) {
        if (value == null) {
            return "";
        }

        String normalized = Normalizer.normalize(value.trim().toLowerCase(), Normalizer.Form.NFD);
        return normalized.replaceAll("\\p{M}", "");
    }

    private static ChatCommand parseMessage(String rawText, String normalizedText) {
        if (rawText == null || !rawText.startsWith("@")) {
            return simple(Type.INVALID, rawText, normalizedText);
        }

        int separatorIndex = rawText.indexOf(':');
        if (separatorIndex <= 1) {
            return simple(Type.INVALID, rawText, normalizedText);
        }

        String receiverUsername = rawText.substring(1, separatorIndex).trim();
        String content = rawText.substring(separatorIndex + 1).trim();
        return new ChatCommand(Type.DIRECT_MESSAGE, rawText, normalizedText, receiverUsername, content);
    }

    private static ChatCommand simple(Type type, String rawText, String normalizedText) {
        return new ChatCommand(type, rawText, normalizedText, null, null);
    }
}
