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
# Usage: 11_update-apps.sh [--dry-run] [--force]
HOME_DIR="/data/data/com.termux/files/home"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
parse_args "$@"

UPDATE="$HOME_DIR/.local/bin/dsh-update"
INSTALL="$HOME_DIR/.local/bin/dsh-install-apk"
SCREEN="$HOME_DIR/.local/bin/dsh-screen"

FORCE=0
for a in "$@"; do [ "$a" = "--force" ] && FORCE=1; done

step "① 检查仓库最新发行版"
LATEST=$("$UPDATE" latest --json 2>/dev/null) || { bad "查不到发行版（没网？）"; done_; exit 1; }
TAG=$(printf '%s' "$LATEST" | python3 -c "import json,sys;print(json.load(sys.stdin).get('tag','?'))" 2>/dev/null)
okf "仓库最新：%s" "${TAG:-?}"

# 本机版本：桥的可以从 ping 里读；控制台读不到（没有 adb 就没有 dumpsys），
# 所以控制台一律按"问一次发行版有没有比它新的"来判断 —— 判断不出就装，宁愿多装一次也不要漏。
BRIDGE_VER=$(timeout 10 "$HOME_DIR/.local/bin/droid-sock" ping --fast 2>/dev/null | sed -n 's/.*"ver": *"\([^"]*\)".*/\1/p')

step "② 下载最新 APK（并按发行版的 SHA256SUMS 校验）"
declare -A GOT
for app in console bridge; do
  if [ "$DRY" = 1 ]; then printf '   · [dry] dsh-update get %s\n' "$app"; GOT[$app]="(dry)"; continue; fi
  f=$("$UPDATE" get "$app" 2>/dev/null | tail -1)
  if [ -n "$f" ] && [ -f "$f" ]; then okf "已下载 %s" "$(basename "$f")"; GOT[$app]="$f"
  else badf "%s 下载失败" "$app"; fi
done

step "③ 安装（装之前先不息屏，用完无论成败都还原）"
if [ "$DRY" = 1 ]; then printf '   · [dry] dsh-screen keep → dsh-install-apk → dsh-screen restore\n'; ok "(dry-run)"; done_; exit 0; fi

"$SCREEN" keep || warn "没能设成不息屏，继续（装包过程中屏幕可能会灭）"
restore_screen() { "$SCREEN" restore >/dev/null 2>&1 || true; }
trap restore_screen EXIT INT TERM   # 中途失败/被 Ctrl-C 也要还原

for app in console bridge; do
  f="${GOT[$app]:-}"
  [ -n "$f" ] && [ -f "$f" ] || { warnf "跳过 %s（没下到）" "$app"; continue; }
  # 桥的当前版本能读到（ping 里有），已经最新就不折腾一次装包流程；
  # 控制台的读不到（没有 adb 就没有 dumpsys），所以照装 —— 安装器会说「已安装相同版本」并立刻结束。
  if [ "$app" = "bridge" ] && [ "$FORCE" != 1 ] && [ -n "$BRIDGE_VER" ]; then
    have=$(printf '%s' "${f##*-v}" | sed 's/\.apk$//')
    if [ "$have" = "$BRIDGE_VER" ]; then okf "桥已是最新（%s），跳过" "$BRIDGE_VER"; continue; fi
  fi
  stepf "安装 %s：%s" "$app" "$(basename "$f")"
  if timeout 280 "$INSTALL" "$f" >/dev/null 2>&1; then okf "%s 安装完成" "$app"
  else badf "%s 安装失败（见上面输出；装包页可能在等你确认）" "$app"; fi
done

restore_screen
trap - EXIT INT TERM
ok "屏幕设置已还原（不再强制常亮）"
done_
