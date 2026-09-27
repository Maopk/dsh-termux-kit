# Operations & Troubleshooting

Field notes from running this kit on a real phone (vivo V2463A / Android 16, non-rooted). Every entry below was
hit in practice and fixed in practice. The full evidence trail — including the dead ends — lives in
[`DSH运维笔记.md`](DSH运维笔记.md) (Chinese, append-only journal).

Golden rule used throughout: **never claim an effect you did not observe.** Distinguish “local file written”,
“committed locally”, “pushed to the remote”, and “the phone actually did it”.

---

## 1. “DSH won't start” / browser opens an error page

**Symptom:** the launcher widget finishes, but the browser shows `HTTP ERROR 404` or a login loop.

**Cause (measured):** `dsh web` **binds the port before mounting routes**. For a second or two `/` answers
404 — the old readiness test (“port is open and HTTP is not 000”) treated that half-started service as ready
and opened the browser into the gap.

**Fix:** readiness means all three: ① the log contains the token line, ② `curl -sL -c /dev/null <url>` returns
200, ③ the process is still alive. `1_start-dsh` implements exactly that.

**Related trap — orphan locks:** `dsh-atomic-write` waits only 2 seconds, and `kill -9` leaves
`~/.dsh/.credentials.yaml.lock` behind, after which **every** later start fails. When no instance is running,
delete that lock. `dsh-restart` does it for you.

---

## 2. Foreign sites die while domestic sites keep working

This is the single most confusing failure mode, and it has **two different causes** — check the Clash log to tell
them apart.

| Observation | Cause | Fix |
|---|---|---|
| Clash home screen says **stopped** (“点此启动”), no `tun` interface, nothing listening on `127.0.0.1:7890` | The VPN core was stopped (the app process may still be alive) | Start it. Then stop the ROM from killing it: allow background activity, battery “unrestricted”, and enable the system **always-on VPN** |
| Core is running, but every foreign request fails after ~5s and the log shows `dns resolve failed: couldn't find ip` | The generated config / DNS state went bad — the core cannot resolve the **proxy server's own domain** | **Re-generate the config by updating the subscription** (Clash → Profiles → ⋮ → Update, or the circular-arrow button). Restarting the core alone does **not** help |

Because “Update” is the only self-heal, we keep the subscription auto-update interval at **60 minutes**.
Measured baseline: right after a core start the first ~70 s are unreliable (3/10 requests), then it settles to
20/20 with ~0.3 s TLS handshakes.

`clash-doctor` runs the five checks (core / direct / proxied / node-domain-fake-ip / verdict) in one command.

---

## 3. The Accessibility bridge stops answering

**Symptom:** `droid-sock` times out; the console's bridge lamp goes red.

**Cause:** the ROM reclaims the app when it is in the background (roughly 10 s after you switch away). This is
normal on this device, not a bug.

**Fix:** `dsh-bridge wake` — a token-carrying broadcast. A cold start can take 20–40 s, so retry rather than
declaring it dead. Since 2026-09-27 the silent broadcast is the **only** default path: the old fallback that
launched the bridge UI (on the assumption a broadcast cannot wake a dead process) was removed at the user's
explicit request — it stole the foreground on every DSH start. Measured: a soft-stopped bridge returns 2 s after
one token-carrying broadcast, with the screen untouched. If the process really is gone the tools now say so and
ask you to tap the 8_ widget, instead of yanking your screen; `DSH_BRIDGE_WAKE_UI=1` restores the old fallback.

**Full stop is a one-way door here:** `dsh-bridge off` also disables Accessibility, and this ROM will not let a
broadcast bring the app back — you must re-enable Accessibility by hand.

---

## 4. adb is not connected

Wireless debugging needs a **working Wi-Fi network**, not just the switch. After a phone reboot you must
re-enable Wireless debugging once by hand; `droid conn` then reconnects (no re-pairing needed).

`droid-panic` is the emergency stop for this channel: it disconnects adb and turns Wireless debugging off.

