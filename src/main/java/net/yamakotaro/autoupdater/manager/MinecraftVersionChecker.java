package net.yamakotaro.autoupdater.manager;

import net.yamakotaro.autoupdater.model.UpdateInfo;
import net.yamakotaro.autoupdater.version.Version;
import org.bukkit.Bukkit;

/**
 * 配布側が申告する対応 Minecraft バージョン範囲と、現在のサーバーバージョンを比較する。
 */
public final class MinecraftVersionChecker {

    private final String configuredServerVersion;

    public MinecraftVersionChecker(String configuredServerVersion) {
        this.configuredServerVersion = configuredServerVersion;
    }

    public String resolveServerVersion() {
        if (configuredServerVersion != null && !configuredServerVersion.isBlank()) {
            return configuredServerVersion;
        }
        try {
            return Bukkit.getMinecraftVersion();
        } catch (NoSuchMethodError | NoClassDefFoundError e) {
            String bukkitVersion = Bukkit.getBukkitVersion(); // 例: "1.26.2-R0.1-SNAPSHOT"
            int dash = bukkitVersion.indexOf('-');
            return dash > 0 ? bukkitVersion.substring(0, dash) : bukkitVersion;
        }
    }

    public boolean isCompatible(UpdateInfo info) {
        if (info.getMinecraftMin() == null && info.getMinecraftMax() == null) {
            return true; // 配布側が範囲を明示していない場合は許可する
        }
        Version server = Version.parse(resolveServerVersion());
        if (info.getMinecraftMin() != null) {
            Version min = Version.parse(info.getMinecraftMin());
            if (server.isOlderThan(min)) return false;
        }
        if (info.getMinecraftMax() != null) {
            Version max = Version.parse(info.getMinecraftMax());
            if (server.isNewerThan(max)) return false;
        }
        return true;
    }
}
