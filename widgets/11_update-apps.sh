#!/data/data/com.termux/files/usr/bin/bash
# 11_update-apps — 一键把控制台和桥更新到仓库最新发行版
#
# 为什么要有它：两个 App 打开时会自己查一次「仓库有没有新版本」，但检测出来之后用户还得
# 自己下 APK、自己点安装。这一步把「查 → 下 → 装」串成一次点击，并且：
#   · 下载后**校验 SHA256**（发行版自带 SHA256SUMS，下载成功 ≠ 文件是对的）
#   · 装之前先把屏幕设成不息屏、装完**无论成败都还原**（这一条是真踩过：一次装包跑到一半
#     屏幕自己灭了，安装页被锁屏盖住，整轮白跑）
#   · 已经是最新就什么都不做，不浪费一次装包流程
#
# 2026-09-28 加「不降级」闸门。为什么：本脚本原来只会"下载仓库最新发行版 → 装"，
# **从不比较两边版本**，于是真出过「把更旧的 APK 盖到本机更新的版本上」——
# 手机上装的是自己编的 v2.21，脚本把发行版里的 v2.20 装了 进去。现在先定版本再下载：
#   仓库 > 本机 → 装    仓库 = 本机 → 跳过（已是最新）    仓库 < 本机 → **不降级**（要装得显式 --force）
#   版本读不到   → 跳过并说明（宁可不动，也不把新的盖成旧的）
#
# Usage: 11_update-apps.sh [--dry-run] [--force] [--console-ver X.Y]
#   --console-ver  控制台自己把自己的版本传进来（App 最清楚自己装的是哪一版）；
#                  不给就用 adb 读系统里那个包的 versionName。
HOME_DIR="${DSH_HOME_DIR:-/data/data/com.termux/files/home}"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"

FORCE=0; CONSOLE_VER=""
# `parse_args` 只认 --dry-run，这里连它一起自己解析（DRY 要尽早生效，后面每步都要看它）
while [ $# -gt 0 ]; do
  case "$1" in
    --dry-run)       DRY=1 ;;
    --force)         FORCE=1 ;;
    --console-ver)   shift; CONSOLE_VER="${1:-}" ;;
    --console-ver=*) CONSOLE_VER="${1#*=}" ;;
  esac
  shift
done
[ "$DRY" = 1 ] && log "== dry-run mode: print only, do not execute =="

UPDATE="$HOME_DIR/.local/bin/dsh-update"
INSTALL="$HOME_DIR/.local/bin/dsh-install-apk"
SCREEN="$HOME_DIR/.local/bin/dsh-screen"
DROID="$HOME_DIR/.local/bin/droid"

step "① 检查最新发行版"
LATEST=$("$UPDATE" latest --json 2>/dev/null) || { bad "查不到发行版（没网？）"; done_; exit 1; }
TAG=$(printf '%s' "$LATEST" | python3 -c "import json,sys;print(json.load(sys.stdin).get('tag','?'))" 2>/dev/null)
okf "发行版最新：%s" "${TAG:-?}"

# ── 本机版本：一个事实一个来源 ────────────────────────────────────────────
#   控制台 → 优先用 App 自己传进来的 --console-ver；小部件路径没传就读系统里的包
#   桥     → 优先用它自己 ping 回来的 ver（不用 adb，最可靠）；桥没起再读系统里的包
pkg_ver() {   # $1=包名 → 系统里那个包当前的 versionName（没有 adb 或没装就是空）
  timeout 25 "$DROID" shell dumpsys package "$1" 2>/dev/null \
    | sed -n 's/.*versionName=\([0-9][0-9.]*\).*/\1/p' | head -1
}
BRIDGE_VER=$(timeout 10 "$HOME_DIR/.local/bin/droid-sock" ping --fast 2>/dev/null | sed -n 's/.*"ver": *"\([^"]*\)".*/\1/p')
[ -n "$BRIDGE_VER" ] || BRIDGE_VER=$(pkg_ver io.dsh.bridge)
[ -n "$CONSOLE_VER" ] || CONSOLE_VER=$(pkg_ver io.dsh.console)
[ -n "$CONSOLE_VER" ] && okf "控制台本机版本：%s" "$CONSOLE_VER"
[ -n "$BRIDGE_VER" ] && okf "桥本机版本：%s" "$BRIDGE_VER"

