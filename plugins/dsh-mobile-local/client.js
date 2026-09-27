window.__ModuleLoader__.load({
  id: 'dsh-mobile-local',
  factory: (require) => {
    var module = { exports: {} };
    var exports = module.exports;

    const React = require('react');
    const inject = ['slots', 'timer'];

    function apply(ctx) {
      const h = React.createElement;
      const useCallback = React.useCallback;
      const useEffect = React.useEffect;
      const useState = React.useState;

      const TASKSD = 'http://127.0.0.1:8787';
      const HOME = '/data/data/com.termux/files/home';

      // Component list: label is what people see, id is the name in the allowlist (dsh-tasksd only accepts these;
      // bridge_wake / bridge_status are tasksd's "virtual tasks", sharing the same ids as the console app).
      // **Grouped by function** (as requested in the console; kept consistent here), **bridge and adb strictly separate**.
      const CATS = ['Start / Stop', 'Channels (adb and bridge separate)', 'Maintenance', 'Emergency'];
      const TASKS = [
        { id: '1_start-dsh', cat: 'Start / Stop', label: 'Start DSH', icon: '▶', hint: 'Opens the page if already running' },
        { id: '4_soft-restart-dsh', cat: 'Start / Stop', label: 'Soft restart', icon: '↻', hint: 'Restart after SIGTERM', danger: true },
        { id: '6_hard-restart-dsh', cat: 'Start / Stop', label: 'Hard restart', icon: '⛔', hint: 'Restart after kill -9', danger: true },
        { id: '2_shutdown-dsh', cat: 'Start / Stop', label: 'Stop DSH', icon: '■', hint: 'Stops the service and closes the browser', danger: true },
        { id: '8_enable-wireless-adb', cat: 'Channels (adb and bridge separate)', label: 'Connect adb', icon: '⚡', hint: 'Wireless debugging only (needs working Wi-Fi)' },
        { id: 'bridge_wake', cat: 'Channels (adb and bridge separate)', label: 'Wake bridge', icon: '🌉', hint: 'Accessibility loopback only, no network needed' },
        { id: 'bridge_status', cat: 'Channels (adb and bridge separate)', label: 'Bridge status', icon: '🔎', hint: 'Bridge only: port / response / version' },
        { id: '10_net-fix', cat: 'Channels (adb and bridge separate)', label: 'Network first aid', icon: '🩺', hint: 'One tap when foreign sites die: decides whether the core stopped or the config went bad, then repairs' },
        { id: '7_reconnect-ai', cat: 'Channels (adb and bridge separate)', label: 'Restore all (adb + bridge)', icon: '🔗', hint: 'Use only when you need both' },
        { id: '3_backup-dsh', cat: 'Maintenance', label: 'Backup', icon: '💾', hint: 'Pack and verify the archive' },
        { id: '5_cleanup-dsh', cat: 'Maintenance', label: 'Cleanup', icon: '🧹', hint: 'Deletes only my artifacts' },
        { id: '9_revoke-pin', cat: 'Emergency', label: 'Revoke password access', icon: '🔑', hint: 'Deletes the stored password; the AI loses it at once' },
        { id: '0_emergency-stop', cat: 'Emergency', label: 'Emergency stop', icon: '🛑', hint: 'Revokes AI control of the phone', danger: true },
      ];

      const CSS = `
.mb-fab { position: fixed; right: 12px; bottom: calc(132px + env(safe-area-inset-bottom, 0px)); z-index: 60;
  width: 50px; height: 50px; border-radius: 25px; border: 1px solid var(--dsw-alias-border-l2);
  background: var(--dsw-alias-bg-layer-2); color: var(--dsw-alias-label-primary);
  display: flex; align-items: center; justify-content: center; font-size: 20px; cursor: pointer;
  box-shadow: 0 4px 14px rgba(0,0,0,.28); -webkit-tap-highlight-color: transparent; }
.mb-fab:active { transform: scale(.95); }
.mb-mask { position: fixed; inset: 0; z-index: 61; background: rgba(0,0,0,.42); }
.mb-sheet { position: fixed; left: 0; right: 0; bottom: 0; z-index: 62;
  background: var(--dsw-alias-bg-layer-1); border-top: 1px solid var(--dsw-alias-border-l1);
  border-radius: 16px 16px 0 0; padding: 10px 12px calc(14px + env(safe-area-inset-bottom, 0px));
  max-height: 78vh; overflow-y: auto; animation: mb-up .16s ease-out; }
@keyframes mb-up { from { transform: translateY(24px); opacity: .6 } to { transform: none; opacity: 1 } }
.mb-head { display: flex; align-items: center; gap: 8px; margin: 2px 2px 10px; }
.mb-title { font-size: 14px; font-weight: 600; color: var(--dsw-alias-label-primary); flex: 1; }
.mb-lamps { display: flex; gap: 10px; flex-wrap: wrap; margin: 0 2px 10px; font-size: 12px; color: var(--dsw-alias-label-secondary); }
.mb-lamp { display: inline-flex; align-items: center; gap: 5px; }
.mb-dot { width: 9px; height: 9px; border-radius: 5px; background: var(--dsw-alias-label-tertiary, #888); }
.mb-dot.on { background: #2ecc71; } .mb-dot.off { background: #e74c3c; }
.mb-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; }
.mb-task { display: flex; align-items: center; gap: 8px; min-height: 52px; padding: 8px 10px;
  border: 1px solid var(--dsw-alias-border-l1); border-radius: 11px; background: var(--dsw-alias-bg-layer-2);
  color: var(--dsw-alias-label-primary); font-size: 13px; text-align: left; cursor: pointer;
  -webkit-tap-highlight-color: transparent; }
.mb-task:active { transform: scale(.98); }
.mb-task.danger { border-color: color-mix(in srgb, var(--dsw-alias-state-error-primary) 45%, transparent); }
.mb-task.armed { background: var(--dsw-alias-state-error-primary); color: #fff; }
.mb-task[disabled] { opacity: .55; }
.mb-ic { width: 22px; text-align: center; font-size: 15px; }
.mb-txt { flex: 1; min-width: 0; }
.mb-name { display: block; font-weight: 600; }
.mb-hint { display: block; font-size: 11px; color: var(--dsw-alias-label-tertiary, #999); margin-top: 1px; }
.mb-task.armed .mb-hint { color: rgba(255,255,255,.85); }
.mb-close { min-width: 40px; min-height: 40px; border: 1px solid var(--dsw-alias-border-l1);
  border-radius: 10px; background: transparent; color: var(--dsw-alias-label-primary); font-size: 13px; cursor: pointer; }
.mb-out { margin-top: 10px; padding: 9px 10px; border-radius: 10px; background: var(--dsw-alias-bg-layer-2);
  border: 1px solid var(--dsw-alias-border-l1); font-size: 11.5px; line-height: 1.5; white-space: pre-wrap;
  word-break: break-all; max-height: 32vh; overflow-y: auto; color: var(--dsw-alias-label-secondary); }
.mb-cat { margin: 12px 2px 6px; font-size: 12px; font-weight: 600; color: var(--dsw-alias-label-secondary); }
.mb-auth { margin-top: 10px; padding: 10px; border-radius: 11px; border: 1px solid var(--dsw-alias-border-l1);
  background: var(--dsw-alias-bg-layer-2); }
.mb-auth-row { display: flex; align-items: center; gap: 8px; }
.mb-auth-lab { display: inline-flex; align-items: center; gap: 8px; flex: 1; font-size: 13px; font-weight: 600;
  color: var(--dsw-alias-label-primary); }
.mb-auth-lab input { width: 20px; height: 20px; }
.mb-auth-state { font-size: 12px; color: var(--dsw-alias-label-secondary); }
.mb-auth-state.on { color: #2ecc71; }
.mb-auth-ask { margin-top: 8px; padding-top: 8px; border-top: 1px dashed var(--dsw-alias-border-l1);
  display: flex; flex-wrap: wrap; align-items: center; gap: 8px; font-size: 12px;
  color: var(--dsw-alias-label-secondary); }
.mb-pw { min-height: 40px; width: 96px; padding: 0 10px; border-radius: 10px;
  border: 1px solid var(--dsw-alias-border-l1); background: transparent; color: var(--dsw-alias-label-primary);
  font-size: 16px; letter-spacing: 2px; }
.mb-btn-inline { display: inline-flex; align-items: center; gap: 4px; min-height: 40px; padding: 0 12px;
  border: 1px solid var(--dsw-alias-border-l1); border-radius: 10px; background: transparent;
  color: var(--dsw-alias-label-primary); font-size: 12.5px; cursor: pointer; }
/* ── mobile touch tweaks (add-only, reversible any time: set enabled to false in ~/.dsh-mobile-ui.json) ── */
@media (pointer: coarse) {
  header button, nav button, [class*="sidebarCol"] button { min-width: 42px; min-height: 42px; }
  [class*="composerSeat"] { padding-bottom: max(8px, env(safe-area-inset-bottom, 0px)) !important; }
  pre, table { overflow-x: auto; -webkit-overflow-scrolling: touch; }
  [class*="hoverOnly"], [class*="hover-only"] { opacity: 1 !important; visibility: visible !important; }
}
`;

      // ── style injection (reversible) ──
      const STYLE_ID = 'dsh-mobile-local';
      if (document.querySelector('style[data-plugin-css="' + STYLE_ID + '"]') === null) {
        const tag = document.createElement('style');
        tag.setAttribute('data-plugin-css', STYLE_ID);
        tag.textContent = CSS;
        document.head.appendChild(tag);
        if (ctx.effect) ctx.effect(() => () => tag.remove());
      }

      // ── RPC with filepanel (use it to read the token instead of building a host route) ──
      // ⚠ Pitfall: filepanel's resolveWithin resolves path on its own (**without joining root**),
      //   then validates with contains(root, target) → path must be **absolute**;
      //   a relative path resolves outside the workspace and reports "path is not inside the workspace" (I hit this).
      async function rpc(method, args) {
        const resp = await fetch('/__dsh__/filepanel/rpc', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ method, args }),
        });
        const res = await resp.json();
        if (!res || res.ok === false) throw new Error((res && res.error) || 'RPC failed');
        return res;
      }

      let TOKEN_CACHE = null;
      async function token() {
        if (TOKEN_CACHE) return TOKEN_CACHE;
        const r = await rpc('panel.readText', { root: HOME, path: HOME + '/.dsh-tasks-token' });
        TOKEN_CACHE = String(r.content || '').trim();
        if (!TOKEN_CACHE) throw new Error('cannot read ~/.dsh-tasks-token');
        return TOKEN_CACHE;
      }

      async function api(path) {
        const t = await token();
        const url = TASKSD + path + (path.indexOf('?') >= 0 ? '&' : '?') + 'token=' + encodeURIComponent(t);
        const resp = await fetch(url, { cache: 'no-store' });
        if (!resp.ok) {
          let msg = 'HTTP ' + resp.status;
          try { const j = await resp.json(); if (j && j.error) msg = j.error; } catch (e) {}
          throw new Error(msg);
        }
        return resp.json();
      }

      // ── task panel ──
      function Lamp(props) {
        return h('span', { className: 'mb-lamp' },
          h('i', { className: 'mb-dot ' + (props.on ? 'on' : 'off') }),
          props.text)
      }

      function TaskSheet(props) {
        const [status, setStatus] = useState(null);
        const [auth, setAuth] = useState(null);
        const [busy, setBusy] = useState('');
        const [armed, setArmed] = useState('');
        const [authArmed, setAuthArmed] = useState('');
        const [pw, setPw] = useState('');
        const [out, setOut] = useState('');
        const [err, setErr] = useState('');

        const load = useCallback(async () => {
          setErr('');
          try {
            const rs = await Promise.all([api('/status'), api('/auth')]);
            setStatus(rs[0].status); setAuth(rs[1].auth);
          } catch (e) { setErr('Failed to read status: ' + (e.message || e)); }
        }, []);

        // Authorization goes through POST (token in the body): tasksd writes both revoke and set to dsh-auth-pass, and the password is never logged
        const apiPost = useCallback(async (path, body) => {
          const t = await token();
          const resp = await fetch(TASKSD + path, {
            method: 'POST', cache: 'no-store', headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(Object.assign({ token: t }, body || {})),
          });
          const j = await resp.json().catch(() => ({}));
          if (!resp.ok || j.ok === false) throw new Error((j && j.error) || ('HTTP ' + resp.status));
          return j;
        }, []);

        const doRevoke = useCallback(async () => {
          setBusy('auth'); setErr(''); setOut('');
          try { const r = await apiPost('/auth/revoke'); setOut('[Revoke password access]\n' + (r.output || '(no output)')); setAuthArmed(''); await load(); }
          catch (e) { setErr('Revoke failed: ' + (e.message || e)); }
          setBusy('');
        }, [apiPost, load]);

        const doAuthorize = useCallback(async () => {
          setBusy('auth'); setErr(''); setOut('');
          try { const r = await apiPost('/auth/set', { pass: pw }); setOut('[Authorize the AI to use your password]\n' + (r.output || '(no output)')); setAuthArmed(''); setPw(''); await load(); }
          catch (e) { setErr('Authorization failed: ' + (e.message || e)); }
          setBusy('');
        }, [apiPost, load, pw]);

        useEffect(() => { load(); }, [load]);

        const fire = useCallback(async (task) => {
          if (task.danger && armed !== task.id) { setArmed(task.id); return; }
          setArmed(''); setBusy(task.id); setOut(''); setErr('');
          try {
            const r = await api('/run?task=' + encodeURIComponent(task.id));
            setOut('[' + task.label + '] took ' + r.seconds + 's, exit code ' + r.exit + '\n' + (r.output || '(no output)'));
            load();
          } catch (e) { setErr('Run failed: ' + (e.message || e)); }
          setBusy('');
        }, [armed, load]);

        const s = (status || {});
        const dshOn = !!(s.dsh && s.dsh.port);
        const brOn = !!(s.bridge && (s.bridge.ok || s.bridge.port));
        const adbOn = !!(s.adb && s.adb.devices && s.adb.devices.length);

        return h('div', null,
          h('div', { className: 'mb-mask', onClick: props.onClose }),
          h('div', { className: 'mb-sheet', onClick: (e) => e.stopPropagation() },
            h('div', { className: 'mb-head' },
              h('span', { className: 'mb-title' }, 'Tasks'),
              h('button', { className: 'mb-close', onClick: load, title: 'Refresh status' }, 'Refresh'),
              h('button', { className: 'mb-close', onClick: props.onClose }, 'Close')),
            h('div', { className: 'mb-lamps' },
              // State first, code second. This used to print the bare HTTP code ('DSH 401'), which tells
              // the user nothing about whether DSH is up — the Console app already showed a state.
              h(Lamp, { on: dshOn, text: 'DSH ' + (dshOn ? 'running' : 'stopped')
                + (s.dsh && s.dsh.http ? ' (HTTP ' + s.dsh.http + ')' : '') }),
              h(Lamp, { on: brOn, text: 'bridge ' + (s.bridge && s.bridge.ver ? 'v' + s.bridge.ver : (brOn ? 'up' : 'none')) }),
              h(Lamp, { on: adbOn, text: 'adb ' + (adbOn ? s.adb.devices[0] : 'not connected') })),
            CATS.map((cat) => {
              const list = TASKS.filter((t) => t.cat === cat);
              if (!list.length) return null;
              return h('div', { key: cat },
                h('div', { className: 'mb-cat' }, '▍' + cat),
                h('div', { className: 'mb-grid' },
                  list.map((t) => {
                    const st = (s.tasks || {})[t.id] || {};
                    const badge = st.last === 'done' ? ' · last ✔' : (st.last ? ' · last ' + st.last : '');
                    return h('button', {
                      key: t.id,
                      className: 'mb-task' + (t.danger ? ' danger' : '') + (armed === t.id ? ' armed' : ''),
                      disabled: busy !== '',
                      onClick: () => fire(t),
                    },
                      h('span', { className: 'mb-ic' }, t.icon),
                      h('span', { className: 'mb-txt' },
                        h('span', { className: 'mb-name' }, busy === t.id ? t.label + ' …' : (armed === t.id ? 'Tap again to confirm' : t.label)),
                        h('span', { className: 'mb-hint' }, t.hint + badge)))
                  })),
                // The Maintenance group carries the "password access" switch (exactly the same semantics as in the console app)
                cat === 'Maintenance' ? h('div', { className: 'mb-auth' },
                  h('div', { className: 'mb-auth-row' },
                    h('label', { className: 'mb-auth-lab' },
                      h('input', {
                        type: 'checkbox',
                        checked: !!(auth && auth.authorized),
                        disabled: busy !== '' || !auth,
                        onChange: (e) => setAuthArmed(e.target.checked ? 'set' : 'revoke'),
                      }),
                      h('span', null, 'Password access')),
                    h('span', { className: 'mb-auth-state' + (auth && auth.authorized ? ' on' : '') },
                      auth ? (auth.authorized ? 'Authorized' : 'Not authorized') : 'Loading…')),
                  h('div', { className: 'mb-hint' }, 'On = the AI can use your 6-digit lock-screen password to pass verification for you (installing packages, removing settings restrictions, etc.); Off = revoke immediately'),
                  authArmed === 'revoke' ? h('div', { className: 'mb-auth-ask' },
                    h('span', null, 'Once revoked, the AI can no longer use this password (installing packages will stop at the security check for you to handle).'),
                    h('button', { className: 'mb-btn-inline', disabled: busy !== '', onClick: doRevoke }, 'Confirm revoke'),
                    h('button', { className: 'mb-btn-inline', disabled: busy !== '', onClick: () => setAuthArmed('') }, 'Cancel')) : null,
                  authArmed === 'set' ? h('div', { className: 'mb-auth-ask' },
                    h('span', null, 'Enter the 6-digit lock-screen password (written only to the 600 file, never logged):'),
                    h('input', {
                      className: 'mb-pw', type: 'password', inputMode: 'numeric', maxLength: 6, value: pw,
                      onChange: (e) => setPw(String(e.target.value || '').replace(/\D/g, '').slice(0, 6)),
                    }),
                    h('button', { className: 'mb-btn-inline', disabled: busy !== '' || pw.length !== 6, onClick: doAuthorize }, 'Authorize'),
                    h('button', { className: 'mb-btn-inline', disabled: busy !== '', onClick: () => { setAuthArmed(''); setPw(''); } }, 'Cancel')) : null,
                  auth ? h('div', { className: 'mb-hint' }, auth.detail) : null) : null);
            }),
            err ? h('div', { className: 'mb-out' }, '⚠ ' + err) : null,
            out ? h('div', { className: 'mb-out' }, out) : null,
            h('div', { className: 'mb-lamps', style: { marginTop: '10px' } },
              h('span', { className: 'mb-hint' }, 'Dangerous actions (restart / stop / emergency stop) need a second tap to confirm; these are the same scripts as the home-screen widgets. '
                + 'You can revoke password access in three ways: this switch / widget 9_revoke-pin / rm ~/.dsh-auth-pass.'))))
      }

      function Fab() {
        const [open, setOpen] = useState(false);
        return h(React.Fragment, null,
          h('button', { className: 'mb-fab', title: 'Task shortcuts', onClick: () => setOpen(true) }, '☰'),
          open ? h(TaskSheet, { onClose: () => setOpen(false) }) : null)
      }

      // Each slot registration has its own try/catch: one missing slot must not affect the other, let alone crash the page
      function trySlot(slot, id, render) {
        try {
          ctx.slots.inject(slot, () => ctx.slots.register({ name: slot, id: id }, render))
        } catch (e) {
          console.warn('[mobileui] slot ' + slot + ' failed to register:', e && e.message)
        }
      }

      // ── master switch: {"enabled": false} in ~/.dsh-mobile-ui.json disables everything (takes effect on refresh) ──
      // On by default; stays on when the file cannot be read (works right after install). This is the user's "one-switch undo".
      let ENABLED = true
      const styleTag = document.querySelector('style[data-plugin-css="' + STYLE_ID + '"]')

      function mount() {
        trySlot('shell.overlay', 'mobile-tasks-fab', () => h(Fab, null))
        trySlot('conversation.input.left', 'mobile-tasks-btn', () => h(Fab, null))
      }
      function unmount() {
        if (styleTag) styleTag.remove()
      }

      if (ENABLED) mount()
      ;(async () => {
        try {
          const r = await rpc('panel.readText', { root: HOME, path: HOME + '/.dsh-mobile-ui.json' })
          const cfg = JSON.parse(String(r.content || '{}'))
          if (cfg && cfg.enabled === false) {
            ENABLED = false
            unmount()
            console.log('[mobileui] disabled by ~/.dsh-mobile-ui.json')
          }
        } catch (e) { /* no such file means it stays on by default */ }
      })()

      console.log('[mobileui] client ready')
    }

    exports.apply = apply;
    exports.inject = inject;
    return module.exports;
  },
});
