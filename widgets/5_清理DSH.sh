#!/data/data/com.termux/files/usr/bin/bash
# 5_清理DSH —— 该删的删干净：多余备份 / **我的截图** / 视觉临时产物 / 工作区大文件 / 日志 / 包缓存
# 原则：只动「我的产物」（有固定前缀或已知临时文件名），你自己的文件一律不碰。
# 用法：5_清理DSH.sh [--dry-run] [--no-pnpm] [--keep-images N] [--keep-runs N] [--keep-full N] [--deep]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
DRY=0; DO_PNPM=0; KEEP_IMG=0; KEEP_RUNS=3; KEEP_FULL=1; DEEP=0
# 2026-09-27 改：KEEP_IMG 默认 10 → **0**（点「清理」就该把我产出的诊断截图清干净；
#   实测用户点了清理后 图片/ 里还剩 19 张，其中 9 张是我今晚截的 clash-*.png
#   因为"只认 5 个前缀"而没被识别成我的）。要留几张加 --keep-images N。
# KEEP_FULL：完整快照（dsh-full-*.tar.zst，一份约 1.1GB）默认只留最新 1 份 ——
#   以前清理脚本**只管 dsh-state-\*，从不碰 dsh-full-\***，所以 备份/ 一直是 1.2GB 清不掉。
# pnpm 缓存默认**不清**：清了会让下次装插件重新下载 400+ 包（实测 4m43s）
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY=1 ;;
    --no-pnpm) DO_PNPM=0 ;;
    --prune-store) DO_PNPM=1 ;;
    --keep-images) KEEP_IMG="${2:-10}"; shift ;;
    --keep-runs)   KEEP_RUNS="${2:-3}"; shift ;;
    --keep-full)   KEEP_FULL="${2:-1}"; shift ;;
    --deep) DEEP=1 ;;
  esac
  shift
done
[ "$DRY" = 1 ] && log "== dry-run 模式：只打印不执行 =="
BAKDIR="$DSH_DIR/备份"; IMGDIR="$DSH_DIR/图片"; RUNS="$HOME_DIR/.dsh-vision-router/artifacts/.runs"
IMG_MANIFEST="$IMGDIR/.dsh-images.list"   # 名字不匹配前缀时，登记到这里就会被清理认领
BEFORE=$(du -sm "$DSH_DIR" 2>/dev/null | cut -f1 || echo 0)
SMOKE_BEFORE=$(du -sm "$LOGS" 2>/dev/null | cut -f1 || echo 0)

step "① 备份：状态包留最新 5 份；完整快照留最新 $KEEP_FULL 份"
N=$(ls -1 "$BAKDIR"/dsh-state-*.tar.gz 2>/dev/null | wc -l)
if [ "$N" -gt 5 ]; then
  run "ls -1t '$BAKDIR'/dsh-state-*.tar.gz | tail -n +6 | xargs -r rm -f"
  ok "删除 $((N-5)) 份状态包，保留 5 份"
else ok "状态包 $N 份，未超上限"; fi
# 完整快照（dsh-snapshot 产出，一份 1.1G）：以前这里完全没管 → 备份目录一直下不去
for d in "$BAKDIR" "$BAKDIR/系统备份"; do
  [ -d "$d" ] || continue
  FN=$(ls -1 "$d"/dsh-full-*.tar.zst 2>/dev/null | wc -l)
  [ "$FN" = 0 ] && { ok "$(basename "$d")：无完整快照"; continue; }
  if [ "$FN" -gt "$KEEP_FULL" ]; then
    ls -1t "$d"/dsh-full-*.tar.zst | tail -n +$((KEEP_FULL+1)) | while read -r f; do
      b="${f%.tar.zst}"
      run "rm -f \"$f\" \"${b}.sha256\" \"${b}.说明.md\""
      ok "删除旧完整快照 $(basename "$f")（含 .sha256 / .说明.md）"
    done
  else ok "$(basename "$d")：完整快照 $FN 份，未超上限（$KEEP_FULL）"; fi
done

