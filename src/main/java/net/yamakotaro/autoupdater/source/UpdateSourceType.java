package net.yamakotaro.autoupdater.source;

public enum UpdateSourceType {
    GITHUB,
    MODRINTH,
    SPIGOT,
    CUSTOM,
    /** plugins.yml に未登録・plugin.yml にも自己申告が無いプラグイン用。チェック時に自動推測する。 */
    NONE;

    public static UpdateSourceType fromConfig(String value) {
        if (value == null) return GITHUB;
        return switch (value.trim().toLowerCase()) {
            case "modrinth" -> MODRINTH;
            case "spigot", "spigotmc" -> SPIGOT;
            case "custom" -> CUSTOM;
            case "none" -> NONE;
            default -> GITHUB;
        };
    }
}
