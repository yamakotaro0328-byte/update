# AutoUpdater

自作プラグインに限らず、GitHub Releases / Modrinth / SpigotMC (Spiget) で配布されている
一般プラグインも含めて自動チェック・ダウンロードする Paper/Purpur 用アップデータです。
稼働中の jar は直接書き換えず、`plugins/update/` に配置して次回サーバー起動時に適用します。

## ビルド方法

```
./gradlew build
```

`build/libs/AutoUpdater-<version>.jar` が生成されます。
`build.gradle.kts` の `paperApiVersion` (gradle.properties) は Paper 26.2 のアーティファクトを
前提にしています。ビルド時に解決できない場合は、実際に公開されている Paper API バージョンに
書き換えてください。

このサンドボックス環境は `repo.papermc.io` へのネットワークアクセスがポリシーで
ブロックされているため、ここでは `./gradlew build` の実行・検証ができていません。
実際にビルドする際は repo.papermc.io に到達できる環境で行ってください。

## 導入

1. `AutoUpdater-<version>.jar` を `plugins/` に配置
2. サーバーを起動すると `plugins/AutoUpdater/config.yml` と `plugins/AutoUpdater/plugins.yml` が生成される
3. `plugins.yml` に管理したいプラグインを追記する (自作/一般プラグイン問わず)

```yaml
plugins:
  Jobs:
    enabled: true
    source: github
    repository: "yourname/Jobs"

  EssentialsX:
    enabled: true
    source: modrinth
    project: "essentialsx"
    loader: "paper"

  ViaVersion:
    enabled: true
    source: spigot
    resource-id: 19254
```

4. 必要に応じて `config.yml` を調整 (チェック間隔、自動ダウンロード可否、通知、セキュリティ設定など)
5. `/autoupdate reload` または再起動で反映

## コマンド / 権限

- `/autoupdate` `/autoupdate check` `/autoupdate update [プラグイン名]`
  `/autoupdate rollback <プラグイン名>` `/autoupdate reload` `/autoupdate gui` (現状はスタブ、将来実装)
- 権限: `autoupdater.admin` (デフォルト op)

## 注意点

- SpigotMC は公式ダウンロード API を持たないため Spiget (api.spiget.org) を経由します。
  配布側が外部ダウンロードを許可していないリソースは自動ダウンロードできず、
  検出のみ行われます (ステータス欄にエラー内容が表示されます)。
- ダウンロード後は SHA-256/SHA-512 等のハッシュ検証・jar 構造検証・バックアップ (`plugins/backups/`)
  を経てから `plugins/update/` に配置します。検証に失敗した場合は現在のプラグインをそのまま維持します。
- `AutoUpdater` 自身は自動更新の対象外です。
