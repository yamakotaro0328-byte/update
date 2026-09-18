package net.yamakotaro.autoupdater.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * config.yml の内容を型安全に取り出すためのラッパー。
 */
public final class ConfigManager {

    private final JavaPlugin plugin;

    private boolean autoUpdateEnabled;
    private boolean updateOnStartup;
    private long checkIntervalMinutes;
    private boolean downloadUpdates;
    private boolean notifyAdmin;
    private boolean autoDiscoverUnlisted;
    private String minecraftCurrentVersion;
    private boolean httpsOnly;
    private boolean requireHash;
    private int connectTimeoutSeconds;
    private int readTimeoutSeconds;
    private long maxFileSizeBytes;
    private String githubApiBaseUrl;
    private boolean customServerEnabled;
    private String customServerBaseUrl;
    private boolean backupEnabled;
    private int backupKeepCount;
    private boolean discordEnabled;
    private String discordWebhookUrl;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

        autoUpdateEnabled = c.getBoolean("auto-update.enabled", true);
        updateOnStartup = c.getBoolean("auto-update.update-on-startup", true);
        checkIntervalMinutes = Math.max(1, c.getLong("auto-update.check-interval-minutes", 30));
        downloadUpdates = c.getBoolean("auto-update.download-updates", true);
        notifyAdmin = c.getBoolean("auto-update.notify-admin", true);
        autoDiscoverUnlisted = c.getBoolean("auto-update.auto-discover-unlisted", true);

        minecraftCurrentVersion = c.getString("minecraft.current-version", "");

        httpsOnly = c.getBoolean("security.https-only", true);
        requireHash = c.getBoolean("security.require-hash", false);
        connectTimeoutSeconds = Math.max(1, c.getInt("security.connect-timeout-seconds", 10));
        readTimeoutSeconds = Math.max(1, c.getInt("security.read-timeout-seconds", 30));
        maxFileSizeBytes = Math.max(1, c.getInt("security.max-file-size-mb", 100)) * 1024L * 1024L;

        githubApiBaseUrl = c.getString("update-source.github.api-base-url", "https://api.github.com");
        customServerEnabled = c.getBoolean("update-source.custom-server.enabled", false);
        customServerBaseUrl = c.getString("update-source.custom-server.base-url", "");

        backupEnabled = c.getBoolean("backup.enabled", true);
        backupKeepCount = Math.max(1, c.getInt("backup.keep-count", 5));

        discordEnabled = c.getBoolean("discord.enabled", false);
        discordWebhookUrl = c.getString("discord.webhook-url", "");
    }

    public boolean isAutoUpdateEnabled() { return autoUpdateEnabled; }
    public boolean isUpdateOnStartup() { return updateOnStartup; }
    public long getCheckIntervalMinutes() { return checkIntervalMinutes; }
    public boolean isDownloadUpdates() { return downloadUpdates; }
    public boolean isNotifyAdmin() { return notifyAdmin; }
    public boolean isAutoDiscoverUnlisted() { return autoDiscoverUnlisted; }
    public String getMinecraftCurrentVersion() { return minecraftCurrentVersion; }
    public boolean isHttpsOnly() { return httpsOnly; }
    public boolean isRequireHash() { return requireHash; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public String getGithubApiBaseUrl() { return githubApiBaseUrl; }
    public boolean isCustomServerEnabled() { return customServerEnabled; }
    public String getCustomServerBaseUrl() { return customServerBaseUrl; }
    public boolean isBackupEnabled() { return backupEnabled; }
    public int getBackupKeepCount() { return backupKeepCount; }
    public boolean isDiscordEnabled() { return discordEnabled; }
    public String getDiscordWebhookUrl() { return discordWebhookUrl; }
}
