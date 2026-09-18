package net.yamakotaro.autoupdater.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.yamakotaro.autoupdater.manager.UpdateManager;
import net.yamakotaro.autoupdater.model.PluginUpdateState;
import net.yamakotaro.autoupdater.model.UpdateStatus;
import net.yamakotaro.autoupdater.notify.NotificationService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

/**
 * autoupdater.admin 権限を持つプレイヤーがログインした際に、
 * 保留中のアップデートがあれば通知する。
 */
public final class PlayerJoinListener implements Listener {

    private final JavaPlugin plugin;
    private final UpdateManager updateManager;

    public PlayerJoinListener(JavaPlugin plugin, UpdateManager updateManager) {
        this.plugin = plugin;
        this.updateManager = updateManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().hasPermission(NotificationService.ADMIN_PERMISSION)) return;

        List<PluginUpdateState> pending = updateManager.getStates().stream()
                .filter(s -> s.getStatus() == UpdateStatus.UPDATE_AVAILABLE)
                .toList();
        if (pending.isEmpty()) return;

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!event.getPlayer().isOnline()) return;
            event.getPlayer().sendMessage(Component.text("[AutoUpdater] ", NamedTextColor.GOLD)
                    .append(Component.text(pending.size() + " 件のアップデートが利用可能です。/autoupdate で確認できます。",
                            NamedTextColor.YELLOW)));
        }, 40L);
    }
}
