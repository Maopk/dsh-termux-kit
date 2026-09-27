# DSH × Termux — Mobile Ops Kit

**English** · [中文](README.zh-CN.md)

Run DeepSeek Harness (DSH) on a **non-rooted Android phone** as something that *starts itself, recovers itself, and tells you plainly what broke*.

Everything is built on **Termux + an Accessibility service + the official `RUN_COMMAND` channel**. No root. No PC required.

---

## What's inside

| Path | Contents |
|---|---|
| `apps/console/` | Source of the **DSH Console app**: a GUI for the 9 task widgets + a home-screen widget |
| `apps/bridge/` | Source of the **DSH Bridge app**: Accessibility service + loopback API so an AI can read the screen, tap, swipe and type |
| `widgets/` | 9 Termux home-screen task widgets + the shared library `common.sh` |
| `i18n/zh.json` | **The only translation source**: every English string → Chinese. `tools/i18n-table` generates the bash table and both apps' Java tables from it, each with that runtime's own escaping rules (see [`docs/i18n.md`](docs/i18n.md)) |
| `tools/` | 33 command-line tools (start, backup, install APKs, tap UI by text, Clash self-check, i18n generation, …) |
| `plugins/` | 3 DSH page plugins (phone task panel / AI self-look & remote control / file panel) |
| `tests/selftest.sh` | Self-test suite: 60 checks (syntax → dry-run → regression → real run → cold-start sandbox) |
| `docs/` | [`operations.md`](docs/operations.md) — how to diagnose the recurring failures · [`architecture.md`](docs/architecture.md) — one-page architecture & data flow · `DSH运维笔记.md` — the raw Chinese engineering journal behind them |
| `dist/` | Prebuilt APKs + SHA256 |

---

## Install

```bash
# 1) Termux (tested on 0.119.0-beta.3) — install dependencies
pkg install zstd imagemagick tesseract tesseract-lang   # tesseract is optional (local OCR)

# 2) Put the scripts in place
git clone <this-repo> dsh-termux-kit && cd dsh-termux-kit
install -m755 tools/*   ~/.local/bin/
mkdir -p ~/.shortcuts/tasks ~/.local/share/dsh-widgets
install -m755 widgets/*.sh ~/.shortcuts/tasks/
install -m755 tests/selftest.sh ~/.local/share/dsh-widgets/
install -m755 widgets/common.sh ~/.local/share/dsh-widgets/

# 3) Let external apps run commands in Termux (needed by the Console app)
grep -q allow-external-apps ~/.termux/termux.properties 2>/dev/null \
  || echo 'allow-external-apps = true' >> ~/.termux/termux.properties
termux-reload-settings

# 4) Install the two apps (use dist/ APKs, or build your own)
bash apps/console/build.sh && bash apps/bridge/build.sh
#   output: apps/console/build/dsh-console.apk, apps/bridge/build/dsh-bridge.apk

# 5) Self-test
bash tests/selftest.sh
```

Two more steps after installing:

1. **Home-screen widgets** — install Termux:Widget, long-press the home screen → Widgets → Termux:Widget, and the 9 tasks from `~/.shortcuts/tasks/` become tappable.
2. **Bridge app** — open it, enable **Accessibility** for “DSH Bridge” in system settings, then give its token to DSH (or write it to `~/.dsh-bridge-token`).

---

## Usage

### Home-screen widgets (`~/.shortcuts/tasks/`, run by Termux:Widget)

