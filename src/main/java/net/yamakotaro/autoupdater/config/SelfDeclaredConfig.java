package net.yamakotaro.autoupdater.config;

import net.yamakotaro.autoupdater.model.ManagedPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * プラグイン開発者自身が、自分の plugin.yml に以下のような "autoupdate" セクションを
 * 書いておくことで、サーバー管理者が plugins.yml に何も登録しなくても
 * 自動更新の対象にできる仕組み。
 *
 * autoupdate:
 *   source: github
 *   repository: "owner/repo"
 */
public final class SelfDeclaredConfig {

    private SelfDeclaredConfig() {}

    public static ManagedPlugin read(String pluginName, Path jarFile) {
        if (jarFile == null) return null;
        try (ZipFile zip = new ZipFile(jarFile.toFile())) {
            ZipEntry entry = zip.getEntry("plugin.yml");
            if (entry == null) return null;
            try (InputStream in = zip.getInputStream(entry)) {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                ConfigurationSection section = yaml.getConfigurationSection("autoupdate");
                if (section == null) return null;
                return ManagedPlugin.fromSection(pluginName, section);
            }
        } catch (Exception e) {
            return null;
        }
    }
}
