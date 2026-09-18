package net.yamakotaro.autoupdater.source;

public enum UpdateSourceType {
    GITHUB,
    MODRINTH,
    SPIGOT,
    CUSTOM;

    public static UpdateSourceType fromConfig(String value) {
        if (value == null) return GITHUB;
        return switch (value.trim().toLowerCase()) {
            case "modrinth" -> MODRINTH;
            case "spigot", "spigotmc" -> SPIGOT;
            case "custom" -> CUSTOM;
            default -> GITHUB;
        };
    }
}
