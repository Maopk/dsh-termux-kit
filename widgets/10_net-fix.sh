#!/data/data/com.termux/files/usr/bin/bash
# 10_net-fix — one tap when foreign sites die (the recurring failure on this phone)
#
# What it does: runs `clash-doctor --fix`, which first decides WHICH of the two known failures this is,
# then repairs it:
#   ① Clash core stopped            → start it
#   ② core up but every foreign site dead (the generated config / DNS state went bad)
#                                   → update the subscription, which regenerates the config
# Measured 2026-09-27: case ② goes from `github 000` to `200` about 4 seconds after the update.
# The repair drives the Clash UI through the accessibility bridge, so the screen is used for a few
# seconds — that is expected.
#
# Usage: 10_net-fix.sh [--dry-run]
HOME_DIR="/data/data/com.termux/files/home"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
parse_args "$@"
step "Network first aid (clash-doctor --fix)"
if [ "$DRY" = 1 ]; then printf '   · [dry] clash-doctor --fix\n'; ok "(dry-run)"; done_; exit 0; fi
timeout 200 "$HOME_DIR/.local/bin/clash-doctor" --fix
rc=$?
case "$rc" in
  0) ok "foreign sites are reachable again" ;;
  *) bad "still broken — see the lines above; the Clash Logs page shows the exact error" ;;
esac
done_
exit $rc
