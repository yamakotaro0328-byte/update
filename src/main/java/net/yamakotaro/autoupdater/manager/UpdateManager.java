package net.yamakotaro.autoupdater.manager;

import net.yamakotaro.autoupdater.backup.BackupManager;
import net.yamakotaro.autoupdater.config.ConfigManager;
import net.yamakotaro.autoupdater.config.ManagedPluginsConfig;
import net.yamakotaro.autoupdater.config.SelfDeclaredConfig;
import net.yamakotaro.autoupdater.http.DownloadResult;
import net.yamakotaro.autoupdater.http.JarDownloader;
import net.yamakotaro.autoupdater.model.ManagedPlugin;
import net.yamakotaro.autoupdater.model.PluginUpdateState;
import net.yamakotaro.autoupdater.model.UpdateInfo;
import net.yamakotaro.autoupdater.model.UpdateStatus;
import net.yamakotaro.autoupdater.notify.NotificationService;
import net.yamakotaro.autoupdater.security.PackageVerifier;
import net.yamakotaro.autoupdater.source.AutoDiscoverySource;
import net.yamakotaro.autoupdater.source.GitHubReleaseSource;
import net.yamakotaro.autoupdater.source.ModrinthSource;
import net.yamakotaro.autoupdater.source.SpigotSource;
import net.yamakotaro.autoupdater.source.UpdateSource;
import net.yamakotaro.autoupdater.source.UpdateSourceType;
import net.yamakotaro.autoupdater.version.Version;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * アップデートの確認・ダウンロード・配置を統括するコアクラス。
 * 現在稼働中の jar は絶対に直接書き換えず、plugins/update/ に新しい jar を置くだけに留める。
 */
public final class UpdateManager {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final ManagedPluginsConfig pluginsConfig;
    private final NotificationService notifier;
    private final MinecraftVersionChecker mcChecker;
    private BackupManager backupManager;
    private final Executor asyncExecutor;

    private final Map<String, PluginUpdateState> states = new ConcurrentHashMap<>();
    private final Map<UpdateSourceType, UpdateSource> sources = new EnumMap<>(UpdateSourceType.class);

    private JarDownloader downloader;
    private Path pluginsDir;
    private Path updateDir;

