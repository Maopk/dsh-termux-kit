# Pre-publish self-check: no keys/tokens/passwords from this machine in the repo
#
# Why a separate script: this setup "grows on a real machine", so it is easy to slip
#   ~/.dsh-bridge-token (bridge token), ~/.dsh-tasks-token (page panel token), 
#   ~/.dsh-auth-pass (the 6-digit lockscreen password), token-bearing DSH URLs
# into the repo. **Run it before publishing, and again before pushing.**
#
# Usage: bash tools/check-no-secrets.sh [repo root]
set -u
ROOT="${1:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"
HOME_DIR="${HOME:-/data/data/com.termux/files/home}"
fail=0

# ① Real secrets that exist on this machine: finding any in the repo is an incident
for f in "$HOME_DIR/.dsh-bridge-token" "$HOME_DIR/.dsh-tasks-token" "$HOME_DIR/.dsh-auth-pass"; do
  [ -s "$f" ] || continue
  val=$(tr -d '\n\r \t' < "$f")
  [ -n "$val" ] || continue
  hits=$(grep -rlF -- "$val" "$ROOT" 2>/dev/null | grep -v '\.git/' | head -5)
  if [ -n "$hits" ]; then
    printf '✘ The contents of %s appear in:\n%s\n' "$(basename "$f")" "$hits"
    fail=1
  else
    printf '✔ no trace of %s in the repo\n' "$(basename "$f")"
  fi
done

# ② Generic patterns: long tokens in URLs, suspected private keys, keystores
if grep -rnE 'token=[A-Za-z0-9_-]{24,}' "$ROOT" --exclude-dir=.git 2>/dev/null | head -3 | grep -q .; then
  printf '✘ repo contains token=<long string> style values (listed above)\n'; fail=1
else
  printf '✔ no URL-style tokens\n'
fi
if find "$ROOT" -name '*.jks' -o -name '*.keystore' -o -name 'id_rsa*' 2>/dev/null | grep -q .; then
  printf '✘ repo contains keystore/private-key files (should be excluded)\n'; fail=1
else
  printf '✔ no keystore/private-key files\n'
fi
if grep -rn -- '-----BEGIN [A-Z ]*PRIVATE KEY-----' "$ROOT" --exclude-dir=.git 2>/dev/null | head -2 | grep -q .; then
  printf '✘ repo contains private-key material\n'; fail=1
else
  printf '✔ no private-key material\n'
fi

# ③ Do not drag runtime artifacts in
for pat in '*.log' 'status.json' '.dsh-url' '*.part'; do
  hits=$(find "$ROOT" -name "$pat" -not -path '*/.git/*' 2>/dev/null | head -3)
  [ -n "$hits" ] && { printf '⚠ runtime artifacts (suggest deleting): %s\n' "$(echo "$hits" | tr '\n' ' ')"; }
done

[ "$fail" = 0 ] && printf '\nVerdict: safe to publish ✅\n' || printf '\nVerdict: **fix the ✘ items above first**\n'
exit "$fail"
