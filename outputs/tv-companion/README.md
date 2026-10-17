# DuoBuddy — DuoBuddy for Android TV and Google TV

A native companion for Android TV and Google TV (Android 8 or later), with the original Duobook book buddy. It blinks, listens, looks down while thinking, and animates its mouth during spoken replies. **Style** changes its cover colour, hat, eyewear, neckwear and face details. Seasonal outfits appear there as optional suggestions and only apply when you choose **Use outfit**. **Appearance** (top right) offers **Follow TV**, **Light**, and **Night**; the choice is saved on this TV and also applies to dialogs, overlay captions, and screensaver speech bubbles. Follow TV uses the TV’s system light/dark setting, with light as the fallback. Fresh replies stay visible while DuoBuddy speaks and for eight seconds afterwards (12 seconds without speech), then the welcome message returns; opening the app does not restore an old reply. A small buddy stays visible over other apps and shows live captions of what it hears, what it is doing and what it says. The Xiaomi Mi Box 4 is the hardware verified so far; other devices still need physical testing.

The Mac runs the OpenAI conversation and TV controller. The TV handles its interface, speech recognition and audio playback. Piper 1.8.0 on M1-2 produces the speech audio (default voice `en_US-hfc_female-medium`; your saved choice takes precedence). There is no wake word: press and release the remote's microphone button, wait for **Listening**, then speak.

## What DuoBuddy can do

Say things like “Play lo-fi on YouTube”, “Pause”, “Next episode”, “Volume to 30 percent”, “What's playing?”, “Open Plex”, “Find Paddington on Stremio”, “Set a ten minute timer for pasta”, “What's the weather tomorrow?”, “Remember that Mum likes subtitles on”, “Go to sleep”.

| Ability | How it works |
| --- | --- |
| **Opens any installed app** | The TV's launcher apps are discovered over ADB (29 on this box), so nothing needs hard-coding. Known apps get friendly names and aliases (`apps.mjs`). Sideloading/installer apps are off limits. |
| **Searches inside apps** | Deep links for YouTube, Netflix, Spotify, Prime Video and the Play Store; Stremio uses its verified sidebar search. Other apps are opened and navigated with the remote and screenshots. |
| **Controls playback** | Play, pause, next, previous, rewind, fast-forward, stop, plus repeated remote keys (menus, scrolling), sleep and wake. |
| **Sets exact volume** | Absolute level in percent, steps, mute and unmute that restores the old level. |
| **Knows what is happening** | Every request starts with a live snapshot: foreground app, media session (what is playing or paused, and its title) and volume, plus time, installed apps, timers and notes. |
| **Opens links** | `https` links open in whichever app handles them. |
| **Timers and reminders** | DuoBuddy speaks when one fires. Timers live while the controller runs. |
| **Memory** | “Remember/forget …” notes saved on the Mac (`work/tv-runtime/memory.json`), shown and editable on the setup page. Notes are passed to the model as data, never as instructions. |
| **Answers questions** | Optional hosted web search for news, weather, scores and facts. If the chosen model rejects it, DuoBuddy turns it off and carries on. |
| **Feels instant** | Pre-rendered spoken acknowledgements (“On it.”) play while a slower request runs. |
| **Conversation mode** | If DuoBuddy ends with a question, the microphone reopens by itself. Toggle it on the setup page. |
| **Stays out of the way** | The buddy and its captions appear only while DuoBuddy listens, thinks, acts or speaks, and disappear about 1.5 s after it stops talking (0.4 s if a video or music is playing). Keeping it on screen while browsing is an opt-in on the setup page and still hides during playback. |
| **Knows who's watching** | “It's Mum” switches to Mum's profile: her own notes and birthday, plus the household's. After 45 quiet minutes DuoBuddy goes back to the household. Manage people on the setup page. |
| **Calm screensaver** | Clock, weather (Open-Meteo, town set on the setup page), a word of the day in the family's learning language, today's timers and notes, with DuoBuddy in the corner: sleepy at night, stretching in the morning, drifting slightly against burn-in. Say “screensaver mode”, or pick **DuoBuddy** under Settings → Device Preferences → Screen saver. |
| **Seasonal outfit ideas** | Style offers a pumpkin or witch hat with a spooky scarf for Halloween (15–31 Oct), a Santa hat for Christmas, a party hat at New Year and on family birthdays, and hearts on Valentine's Day. Nothing changes until you choose **Use outfit**. You can keep or change that look like any other Style choice, or turn suggestions off on the setup page. |
| **Reacts to what's on** | When a new title starts, DuoBuddy pops in for a few seconds: headphones and a groove for music, peeking from under a blanket for horror, a laugh for comedy, a team scarf for a match. During football it follows the score (ESPN's public scoreboard), cheers at goals and sulks if the family's team concedes (“Remember I support PSV”). Unclear titles are classified by the model once each. |
| **Listens in place** | The mic button listens over whatever you are watching; the video keeps its place. A second press ends the utterance. |

