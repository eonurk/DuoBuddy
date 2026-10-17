#!/bin/bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")/../.." && pwd)"
source_root="$task_root/outputs/tv-companion"
tool_root="$task_root/work/build-tools"
build_root="$task_root/work/tv-build"
jdk_root="$tool_root/jdk-17.0.20.1+1/Contents/Home"
android_jar="$tool_root/android-9/android.jar"
android_build="$tool_root/android-15"
rm -rf "$build_root/classes" "$build_root/dex"
mkdir -p "$build_root/classes" "$build_root/dex"
"$jdk_root/bin/javac" -source 8 -target 8 -classpath "$android_jar:$tool_root/androidsvg/classes.jar" -d "$build_root/classes" "$source_root"/android/src/family/tv/companion/*.java
"$jdk_root/bin/jar" cf "$build_root/classes.jar" -C "$build_root/classes" .
"$jdk_root/bin/java" -cp "$android_build/lib/d8.jar" com.android.tools.r8.D8 --release --min-api 26 --lib "$android_jar" --output "$build_root/dex" "$build_root/classes.jar" "$tool_root/androidsvg/classes.jar"
"$android_build/aapt2" compile --dir "$source_root/android/res" -o "$build_root/resources.zip"
"$android_build/aapt2" link -I "$android_jar" --manifest "$source_root/android/AndroidManifest.xml" -A "$source_root/android/assets" -o "$build_root/resources.apk" "$build_root/resources.zip"
cp "$build_root/resources.apk" "$build_root/unsigned.apk"
(cd "$build_root/dex" && /usr/bin/zip -q "$build_root/unsigned.apk" classes.dex)
"$android_build/zipalign" -f 4 "$build_root/unsigned.apk" "$build_root/aligned.apk"
if [ ! -f "$build_root/companion.keystore" ]; then
  "$jdk_root/bin/keytool" -genkeypair -keystore "$build_root/companion.keystore" -alias companion -storepass android -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Local TV Companion'
fi
"$jdk_root/bin/java" -jar "$android_build/lib/apksigner.jar" sign --ks "$build_root/companion.keystore" --ks-pass pass:android --out "$source_root/DuoBook-TV.apk" "$build_root/aligned.apk"
"$jdk_root/bin/java" -jar "$android_build/lib/apksigner.jar" verify "$source_root/DuoBook-TV.apk"
echo 'Built outputs/tv-companion/DuoBook-TV.apk'
