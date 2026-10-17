# Mi Box offline speech comparison

Measured on the physical Xiaomi MIBOX4 (Android 9 / API 28, 32-bit ARM, about 2 GB total RAM).

All clips below were generated on the TV box without a speech API or Mac synthesis. Each uses the same text:

> Hi! I'm your DuoBuddy. Shall we find something good to watch?

The table selects the fastest valid **warm** short run for each model. Model loading is excluded; initial startup took approximately 8–19 seconds in these tests. These are individual exploratory measurements, not statistical latency guarantees.

| Model | Threads | Full-file generation | Clip length | Generation / audio | Sampled process PSS |
| --- | ---: | ---: | ---: | ---: | ---: |
| Kitten Nano INT8 | 2 | 42.48 s | 6.11 s | 6.95× | 184.0 MiB |
| Kitten Nano FP32 | 4 | 14.69 s | 6.11 s | 2.41× | 151.3 MiB |
| Kitten Micro | 4 | 34.98 s | 5.57 s | 6.28× | 188.0 MiB |
| Piper Amy Low | engine default (2) | 5.82 s | 4.39 s | 1.33× | 170.6 MiB |

## Listen

- [Kitten Nano INT8](samples/nano-comparison.wav)
- [Kitten Nano FP32](samples/nano-fp32-comparison.wav)
- [Kitten Micro](samples/micro-comparison.wav)
- [Piper Amy Low](samples/amy-comparison.wav)

## Important limits

- A generation/audio ratio below 1 would mean faster than playback. Full-file playback here waits until all audio is written.
- Piper delivered its first callback after about 0.8 seconds for the short text. That is audio availability, not a measured end-to-end streaming playback delay. The tested Kitten JNI route returned valid full audio but did not invoke the streaming callback.
- Memory is sampled PSS. Piper measures its speech-service process; direct Kitten runs measure the lab UI plus the native engine together. The scopes differ, sampling can miss peaks, and dumpsys requests Java GCs. Four-thread comparisons used less frequent sampling than the initial runs.
- The first stock Kitten Android service tests produced empty WAVs. A first direct diagnostic also lacked the required external pronunciation setup. Those are failed runs, excluded from this listening table. The corrected native route initializes the separate Piper phonemizer, supplies phoneme codepoints and saves the returned audio.
- These tests ran with the lab in front. Netflix/4K playback performance and long-running stability have not been verified.
- The default Android speech setting was checked before and after each completed harness run and was unchanged. Buddy’s production voice/configuration was not modified.

Raw timing, memory samples and source hashes are preserved in the timestamped folders. See [the lab instructions](../../experiments/voice-lab/README.md) for reproduction. Review diagnostic logs before sharing them.

Cleanup: the temporary publisher TTS engine was uninstalled after testing. The isolated native Voice Lab remains installed; production Buddy was not changed.