step "② 我的截图：识别的都清（保留 $KEEP_IMG 张）；超过 7 天的先删"
# 2026-09-27 修：以前只认 self-look-/droid-/adb-/board-/screen- 五个前缀，
#   我后来用 droid-sock shot / 自看 存的名字是 clash-*、whale-*、real-* 等 → **全被判成"你的图片"没动**。
#   现在：前缀表扩到 13 个 + 一个清单文件兜底（名字再怪，写进清单就会被认领）。
mine_list() {
  {
    find "$IMGDIR" -maxdepth 1 -type f \
      \( -name 'self-look-*' -o -name 'droid-*' -o -name 'adb-*' -o -name 'board-*' -o -name 'screen-*' \
         -o -name 'clash-*' -o -name 'whale-*' -o -name 'real-*' -o -name 'probe-*' -o -name 'shot-*' \
         -o -name 'capture-*' -o -name 'termux-*' -o -name 'dsh-*' \) 2>/dev/null
    [ -f "$IMG_MANIFEST" ] && grep -v '^[[:space:]]*#' "$IMG_MANIFEST" | grep -v '^[[:space:]]*$' | sed "s|^|$IMGDIR/|"
  } | sort -u
}
MINE_LIST="$(mine_list)"
MINE=$(printf '%s\n' "$MINE_LIST" | grep -c . || true)
TOTAL=$(find "$IMGDIR" -maxdepth 1 -type f ! -name '.*' 2>/dev/null | wc -l)   # 排除 .dsh-images.list 这类我自己的登记文件
OTHER=$((TOTAL-MINE))
if [ "$MINE" -gt 0 ]; then
  OLD=$(printf '%s\n' "$MINE_LIST" | while read -r f; do [ -n "$f" ] && [ -n "$(find "$f" -maxdepth 0 -mtime +7 2>/dev/null)" ] && printf '%s\n' "$f"; done | grep -c . || true)
  if [ "$OLD" -gt 0 ]; then
    printf '%s\n' "$MINE_LIST" | while read -r f; do
      [ -n "$f" ] && [ -n "$(find "$f" -maxdepth 0 -mtime +7 2>/dev/null)" ] && run "rm -f \"$f\""
    done
    ok "按 7 天规则删 $OLD 张"
  fi
  LEFT_LIST="$(mine_list)"
  LEFT=$(printf '%s\n' "$LEFT_LIST" | grep -c . || true)
  if [ "$LEFT" -gt "$KEEP_IMG" ]; then
    printf '%s\n' "$LEFT_LIST" | while read -r f; do
      [ -n "$f" ] && stat -c '%Y %n' "$f" 2>/dev/null
    done | sort -rn | tail -n +$((KEEP_IMG+1)) | cut -d' ' -f2- | while read -r f; do
      run "rm -f \"$f\""
    done
    ok "删除 $((LEFT-KEEP_IMG)) 张，保留最新 $KEEP_IMG 张"
  else ok "我的截图 $LEFT 张，未超上限（$KEEP_IMG）"; fi
else ok "没有我的截图"; fi
[ "$OTHER" -gt 0 ] && ok "另有你自己的图片 $OTHER 个，**未触碰**（若其中有我产出的，把文件名写进 $IMG_MANIFEST 就会被认领）"
step "③ 视觉工具临时产物：只留最新 $KEEP_RUNS 次"
if [ -d "$RUNS" ]; then
  R=$(find "$RUNS" -mindepth 1 -maxdepth 1 2>/dev/null | wc -l)
  if [ "$R" -gt "$KEEP_RUNS" ]; then
    run "find '$RUNS' -mindepth 1 -maxdepth 1 -printf '%T@ %p\\n' | sort -rn | tail -n +$((KEEP_RUNS+1)) | cut -d' ' -f2- | xargs -r rm -rf"
    ok "删除 $((R-KEEP_RUNS)) 个旧的看图运行目录（含隐藏目录）"
  else ok "现有 $R 个，未超上限"; fi
else ok "没有视觉临时目录"; fi

step "④ 工作区大文件与测试残留（~/.smoke）"
for f in readmes.json updates.json; do
  [ -f "$LOGS/$f" ] && { SZ=$(du -h "$LOGS/$f" | cut -f1); run "rm -f '$LOGS/$f'"; ok "删除 $f（$SZ，抓取时的大转储）"; }
done
for pat in 'idx*.html' 'index.html' 'c*.css' 'whale.js' 'u.xml' 'board-*.png' 'adb-shot.png' 'cj*.txt' 'ax.json' 'ax2.json' 'autox.json' 'pair-crop*.png' 'droid-ui.xml' 'probe-boot.log'; do
  for f in $LOGS/$pat; do
    [ -f "$f" ] || continue
    run "rm -f '$f'"; ok "删除测试残留 $(basename "$f")"
  done
done
if [ "$DEEP" = 1 ]; then
  for f in android.jar plugins.json platform-*.zip; do
    [ -f "$LOGS/$f" ] && { SZ=$(du -h "$LOGS/$f" | cut -f1); run "rm -f '$LOGS/$f'"; ok "深度清理 $f（$SZ，需要时可重新下载）"; }
  done
else
  [ -f "$LOGS/android.jar" ] && ok "保留 android.jar（改 App 时要重新编译）"
  [ -f "$LOGS/plugins.json" ] && ok "保留 plugins.json（查插件市场用）"
