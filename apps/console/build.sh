#!/data/data/com.termux/files/usr/bin/bash
# Build the DSH Console APK (Termux only, no Android Studio)
set -e
SRC="$HOME/dsh-console"
AJ="$HOME/.smoke/android.jar"
OUT="$SRC/build"
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen"

echo "① aapt2 compile (resources)"
aapt2 compile --dir "$SRC/res" -o "$OUT/res.zip"

echo "② aapt2 link (generates base.apk and R.java)"
aapt2 link -o "$OUT/base.apk" -I "$AJ" --manifest "$SRC/AndroidManifest.xml" \
  -R "$OUT/res.zip" --java "$OUT/gen" \
  --min-sdk-version 26 --target-sdk-version 34 --auto-add-overlay

echo "③ javac (Java → class)"
javac --release 8 -nowarn -classpath "$AJ" -d "$OUT/classes" \
  $(find "$SRC/src" "$OUT/gen" -name '*.java')

echo "④ d8 (class → dex)"
d8 --lib "$AJ" --min-api 26 --output "$OUT/dex" $(find "$OUT/classes" -name '*.class')

echo "⑤ Package classes.dex"
cp "$OUT/base.apk" "$OUT/unsigned.apk"
(cd "$OUT/dex" && zip -q -u "$OUT/unsigned.apk" classes.dex)

echo "⑥ Sign"
if [ ! -f "$SRC/ks.jks" ]; then
  keytool -genkeypair -keystore "$SRC/ks.jks" -alias dsh -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass dshbridge -keypass dshbridge \
    -dname "CN=DSH Bridge,O=DSH,C=CN" >/dev/null 2>&1
  echo "   Generated self-signed key $SRC/ks.jks (password dshbridge)"
fi
apksigner sign --ks "$SRC/ks.jks" --ks-pass pass:dshbridge --key-pass pass:dshbridge \
  --out "$OUT/dsh-console.apk" "$OUT/unsigned.apk"

echo "⑦ Verify"
apksigner verify --print-certs "$OUT/dsh-console.apk" | head -4
ls -l "$OUT/dsh-console.apk" | awk '{printf "   Artifact: %s  %.1f KB\n", $9, $5/1024}'
echo -n "   In-package version: "; aapt2 dump badging "$OUT/dsh-console.apk" 2>/dev/null | head -1 | grep -oE "versionCode='[0-9]+' versionName='[^']+'"
