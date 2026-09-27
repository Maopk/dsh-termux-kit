# Architecture & Data Flow (one page)

> 中文版：[架构与数据流.md](架构与数据流.md)

## Processes and ports

| Who | Address / file | Notes |
|---|---|---|
| DSH web | `127.0.0.1:8080` | The main service. It **binds the port before mounting routes** — during that window `/` returns 404, so “ready” must mean HTTP 200, never “port is open” |
| DSH bridge | `127.0.0.1:8788` | Loopback service started by the Accessibility app; token in `~/.dsh-bridge-token` (600). **Needs no network** |
| Page task-panel backend | `127.0.0.1:8787` | `tools/dsh-tasksd`; token in `~/.dsh-tasks-token` (600); only allow-listed scripts/commands can run |
| File-panel RPC | `/__dsh__/filepanel/rpc` inside DSH | The page plugins use it to read/write files by **absolute path** (relative paths are rejected as “outside the workspace”) |
| Sandbox second instance | `127.0.0.1:8099` | Used by the self-test; requires `DSH_WEB_EXTRA="--patch ~/.smoke/patch.yml"` and `--patch` **before** `--port` |

## The three independent ways “I can use the phone” (never merged in the UI)

```
① Accessibility bridge   droid-sock ──▶ 8788 ──▶ DSH Bridge app ──▶ Accessibility APIs
             Needs no network. Loses connection after the ROM reclaims the app (normal);
             a token-carrying broadcast wakes it (2s–40s).

② adb wireless           droid / droid-sock ──▶ 127.0.0.1:5555 ──▶ adbd
             Requires a *working Wi-Fi network* (flipping the switch is not enough);
             after a reboot, Wireless debugging must be re-enabled by hand.

③ Page remote control    dsh-control ──▶ ~/.dsh-look-cmd.json ──▶ the page plugin's 2s poll ──▶ browser
             Touches only the DSH page itself, never the system.
```

## Full data flow of one “start DSH”

```
widget / Console app
   └─▶ common.sh: dsh_start()
         ├─ readiness = token line in the log → curl the URL following redirects returns 200 → process alive
         ├─ start mutex ~/.dsh-boot-<port>.lock (idempotent for the same pid; stale if the pid is
         │    dead or the directory has not been touched for 5 minutes)
         └─ when no instance is running, remove ~/.dsh/.credentials.yaml.lock
              (dsh-atomic-write waits only 2 seconds; `kill -9` leaves an orphan lock behind,
               after which every later start fails)
   └─▶ bridge_wake (token-carrying broadcast)
   └─▶ tasksd_ensure (127.0.0.1:8787)
   └─▶ publish_status (writes Download/dsh/状态/{status.json,status.txt})
```

## How status reaches the UI

```
tools/dsh-status-pub            (collects: DSH / bridge / adb / locks / task tails)
   ├─ without --json: writes status.json + status.txt (for widgets and pages)
   └─ --json: prints to stdout only (for the Console app; **this path writes no file**)
        └─▶ the app's TaskResultReceiver → Last.setStatus → rendered
```
> ⚠ Note that branch: `--json` **does not write a file**. Never use the mtime of `status.json` to decide
> whether “that refresh from the app actually ran” — a trap we hit in practice (see `docs/DSH运维笔记.md`, 补三十二续).

## Data flow of installing an APK (the flow specific to this device)

```
tools/dsh-install-apk
   ├─ dsh-auth-pass digits  ← ~/.dsh-auth-pass (600); exits 3 when not authorized
   ├─ am start -a VIEW file://<temporary ASCII path> -t application/vnd.android.package-archive
   ├─ taps “allow for this time only” (no permanent grant)
   ├─ the **blue second half** of the small “you may authorize this installation” line
   │     ← located by pixel colour with dsh-find-blue
   │       (it is not in the Accessibility tree; the ROM ships two layouts, paragraph y≈1135/1255)
   ├─ “lock-screen PIN verification” → 6-digit keypad (coordinates read live from the Accessibility tree)
   └─ verify: launch the same APK again; only “same version already installed (x.y)” proves success
```
