package net.yamakotaro.autoupdater.model;

import net.yamakotaro.autoupdater.source.UpdateSourceType;

/**
 * plugins.yml 1エントリ分の設定。自作プラグインに限らず、
 * GitHub Releases / Modrinth / SpigotMC いずれで配布されている
 * 一般プラグインも同じ仕組みで管理できる。
 */
public final class ManagedPlugin {

    private final String name;
    private final boolean enabled;
    private final UpdateSourceType sourceType;

    // GitHub
    private final String repository;
    private final String assetPattern;

    // Modrinth
    private final String modrinthProject;
    private final String modrinthLoader;
    private final String modrinthGameVersion;

    // Spigot (Spiget)
    private final int spigotResourceId;

    public ManagedPlugin(String name, boolean enabled, UpdateSourceType sourceType,
                          String repository, String assetPattern,
                          String modrinthProject, String modrinthLoader, String modrinthGameVersion,
                          int spigotResourceId) {
        this.name = name;
        this.enabled = enabled;
        this.sourceType = sourceType;
        this.repository = repository;
        this.assetPattern = assetPattern;
        this.modrinthProject = modrinthProject;
        this.modrinthLoader = modrinthLoader;
        this.modrinthGameVersion = modrinthGameVersion;
        this.spigotResourceId = spigotResourceId;
    }

    public String getName() { return name; }
    public boolean isEnabled() { return enabled; }
    public UpdateSourceType getSourceType() { return sourceType; }
    public String getRepository() { return repository; }
    public String getAssetPattern() { return assetPattern; }
    public String getModrinthProject() { return modrinthProject; }
    public String getModrinthLoader() { return modrinthLoader; }
    public String getModrinthGameVersion() { return modrinthGameVersion; }
    public int getSpigotResourceId() { return spigotResourceId; }
}
