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

## 導入 (plugins.yml への登録は不要)

1. `AutoUpdater-<version>.jar` を `plugins/` に配置して起動するだけで、
   インストール済みの全プラグインが自動的にチェック対象になります。
   更新元は次の優先順位で決まります:

   1. `plugins/AutoUpdater/plugins.yml` への明示登録 (任意。精度を上げたい場合のみ)
   2. 各プラグイン自身の `plugin.yml` に書かれた `autoupdate:` セクション (開発者による自己申告)
   3. プラグイン名から Modrinth の slug を自動推測 (例: `EssentialsX` -> `essentialsx`)

   3 で解決できないプラグインはステータスが `ERROR` になるだけで、他のプラグインには影響しません。
   自動検出自体を止めたい場合は `config.yml` の `auto-update.auto-discover-unlisted: false` にしてください。

2. GitHub Releases や Spigot(resource-id) は自動推測できないため、それらを使いたい場合や、
   Modrinth の slug 推測がうまくいかない場合は `plugins.yml` に手動登録して上書きします:

```yaml
plugins:
  Jobs:
    enabled: true
    source: github
    repository: "yourname/Jobs"

  ViaVersion:
    enabled: true
    source: spigot
    resource-id: 19254
```

   あるいは自作プラグインの `plugin.yml` に直接埋め込むこともできます:

```yaml
autoupdate:
  source: github
  repository: "yourname/Jobs"
```

3. 必要に応じて `config.yml` を調整 (チェック間隔、自動ダウンロード可否、通知、セキュリティ設定など)
4. `/autoupdate reload` または再起動で反映

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
