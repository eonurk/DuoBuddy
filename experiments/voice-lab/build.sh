#!/bin/bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")/../.." && pwd)"
lab="$task_root/experiments/voice-lab"
build="$task_root/work/voice-lab/build"
tools="$task_root/work/build-tools"
jdk="$tools/jdk-17.0.20.1+1/Contents/Home"
android="$tools/android-15"
platform="$tools/android-9/android.jar"
mkdir -p "$build/classes" "$build/dex"
"$jdk/bin/javac" -source 8 -target 8 -classpath "$platform" -d "$build/classes" "$lab"/src/co/duobook/voicelab/*.java
"$jdk/bin/jar" cf "$build/classes.jar" -C "$build/classes" .
"$jdk/bin/java" -cp "$android/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 26 --lib "$platform" --output "$build/dex" "$build/classes.jar"
"$android/aapt2" link -I "$platform" --manifest "$lab/AndroidManifest.xml" -o "$build/resources.apk"
cp "$build/resources.apk" "$build/unsigned.apk"
(cd "$build/dex" && /usr/bin/zip -q "$build/unsigned.apk" classes.dex)
"$android/zipalign" -f 4 "$build/unsigned.apk" "$build/aligned.apk"
if [ ! -f "$build/lab.keystore" ]; then
  "$jdk/bin/keytool" -genkeypair -keystore "$build/lab.keystore" -alias voicelab -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 3650 -dname 'CN=DuoBuddy Local Voice Lab'
fi
"$jdk/bin/java" -jar "$android/lib/apksigner.jar" sign --ks "$build/lab.keystore" --ks-pass pass:android --out "$build/DuoBuddy-Voice-Lab.apk" "$build/aligned.apk"
"$jdk/bin/java" -jar "$android/lib/apksigner.jar" verify "$build/DuoBuddy-Voice-Lab.apk"
echo 'Built work/voice-lab/build/DuoBuddy-Voice-Lab.apk'
