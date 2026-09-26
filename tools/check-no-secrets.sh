# 发布前自检：确认仓库里没有这台机器的密钥/令牌/密码
#
# 为什么单独一个脚本：这套东西是"长在实际机器上"的，一不小心就会把
#   ~/.dsh-bridge-token（桥令牌）、~/.dsh-tasks-token（页面面板令牌）、
#   ~/.dsh-auth-pass（那 6 位锁屏密码）、带 token 的 DSH URL
# 复制进仓库。**发布前跑一遍，推之前再跑一遍。**
#
# 用法：bash tools/check-no-secrets.sh [仓库根目录]
set -u
ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
HOME_DIR="${HOME:-/data/data/com.termux/files/home}"
fail=0

# ① 本机真实存在的敏感值：只要它们在仓库里出现就是事故
for f in "$HOME_DIR/.dsh-bridge-token" "$HOME_DIR/.dsh-tasks-token" "$HOME_DIR/.dsh-auth-pass"; do
  [ -s "$f" ] || continue
  val=$(tr -d '\n\r \t' < "$f")
  [ -n "$val" ] || continue
  hits=$(grep -rlF -- "$val" "$ROOT" 2>/dev/null | grep -v '\.git/' | head -5)
  if [ -n "$hits" ]; then
    printf '✘ 发现 %s 的内容出现在：\n%s\n' "$(basename "$f")" "$hits"
    fail=1
  else
    printf '✔ %s 的内容没有出现在仓库里\n' "$(basename "$f")"
  fi
done

# ② 通用模式：URL 里的长 token、疑似私钥、keystore
if grep -rnE 'token=[A-Za-z0-9_-]{24,}' "$ROOT" --exclude-dir=.git 2>/dev/null | head -3 | grep -q .; then
  printf '✘ 仓库里出现形如 token=<长串> 的内容（上面已列出）\n'; fail=1
else
  printf '✔ 没有 URL 形式的 token\n'
fi
if find "$ROOT" -name '*.jks' -o -name '*.keystore' -o -name 'id_rsa*' 2>/dev/null | grep -q .; then
  printf '✘ 仓库里有密钥库/私钥文件（应当排除）\n'; fail=1
else
  printf '✔ 没有密钥库/私钥文件\n'
fi
if grep -rn -- '-----BEGIN [A-Z ]*PRIVATE KEY-----' "$ROOT" --exclude-dir=.git 2>/dev/null | head -2 | grep -q .; then
  printf '✘ 仓库里有私钥内容\n'; fail=1
else
  printf '✔ 没有私钥内容\n'
fi

# ③ 别把运行期产物带进来
for pat in '*.log' 'status.json' '.dsh-url' '*.part'; do
  hits=$(find "$ROOT" -name "$pat" -not -path '*/.git/*' 2>/dev/null | head -3)
  [ -n "$hits" ] && { printf '⚠ 运行期产物（建议删）：%s\n' "$(echo "$hits" | tr '\n' ' ')"; }
done

[ "$fail" = 0 ] && printf '\n结论：可以发布 ✅\n' || printf '\n结论：**先处理上面带 ✘ 的项**\n'
exit "$fail"
