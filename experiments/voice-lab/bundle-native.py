#!/usr/bin/env python3
"""Build isolated native benchmarks using verified publisher APK dependencies.

Only model assets, the publisher's DEX code and 32-bit ARM native libraries are
reused. No publisher app components or permissions are installed in this APK.
"""
from pathlib import Path
import hashlib
import shutil
import subprocess
import sys
import tarfile
import json
import zipfile

ROOT = Path(__file__).resolve().parents[2]
WORK = ROOT / 'work/voice-lab'
BUILD = WORK / 'build'
TOOLS = ROOT / 'work/build-tools'
JAVA = TOOLS / 'jdk-17.0.20.1+1/Contents/Home/bin/java'
SIGNER = TOOLS / 'android-15/lib/apksigner.jar'
HASHES = {
    'nano': 'cd91e7e9924b4303fd3311a364159a9e687806527c30a36ce8a0f165e61c8e1c',
    'micro': 'cae31315449c0ee407ce666c8cbdc602dd7a10aa97aea3e832ada8c2dfe3ad0d',
    'amy': '1614031814e3504acb8be3265eef8657d482a81c535a0900ee04c6ebf15fab68',
}
if 'nano-fp32' in sys.argv[1:]: HASHES['nano-fp32'] = HASHES['nano']
for name, expected in HASHES.items():
    if len(sys.argv) > 1 and name not in sys.argv[1:]: continue
    upstream = WORK / 'downloads' / (('nano' if name == 'nano-fp32' else name) + '.apk')
    assert hashlib.sha256(upstream.read_bytes()).hexdigest() == expected, name
    unsigned = BUILD / (name + '-native-unsigned.apk')
    with zipfile.ZipFile(BUILD / 'resources.apk') as base, zipfile.ZipFile(upstream) as source, zipfile.ZipFile(unsigned, 'w', zipfile.ZIP_DEFLATED) as target:
        for member in base.namelist(): target.writestr(member, base.read(member))
        dex = sorted(p for p in source.namelist() if p.startswith('classes') and p.endswith('.dex'))
        for member in source.namelist():
            if name == 'nano-fp32' and member.startswith('assets/kitten-nano-en-v0_8-int8/'): continue
            if member in dex or member.startswith('lib/armeabi-v7a/') or member.startswith('assets/'):
                target.writestr(member, source.read(member))
        if name == 'nano-fp32':
            archive_path = WORK / 'downloads/nano-fp32.tar.bz2'
            assert hashlib.sha256(archive_path.read_bytes()).hexdigest() == '16092117bfe591ddcd58d078e1454603b8e1caea46f85653b2c2efae76bd883e', 'FP32 archive checksum'
            with tarfile.open(archive_path) as archive:
                for filename in ['model.fp32.onnx', 'voices.bin', 'tokens.txt', 'LICENSE']:
                    relative = 'kitten-nano-en-v0_8-fp32/' + filename
                    target.writestr('assets/' + relative, archive.extractfile(relative).read())
        target.writestr('assets/voice-lab.json', json.dumps({'model': name}))
        target.writestr(f'classes{len(dex) + 1}.dex', (BUILD / 'dex/classes.dex').read_bytes())
    aligned = BUILD / (name + '-native-aligned.apk')
    subprocess.run([str(TOOLS / 'android-15/zipalign'), '-f', '4', str(unsigned), str(aligned)], check=True)
    output = BUILD / (name + '-native.apk')
    subprocess.run([str(JAVA), '-jar', str(SIGNER), 'sign', '--ks', str(BUILD / 'lab.keystore'), '--ks-pass', 'pass:android', '--out', str(output), str(aligned)], check=True)
    subprocess.run([str(JAVA), '-jar', str(SIGNER), 'verify', str(output)], check=True)
    print(name, output.stat().st_size, flush=True)
