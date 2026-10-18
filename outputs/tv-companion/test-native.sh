#!/bin/bash
# Build a separate instrumentation APK; requires an installed, matching signed DuoBuddy APK.
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
build="$root/work/native-test"
tools="$root/work/build-tools"
jdk="$tools/jdk-17.0.20.1+1/Contents/Home/bin"
mkdir -p "$build/classes" "$build/dex"
"$jdk/javac" -source 8 -target 8 -cp "$tools/android-9/android.jar:$root/work/tv-build/classes" -d "$build/classes" "$root/outputs/tv-companion/tests/NativeRuntimeTest.java"
"$jdk/jar" cf "$build/classes.jar" -C "$build/classes" .
"$jdk/java" -cp "$tools/android-15/lib/d8.jar" com.android.tools.r8.D8 --min-api 26 --lib "$tools/android-9/android.jar" --lib "$root/work/tv-build/classes.jar" --output "$build/dex" "$build/classes.jar"
cat > "$build/AndroidManifest.xml" <<'XML'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="family.tv.companion.tests"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="28"/><application android:label="DuoBuddy tests"/><instrumentation android:name="family.tv.companion.NativeRuntimeTest" android:targetPackage="family.tv.companion"/></manifest>
XML
"$tools/android-15/aapt2" link -I "$tools/android-9/android.jar" --manifest "$build/AndroidManifest.xml" -o "$build/tests.apk"
python3 - "$build" <<'PY'
from pathlib import Path
import zipfile,sys
p=Path(sys.argv[1])
with zipfile.ZipFile(p/'tests.apk','a',zipfile.ZIP_DEFLATED) as z:
 for dex in (p/'dex').glob('*.dex'):z.write(dex,dex.name)
PY
"$tools/android-15/zipalign" -f 4 "$build/tests.apk" "$build/aligned.apk"
"$jdk/java" -jar "$tools/android-15/lib/apksigner.jar" sign --ks "$root/work/tv-build/companion.keystore" --ks-pass pass:android --out "$build/DuoBuddy-tests.apk" "$build/aligned.apk"
echo "Built separate instrumentation APK in work/native-test. Do not publish it."
