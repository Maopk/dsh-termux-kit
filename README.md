# DSH × Termux — mobile ops kit

**English** · [中文](README.zh-CN.md)

Running DeepSeek Harness (DSH) on a non-rooted Android phone, without having to sit and watch it.

Running DSH on a phone is not like running it on a laptop: Termux gets frozen by the system, the browser window disappears, and when something breaks you cannot see the log from where you are. This repo is about covering those gaps. Twelve Termux widgets start, stop and recover things; a Console app gives you buttons; an Accessibility app lets the AI read the screen, tap, swipe and type. It all runs on Termux, Android's Accessibility service and Termux's own `RUN_COMMAND`. No root, no PC.

## Why I built it

I use DSH on my phone. It is a vivo, and it freezes background apps hard: the bridge (the Accessibility service) gets reclaimed by the system every so often, adb wireless debugging dies the moment Wi-Fi drops, and the browser window is occasionally thrown away. Rescuing all of that by hand got old, so it became scripts and apps.

I also kept a journal: `docs/DSH运维笔记.md` (Chinese) is the evidence and the post-mortem for every trap I hit. Most of "Traps I actually hit" below is picked from it.

## Getting it running

The easy path is the APKs from the latest release. Current release: **[v1.12](https://github.com/Maopk/dsh-termux-kit/releases/tag/v1.12)** — Console 1.21, Bridge 2.24, page panel 0.10.0, all three on the release page with `SHA256SUMS.txt`.

Source on `master` can be newer than the release: that is what "pushed but not released" looks like. The version line in both apps keeps those numbers apart on purpose — yours, the release, and the repo source.

To build from source:

```bash
# 1) dependencies in Termux. tesseract is optional (local OCR only)
pkg install zstd imagemagick tesseract tesseract-lang

# 2) put the scripts in place
git clone https://github.com/Maopk/dsh-termux-kit && cd dsh-termux-kit
install -m755 tools/* ~/.local/bin/
bash tools/install-widgets      # installs the 12 widgets, plus Chinese names for them

# 3) let external apps run commands in Termux (the Console app needs this)
grep -q allow-external-apps ~/.termux/termux.properties 2>/dev/null \
  || echo 'allow-external-apps = true' >> ~/.termux/termux.properties
termux-reload-settings

# 4) build the two apps (output in apps/console/build/ and apps/bridge/build/)
bash apps/console/build.sh && bash apps/bridge/build.sh

# 5) self-test: walks all 12 widgets, the destructive ones only rehearse by default
bash tests/selftest.sh
```

Two things left after that:

1. Install Termux:Widget, long-press the home screen → Widgets → Termux:Widget, and the 12 widgets show up. The Chinese names are forwards, so a shortcut already on your home screen keeps working.
2. Open the Bridge app, enable Accessibility for it in system settings, then give its token to DSH (or write it to `~/.dsh-bridge-token`).

## What it actually does

- Starts DSH and waits for **real** readiness before opening the browser: a token line in the log, that URL returning 200, and the process still alive. DSH binds the port before it mounts its routes, so anything that only checks the port will open a 404 page for you.
- Stop, soft restart, hard restart, backup, cleanup and emergency stop, one widget each. Backup verifies the archive (`zstd -t` plus an entry count) instead of just producing a file.
- adb end to end: turn on Wireless debugging, find the port, connect, verify. When it breaks it repairs itself, and when it cannot it says which step failed.
- Status at a glance: a lamp and a line for each of the three channels (DSH, bridge, adb). The bridge and adb are separate things — when something breaks you need to know which one — so they are never merged into one switch.
- Lets the AI read the screen, tap, swipe and type. The bridge goes over loopback 8788 and needs no network; adb goes through a shell and is stronger but needs working Wi-Fi.
- Fully automatic APK installs: it drives vivo's installer page, taps the blue authorisation text and types your 6-digit lock-screen PIN. The PIN lives only in `~/.dsh-auth-pass` (mode 600).
- Updates both apps from the release: download, verify SHA256, install. It never downgrades.
- Network first aid: decides whether the Clash core stopped or the generated config went bad, then fixes it.
- One set of wording for three surfaces: the Console app, the Bridge app and the page panel. Change it once and all three change.

### The 12 widgets

| Widget | What it does |
|---|---|
| `1_start-dsh` | Starts DSH, waits for real readiness, then opens the browser |
| `2_shutdown-dsh` | Stops the service and closes the DSH window; keeps Termux alive unless you pass `--close-termux` |
| `3_backup-dsh` | Packages and verifies the archive |
| `4_soft-restart-dsh` | Restarts via SIGTERM |
| `5_cleanup-dsh` | Deletes the screenshots and state packs the kit itself produces, never your files |
| `6_hard-restart-dsh` | Restarts via SIGKILL |
| `7_reconnect-ai` | Recovers adb and the bridge, and says which of the two failed |
| `8_enable-wireless-adb` | Semi-automatically enables Wireless debugging and connects adb |
| `9_revoke-pin` | Revokes "the AI may use my lock-screen PIN for system verification" |
| `10_net-fix` | Repairs the network: Clash core stopped, or bad generated config |
| `11_update-apps` | Updates the Console and Bridge apps from the release, upgrades only |
| `0_emergency-stop` | One tap to revoke all AI control: bridge, token, adb wireless debugging |

Every widget supports `--dry-run`, which prints and executes nothing.

### Commands you will actually type

```bash
dsh-status-pub --json --brief   # collect status (DSH, bridge, adb, locks, tasks) as JSON
dsh-restart                     # safe restart: stuck page vs dead service, kills only bin.js web, waits for real readiness
dsh-bridge status|wake|stop     # bridge only (loopback, no network needed)
droid conn|shot|ui|tap|text     # adb only
dsh-uitap "刷新状态"            # tap UI by its text; it scrolls for you, far more reliable than fixed coordinates
dsh-install-apk <apk> --verify  # fully automatic install, then opens the APK again to verify
clash-doctor                    # five-step Clash check, including the "node domain eaten by fake-ip" trap
dsh-gh push|release|status      # maintain this repo
```

### The rules the three surfaces share

The Console app, the Bridge app and the page panel show the same categories and the same wording, because all three are generated from one file (`ui/controls.json`). Console and panel show seven categories; the Bridge app shows four of them and folds "About this app" at the bottom. The widgets cover three.

- A button triggers one action. A switch is a lasting state (bridge running, PIN usage rights). A lamp is read-only.
- Under every control there is one grey line saying what it does and what it costs. Long-press for the full text.
- Colours mean one thing everywhere: green ok, yellow transitional, red broken, grey off or not installed, and the lamps follow that same rule.
- Danger is shown by shape, not by a separate category: emergency actions are solid red with white text; only "Stop DSH" (the one irreversible action) is white with red text and a red border; every other dangerous control is ordinary styling plus a red warning mark.
- Language is one setting (`~/.dsh-lang`) shared by the widgets, the panel and both apps.

## Traps I actually hit

Everything here happened on a real phone. Each entry is what you see, why it happens, and how to get around it.

### You tap "start DSH" and no page shows up.
Symptom: the Console says the start finished (exit=0), but no browser or DSH window appears.
Cause: `dsh web` opens the browser itself, and the start script also asks Termux to open it — but `am start` from a backgrounded Termux is sometimes dropped by the system. I measured it: while Termux holds `termux-wake-lock` it succeeds, so it is not a hard rule.
Way around: wait a second or two; usually `dsh web` opened it. If it still is not there, tap the "open the DSH page" row at the bottom of the Console — that tap is yours, it comes from the foreground, and it always works.

### The bridge keeps dropping.
Symptom: the bridge lamp goes grey, or waking it seems to do nothing.
Cause: Android reclaims Accessibility services. On this vivo it can drop about 10 seconds after you switch to another app.
Way around: tapping widget `1`, `7` or `8` wakes it with a token. If the process was reclaimed, a cold start takes 20 to 40 seconds — do not call it dead too early. This is normal, not a defect.

### adb will not connect, or dies when Wi-Fi does.
Symptom: `adb devices` is empty; `droid conn` cannot reach anything.
Cause: Wireless debugging needs a **working Wi-Fi network**, not just the switch flipped. When Wi-Fi drops, Android also clears Wireless debugging, and the port goes with it.
Way around: run `droid-ensure`. It walks the whole ladder — read Wi-Fi state, borrow the bridge to flip Wireless debugging, scan for the port, connect, verify — and names the step that failed. To keep adb usable long term, leave Wi-Fi and Wireless debugging on; to take it back, turn Wireless debugging off.

### The APK install stalls, or the installer page disappears.
Symptom: `dsh-install-apk` sits waiting for the authorisation page, then times out.
Cause: vivo's installer page times out after about 50 seconds of no input, and the blue half of the "you may authorise this install" line is not in the Accessibility tree.
Way around: run it again — the second run usually works (the first often only brings the page up). To check the state, read what the installer itself said: "same version already installed" means you are already on the target version.

### After `pkill -9 -f node`, DSH never starts again.
Symptom: the start script reports another start already in progress, or keeps failing.
Cause: `-9` leaves an orphan write lock (`~/.dsh/.credentials.yaml.lock`), and DSH waits at most 2 seconds for that lock.
Way around: do not use `pkill -9 -f node` (it also kills unrelated node processes). Use `dsh-restart`: it decides whether the page is stuck or the service is dead, kills only `bin.js web`, clears the orphan lock first, waits for real readiness and writes the fresh auth URL back to `~/.dsh-url`.

### The page says it cannot read anything, but the service is alive.
Symptom: the DSH page loads and reports it cannot read content.
Cause: usually Termux was frozen, or you opened a half-started instance (port listening, routes not mounted yet).
Way around: pull to refresh first — the browser login cookie is signed with a persistent key, so a DSH restart does not invalidate it and you do not need a new URL. If that fails, restart with widget `4` or `6`.

### A cold start takes about 18 seconds. It is not stuck.
Cause: the bottleneck is how much `node_modules` has to be loaded (mine is around 700MB now), not the number of plugins.
Way around: nothing to do but wait. The start script waits for real readiness, so the page opens by itself when it is actually up.

### Closing DSH left the browser window open.
Symptom: the script says the window was not closed.
Cause: without adb the only way to close another app's window is to press Back through the Accessibility bridge, and that requires the window to be in front. Pulling it to the front would yank you out of whatever you were doing.
Way around: that trade-off is deliberate — I would rather leave the window than steal your foreground. Swipe it away yourself, or connect adb: with adb it is a silent `force-stop` that never touches the foreground.

### The version line shows three different numbers.
Symptom: the Console says "Version: v1.21 · the release channel is still v1.14 · the repo source is v1.21".
Cause: those are three different facts. What you installed, what the latest GitHub Release contains, and what the version number in `master` says. Push source without cutting a release and they disagree.
Way around: nothing to work around, it is telling the truth. To make it say "up to date", cut a release.

## Dependencies and limits

- Tested on Termux 0.119.0-beta.3. That is a GitHub pre-release, so the add-ons must come from the same source.
- Verified on a vivo V2463A running Android 16. On another device you may need to adjust coordinates and the text being matched: installer wording, power-saving policy and how aggressively the system freezes background apps all differ per ROM.
- No root, and nothing here needs it.
- Getting reclaimed by the system is normal for the bridge, not a failure.
- The adb channel needs working Wi-Fi. The bridge channel needs no network.
- Installing an APK asks for your 6-digit lock-screen PIN once, for Android's own verification. The PIN is stored only in `~/.dsh-auth-pass` (mode 600) and read only when passing that verification for you.
- About 18 seconds for a cold start is normal.

## Taking control back

Every long-lived channel can be revoked by you alone, without going through this kit:

| Channel | What it grants | How to revoke it |
|---|---|---|
| Accessibility bridge | Read the screen, tap, swipe, type | Widget `0_emergency-stop`, `dsh-bridge stop`, or turn Accessibility off in system settings |
| Loopback token | The credential to call the bridge | Delete `~/.dsh-bridge-token` |
| adb wireless debugging | Shell-level power, the strongest of the three | `droid-panic`, turn Wireless debugging off, or reboot the phone |
| Page remote control | Clicking around in your DSH page | Write `{"enabled": false}` into `~/.dsh-mobile-ui.json` |
| PIN usage rights | Passing system verification with your lock-screen PIN | The Console switch, widget `9_revoke-pin`, or `rm ~/.dsh-auth-pass` |

The PIN is read only when passing system verification on your behalf. It is never used to unlock the phone and browse it, never for payments, and never for anything outside the task you asked for.

## What is where

| Path | Contents |
|---|---|
| `apps/console/` | The Console app: the one with the buttons |
| `apps/bridge/` | The Accessibility bridge app: screen reading, tapping, typing |
| `widgets/` | The 12 Termux widgets plus the shared `common.sh` |
| `tools/` | Command-line tools: start, backup, install APKs, tap by text, Clash self-check, and so on |
| `plugins/` | Three DSH page plugins: phone panel, AI self-look and remote control, file panel |
| `ui/` | The wording and colour sources for all three surfaces: `ui/controls.json` (categories, control names, one-line consequences, danger flags, `appVersions`) and `ui/theme.json` (27 colours → two `Palette.java`, two `res/values/dsh_theme.xml` and the console's shape drawables). `tools/ui-controls gen` writes both out, and `tools/i18n-audit` checks the result item by item |
| `i18n/zh.json` | The only translation source; every Chinese string in all three surfaces is generated from it |
| `tests/selftest.sh` | The self-test suite |
| `docs/` | [Engineering journal](docs/DSH运维笔记.md) · [troubleshooting](docs/operations.md) · [architecture](docs/architecture.md) · [languages](docs/i18n.md) |
| `dist/` | Built APKs and their SHA256 |
| `CONTRIBUTING.md` | The rules I hold myself to: how every change gets logged, and which gates run before a push |
| `CHANGELOG.md` | Every version's changes, newest first, in [Keep a Changelog](https://keepachangelog.com/) form |

## Things by other people

There is no third-party code in this repo. It depends on these, installed separately:

- Termux (GPLv3), its `RUN_COMMAND` interface and the Termux:Widget add-on. The foundation for everything here.
- DeepSeek Harness itself belongs to DeepSeek. This repo is not an official project and has nothing to do with them.
- Building uses `aapt2`, `d8` and `apksigner` from Termux's Android SDK build tools.
- Packaging uses zstd, image handling uses imagemagick, and local OCR uses tesseract (optional).

## License

MIT, see `LICENSE`.

The code is delivered as "it works on my phone". No promise that it behaves the same on yours.
