#!/data/data/com.termux/files/usr/bin/bash
# 7_重连AI通道 —— 重启手机后跑这个：重连 adb + 唤醒 DSH 桥，并报告三条通道状态
# 前提：adb 需要你在开发者选项里打开「无线调试」（配对记录已保留，不用再输配对码）
# ⚠️ 实测：Wi-Fi 一旦断开，安卓会自动关掉「无线调试」→ adb 随之失效（必须由用户重新打开）
# 用法：7_重连AI通道.sh [--dry-run]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
parse_args "$@"
cd "$SHARED" 2>/dev/null || cd "$HOME_DIR"
[ -f "$HOME_DIR/.bashrc" ] && . "$HOME_DIR/.bashrc" >/dev/null 2>&1

step "① adb 通道"
if adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then
  ok "已连接：$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
else
  warn "未连接 → 先调用「8_自动开无线调试」（联网时自动把开关打开）"
  if [ -x "$SELF_DIR/8_自动开无线调试.sh" ]; then
    bash "$SELF_DIR/8_自动开无线调试.sh" $([ "$DRY" = 1 ] && echo --dry-run) 2>&1 | sed 's/^/     /' | tail -12 || true
  fi
  if ! adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then
    warn "自动开启没成功，退回手工路径：试固定端口 5555 → mDNS 找端口"
    if [ -x "$HOME_DIR/.local/bin/droid" ]; then
      [ "$DRY" = 1 ] || timeout 40 "$HOME_DIR/.local/bin/droid" conn >/dev/null 2>&1
      if ! adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then
        P=$(timeout 30 "$HOME_DIR/.local/bin/droid" discover 2>/dev/null | grep -oE 'port=[0-9]+' | head -1 | cut -d= -f2)
        if [ -n "$P" ] && [ "$DRY" != 1 ]; then timeout 25 adb connect "127.0.0.1:$P" >/dev/null 2>&1; fi
      fi
    fi
    if adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then
      ok "已连上：$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
    else
      bad "adb 仍连不上：请确认 Wi-Fi 已连 + 桥 App 已授予 WRITE_SECURE_SETTINGS"
    fi
  else
    ok "自动开启成功"
  fi
fi

step "② DSH 桥通道"
if port_open 8788; then ok "桥在监听 127.0.0.1:8788"
else
  [ "$DRY" = 1 ] || bridge_wake
  bridge_alive && ok "广播唤醒成功（带 token）" || warn "桥没起来：token 被吊销 或 无障碍被关（设置→无障碍→DSH 桥）"
fi

step "③ 状态总览"
printf '   DSH Web : %s\n' "$(port_open "$DSH_PORT" && echo "在跑（HTTP $(http_code "$DSH_PORT")）" || echo '未运行')"
printf '   adb     : %s\n' "$(adb devices 2>/dev/null | awk 'NR>1 && $2=="device"{print $1; exit}' || echo '未连接')"
printf '   桥      : %s\n' "$(port_open 8788 && echo '监听中' || echo '未监听')"
printf '   提示    : 「无线调试」必须在以下时机由你打开一次：重启手机后 / Wi-Fi 断过之后（安卓会在 Wi-Fi 断开时自动关掉它）\n'
done_