    public UpdateManager(JavaPlugin plugin, ConfigManager configManager, ManagedPluginsConfig pluginsConfig,
                          NotificationService notifier) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.pluginsConfig = pluginsConfig;
        this.notifier = notifier;
        this.mcChecker = new MinecraftVersionChecker(configManager.getMinecraftCurrentVersion());
        this.asyncExecutor = r -> Bukkit.getScheduler().runTaskAsynchronously(plugin, r);
    }

    /** config.yml / plugins.yml を再読み込みし、ソースと管理対象一覧を再構築する。 */
    public void reload() {
        configManager.load();
        notifier.configure(configManager.isNotifyAdmin(), configManager.isDiscordEnabled(),
                configManager.getDiscordWebhookUrl());

        this.pluginsDir = plugin.getDataFolder().getParentFile().toPath();
        this.updateDir = pluginsDir.resolve("update");
        this.downloader = new JarDownloader(configManager.getMaxFileSizeBytes());
        this.backupManager = new BackupManager(
                plugin.getDataFolder().getParentFile().toPath().resolve("backups"),
                configManager.getBackupKeepCount());

        ModrinthSource modrinthSource = new ModrinthSource();
        SpigotSource spigotSource = new SpigotSource();
        sources.clear();
        sources.put(UpdateSourceType.GITHUB, new GitHubReleaseSource(configManager.getGithubApiBaseUrl()));
        sources.put(UpdateSourceType.MODRINTH, modrinthSource);
        sources.put(UpdateSourceType.SPIGOT, spigotSource);
        sources.put(UpdateSourceType.NONE, new AutoDiscoverySource(modrinthSource, spigotSource));
        // CUSTOM は将来の独自アップデートサーバー対応用 (未実装時は checkOne 側で ERROR にする)

        pluginsConfig.load();
        states.clear();

        // plugins.yml に何も登録しなくても、サーバーにインストール済みの全プラグインを自動で対象にする。
        // 優先順位: (1) plugins.yml の手動登録 (2) プラグイン自身の plugin.yml の autoupdate セクション
        //          (3) Modrinth slug の自動推測
        for (Plugin installed : Bukkit.getPluginManager().getPlugins()) {
            String name = installed.getName();
            if (name.equalsIgnoreCase(plugin.getName())) continue; // AutoUpdater 自身は対象外

            ManagedPlugin mp = pluginsConfig.getOverride(name);
            if (mp == null) {
                mp = SelfDeclaredConfig.read(name, resolveOriginalJarFile(name));
            }
            if (mp == null) {
                if (!configManager.isAutoDiscoverUnlisted()) continue;
                mp = new ManagedPlugin(name, true, UpdateSourceType.NONE, "", "", "", "paper", "", -1);
            }

            Version current = Version.parse(installed.getDescription().getVersion());
            PluginUpdateState state = new PluginUpdateState(mp, current);
            if (!mp.isEnabled()) {
                state.setStatus(UpdateStatus.DISABLED);
            }
            states.put(name, state);
        }

        // plugins.yml に登録されているがサーバーに未インストールのものは、管理画面に分かるよう残しておく。
        for (String name : pluginsConfig.getAllOverrideNames()) {
            if (states.containsKey(name) || name.equalsIgnoreCase(plugin.getName())) continue;
            PluginUpdateState state = new PluginUpdateState(pluginsConfig.getOverride(name), null);
            state.setStatus(UpdateStatus.NOT_INSTALLED);
            states.put(name, state);
        }
    }

    public List<PluginUpdateState> getStates() {
        return states.values().stream()
                .sorted((a, b) -> a.getPlugin().getName().compareToIgnoreCase(b.getPlugin().getName()))
                .toList();
    }

    public PluginUpdateState getState(String pluginName) {
        return states.get(pluginName);
    }

    public CompletableFuture<Void> checkAll() {
        notifier.console("Checking for updates...");
        List<CompletableFuture<Void>> futures = states.values().stream()
                .filter(s -> s.getPlugin().isEnabled() && s.getStatus() != UpdateStatus.NOT_INSTALLED)
                .map(this::checkOne)
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public CompletableFuture<Void> checkOne(PluginUpdateState state) {
        ManagedPlugin mp = state.getPlugin();
        UpdateSource source = sources.get(mp.getSourceType());
        if (source == null) {
            state.setStatus(UpdateStatus.ERROR);
            state.setLastError("未対応の更新元です: " + mp.getSourceType());
            state.setLastChecked(java.time.Instant.now());
            return CompletableFuture.completedFuture(null);
        }

        state.setStatus(UpdateStatus.CHECKING);
        return source.fetchLatest(mp).handle((info, ex) -> {
            state.setLastChecked(java.time.Instant.now());
            if (ex != null) {
                state.setStatus(UpdateStatus.ERROR);
                state.setLastError(rootMessage(ex));
                notifier.consoleWarn("Update check failed for " + mp.getName() + ": " + rootMessage(ex));
                return null;
            }
            state.setLatestInfo(info);
            evaluateStatus(state, info);
            return null;
        });
    }

    private void evaluateStatus(PluginUpdateState state, UpdateInfo info) {
        Version current = state.getCurrentVersion();
        if (current != null && !info.getVersion().isNewerThan(current)) {
            state.setStatus(UpdateStatus.UP_TO_DATE);
            return;
        }
        if (!mcChecker.isCompatible(info)) {
            state.setStatus(UpdateStatus.INCOMPATIBLE);
            state.setLastError("対応 Minecraft バージョン範囲外です (server=" + mcChecker.resolveServerVersion() + ")");
            return;
        }
        state.setStatus(UpdateStatus.UPDATE_AVAILABLE);
        if (configManager.isNotifyAdmin()) {
            notifier.updateAvailable(state);
        } else {
            notifier.console("Update available: " + state.getPlugin().getName()
                    + " " + (current == null ? "?" : current.getRaw()) + " -> " + info.getVersion().getRaw());
        }
    }

    public CompletableFuture<Void> updateAllAvailable() {
        List<CompletableFuture<Boolean>> futures = states.values().stream()
                .filter(s -> s.getStatus() == UpdateStatus.UPDATE_AVAILABLE)
                .map(s -> updateOne(s.getPlugin().getName()))
                .toList();
        return CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]));
    }

    public CompletableFuture<Boolean> updateOne(String pluginName) {
        PluginUpdateState state = states.get(pluginName);
        if (state == null) {
            notifier.consoleWarn("不明なプラグインです: " + pluginName);
            return CompletableFuture.completedFuture(false);
        }
        if (state.getStatus() != UpdateStatus.UPDATE_AVAILABLE) {
            notifier.console(pluginName + " は現在アップデート対象ではありません (status=" + state.getStatus() + ")");
            return CompletableFuture.completedFuture(false);
        }
        UpdateInfo info = state.getLatestInfo();
        if (info == null || info.getDownloadUrl() == null) {
            notifier.consoleWarn(pluginName + " のダウンロード URL が取得できません");
            state.setStatus(UpdateStatus.ERROR);
            return CompletableFuture.completedFuture(false);
        }
        // ダウングレード防止の最終確認
        Version current = state.getCurrentVersion();
        if (current != null && !info.getVersion().isNewerThan(current)) {
            state.setStatus(UpdateStatus.UP_TO_DATE);
            return CompletableFuture.completedFuture(false);
        }
        if (!mcChecker.isCompatible(info)) {
            state.setStatus(UpdateStatus.INCOMPATIBLE);
            return CompletableFuture.completedFuture(false);
        }

        state.setStatus(UpdateStatus.DOWNLOADING);
        Path originalJar = resolveOriginalJarFile(pluginName);
        String targetFileName = originalJar != null ? originalJar.getFileName().toString() : pluginName + ".jar";
        Path tempFile = updateDir.resolve(".tmp").resolve(pluginName + "-" + UUID.randomUUID() + ".download");

        notifier.console("Downloading " + targetFileName + "...");

        return CompletableFuture.supplyAsync(() -> {
            try {
                DownloadResult result = downloader.download(info.getDownloadUrl(), tempFile, info.getHashAlgorithm());

                if (info.getExpectedHash() != null) {
                    if (!PackageVerifier.hashMatches(result.hashHex(), info.getExpectedHash())) {
                        throw new IllegalStateException("ハッシュ値が一致しません (改ざんまたは破損の可能性)");
                    }
                } else if (configManager.isRequireHash()) {
                    throw new IllegalStateException("ハッシュ値を取得できず、require-hash が有効なため中断しました");
                }

                if (!PackageVerifier.isValidPluginJar(tempFile)) {
                    throw new IllegalStateException("ダウンロードされたファイルは有効な jar ではありません");
                }

                if (configManager.isBackupEnabled() && originalJar != null) {
                    backupManager.backup(pluginName, current == null ? null : current.getRaw(), originalJar);
                }

                Path destination = updateDir.resolve(targetFileName);
                JarDownloader.atomicReplace(tempFile, destination);

                state.setStatus(UpdateStatus.DOWNLOADED);
                notifier.updateScheduled(state);
                return true;
            } catch (Exception e) {
                deleteQuietly(tempFile);
                state.setStatus(UpdateStatus.ERROR);
                state.setLastError(rootMessage(e));
                notifier.consoleWarn("Update failed for " + pluginName + ": " + rootMessage(e));
                return false;
            }
        }, asyncExecutor);
    }

    /** 直近のバックアップを plugins/update/ に戻すことで、次回起動時のロールバックを予約する。 */
    public boolean rollback(String pluginName) {
        Path backup = backupManager.latestBackup(pluginName);
        if (backup == null) {
            notifier.consoleWarn("バックアップが見つかりません: " + pluginName);
            return false;
        }
        Path originalJar = resolveOriginalJarFile(pluginName);
        String targetFileName = originalJar != null ? originalJar.getFileName().toString() : pluginName + ".jar";
        try {
            Files.createDirectories(updateDir);
            Files.copy(backup, updateDir.resolve(targetFileName), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            notifier.console("Rollback scheduled for next restart: " + pluginName + " <- " + backup.getFileName());
            return true;
        } catch (Exception e) {
            notifier.consoleWarn("ロールバックの予約に失敗しました: " + rootMessage(e));
            return false;
        }
    }

    private Path resolveOriginalJarFile(String pluginName) {
        Plugin p = Bukkit.getPluginManager().getPlugin(pluginName);
        if (p instanceof JavaPlugin jp) {
            try {
                Method m = JavaPlugin.class.getDeclaredMethod("getFile");
                m.setAccessible(true);
                File f = (File) m.invoke(jp);
                if (f != null) return f.toPath();
            } catch (Exception ignored) {
                // 反射に失敗した場合はフォールバックへ
            }
        }
        return pluginsDir.resolve(pluginName + ".jar");
    }

    private static String rootMessage(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null
                && (cur instanceof java.util.concurrent.CompletionException
                    || cur instanceof java.util.concurrent.ExecutionException
                    || cur.getMessage() == null)) {
            cur = cur.getCause();
        }
        return cur.getMessage() != null ? cur.getMessage() : cur.toString();
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (Exception ignored) {
        }
    }
}
