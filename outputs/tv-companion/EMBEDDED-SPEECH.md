# Embedded TV speech

TV app v0.7 embeds **Piper Amy Low** (`en_US-amy-low`, mono 16 kHz) as the fast default. **Voice** in the TV header offers Lessac Medium, Amy Medium and Lessac High as optional downloads (about 63, 63 and 114 MB respectively, all 22.05 kHz). The choice is saved on the TV; it can be previewed, switched back to Amy Low, or removed to recover storage. It runs through the sherpa-onnx native runtime in the Buddy foreground service. The default voice needs no separately installed speech engine, TTS API, Mac voice or first-run model download. Optional voices download directly to the TV once, then synthesize offline. The Mac still runs the conversation model and TV controller; speech recognition still uses the TV's configured recognition service.

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

## Verification

Run `node --test outputs/tv-companion/tests/*.test.mjs` from the workspace root. The tests cover atomic model installation, corruption/truncation/cancellation, pinned catalogue metadata, speech cancellation, concrete JNI callback signature, v0.7 text reply negotiation, Stop clearing text, older-TV compatibility, pairing and the existing controller behavior.

Device playback measurements and final APK checksum are recorded after the physical smoke test. They are exploratory observations, not latency guarantees under simultaneous video playback.