---

## 5. “I closed DSH but the window stayed”

`termux-am` (0.8.1) has **no `force-stop` subcommand**, so from Termux you cannot kill another app's window.
The only channel that can close it is the **Accessibility bridge** (bring the window to the front, then press
Back until it is gone) — which means the close step must run **before** the bridge is stopped.

`dsh-close-window` implements this: adb first, bridge otherwise, and it puts your previous app back in the
foreground afterwards. Verify without closing anything: `dsh-close-window --probe`.

---

## 6. “Right after closing DSH, the console can't refresh”

Two causes, both fixed:

- The close script used to `kill -9` **Termux itself**; the console talks to Termux only through
  `RUN_COMMAND`, so its next refresh had to wait for a cold start and hit the 25 s timeout. Now Termux is kept
  alive (`--close-termux` closes it too).
- `dsh-status-pub` had per-step timeouts that could add up past 30 s. It now has a **6-second whole-run budget**
  (`--budget N`), and `droid-sock ping --fast` skips the 14-second wake ladder.

---

## 7. The gallery shows images that are “corrupted” when opened

**Cause:** screenshots written into a **public media directory** (`Download/dsh/图片/`) are indexed by
MediaStore. Deleting them with `rm` removes the file but **not the index row**, so the gallery still lists them
(with size and dimensions) and fails to open them.

**Fix:** the folder now carries a `.nomedia` file, so future screenshots are never indexed. For stale rows:
rename the folder once (the provider drops that path) or clear the **Media Storage** app's data; with adb
connected, `5_cleanup-dsh` also runs the `content delete` that removes them properly.

**Lesson:** intermediate artifacts belong in a private directory, or beside a `.nomedia`. Deleting a file is one
ledger; the media database is another.

---

## 8. Installing an APK on this ROM (the part that is device-specific)

`dsh-install-apk <apk>` automates it: it taps the small “you may authorize this installation” line's **blue
second half** (found by pixel colour — it is not in the Accessibility tree), then waits for the lock-screen PIN
keypad and types the PIN from `~/.dsh-auth-pass` (600). The whole flow times out after ~50 s of inactivity, and
the big bottom button **cancels** the install — do not tap it.

The PIN is read only to pass system verification for the operation you asked for; `9_revoke-pin` (or the console
switch, or `rm ~/.dsh-auth-pass`) revokes it instantly.

---

## 9. Where things live, and what is worth keeping

```
~/.local/bin/            26 tools
~/.shortcuts/tasks/      the 9 home-screen widgets
~/.local/share/dsh-widgets/  common.sh + selftest.sh
Download/dsh/
   ├── 备份/dsh-state-*.tar.gz      state packs (~25 MB each, keep 5) — sessions, settings, plugin list, notes
   ├── 备份/系统备份/dsh-full-*.tar.zst   FULL snapshots (~1.1 GB) — whole Termux prefix + DSH runtime,
   │                                    built by dsh-snapshot, for reinstalling or moving to a new phone.
   │                                    ⚠ contains credentials; keep it as a sensitive file.
   ├── 图片/                        screenshots the AI takes (now `.nomedia`, invisible to the gallery)
   ├── 文档/ 脚本/ 状态/ 配置/       notes, script copies, status JSON, generated config
   └── 应用/                       built APKs
```

`5_cleanup-dsh` clears the kit's own artifacts: screenshots are deleted by default (its own images only — your
files are never touched), state packs are capped at 5, and **full snapshots at 1** (`--keep-full N`).
If the backup folder still looks large, that is the snapshot, not a failed cleanup — the summary line now says so
explicitly.

---

## 10. Self-test

```bash
bash tests/selftest.sh        # 60 checks: syntax → dry-runs → regression → real runs → cold-start sandbox
```

Everything testable runs for real (a second DSH instance is started on port 8099 in a sandbox profile, with its
own patch file). Six checks are skipped by design — the ones that would kill the running session; those steps are
verified individually instead.
