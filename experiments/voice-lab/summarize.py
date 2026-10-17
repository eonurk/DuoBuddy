#!/usr/bin/env python3
"""Create a listening comparison from completed physical-device runs."""
from pathlib import Path
import array
import hashlib
import json
import math
import shutil
import wave

ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'outputs/voice-lab'
NAMES = {'nano': 'Kitten Nano INT8', 'nano-fp32': 'Kitten Nano FP32', 'micro': 'Kitten Micro', 'amy': 'Piper Amy Low'}
selected = {}
for folder in sorted(OUTPUT.iterdir()):
    if not (folder / 'results.json').exists(): continue
    report = json.loads((folder / 'results.json').read_text())
    for result in report.get('results', []):
        if result.get('error') or result.get('sample') != 'short' or result.get('temperature') != 'warm': continue
        audio = folder / (result['id'] + '.wav')
        if not audio.exists(): continue
        with wave.open(str(audio), 'rb') as wav:
            assert wav.getsampwidth() == 2 and wav.getnchannels() == 1
            samples = array.array('h', wav.readframes(wav.getnframes()))
            duration = wav.getnframes() / wav.getframerate()
        assert abs(duration - result['audioSeconds']) < 0.001
        rms = math.sqrt(sum(x * x for x in samples) / max(1, len(samples)))
        assert duration > 0 and rms > 10, 'Empty/silent sample: ' + str(audio)
        model = result['model']
        if model not in selected or result['synthesisMs'] < selected[model]['synthesisMs']:
            selected[model] = {**result, 'sourceRun': folder.name, 'sourceWav': str(audio.relative_to(OUTPUT)), 'waveSha256': hashlib.sha256(audio.read_bytes()).hexdigest(), 'rms16Bit': round(rms)}

samples_dir = OUTPUT / 'samples'
samples_dir.mkdir(exist_ok=True)
lines = [
    '# Mi Box offline speech comparison', '',
    'Measured on the physical Xiaomi MIBOX4 (Android 9 / API 28, 32-bit ARM, about 2 GB total RAM).', '',
    'All clips below were generated on the TV box without a speech API or Mac synthesis. Each uses the same text:', '',
    '> Hi! I\'m your DuoBuddy. Shall we find something good to watch?', '',
    'The table selects the fastest valid **warm** short run for each model. Model loading is excluded; initial startup took approximately 8–19 seconds in these tests. These are individual exploratory measurements, not statistical latency guarantees.', '',
    '| Model | Threads | Full-file generation | Clip length | Generation / audio | Sampled process PSS |',
    '| --- | ---: | ---: | ---: | ---: | ---: |',
]
for model, label in NAMES.items():
    if model not in selected: continue
    result = selected[model]
    file = samples_dir / (model + '-comparison.wav')
    shutil.copyfile(OUTPUT / result['sourceWav'], file)
    result['comparisonWav'] = str(file.relative_to(OUTPUT))
    threads = result.get('threads', 'engine default (2)')
    memory = result.get('sampledPeakEnginePssKiB')
    lines.append(f"| {label} | {threads} | {result['synthesisMs']/1000:.2f} s | {result['audioSeconds']:.2f} s | {result['realTimeFactor']:.2f}× | {memory/1024:.1f} MiB |")
lines += ['', '## Listen', '']
for model, label in NAMES.items():
    if model in selected: lines += [f"- [{label}]({selected[model]['comparisonWav']})"]
lines += [
    '', '## Important limits', '',
    '- A generation/audio ratio below 1 would mean faster than playback. Full-file playback here waits until all audio is written.',
    '- Piper delivered its first callback after about 0.8 seconds for the short text. That is audio availability, not a measured end-to-end streaming playback delay. The tested Kitten JNI route returned valid full audio but did not invoke the streaming callback.',
    '- Memory is sampled PSS. Piper measures its speech-service process; direct Kitten runs measure the lab UI plus the native engine together. The scopes differ, sampling can miss peaks, and dumpsys requests Java GCs. Four-thread comparisons used less frequent sampling than the initial runs.',
    '- The first stock Kitten Android service tests produced empty WAVs. A first direct diagnostic also lacked the required external pronunciation setup. Those are failed runs, excluded from this listening table. The corrected native route initializes the separate Piper phonemizer, supplies phoneme codepoints and saves the returned audio.',
    '- These tests ran with the lab in front. Netflix/4K playback performance and long-running stability have not been verified.',
    '- The default Android speech setting was checked before and after each completed harness run and was unchanged. Buddy’s production voice/configuration was not modified.',
    '', 'Raw timing, memory samples and source hashes are preserved in the timestamped folders. See [the lab instructions](../../experiments/voice-lab/README.md) for reproduction. Review diagnostic logs before sharing them.', '',
]
(OUTPUT / 'RESULTS.md').write_text('\n'.join(lines))
(OUTPUT / 'comparison.json').write_text(json.dumps(selected, indent=2) + '\n')
print('Prepared comparison for:', ', '.join(selected))