fi
# 冒烟测试日志：只留最新 3 个
BOOTS=$(find "$LOGS" -maxdepth 1 -name 'boot*.log' 2>/dev/null | wc -l)
if [ "$BOOTS" -gt 3 ]; then
  run "find '$LOGS' -maxdepth 1 -name 'boot*.log' -printf '%T@ %p\\n' | sort -rn | tail -n +4 | cut -d' ' -f2- | xargs -r rm -f"
  ok "冒烟日志只留最新 3 个（删 $((BOOTS-3)) 个）"
else ok "冒烟日志 $BOOTS 个，未超上限"; fi

for f in "$LOGS"/*.log; do
  [ -f "$f" ] || continue
  SZ=$(( $(stat -c%s "$f") / 1024 ))
  [ "$SZ" -gt 512 ] && { run ": > '$f'"; ok "清空日志 $(basename "$f")（${SZ}KB）"; }
done
run "find '$LOGS' -maxdepth 1 -name '*.log.bak' -mtime +14 -delete 2>/dev/null || true"

step "⑤ 认证 URL 落盘（删日志前先保存）"
URLFILE="$HOME_DIR/.dsh-url"
if grep -oE "http://127\\.0\\.0\\.1:${DSH_PORT}/\\?token=[A-Za-z0-9_-]+" "$(dsh_log)" 2>/dev/null | tail -1 > "$URLFILE.tmp" && [ -s "$URLFILE.tmp" ]; then
  mv -f "$URLFILE.tmp" "$URLFILE"; ok "已保存 $(basename "$URLFILE")"
else
  rm -f "$URLFILE.tmp"; [ -f "$URLFILE" ] && ok "沿用已有 $(basename "$URLFILE")" || warn "没有可保存的 token URL"
fi

step "⑥ 日志轮换 + 包管理器缓存（默认不动 pnpm 缓存）"
rotate_log "$(dsh_log)" 2
if [ "$DO_PNPM" = 1 ] && need pnpm; then
  [ "$DRY" = 1 ] && printf '   · [dry] pnpm store prune\n' || { R=$(timeout 180 pnpm store prune 2>&1 | tail -1); ok "${R:-已清理}"; }
else ok "跳过 pnpm 缓存清理（保留缓存，下次装插件才快；要清加 --prune-store）"; fi

step "⑦ 结果"
AFTER=$(du -sm "$DSH_DIR" 2>/dev/null | cut -f1 || echo 0)
SMOKE_AFTER=$(du -sm "$LOGS" 2>/dev/null | cut -f1 || echo 0)
printf '   Download/dsh：%sMB → %sMB（释放 %sMB）\n' "$BEFORE" "$AFTER" "$((BEFORE-AFTER))"
printf '   ~/.smoke    ：%sMB → %sMB（释放 %sMB）\n' "$SMOKE_BEFORE" "$SMOKE_AFTER" "$((SMOKE_BEFORE-SMOKE_AFTER))"
# 2026-09-27 加：把"备份目录里到底是什么"说清楚 —— 以前只报"备份 N 份"，
# 用户看到 1.2G 清不掉会以为清理坏了，其实是那份换机用的完整快照占的。
STATE_N=$(ls -1 "$BAKDIR"/dsh-state-*.tar.gz 2>/dev/null | wc -l)
FS_N=$(ls -1 "$BAKDIR"/dsh-full-*.tar.zst "$BAKDIR"/系统备份/dsh-full-*.tar.zst 2>/dev/null | wc -l)
FS_H=$(du -ch "$BAKDIR"/dsh-full-*.tar.zst "$BAKDIR"/系统备份/dsh-full-*.tar.zst 2>/dev/null | tail -1 | cut -f1)
BAK_H=$(du -sh "$BAKDIR" 2>/dev/null | cut -f1)
MINE_LEFT=$(mine_list | grep -c . || true)
ALL_LEFT=$(find "$IMGDIR" -maxdepth 1 -type f ! -name '.*' 2>/dev/null | wc -l)
OTHER_LEFT=$((ALL_LEFT-MINE_LEFT))
printf '   备份目录 %s：状态包 %s 份（上限 5）＋ 完整快照 %s 份（%s）\n' "${BAK_H:-?}" "$STATE_N" "$FS_N" "${FS_H:-无}"
printf '     ↳ 完整快照＝换机/重装用的整机归档（备份/系统备份/dsh-full-*.tar.zst）；嫌占地方删掉它，或加 --keep-full 0\n'
printf '   我的截图 %s 张（上限 %s）／ 你自己的图片 %s 个（未触碰）\n' "$MINE_LEFT" "$KEEP_IMG" "$OTHER_LEFT"
done_
