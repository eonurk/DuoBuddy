#!/usr/bin/env python3
"""Fetch hash-pinned QR compile dependency and optional pure JVM test dependency."""
from pathlib import Path
import hashlib, sys, urllib.request
root = Path(__file__).resolve().parents[2] / 'work/build-tools/native'
root.mkdir(parents=True, exist_ok=True)
artifacts = [('zxing-core-3.5.3.jar', 'com/google/zxing/core/3.5.3/core-3.5.3.jar', '8d8064c1636fdaef7189dd9055c7d59950a8940a12f2293956446ec3c109fd82')]
if '--tests' in sys.argv:
    artifacts.append(('json-20240303.jar', 'org/json/json/20240303/json-20240303.jar', '3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'))
for name, artifact, digest in artifacts:
    path = root / name
    if not path.exists():
        with urllib.request.urlopen('https://repo.maven.apache.org/maven2/' + artifact, timeout=60) as response:
            path.write_bytes(response.read())
    if hashlib.sha256(path.read_bytes()).hexdigest() != digest:
        raise SystemExit('Dependency checksum mismatch: ' + name)