The assistant works through a fixed set of tools (`tools.mjs`) with validated arguments; it cannot run arbitrary shell commands. Screenshots, web results, app content and saved notes are treated as untrusted data. DuoBuddy will not enter credentials, create accounts, buy, subscribe, install or remove apps, or change system settings; sign-in pages, activation codes and paywalls are described for you to handle.

## Start and connect

1. Double-click `Start DuoBuddy.command` (or run `start.command`) on the Mac if the controller is not running. Its terminal prints a local setup link.
2. On the TV, enable Developer options (select its Android build number seven times), then USB / network debugging or Wireless debugging. Keep the TV and Mac on the same network.
3. Open the local setup link and use **Connect your TV**. For network debugging, enter the TV IP address (the default port is 5555) and accept the authorization prompt on the TV. For Wireless debugging, enter its connection address and port. If it needs pairing, open **Pair device with pairing code** on the TV and enter that screen's separate pairing address, port and six-digit code in the setup page. The pairing port and connection port differ.
4. Select **Connect TV**, then **Install / update DuoBuddy on this TV**. Installation grants microphone and caption-overlay access and opens DuoBuddy with this Mac's callback address and a pairing token. It keeps existing DuoBuddy app data. Use **Use DuoBuddy as the TV screensaver** if desired, including on Google TV where third-party screensavers may be hidden in Settings.
5. Enter your OpenAI API key and choose **Save & connect**. The default model is `gpt-6-luna`; the field accepts another compatible vision/tool-calling Responses model. On the TV, try Talk or Type.

The TV address and optional remote name are remembered across controller restarts. Leaving the remote name blank enables automatic detection; an entered name restricts monitoring to devices with that name (case-insensitive). `TV_DEVICE` and `TV_REMOTE_NAME` override saved choices on startup. `TV_ADB` can point to another ADB executable. Wireless debugging connection ports can change after a reboot; update the address and reconnect if needed. Some devices require a USB debugging connection before network ADB can be enabled, and some firmware blocks remote input monitoring or does not include a speech recognition engine. Typing and direct controls remain available when those voice features are unavailable.

The controller Mac and M1-2 must stay awake, on the same network as the TV. The controller opens an SSH tunnel to `m1-2` and starts or reuses a Piper worker there, which listens only on M1-2's loopback interface. No API key is sent to M1-2 or to the TV. The start script keeps the Mac awake; close it with Ctrl-C. Nothing is installed as a login service.

### Setup page

TV address, wireless pairing, app installation, screensaver selection, voice choice and preview, assistant toggles (conversation mode, web search), remote-button toggle, a live “TV right now” panel, an app grid generated from the TV, play/volume buttons, running timers, saved notes, and a screenshot viewer. The direct controls work without an API key.

## How the remote microphone button works

The Mac controller discovers inputs with `KEY_VOICECOMMAND`, `KEY_ASSISTANT` or a remote's `KEY_SEARCH`, and responds only when that key is released; other events are discarded without being recorded. It prefers dedicated microphone keys and remote-like names. On the real Mi Box this selects **Xiaomi RC**, with the IR keypad as a backup if the Bluetooth remote disappears. A name entered on the setup page restricts discovery to that name. Android TV can start Google's assistant before third-party handlers, so the controller closes Google's button session, then tells DuoBuddy's service to listen. The service uses the TV's speech recogniser, shows captions in the overlay, and sends the text to the controller. Remote microphone routing and assistant interception vary by firmware and still need testing on Google TV and Shield hardware.

The service accepts the controller's `LISTEN` command because it is exported but protected by `android.permission.DUMP`, which `adb shell` holds and ordinary apps cannot obtain. If overlays are not allowed, DuoBuddy falls back to opening its full screen. Older installed builds (reporting no version) use that full-screen path automatically. Stopping the controller returns the mic button to Google's normal behaviour. No system app is disabled or removed.

## Local data and API use

Common exact English controls such as “pause”, “resume”, “volume up”, “mute” and “set the volume to 40 percent” run locally with zero model calls. Other phrasing, questions and compound requests use the assistant. During navigation only the latest screenshot is resent; older screens become text placeholders. The controller logs input, cached and output tokens for both conversations and background model requests (title reactions and word of the day).

The API key is stored only on the Mac in `work/tv-runtime/credentials.json` (owner-only). The TV stores a random pairing token, not the key. The key-entry endpoint binds only to `127.0.0.1`. Conversation text, requested TV screenshots and web-search queries go to OpenAI and are billed to your account. Requests are sent with `store: true`, so conversations (including any TV screenshots) appear in the OpenAI platform Logs for the key's project. Local conversation history is held in memory and cleared on restart; notes persist until forgotten. The TV and controller talk over plain HTTP on the home network with a bearer token.

## Layout

