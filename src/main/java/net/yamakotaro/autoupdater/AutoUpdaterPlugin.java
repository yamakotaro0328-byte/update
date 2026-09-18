package net.yamakotaro.autoupdater;

import net.yamakotaro.autoupdater.command.AutoUpdateCommand;
import net.yamakotaro.autoupdater.config.ConfigManager;
import net.yamakotaro.autoupdater.config.ManagedPluginsConfig;
import net.yamakotaro.autoupdater.http.Http;
import net.yamakotaro.autoupdater.listener.PlayerJoinListener;
import net.yamakotaro.autoupdater.manager.UpdateManager;
import net.yamakotaro.autoupdater.notify.NotificationService;
import net.yamakotaro.autoupdater.task.UpdateCheckTask;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class AutoUpdaterPlugin extends JavaPlugin {

    private ConfigManager configManager;
    private ManagedPluginsConfig pluginsConfig;
    private NotificationService notificationService;
    private UpdateManager updateManager;
    private UpdateCheckTask scheduledTask;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.load();

        Http.configure(configManager.getConnectTimeoutSeconds(), configManager.getReadTimeoutSeconds(),
                command -> getServer().getScheduler().runTaskAsynchronously(this, command));

        notificationService = new NotificationService(this);
        notificationService.configure(configManager.isNotifyAdmin(), configManager.isDiscordEnabled(),
                configManager.getDiscordWebhookUrl());

        pluginsConfig = new ManagedPluginsConfig(this);
        updateManager = new UpdateManager(this, configManager, pluginsConfig, notificationService);
        updateManager.reload();

        PluginCommand cmd = getCommand("autoupdate");
        if (cmd != null) {
            AutoUpdateCommand executor = new AutoUpdateCommand(updateManager, this::reloadAll);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().warning("autoupdate コマンドの登録に失敗しました (plugin.yml を確認してください)");
        }

        getServer().getPluginManager().registerEvents(new PlayerJoinListener(this, updateManager), this);

        if (configManager.isAutoUpdateEnabled()) {
            startScheduledTask();
            if (configManager.isUpdateOnStartup()) {
                getServer().getScheduler().runTaskLaterAsynchronously(this, () ->
                        updateManager.checkAll().thenRun(() -> {
                            if (configManager.isDownloadUpdates()) {
                                updateManager.updateAllAvailable();
                            }
                        }), 100L);
            }
        } else {
            getLogger().info("auto-update.enabled が false のため、自動チェックは行いません。");
        }

        getLogger().info("AutoUpdater が有効になりました。");
    }

    @Override
    public void onDisable() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
        }
        getServer().getScheduler().cancelTasks(this);
    }

    private void reloadAll() {
        updateManager.reload();
        if (configManager.isAutoUpdateEnabled()) {
            startScheduledTask();
        } else if (scheduledTask != null) {
            scheduledTask.cancel();
            scheduledTask = null;
        }
    }

    private void startScheduledTask() {
        if (scheduledTask != null) {
            scheduledTask.cancel();
        }
        scheduledTask = new UpdateCheckTask(updateManager, configManager);
        long intervalTicks = configManager.getCheckIntervalMinutes() * 60L * 20L;
        scheduledTask.runTaskTimerAsynchronously(this, intervalTicks, intervalTicks);
    }
}
