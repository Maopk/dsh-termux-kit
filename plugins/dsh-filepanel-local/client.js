window.__ModuleLoader__.load({
  id: 'dsh-filepanel-local',
  factory: (require) => {
    var module = { exports: {} };
    var exports = module.exports;

    const React = require('react');
    const inject = ['slots', 'timer', 'workspaces'];

    function apply(ctx) {
    const h = React.createElement

    const CSS_TEXT = `
@keyframes dsh-fp-slide { from { transform: translateX(48px); opacity: 0 } to { transform: none; opacity: 1 } }
.fp-btn { display: inline-flex; align-items: center; gap: 6px; padding: 5px 10px; border: 1px solid var(--dsw-alias-border-l1); border-radius: 7px; background: transparent; color: var(--dsw-alias-label-primary); font-size: 12px; cursor: pointer; white-space: nowrap; user-select: none; transition: background .12s ease, border-color .12s ease, transform .06s ease, filter .12s ease; }
.fp-btn:hover { background: var(--dsw-alias-bg-layer-2); border-color: var(--dsw-alias-border-l2); }
.fp-btn:active { transform: scale(.97); }
.fp-btn:focus-visible { outline: 2px solid var(--dsw-alias-brand-primary); outline-offset: 1px; }
.fp-btn.primary { background: var(--dsw-alias-brand-primary); border-color: transparent; color: #fff; }
.fp-btn.primary:hover { filter: brightness(1.08); }
.fp-btn.primary:active { filter: brightness(.94); }
.fp-btn.on { border-color: var(--dsw-alias-brand-primary); color: var(--dsw-alias-brand-primary); }
.fp-btn.icon { padding: 5px 7px; }
.fp-btn.danger:hover { border-color: var(--dsw-alias-state-error-primary); color: var(--dsw-alias-state-error-primary); }
.fp-act { display: inline-flex; align-items: center; gap: 4px; border: none; background: transparent; color: var(--dsw-alias-label-secondary); font-size: 11px; padding: 3px 6px; border-radius: 5px; cursor: pointer; white-space: nowrap; transition: background .1s ease, color .1s ease; }
.fp-act:hover { background: var(--dsw-alias-bg-layer-1); color: var(--dsw-alias-label-primary); }
.fp-act.danger { color: var(--dsw-alias-state-error-primary); }
.fp-row { display: flex; align-items: center; gap: 10px; padding: 6px 10px; border-radius: 7px; cursor: pointer; transition: background .1s ease; }
.fp-row:hover { background: var(--dsw-alias-bg-layer-2); }
.fp-row.selected { background: color-mix(in srgb, var(--dsw-alias-brand-primary) 12%, transparent); }
.fp-row .fp-row-actions { display: none; margin-left: auto; flex: none; }
.fp-row:hover .fp-row-actions { display: inline-flex; gap: 2px; }
.fp-crumb { display: inline-flex; align-items: center; gap: 4px; padding: 2px 6px; border-radius: 5px; white-space: nowrap; cursor: pointer; transition: background .12s ease, color .12s ease; }
.fp-crumb:hover { background: var(--dsw-alias-bg-layer-2); color: var(--dsw-alias-label-primary); }
.fp-crumb.current { cursor: default; color: var(--dsw-alias-label-primary); font-weight: 500; }
.fp-input { box-sizing: border-box; padding: 6px 9px; border: 1px solid var(--dsw-alias-border-l1); border-radius: 7px; background: var(--dsw-alias-bg-layer-1); color: var(--dsw-alias-label-primary); font-size: 12px; outline: none; transition: border-color .12s ease, box-shadow .12s ease; }
.fp-input:focus { border-color: var(--dsw-alias-brand-primary); box-shadow: 0 0 0 2px color-mix(in srgb, var(--dsw-alias-brand-primary) 20%, transparent); }
.fp-scroll::-webkit-scrollbar { width: 8px; height: 8px; }
.fp-scroll::-webkit-scrollbar-thumb { background: var(--dsw-alias-border-l1); border-radius: 4px; }
.fp-scroll::-webkit-scrollbar-thumb:hover { background: var(--dsw-alias-border-l2); }
.fp-scroll::-webkit-scrollbar-track { background: transparent; }
.fp-checkbox { flex: none; width: 14px; height: 14px; margin: 0; accent-color: var(--dsw-alias-brand-primary); cursor: pointer; }
.fp-dir { display: flex; align-items: center; gap: 8px; padding: 6px 8px; border-radius: 6px; cursor: pointer; }
.fp-dir:hover { background: var(--dsw-alias-bg-layer-2); }
`
    ctx.effect(() => {
      const styleId = 'dsh-filepanel-local'
      if (document.querySelector('style[data-plugin-css="' + styleId + '"]') !== null) return
      const tag = document.createElement('style')
      tag.setAttribute('data-plugin-css', styleId)
      tag.textContent = CSS_TEXT
      document.head.appendChild(tag)
      return () => { tag.remove() }
    })

    async function call(method, args) {
      let res = null
      if (typeof host !== 'undefined' && host && typeof host.call === 'function') {
        res = await host.call(method, args)
      } else {
        const resp = await fetch('/__dsh__/filepanel/rpc', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ method, args }),
        })
        res = await resp.json()
      }
      if (!res || res.ok === false) {
        throw new Error((res && res.error) ? res.error : 'Operation failed')
      }
      return res
    }

    const TEXT_EXT = ['txt','md','markdown','json','js','mjs','cjs','ts','tsx','jsx','py','css','scss','less','html','htm','xml','yml','yaml','csv','tsv','log','sh','bash','zsh','go','rs','java','c','cc','cpp','h','hpp','sql','toml','ini','cfg','conf','env','gitignore','gitattributes','dockerfile','makefile','graphql','vue','svelte','rb','php','lua','swift','kt','kts','dart','r','jl','ex','exs','ps1','bat','cmd','properties','prisma']
    const IMG_EXT = ['png','jpg','jpeg','gif','webp','svg','bmp','ico','avif']
    function isTextName(name) {
      const n = String(name || '').toLowerCase()
      for (const ext of TEXT_EXT) {
        if (n === ext || n.endsWith('.' + ext)) return true
      }
      return false
    }
    function extOf(name) { return String(name || '').split('.').pop().toLowerCase() }
    function isImageName(name) { const e = extOf(name); return IMG_EXT.indexOf(e) >= 0 }
    function isPdfName(name) { return extOf(name) === 'pdf' }

    const LANG_MAP = { js:'js', mjs:'js', cjs:'js', jsx:'js', ts:'ts', tsx:'ts', py:'python', rb:'ruby', go:'go', rs:'rust', java:'java', c:'c', cpp:'cpp', h:'c', hpp:'cpp', cs:'csharp', php:'php', swift:'swift', kt:'kotlin', sql:'sql', sh:'shell', bash:'shell', zsh:'shell', yml:'yaml', yaml:'yaml', json:'json', html:'html', htm:'html', css:'css', scss:'css', less:'css', vue:'html', svelte:'html', xml:'xml', toml:'ini', ini:'ini', cfg:'ini', conf:'ini', properties:'ini', dockerfile:'shell', makefile:'make', bat:'shell', ps1:'shell', cmd:'shell' }
    const KEYWORDS = new Set(['function','return','if','else','elif','for','while','do','switch','case','break','continue','const','let','var','new','class','extends','import','export','from','default','try','catch','finally','throw','async','await','yield','typeof','instanceof','in','of','this','super','null','undefined','true','false','def','lambda','and','or','not','pass','print','None','True','False','public','private','protected','static','void','int','float','double','char','bool','string','struct','enum','interface','package','namespace','using','global','nonlocal','raise','except','assert','del','is','match','as','with','select','from','where','insert','update','delete','create','table','into','values','set','order','by','group','having','join','on','union','all','distinct','limit','offset','begin','commit','rollback'])
    const TOKEN_RE = /(\/\/[^\n]*|#[^\n]*)|("(?:[^"\\\n]|\\.)*"|'(?:[^'\\\n]|\\.)*'|`(?:[^`\\]|\\.)*`)|(\b\d[\w_.]*\b)|([A-Za-z_$][\w$]*)|(\s+)|([^\sA-Za-z_$0-9]+)/g
    function tokenizeLine(line) {
      const out = []
      let m
      TOKEN_RE.lastIndex = 0
      while ((m = TOKEN_RE.exec(line)) !== null) {
        if (m[1] !== undefined) out.push({ t: m[1], type: 'comment' })
        else if (m[2] !== undefined) out.push({ t: m[2], type: 'string' })
        else if (m[3] !== undefined) out.push({ t: m[3], type: 'number' })
        else if (m[4] !== undefined) out.push({ t: m[4], type: KEYWORDS.has(m[4]) ? 'keyword' : 'default' })
        else if (m[5] !== undefined) out.push({ t: m[5], type: 'default' })
        else out.push({ t: m[6], type: 'default' })
      }
      return out
    }
    const TOKEN_STYLE = {
      comment: { color: '#8a9199', fontStyle: 'italic' },
      string: { color: '#2e9e5b' },
      number: { color: '#c78b36' },
      keyword: { color: '#7c5cf0', fontWeight: 500 },
      default: {},
    }
    const MAX_HL_LINES = 4000

    function formatSize(n) {
      const v = Number(n) || 0
      if (v < 1024) return v + ' B'
      if (v < 1048576) return (v / 1024).toFixed(1) + ' KB'
      if (v < 1073741824) return (v / 1048576).toFixed(1) + ' MB'
      return (v / 1073741824).toFixed(1) + ' GB'
    }
    function speedText(bps) {
      if (bps >= 1048576) return (bps / 1048576).toFixed(1) + ' MB/s'
      if (bps >= 1024) return (bps / 1024).toFixed(1) + ' KB/s'
      return Math.round(bps) + ' B/s'
    }

    function readSliceBase64(file, start, end) {
      return new Promise((resolve, reject) => {
        const r = new FileReader()
        r.onload = () => { const s = String(r.result || ''); resolve(s.slice(s.indexOf(',') + 1)) }
        r.onerror = () => reject(new Error('Failed to read file'))
        r.readAsDataURL(file.slice(start, end))
      })
    }

    // ---- SVG line icons (no emoji) ----
    const ICON_PATHS = {
      folder: 'M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7z',
      file: 'M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z',
      image: 'M4 5h16v14H4zM8.5 9.5a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3zM20 17l-5-5-4 4-3-3-4 4',
      pdf: 'M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM12 9v7M9 12h6',
      archive: 'M7 3h7l5 5v11a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2zM10 9h5M10 12h5M10 15h3',
      search: 'M11 19a8 8 0 1 0 0-16 8 8 0 0 0 0 16zM21 21l-4.3-4.3',
      up: 'M12 19V5M5 12l7-7 7 7',
      refresh: 'M21 12a9 9 0 1 1-2.6-6.4M21 3v6h-6',
      download: 'M12 3v12M7 10l5 5 5-5M4 21h16',
      upload: 'M12 21V9M7 14l5-5 5 5M4 3h16',
      plusFile: 'M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8zM14 3v5h5M12 11v6M9 14h6',
      plusFolder: 'M3 7a2 2 0 0 1 2-2h4l2 2h8a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7zM12 10v6M9 13h6',
      rename: 'M4 20h4L19 9a2 2 0 0 0-4-4L4 16v4z',
      trash: 'M4 7h16M9 7V4h6v3M6 7l1 13h10l1-13M10 11v6M14 11v6',
      copy: 'M8 8V5a1 1 0 0 1 1-1h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1h-3M5 8h10a1 1 0 0 1 1 1v10a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V9a1 1 0 0 1 1-1z',
      move: 'M5 8l-3 4 3 4M2 12h9M14 4l8 8-8 8M22 12h-8',
      external: 'M14 4h6v6M20 4L10 14M18 13v6a1 1 0 0 1-1 1H5a1 1 0 0 1-1-1V7a1 1 0 0 1 1-1h6',
      close: 'M6 6l12 12M18 6L6 18',
      edit: 'M4 20h4L19 9a2 2 0 0 0-4-4L4 16v4z',
      save: 'M5 3h12l2 2v16H3V5l2-2zM7 3v6h8V3M7 21v-7h10v7',
      check: 'M4 12l5 5L20 7',
      chevron: 'M9 6l6 6-6 6',
      unzip: 'M12 3v10M8 9l4 4 4-4M4 19h16',
      globe: 'M12 3a9 9 0 1 0 0 18 9 9 0 0 0 0-18zM3 12h18M12 3c-2 2.6-3 6-3 9s1 6.4 3 9c2-2.6 3-6 3-9s-1-6.4-3-9z',
      grid: 'M4 4h7v7H4zM13 4h7v7h-7zM4 13h7v7H4zM13 13h7v7h-7z',
    }
    function Icon(props) {
      const d = ICON_PATHS[props.name] || ICON_PATHS.file
      return h('svg', {
        width: props.size || 14,
        height: props.size || 14,
        viewBox: '0 0 24 24',
        fill: 'none',
        stroke: 'currentColor',
        strokeWidth: 1.8,
        strokeLinecap: 'round',
        strokeLinejoin: 'round',
        style: { flex: 'none', ...(props.style || {}) },
      }, h('path', { d }))
    }
    function entryIconName(entry) {
      if (entry.type === 'directory') return 'folder'
      const e = extOf(entry.name)
      if (IMG_EXT.indexOf(e) >= 0) return 'image'
      if (e === 'pdf') return 'pdf'
      if (e === 'zip') return 'archive'
      return 'file'
    }

    function Btn(props) {
      return h('button', {
        className: 'fp-btn' + (props.kind === 'primary' ? ' primary' : '') + (props.on ? ' on' : '') + (props.iconOnly ? ' icon' : '') + (props.danger ? ' danger' : ''),
        style: props.style,
        title: props.title,
        onClick: props.onClick,
      }, props.children)
    }

    const CHUNK = 1024 * 1024
    async function uploadFile(root, path, file, onProgress, isCancelled) {
      await call('panel.uploadStart', { root, path, size: file.size })
      const total = file.size
      let sent = 0
      const startedAt = Date.now()
      const throwCancelled = async () => {
        await call('panel.uploadAbort', { root, path }).catch(() => {})
        throw Object.assign(new Error('Cancelled'), { code: 'CANCELLED' })
      }
      try {
        for (let start = 0; start < total; start += CHUNK) {
          if (isCancelled && isCancelled()) return await throwCancelled()
          const end = Math.min(start + CHUNK, total)
          const b64 = await readSliceBase64(file, start, end)
          if (isCancelled && isCancelled()) return await throwCancelled()
          sent = end
          if (onProgress) onProgress(sent, total, startedAt)
          await call('panel.uploadChunk', { root, path, base64: b64, final: end >= total })
        }
      } catch (e) {
        if (!(e && e.code === 'CANCELLED')) call('panel.uploadAbort', { root, path }).catch(() => {})
        throw e
      }
    }

    // ---- Layout styles (dynamic parts inline, the rest via CSS classes) ----
    const S = {
      hotzone: { position: 'fixed', right: 0, top: 0, bottom: 0, width: 8, zIndex: 290, pointerEvents: 'auto' },
      tab: { position: 'fixed', right: 0, top: '50%', transform: 'translateY(-50%)', zIndex: 295, pointerEvents: 'auto', display: 'flex', alignItems: 'center', justifyContent: 'center', width: 22, height: 84, borderRadius: '10px 0 0 10px', background: 'var(--dsw-alias-bg-overlay)', border: '1px solid var(--dsw-alias-border-l1)', borderRight: 'none', color: 'var(--dsw-alias-label-secondary)', cursor: 'pointer', boxShadow: '-3px 0 10px rgba(0,0,0,0.15)', transition: 'color .12s ease, border-color .12s ease, width .12s ease' },
      overlay: { position: 'fixed', inset: 0, zIndex: 300, pointerEvents: 'none', display: 'flex', justifyContent: 'flex-end' },
      catcher: { position: 'absolute', inset: 0, pointerEvents: 'auto' },
      drawer: { position: 'relative', zIndex: 1, pointerEvents: 'auto', height: '100%', background: 'var(--dsw-alias-bg-overlay)', borderLeft: '1px solid var(--dsw-alias-border-l1)', display: 'flex', flexDirection: 'column', color: 'var(--dsw-alias-label-primary)', font: '13px/1.5 -apple-system, Segoe UI, Roboto, PingFang SC, Microsoft YaHei, sans-serif', boxShadow: '-12px 0 32px rgba(0,0,0,0.18)', animation: 'dsh-fp-slide .18s ease-out' },
      resizeHandle: { position: 'absolute', left: 0, top: 0, bottom: 0, width: 12, cursor: 'col-resize', zIndex: 999, display: 'flex', alignItems: 'center', justifyContent: 'center' },
      resizeGrip: { width: 4, height: 52, borderRadius: 2, background: 'var(--dsw-alias-border-l1)', boxShadow: '0 0 0 1px var(--dsw-alias-bg-overlay)', transition: 'background .12s ease' },
      header: { display: 'flex', alignItems: 'center', gap: 8, padding: '11px 14px 11px 18px', borderBottom: '1px solid var(--dsw-alias-border-l1)', flex: 'none' },
      headerTitle: { display: 'inline-flex', alignItems: 'center', gap: 7, fontWeight: 600, fontSize: 13, whiteSpace: 'nowrap', color: 'var(--dsw-alias-label-primary)' },
      breadcrumb: { display: 'flex', alignItems: 'center', gap: 2, overflow: 'hidden', flex: 1, minWidth: 0, fontSize: 12, color: 'var(--dsw-alias-label-secondary)' },
      crumbSep: { color: 'var(--dsw-alias-label-secondary)', opacity: 0.45, flex: 'none', display: 'inline-flex' },
      toolbar: { display: 'flex', gap: 6, padding: '8px 14px 8px 18px', borderBottom: '1px solid var(--dsw-alias-border-l1)', flexWrap: 'wrap', flex: 'none', alignItems: 'center' },
      batchBar: { display: 'flex', alignItems: 'center', gap: 6, padding: '7px 14px 7px 18px', borderBottom: '1px solid var(--dsw-alias-border-l1)', flex: 'none', flexWrap: 'wrap', background: 'color-mix(in srgb, var(--dsw-alias-brand-primary) 6%, transparent)' },
      batchCount: { fontSize: 12, fontWeight: 500, marginRight: 4, color: 'var(--dsw-alias-label-primary)' },
      list: { flex: 1, overflow: 'auto', padding: '6px 8px', transition: 'outline .12s ease' },
      name: { flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' },
      size: { flex: 'none', color: 'var(--dsw-alias-label-secondary)', fontSize: 11 },
      empty: { padding: 32, textAlign: 'center', color: 'var(--dsw-alias-label-secondary)', fontSize: 12 },
      status: { padding: '6px 14px 6px 18px', fontSize: 12, borderTop: '1px solid var(--dsw-alias-border-l1)', flex: 'none', minHeight: 30, boxSizing: 'border-box' },
      statusOk: { color: 'var(--dsw-alias-state-success-primary)' },
      statusErr: { color: 'var(--dsw-alias-state-error-primary)' },
      progressWrap: { padding: '8px 14px 8px 18px', borderTop: '1px solid var(--dsw-alias-border-l1)', flex: 'none' },
      progressText: { fontSize: 12, marginBottom: 5, color: 'var(--dsw-alias-label-secondary)' },
      progressTrack: { height: 4, background: 'var(--dsw-alias-bg-layer-2)', borderRadius: 2, overflow: 'hidden' },
      progressBar: { height: '100%', background: 'var(--dsw-alias-brand-primary)', transition: 'width .12s linear', borderRadius: 2 },
      preview: { display: 'flex', flexDirection: 'column', height: '48%', borderTop: '1px solid var(--dsw-alias-border-l1)', flex: 'none' },
      previewHead: { display: 'flex', alignItems: 'center', gap: 8, padding: '7px 12px', borderBottom: '1px solid var(--dsw-alias-border-l1)', fontSize: 12 },
      previewBody: { flex: 1, overflow: 'auto', padding: '8px 0', margin: 0, font: '12px/1.6 ui-monospace, SFMono-Regular, Consolas, monospace', color: 'var(--dsw-alias-label-primary)' },
      textarea: { flex: 1, resize: 'none', border: 'none', outline: 'none', background: 'transparent', color: 'var(--dsw-alias-label-primary)', padding: '10px 12px', font: '12px/1.6 ui-monospace, SFMono-Regular, Consolas, monospace' },
      codeLine: { display: 'flex' },
      lineNo: { flex: 'none', width: 44, textAlign: 'right', paddingRight: 10, color: 'var(--dsw-alias-label-secondary)', opacity: 0.5, userSelect: 'none' },
      lineCode: { flex: 1, whiteSpace: 'pre' },
      modal: { position: 'absolute', inset: 0, display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(0,0,0,0.35)', zIndex: 5 },
      modalBox: { width: 340, background: 'var(--dsw-alias-bg-overlay)', border: '1px solid var(--dsw-alias-border-l1)', borderRadius: 12, padding: 16, boxShadow: '0 12px 40px rgba(0,0,0,.28)' },
      modalTitle: { fontWeight: 600, marginBottom: 8, fontSize: 13 },
      modalActions: { display: 'flex', justifyContent: 'flex-end', gap: 8, marginTop: 14 },
      createRow: { display: 'flex', gap: 6, padding: '0 14px 8px 18px', flex: 'none' },
      dirList: { maxHeight: 220, overflow: 'auto', border: '1px solid var(--dsw-alias-border-l1)', borderRadius: 8, padding: 4 },
      iconRow: { flex: 'none', width: 18, textAlign: 'center', display: 'inline-flex', justifyContent: 'center', color: 'var(--dsw-alias-label-secondary)' },
      iconRowFolder: { flex: 'none', width: 18, textAlign: 'center', display: 'inline-flex', justifyContent: 'center', color: 'var(--dsw-alias-brand-primary)' },
      iconRowAccent: { flex: 'none', width: 18, textAlign: 'center', display: 'inline-flex', justifyContent: 'center', color: 'var(--dsw-alias-brand-primary)' },
      dragOver: { outline: '2px dashed var(--dsw-alias-brand-primary)', outlineOffset: -4, borderRadius: 8, background: 'color-mix(in srgb, var(--dsw-alias-brand-primary) 6%, transparent)' },
      tabActive: { color: 'var(--dsw-alias-brand-primary)', borderColor: 'var(--dsw-alias-brand-primary)', width: 26 },
    }

    // ---- Panel component ----
    function PanelHost(props) {
      const useSessions = typeof props.useSessions === 'function' ? props.useSessions : null
      const useWorkspaces = typeof props.useWorkspaces === 'function' ? props.useWorkspaces : null
      const current = useSessions ? useSessions((s) => s.current) : undefined
      const byId = useSessions ? useSessions((s) => s.byId) : undefined
      const wss = useWorkspaces ? useWorkspaces((s) => s.items) : []
      const recentId = useWorkspaces ? useWorkspaces((s) => s.recentWorkspaceId) : undefined

      const session = current ? byId[current] : undefined
      const root = (session && session.cwd) ? session.cwd : ((wss.find((w) => w.workspaceId === recentId) || {}).path)

      const [open, setOpen] = React.useState(false)
      const [width, setWidth] = React.useState(560)
      const [path, setPath] = React.useState(null)
      const [entries, setEntries] = React.useState([])
      const [loading, setLoading] = React.useState(false)
      const [error, setError] = React.useState(null)
      const [filter, setFilter] = React.useState('')
      const [globalSearch, setGlobalSearch] = React.useState(false)
      const [searchResults, setSearchResults] = React.useState(null)
      const [searching, setSearching] = React.useState(false)
      const [searchTimer, setSearchTimer] = React.useState(null)
      const [selected, setSelected] = React.useState([])
      const [dragOver, setDragOver] = React.useState(false)
      const [moveDlg, setMoveDlg] = React.useState(null)
      const [preview, setPreview] = React.useState(null)
      const [editing, setEditing] = React.useState(false)
      const [draft, setDraft] = React.useState('')
      const [creating, setCreating] = React.useState(null)
      const [createName, setCreateName] = React.useState('')
      const [renaming, setRenaming] = React.useState(null)
      const [renameDraft, setRenameDraft] = React.useState('')
      const [confirm, setConfirm] = React.useState(null)
      const [status, setStatus] = React.useState(null)
      const [token, setToken] = React.useState('')
      const [upload, setUpload] = React.useState(null)
      const [gripHover, setGripHover] = React.useState(false)
      const [tabHover, setTabHover] = React.useState(false)
      const [openTimer, setOpenTimer] = React.useState(null)
      const [closeTimer, setCloseTimer] = React.useState(null)
      const cancelRef = React.useRef(false)

      function flash(kind, text) { setStatus({ kind, text }) }
      React.useEffect(() => {
        if (!status) return
        return ctx.timeout(() => setStatus(null), 3200)
      }, [status])

      async function ensureToken() {
        if (token) return token
        const r = await call('panel.token', {})
        setToken(r.token)
        return r.token
      }

      function fileUrl(entry, inline, tk) {
        const t = tk || token
        return '/__dsh__/filepanel/download?t=' + encodeURIComponent(t) +
          '&root=' + encodeURIComponent(root) +
          '&path=' + encodeURIComponent(entry.path) +
          '&name=' + encodeURIComponent(entry.name) +
          (inline ? '&inline=1' : '')
      }

      function scheduleOpen() {
        if (open || openTimer) return
        setOpenTimer(ctx.timeout(() => { setOpen(true); setOpenTimer(null) }, 300))
      }
      function cancelOpen() {
        if (openTimer) { openTimer(); setOpenTimer(null) }
      }
      function scheduleClose() {
        if (!open || closeTimer) return
        setCloseTimer(ctx.timeout(() => { setOpen(false); setCloseTimer(null) }, 500))
      }
      function cancelClose() {
        if (closeTimer) { closeTimer(); setCloseTimer(null) }
      }
      function openNow() {
        cancelOpen(); setOpen(true)
      }
      function closeNow() {
        cancelClose(); setOpen(false)
      }

      async function load(dir) {
        if (!root) return
        setLoading(true); setError(null)
        try {
          const res = await call('panel.list', { root, path: dir || root })
          setPath(res.path); setEntries(res.entries)
        } catch (e) { setError(e.message || String(e)) } finally { setLoading(false) }
      }

      React.useEffect(() => {
        if (open && root) {
          load(root)
          if (!token) {
            ensureToken().catch(() => {})
          }
        }
      }, [open, root])

      function enterDir(dir) { load(dir) }
      function parentOf(p) { const i = p.lastIndexOf('/'); return i <= 0 ? p : p.slice(0, i) }
      function rootName() { return String(root || '').split('/').filter(Boolean).pop() || root || '' }
      function crumbs() {
        if (!path || !root) return [{ label: rootName(), dir: root }]
        const rel = path === root ? [] : String(path).slice(String(root).length).split('/').filter(Boolean)
        const out = [{ label: rootName(), dir: root }]
        let acc = root
        for (const seg of rel) { acc = acc + '/' + seg; out.push({ label: seg, dir: acc }) }
        return out
      }

      function startResize(e) {
        e.stopPropagation(); e.preventDefault()
        const startX = e.clientX
        const startWidth = width
        const onMove = (ev) => {
          const maxW = Math.round(window.innerWidth * 0.9)
          setWidth(Math.min(Math.max(startWidth + (startX - ev.clientX), 320), maxW))
        }
        const onUp = () => {
          window.removeEventListener('mousemove', onMove)
          window.removeEventListener('mouseup', onUp)
        }
        window.addEventListener('mousemove', onMove)
        window.addEventListener('mouseup', onUp)
      }

      function goUp() {
        if (!path) return
        if (path === root) { flash('err', 'Already at workspace root'); return }
        load(parentOf(path))
      }

      function toggleSelect(p) {
        setSelected((prev) => prev.includes(p) ? prev.filter((x) => x !== p) : [...prev, p])
      }

      function exitGlobal() {
        if (searchTimer) { searchTimer(); setSearchTimer(null) }
        setGlobalSearch(false); setSearchResults(null); setSearching(false)
      }

      async function doGlobalSearch(q) {
        if (!q || !q.trim()) { setSearchResults(null); return }
        setSearching(true)
        try {
          const res = await call('panel.search', { root, query: q.trim(), maxResults: 200 })
          setSearchResults(res.results)
        } catch (e) { flash('err', (e && e.message) || 'Search failed') } finally { setSearching(false) }
      }

      function onSearchInput(v) {
        setFilter(v)
        if (!globalSearch) return
        if (searchTimer) { searchTimer(); setSearchTimer(null) }
        if (!v.trim()) { setSearchResults(null); return }
        const t = ctx.timeout(() => { doGlobalSearch(v); setSearchTimer(null) }, 300)
        setSearchTimer(t)
      }

      async function loadMoveDirs(dir) {
        if (!root) return
        try {
          const res = await call('panel.list', { root, path: dir })
          setMoveDlg((d) => d ? { ...d, dir: res.path, dirs: res.entries.filter((x) => x.type === 'directory') } : d)
        } catch (e) { flash('err', (e && e.message) || 'Load failed') }
      }
      function openMoveDlg(mode, paths) {
        setMoveDlg({ mode, paths, dir: path || root, dirs: [] })
        loadMoveDirs(path || root)
      }
      async function doMoveCopy() {
        if (!moveDlg) return
        try {
          if (moveDlg.mode === 'move') await call('panel.move', { root, paths: moveDlg.paths, dest: moveDlg.dir })
          else await call('panel.copy', { root, paths: moveDlg.paths, dest: moveDlg.dir })
          const n = moveDlg.paths.length
          setMoveDlg(null); setSelected([])
          flash('ok', (moveDlg.mode === 'move' ? 'Moved ' : 'Copied ') + n + ' items')
          load(path)
        } catch (e) { flash('err', (e && e.message) || 'Operation failed') }
      }

      async function batchDownload() {
        for (const p of selected) {
          const en = entries.find((x) => x.path === p)
          if (en && en.type === 'file') await download(en)
        }
      }
      async function doUnzip(entry) {
        try {
          await call('panel.unzip', { root, zipPath: entry.path, destDir: path })
          flash('ok', 'Extracted here')
          load(path)
        } catch (e) { flash('err', (e && e.message) || 'Extraction failed') }
      }

      async function openPreview(entry) {
        setEditing(false); setDraft('')
        const name = entry.name
        if (isImageName(name) || isPdfName(name)) {
          try {
            const tk = await ensureToken()
            const url = fileUrl(entry, true, tk)
            setPreview({ kind: isPdfName(name) ? 'pdf' : 'image', path: entry.path, name, url, content: null, tooLarge: false, size: entry.size, loading: false, error: null })
          } catch (e) {
            setPreview({ kind: 'text', path: entry.path, name, url: null, content: null, tooLarge: false, size: entry.size, loading: false, error: (e && e.message) || String(e) })
          }
          return
        }
        setPreview({ kind: 'text', path: entry.path, name, url: null, content: null, tooLarge: false, size: entry.size, loading: true, error: null })
        try {
          const res = await call('panel.readText', { root, path: entry.path })
          if (res.tooLarge) setPreview({ kind: 'text', path: entry.path, name, url: null, content: null, tooLarge: true, size: res.size, loading: false, error: null })
          else setPreview({ kind: 'text', path: entry.path, name, url: null, content: res.content, tooLarge: false, size: res.size, loading: false, error: null })
        } catch (e) {
          setPreview({ kind: 'text', path: entry.path, name, url: null, content: null, tooLarge: false, size: entry.size, loading: false, error: e.message || String(e) })
        }
      }

      async function saveDraft() {
        if (!preview) return
        try {
          await call('panel.writeText', { root, path: preview.path, content: draft })
          flash('ok', 'Saved ' + preview.name)
          setEditing(false)
          setPreview({ ...preview, content: draft })
          load(path)
        } catch (e) { flash('err', e.message || String(e)) }
      }

      async function createEntry() {
        const name = createName.trim()
        if (!name || !path) return
        try {
          if (creating === 'file') await call('panel.writeText', { root, path: path + '/' + name, content: '' })
          else if (creating === 'dir') await call('panel.createDir', { root, path: path + '/' + name })
          else if (creating === 'zip') await call('panel.zip', { root, paths: selected.length ? selected : [path], zipName: name })
          setCreating(null); setCreateName(''); flash('ok', 'Created ' + name); load(path)
        } catch (e) { flash('err', e.message || String(e)) }
      }

      async function commitRename() {
        const name = renameDraft.trim()
        if (!name || !renaming) return
        try {
          await call('panel.rename', { root, path: renaming.path, newName: name })
          setRenaming(null); flash('ok', 'Renamed'); load(path)
        } catch (e) { flash('err', e.message || String(e)) }
      }

      async function doDelete() {
        if (!confirm) return
        try {
          if (confirm.batch) {
            for (const p of selected) await call('panel.remove', { root, path: p })
            const n = selected.length
            setSelected([])
            flash('ok', 'Deleted ' + n + ' items')
          } else {
            await call('panel.remove', { root, path: confirm.path })
            flash('ok', 'Deleted ' + confirm.name)
          }
          setConfirm(null); load(path)
        } catch (e) { flash('err', e.message || String(e)); setConfirm(null) }
      }

      async function download(entry) {
        try {
          const tk = await ensureToken()
          const url = fileUrl(entry, false, tk)
          const a = document.createElement('a')
          a.href = url; a.download = entry.name
          document.body.appendChild(a); a.click(); a.remove()
        } catch (e) { flash('err', (e && e.message) || 'Download failed') }
      }

      async function openInBrowser(entry) {
        try {
          const tk = await ensureToken()
          const url = fileUrl(entry, true, tk)
          const a = document.createElement('a')
          a.href = url; a.target = '_blank'; a.rel = 'noopener'
          document.body.appendChild(a); a.click(); a.remove()
        } catch (e) { flash('err', (e && e.message) || 'Open failed') }
      }

      function copyPath(entry) {
        if (navigator && navigator.clipboard) {
          navigator.clipboard.writeText(entry.path).then(() => flash('ok', 'Path copied')).catch(() => flash('err', 'Copy failed'))
        } else flash('err', 'Clipboard unavailable')
      }

      function startUploads(files) {
        if (!files.length || !path) return
        cancelRef.current = false
        let failed = 0
        const next = (i) => {
          if (cancelRef.current) {
            setUpload(null)
            flash('ok', 'Upload cancelled')
            return
          }
          if (i >= files.length) {
            setUpload(null)
            flash(failed ? 'err' : 'ok', 'Upload finished: ' + (files.length - failed) + ' ok, ' + failed + ' failed')
            load(path)
            return
          }
          const f = files[i]
          const full = path + '/' + f.name
          const doNext = () => next(i + 1)
          if (f.size > 2 * 1024 * 1024 * 1024) { failed++; flash('err', f.name + ' exceeds 2GB, skipped'); return doNext() }
          uploadFile(root, full, f, (sent, total, startedAt) => {
            const secs = (Date.now() - startedAt) / 1000
            setUpload({ name: f.name, sent, total, speed: speedText(secs > 0 ? sent / secs : 0) })
          }, () => cancelRef.current).then(() => {
            setUpload(null)
            doNext()
          }, (err) => {
            setUpload(null)
            if (err && err.code === 'CANCELLED') {
              flash('ok', 'Upload cancelled')
              return
            }
            failed++
            flash('err', f.name + ' failed: ' + ((err && err.message) || err))
            doNext()
          })
        }
        next(0)
      }
      function onFiles(e) {
        const files = Array.from(e.target.files || [])
        e.target.value = ''
        startUploads(files)
      }

      // hooks already called
      if (!open) {
        return [
          h('div', { key: 'hz', style: S.hotzone, onMouseEnter: scheduleOpen, onMouseLeave: cancelOpen }),
          h('button', {
            key: 'tab',
            style: tabHover ? { ...S.tab, ...S.tabActive } : S.tab,
            title: 'File panel: hover the far right edge, or click here to open',
            onMouseEnter: () => { setTabHover(true); scheduleOpen() },
            onMouseLeave: () => { setTabHover(false); cancelOpen() },
            onClick: openNow,
          }, h(Icon, { name: 'folder', size: 14 })),
        ]
      }

      const q = filter.trim().toLowerCase()
      const visible = q ? entries.filter((e) => e.name.toLowerCase().includes(q)) : entries
      const sorted = visible.slice().sort((a, b) => {
        if (a.type !== b.type) return a.type === 'directory' ? -1 : 1
        return a.name.localeCompare(b.name)
      })

      const rows = sorted.map((entry) => {
        const iconName = entryIconName(entry)
        const iconStyle = entry.type === 'directory' ? S.iconRowFolder : (iconName === 'file' ? S.iconRow : S.iconRowAccent)
        const isSel = selected.includes(entry.path)
        return h('div', {
          key: entry.path,
          className: 'fp-row' + (isSel ? ' selected' : ''),
          onClick: () => entry.type === 'directory' ? enterDir(entry.path) : openPreview(entry),
        },
          h('input', { type: 'checkbox', className: 'fp-checkbox', checked: isSel, onChange: (e) => { e.stopPropagation(); toggleSelect(entry.path) }, onClick: (e) => e.stopPropagation() }),
          h('span', { style: iconStyle }, h(Icon, { name: iconName, size: 15 })),
          (renaming && renaming.path === entry.path)
            ? h('input', { className: 'fp-input', style: { flex: 1, minWidth: 0 }, autoFocus: true, value: renameDraft, onChange: (e) => setRenameDraft(e.target.value), onClick: (e) => e.stopPropagation(), onKeyDown: (e) => { if (e.key === 'Enter') commitRename(); if (e.key === 'Escape') setRenaming(null) } })
            : h('span', { style: S.name, title: entry.name }, entry.name),
          h('span', { style: S.size }, entry.type === 'file' ? formatSize(entry.size) : ''),
          entry.type === 'file' ? h('button', { className: 'fp-act', title: 'Download', style: { flex: 'none', color: 'var(--dsw-alias-label-primary)', fontWeight: 500 }, onClick: (e) => { e.stopPropagation(); download(entry) } }, h(Icon, { name: 'download', size: 13 })) : null,
          h('span', { className: 'fp-row-actions', onClick: (e) => e.stopPropagation() },
            h('button', { className: 'fp-act', title: 'Open in browser', onClick: () => openInBrowser(entry) }, h(Icon, { name: 'external', size: 12 })),
            entry.type === 'file' && extOf(entry.name) === 'zip' ? h('button', { className: 'fp-act', title: 'Extract here', onClick: () => doUnzip(entry) }, h(Icon, { name: 'unzip', size: 12 })) : null,
            h('button', { className: 'fp-act', title: 'Rename', onClick: () => { setRenaming({ path: entry.path, name: entry.name }); setRenameDraft(entry.name) } }, h(Icon, { name: 'rename', size: 12 })),
            h('button', { className: 'fp-act', title: 'Copy path', onClick: () => copyPath(entry) }, h(Icon, { name: 'copy', size: 12 })),
            h('button', { className: 'fp-act danger', title: 'Delete', onClick: () => setConfirm({ path: entry.path, name: entry.name }) }, h(Icon, { name: 'trash', size: 12 })),
          ),
        )
      })

      const uploadPct = upload && upload.total ? Math.round(upload.sent / upload.total * 100) : 0
      const crumbList = crumbs()

      const resultRows = (searchResults || []).map((r) => {
        const parent = String(r.path).slice(0, String(r.path).lastIndexOf('/'))
        const iconName = r.type === 'directory' ? 'folder' : (isImageName(r.name) ? 'image' : (isPdfName(r.name) ? 'pdf' : (extOf(r.name) === 'zip' ? 'archive' : 'file')))
        return h('div', {
          key: r.path,
          className: 'fp-row',
          onClick: () => {
            exitGlobal()
            if (r.type === 'directory') { load(r.path) }
            else { load(parent); openPreview(r) }
          },
        },
          h('span', { style: S.iconRow }, h(Icon, { name: iconName, size: 15 })),
          h('span', { style: S.name, title: r.path }, r.name),
          h('span', { style: { ...S.size, maxWidth: 180, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } }, r.type === 'file' ? (formatSize(r.size) + ' · ' + (parent || root)) : (parent || root)),
        )
      })

      let previewBody = null
      if (preview) {
        if (preview.kind === 'image') {
          previewBody = h('div', { style: { flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', overflow: 'auto', padding: 12 } },
            h('img', { src: preview.url, alt: preview.name, style: { maxWidth: '100%', maxHeight: '100%', objectFit: 'contain', borderRadius: 4 }, onError: () => flash('err', 'Image failed to load') }),
          )
        } else if (preview.kind === 'pdf') {
          previewBody = h('div', { style: { flex: 1, overflow: 'hidden' } },
            h('iframe', { src: preview.url, title: preview.name, style: { width: '100%', height: '100%', border: 'none', background: '#fff' } }),
          )
        } else if (preview.loading) {
          previewBody = h('div', { style: S.empty }, 'Loading…')
        } else if (preview.tooLarge) {
          previewBody = h('div', { style: S.empty }, 'File too large (' + formatSize(preview.size) + '), preview skipped, tap Download to view')
        } else if (preview.error) {
          previewBody = h('div', { style: S.empty }, preview.error)
        } else if (editing) {
          previewBody = h('textarea', {
            style: S.textarea,
            value: draft,
            onChange: (e) => setDraft(e.target.value),
            onKeyDown: (e) => { if ((e.ctrlKey || e.metaKey) && (e.key === 's' || e.key === 'S')) { e.preventDefault(); saveDraft() } },
          })
        } else {
          const content = preview.content || ''
          const lines = content.split('\n')
          const lang = LANG_MAP[extOf(preview.name)] || ''
          if (lang && lines.length <= MAX_HL_LINES) {
            previewBody = h('div', { style: S.previewBody },
              lines.map((line, idx) => h('div', { key: idx, style: S.codeLine },
                h('span', { style: S.lineNo }, String(idx + 1)),
                h('span', { style: S.lineCode }, tokenizeLine(line).map((tk, j) => h('span', { key: j, style: TOKEN_STYLE[tk.type] }, tk.t))),
              )),
            )
          } else {
            previewBody = h('pre', { style: { ...S.previewBody, padding: '8px 12px' } }, content)
          }
        }
      }

      return h('div', { style: S.overlay, onMouseEnter: cancelClose, onMouseLeave: scheduleClose },
        h('div', { style: S.catcher, onClick: closeNow }),
        h('div', { style: { ...S.drawer, width } },
          h('div', {
            style: S.resizeHandle,
            title: 'Drag to resize',
            onMouseDown: startResize,
            onMouseEnter: () => setGripHover(true),
            onMouseLeave: () => setGripHover(false),
          }, h('div', { style: gripHover ? { ...S.resizeGrip, background: 'var(--dsw-alias-brand-primary)' } : S.resizeGrip })),
          h('div', { style: S.header },
            h('span', { style: S.headerTitle }, h(Icon, { name: 'folder', size: 15 }), 'Files'),
            h('div', { style: S.breadcrumb },
              crumbList.map((c, i, arr) => {
                const isLast = i === arr.length - 1
                return h('span', { key: 'crumb' + i, style: { display: 'inline-flex', alignItems: 'center', gap: 2 } },
                  i > 0 ? h('span', { style: S.crumbSep }, h(Icon, { name: 'chevron', size: 11 })) : null,
                  h('span', { className: 'fp-crumb' + (isLast ? ' current' : ''), onClick: () => { if (!isLast) load(c.dir) } }, c.label),
                )
              }),
            ),
            h(Btn, { iconOnly: true, title: 'Close', onClick: closeNow }, h(Icon, { name: 'close', size: 15 })),
          ),
          h('div', { style: S.toolbar },
            h(Btn, { iconOnly: true, title: 'Go up', onClick: goUp }, h(Icon, { name: 'up', size: 14 })),
            h(Btn, { iconOnly: true, title: 'Refresh', onClick: () => load(path) }, h(Icon, { name: 'refresh', size: 14 })),
            h(Btn, { title: 'New file', onClick: () => { setCreating('file'); setCreateName('') } }, h(Icon, { name: 'plusFile', size: 13 }), 'New file'),
            h(Btn, { title: 'New folder', onClick: () => { setCreating('dir'); setCreateName('') } }, h(Icon, { name: 'plusFolder', size: 13 }), 'New folder'),
            h('label', { className: 'fp-btn', title: 'Upload files', style: { cursor: 'pointer' } },
              h(Icon, { name: 'upload', size: 13 }),
              'Upload',
              h('input', { type: 'file', multiple: true, style: { display: 'none' }, onChange: onFiles }),
            ),
            h('input', { className: 'fp-input', style: { flex: 1, minWidth: 110 }, placeholder: globalSearch ? 'Search entire workspace (name / content)…' : 'Search current folder…', value: filter, onChange: (e) => onSearchInput(e.target.value) }),
            h(Btn, { on: globalSearch, iconOnly: true, title: 'Global search', onClick: () => { if (globalSearch) exitGlobal(); else { setGlobalSearch(true); setFilter(''); setSearchResults(null) } } }, h(Icon, { name: 'search', size: 14 })),
          ),
          selected.length > 0 ? h('div', { style: S.batchBar },
            h('span', { style: S.batchCount }, 'Selected ' + selected.length + ' items'),
            h(Btn, { title: 'Download selected files', onClick: batchDownload }, h(Icon, { name: 'download', size: 13 }), 'Download'),
            h(Btn, { title: 'Pack as zip', onClick: () => { setCreating('zip'); setCreateName('archive.zip') } }, h(Icon, { name: 'archive', size: 13 }), 'Zip'),
            h(Btn, { title: 'Move to…', onClick: () => openMoveDlg('move', selected) }, h(Icon, { name: 'move', size: 13 }), 'Move'),
            h(Btn, { title: 'Copy to…', onClick: () => openMoveDlg('copy', selected) }, h(Icon, { name: 'copy', size: 13 }), 'Copy'),
            h(Btn, { danger: true, title: 'Delete selected', onClick: () => setConfirm({ batch: true, count: selected.length }) }, h(Icon, { name: 'trash', size: 13 }), 'Delete'),
            h(Btn, { title: 'Clear selection', onClick: () => setSelected([]) }, 'Clear'),
          ) : null,
          creating ? h('div', { style: S.createRow },
            h('input', { className: 'fp-input', style: { flex: 1, minWidth: 0 }, placeholder: creating === 'file' ? 'File name, e.g. notes.md' : (creating === 'dir' ? 'Folder name' : 'Archive name, e.g. archive.zip'), value: createName, autoFocus: true, onChange: (e) => setCreateName(e.target.value), onKeyDown: (e) => { if (e.key === 'Enter') createEntry(); if (e.key === 'Escape') { setCreating(null); setCreateName('') } } }),
            h(Btn, { kind: 'primary', onClick: createEntry }, h(Icon, { name: 'check', size: 13 }), 'Create'),
            h(Btn, { onClick: () => { setCreating(null); setCreateName('') } }, 'Cancel'),
          ) : null,
          h('div', {
            className: 'fp-scroll',
            style: { ...S.list, ...(dragOver ? S.dragOver : {}) },
            onDragOver: (e) => { e.preventDefault(); if (!dragOver) setDragOver(true) },
            onDragLeave: () => setDragOver(false),
            onDrop: (e) => { e.preventDefault(); setDragOver(false); const files = Array.from((e.dataTransfer && e.dataTransfer.files) || []); if (files.length) startUploads(files) },
          },
            globalSearch
              ? (searching ? h('div', { style: S.empty }, 'Searching…')
                : (searchResults === null ? h('div', { style: S.empty }, 'Type a keyword to search the whole workspace (including file contents)') : (searchResults.length ? resultRows : h('div', { style: S.empty }, 'No matching results'))))
              : (error ? h('div', { style: S.empty }, error) :
                rows.length ? rows : h('div', { style: S.empty }, q ? 'No matching files' : (dragOver ? 'Drop to upload here' : 'Empty folder (drop files here to upload)'))),
          ),
          preview ? h('div', { style: S.preview },
            h('div', { style: S.previewHead },
              h('span', { style: { ...S.headerTitle, fontSize: 12, flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } }, h(Icon, { name: entryIconName({ name: preview.name, type: 'file' }), size: 13 }), preview.name),
              h('span', { style: S.size }, preview.size ? formatSize(preview.size) : ''),
              preview.kind === 'text' && !preview.error ? (editing
                ? [
                    h(Btn, { key: 'save', kind: 'primary', onClick: saveDraft }, h(Icon, { name: 'save', size: 13 }), 'Save'),
                    h(Btn, { key: 'cancel', onClick: () => { setEditing(false); setDraft('') } }, 'Cancel edit'),
                  ]
                : h(Btn, { key: 'edit', onClick: () => { setEditing(true); setDraft(preview.content || '') }, iconOnly: true, title: 'Edit (Ctrl+S to save)' }, h(Icon, { name: 'edit', size: 13 }))) : null,
              h(Btn, { onClick: () => download(preview), iconOnly: true, title: 'Download' }, h(Icon, { name: 'download', size: 13 })),
              h(Btn, { iconOnly: true, title: 'Close preview', onClick: () => { setPreview(null); setEditing(false) } }, h(Icon, { name: 'close', size: 13 })),
            ),
            previewBody,
          ) : null,
          upload ? h('div', { style: S.progressWrap },
            h('div', { style: { display: 'flex', alignItems: 'center', gap: 8, marginBottom: 5 } },
              h('div', { style: { ...S.progressText, marginBottom: 0, flex: 1, minWidth: 0, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' } }, 'Uploading ' + upload.name + ' — ' + uploadPct + '% · ' + upload.speed + ' · ' + formatSize(upload.sent) + ' / ' + formatSize(upload.total)),
              h(Btn, { iconOnly: true, title: 'Cancel upload', onClick: () => { cancelRef.current = true; setUpload(null); flash('ok', 'Upload cancelled') } }, h(Icon, { name: 'close', size: 13 })),
            ),
            h('div', { style: S.progressTrack },
              h('div', { style: { ...S.progressBar, width: uploadPct + '%' } }),
            ),
          ) : null,
          h('div', { style: { ...S.status, ...(status ? (status.kind === 'ok' ? S.statusOk : S.statusErr) : {}) } }, status ? status.text : (globalSearch ? 'Global search mode' : (path ? path : (root ? '' : 'No workspace selected'))) ),
          confirm ? h('div', { style: S.modal },
            h('div', { style: S.modalBox },
              h('div', { style: S.modalTitle }, confirm.batch ? 'Delete ' + confirm.count + ' items?' : 'Confirm delete'),
              h('div', null, confirm.batch ? 'The ' + confirm.count + ' selected items will be permanently deleted. This cannot be undone.' : 'Delete ', confirm.batch ? null : h('strong', null, confirm.name), confirm.batch ? null : '? This cannot be undone.'),
              h('div', { style: S.modalActions },
                h(Btn, { onClick: () => setConfirm(null) }, 'Cancel'),
                h(Btn, { kind: 'primary', danger: true, onClick: doDelete }, h(Icon, { name: 'trash', size: 13 }), 'Delete'),
              ),
            ),
          ) : null,
          moveDlg ? h('div', { style: S.modal },
            h('div', { style: { ...S.modalBox, width: 380 } },
              h('div', { style: S.modalTitle }, (moveDlg.mode === 'move' ? 'Move to' : 'Copy to') + ' (' + moveDlg.paths.length + ' items)'),
              h('div', { style: { display: 'flex', alignItems: 'center', gap: 6, marginBottom: 8 } },
                h(Btn, { iconOnly: true, title: 'Go up', onClick: () => { if (moveDlg.dir !== root) loadMoveDirs(parentOf(moveDlg.dir)) } }, h(Icon, { name: 'up', size: 13 })),
                h('span', { style: { fontSize: 12, color: 'var(--dsw-alias-label-secondary)', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 } }, moveDlg.dir),
              ),
              h('div', { className: 'fp-scroll', style: S.dirList },
                moveDlg.dirs.length
                  ? moveDlg.dirs.map((d) => h('div', { key: d.path, className: 'fp-dir', onClick: () => loadMoveDirs(d.path) }, h(Icon, { name: 'folder', size: 14, style: { color: 'var(--dsw-alias-brand-primary)' } }), h('span', { style: S.name }, d.name)))
                  : h('div', { style: S.empty }, 'No subfolders'),
              ),
              h('div', { style: S.modalActions },
                h(Btn, { onClick: () => setMoveDlg(null) }, 'Cancel'),
                h(Btn, { kind: 'primary', onClick: doMoveCopy }, moveDlg.mode === 'move' ? 'Move here' : 'Copy here'),
              ),
            ),
          ) : null,
        ),
      )
    }

    ctx.slots.inject('shell.overlay', () => ctx.slots.register(
      { name: 'shell.overlay', id: 'filepanel-overlay' },
      (props) => h(PanelHost, props),
    ))

    console.log('[filepanel] client ready')
    }

    exports.apply = apply;
    exports.inject = inject;
    return module.exports;
  },
});
