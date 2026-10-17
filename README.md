# DuoBuddy

The project source lives in [`outputs/tv-companion`](outputs/tv-companion), including the Node controller, setup page, Android app and tests.

Double-click `Start DuoBuddy.command` to run the controller from this folder. Open the local setup link it prints.

See the [project documentation](outputs/tv-companion/README.md) for TV connection, pairing, building and verification.

For a shareable Mac app with Node, ADB and the TV app bundled, see [Mac distribution](packaging/macos/README.md). Tester builds use the Mac's built-in voice and fresh per-user settings, with no dependency on the developer's other machines.

The `work` directory contains local SDKs, build products, backups and private runtime settings. It is excluded from Git. The original workspace is preserved at `/Users/onur-lumc/Documents/Codex/2026-10-17/the-x20.backup-before-desktop-move`. Its former path now points here so existing sessions use the same source files.

