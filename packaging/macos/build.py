#!/usr/bin/env python3
"""Build a universal, self-contained Mac app from an explicit public-file list."""
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import shutil
import struct
import subprocess
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / 'outputs/tv-companion'
HERE = Path(__file__).resolve().parent
WORK = ROOT / 'work/mac-distribution'
DIST = ROOT / 'dist'
NODE_VERSION = '22.23.3'
NODE_HASHES = {
    'arm64': '23b25245dcfb9af7262f8ff142e9e2e0af025368117329e7a7458a51e5922f53',
    'x64': '8a677b0219178efd6eb0e475457c4afb452b521a92f6e67845a73bd85727f2a8',
}
ADB_HASH = '1811e253b21b12cbfda7201ebaf86c10e7ddcb5c606a7a81f7c82b4c429c2d3b'
MODULES = '''ambient apps mac-voice memory model-config persona providers quick-controls reactions
remote-button runtime-config seasonal server state-delivery timers tools tv updates voice-config'''.split()


def run(*args, **kwargs):
    subprocess.run([str(a) for a in args], check=True, **kwargs)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def copy(source, destination):
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copyfile(source, destination)


def main():
    if os.uname().sysname != 'Darwin':
        raise SystemExit('Build this app on macOS with Xcode command line tools installed.')
    version = json.loads((SOURCE / 'release.json').read_text())['version']
    if not re.fullmatch(r'\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?', version):
        raise SystemExit('Invalid release version')
    WORK.mkdir(parents=True, exist_ok=True)
    DIST.mkdir(exist_ok=True)
    stage = WORK / f'DuoBuddy {version}'
    if stage.exists():
        shutil.rmtree(stage)
    app = stage / 'DuoBuddy.app'
    contents = app / 'Contents'
    resources = contents / 'Resources'
    binaries = resources / 'bin'
    controller = resources / 'controller'
    notices = resources / 'Licenses'
    for path in (binaries, controller, notices, contents / 'MacOS'):
        path.mkdir(parents=True, exist_ok=True)

    # Pull only these members, never extract an entire third-party archive.
    for arch, expected in NODE_HASHES.items():
        name = f'node-v{NODE_VERSION}-darwin-{arch}'
        archive = ROOT / 'work' / (name + '.tar.gz')
        if not archive.exists():
            print(f'Downloading official Node.js for {arch}…', flush=True)
            with urllib.request.urlopen(f'https://nodejs.org/dist/v{NODE_VERSION}/{name}.tar.gz', timeout=120) as response:
                archive.write_bytes(response.read())
        if sha(archive) != expected:
            raise SystemExit(f'Checksum mismatch: {archive.name}. Delete it and retry.')
        with tarfile.open(archive) as tar:
            for member, output in [('bin/node', WORK / f'node-{arch}'), ('LICENSE', notices / 'Node.js-LICENSE.txt')]:
                output.write_bytes(tar.extractfile(f'{name}/{member}').read())
    run('/usr/bin/lipo', '-create', WORK / 'node-arm64', WORK / 'node-x64', '-output', binaries / 'node')
    adb = ROOT / 'work/android-tools/platform-tools/adb'
    if sha(adb) != ADB_HASH:
        raise SystemExit('ADB does not match the reviewed Platform Tools 37.0.1 binary. Review and update its checksum before bundling.')
    copy(adb, binaries / 'adb')
    copy(adb.parent / 'NOTICE.txt', notices / 'Android-Platform-Tools-NOTICE.txt')
    copy(SOURCE / 'android/assets/fonts/OFL.txt', notices / 'Quicksand-OFL.txt')
    copy(SOURCE / 'android/assets/AndroidSVG-LICENSE.txt', notices / 'AndroidSVG-LICENSE.txt')
    for name in MODULES:
        copy(SOURCE / (name + '.mjs'), controller / (name + '.mjs'))
    # The private SSH adapter and non-commercial voice sample are excluded.
    (controller / 'piper-client.mjs').write_text('export class PiperClient { async ensure() { throw Error("This edition uses macOS speech."); } close() {} }\n')
    for name in ('setup.html', 'release.json', 'DuoBuddy-TV.apk', 'android/AndroidManifest.xml', 'android/assets/fonts/Quicksand.ttf'):
        copy(SOURCE / name, controller / name)
    manifest = (SOURCE / 'android/AndroidManifest.xml').read_text()
    tv_version = int(re.search(r'android:versionCode="(\d+)"', manifest)[1])
    # Fail if a stale APK would mislead setup's version comparison.
    aapt = ROOT / 'work/build-tools/android-15/aapt2'
    badging = subprocess.check_output([str(aapt), 'dump', 'badging', str(controller / 'DuoBuddy-TV.apk')], text=True)
    if f"versionCode='{tv_version}'" not in badging:
        raise SystemExit('The APK is stale. Run outputs/tv-companion/build.sh first.')
    info = {
        'CFBundleExecutable': 'Buddy', 'CFBundleIdentifier': 'co.duobook.duobuddy.mac',
        'CFBundleName': 'DuoBuddy', 'CFBundleDisplayName': 'DuoBuddy',
        'CFBundlePackageType': 'APPL', 'CFBundleShortVersionString': version.split('-')[0],
        'CFBundleVersion': f'{tv_version}.1', 'CFBundleIconFile': 'Buddy.icns',
        'LSMinimumSystemVersion': '12.0', 'LSUIElement': True,
        'NSHighResolutionCapable': True,
        'NSLocalNetworkUsageDescription': 'Buddy connects to your Android TV to install the companion and control playback.',
        'NSAppTransportSecurity': {'NSAllowsLocalNetworking': True},
    }
    (contents / 'Info.plist').write_bytes(plistlib.dumps(info))
    iconset = WORK / 'Buddy.iconset'
    iconset.mkdir(exist_ok=True)
    icon = SOURCE / 'android/res/drawable-nodpi/duobuddy_app_icon.png'
    for size in (16, 32, 64, 128, 256, 512):
        for scale in (1, 2):
            name = f'icon_{size}x{size}' + ('@2x' if scale == 2 else '') + '.png'
            run('/usr/bin/sips', '-z', size * scale, size * scale, icon, '--out', iconset / name, stdout=subprocess.DEVNULL)
    # Modern ICNS stores PNG representations directly. Avoid iconutil's dependency
    # on an available WindowServer during headless/sandboxed builds.
    chunks = b''
    for size, kind in [(16, b'icp4'), (32, b'icp5'), (64, b'icp6'), (128, b'ic07'), (256, b'ic08'), (512, b'ic09'), (1024, b'ic10')]:
        png = (iconset / ('icon_512x512@2x.png' if size == 1024 else f'icon_{size}x{size}.png')).read_bytes()
        chunks += struct.pack('>4sI', kind, len(png) + 8) + png
    (resources / 'Buddy.icns').write_bytes(struct.pack('>4sI', b'icns', len(chunks) + 8) + chunks)
    env = {**os.environ, 'CLANG_MODULE_CACHE_PATH': str(WORK / 'module-cache'), 'SWIFT_MODULECACHE_PATH': str(WORK / 'module-cache')}
    for arch in ('arm64', 'x86_64'):
        run('/usr/bin/xcrun', 'swiftc', HERE / 'Buddy.swift', '-O', '-swift-version', '5', '-target', f'{arch}-apple-macosx12.0', '-module-cache-path', WORK / 'module-cache', '-o', WORK / f'Buddy-{arch}', env=env)
    run('/usr/bin/lipo', '-create', WORK / 'Buddy-arm64', WORK / 'Buddy-x86_64', '-output', contents / 'MacOS/Buddy')
    for binary in (binaries / 'node', binaries / 'adb', contents / 'MacOS/Buddy'):
        binary.chmod(0o755)
    copy(HERE / 'Read Me.txt', stage / 'Read Me.txt')
    copy(HERE / 'Privacy.txt', resources / 'Privacy.txt')
    copy(HERE / 'Privacy.txt', stage / 'Privacy.txt')
    os.symlink('/Applications', stage / 'Applications')
    identity = os.environ.get('BUDDY_SIGNING_IDENTITY', '-')
    options = ['--options', 'runtime', '--timestamp'] if identity != '-' else []
    for binary in (binaries / 'node', binaries / 'adb'):
        entitlements = ['--entitlements', str(HERE / 'node-entitlements.plist')] if binary.name == 'node' else []
        run('/usr/bin/codesign', '--force', '--sign', identity, *options, *entitlements, binary)
    run('/usr/bin/codesign', '--force', '--sign', identity, *options, app)
    run('/usr/bin/codesign', '--verify', '--deep', '--strict', app)
    # Audit the exact payload before archiving. No recursive source/work copying.
    for file in app.rglob('*'):
        if file.is_symlink():
            raise SystemExit(f'Unexpected payload symlink: {file}')
        if file.name in {'credentials.json', 'config.json', 'pairing.json', 'memory.json'} or file.suffix in {'.keystore', '.onnx', '.wav'}:
            raise SystemExit(f'Private or excluded payload: {file}')
    payload = {str(p.relative_to(app)): sha(p) for p in sorted(app.rglob('*')) if p.is_file()}
    (stage / 'Build manifest.json').write_text(json.dumps({'version': version, 'node': NODE_VERSION, 'tvVersionCode': tv_version, 'signing': 'ad-hoc' if identity == '-' else 'Developer ID', 'files': payload}, indent=2) + '\n')
    output = DIST / f'DuoBuddy-{version}-mac-universal.zip'
    if output.exists():
        output.unlink()
    run('/usr/bin/ditto', '-c', '-k', '--sequesterRsrc', '--keepParent', stage, output)
    # Optional release signing; credentials stay in the user's Keychain.
    profile = os.environ.get('BUDDY_NOTARY_PROFILE')
    if profile:
        if identity == '-':
            raise SystemExit('Notarization needs BUDDY_SIGNING_IDENTITY.')
        run('/usr/bin/xcrun', 'notarytool', 'submit', output, '--keychain-profile', profile, '--wait')
        run('/usr/bin/xcrun', 'stapler', 'staple', app)
        payload = {str(p.relative_to(app)): sha(p) for p in sorted(app.rglob('*')) if p.is_file()}
        (stage / 'Build manifest.json').write_text(json.dumps({'version': version, 'node': NODE_VERSION, 'tvVersionCode': tv_version, 'signing': 'Developer ID, notarized', 'files': payload}, indent=2) + '\n')
        output.unlink()
        run('/usr/bin/ditto', '-c', '-k', '--sequesterRsrc', '--keepParent', stage, output)
    (DIST / (output.name + '.sha256')).write_text(f'{sha(output)}  {output.name}\n')
    print(f'Built {output} ({output.stat().st_size // 1024 // 1024} MB). Signing: {"ad-hoc tester build" if identity == "-" else "Developer ID"}', flush=True)


if __name__ == '__main__':
    main()
