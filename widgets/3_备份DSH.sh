#!/data/data/com.termux/files/usr/bin/bash
# 3_备份DSH —— 备份并**校验**：生成快照 → 验证归档完整 → 执行保留策略 → 报告占用
# 用法：3_备份DSH.sh [--dry-run] [--verify-only]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
DRY=0; VERIFY_ONLY=0
for a in "$@"; do case "$a" in --dry-run) DRY=1 ;; --verify-only) VERIFY_ONLY=1 ;; esac; done
BAKDIR="$DSH_DIR/备份"
mkdir -p "$BAKDIR" 2>/dev/null

step "① 生成快照"
if [ "$VERIFY_ONLY" = 1 ]; then
  warn "verify-only：跳过生成"
elif [ -x "$HOME_DIR/.local/bin/dsh-backup" ]; then
  if [ "$DRY" = 1 ]; then printf '   · [dry] dsh-backup\n'; else
    OUT=$("$HOME_DIR/.local/bin/dsh-backup" 2>&1 | tail -2); printf '   %s\n' "$OUT"
  fi
else
  die "找不到 dsh-backup"
fi

step "② 校验最新归档"
NEW=$(ls -1t "$BAKDIR"/dsh-state-*.tar.gz 2>/dev/null | head -1)
[ -z "$NEW" ] && die "备份目录里没有归档"
SZ=$(stat -c%s "$NEW" 2>/dev/null || echo 0)
[ "$SZ" -gt 1048576 ] && ok "大小 $(numfmt --to=iec $SZ 2>/dev/null || echo $((SZ/1024))K)" || warn "归档偏小（$SZ 字节），请留意"
CNT=$(tar -tzf "$NEW" 2>/dev/null | wc -l)
[ "$CNT" -gt 10 ] && ok "归档可读，含 $CNT 个条目" || die "归档损坏或为空（条目 $CNT）"
sha256sum "$NEW" 2>/dev/null | cut -c1-16 | sed 's/^/   sha256(前16): /'

step "③ 保留策略（30 天 / 上限 200 份，手动清理只留 5 份）"
TOT=$(ls -1 "$BAKDIR"/dsh-state-*.tar.gz 2>/dev/null | wc -l)
FIND_OLD=$(find "$BAKDIR" -name 'dsh-state-*.tar.gz' -mtime +30 2>/dev/null | wc -l)
[ "$FIND_OLD" -gt 0 ] && { run "find '$BAKDIR' -name 'dsh-state-*.tar.gz' -mtime +30 -delete"; ok "删除超期 $FIND_OLD 份"; } || ok "无超期归档"
if [ "$TOT" -gt 200 ]; then
  run "ls -1t '$BAKDIR'/dsh-state-*.tar.gz | tail -n +201 | xargs -r rm -f"
  ok "按上限裁剪至 200 份"
fi

step "④ 报告"
printf '   最新：%s\n' "$NEW"
printf '   目录：%s\n' "$BAKDIR"
printf '   共 %s 份，占用 %s\n' "$(ls -1 "$BAKDIR"/dsh-state-*.tar.gz 2>/dev/null | wc -l)" "$(du -sh "$BAKDIR" 2>/dev/null | cut -f1)"
done_
