/**
 * dsh-filepannel-plugin — Host half
 *
 * A dynamic Cordis plugin for DeepSeek Harness (DSH). Registers Package-private
 * RPC handlers (file listing, text read/write, mkdir, remove, rename, streamed
 * upload, search, move, copy, zip/unzip) plus a same-origin download route on
 * the web server. All paths are validated to stay inside the session workspace.
 */
export const name = 'dsh-filepanel-local'
export const inject = ['fs', 'shell', 'webServer', 'sandboxPolicy']

function apply(ctx) {
    const MAX_TEXT_PREVIEW = 512 * 1024
    const MAX_DOWNLOAD = 256 * 1024 * 1024

    const MIME = {
      pdf: 'application/pdf',
      png: 'image/png', jpg: 'image/jpeg', jpeg: 'image/jpeg', gif: 'image/gif', webp: 'image/webp', svg: 'image/svg+xml', bmp: 'image/bmp', ico: 'image/x-icon', avif: 'image/avif',
      txt: 'text/plain', md: 'text/markdown', markdown: 'text/markdown', json: 'application/json', js: 'text/javascript', mjs: 'text/javascript', cjs: 'text/javascript', html: 'text/html', htm: 'text/html', css: 'text/css', xml: 'application/xml', csv: 'text/csv', log: 'text/plain', yml: 'text/plain', yaml: 'text/plain',
    }

    const fail = (e) => ({ ok: false, error: (e && e.message) ? e.message : String(e) })
    const ok = (data) => ({ ok: true, ...data })

    function shq(s) {
      return "'" + String(s).replace(/'/g, "'\\''") + "'"
    }

    function parseQuery(raw) {
      const out = {}
      const q = String(raw || '').split('?')[1] || ''
      for (const pair of q.split('&')) {
        if (!pair) continue
        const i = pair.indexOf('=')
        if (i < 0) { out[decodeURIComponent(pair)] = ''; continue }
        out[decodeURIComponent(pair.slice(0, i))] = decodeURIComponent(pair.slice(i + 1))
      }
      return out
    }

    // Resolve a path and enforce it stays inside the workspace root.
    async function resolveWithin(root, path) {
      const rootTarget = await ctx.fs.resolve(String(root || ''))
      const info = await ctx.fs.stat(rootTarget)
      if (!info || info.type !== 'directory') throw new Error('Invalid workspace root: ' + root)
      const target = await ctx.fs.resolve(String(path || ''))
      if (!ctx.fs.contains(rootTarget, target)) throw new Error('Path is outside the workspace')
      return { rootTarget, target }
    }

    function policyFor(root) {
      // No sandbox backend is available on Termux: danger-full-access must be explicit,
      // otherwise the shell service refuses to run with "no sandbox backend is usable".
      return { mode: 'danger-full-access', workspaceRoot: String(root) }
    }

    async function shellRun(root, command, stdin) {
      const spec = ctx.shell.resolve({
        command,
        workdir: String(root),
        sandboxPolicy: policyFor(root),
        stdoutMaxBytes: 16 * 1024,
        timeoutMs: 60000,
        ...(stdin === undefined ? {} : { stdin }),
      })
      const result = await ctx.shell.run(spec)
      if (result.exitCode !== 0) {
        const stderr = (result.stderr && result.stderr.text) || ''
        const stdout = (result.stdout && result.stdout.text) || ''
        throw new Error((stderr || stdout).trim().slice(0, 400) || 'Command failed')
      }
      return result
    }

    function parentOf(p) {
      const i = String(p).lastIndexOf('/')
      return i <= 0 ? String(p) : String(p).slice(0, i)
    }

    function toRel(root, p) {
      const s = String(p)
      const r = String(root).replace(/\/+$/, '')
      return s.startsWith(r) ? s.slice(r.length).replace(/^\//, '') : s
    }

    // One-time token for the download route (keeps other local pages from guessing URLs).
    const token = 'fp' + Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2)

    // Streaming upload sessions: key = root|path → { root, path, tmpPath }
    const uploads = new Map()

    const handlers = {
      'panel.token': async () => ok({ token }),

      'panel.list': async (args) => {
        try {
          const root = String(args.root || '')
          const path = String(args.path || root)
          const { target } = await resolveWithin(root, path)
          const info = await ctx.fs.stat(target)
          if (!info || info.type !== 'directory') throw new Error('Not a directory')
          const entries = await ctx.fs.listDir(target)
          return ok({
            root,
            path,
            entries: entries.map((e) => ({
              name: e.name,
              type: e.type,
              size: e.size || 0,
              path: path.replace(/\/+$/, '') + '/' + e.name,
            })),
          })
        } catch (e) { return fail(e) }
      },

      // Recursive workspace search: file/dir names plus text content of small files.
      'panel.search': async (args) => {
        try {
          const root = String(args.root || '')
          const query = String(args.query || '').toLowerCase()
          const maxResults = Math.min(Number(args.maxResults) || 200, 500)
          if (!query) return ok({ results: [] })
          const rootTarget = await ctx.fs.resolve(root)
          const results = []
          let visited = 0
          const MAX_VISITED = 4000
          const MAX_DEPTH = 8
          const walk = async (dir, depth) => {
            if (depth > MAX_DEPTH || visited > MAX_VISITED || results.length >= maxResults) return
            visited++
            let target
            try { target = await ctx.fs.resolve(dir) } catch { return }
            if (!ctx.fs.contains(rootTarget, target)) return
            let entries = []
            try { entries = await ctx.fs.listDir(target) } catch { return }
            for (const e of entries) {
              if (results.length >= maxResults) return
              const full = String(dir).replace(/\/+$/, '') + '/' + e.name
              const nameL = e.name.toLowerCase()
              if (nameL.includes(query)) {
                results.push({ name: e.name, path: full, type: e.type, size: e.size || 0 })
              } else if (e.type === 'file' && e.size && e.size <= 256 * 1024) {
                try {
                  const t2 = await ctx.fs.resolve(full)
                  if (!ctx.fs.contains(rootTarget, t2)) continue
                  const text = await ctx.fs.readText(t2)
                  if (text.toLowerCase().includes(query)) results.push({ name: e.name, path: full, type: e.type, size: e.size || 0 })
                } catch { /* binary or unreadable: skip */ }
              }
              if (e.type === 'directory') await walk(full, depth + 1)
            }
          }
          await walk(root, 0)
          return ok({ results })
        } catch (e) { return fail(e) }
      },

      'panel.move': async (args) => {
        try {
          const root = String(args.root || '')
          const dest = String(args.dest || '')
          const paths = Array.isArray(args.paths) ? args.paths.map(String) : []
          if (!paths.length) throw new Error('No items selected')
          const { target: destTarget } = await resolveWithin(root, dest)
          const destInfo = await ctx.fs.stat(destTarget)
          if (!destInfo || destInfo.type !== 'directory') throw new Error('Destination is not a directory')
          const destKey = destTarget.targetKey
          for (const p of paths) {
            const { target } = await resolveWithin(root, p)
            if (target.targetKey === destKey) throw new Error('Destination is the same as the source')
            const name = String(p).split('/').pop()
            const destPath = String(dest).replace(/\/+$/, '') + '/' + name
            const exists = await ctx.fs.stat(await ctx.fs.resolve(destPath)).catch(() => undefined)
            if (exists) throw new Error('An item with the same name already exists there: ' + name)
          }
          for (const p of paths) {
            await shellRun(root, 'mv -- ' + shq(p) + ' ' + shq(String(dest).replace(/\/+$/, '')))
          }
          return ok({})
        } catch (e) { return fail(e) }
      },

      'panel.copy': async (args) => {
        try {
          const root = String(args.root || '')
          const dest = String(args.dest || '')
          const paths = Array.isArray(args.paths) ? args.paths.map(String) : []
          if (!paths.length) throw new Error('No items selected')
          const { target: destTarget } = await resolveWithin(root, dest)
          const destInfo = await ctx.fs.stat(destTarget)
          if (!destInfo || destInfo.type !== 'directory') throw new Error('Destination is not a directory')
          const destKey = destTarget.targetKey
          for (const p of paths) {
            const { target } = await resolveWithin(root, p)
            if (target.targetKey === destKey) throw new Error('Destination is the same as the source')
            const name = String(p).split('/').pop()
            const destPath = String(dest).replace(/\/+$/, '') + '/' + name
            const exists = await ctx.fs.stat(await ctx.fs.resolve(destPath)).catch(() => undefined)
            if (exists) throw new Error('An item with the same name already exists there: ' + name)
          }
          for (const p of paths) {
            await shellRun(root, 'cp -r -- ' + shq(p) + ' ' + shq(String(dest).replace(/\/+$/, '')))
          }
          return ok({})
        } catch (e) { return fail(e) }
      },

      'panel.zip': async (args) => {
        try {
          const root = String(args.root || '')
          let zipName = String(args.zipName || '').trim()
          if (!zipName) throw new Error('Archive name is required')
          if (!/\.zip$/i.test(zipName)) zipName += '.zip'
          const paths = Array.isArray(args.paths) ? args.paths.map(String) : []
          if (!paths.length) throw new Error('No items selected to pack')
          for (const p of paths) await resolveWithin(root, p)
          const rels = paths.map((p) => toRel(root, p))
          const zipRel = toRel(root, String(root).replace(/\/+$/, '') + '/' + zipName)
          await shellRun(root, 'zip -r -q ' + shq(zipRel) + ' ' + rels.map(shq).join(' '))
          return ok({ zipName })
        } catch (e) { return fail(e) }
      },

      'panel.unzip': async (args) => {
        try {
          const root = String(args.root || '')
          const zipPath = String(args.zipPath || '')
          const destDir = String(args.destDir || root)
          const { target } = await resolveWithin(root, zipPath)
          const info = await ctx.fs.stat(target)
          if (!info || info.type !== 'file') throw new Error('Archive does not exist')
          await resolveWithin(root, destDir)
          await shellRun(root, 'unzip -o -q ' + shq(zipPath) + ' -d ' + shq(destDir))
          return ok({})
        } catch (e) { return fail(e) }
      },

      'panel.readText': async (args) => {
        try {
          const root = String(args.root || '')
          const path = String(args.path || '')
          const { target } = await resolveWithin(root, path)
          const info = await ctx.fs.stat(target)
          if (!info) throw new Error('File does not exist')
          if (info.type !== 'file') throw new Error('Not a file')
          if (info.size && info.size > MAX_TEXT_PREVIEW) return ok({ tooLarge: true, size: info.size })
          const content = await ctx.fs.readText(target)
          return ok({ tooLarge: false, content, size: info.size || content.length })
        } catch (e) { return fail(e) }
      },

      'panel.writeText': async (args) => {
        try {
          const root = String(args.root || '')
          const path = String(args.path || '')
          const content = String(args.content === undefined ? '' : args.content)
          const { target } = await resolveWithin(root, path)
          const outcome = await ctx.fs.writeText(target, content, undefined, undefined, policyFor(root))
          return ok({ operation: outcome.operation })
        } catch (e) { return fail(e) }
      },

      'panel.createDir': async (args) => {
        try {
          const root = String(args.root || '')
          const path = String(args.path || '')
          await resolveWithin(root, path)
          await shellRun(root, 'mkdir ' + shq(path))
          return ok({})
        } catch (e) { return fail(e) }
      },

      'panel.remove': async (args) => {
        try {
          const root = String(args.root || '')
          const path = String(args.path || '')
          if (String(root).replace(/\/+$/, '') === String(path).replace(/\/+$/, '')) throw new Error('Cannot delete the workspace root')
          const { target } = await resolveWithin(root, path)
          const info = await ctx.fs.stat(target)
          if (!info) throw new Error('Target does not exist')
          await shellRun(root, 'rm -rf -- ' + shq(path))
          return ok({})
        } catch (e) { return fail(e) }
      },

      'panel.rename': async (args) => {
        try {
          const root = String(args.root || '')
          const path = String(args.path || '')
          const newName = String(args.newName || '').trim()
          if (!newName || newName.indexOf('/') >= 0 || newName === '.' || newName === '..') throw new Error('Invalid file name')
          const parent = parentOf(path)
          const newPath = parent + '/' + newName
          const { target } = await resolveWithin(root, path)
          const info = await ctx.fs.stat(target)
          if (!info) throw new Error('Target does not exist')
          await resolveWithin(root, newPath)
          await shellRun(root, 'mv -- ' + shq(path) + ' ' + shq(newPath))
          return ok({})
        } catch (e) { return fail(e) }
      },

      'panel.uploadStart': async (args) => {
        try {
          const root = String(args.root || '')
          const path = String(args.path || '')
          await resolveWithin(root, path)
          const tmpPath = String(path).replace(/\/+$/, '') + '.fpup'
          await shellRun(root, 'rm -f -- ' + shq(tmpPath)).catch(() => {})
          uploads.set(root + '|' + path, { root, path, tmpPath })
          return ok({})
        } catch (e) { return fail(e) }
      },

      // Streamed upload: append each base64 chunk to a temp file, atomically rename on finish.
      'panel.uploadChunk': async (args) => {
        try {
          const root = String(args.root || '')
          const path = String(args.path || '')
          const base64 = String(args.base64 || '')
          const key = root + '|' + path
          const entry = uploads.get(key)
          if (!entry) throw new Error('Upload session not found, please retry')
          try {
            const spec = ctx.shell.resolve({
              command: 'base64 -d >> ' + shq(entry.tmpPath),
              workdir: root,
              stdin: base64,
              sandboxPolicy: policyFor(root),
              stdoutMaxBytes: 8192,
              timeoutMs: 60000,
            })
            let result = await ctx.shell.run(spec)
            if (result.exitCode !== 0) {
              const spec2 = ctx.shell.resolve({
                command: 'openssl base64 -d -A >> ' + shq(entry.tmpPath),
                workdir: root,
                stdin: base64,
                sandboxPolicy: policyFor(root),
                stdoutMaxBytes: 8192,
                timeoutMs: 60000,
              })
              result = await ctx.shell.run(spec2)
              if (result.exitCode !== 0) {
                throw new Error(((result.stderr && result.stderr.text) || '').trim().slice(0, 300) || 'Write failed')
              }
            }
            if (args.final) {
              await shellRun(root, 'mv -- ' + shq(entry.tmpPath) + ' ' + shq(path))
              uploads.delete(key)
            }
          } catch (e) {
            await shellRun(root, 'rm -f -- ' + shq(entry.tmpPath)).catch(() => {})
            uploads.delete(key)
            throw e
          }
          return ok({})
        } catch (e) { return fail(e) }
      },

      'panel.uploadAbort': async (args) => {
        try {
          const root = String(args.root || '')
          const path = String(args.path || '')
          const key = root + '|' + path
          const entry = uploads.get(key)
          if (entry) {
            await shellRun(root, 'rm -f -- ' + shq(entry.tmpPath)).catch(() => {})
            uploads.delete(key)
          }
          return ok({})
        } catch (e) { return fail(e) }
      },
    }

    // Bundle environments have no dynamic-sandbox harness: expose the same RPC surface through a webServer route,
    // the client calls it with fetch (host.call still works in a dynamic environment, see the dual-channel logic in client.js).
    ctx.effect(() => ctx.webServer.register({
      kind: 'exact',
      path: '/__dsh__/filepanel/rpc',
      handler: async (req, res) => {
        try {
          const chunks = []
          for await (const chunk of req) chunks.push(chunk)
          const raw = Buffer.concat(chunks).toString('utf8') || '{}'
          const parsed = JSON.parse(raw)
          const method = String((parsed && parsed.method) || '')
          const fn = handlers[method]
          const result = fn === undefined
            ? { ok: false, error: 'Unknown method: ' + method }
            : await fn((parsed && parsed.args) || {})
          res.writeHead(200, { 'Content-Type': 'application/json' })
          res.end(JSON.stringify(result))
        } catch (e) {
          try {
            res.writeHead(500, { 'Content-Type': 'application/json' })
            res.end(JSON.stringify({ ok: false, error: (e && e.message) ? e.message : String(e) }))
          } catch (_) { /* ignore */ }
        }
      },
    }))

    // Same-origin download route: token check + workspace containment, supports inline preview (images/PDF) and attachment downloads.
    ctx.effect(() => ctx.webServer.register({
      kind: 'exact',
      path: '/__dsh__/filepanel/download',
      handler: async (req, res) => {
        try {
          const q = parseQuery(req.url)
          if (q.t !== token) { res.writeHead(403); res.end('forbidden'); return }
          const root = String(q.root || '')
          const path = String(q.path || '')
          const name = String(q.name || String(path).split('/').pop() || 'file')
          const { target } = await resolveWithin(root, path)
          const info = await ctx.fs.stat(target)
          if (!info || info.type !== 'file') { res.writeHead(404); res.end('not found'); return }
          if (info.size && info.size > MAX_DOWNLOAD) { res.writeHead(413); res.end('too large'); return }
          const bytes = await ctx.fs.readBytes(target, undefined, MAX_DOWNLOAD)
          const ext = String(name).split('.').pop().toLowerCase()
          const mime = MIME[ext] || 'application/octet-stream'
          const wantInline = q.inline === '1' && mime !== 'application/octet-stream'
          res.writeHead(200, {
            'Content-Type': mime,
            'Content-Disposition': (wantInline ? 'inline' : 'attachment') + '; filename="' + name.replace(/["\r\n]/g, '_') + '"',
            'Content-Length': String(bytes.length),
            'Cache-Control': 'no-store',
          })
          res.end(bytes)
        } catch (e) {
          try { res.writeHead(500); res.end('internal error') } catch (_) { /* ignore */ }
        }
      },
    }))

    console.log('[filepanel] host ready')
}

// The Cordis loader unwraps the default export and only reads inject from it (named exports are ignored),
// so inject must be attached to the default-exported function (same pattern as dsh-filetransfer).
apply.inject = inject

export default apply
