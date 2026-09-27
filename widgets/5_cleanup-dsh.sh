#!/data/data/com.termux/files/usr/bin/bash
# 5_cleanup-dsh — delete what should go: surplus backups / **my screenshots** / vision temp artifacts / large workspace files / logs / package cache
# Rule: touch only "my artifacts" (fixed prefixes or known temp filenames); your own files are never touched.
# Usage: 5_cleanup-dsh.sh [--dry-run] [--no-pnpm] [--keep-images N] [--keep-runs N] [--keep-full N] [--deep]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
DRY=0; DO_PNPM=0; KEEP_IMG=0; KEEP_RUNS=3; KEEP_FULL=1; DEEP=0
# 2026-09-27 change: KEEP_IMG default 10 → **0** (tapping "Cleanup" should wipe the diagnostic screenshots I produced;
#   measured: after you tapped cleanup, 图片/ still held 19 files, 9 of them clash-*.png shot by me that night
#   because "only 5 prefixes are recognized" they were not claimed as mine). Add --keep-images N to keep a few.
# KEEP_FULL: full snapshots (dsh-full-*.tar.zst, about 1.1GB each) default to keeping only the newest 1 —
#   the cleanup script used to **handle only dsh-state-\* and never touch dsh-full-\***, so 备份/ sat at 1.2GB and would not shrink.
# The pnpm cache is **not** cleared by default: clearing it makes the next plugin install re-download 400+ packages (measured 4m43s)
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
[ "$DRY" = 1 ] && log "== dry-run mode: print only, do not execute =="
BAKDIR="$DSH_DIR/备份"; IMGDIR="$DSH_DIR/图片"; RUNS="$HOME_DIR/.dsh-vision-router/artifacts/.runs"
IMG_MANIFEST="$IMGDIR/.dsh-images.list"   # If a name matches no prefix, listing it here makes cleanup claim it
BEFORE=$(du -sm "$DSH_DIR" 2>/dev/null | cut -f1 || echo 0)
SMOKE_BEFORE=$(du -sm "$LOGS" 2>/dev/null | cut -f1 || echo 0)

stepf "① Backups: keep the newest 5 state bundles; keep the newest %s full snapshots" "$KEEP_FULL"
N=$(ls -1 "$BAKDIR"/dsh-state-*.tar.gz 2>/dev/null | wc -l)
if [ "$N" -gt 5 ]; then
  runf "ls -1t '%s'/dsh-state-*.tar.gz | tail -n +6 | xargs -r rm -f" "$BAKDIR"
  okf "Deleted %s state bundles, kept 5" "$((N-5))"
else okf "%s state bundles, under the cap" "$N"; fi
# Full snapshots (produced by dsh-snapshot, 1.1G each): this used to be ignored entirely → the backup directory never shrank
for d in "$BAKDIR" "$BAKDIR/系统备份"; do
  [ -d "$d" ] || continue
  FN=$(ls -1 "$d"/dsh-full-*.tar.zst 2>/dev/null | wc -l)
  [ "$FN" = 0 ] && { okf "%s" "$(basename "$d"): no full snapshots"; continue; }
  if [ "$FN" -gt "$KEEP_FULL" ]; then
    ls -1t "$d"/dsh-full-*.tar.zst | tail -n +$((KEEP_FULL+1)) | while read -r f; do
      b="${f%.tar.zst}"
      runf "rm -f \\\"%s\\\" \\\"%s.sha256\\\" \\\"%s.说明.md\\\"" "$f" "${b}" "${b}"
      okf "Deleted the old full snapshot %s" "$(basename "$f") (with .sha256 / .说明.md)"
    done
  else okf "%s" "$(basename "$d"): $FN full snapshots, under the cap ($KEEP_FULL)"; fi
done

stepf "② My screenshots: clear every recognized one (keep %s); delete anything older than 7 days first" "$KEEP_IMG"
# 2026-09-27 fix: only five prefixes were recognized (self-look-/droid-/adb-/board-/screen-),
#   and the names I later saved with droid-sock shot / self-view were clash-*, whale-*, real-* etc. → **all judged "your images" and left alone**.
#   Now: the prefix list grew to 13 + a manifest file as a fallback (however odd the name, listing it gets it claimed).
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
TOTAL=$(find "$IMGDIR" -maxdepth 1 -type f ! -name '.*' 2>/dev/null | wc -l)   # Exclude my own registry files such as .dsh-images.list
OTHER=$((TOTAL-MINE))
if [ "$MINE" -gt 0 ]; then
  OLD=$(printf '%s\n' "$MINE_LIST" | while read -r f; do [ -n "$f" ] && [ -n "$(find "$f" -maxdepth 0 -mtime +7 2>/dev/null)" ] && printf '%s\n' "$f"; done | grep -c . || true)
  if [ "$OLD" -gt 0 ]; then
    printf "$(dsh_msg '%s\n')" "$MINE_LIST" | while read -r f; do
      [ -n "$f" ] && [ -n "$(find "$f" -maxdepth 0 -mtime +7 2>/dev/null)" ] && runf "rm -f \\\"%s\\\"" "$f"
    done
    okf "Deleted %s of them by the 7-day rule" "$OLD"
  fi
  LEFT_LIST="$(mine_list)"
  LEFT=$(printf '%s\n' "$LEFT_LIST" | grep -c . || true)
  if [ "$LEFT" -gt "$KEEP_IMG" ]; then
    printf "$(dsh_msg '%s\n')" "$LEFT_LIST" | while read -r f; do
      [ -n "$f" ] && stat -c '%Y %n' "$f" 2>/dev/null
    done | sort -rn | tail -n +$((KEEP_IMG+1)) | cut -d' ' -f2- | while read -r f; do
      runf "rm -f \\\"%s\\\"" "$f"
    done
    okf "Deleted %s, kept the newest %s" "$((LEFT-KEEP_IMG))" "$KEEP_IMG"
  else okf "%s screenshots of mine, under the cap (%s)" "$LEFT" "$KEEP_IMG"; fi
