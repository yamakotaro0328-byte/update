package net.yamakotaro.autoupdater.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.yamakotaro.autoupdater.manager.UpdateManager;
import net.yamakotaro.autoupdater.model.PluginUpdateState;
import net.yamakotaro.autoupdater.model.UpdateStatus;
import net.yamakotaro.autoupdater.notify.NotificationService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * /autoupdate コマンド本体。
 */
public final class AutoUpdateCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUBCOMMANDS = List.of("check", "update", "reload", "gui", "rollback");

    private final UpdateManager updateManager;
    private final Runnable reloadCallback;

    public AutoUpdateCommand(UpdateManager updateManager, Runnable reloadCallback) {
        this.updateManager = updateManager;
        this.reloadCallback = reloadCallback;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                              @NotNull String label, String[] args) {
        if (!sender.hasPermission(NotificationService.ADMIN_PERMISSION)) {
            sender.sendMessage(Component.text("このコマンドを実行する権限がありません。", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendStatus(sender);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "check" -> {
                sender.sendMessage(Component.text("[AutoUpdater] アップデートを確認しています...", NamedTextColor.GRAY));
                updateManager.checkAll().thenRun(() -> sendStatus(sender));
            }
            case "update" -> {
                if (args.length >= 2) {
                    String name = resolvePluginName(args[1]);
                    if (name == null) {
                        sender.sendMessage(Component.text("プラグインが見つかりません: " + args[1], NamedTextColor.RED));
                        return true;
                    }
                    sender.sendMessage(Component.text("[AutoUpdater] " + name + " をダウンロードしています...", NamedTextColor.GRAY));
                    updateManager.updateOne(name).thenAccept(ok -> sender.sendMessage(
                            ok ? Component.text(name + " の更新をダウンロードしました。次回起動時に適用されます。", NamedTextColor.GREEN)
                               : Component.text(name + " の更新に失敗、または対象がありませんでした。", NamedTextColor.RED)));
                } else {
                    sender.sendMessage(Component.text("[AutoUpdater] 利用可能なアップデートをダウンロードしています...", NamedTextColor.GRAY));
                    updateManager.updateAllAvailable().thenRun(() ->
                            sender.sendMessage(Component.text("[AutoUpdater] ダウンロード処理が完了しました。", NamedTextColor.GREEN)));
                }
            }
            case "rollback" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("使い方: /autoupdate rollback <プラグイン名>", NamedTextColor.RED));
                    return true;
                }
                String name = resolvePluginName(args[1]);
                if (name == null) {
                    sender.sendMessage(Component.text("プラグインが見つかりません: " + args[1], NamedTextColor.RED));
                    return true;
                }
                boolean ok = updateManager.rollback(name);
                sender.sendMessage(ok
                        ? Component.text(name + " のロールバックを次回起動時に適用するよう予約しました。", NamedTextColor.GREEN)
                        : Component.text(name + " のロールバックに失敗しました (バックアップが無い可能性があります)。", NamedTextColor.RED));
            }
            case "reload" -> {
                reloadCallback.run();
                sender.sendMessage(Component.text("[AutoUpdater] config.yml / plugins.yml を再読み込みしました。", NamedTextColor.GREEN));
            }
            case "gui" -> {
                if (!(sender instanceof Player)) {
                    sender.sendMessage(Component.text("GUI はプレイヤーのみ実行できます。", NamedTextColor.RED));
                } else {
                    sender.sendMessage(Component.text("GUI は将来のバージョンで実装予定です。現在は /autoupdate で状況を確認してください。",
                            NamedTextColor.YELLOW));
                }
            }
            default -> sender.sendMessage(Component.text("不明なサブコマンドです: " + args[0], NamedTextColor.RED));
        }
        return true;
    }

    private void sendStatus(CommandSender sender) {
        List<PluginUpdateState> states = updateManager.getStates();
        sender.sendMessage(Component.text("[AutoUpdater]", NamedTextColor.GOLD));
        if (states.isEmpty()) {
            sender.sendMessage(Component.text("管理対象のプラグインが設定されていません (plugins.yml を確認してください)。",
                    NamedTextColor.GRAY));
            return;
        }
        int nameWidth = states.stream().mapToInt(s -> s.getPlugin().getName().length()).max().orElse(4) + 2;
        for (PluginUpdateState s : states) {
            sender.sendMessage(formatLine(s, nameWidth));
        }
    }

    private Component formatLine(PluginUpdateState s, int nameWidth) {
        String name = padRight(s.getPlugin().getName(), nameWidth);
        String current = s.getCurrentVersion() == null ? "-" : s.getCurrentVersion().getRaw();

        return switch (s.getStatus()) {
            case UPDATE_AVAILABLE -> Component.text(name + current + " -> "
                    + s.getLatestInfo().getVersion().getRaw() + "  UPDATE AVAILABLE", NamedTextColor.YELLOW);
            case UP_TO_DATE -> Component.text(name + current + "  UP TO DATE", NamedTextColor.GREEN);
            case DOWNLOADED -> Component.text(name + current + "  DOWNLOADED (次回起動時に適用)", NamedTextColor.AQUA);
            case DOWNLOADING -> Component.text(name + current + "  DOWNLOADING...", NamedTextColor.AQUA);
            case CHECKING -> Component.text(name + current + "  CHECKING...", NamedTextColor.GRAY);
            case INCOMPATIBLE -> Component.text(name + current + "  INCOMPATIBLE", NamedTextColor.RED);
            case NOT_INSTALLED -> Component.text(name + "NOT INSTALLED", NamedTextColor.DARK_GRAY);
            case DISABLED -> Component.text(name + "DISABLED", NamedTextColor.DARK_GRAY);
            case ERROR -> Component.text(name + "ERROR: " + (s.getLastError() == null ? "?" : s.getLastError()),
                    NamedTextColor.RED);
            case UNKNOWN -> Component.text(name + "未確認 (/autoupdate check を実行してください)", NamedTextColor.GRAY);
        };
    }

    private static String padRight(String s, int width) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private String resolvePluginName(String input) {
        return updateManager.getStates().stream()
                .map(s -> s.getPlugin().getName())
                .filter(n -> n.equalsIgnoreCase(input))
                .findFirst()
                .orElse(null);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                                  @NotNull String alias, String[] args) {
        if (!sender.hasPermission(NotificationService.ADMIN_PERMISSION)) return List.of();

        if (args.length == 1) {
            return SUBCOMMANDS.stream().filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("update") || args[0].equalsIgnoreCase("rollback"))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return updateManager.getStates().stream()
                    .map(s -> s.getPlugin().getName())
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
