# Offline voice tests on the Mi Box

This is an isolated Android experiment, not a change to DuoBuddy's speech engine.
The connected MIBOX4 has Android 9 / API 28, a 32-bit ARM userspace
(`armeabi-v7a`) and approximately 2 GB RAM. The lab app has no microphone,
network, storage or system permissions. Its WAV files and JSON results are saved
to its own external-files directory and pulled back to `outputs/voice-lab/`.

The official Sherpa-ONNX model documentation links the Android test APKs used here:

- [Kitten Nano 0.8 INT8](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/kitten-nano-en-v0_8-int8.html)
- [Kitten Nano 0.8 FP32](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/kitten-nano-en-v0_8-fp32.html)
- [Kitten Micro 0.8](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/kitten-micro-en-v0_8.html)
- [Piper Amy Low](https://k2-fsa.github.io/sherpa/onnx/tts/all/English/vits-piper-en_US-amy-low.html)

The APKs are cached locally under `work/voice-lab/downloads/{nano,micro,amy}.apk`.
Publisher certificate SHA-256:
`030a90bdd6e4c5eef1e161acfa6455a912c45cb2423b64df449ca88fdee097bb`.
The harness verifies signatures before installing. File hashes and model-source
links are recorded with the results. No developer keys or household data are
copied into the lab.

## Build and run

The existing project JDK 17, Android API 28 jar, D8 and signing tools are used:

```sh
bash experiments/voice-lab/build.sh
python3 experiments/voice-lab/run.py --models nano,micro,amy --play
```

That route explicitly binds Android TextToSpeech to the test engine without
changing `tts_default_synth`. Each model APK has the same package name, so the
engine is replaced between models. The original foreground activity is restored
after each run. Do not run two harnesses against the same TV simultaneously.

## Native diagnosis and comparison

The stock Kitten Android engine returned empty WAVs even though it spent time
in inference. To distinguish an Android speech-service problem from model
behavior, the native lab reuses only the verified publisher's DEX dependencies,
32-bit native libraries and model assets, plus our small benchmark Activity.
It installs under `co.duobook.voicelab`, with no publisher app components or
permissions. These locally signed APKs are diagnostic builds, not release apps.

The Android libraries need pronunciation supplied through the separate Piper
phonemizer. `DirectVoice` initializes it and sets `GenerationConfig`'s phoneme
codepoints explicitly. It saves the returned `GeneratedAudio` rather than relying
on a streaming callback. This produced valid Kitten Nano audio where the stock
Android service had returned empty files. The first direct diagnostic, before
phonemizer setup was added, also returned empty audio; retain it as a failed run.

```sh
python3 experiments/voice-lab/bundle-native.py nano micro amy
python3 experiments/voice-lab/run.py --models nano --direct --quick --speaker 1 --threads 2 --play
```

For the FP32 Nano comparison, download the official
`kitten-nano-en-v0_8-fp32.tar.bz2` release archive into
`work/voice-lab/downloads/nano-fp32.tar.bz2`, then:

```sh
python3 experiments/voice-lab/bundle-native.py nano-fp32
python3 experiments/voice-lab/run.py --models nano-fp32 --direct --quick --speaker 1 --threads 2 --play
python3 experiments/voice-lab/run.py --models nano-fp32 --direct --quick --speaker 1 --threads 4 --reuse --memory-interval 4
```

`--reuse` checks the installed APK's SHA-256 before skipping installation. The
same short text is used in all comparisons; output duration varies by voice.
Kitten speaker 1 is a female voice. Piper Amy has a single speaker, index 0.

## Reading the results

- `initMs`: startup/weight loading, including phonemizer setup when needed.
- `synthesisMs`: total time to generate and save the complete WAV.
- `firstAudioMs`: first nonempty streaming callback; `-1` means none was observed.
  Kitten's returned WAV can still be valid when its callback is never invoked.
- `audioSeconds`: measured from the generated WAV's sample data and byte rate.
- `realTimeFactor`: generation time divided by audio duration; below 1 is faster
  than playback. Full-file playback in this diagnostic waits for synthesis.
- `sampledPeakEnginePssKiB`: sampled process PSS, not a true instantaneous peak.
  It covers the speech-engine process for Android TTS, and the combined lab UI
  plus native engine for direct mode. These are not identical memory scopes.
- Memory sampling uses `dumpsys meminfo`, which requests Java GCs on this device
  and adds measurement overhead. `--memory-interval` controls its frequency.

The first runs were made with the lab in front, not during Netflix/4K playback.
These are exploratory measurements, not a claim of production latency or
cross-device compatibility. Integration needs testing during normal video use.

Diagnostic logcat files can include unrelated device logs. Review them before
sharing. The harness leaves the default speech setting and Buddy's app data
unchanged; it does not update the production companion or Mac package.
