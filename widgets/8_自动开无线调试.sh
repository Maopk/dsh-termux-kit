#!/data/data/com.termux/files/usr/bin/bash
# 8_自动开无线调试 —— 联网时自动把「无线调试」打开并接上 adb（全程不需要你点开关）
#
# 原理：adb 的 TCP 监听依附于「无线调试」服务，而该服务在 Wi-Fi 断开时会被系统自动关闭。
#      本脚本通过 DSH 桥（无障碍 App，走回环、不需要网络）调用 Settings.Global.putInt
#      把 adb_wifi_enabled 置 1 —— 这就是设置里那个开关对应的值。
#      唯一前提：桥 App 已被授予 WRITE_SECURE_SETTINGS（一次性，用 adb pm grant）。
#
# 用法：8_自动开无线调试.sh [--dry-run] [--force] [--no-ui]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
SOCK="$HOME_DIR/.local/bin/droid-sock"
DRY=0; FORCE=0; NO_UI=0
for a in "$@"; do case "$a" in --dry-run) DRY=1 ;; --force) FORCE=1 ;; --no-ui) NO_UI=1 ;; esac; done
[ "$DRY" = 1 ] && log "== dry-run 模式：只打印不执行 =="

bridge_alive() { port_open 8788; }
# 唤醒必须带 token（v1.7 起：服务被关掉时只有带对 token 的广播才允许重新授权无障碍）
wake_bridge() { bridge_wake; }

step "① 桥通道（回环，不需要 Wi-Fi）"
if bridge_alive; then ok "桥在监听 127.0.0.1:8788"
else
  warn "桥没在听，发唤醒广播"
  wake_bridge
  bridge_alive && ok "桥已唤醒" || die "桥起不来——请确认：设置→无障碍→DSH 桥 已开启"
fi

step "② 联网状态（桥实测：直连 1.1.1.1:443）"
NET=$($SOCK netstate 2>/dev/null || true)
echo "$NET" | sed 's/^/   /'
WIFI_ON=$(echo "$NET" | grep -oE '"wifi_on": *-?[0-9]+' | grep -oE '\-?[0-9]+$')
ONLINE=$(echo "$NET" | grep -oE '"online": *(true|false)' | grep -oE '(true|false)$')
ADB_WIFI=$(echo "$NET" | grep -oE '"adb_wifi": *-?[0-9]+' | grep -oE '\-?[0-9]+$')
[ "$WIFI_ON" = "1" ] && ok "Wi-Fi 开着" || warn "Wi-Fi 没开（无线调试必须依赖 Wi-Fi；Wi-Fi 开关只能你自己开）"
[ "$ONLINE" = "true" ] && ok "真的能上网" || warn "没测到外网连通性（仍会尝试打开开关）"
case "$ADB_WIFI" in 1) ok "「无线调试」当前已是开启" ;; 0) warn "「无线调试」当前是关闭" ;; *) warn "读不到 adb_wifi_enabled（可能缺权限）" ;; esac

# Wi-Fi 关着时，adbd 的 TCP 监听**不可能**起来：
#   ① 无线调试跑在 wlan 接口上；② 而且 Wi-Fi 一关，Android 会把 adb_wifi_enabled 直接清回 0
#      （实测：手动置 1 之后读回来又变 0）——所以"硬置开关"这条路是死的。
# 但 Android 10+ 又不允许 App 直接开 Wi-Fi。于是这里改成**半自动**（2026-09-26 晚）：
#   把 Wi-Fi 设置页打开 → 你点一下 Wi-Fi → 本组件**自动检测到并继续**跑完剩下全部步骤。
# 只要你点一下，不用再点第二次；不想让它碰屏幕就加 --no-ui。
if [ "$WIFI_ON" = "0" ] && [ "$FORCE" = 0 ]; then
  if [ "$NO_UI" = 1 ]; then
    step "③ 前置条件不满足（--no-ui：完全不碰屏幕）"
    bad "Wi-Fi 是关的 → adb 的 TCP 监听不会出现"
    printf '   自己打开 Wi-Fi 后重跑本组件，或点「7_重连AI通道」\n'
    printf '\n【%s】未执行：等待 Wi-Fi（用时 %ss）\n' "$SELF" "$(( $(date +%s) - T0 ))"
    exit 3
  fi
  step "③ Wi-Fi 关着 → 打开 Wi-Fi 页并自动等你打开（最多 90 秒）"
  warn "Android 10+ 不允许 App 直接开 Wi-Fi，这一步只能你点一下（没有别的办法）"
  if [ "$DRY" = 1 ]; then printf '   · [dry] am start -a android.settings.WIFI_SETTINGS\n'; ok "（dry-run）"
  else
    am start -a android.settings.WIFI_SETTINGS >/dev/null 2>&1 && ok "已打开 Wi-Fi 设置页" || warn "没能打开设置页（自己下拉快捷开关也行）"
  fi
  printf '   · 你打开 Wi-Fi 后本组件会**自动继续**，不用再点我\n'
  W=0
  for i in $(seq 1 90); do
    [ "$DRY" = 1 ] && break
    N2=$(timeout 12 "$SOCK" netstate 2>/dev/null || true)
    W=$(printf '%s' "$N2" | grep -oE '"wifi_on": *-?[0-9]+' | grep -oE '\-?[0-9]+$')
    [ "$W" = "1" ] && { ok "Wi-Fi 已开（等了 ${i}s），继续往下走"; break; }
    sleep 1
  done
  if [ "$DRY" != 1 ] && [ "$W" != "1" ]; then
    warn "90 秒内没等到 Wi-Fi 打开"
    printf '\n【%s】未执行：等待 Wi-Fi 超时（用时 %ss）\n' "$SELF" "$(( $(date +%s) - T0 ))"
    exit 3
  fi
