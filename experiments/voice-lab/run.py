#!/usr/bin/env python3
"""Benchmark downloaded, signed official TTS engines on the selected Android TV.

Installs a separate lab app and swaps the one test-engine package. Never changes
the system default TTS engine or Buddy's package, settings or running controller.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'work/voice-lab'
OUTPUT = ROOT / 'outputs/voice-lab'
ENGINE = 'com.k2fsa.sherpa.onnx.tts.engine'
LAB = 'co.duobook.voicelab'
REMOTE = f'/sdcard/Android/data/{LAB}/files'
MODELS = {
    'nano': 'kitten-nano-en-v0_8-int8',
    'nano-fp32': 'kitten-nano-en-v0_8-fp32',
    'micro': 'kitten-micro-en-v0_8',
    'amy': 'vits-piper-en_US-amy-low',
}
SIGNER = '030a90bdd6e4c5eef1e161acfa6455a912c45cb2423b64df449ca88fdee097bb'


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--models', default='nano,micro,amy')
    parser.add_argument('--device', default=os.environ.get('TV_DEVICE', '192.168.178.87:5555'))
    parser.add_argument('--play', action='store_true', help='Play each model\'s warm short sample on the TV')
    parser.add_argument('--direct', action='store_true', help='Use the isolated bundled JNI benchmark instead of Android TTS service')
    parser.add_argument('--threads', type=int, choices=[1, 2, 3, 4], default=2)
    parser.add_argument('--speaker', type=int, choices=range(8), default=0)
    parser.add_argument('--quick', action='store_true', help='Only short cold and warm tests')
    parser.add_argument('--reuse', action='store_true', help='Skip native APK installation if its installed SHA-256 matches')
    parser.add_argument('--memory-interval', type=float, default=0.8, help='Seconds between memory samples (dumpsys can request a Java GC)')
    args = parser.parse_args()
    models = args.models.split(',')
    if not models or any(m not in MODELS for m in models):
        raise SystemExit('Choose models from nano,micro,amy')
    adb = os.environ.get('TV_ADB', str(ROOT / 'work/android-tools/platform-tools/adb'))
    WORK.mkdir(parents=True, exist_ok=True)
    OUTPUT.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime('%Y%m%d-%H%M%S')
    run_dir = OUTPUT / stamp
    run_dir.mkdir()

    def command(*argv, timeout=20, check=True):
        return subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=timeout, check=check).stdout

    def tv(*argv, **kw):
        return command(adb, '-s', args.device, *argv, **kw)

    def shell(text, **kw):
        return tv('shell', text, **kw)

    default_engine = shell('settings get secure tts_default_synth').strip()
    original_activity = shell('dumpsys activity activities | grep mResumedActivity')
    original = re.search(r'\bu\d+ ([A-Za-z0-9_.]+/[A-Za-z0-9_.$]+)', original_activity)
    facts = shell('getprop ro.product.model; getprop ro.product.cpu.abilist; getprop ro.build.version.sdk; head -5 /proc/meminfo; df -k /data')
    report = {'timestamp': stamp, 'implementation': 'direct-jni' if args.direct else 'android-tts-service', 'deviceFacts': facts, 'systemDefaultTtsBefore': default_engine, 'originalActivity': original_activity.strip(), 'models': {}, 'results': []}
    results_file = run_dir / 'results.json'

    def save():
        results_file.write_text(json.dumps(report, indent=2) + '\n')

    def sample_memory():
        raw = shell(f'dumpsys meminfo {LAB if args.direct else ENGINE}', check=False, timeout=8)
        match = re.search(r'^\s*TOTAL\s+(\d+)', raw, re.M)
        rss = re.search(r'TOTAL RSS:\s*(\d+)', raw)
        return {'elapsed': round(time.monotonic() - clock_start, 3), 'pssKiB': int(match[1]) if match else None, 'rssKiB': int(rss[1]) if rss else None, 'raw': raw}

    java = ROOT / 'work/build-tools/jdk-17.0.20.1+1/Contents/Home/bin/java'
    signer = ROOT / 'work/build-tools/android-15/lib/apksigner.jar'
    aapt = ROOT / 'work/build-tools/android-15/aapt2'
    # Verify all three downloads before changing the TV. The fingerprint belongs
    # to the publisher whose APK is linked by the official model documentation.
    for model in models:
        apk = WORK / 'downloads' / (('nano' if model == 'nano-fp32' else model) + '.apk')
        verified = command(str(java), '-jar', str(signer), 'verify', '--print-certs', str(apk))
        (run_dir / (model + '-signature.txt')).write_text(verified)
        if f'certificate SHA-256 digest: {SIGNER}' not in verified:
            raise SystemExit(f'Unexpected publisher signature: {model}')
        badging = command(str(aapt), 'dump', 'badging', str(apk))
        if f"package: name='{ENGINE}'" not in badging or "minSdkVersion:'21'" not in badging:
            raise SystemExit(f'Unexpected test engine: {model}')
        report['models'][model] = {'name': MODELS[model], 'upstreamApkBytes': apk.stat().st_size, 'upstreamApkSha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'publisherCertificateSha256': SIGNER, 'source': f'https://k2-fsa.github.io/sherpa/onnx/tts/all/English/{MODELS[model]}.html'}
    save()
    if not args.direct:
        print('Installing isolated voice benchmark app…', flush=True)
        print(tv('install', '-r', str(WORK / 'build/DuoBuddy-Voice-Lab.apk'), timeout=120).strip(), flush=True)
    try:
        for model in models:
            print(f'{model}: installing offline engine…', flush=True)
            shell(f'am force-stop {LAB}; am force-stop {ENGINE}')
            apk = WORK / 'build' / (model + '-native.apk') if args.direct else WORK / 'downloads' / (model + '.apk')
            report['models'][model]['installedApkBytes'] = apk.stat().st_size
            report['models'][model]['installedApkSha256'] = hashlib.sha256(apk.read_bytes()).hexdigest()
            reuse = False
            if args.direct and args.reuse:
                location = shell(f'pm path {LAB}', check=False).strip()
                if re.fullmatch(r'package:/data/app/[A-Za-z0-9_./=+-]+/base\.apk', location):
                    remote_hash = shell('sha256sum ' + location.removeprefix('package:'), check=False).split()
                    reuse = bool(remote_hash) and remote_hash[0] == hashlib.sha256(apk.read_bytes()).hexdigest()
            if reuse:
                print('Using the identical native APK already installed.', flush=True)
            else:
                print(tv('install', '-r', str(apk), timeout=180).strip(), flush=True)
            tests = [('short', 'cold'), ('short', 'warm')] + ([] if args.quick else [('long', 'warm')])
            for sample, temperature in tests:
                test_id = f'{model}-{sample}-{temperature}-{stamp}'
                play = args.play and sample == 'short' and temperature == 'warm'
                clock_start = time.monotonic()
                print(f'{model}: {sample} / {temperature}…', flush=True)
                sid = 0 if model == 'amy' else args.speaker
                shell(f'am start -n {LAB}/.VoiceLabActivity --es model {model} --es sample {sample} --es id {test_id} --ez autoplay {str(play).lower()} --ez direct {str(args.direct).lower()} --ei threads {args.threads} --ei speaker {sid}', timeout=15)
                samples = []
                result = None
                last_memory = -100
                last_progress = 0
                while time.monotonic() - clock_start < 140:
                    elapsed = time.monotonic() - clock_start
                    if elapsed - last_memory >= max(0.8, args.memory_interval):
                        samples.append(sample_memory()); last_memory = elapsed
                    raw = shell(f'cat {REMOTE}/{test_id}.json 2>/dev/null', check=False)
                    if raw.strip().startswith('{'):
                        result = json.loads(raw)
                        break
                    if elapsed - last_progress >= 12:
                        print(f'{model}: still generating ({int(time.monotonic() - clock_start)} s)…', flush=True)
                        last_progress = elapsed
                    time.sleep(0.4)
                if result is None:
                    result = {'id': test_id, 'model': model, 'sample': sample, 'error': 'No completion within 140 seconds (engine crash or timeout)'}
                result['temperature'] = temperature
                result['memoryProcess'] = LAB if args.direct else ENGINE
                measured = [m['pssKiB'] for m in samples if m['pssKiB'] is not None]
                result['sampledPeakEnginePssKiB'] = max(measured) if measured else None
                result['memorySamples'] = [{k: v for k, v in m.items() if k != 'raw'} for m in samples]
                result['afterMeminfo'] = shell('head -5 /proc/meminfo')
                (run_dir / (test_id + '-meminfo.txt')).write_text('\n\n'.join(f"t={m['elapsed']}\n{m['raw']}" for m in samples))
                if result.get('audioBytes', 0) > 44:
                    tv('pull', f'{REMOTE}/{test_id}.wav', str(run_dir / (test_id + '.wav')), timeout=30)
                report['results'].append(result)
                save()
                print(json.dumps({k: result.get(k) for k in ['model', 'sample', 'temperature', 'initMs', 'synthesisMs', 'audioSeconds', 'realTimeFactor', 'sampledPeakEnginePssKiB', 'boundEngine', 'error']}), flush=True)
                if result.get('error'):
                    diagnostics = WORK / 'diagnostics' / stamp
                    diagnostics.mkdir(parents=True, exist_ok=True)
                    (diagnostics / (test_id + '-logcat.txt')).write_text(tv('logcat', '-d', '-t', '800', timeout=15))
                    shell(f'am force-stop {LAB}; am force-stop {ENGINE}')
                    break
                if play:
                    time.sleep(min(float(result.get('audioSeconds', 0)) + 0.5, 15))
            shell(f'am force-stop {LAB}; am force-stop {ENGINE}')
    finally:
        report['systemDefaultTtsAfter'] = shell('settings get secure tts_default_synth').strip()
        report['defaultTtsUnchanged'] = report['systemDefaultTtsAfter'] == default_engine
        save()
        if original:
            shell('am start -n ' + original[1])
        print(f'Results saved to {run_dir}', flush=True)
        print('System default TTS unchanged:', report['defaultTtsUnchanged'], flush=True)


if __name__ == '__main__':
    main()
