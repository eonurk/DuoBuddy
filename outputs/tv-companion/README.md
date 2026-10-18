# DuoBuddy for Android TV

DuoBuddy runs on the TV. Install the APK, choose **Model**, and connect your own OpenAI or Google Gemini API key. No Mac, computer, Node server, ADB connection, or separate speech app is needed to run it. AI replies require internet access and your provider account; provider usage may cost money.

## Install and connect

1. Download **DuoBuddy-TV-1.0.0-beta.1.apk** from [GitHub Releases](https://github.com/eonurk/DuoBuddy/releases). Sideload it on an ARM Android TV / Google TV running Android 8 or later. Allow installation from your chosen installer if Android asks.
2. Open DuoBuddy and choose **Model**. Pick **OpenAI · GPT-6 Luna** or **Google · Gemini 3.1 Flash-Lite**, then **Set up on phone**.
3. Connect your phone to the TV’s Wi-Fi, scan the TV’s QR code, and paste your provider API key. The TV hosts a temporary HTTPS page. Its certificate is local and self-signed: compare the certificate’s SHA-256 fingerprint with the TV before trusting it. You can also choose **Enter key on TV**.
4. Confirm **Save & connect** on the TV. After setup, close the phone page and choose **Done**. Your phone is no longer needed.
5. Choose **Talk** and allow microphone access, or choose **Type** to enter a request. If your remote supports choosing a default assistant, select DuoBuddy in **Model → Settings → Default voice assistant**. Some devices, including the Mi Box 4, send the microphone button straight to Google's assistant. There, turn on **TV control** (Android Accessibility): DuoBuddy then closes Google's voice window and listens in its place. Talk remains available.

Keys are encrypted in app-private storage using Android Keystore. Each provider has its own saved key. The model picker switches between saved providers; the phone form also accepts a custom model ID. Keys go only to the chosen provider and are never bundled in the APK, QR, logs, or GitHub release. Uninstalling clears local data and requires entering keys again.

## What runs on the TV

- Direct HTTPS AI requests, tool execution, short conversation context, household/person notes, settings, and alarm-backed timers.
- Embedded sherpa-onnx / Piper Amy Low speech, with optional downloadable voices. Speech synthesis works offline. Recognition uses Android’s installed speech recognition service and may require internet.
- Installed-app discovery, launch/search links, media commands and volume. Optional **TV control** in Android Accessibility enables navigation, typing into supported non-password fields, and reading accessible screen text.
- Appearance, character customization, captions and a local clock/timer screensaver. Optional overlay permission shows DuoBuddy over other apps.

Examples: “Open YouTube”, “Pause”, “Volume up”, “Set a timer for 5 minutes”, “Remember that we like subtitles”, “What is a black hole?” Exact simple media, volume and numeric timer commands work without an AI key.

## Beta limits

The Xiaomi Mi Box 4 on Android 9 is the physical test device. ARMv7 and ARM64 libraries are included; ARM64 and other TVs still need hardware testing. Android permissions and individual apps determine how much control works. Reading uses accessible text, not screenshots or protected video. Playback title metadata, arbitrary remote key injection, waking a sleeping TV, and the legacy controller’s screenshot navigation are unavailable. Search links may open without playing a result.

OpenAI web search is optional; Gemini web search is not enabled. The standalone screensaver has no weather feed or automatic reactions. This beta does not provide a wake word. Timers persist across ordinary process restarts and reboot, but Android force-stop, disabled permissions, or manufacturer power restrictions can prevent alarms until the app is reopened.

## Source and verification

Build from the repository root with `bash outputs/tv-companion/build.sh`. The current build uses the local Android/JDK toolchain under `work/build-tools`; speech and QR dependency downloads are checksum pinned. The APK contains no Node runtime or Mac controller. `node --test outputs/tv-companion/tests/*.test.mjs` runs the host regression suite. `test-native.sh` builds a separate on-device instrumentation APK; it is not part of the release APK.

See [embedded speech and dependency notices](EMBEDDED-SPEECH.md). The bundled pronunciation stack includes GPL-3.0 code; retain its licenses and corresponding-source information when redistributing. [Legacy controller documentation](LEGACY-CONTROLLER.md) is retained for earlier builds.