fi

step "③ 打开「无线调试」开关"
if [ "$ADB_WIFI" = "1" ] && [ "$FORCE" = 0 ]; then
  ok "已是开启，跳过（要强制重写加 --force）"
else
  if [ "$DRY" = 1 ]; then printf '   · [dry] %s adbwifi 1\n' "$SOCK"
  else
    R=$($SOCK adbwifi 1 2>&1 | tail -1); echo "   $R"
    case "$R" in *'"adb_wifi": 1'*) ok "开关已置 1" ;; *) warn "写入结果不明确：$R" ;; esac
  fi
fi

# ③·校验：**写设置 ≠ adbd 真的起来**。2026-09-26 实测：桥把 adb_wifi_enabled 置 1，
# 过一会儿系统又清回 0（这一版系统只认「开发者选项 → 无线调试」里那次手动打开）。
# 不回读就会白扫 3 万个端口、最后报一句和事实相反的"网络不通"——所以这里先确认。
if [ "$DRY" != 1 ]; then
  A4=""; O4=""
  for i in 1 2 3; do
    N4=$(timeout 12 "$SOCK" netstate 2>/dev/null || true)
    A4=$(printf '%s' "$N4" | grep -oE '"adb_wifi": *-?[0-9]+' | grep -oE '\-?[0-9]+$')
    [ "$A4" = "1" ] && break
    sleep 1
  done
  O4=$(printf '%s' "${N4:-}" | grep -oE '"online": *(true|false)' | grep -oE '(true|false)$')
  if [ "$A4" != "1" ]; then
    step "③·校验：开关没保住（adb_wifi=${A4:-?}）"
    bad "写完设置又被清回 0 —— 光写 Settings.Global 不足以让 adbd 起来"
    if [ "$O4" = "true" ]; then
      printf '   网络是通的（online=true）→ **不是网络问题**：这一版系统只认「开发者选项 → 无线调试」里手动打开的那次\n'
      printf '   要做的：设置 → 开发者选项 → 无线调试 → 打开一次（约 20 秒；之后本组件才能自动维持）\n'
    else
      printf '   而且网络也不通（online=%s）→ 无线调试依附"可用的 Wi-Fi 网络"，先连上 AP 再说\n' "${O4:-?}"
      printf '   要做的：连上一个 Wi-Fi（不是只拨开关），再重跑本组件\n'
    fi
    printf '\n【%s】未完成：无线调试开关没保住（用时 %ss）\n' "$SELF" "$(( $(date +%s) - T0 ))"
    exit 3
  fi
  ok "回读确认 adb_wifi=1（继续找端口）"
fi