| Widget | What it does |
|---|---|
| `1_start-dsh` | Starts DSH and waits for **real readiness** (token line in the log + URL returns 200 + process alive) before opening the browser |
| `2_shutdown-dsh` | Stops the service **and closes the DSH window** (adb first; without adb it drives the Accessibility bridge to press Back — it no longer fakes success). Keeps **Termux alive by default** (`--close-termux` closes it too, since the Console app needs Termux to refresh). `--keep-bridge` keeps the bridge |
| `3_backup-dsh` | Packages and verifies the archive (`zstd -t` + entry count) |
| `4_soft-restart-dsh` / `6_hard-restart-dsh` | Restart via SIGTERM / SIGKILL (takes the lock *before* acting, so double-taps can't kill the instance it just started) |
| `5_cleanup-dsh` | Cleans the kit's own artifacts: **my screenshots are deleted by default**, keeps 5 state packs and **1 full snapshot**; your files are untouched (oddly-named images I produce can be claimed via `图片/.dsh-images.list`) |
| `7_reconnect-ai` | Recovers adb and the bridge, and says clearly which of the two failed |
| `8_enable-wireless-adb` | Semi-automatically enables Wireless debugging and connects adb (if Wi-Fi is off it opens the settings page, waits for your tap, then continues) |
| `9_revoke-pin` | Revokes the “AI may use your lock-screen PIN for system verification” grant |
| `10_net-fix` | One tap when foreign sites die: decides whether the Clash core is stopped or the generated config went bad, then repairs it (`clash-doctor --fix`) |
| `0_emergency-stop` | One-tap kill switch: revokes all AI control (bridge, token, adb wireless debugging) |

Every widget supports `--dry-run` (print only, execute nothing).

### DSH Console app

- Three status lamps on top (DSH / bridge / adb) plus a detail line with timestamps.
- Buttons are **grouped by function**: `Start·Stop` / `Channels (adb and bridge kept separate)` / `Maintenance` / `Emergency`.
- **Logs** live on their own screen: command sent, result, exit code, raw output; the button shows an unread badge.
- “PIN usage rights” is a **switch**: on = the AI may use your 6-digit lock-screen PIN to pass system verification; off = revoked immediately.
- **Language switch** (System / 中文 / English) under Maintenance: it writes the shared `~/.dsh-lang`, so the app, the DSH page panel and the 10 widgets all follow the same choice. Source text is English; Chinese comes from the shared table in `i18n/zh.json` (see [`docs/i18n.md`](docs/i18n.md)).
- Dangerous actions (restart / shutdown / emergency stop / revoke) require confirmation.
- Timeouts are per task (backup 420s / restart 300s / queries 25s with one automatic resend), and timeout messages state the real reason (e.g. “the phone was busy”).
- It requests exactly one permission: `com.termux.permission.RUN_COMMAND`. No storage, network, accessibility or overlay permissions.

### DSH page plugins (`plugins/`)

Drop a directory into your DSH profile's `local/`, register it in **both** `dependencies` and `dsh.profile.bundles` in `package.json`, then `pnpm install` and refresh the page:

- `dsh-mobile-local` — the ☰ button at the bottom right opens a phone task panel (grouped tasks + status lamps + the PIN switch). Backed by `tools/dsh-tasksd` on `127.0.0.1:8787` (token + allow-list).
- `dsh-selflook-local` — renders the page to PNG (so the AI can look at it) and delivers click/swipe/eval commands into the page.
- `dsh-filepanel-local` — the local file-panel implementation, which also serves as the RPC channel for the two plugins above.

### Common commands

```bash
dsh-status-pub --json --brief   # collect status (DSH/bridge/adb/locks/tasks) → JSON
dsh-restart                     # safe restart: decides "page is stuck" vs "service is dead", kills only bin.js web, waits for real readiness
dsh-bridge status|wake|stop     # bridge only (accessibility loopback, needs no network)
droid conn|shot|ui|tap|text     # adb only
dsh-uitap "刷新状态"            # tap UI by its text (auto-scrolls; far more reliable than hard-coded coordinates)
dsh-install-apk <apk> --verify  # fully automatic APK install, then re-opens the APK to verify
clash-doctor                    # five-step Clash self-check (including the "node domain eaten by fake-ip" trap)
dsh-gh push|release|status      # maintain this repo: push / cut a release / show status
```

---

## Revoking access & safety

Every long-lived channel ships with a way for **you** to take it back in one step:

| Channel | What it grants | How to revoke |
|---|---|---|
| Accessibility bridge | Read screen, tap, swipe, type | Widget `0_emergency-stop` / `dsh-bridge stop` / turn the Accessibility switch off in system settings |
| Loopback token | Credential to call the bridge | Delete `~/.dsh-bridge-token` |
| adb wireless debugging | Shell-level power (the strongest) | `droid-panic` / turn Wireless debugging off / reboot the phone |
| Page remote control | Clicking around your DSH page | Write `{"enabled": false}` into `~/.dsh-mobile-ui.json` |
| PIN usage rights | Passing system verification with your lock-screen PIN | Console switch / widget `9_revoke-pin` / `rm ~/.dsh-auth-pass` |

The PIN is stored only in `~/.dsh-auth-pass` (`chmod 600`) and read only when passing system verification **on your behalf** — never to unlock the phone and browse its contents, never for payments, never for anything outside the task you asked for.

---

## Known limitations

- Installer wording, Accessibility behaviour and power-saving policy differ per ROM. This kit is verified on **vivo / Android 16**; on another device you may need to adjust coordinates or the text it matches on.
- **adb wireless debugging needs a working Wi-Fi network** (flipping the switch is not enough), and after a reboot you must re-enable Wireless debugging once by hand.
- The **Accessibility bridge gets reclaimed by the system** (it can drop ~10s after you switch to another app). That is normal; a token-carrying broadcast wakes it, and a cold start can take 20–40s.
- The first APK install asks for your fingerprint/lock-screen PIN — that is Android's own security check; this kit only automates reaching it.

---

## License

MIT (see `LICENSE`). The code is delivered as “verified working on one real device”, with no promise that it behaves the same on yours.
