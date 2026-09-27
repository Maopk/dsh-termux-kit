#!/data/data/com.termux/files/usr/bin/bash
# 9_revoke-pin — a redundant escape hatch: take back the "the AI may use your lock-screen PIN" grant in one step
#
# Meaning: those 6 digits are **the general credential for system authentication** (installing a package asks for "security verification", lifting the "restricted setting" on an app,
# sensitive confirmations in Developer options… all pop it). Revoking = deleting ~/.dsh-auth-pass (a small file with mode 600),
# after which the AI is no longer allowed to use it: anything that needs it to pass verification stops in front of you.
# **No screen, no buttons**, and it does not affect DSH/bridge/adb (each has its own revoke path).
#
# Usage: 9_revoke-pin.sh [--dry-run]
HOME_DIR="/data/data/com.termux/files/home"
SELF_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
. "$HOME_DIR/.local/share/dsh-widgets/common.sh"
parse_args "$@"
HELPER="$HOME_DIR/.local/bin/dsh-auth-pass"
PASSFILE="$HOME_DIR/.dsh-auth-pass"

step "① Current grant state (only presence and permissions are checked; the PIN is never shown)"
if [ "$DRY" = 1 ]; then printf '   · [dry] %s status\n' "$HELPER"; ok "(dry-run)"
elif [ -x "$HELPER" ]; then "$HELPER" status || true
else warnf "%s not found" "$HELPER"; fi

step "② Revoke (overwrite first, then delete)"
if [ "$DRY" = 1 ]; then printf '   · [dry] %s revoke\n' "$HELPER"; ok "(dry-run)"
elif [ -x "$HELPER" ]; then
  if "$HELPER" revoke; then ok "Revoke command completed"; else die "Revoke failed (see the output above)"; fi
else die "$HELPER not found, cannot revoke"
fi

step "③ Re-check: the file must really be gone"
if [ "$DRY" = 1 ]; then printf '   · [dry] re-check that %s does not exist\n' "$PASSFILE"; ok "(dry-run)"
elif [ -f "$PASSFILE" ]; then
  die "Re-check failed: $PASSFILE is still there, the revoke did not take effect"
else
  okf "Deleted and confirmed: %s does not exist → the AI can never use that PIN to pass any verification again" "$PASSFILE"
fi

printf "$(dsh_msg '\n   To restore: hand over the 6 digits again (or flip the switch under "Maintenance" in the Console back on); if you would rather not, use your fingerprint.\n')"
printf "$(dsh_msg '   It only governs "whether the AI can use your lock-screen PIN"; the bridge / adb / DSH are unaffected (each has its own revoke path).\n')"
done_