step "④ 找无线调试端口（mDNS → 失败就本地扫端口）"
# 为什么不能只靠 mDNS：dt=没有真正连上网络时组播发现什么都看不到（实测踩到），
# 但 adbd 往往照样在回环上听着 —— 直接扫本地端口更可靠。
PORT=""; CANDS=""
if [ "$DRY" = 1 ]; then printf '   · [dry] 先看固定端口 5555 → mDNS → 再扫 30000-60999\n'
else
  # 0) 固定端口优先：跑过 `adb tcpip 5555` 之后 adbd 就在 5555 上，
  #    而且它**不在** 30000-60999 的扫描区间里，不先查会白等 mDNS+扫描 ~45 秒。
  if port_open 5555; then ok "固定端口 5555 在听（tcpip 模式），优先用它"; CANDS="5555"; fi
  if [ -z "$CANDS" ]; then
  for i in 1 2 3; do
    PORT=$(timeout 12 "$HOME_DIR/.local/bin/droid" discover 2>/dev/null | grep -oE 'port=[0-9]+' | head -1 | cut -d= -f2)
    [ -n "$PORT" ] && break
    sleep 2
  done
  fi
  if [ -n "$CANDS" ]; then :
  elif [ -n "$PORT" ]; then ok "mDNS 发现端口 $PORT"; CANDS="$PORT"
  else
    warn "mDNS 没发现（网络不通时它没用）→ 改扫本地端口"
    SCAN=$(python3 - <<'PYEOF' 2>/dev/null
import socket, concurrent.futures
def probe(x):
    s=socket.socket(); s.settimeout(0.12)
    try:
        s.connect(('127.0.0.1', x)); s.close(); return x
    except Exception: return None
found=[]
with concurrent.futures.ThreadPoolExecutor(max_workers=500) as ex:
    for r in ex.map(probe, range(30000, 61000), chunksize=128):
        if r: found.append(r)
print(' '.join(str(x) for x in found))
PYEOF
)
    [ -n "$SCAN" ] && { ok "本地扫到候选端口：$SCAN"; CANDS="$SCAN"; } || warn "本地也没扫到任何监听端口（adbd 没起来）"
  fi
fi

step "⑤ 连接 adb"
if adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then
  ok "adb 已连接：$(adb devices | awk 'NR>1 && $2=="device"{print $1; exit}')"
else
  CONNECTED=0
  for p in $CANDS 5555; do
    t="127.0.0.1:$p"
    [ "$DRY" = 1 ] && { printf '   · [dry] adb connect %s\n' "$t"; CONNECTED=1; break; }
    timeout 15 adb connect "$t" >/dev/null 2>&1
    if adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then ok "已连上 $t"; CONNECTED=1; break
    else adb disconnect "$t" >/dev/null 2>&1; fi   # 不是 adbd 的端口会留 offline 假记录，随手清掉
  done
  if [ "$CONNECTED" != 1 ]; then
    step "⑤·诊断：为什么连不上（不再只报"连不上"）"
    N3=$(timeout 12 "$SOCK" netstate 2>/dev/null || true)
    W3=$(printf '%s' "$N3" | grep -oE '"wifi_on": *-?[0-9]+' | grep -oE '\-?[0-9]+$')
    O3=$(printf '%s' "$N3" | grep -oE '"online": *(true|false)' | grep -oE '(true|false)$')
    A3=$(printf '%s' "$N3" | grep -oE '"adb_wifi": *-?[0-9]+' | grep -oE '\-?[0-9]+$')
    printf '   现状：wifi_on=%s online=%s adb_wifi=%s\n' "$W3" "$O3" "$A3"
    if [ "$W3" = "1" ] && [ "$A3" = "0" ] && [ "$O3" != "true" ]; then
      bad "Wi-Fi 开关是开的，但**没有真正连上网络** → 系统把「无线调试」又清回了 0"
      printf '   （无线调试依附"可用的 Wi-Fi 网络"，不只是开关；直连 1.1.1.1:443 也失败了）\n'
      printf '   要做的：连上一个 Wi-Fi（AP）——不是只把开关拨开；连上后重跑本组件\n'
    elif [ "$W3" = "1" ] && [ "$A3" = "0" ]; then
      bad "网络是通的（online=true），但开关被系统清回了 0 —— 不是网络问题"
      printf '   这一版系统只认「开发者选项 → 无线调试」里手动打开的那次\n'
      printf '   要做的：设置 → 开发者选项 → 无线调试 → 打开一次，再重跑本组件\n'
    elif [ "$W3" = "1" ] && [ "$A3" = "1" ]; then
      bad "开关是 1，但没有 adbd 监听 → 框架没被真正启动"
      printf '   要做的：开发者选项 → 无线调试 → 手动打开一次（之后本组件就能自动维持）\n'
    else
      bad "前置仍未满足（wifi_on=$W3 online=$O3 adb_wifi=$A3）"
    fi
    exit 1
  fi
fi

step "⑥ 校验"
if [ "$DRY" != 1 ]; then
  echo -n "   shell: "; timeout 20 adb shell echo "adb-ok $(getprop ro.product.model 2>/dev/null)" 2>&1 | tail -1
  echo -n "   固定端口状态: "; adb devices | awk 'NR>1{printf "%s(%s) ", $1, $2}'; echo
fi
printf '\n  提示：想让 adb 长期可用 → Wi-Fi 与「无线调试」都要留着；想收回 → 关「无线调试」即可\n'
done_