else ok "No screenshots of mine"; fi
[ "$OTHER" -gt 0 ] && okf "Plus %s images of your own, **untouched** (if any of them are mine, list the filename in %s and it gets claimed)" "$OTHER" "$IMG_MANIFEST"
step "②·b Media-library hygiene: pin .nomedia in the image dir so the gallery keeps no 'ghost' entries"
# 2026-09-27 from your real testing: after cleanup the gallery still showed clash-*.png, but opening one said "corrupted".
#   Cause: those images live in Download/dsh/图片 (**a public media directory**) → MediaStore indexed them,
#   but cleanup deletes the files with plain rm without telling the media library → the index rows (time/size/thumbnail) stay,
#   while the files are gone ⇒ the gallery still lists them and opening one reports corruption.
#   Two fixes: ① put .nomedia in that directory → images written there later are **never indexed by the gallery**;
#            ② with adb, also drop the dead rows from MediaStore (only the shell identity has permission).
touch "$IMGDIR/.nomedia" && okf "%s/.nomedia is in place (the gallery no longer indexes this directory)" "$IMGDIR"
if adb devices 2>/dev/null | awk 'NR>1 && $2=="device"' | grep -q .; then
  DEL=$(timeout 25 adb shell content delete --uri content://media/external/images/media \
        --where "_data LIKE '%/Download/dsh/图片/%'" 2>&1 | tail -1)
  okf "Asked the media library to delete the old rows for this directory: %s" "${DEL:-done}"
else
  ok "(no adb: the system clears these rows during idle maintenance or a reboot; you can also clear the 'Media Storage' data by hand)"
fi

stepf "③ Vision-tool temp artifacts: keep only the newest %s runs" "$KEEP_RUNS"
if [ -d "$RUNS" ]; then
  R=$(find "$RUNS" -mindepth 1 -maxdepth 1 2>/dev/null | wc -l)
  if [ "$R" -gt "$KEEP_RUNS" ]; then
    runf "find '%s' -mindepth 1 -maxdepth 1 -printf '%T@ %p\\\\n' | sort -rn | tail -n +%s | cut -d' ' -f2- | xargs -r rm -rf" "$RUNS" "$((KEEP_RUNS+1))"
    okf "Deleted %s old vision run directories (hidden ones included)" "$((R-KEEP_RUNS))"
  else okf "%s present, under the cap" "$R"; fi
else ok "No vision temp directory"; fi

step "④ Large workspace files and test leftovers (~/.smoke)"
for f in readmes.json updates.json; do
  [ -f "$LOGS/$f" ] && { SZ=$(du -h "$LOGS/$f" | cut -f1); runf "rm -f '%s/%s'" "$LOGS" "$f"; okf "Deleted %s (%s, a big dump from fetching)" "$f" "$SZ"; }
done
for pat in 'idx*.html' 'index.html' 'c*.css' 'whale.js' 'u.xml' 'board-*.png' 'adb-shot.png' 'cj*.txt' 'ax.json' 'ax2.json' 'autox.json' 'pair-crop*.png' 'droid-ui.xml' 'probe-boot.log'; do
  for f in $LOGS/$pat; do
    [ -f "$f" ] || continue
    runf "rm -f '%s'" "$f"; okf "Deleted test leftover %s" "$(basename "$f")"
  done
done
if [ "$DEEP" = 1 ]; then
  for f in android.jar plugins.json platform-*.zip; do
    [ -f "$LOGS/$f" ] && { SZ=$(du -h "$LOGS/$f" | cut -f1); runf "rm -f '%s/%s'" "$LOGS" "$f"; okf "Deep clean of %s (%s, can be re-downloaded when needed)" "$f" "$SZ"; }
  done
else
  [ -f "$LOGS/android.jar" ] && ok "Keeping android.jar (needed to rebuild when the app changes)"
  [ -f "$LOGS/plugins.json" ] && ok "Keeping plugins.json (used to query the plugin market)"
