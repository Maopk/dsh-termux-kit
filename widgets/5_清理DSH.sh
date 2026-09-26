#!/data/data/com.termux/files/usr/bin/bash
# 5_清理DSH —— 该删的删干净：多余备份 / **我的截图** / 视觉临时产物 / 工作区大文件 / 日志 / 包缓存
# 原则：只动「我的产物」（有固定前缀或已知临时文件名），你自己的文件一律不碰。
# 用法：5_清理DSH.sh [--dry-run] [--no-pnpm] [--keep-images N] [--keep-runs N] [--deep]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
DRY=0; DO_PNPM=0; KEEP_IMG=10; KEEP_RUNS=3; DEEP=0   # pnpm 缓存默认**不清**：清了会让下次装插件重新下载 400+ 包（实测 4m43s）
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run) DRY=1 ;;
    --no-pnpm) DO_PNPM=0 ;;
    --prune-store) DO_PNPM=1 ;;
    --keep-images) KEEP_IMG="${2:-10}"; shift ;;
    --keep-runs)   KEEP_RUNS="${2:-3}"; shift ;;
    --deep) DEEP=1 ;;
  esac
  shift
done
[ "$DRY" = 1 ] && log "== dry-run 模式：只打印不执行 =="
BAKDIR="$DSH_DIR/备份"; IMGDIR="$DSH_DIR/图片"; RUNS="$HOME_DIR/.dsh-vision-router/artifacts/.runs"
BEFORE=$(du -sm "$DSH_DIR" 2>/dev/null | cut -f1 || echo 0)
SMOKE_BEFORE=$(du -sm "$LOGS" 2>/dev/null | cut -f1 || echo 0)

step "① 备份：只保留最新 5 份（手动清理规则）"
N=$(ls -1 "$BAKDIR"/dsh-state-*.tar.gz 2>/dev/null | wc -l)
if [ "$N" -gt 5 ]; then
  run "ls -1t '$BAKDIR'/dsh-state-*.tar.gz | tail -n +6 | xargs -r rm -f"
  ok "删除 $((N-5)) 份，保留 5 份"
else ok "现有 $N 份，未超上限"; fi

step "② 我的截图：只留最新 $KEEP_IMG 张，且超过 7 天的直接删"
# 只认我的前缀，你自己的图片不会被碰
MINE=$(find "$IMGDIR" -maxdepth 1 -type f \( -name 'self-look-*' -o -name 'droid-*' -o -name 'adb-*' -o -name 'board-*' -o -name 'screen-*' \) 2>/dev/null | wc -l)
OTHER=$(find "$IMGDIR" -maxdepth 1 -type f 2>/dev/null | wc -l)
OTHER=$((OTHER-MINE))
if [ "$MINE" -gt 0 ]; then
  OLD=$(find "$IMGDIR" -maxdepth 1 -type f -mtime +7 \( -name 'self-look-*' -o -name 'droid-*' -o -name 'adb-*' -o -name 'board-*' -o -name 'screen-*' \) 2>/dev/null | wc -l)
  [ "$OLD" -gt 0 ] && { run "find '$IMGDIR' -maxdepth 1 -type f -mtime +7 \\( -name 'self-look-*' -o -name 'droid-*' -o -name 'adb-*' -o -name 'board-*' -o -name 'screen-*' \\) -delete"; ok "按 7 天规则删 $OLD 张"; }
  LEFT=$(find "$IMGDIR" -maxdepth 1 -type f \( -name 'self-look-*' -o -name 'droid-*' -o -name 'adb-*' -o -name 'board-*' -o -name 'screen-*' \) 2>/dev/null | wc -l)
  if [ "$LEFT" -gt "$KEEP_IMG" ]; then
    run "find '$IMGDIR' -maxdepth 1 -type f \\( -name 'self-look-*' -o -name 'droid-*' -o -name 'adb-*' -o -name 'board-*' -o -name 'screen-*' \\) -printf '%T@ %p\\n' | sort -rn | tail -n +$((KEEP_IMG+1)) | cut -d' ' -f2- | xargs -r rm -f"
    ok "超量裁剪：删 $((LEFT-KEEP_IMG)) 张，保留最新 $KEEP_IMG 张"
  else ok "我的截图 $LEFT 张，未超上限"; fi
else ok "没有我的截图"; fi
[ "$OTHER" -gt 0 ] && ok "另有你自己的图片 $OTHER 个，**未触碰**"

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
printf '   备份 %s 份 / 我的截图 %s 张 / 你的图片 %s 个\n' \
  "$(ls -1 "$BAKDIR"/dsh-state-*.tar.gz 2>/dev/null | wc -l)" \
  "$(find "$IMGDIR" -maxdepth 1 -type f \( -name 'self-look-*' -o -name 'droid-*' -o -name 'adb-*' \) 2>/dev/null | wc -l)" \
  "$(find "$IMGDIR" -maxdepth 1 -type f ! -name 'self-look-*' ! -name 'droid-*' ! -name 'adb-*' ! -name 'board-*' ! -name 'screen-*' 2>/dev/null | wc -l)"
done_
