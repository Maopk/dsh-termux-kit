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

      // 组件清单：label 是给人看的，id 是白名单里的名字（dsh-tasksd 只认这些；
      // bridge_wake / bridge_status 是 tasksd 的"虚拟任务"，与控制台 App 用同一套 id）。
      // **按功能分类**（用户在控制台提的要求，这里保持一致），**桥与 adb 严格分开**。
      const CATS = ['启动 / 停止', '通道（adb 与桥分开）', '维护', '紧急'];
      const TASKS = [
        { id: '1_启动DSH', cat: '启动 / 停止', label: '启动 DSH', icon: '▶', hint: '已在跑则直接开页面' },
        { id: '4_软重启DSH', cat: '启动 / 停止', label: '软重启', icon: '↻', hint: 'SIGTERM 后重启', danger: true },
        { id: '6_硬重启DSH', cat: '启动 / 停止', label: '硬重启', icon: '⛔', hint: '-9 强杀后重启', danger: true },
        { id: '2_关闭DSH', cat: '启动 / 停止', label: '关闭 DSH', icon: '■', hint: '停服务并关浏览器', danger: true },
        { id: '8_自动开无线调试', cat: '通道（adb 与桥分开）', label: '连 adb', icon: '⚡', hint: '只走无线调试（需要可用 Wi-Fi）' },
        { id: 'bridge_wake', cat: '通道（adb 与桥分开）', label: '唤醒桥', icon: '🌉', hint: '只走无障碍回环，不需要网络' },
        { id: 'bridge_status', cat: '通道（adb 与桥分开）', label: '看桥状态', icon: '🔎', hint: '只查桥：端口 / 应答 / 版本' },
        { id: '7_重连AI通道', cat: '通道（adb 与桥分开）', label: '全部恢复（adb + 桥）', icon: '🔗', hint: '两条都要时才用' },
        { id: '3_备份DSH', cat: '维护', label: '备份', icon: '💾', hint: '打包并校验归档' },
        { id: '5_清理DSH', cat: '维护', label: '清理', icon: '🧹', hint: '只删我的产物' },
        { id: '9_撤销密码授权', cat: '紧急', label: '收回密码使用权', icon: '🔑', hint: '删掉存着的密码，AI 立刻用不了' },
        { id: '0_紧急停止', cat: '紧急', label: '紧急停止', icon: '🛑', hint: '撤销 AI 对手机的控制', danger: true },
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
/* ── 手机端触屏微调（只加不删，随时可关：把 ~/.dsh-mobile-ui.json 里 enabled 改成 false）── */
@media (pointer: coarse) {
  header button, nav button, [class*="sidebarCol"] button { min-width: 42px; min-height: 42px; }
  [class*="composerSeat"] { padding-bottom: max(8px, env(safe-area-inset-bottom, 0px)) !important; }
  pre, table { overflow-x: auto; -webkit-overflow-scrolling: touch; }
  [class*="hoverOnly"], [class*="hover-only"] { opacity: 1 !important; visibility: visible !important; }
}
`;

      // ── 样式注入（可撤销）──
      const STYLE_ID = 'dsh-mobile-local';
      if (document.querySelector('style[data-plugin-css="' + STYLE_ID + '"]') === null) {
        const tag = document.createElement('style');
        tag.setAttribute('data-plugin-css', STYLE_ID);
        tag.textContent = CSS;
        document.head.appendChild(tag);
        if (ctx.effect) ctx.effect(() => () => tag.remove());
      }

      // ── 与 filepanel 的 RPC（用它读 token，避免自己造宿主路由）──
      // ⚠ 坑：filepanel 的 resolveWithin 只把 path 单独 resolve（**不拼 root**），
      //   再用 contains(root, target) 校验 → path 必须传**绝对路径**，
      //   传相对路径会被解析到工作区外并报"路径不在工作区内"（我踩过）。
      async function rpc(method, args) {
        const resp = await fetch('/__dsh__/filepanel/rpc', {
          method: 'POST', headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ method, args }),
        });
        const res = await resp.json();
        if (!res || res.ok === false) throw new Error((res && res.error) || 'RPC 失败');
        return res;
      }

      let TOKEN_CACHE = null;
      async function token() {
        if (TOKEN_CACHE) return TOKEN_CACHE;
        const r = await rpc('panel.readText', { root: HOME, path: HOME + '/.dsh-tasks-token' });
        TOKEN_CACHE = String(r.content || '').trim();
        if (!TOKEN_CACHE) throw new Error('读不到 ~/.dsh-tasks-token');
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

      // ── 任务面板 ──
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
          } catch (e) { setErr('读状态失败：' + (e.message || e)); }
        }, []);

        // 授权走 POST（token 放 body）：收回/写入都由 tasksd 落到 dsh-auth-pass，密码不写日志
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
          try { const r = await apiPost('/auth/revoke'); setOut('【收回密码使用权】\n' + (r.output || '(无输出)')); setAuthArmed(''); await load(); }
          catch (e) { setErr('收回失败：' + (e.message || e)); }
          setBusy('');
        }, [apiPost, load]);

        const doAuthorize = useCallback(async () => {
          setBusy('auth'); setErr(''); setOut('');
          try { const r = await apiPost('/auth/set', { pass: pw }); setOut('【授权 AI 使用你的密码】\n' + (r.output || '(无输出)')); setAuthArmed(''); setPw(''); await load(); }
          catch (e) { setErr('授权失败：' + (e.message || e)); }
          setBusy('');
        }, [apiPost, load, pw]);

        useEffect(() => { load(); }, [load]);

        const fire = useCallback(async (task) => {
          if (task.danger && armed !== task.id) { setArmed(task.id); return; }
          setArmed(''); setBusy(task.id); setOut(''); setErr('');
          try {
            const r = await api('/run?task=' + encodeURIComponent(task.id));
            setOut('【' + task.label + '】用时 ' + r.seconds + 's，退出码 ' + r.exit + '\n' + (r.output || '(无输出)'));
            load();
          } catch (e) { setErr('执行失败：' + (e.message || e)); }
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
              h('span', { className: 'mb-title' }, '任务'),
              h('button', { className: 'mb-close', onClick: load, title: '刷新状态' }, '刷新'),
              h('button', { className: 'mb-close', onClick: props.onClose }, '关闭')),
            h('div', { className: 'mb-lamps' },
              h(Lamp, { on: dshOn, text: 'DSH ' + (s.dsh && s.dsh.http ? s.dsh.http : (dshOn ? '在跑' : '停了')) }),
              h(Lamp, { on: brOn, text: '桥 ' + (s.bridge && s.bridge.ver ? 'v' + s.bridge.ver : (brOn ? '在' : '无')) }),
              h(Lamp, { on: adbOn, text: 'adb ' + (adbOn ? s.adb.devices[0] : '未连') })),
            CATS.map((cat) => {
              const list = TASKS.filter((t) => t.cat === cat);
              if (!list.length) return null;
              return h('div', { key: cat },
                h('div', { className: 'mb-cat' }, '▍' + cat),
                h('div', { className: 'mb-grid' },
                  list.map((t) => {
                    const st = (s.tasks || {})[t.id] || {};
                    const badge = st.last === '完成' ? ' · 上次✔' : (st.last ? ' · 上次' + st.last : '');
                    return h('button', {
                      key: t.id,
                      className: 'mb-task' + (t.danger ? ' danger' : '') + (armed === t.id ? ' armed' : ''),
                      disabled: busy !== '',
                      onClick: () => fire(t),
                    },
                      h('span', { className: 'mb-ic' }, t.icon),
                      h('span', { className: 'mb-txt' },
                        h('span', { className: 'mb-name' }, busy === t.id ? t.label + ' …' : (armed === t.id ? '再点一次确认' : t.label)),
                        h('span', { className: 'mb-hint' }, t.hint + badge)))
                  })),
                // 维护类下面挂「密码使用权」开关（与控制台 App 里的语义完全一致）
                cat === '维护' ? h('div', { className: 'mb-auth' },
                  h('div', { className: 'mb-auth-row' },
                    h('label', { className: 'mb-auth-lab' },
                      h('input', {
                        type: 'checkbox',
                        checked: !!(auth && auth.authorized),
                        disabled: busy !== '' || !auth,
                        onChange: (e) => setAuthArmed(e.target.checked ? 'set' : 'revoke'),
                      }),
                      h('span', null, '密码使用权')),
                    h('span', { className: 'mb-auth-state' + (auth && auth.authorized ? ' on' : '') },
                      auth ? (auth.authorized ? '已授权' : '未授权') : '读取中…')),
                  h('div', { className: 'mb-hint' }, '开＝AI 可用你那 6 位锁屏密码替你过身份验证（装包、解除设置限制等）；关＝立刻收回'),
                  authArmed === 'revoke' ? h('div', { className: 'mb-auth-ask' },
                    h('span', null, '收回后 AI 不能再动用这个密码（装包的「安全验证」等会停在你这儿）。'),
                    h('button', { className: 'mb-btn-inline', disabled: busy !== '', onClick: doRevoke }, '确认收回'),
                    h('button', { className: 'mb-btn-inline', disabled: busy !== '', onClick: () => setAuthArmed('') }, '取消')) : null,
                  authArmed === 'set' ? h('div', { className: 'mb-auth-ask' },
                    h('span', null, '输入 6 位锁屏密码（只写进 600 文件，不进日志）：'),
                    h('input', {
                      className: 'mb-pw', type: 'password', inputMode: 'numeric', maxLength: 6, value: pw,
                      onChange: (e) => setPw(String(e.target.value || '').replace(/\D/g, '').slice(0, 6)),
                    }),
                    h('button', { className: 'mb-btn-inline', disabled: busy !== '' || pw.length !== 6, onClick: doAuthorize }, '授权'),
                    h('button', { className: 'mb-btn-inline', disabled: busy !== '', onClick: () => { setAuthArmed(''); setPw(''); } }, '取消')) : null,
                  auth ? h('div', { className: 'mb-hint' }, auth.detail) : null) : null);
            }),
            err ? h('div', { className: 'mb-out' }, '⚠ ' + err) : null,
            out ? h('div', { className: 'mb-out' }, out) : null,
            h('div', { className: 'mb-lamps', style: { marginTop: '10px' } },
              h('span', { className: 'mb-hint' }, '危险动作（重启/关闭/紧急停止）需要点两次确认；这些和桌面小组件是同一批脚本。'
                + '密码使用权的收回入口有三个：这个开关 / 小组件 9_撤销密码授权 / rm ~/.dsh-auth-pass。'))))
      }

      function Fab() {
        const [open, setOpen] = useState(false);
        return h(React.Fragment, null,
          h('button', { className: 'mb-fab', title: '任务快捷按钮', onClick: () => setOpen(true) }, '☰'),
          open ? h(TaskSheet, { onClose: () => setOpen(false) }) : null)
      }

      // 每个插槽注册都单独 try/catch：一个插槽不存在也不能连累另一个、更不能把页面搞崩
      function trySlot(slot, id, render) {
        try {
          ctx.slots.inject(slot, () => ctx.slots.register({ name: slot, id: id }, render))
        } catch (e) {
          console.warn('[mobileui] 插槽 ' + slot + ' 注册失败：', e && e.message)
        }
      }

      // ── 总开关：~/.dsh-mobile-ui.json 里 {"enabled": false} 即整体停用（刷新生效）──
      // 默认开；读不到文件也保持开（新装就能用）。这是给用户留的"一键撤销"。
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
            console.log('[mobileui] 已被 ~/.dsh-mobile-ui.json 停用')
          }
        } catch (e) { /* 没有这个文件就是默认开 */ }
      })()

      console.log('[mobileui] client ready')
    }

    exports.apply = apply;
    exports.inject = inject;
    return module.exports;
  },
});