fi
# Smoke-test logs: keep only the newest 3
BOOTS=$(find "$LOGS" -maxdepth 1 -name 'boot*.log' 2>/dev/null | wc -l)
if [ "$BOOTS" -gt 3 ]; then
  runf "find '%s' -maxdepth 1 -name 'boot*.log' -printf '%T@ %p\\\\n' | sort -rn | tail -n +4 | cut -d' ' -f2- | xargs -r rm -f" "$LOGS"
  okf "Keeping only the newest 3 smoke logs (deleted %s)" "$((BOOTS-3))"
else okf "%s smoke logs, under the cap" "$BOOTS"; fi

for f in "$LOGS"/*.log; do
  [ -f "$f" ] || continue
  SZ=$(( $(stat -c%s "$f") / 1024 ))
  [ "$SZ" -gt 512 ] && { runf ": > '%s'" "$f"; okf "Truncated log %s" "$(basename "$f") (${SZ}KB)"; }
done
runf "find '%s' -maxdepth 1 -name '*.log.bak' -mtime +14 -delete 2>/dev/null || true" "$LOGS"

step "⑤ Save the auth URL to disk (before the log is deleted)"
URLFILE="$HOME_DIR/.dsh-url"
if grep -oE "http://127\\.0\\.0\\.1:${DSH_PORT}/\\?token=[A-Za-z0-9_-]+" "$(dsh_log)" 2>/dev/null | tail -1 > "$URLFILE.tmp" && [ -s "$URLFILE.tmp" ]; then
  mv -f "$URLFILE.tmp" "$URLFILE"; okf "Saved %s" "$(basename "$URLFILE")"
else
  rm -f "$URLFILE.tmp"; [ -f "$URLFILE" ] && okf "Reusing the existing %s" "$(basename "$URLFILE")" || warn "No token URL to save"
fi

step "⑥ Log rotation + package-manager cache (the pnpm cache is left alone by default)"
rotate_logf "%s" "$(dsh_log)" 2
if [ "$DO_PNPM" = 1 ] && need pnpm; then
  [ "$DRY" = 1 ] && printf '   · [dry] pnpm store prune\n' || { R=$(timeout 180 pnpm store prune 2>&1 | tail -1); okf "%s" "${R:-pruned}"; }
else ok "Skipping the pnpm cache prune (keeping it makes the next plugin install fast; add --prune-store to prune)"; fi

step "⑦ Result"
AFTER=$(du -sm "$DSH_DIR" 2>/dev/null | cut -f1 || echo 0)
SMOKE_AFTER=$(du -sm "$LOGS" 2>/dev/null | cut -f1 || echo 0)
printf "$(dsh_msg '   Download/dsh: %sMB → %sMB (freed %sMB)\n')" "$BEFORE" "$AFTER" "$((BEFORE-AFTER))"
printf "$(dsh_msg '   ~/.smoke    : %sMB → %sMB (freed %sMB)\n')" "$SMOKE_BEFORE" "$SMOKE_AFTER" "$((SMOKE_BEFORE-SMOKE_AFTER))"
# 2026-09-27 added: spell out "what the backup directory actually holds" — it used to report only "N backups",
# and seeing 1.2G refuse to shrink made you think cleanup was broken, when in fact the full snapshot kept for phone migration was holding it.
STATE_N=$(ls -1 "$BAKDIR"/dsh-state-*.tar.gz 2>/dev/null | wc -l)
FS_N=$(ls -1 "$BAKDIR"/dsh-full-*.tar.zst "$BAKDIR"/系统备份/dsh-full-*.tar.zst 2>/dev/null | wc -l)
FS_H=$(du -ch "$BAKDIR"/dsh-full-*.tar.zst "$BAKDIR"/系统备份/dsh-full-*.tar.zst 2>/dev/null | tail -1 | cut -f1)
BAK_H=$(du -sh "$BAKDIR" 2>/dev/null | cut -f1)
MINE_LEFT=$(mine_list | grep -c . || true)
ALL_LEFT=$(find "$IMGDIR" -maxdepth 1 -type f ! -name '.*' 2>/dev/null | wc -l)
OTHER_LEFT=$((ALL_LEFT-MINE_LEFT))
printf "$(dsh_msg '   Backup dir %s: state bundles %s (max 5) + full snapshots %s (%s)\n')" "${BAK_H:-?}" "$STATE_N" "$FS_N" "${FS_H:-none}"
printf "$(dsh_msg '     ↳ a full snapshot is the whole-device archive for migrating or reinstalling (备份/系统备份/dsh-full-*.tar.zst); delete it if it takes too much room, or add --keep-full 0\n')"
printf "$(dsh_msg '   My screenshots %s (cap %s) / your own images %s (untouched)\n')" "$MINE_LEFT" "$KEEP_IMG" "$OTHER_LEFT"
done_
