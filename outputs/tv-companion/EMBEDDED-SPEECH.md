# Embedded TV speech

TV app v0.7 embeds **Piper Amy Low** (`en_US-amy-low`, mono 16 kHz) as the fast default. **Voice** in the TV header offers Lessac Medium, Amy Medium and Lessac High as optional downloads (about 63, 63 and 114 MB respectively, all 22.05 kHz). The choice is saved on the TV; it can be previewed, switched back to Amy Low, or removed to recover storage. It runs through the sherpa-onnx native runtime in the Buddy foreground service. The default voice needs no separately installed speech engine, TTS API, Mac voice or first-run model download. Optional voices download directly to the TV once, then synthesize offline. In standalone v1.0.0-beta.1, model requests and TV control also run on the TV. Speech recognition still uses the TV’s configured recognition service. Historical notes below describe earlier controller builds.

## Build and provenance

Run `bash outputs/tv-companion/build.sh` from the workspace root. `bundle-piper.py` downloads missing build dependencies into the ignored `work/piper-runtime` cache (or reuses the earlier verified Voice Lab downloads), verifies SHA-256 and the publisher certificate, and embeds the model, pronunciation data, runtime DEX and native libraries. Only Buddy's manifest and components are registered. Kotlin's compile-time library is separately pinned and is not duplicated in the APK. The existing AndroidSVG dependency is unchanged.

The APK supports `armeabi-v7a` and `arm64-v8a`. The Mi Box uses the first; ARM64 is bundled and signature-checked but still needs a physical test. The model adds about 63 MB of uncompressed weights. The complete signed APK is about 95 MB, including both native runtimes and pronunciation data.

Dependency APKs are the official `csukuangfj2/sherpa-onnx-apk` v1.13.8 Amy Low Android engines. SHA-256 checksums and download addresses are pinned in `bundle-piper.py`. Publisher certificate SHA-256:

```
030a90bdd6e4c5eef1e161acfa6455a912c45cb2423b64df449ca88fdee097bb
```

Full license notices are shipped in `android/assets/licenses/piper/`; the upstream voice MODEL_CARD and model JSON are preserved alongside the model. The pronunciation dependency includes GPL-3.0 code. The exact corresponding source/license obligations must be retained when distributing these dependencies; this is not an exclusively Apache/MIT bundle.

## Runtime behavior

- One engine loads off the main thread when the service starts and stays warm for later replies. It uses two inference threads at normal CPU priority. Switching voices releases the previous engine before loading the new one, so several models are not retained in memory.
- Optional downloads use immutable upstream revisions and pinned model SHA-256 checksums. Models are streamed to private temporary files, size/hash checked, then moved atomically into place. Cancellation, truncation and corruption preserve the previous selected voice; retries remove partial files. Download size and removal are shown in the TV picker. A fresh install uses bundled Amy Low.
- Pronunciation runs locally and supplies phoneme codepoints explicitly to the model.
- A concrete `PiperCallback.invoke(float[]) -> Integer` method is required by JNI. A generic reflection Proxy lacks that signature and can abort the process; the callback signature has a regression check.
- AudioTrack plays PCM as native sentence chunks arrive. If a wrapper returns audio without callbacks, the app plays the returned samples instead. Empty output is an error.
- Stop, a new speech turn, microphone activation and service destruction invalidate stale native/UI callbacks. Queued speech is replaced, not accumulated. Audio focus is requested only when audio is ready and released on completion, interruption or Stop.
- Captions remain during loading/playback, and follow-up listening starts only after the final samples finish playing.
- The controller detects TV v0.7+ through the existing version header and sends `speechText`, with no generated WAV. This also covers acknowledgements, timer announcements and voice previews. Older TV builds retain the existing Mac/remote audio path. New TV builds can synthesize final reply text from older controllers, although those older controllers will still spend time preparing their remote audio.
- The first controller snapshot records its reply ID without replaying a previous conversation.

