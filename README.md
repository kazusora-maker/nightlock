# 21:30ロック

毎晩 21:30 に Android スマホの全てのアプリをロックし、パスワードか顔認証で解除するまで使えなくするアプリです。
電話・緊急通報・アラームはロック中も使えます。

- **共有用のページ（これ1つでOK）：https://kazusora-maker.github.io/nightlock/**
- スマホへの入れ方：[スマホ設定ガイド.md](スマホ設定ガイド.md)（ChatGPT などに貼って案内してもらえます）
- ダウンロード：[Releases の最新版](../../releases/latest) の `nightlock.apk`

## ビルド
`main` にプッシュすると GitHub Actions がビルドし、Releases の `latest` に APK を置きます。
署名の鍵はリポジトリの Secrets（`KEYSTORE_BASE64` / `KEYSTORE_PASSWORD`）に入れます。鍵が無いとテスト用の署名になります。
