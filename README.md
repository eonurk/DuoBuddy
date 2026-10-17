# DuoBuddy

The project source lives in [`outputs/tv-companion`](outputs/tv-companion), including the Node controller, setup page, Android app and tests.

Double-click `Start DuoBuddy.command` to run the controller from this folder. Open the local setup link it prints.

See the [project documentation](outputs/tv-companion/README.md) for TV connection, pairing, building and verification.

For a shareable Mac app with Node, ADB and the TV app bundled, see [Mac distribution](packaging/macos/README.md). Tester builds use fresh per-user settings. TV app v0.7 bundles Piper Amy and generates speech on the TV; older TV apps retain the Mac voice fallback.

The `work` directory contains local SDKs, build products, backups and private runtime settings. It is excluded from Git. The original workspace is preserved at `/Users/onur-lumc/Documents/Codex/2026-10-17/the-x20.backup-before-desktop-move`. Its former path now points here so existing sessions use the same source files.

