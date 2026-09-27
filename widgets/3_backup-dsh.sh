#!/data/data/com.termux/files/usr/bin/bash
# 3_backup-dsh — back up and **verify**: create a snapshot → validate the archive → apply the retention policy → report usage
# Usage: 3_backup-dsh.sh [--dry-run] [--verify-only]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
DRY=0; VERIFY_ONLY=0
for a in "$@"; do case "$a" in --dry-run) DRY=1 ;; --verify-only) VERIFY_ONLY=1 ;; esac; done
BAKDIR="$DSH_DIR/备份"
mkdir -p "$BAKDIR" 2>/dev/null

step "① Create the snapshot"
if [ "$VERIFY_ONLY" = 1 ]; then
  warn "verify-only: skipping snapshot creation"
elif [ -x "$HOME_DIR/.local/bin/dsh-backup" ]; then
  if [ "$DRY" = 1 ]; then printf '   · [dry] dsh-backup\n'; else
    OUT=$("$HOME_DIR/.local/bin/dsh-backup" 2>&1 | tail -2); printf '   %s\n' "$OUT"
  fi
else
  die "dsh-backup not found"
fi

step "② Verify the newest archive"
NEW=$(ls -1t "$BAKDIR"/dsh-state-*.tar.gz 2>/dev/null | head -1)
[ -z "$NEW" ] && die "No archive in the backup directory"
SZ=$(stat -c%s "$NEW" 2>/dev/null || echo 0)
[ "$SZ" -gt 1048576 ] && ok "Size $(numfmt --to=iec $SZ 2>/dev/null || echo $((SZ/1024))K)" || warn "The archive is small ($SZ bytes), keep an eye on it"
CNT=$(tar -tzf "$NEW" 2>/dev/null | wc -l)
[ "$CNT" -gt 10 ] && ok "Archive readable, $CNT entries" || die "Archive is corrupt or empty ($CNT entries)"
sha256sum "$NEW" 2>/dev/null | cut -c1-16 | sed 's/^/   sha256(first 16): /'

step "③ Retention policy (30 days / 200 max; manual cleanup keeps only 5)"
TOT=$(ls -1 "$BAKDIR"/dsh-state-*.tar.gz 2>/dev/null | wc -l)
FIND_OLD=$(find "$BAKDIR" -name 'dsh-state-*.tar.gz' -mtime +30 2>/dev/null | wc -l)
[ "$FIND_OLD" -gt 0 ] && { run "find '$BAKDIR' -name 'dsh-state-*.tar.gz' -mtime +30 -delete"; ok "Deleted $FIND_OLD expired archives"; } || ok "No expired archives"
if [ "$TOT" -gt 200 ]; then
  run "ls -1t '$BAKDIR'/dsh-state-*.tar.gz | tail -n +201 | xargs -r rm -f"
  ok "Trimmed to the 200-archive cap"
fi

step "④ Report"
printf '   Newest: %s\n' "$NEW"
printf '   Directory: %s\n' "$BAKDIR"
printf '   %s archives, using %s\n' "$(ls -1 "$BAKDIR"/dsh-state-*.tar.gz 2>/dev/null | wc -l)" "$(du -sh "$BAKDIR" 2>/dev/null | cut -f1)"
done_
