package net.yamakotaro.autoupdater.config;

import net.yamakotaro.autoupdater.model.ManagedPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * plugins.yml (任意の手動オーバーライド一覧) の読み込み。
 * ここに登録されていないプラグインも、後述の自己申告 (plugin.yml 内 autoupdate セクション) や
 * Modrinth slug 自動推測によって、登録なしで自動更新の対象になり得る。
 */
public final class ManagedPluginsConfig {

    private final JavaPlugin plugin;
    private final File file;
    private Map<String, ManagedPlugin> overrides = new HashMap<>();

    public ManagedPluginsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "plugins.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("plugins.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        Map<String, ManagedPlugin> map = new HashMap<>();

        ConfigurationSection section = yaml.getConfigurationSection("plugins");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(name);
                if (entry == null) continue;
                map.put(name, ManagedPlugin.fromSection(name, entry));
            }
        }

        this.overrides = map;
    }

    /** plugins.yml に明示登録されている場合のみ値を返す (無ければ null)。 */
    public ManagedPlugin getOverride(String pluginName) {
        return overrides.get(pluginName);
    }

    public java.util.Set<String> getAllOverrideNames() {
        return overrides.keySet();
    }
}