# 远端最新 vs 本机 → 打印 "install|current|ahead|unknown [远端版本]"
verdict() {   # $1=app  $2=本机版本
  # 本机版本读不到就**直接 unknown**，绝不拿 0 去比 —— 拿 0 比出来的永远是"仓库更新，装吧"，
  # 那正是这个闸门要防的那件事（用假数据把"不知道"变成"可以装"）。要装得显式 --force。
  [ -n "$2" ] || { printf 'unknown\n'; return 0; }
  "$UPDATE" check --app "$1" --current "$2" --json 2>/dev/null | python3 -c "
import json, re, sys
def t(v):
    m = re.findall(r'\d+', v or '')
    return tuple(int(x) for x in m) if m else (0,)
try:
    d = json.load(sys.stdin)
except Exception:
    print('unknown'); raise SystemExit
latest, cur = d.get('latest_app') or '', d.get('current') or ''
if not latest:            print('unknown')
elif t(latest) > t(cur):  print('install ' + latest)
elif t(latest) == t(cur): print('current ' + latest)
else:                     print('ahead ' + latest)
"
}

step "② 定版本：只有仓库比本机新才下载安装（本机领先就绝不降级）"
declare -A GOT
for app in console bridge; do
  case "$app" in console) LOCAL="$CONSOLE_VER" ;; bridge) LOCAL="$BRIDGE_VER" ;; esac
  read -r vd vlatest <<<"$(verdict "$app" "$(printf '%s' "$LOCAL")")"
  case "$vd" in
    install)
      okf "%s：本机 %s → 发行版 %s，要更新" "$app" "${LOCAL:-未知}" "${vlatest:-?}" ;;
    current)
      okf "%s 已是最新（v%s），跳过" "$app" "$vlatest" ;;
    ahead)
      if [ "$FORCE" = 1 ]; then
        warnf "%s：本机 v%s **领先**发行版 v%s，--force 强制装（等于降级）" "$app" "$LOCAL" "$vlatest"
      else
        warnf "%s：本机 v%s 领先发行版 v%s，**不降级**，跳过（确实要装就加 --force）" "$app" "$LOCAL" "$vlatest"
        continue
      fi ;;
    *)
      warnf "%s：读不到可比对的版本（本机 %s）→ 跳过，避免把新的盖成旧的（要装就加 --force）" "$app" "${LOCAL:-未知}"
      [ "$FORCE" = 1 ] || continue ;;
  esac
  # 走到这里才下载：决定不装的就别浪费一次下载
  if [ "$DRY" = 1 ]; then printf '   · [dry] dsh-update get %s\n' "$app"; GOT[$app]="(dry)"; continue; fi
  f=$("$UPDATE" get "$app" 2>/dev/null | tail -1)
  if [ -n "$f" ] && [ -f "$f" ]; then okf "已下载 %s" "$(basename "$f")"; GOT[$app]="$f"
  else badf "%s 下载失败" "$app"; fi
done

step "③ 安装（装之前先不息屏，用完无论成败都还原）"
if [ "$DRY" = 1 ]; then printf '   · [dry] dsh-screen keep → dsh-install-apk → dsh-screen restore\n'; ok "(dry-run)"; done_; exit 0; fi

if [ "${#GOT[@]}" = 0 ]; then ok "没有需要安装的（两个都已是本机最新）"; done_; exit 0; fi

"$SCREEN" keep || warn "没能设成不息屏，继续（装包过程中屏幕可能会灭）"
restore_screen() { "$SCREEN" restore >/dev/null 2>&1 || true; }
trap restore_screen EXIT INT TERM   # 中途失败/被 Ctrl-C 也要还原

for app in console bridge; do
  f="${GOT[$app]:-}"
  [ -n "$f" ] && [ -f "$f" ] || { warnf "跳过 %s（没下到）" "$app"; continue; }
  # 版本闸门已经在 ② 定过了，这里只管装 —— 装完再复核一次系统里的版本，确认真的生效
  want=$(printf '%s' "${f##*-v}" | sed 's/\.apk$//')
  stepf "安装 %s：%s（目标 v%s）" "$app" "$(basename "$f")" "$want"
  if timeout 280 "$INSTALL" "$f" >/dev/null 2>&1; then okf "%s 安装完成" "$app"
  else badf "%s 安装失败（见上面输出；装包页可能在等你确认）" "$app"; fi
done

restore_screen
trap - EXIT INT TERM
ok "屏幕设置已还原（不再强制常亮）"
done_
