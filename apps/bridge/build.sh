#!/data/data/com.termux/files/usr/bin/bash
# Build the DSH Bridge APK (Termux only, no Android Studio)
set -e
SRC="$(cd "$(dirname "$0")" && pwd)"     # this script's own folder: work here, wherever the repo is
KIT="$(cd "$SRC/../.." && pwd)"          # repo root (apps/bridge -> ../..)
AJ="${DSH_AJ:-$HOME/.smoke/android.jar}" # Android platform jar, needed by aapt2 link / javac / d8
OUT="$SRC/build"

# ── 依赖检查（2026-10-01 加）──
# 为什么：以前这里是 $HOME/droid-bridge 和 $HOME/dsh-termux-kit 两个写死的路径，仓库放到别处
# （或从 GitHub clone 下来）就必然失败，报的是半路一句 "command not found"，看不出缺什么。
for t in aapt2 javac d8 zip keytool apksigner; do
  command -v "$t" >/dev/null 2>&1 || {
    echo "✘ 缺 $t —— 编译 APK 需要 aapt2 / javac / d8 / zip / keytool / apksigner 都在 PATH 上"
    echo "   Termux 里装 Android 构建工具后再跑一次；不打算编译两个 App 的话，直接用发行版里的 APK。"
    exit 1; }
done
if [ ! -f "$AJ" ]; then
  echo "✘ 缺 android.jar：$AJ"
  echo "   aapt2 link -I、javac -classpath、d8 --lib 都要它。它不在本仓库里，取法二选一："
  echo "   ① 在装了 Android SDK 的电脑上取 \$ANDROID_HOME/platforms/android-34/android.jar，"
  echo "      复制到手机的 \$HOME/.smoke/android.jar；"
  echo "   ② 已经有这个文件时，用环境变量指过来：DSH_AJ=/path/to/android.jar bash apps/bridge/build.sh"
  exit 1
fi

rm -rf "$OUT"; mkdir -p "$OUT/classes" "$OUT/dex" "$OUT/gen"

# ── 出包门禁（2026-09-27 加）──
# 为什么：上一轮改完文案**没有重新生成/重新出包**，手机上跑的仍是旧表 —— 用户看到的是
# "上一轮的要求根本没做到"（channels ×3、auto 打头、up to date 全是英文）。
# 现在编译前强制：① 从 ui/controls.json + ui/theme.json 重新生成三个产物；
# ② 跑 tools/i18n-audit（字面量调用 / 中文覆盖 / 主题一致 / 色板一致 / 状态口径）。
# 任一项不过就直接中止，不许产出一个"看起来修好了"的包。
if [ -x "$KIT/tools/ui-controls" ]; then
  python3 "$KIT/tools/ui-controls" gen || exit 1
  python3 "$KIT/tools/i18n-audit" --quiet || {
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
# ── 密钥库查找（2026-10-03 加）──
# 为什么：ks.jks 在 .gitignore 里（*.jks），只存在于**当初出包的那个目录**。构建目录一变
# （真源码 ~/droid-bridge → 仓库里的 apps/bridge），这里就会**悄悄生成一把新钥匙**，
# 新包签名和手机上已装的那份对不上，安装器直接拒绝覆盖（只报 "App not installed"，
# 不解释原因）。现在按顺序找一把**已经存在**的密钥库，全都没有才新建，并打印用的是哪一个。
KS="${DSH_KS:-}"
if [ -z "$KS" ]; then
  for c in "$SRC/ks.jks" "$HOME/droid-bridge/ks.jks" "$HOME/.dsh-bridge/ks.jks"; do
    [ -f "$c" ] && { KS="$c"; break; }
  done
fi
[ -n "$KS" ] || KS="$SRC/ks.jks"
if [ ! -f "$KS" ]; then
  keytool -genkeypair -keystore "$KS" -alias dsh -keyalg RSA -keysize 2048 \
    -validity 10000 -storepass dshbridge -keypass dshbridge \
    -dname "CN=DSH Bridge,O=DSH,C=CN" >/dev/null 2>&1
  echo "   Generated self-signed key $KS (password dshbridge) —— 以后一直用它，别丢"
else
  echo "   Keystore: $KS"
fi
apksigner sign --ks "$KS" --ks-pass pass:dshbridge --key-pass pass:dshbridge \
  --out "$OUT/dsh-bridge.apk" "$OUT/unsigned.apk"

echo "⑦ Verify"
apksigner verify --print-certs "$OUT/dsh-bridge.apk" | head -4
ls -l "$OUT/dsh-bridge.apk" | awk '{printf "   Artifact: %s  %.1f KB\n", $9, $5/1024}'
