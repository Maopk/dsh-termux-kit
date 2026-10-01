# DSH × Termux — mobile operations kit

**English** · [中文](README.zh-CN.md)

Running DeepSeek Harness (DSH) on a non-rooted Android phone without continuous manual supervision.

Running DSH on a phone differs from running it on a laptop: Termux is frozen by the system, the browser window is discarded, and when a failure occurs the log cannot be read from the device. This repository covers those gaps. Twelve Termux widgets handle start, stop and recovery; a Console app provides buttons; an Accessibility app lets the AI read the screen, tap, swipe and type. Everything runs on Termux, Android's Accessibility service and Termux's own `RUN_COMMAND`. No root and no PC are required.

## Overview

DSH is used on a vivo phone, and that ROM freezes background apps aggressively: the accessibility bridge is periodically reclaimed by the system, adb wireless debugging terminates as soon as Wi-Fi drops, and the browser window is occasionally discarded. Recovering all of that manually became impractical, so it was implemented as scripts and apps.

An engineering journal is maintained as well: `docs/DSH运维笔记.md` (Chinese) records the evidence and post-mortem for every problem encountered. Most entries under "Troubleshooting" below are selected from it.

## Quick start

The simplest path is the APKs from the latest release. Current release: **[v1.12](https://github.com/Maopk/dsh-termux-kit/releases/tag/v1.12)** — Console 1.21, Bridge 2.24, page panel 0.10.0, all three on the release page with `SHA256SUMS.txt`.

Source on `master` can be newer than the release: this is what "pushed but not released" looks like. The version line in both apps keeps those numbers apart on purpose — the installed version, the release, and the repo source.

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

Two steps remain after that:

1. Install Termux:Widget, long-press the home screen → Widgets → Termux:Widget, and the 12 widgets appear. The Chinese names are forwarding aliases, so a shortcut already on the home screen keeps working.
2. Open the Bridge app, enable Accessibility for it in system settings, then give its token to DSH (or write it to `~/.dsh-bridge-token`).

## Features

- Starts DSH and waits for **real** readiness before opening the browser: a token line in the log, that URL returning 200, and the process still alive. DSH binds the port before it mounts its routes, so a check that only tests the port opens a 404 page.
- Stop, soft restart, hard restart, backup, cleanup and emergency stop: one widget each. Backup verifies the archive (`zstd -t` plus an entry count) rather than merely producing a file.
- Complete adb workflow: enable Wireless debugging, find the port, connect, verify. It recovers automatically when the connection breaks, and on failure it reports the step that failed.
- Status overview: a status indicator and a text line for each of the three channels (DSH, bridge, adb). The bridge and adb are two independent channels — when a failure occurs it must be clear which one failed — so they are never merged into a single switch.
- Provides the AI with screen reading, tapping, swiping and typing. The bridge uses loopback 8788 and requires no network; adb uses a shell, is more capable, but requires working Wi-Fi.
- Fully automatic APK installation: it drives vivo's installer page, taps the blue authorisation text and enters the 6-digit lock-screen PIN. The PIN is stored only in `~/.dsh-auth-pass` (mode 600).
- Updates both apps from the release: download, verify SHA256, install. It never downgrades.
- Network repair: determines whether the Clash core has stopped or the generated config is invalid, then repairs it.
- A single wording set for all three surfaces: the Console app, the Bridge app and the panel in the lower right of the web page. A change in one place applies to all three.

### The 12 widgets

| Widget | Function |
|---|---|
| `1_start-dsh` | Starts DSH, waits for real readiness, then opens the browser |
| `2_shutdown-dsh` | Stops the service and closes the DSH window; Termux is kept unless `--close-termux` is passed |
| `3_backup-dsh` | Packages and verifies the archive |
| `4_soft-restart-dsh` | Soft stop (SIGTERM), then restart |
| `5_cleanup-dsh` | Deletes the screenshots and state packs produced by the kit itself; user files are never touched |
| `6_hard-restart-dsh` | Hard kill (SIGKILL), then restart |
| `7_reconnect-ai` | Recovers adb and the bridge, and reports which of the two failed |
| `8_enable-wireless-adb` | Semi-automatically enables Wireless debugging and connects adb |
| `9_revoke-pin` | Revokes "the AI may use my lock-screen PIN for system verification" |
| `10_net-fix` | Repairs the network: Clash core stopped, or invalid generated config |
| `11_update-apps` | Updates the Console and Bridge apps from the release; upgrades only |
| `0_emergency-stop` | Emergency stop: revoke AI control of the phone — bridge, token, adb wireless debugging |

Every widget supports `--dry-run`, which prints and executes nothing.

### Commands

```bash
dsh-status-pub --json --brief   # collect status (DSH, bridge, adb, locks, tasks) as JSON
dsh-restart                     # safe restart: unresponsive page vs terminated service, kills only bin.js web, waits for real readiness
dsh-bridge status|wake|stop     # bridge only (loopback, no network needed)
droid conn|shot|ui|tap|text     # adb only
dsh-uitap "刷新状态"            # tap UI by its text; it scrolls for you, far more reliable than fixed coordinates
dsh-install-apk <apk> --verify  # fully automatic install, then opens the APK again to verify
clash-doctor                    # five-step Clash check, including the "node domain eaten by fake-ip" problem
dsh-gh push|release|status      # maintain this repo
```

### Conventions shared by the three surfaces

The Console app, the Bridge app and the page panel display the same categories and the same wording, because all three are generated from a single file (`ui/controls.json`). The Console and the panel show seven categories; the Bridge app shows four of them and folds "About this app" at the bottom. The widgets cover three.

- A button triggers one action. A switch represents a persistent state (bridge running, PIN usage rights). A status indicator is read-only.
- Below every control name there is one line of secondary text stating what it does and what it costs; long-press to view the full text.
- Colour has one meaning everywhere: green for normal, yellow for transitional, red for abnormal, grey for disabled or not installed; status indicators use the same rule.
- Danger is indicated by shape, not by a separate category: emergency actions are solid red with white text; only "Stop DSH" (the sole irreversible action) is white with red text and a red border; every other dangerous control uses ordinary styling plus a red warning mark.
- Language is a single setting (`~/.dsh-lang`), followed by the widgets, the panel and both apps.

## Troubleshooting

Every entry below occurred on a real device. Each entry states the symptom, the cause and the solution.

### Tapping "start DSH" produces no page
Symptom: the Console reports that the start completed (exit=0), but no browser or DSH window appears.
Cause: `dsh web` opens the browser itself, and the start script also asks Termux to open it; however, an `am start` issued from a backgrounded Termux is sometimes discarded by the system. Measured: the call succeeds while Termux holds `termux-wake-lock`, so this is not a hard rule.
Solution: wait one or two seconds; in most cases `dsh web` opened it. If the page still does not appear, tap the "open the DSH page" row at the bottom of the Console — that tap originates from the foreground and always works.

### The accessibility bridge keeps disconnecting
Symptom: the bridge status indicator turns grey, or the wake action appears to have no effect.
Cause: Android reclaims Accessibility services. On this vivo device the bridge can drop about 10 seconds after focus switches to another app.
Solution: tapping widget `1`, `7` or `8` wakes it with a token. If the process was reclaimed, a cold start takes 20 to 40 seconds — do not conclude prematurely that the process has terminated. This is expected behaviour, not a failure.

### adb does not connect, or disconnects when Wi-Fi drops
Symptom: `adb devices` returns an empty list; `droid conn` cannot reach anything.
Cause: Wireless debugging requires a **working Wi-Fi network**, not merely the switch enabled. When Wi-Fi drops, Android also clears Wireless debugging, and the port is lost with it.
Solution: run `droid-ensure`. It executes the full sequence — read Wi-Fi state, use the bridge to enable Wireless debugging, scan for the port, connect, verify — and reports the step that failed. To keep adb available long term, leave Wi-Fi and Wireless debugging enabled; to revoke it, turn Wireless debugging off.

### The APK installation stalls, or the installer page disappears by itself
Symptom: `dsh-install-apk` waits for the authorisation page and then times out.
Cause: vivo's installer page times out after about 50 seconds without input, and the blue half of the "you may authorise this install" line is not present in the Accessibility tree.
Solution: run it again — the second run usually succeeds (the first often only brings the page up). To confirm the state, read the installer's own message: "same version already installed" means the target version is already installed.

### After `pkill -9 -f node`, DSH never starts again
Symptom: the start script reports that another start is already in progress, or repeatedly fails.
Cause: `-9` leaves an orphan write lock (`~/.dsh/.credentials.yaml.lock`), and DSH waits at most 2 seconds for that lock.
Solution: do not use `pkill -9 -f node` (it also terminates unrelated node processes). Use `dsh-restart`: it determines whether the page is unresponsive or the service has terminated, kills only `bin.js web`, clears the orphan lock before restarting, waits for real readiness, and writes the new authentication URL back to `~/.dsh-url`.

### The page reports it cannot read content, but the service is alive
Symptom: the DSH page loads and reports that it cannot read content.
Cause: in most cases Termux was frozen, or a half-started instance was opened (port listening, routes not yet mounted).
Solution: pull to refresh first — the browser login cookie is signed with a persistent key, so restarting DSH does not invalidate it and a new URL is not required. If that does not help, restart with widget `4` or `6`.

### A cold start takes about 18 seconds; it is not stuck
Cause: the bottleneck is the volume of `node_modules` that must be loaded (about 700MB on this device), not the number of plugins.
Solution: there is no shortcut; wait. The start script waits for real readiness, so the page opens by itself once the service is actually up.

### Closing DSH leaves the browser window open
Symptom: the script reports that the window was not closed.
Cause: without adb, the only way to close another app's window is to press Back through the Accessibility bridge, and that requires the window to be in the foreground. Bringing it to the foreground would interrupt whatever the user was doing.
Solution: the trade-off is deliberate — leaving the window open is preferred over taking the foreground. Swipe it away manually, or connect adb: with adb the close is a silent `force-stop` that never touches the foreground.

### The three version numbers have different meanings
Symptom: the Console displays "Version: v1.21 · the release channel is still v1.14 · the repo source is v1.21".
Cause: these are three distinct facts: the version installed locally, the version offered in GitHub Releases, and the version in `master`. Pushing source without cutting a release makes them disagree.
Solution: no workaround is required; the display is accurate. To make it report "up to date", cut a release.

## Requirements and limits

- Tested on Termux 0.119.0-beta.3. Termux is a GitHub pre-release, so the add-ons must come from the same source.
- Verified on a vivo V2463A running Android 16. On other devices the coordinates and the matched text may need adjustment: installer wording, power-saving policy and the aggressiveness of background freezing differ per ROM.
- No root is required, and no root-only mechanism is used.
- Being reclaimed by the system is expected behaviour for the bridge, not a failure.
- The adb channel requires working Wi-Fi; the bridge channel requires no network.
- Installing an APK requires the 6-digit lock-screen PIN once, for Android's own verification. The PIN is stored only in `~/.dsh-auth-pass` (mode 600) and is read only when passing that verification on your behalf.
- A cold start of about 18 seconds is normal.

## Security and permissions

Every long-lived channel can be revoked by the user alone, without going through this kit:

| Channel | What it grants | How to revoke |
|---|---|---|
| Accessibility bridge | Read the screen, tap, swipe, type | Widget `0_emergency-stop`, `dsh-bridge stop`, or turn Accessibility off in system settings |
| Loopback token | The credential for calling the bridge | Delete `~/.dsh-bridge-token` |
| adb wireless debugging | Shell-level capability, the strongest of the three | `droid-panic`, turn Wireless debugging off, or reboot the phone |
| Page remote control | Clicking within the DSH page | Write `{"enabled": false}` into `~/.dsh-mobile-ui.json` |
| PIN usage rights | Passing system verification with the lock-screen PIN | The Console switch, widget `9_revoke-pin`, or `rm ~/.dsh-auth-pass` |

The PIN is read only when passing system verification on your behalf. It is never used to unlock the phone or browse its contents, never for payments, and never for anything outside the requested task.

## Repository layout

| Path | Contents |
|---|---|
| `apps/console/` | The Console app: the button-based interface |
| `apps/bridge/` | The Accessibility bridge app: screen reading, tapping and typing |
| `widgets/` | The 12 Termux widgets plus the shared `common.sh` |
| `tools/` | Command-line tools: start, backup, APK installation, tap by text, Clash self-check, and so on |
| `plugins/` | Three DSH page plugins: phone panel, AI self-look and remote control, file panel |
| `ui/` | The wording and colour sources for all three surfaces: `ui/controls.json` (categories, control names, one-line consequences, danger flags, `appVersions`) and `ui/theme.json` (27 colours → two `Palette.java`, two `res/values/dsh_theme.xml` and the console's shape drawables). `tools/ui-controls gen` writes both out, and `tools/i18n-audit` checks the result item by item |
| `i18n/zh.json` | The only translation source; every Chinese string in all three surfaces is generated from it |
| `tests/selftest.sh` | The self-test suite |
| `docs/` | [Engineering journal](docs/DSH运维笔记.md) · [troubleshooting](docs/operations.md) · [architecture](docs/architecture.md) · [languages](docs/i18n.md) · [glossary](docs/术语表.md) |
| `dist/` | Built APKs and their SHA256 |
| `CONTRIBUTING.md` | The rules this repository follows: how every change gets logged, and which gates run before a push |
| `CHANGELOG.md` | Every version's changes, newest first, in [Keep a Changelog](https://keepachangelog.com/) form |

## Third-party components

There is no third-party code in this repository. It depends on the following, installed separately:

- Termux (GPLv3), its `RUN_COMMAND` interface and the Termux:Widget add-on. The foundation of the entire kit.
- DeepSeek Harness itself belongs to DeepSeek. This repository is not an official project and is unrelated to DeepSeek.
- Building uses `aapt2`, `d8` and `apksigner` from Termux's Android SDK build tools.
- Packaging uses zstd, image processing uses imagemagick, and local OCR uses tesseract (optional).

## License

MIT, see `LICENSE`.

The code is delivered as "verified on this device". No guarantee of identical behaviour on other devices.
