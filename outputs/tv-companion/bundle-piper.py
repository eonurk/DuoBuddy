#!/usr/bin/env python3
"""Embed verified upstream runtime code and Amy assets, never upstream app components."""
import hashlib
from pathlib import Path
import subprocess
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[2]
BUILD = ROOT / 'work/tv-build'
CACHE = ROOT / 'work/piper-runtime'
TOOLS = ROOT / 'work/build-tools'
CERT = '030a90bdd6e4c5eef1e161acfa6455a912c45cb2423b64df449ca88fdee097bb'
SOURCES = {
    'armeabi-v7a': ('amy.apk', '1614031814e3504acb8be3265eef8657d482a81c535a0900ee04c6ebf15fab68'),
    'arm64-v8a': ('amy-arm64.apk', '411c58fed69f7fb45edd391d50859b6112b5089d87aa4a7ca3352c4b92753ab0'),
}
BASE = 'https://huggingface.co/csukuangfj2/sherpa-onnx-apk/resolve/main/tts-engine-new/1.13.8/'


def verified(abi, name, expected):
    cached = CACHE / name
    lab = ROOT / 'work/voice-lab/downloads' / name
    source = lab if lab.exists() else cached
    if not source.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        url = BASE + f'sherpa-onnx-1.13.8-{abi}-eng-tts-engine-vits-piper-en_US-amy-low.apk?download=true'
        print('Downloading official Piper runtime for ' + abi, flush=True)
        with urllib.request.urlopen(url, timeout=120) as response:
            cached.write_bytes(response.read())
    if hashlib.sha256(source.read_bytes()).hexdigest() != expected:
        raise SystemExit('Piper dependency checksum mismatch: ' + str(source))
    result = subprocess.run([str(TOOLS / 'jdk-17.0.20.1+1/Contents/Home/bin/java'), '-jar',
                             str(TOOLS / 'android-15/lib/apksigner.jar'), 'verify', '--print-certs', str(source)],
                            capture_output=True, text=True, check=True)
    if 'certificate SHA-256 digest: ' + CERT not in result.stdout:
        raise SystemExit('Unexpected Piper publisher certificate')
    return source


def main():
    kotlin = CACHE / 'kotlin-stdlib-2.0.21.jar'
    if '--prepare' in __import__('sys').argv:
        CACHE.mkdir(parents=True, exist_ok=True)
        if not kotlin.exists():
            with urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/jetbrains/kotlin/kotlin-stdlib/2.0.21/kotlin-stdlib-2.0.21.jar', timeout=60) as response:
                kotlin.write_bytes(response.read())
        if hashlib.sha256(kotlin.read_bytes()).hexdigest() != 'f31cc53f105a7e48c093683bbd5437561d1233920513774b470805641bedbc09':
            raise SystemExit('Kotlin compile dependency checksum mismatch')
        return
    dependencies = {abi: verified(abi, *spec) for abi, spec in SOURCES.items()}
    with zipfile.ZipFile(BUILD / 'unsigned.apk', 'w', zipfile.ZIP_DEFLATED) as out:
        with zipfile.ZipFile(BUILD / 'resources.apk') as resources:
            for name in resources.namelist():
                out.writestr(name, resources.read(name))
        with zipfile.ZipFile(dependencies['armeabi-v7a']) as upstream:
            dex = sorted(n for n in upstream.namelist() if n.startswith('classes') and n.endswith('.dex'))
            for name in dex:
                out.writestr(name, upstream.read(name))
            for name in upstream.namelist():
                if name.startswith(('assets/espeak-ng-data/', 'assets/vits-piper-en_US-amy-low/')):
                    out.writestr(name, upstream.read(name))
        # One app DEX follows the upstream Kotlin/JNI dependencies. Android 8+ has native multidex.
        out.writestr(f'classes{len(dex) + 1}.dex', (BUILD / 'dex/classes.dex').read_bytes())
        for abi, source in dependencies.items():
            with zipfile.ZipFile(source) as upstream:
                for library in ['libonnxruntime.so', 'libsherpa-onnx-jni.so', 'libpiper-phonemize-jni.so']:
                    name = f'lib/{abi}/{library}'
                    out.writestr(name, upstream.read(name))
    print('Embedded Amy Low and Piper runtimes for ARMv7 + ARM64', flush=True)


if __name__ == '__main__':
    main()
