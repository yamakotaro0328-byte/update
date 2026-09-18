package net.yamakotaro.autoupdater.notify;

import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.yamakotaro.autoupdater.http.Http;
import net.yamakotaro.autoupdater.model.PluginUpdateState;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.net.URI;
import java.net.http.HttpRequest;
import java.time.Duration;

/**
 * コンソール・管理者プレイヤー・Discord Webhook への通知をまとめて扱う。
 */
public final class NotificationService {

    public static final String ADMIN_PERMISSION = "autoupdater.admin";

    private final JavaPlugin plugin;
    private boolean notifyAdmin;
    private boolean discordEnabled;
    private String discordWebhookUrl;

    public NotificationService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void configure(boolean notifyAdmin, boolean discordEnabled, String discordWebhookUrl) {
        this.notifyAdmin = notifyAdmin;
        this.discordEnabled = discordEnabled;
        this.discordWebhookUrl = discordWebhookUrl;
    }

    public void console(String message) {
        plugin.getLogger().info(message);
    }

    public void consoleWarn(String message) {
        plugin.getLogger().warning(message);
    }

    public void updateAvailable(PluginUpdateState state) {
        String name = state.getPlugin().getName();
        String current = state.getCurrentVersion() == null ? "?" : state.getCurrentVersion().getRaw();
        String latest = state.getLatestInfo().getVersion().getRaw();

        console("Update available: " + name + " " + current + " -> " + latest);

        if (notifyAdmin) {
            Component msg = Component.text("[AutoUpdater] ", NamedTextColor.GOLD)
                    .append(Component.text(name + " " + current + " -> " + latest + " が利用可能です。",
                            NamedTextColor.YELLOW));
            broadcastToAdmins(msg);
        }

        sendDiscord(name, current, latest, "Update available");
    }

    public void updateScheduled(PluginUpdateState state) {
        String name = state.getPlugin().getName();
        console("Update scheduled for next server restart: " + name + " -> " + state.getLatestInfo().getVersion().getRaw());

        if (notifyAdmin) {
            Component msg = Component.text("[AutoUpdater] ", NamedTextColor.GOLD)
                    .append(Component.text(name + " の更新を次回サーバー起動時に適用します。", NamedTextColor.GREEN));
            broadcastToAdmins(msg);
        }
    }

    private void broadcastToAdmins(Component message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission(ADMIN_PERMISSION)) {
                p.sendMessage(message);
            }
        }
    }

    private void sendDiscord(String pluginName, String current, String latest, String status) {
        if (!discordEnabled || discordWebhookUrl == null || discordWebhookUrl.isBlank()) return;
        try {
            Http.requireHttps(discordWebhookUrl);
        } catch (IllegalArgumentException e) {
            consoleWarn("Discord webhook-url が不正です (https のみ許可): " + e.getMessage());
            return;
        }

        String content = "**[AutoUpdater]**\n" + pluginName + "\n" + current + " -> " + latest
                + "\n" + status + (status.startsWith("Update available") ? " scheduled for next restart." : "");
        JsonObject payload = new JsonObject();
        payload.addProperty("content", content);

        HttpRequest request = HttpRequest.newBuilder(URI.create(discordWebhookUrl))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .build();

        Http.client().sendAsync(request, java.net.http.HttpResponse.BodyHandlers.discarding())
                .exceptionally(ex -> {
                    consoleWarn("Discord 通知の送信に失敗しました: " + ex.getMessage());
                    return null;
                });
    }
}
