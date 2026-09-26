#!/data/data/com.termux/files/usr/bin/bash
# 0_紧急停止 —— 一键撤销 AI 对这台手机的全部控制（不看屏幕、不点按钮也能用）
# 撤销四路：① DSH 桥（无障碍）② token ③ 共享目录指令通道 ④ adb 无线调试
# 用法：0_紧急停止.sh [--dry-run]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
parse_args "$@"
cd "$SHARED" 2>/dev/null || cd "$HOME_DIR"
[ -f "$HOME_DIR/.bashrc" ] && . "$HOME_DIR/.bashrc" >/dev/null 2>&1

log "紧急停止：目标是把 AI 的四条控制路径全部切断"

step "① DSH 桥（无障碍服务）"
if [ "$DRY" = 1 ]; then printf '   · [dry] droid-sock panic\n'
  ok "（dry-run）"
elif [ -x "$HOME_DIR/.local/bin/droid-sock" ] && timeout 8 "$HOME_DIR/.local/bin/droid-sock" panic >/dev/null 2>&1; then
  ok "桥已自杀：端口关闭 + 无障碍自行关闭"
else
  warn "桥没有响应（可能本来就停着）"
fi

step "② 吊销 token"
if [ -f "$HOME_DIR/.dsh-bridge-token" ]; then
  run "mv -f '$HOME_DIR/.dsh-bridge-token' '$HOME_DIR/.dsh-bridge-token.revoked-$(date +%s)'"
  ok "token 已吊销（改名保留备查）"
else
  ok "没有 token 文件，无需吊销"
fi

step "③ 清空共享目录指令通道（AutoX 桥 / 页面遥控）"
run ": > '$SHARED/dsh-droid/cmd.json' 2>/dev/null || true"
run ": > '$HOME_DIR/.dsh-look-cmd.json' 2>/dev/null || true"
ok "指令文件已清空"

step "④ 切断 adb 无线调试（这是最强的一条通道）"
if need adb && adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then
  run "timeout 10 adb usb >/dev/null 2>&1 || true"
  ok "adbd 已切回 USB 模式（TCP 监听消失）"
  run "timeout 10 adb disconnect >/dev/null 2>&1 || true"
  run "adb kill-server >/dev/null 2>&1 || true"
  ok "adb 连接已断开、服务端已停"
else
  warn "adb 当前没连着（已连的会被下面的收尾一并处理）"
  run "adb kill-server >/dev/null 2>&1 || true"
fi

step "⑤ 结束我这边可能挂着的自动化进程"
K=0
for pat in "auto-look.sh" "droid-hub" "dsh-control" "dsh-eval" "pair-now" "deep-nav" "adb-recon"; do
  for pid in $(ps -ef 2>/dev/null | grep "[${pat:0:1}]${pat:1}" | awk '{print $2}'); do
    kill "$pid" 2>/dev/null && K=$((K+1))
  done
done
ok "已结束 $K 个相关进程"

step "⑥ 打开无障碍设置页，方便你一眼确认"
if [ "$DRY" = 1 ]; then printf '   · [dry] am start 无障碍设置\n'; else am start -a android.settings.ACCESSIBILITY_SETTINGS >/dev/null 2>&1; fi

step "自检"
sleep 1
port_open 8788 && bad "8788 还在监听（异常）" || ok "8788 已关闭"
adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q . && warn "还有 adb 设备连着（重启后必然归零）" || ok "adb 无已连接设备"
printf '\n如果手机仍在自己动，按顺序：音量+/- 按住3秒 → 通知栏紧急停止 → 重启手机 → 安全模式\n自救卡：%s/dsh/文档/手机失控自救卡.md\n' "$DL"
done_
