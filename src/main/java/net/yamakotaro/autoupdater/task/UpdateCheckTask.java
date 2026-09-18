package net.yamakotaro.autoupdater.task;

import net.yamakotaro.autoupdater.config.ConfigManager;
import net.yamakotaro.autoupdater.manager.UpdateManager;
import org.bukkit.scheduler.BukkitRunnable;

/**
 * 一定間隔で自動的にアップデートを確認し、設定に応じてダウンロードまで行う。
 */
public final class UpdateCheckTask extends BukkitRunnable {

    private final UpdateManager updateManager;
    private final ConfigManager config;

    public UpdateCheckTask(UpdateManager updateManager, ConfigManager config) {
        this.updateManager = updateManager;
        this.config = config;
    }

    @Override
    public void run() {
        updateManager.checkAll().thenRun(() -> {
            if (config.isDownloadUpdates()) {
                updateManager.updateAllAvailable();
            }
        });
    }
}
