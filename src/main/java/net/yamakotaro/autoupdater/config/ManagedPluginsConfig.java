package net.yamakotaro.autoupdater.config;

import net.yamakotaro.autoupdater.model.ManagedPlugin;
import net.yamakotaro.autoupdater.source.UpdateSourceType;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * plugins.yml (管理対象プラグイン一覧) の読み込み。
 */
public final class ManagedPluginsConfig {

    private final JavaPlugin plugin;
    private final File file;
    private List<ManagedPlugin> managedPlugins = new ArrayList<>();

    public ManagedPluginsConfig(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "plugins.yml");
    }

    public void load() {
        if (!file.exists()) {
            plugin.saveResource("plugins.yml", false);
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        List<ManagedPlugin> list = new ArrayList<>();

        ConfigurationSection section = yaml.getConfigurationSection("plugins");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                ConfigurationSection entry = section.getConfigurationSection(name);
                if (entry == null) continue;

                boolean enabled = entry.getBoolean("enabled", true);
                UpdateSourceType sourceType = UpdateSourceType.fromConfig(entry.getString("source", "github"));

                String repository = entry.getString("repository", "");
                String assetPattern = entry.getString("asset-pattern", "");

                String modrinthProject = entry.getString("project", "");
                String modrinthLoader = entry.getString("loader", "paper");
                String modrinthGameVersion = entry.getString("game-version", "");

                int spigotResourceId = entry.getInt("resource-id", -1);

                list.add(new ManagedPlugin(name, enabled, sourceType, repository, assetPattern,
                        modrinthProject, modrinthLoader, modrinthGameVersion, spigotResourceId));
            }
        }

        this.managedPlugins = list;
    }

    public List<ManagedPlugin> getManagedPlugins() {
        return managedPlugins;
    }
}