## Reply delivery (TV v0.8)

- The TV holds an authenticated state request open for up to 20 seconds. A revision change returns the new state immediately and the TV reconnects, without the previous 1.3-second delay. A dedicated, cancellable connection leaves chat and Stop requests free. Older controllers retain polling; older TV builds retain ordinary state GETs.
- A controller session ID makes reconnects distinguish a restarted controller from an unchanged revision. Disconnects and heartbeats release held requests; connection failures retry after 1.3 seconds.
- The controller refreshes TV context in the background every five seconds while idle. Ordinary chat uses a snapshot no more than ten seconds old, or explicitly reports missing context to the model. TV tools continue to read current state when required; changing TVs clears cached context.
- New TVs check whether playback permits follow-up listening after speech finishes. The check is tied to the reply ID and fails closed on missing TV state, a new request or Stop. It no longer delays publishing the spoken reply. Older TV builds keep the check before reply publication.
- `tests/reply-latency.test.mjs` verifies that a simulated 2.2-second status read does not block an ordinary reply, along with authenticated delivery, restart recovery and follow-up cancellation. Model and voice calls in this test are local fixtures.

## Verification

TV v0.10 adds **Model** beside Voice and Appearance. Its remote-friendly picker marks the active model, lists GPT-6 Luna and Gemini 3.1 Flash-Lite, and retains an already saved custom model. Providers without a saved key are marked **key needed**; selecting one explains the missing setup and leaves the active model unchanged. Selection uses paired TV endpoints, reuses the matching provider's saved credential, validates model access without a paid generation and persists only on success. No key is returned to the TV. This is the model-picker change only; the existing controller still runs the conversation loop. Provider adapters keep OpenAI Responses behavior and translate Gemini tool calls, tool results, screenshot inputs and background JSON responses, retaining Gemini's signed tool-call metadata. Gemini web search is not enabled in this build. Official API references: [OpenAI Luna](https://developers.openai.com/api/docs/models/gpt-6-luna), [Gemini compatibility](https://ai.google.dev/gemini-api/docs/openai). All 82 controller tests passed, including provider switching, persistence, unavailable keys and signed Gemini tool round trips using local API fixtures. A live Gemini request still requires a user-supplied Google API key.

TV v0.9 also keeps a request open across premature recognition results. The initial speech window is 12 seconds (10 seconds for a follow-up), with the existing 30-second session bound. Completed segments are retained while a healthy recognizer is reused for continuation, and the combined request is submitted after a 2.5-second quiet window following the last final segment. New speech and partial results postpone submission until the engine finishes that segment. Talk again explicitly finishes without the continuation delay. Empty retries, stale callbacks, shutdown and timeout remain bounded and covered by `ListeningSessionTest.java`. The recognizer receives longer silence hints as well, but the continuation behavior does not rely on it honoring them ([Android recognition extras](https://developer.android.com/reference/android/speech/RecognizerIntent#EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS)).

Run `node --test outputs/tv-companion/tests/*.test.mjs` from the workspace root. The tests cover atomic model installation, corruption/truncation/cancellation, pinned catalogue metadata, speech cancellation, concrete JNI callback signature, v0.7 text reply negotiation, Stop clearing text, older-TV compatibility, pairing and the existing controller behavior.

Device playback measurements and final APK checksum are recorded after the physical smoke test. They are exploratory observations, not latency guarantees under simultaneous video playback.

On 2026-10-18, v0.8 was rebuilt, signature-verified and installed on the Mi Box, and the source controller was restarted with its existing configuration. All 76 tests passed. One live short greeting with the selected Amy Low voice produced reply text in 1,567 ms, a matching TV receipt log at 1,606 ms, and the first audio log at 4,018 ms from submission (2,366 ms of voice preparation). These are Mac-observed timings from one request, including log transport, rather than a controlled before/after benchmark. Timing evidence is in the ignored `work/reply-speed-smoke.json` and `work/reply-speed-controller.log` files.