| File | Purpose |
| --- | --- |
| `server.mjs` | HTTP endpoints, conversation loop, speech, remote button, timers |
| `tools.mjs` | Tool schemas, validation and execution |
| `tv.mjs` | ADB controller, parsers for focus, media sessions and volume |
| `apps.mjs` | App catalogue, aliases, search deep links, URL validation |
| `persona.mjs` | DuoBuddy's instructions and live context |
| `timers.mjs`, `memory.mjs` | Timers; household and per-person notes, birthdays |
| `seasonal.mjs`, `ambient.mjs`, `reactions.mjs` | Seasonal outfits; screensaver weather and word of the day; reactions and live scores |
| `android/` | TV app: service (mic, speech, overlay), activity, mascot view |

`DuoBuddy-TV.apk` (v0.6) is the signed, locally built app; `build.sh` rebuilds it with the Java 17, Android platform 28, build tools 35 and AndroidSVG tools in `work/build-tools`. The previous build is kept as `work/DuoBook-TV.previous.apk`. `server.mjs` uses Node 20 with no npm dependencies. Run checks with `node --test tests/*.test.mjs` (56 tests). Artwork is rendered from the original Duobook `PracticeBuddy` SVGs (`android/assets/buddy`) with [AndroidSVG 1.4](https://bigbadaboom.github.io/androidsvg/) (Apache 2.0). Piper voices come from the [Piper voice repository](https://huggingface.co/rhasspy/piper-voices); HFC female is CC BY-NC-SA 4.0 per its model card.

The Piper worker runs under `/Users/eonurk/Documents/Codex/tv-companion-piper` on M1-2 in a dedicated virtual environment, loading selected voices on demand (two cached).

## Known limits

- Stremio 1.11.2 has a search deep-link bug ([upstream](https://github.com/Stremio/stremio-bugs/issues/2833)), so DuoBuddy uses the sidebar search with coordinates checked on this box; a redesign may need updating `stremioSearch` in `tv.mjs`.
- YouTube on this box currently shows its sign-in screen; DuoBuddy will describe it but not sign in. Netflix, Prime Video and Plex search links are untested here. Spotify's search link opened the app, which then resumed playback by itself.
- Playback depends on the apps, subscriptions and Stremio configuration already on the TV.
- Timers are lost when the controller restarts. The UIAutomator tree is not used because it takes about 7 seconds on this box; DuoBuddy relies on screenshots.
- Speech recognition uses Google's engine on the TV, so it is English-only (`en-US`) for now.

## Verification status

- Night appearance: built and signature-verified; installed on the Mi Box. Checked the dark home screen, Appearance picker and remote focus, typing dialog, and persistence after stopping and reopening the app. Light and Follow TV remain available from Appearance.
- Unit and integration tests: 56 pass, including wireless pairing and connection validation, strict remote selection, modern volume fallback, installing and configuring a selected TV with fake ADB, failed connection recovery, and restoring the saved TV after restart.
- On the real Mi Box: v0.6 installed; model identity, app launcher list and live status (foreground app, paused Spotify track, volume) correct; Spotify search link opens the app; overlay caption shows **Listening…** over the TV home screen from the controller's `LISTEN` command, with speech input starting and timing out cleanly.
- OpenAI API accepts all 14 strict tool schemas plus hosted web search; for “Set a one minute timer called tea” the model called `timer` with the right arguments.
- Remote button: the controller's watcher used to receive nothing, because `getevent` block-buffers its output without a terminal on Android 9; it now runs with `adb shell -tt`. A kernel-level `sendevent` of `KEY_VOICECOMMAND` on `/dev/input/event3` now makes the controller react and DuoBuddy start listening within about a second. (`sendevent` does not wake Google's assistant, so the Google flash on a real press is not reproduced by it.)
- Voice fallback: if M1-2 cannot be reached, DuoBuddy speaks with this Mac's own voice (`say`, preferring Samantha) so it is never mute, decides instantly instead of waiting out SSH timeouts, and switches back to Piper by itself when M1-2 returns (checked every 30 s). “On it.” uses whichever voice is active. Previously a dead M1-2 meant only the cached “On it.” clip was ever heard.
- Spotify: its TV app only fills the search box from a link; `search_app` now waits for the app, presses Enter (which plays the best match) and waits until playback registers, then reports what is playing. Requests are capped at 10 steps, and hitting the cap now says what is playing.
- Failure handling: an error is now spoken (never a silent replay of the previous clip, which looped “On it.” after a failed request). Dropped connections to OpenAI are retried and logged with their cause in the controller terminal; tool names (not speech) are logged too. Screenshots are sent as JPEG, and conversation mode will not re-listen while media is playing or after two automatic re-listens in a row.
- Live check on the Mi Box: “What is on the TV screen?” (8 s) and “can you open Daft Punk on Spotify” (open, search, look, check status, spoken answer in 14 s) both completed cleanly.
- Not yet verified end to end: a real press of the physical remote button followed by speech and a spoken answer (whether DuoBuddy hears the remote's microphone after Google's session is closed), conversation-mode re-listening, spoken acknowledgements, and multi-step navigation of Netflix/Prime/Plex.
