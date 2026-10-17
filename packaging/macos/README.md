# DuoBuddy Mac distribution

Build a self-contained early tester app on macOS with Xcode command line tools:

```sh
bash outputs/tv-companion/build.sh
python3 packaging/macos/build.py
```

The result is `dist/DuoBuddy-0.5.0-beta.1-mac-universal.zip`, with a checksum alongside it. Extract it and drag `DuoBuddy.app` to Applications. macOS 12+ and both Intel and Apple Silicon are supported by the binaries. Intel hardware still needs a physical launch test.

The build uses official Node 22.23.3 archives with pinned SHA-256 checksums, the local verified universal ADB 37.0.1 binary and its notices, and an explicit allowlist of controller files. It includes the latest signed TV APK and checks its version against the manifest. It never copies the developer's runtime directory, credentials, household notes, ADB identity, SSH files, voice models or APK signing keystore. Bundled speech uses macOS; the private Piper adapter is replaced with a disabled stub. No SDK or Node installation is required on a tester's Mac.

The app runs as a menu bar accessory, opens setup in the default browser and prevents idle sleep while its own controller runs. Quit stops that controller and releases the sleep assertion. Its two ports are 18765 (paired TV API, LAN) and 18766 (setup, loopback), so it can coexist with the source controller's default ports. New users start with no selected TV and no API key. App settings live in `~/Library/Application Support/DuoBuddy` with owner-only permissions, separately from source-checkout settings. Replacing the app preserves them.

## Verification

```sh
node --test outputs/tv-companion/tests/*.test.mjs
```

Run the actual packaged launcher without opening a browser or contacting a TV:

```sh
"work/mac-distribution/DuoBuddy 0.5.0-beta.1/DuoBuddy.app/Contents/MacOS/Buddy" --smoke-test /tmp/duobuddy-smoke-test
```

Use a fresh temporary directory. This starts and stops the bundled Node controller, checks its PID-specific readiness file, uses built-in Mac speech, and deliberately supplies a nonexistent ADB executable. The launch must succeed without a selected TV. Inspect `controller.log` in the temporary directory for diagnostics.

## Signing and publishing

The default build is ad-hoc signed and **not notarized**. A downloaded copy may need macOS's Privacy & Security → Open Anyway flow. It is suitable for a small trusted tester group; wider distribution needs a Developer ID certificate and notarization.

The same script supports both through existing Keychain credentials:

```sh
BUDDY_SIGNING_IDENTITY='Developer ID Application: YOUR NAME (TEAMID)' \
BUDDY_NOTARY_PROFILE='YOUR_KEYCHAIN_PROFILE' \
python3 packaging/macos/build.py
```

Node is signed with JIT entitlements; binaries and the app get hardened runtime signatures and timestamps for Developer ID builds. The script submits the archive, waits for acceptance, staples the app, then recreates the ZIP. It does not publish anything.

For an update, change `outputs/tv-companion/release.json` to the new semantic version, increment the Android version if its code changes, rebuild and validate. Publish the ZIP and checksum as assets of a stable public release at `eonurk/DuoBuddy`, with a matching tag such as `v0.5.0`. Update checks are user initiated and use GitHub's latest stable release. They accept only the repository's matching Mac ZIP asset. The setup page provides a download link and replacement instructions; it does not execute downloaded code. Once the Mac app is replaced, the existing Install / update button installs its bundled TV APK without clearing TV data. No public release has been created by this task.
