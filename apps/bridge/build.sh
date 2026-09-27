#!/data/data/com.termux/files/usr/bin/bash
# Build the DSH Bridge APK (Termux only, no Android Studio)
set -e
SRC="$HOME/droid-bridge"
AJ="$HOME/.smoke/android.jar"
OUT="$SRC/build"
rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen"

# ── 出包门禁（2026-09-27 加）──
# 为什么：上一轮改完文案**没有重新生成/重新出包**，手机上跑的仍是旧表 —— 用户看到的是
# "上一轮的要求根本没做到"（channels ×3、auto 打头、up to date 全是英文）。
# 现在编译前强制：① 从 ui/controls.json + ui/theme.json 重新生成三个产物；
# ② 跑 tools/i18n-audit（字面量调用 / 中文覆盖 / 主题一致 / 色板一致 / 状态口径）。
# 任一项不过就直接中止，不许产出一个"看起来修好了"的包。
if [ -x "$HOME/dsh-termux-kit/tools/ui-controls" ]; then
  python3 "$HOME/dsh-termux-kit/tools/ui-controls" gen || exit 1
  python3 "$HOME/dsh-termux-kit/tools/i18n-audit" --quiet || {
    echo "✘ 文案/主题审计没过 —— 拒绝出包（跑 tools/i18n-audit 看细节）"; exit 1; }
fi

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
  --out "$OUT/dsh-bridge.apk" "$OUT/unsigned.apk"

echo "⑦ Verify"
apksigner verify --print-certs "$OUT/dsh-bridge.apk" | head -4
ls -l "$OUT/dsh-bridge.apk" | awk '{printf "   Artifact: %s  %.1f KB\n", $9, $5/1024}'
