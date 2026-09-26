// dsh-selflook-local —— 让 DSH 界面把自身渲染成 PNG 回传（AI 自看）
// bundle 规范：具名 name/inject + apply.inject + export default apply
export const name = 'dsh-selflook-local'
export const inject = ['shell', 'sandboxPolicy', 'webServer']

const HOME = '/data/data/com.termux/files/home'
const REQ = HOME + '/.dsh-look-request'
const LAST = HOME + '/.dsh-look-last.txt'
const OUTDIR = HOME + '/storage/downloads/dsh/图片'

function apply(ctx) {
  let inFlightAt = 0   // 上次触发时间；超过 15s 视为放弃，允许重新触发
  // Termux 上没有可用的沙箱后端：必须显式用 danger-full-access，
  // 否则 shell 服务会以“no sandbox backend is usable”拒绝执行。
  const policy = () => ({ mode: 'danger-full-access', workspaceRoot: HOME })

  async function sh(command, stdin) {
    const spec = ctx.shell.resolve({
      command,
      workdir: HOME,
      sandboxPolicy: policy(),
      stdoutMaxBytes: 32 * 1024,
      timeoutMs: 60000,
      ...(stdin === undefined ? {} : { stdin }),
    })
    return await ctx.shell.run(spec)
  }

  ctx.effect(() => ctx.webServer.register({
    kind: 'exact',
    path: '/__dsh__/selflook/rpc',
    handler: async (req, res) => {
      const send = (code, obj) => {
        try { res.writeHead(code, { 'Content-Type': 'application/json' }); res.end(JSON.stringify(obj)) } catch (_) { /* ignore */ }
      }
      try {
        const chunks = []
        for await (const c of req) chunks.push(c)
        const body = JSON.parse(Buffer.concat(chunks).toString('utf8') || '{}')
        const method = String(body.method || '')

        if (method === 'poll') {
          const r = await sh('test -f ' + REQ + ' && echo yes || echo no')
          const text = (r && r.stdout && r.stdout.text) ? r.stdout.text : ''
          const stale = (Date.now() - inFlightAt) > 15000
          if (text.indexOf('yes') >= 0 && stale) {
            await sh('rm -f ' + REQ)
            inFlightAt = Date.now()
            return send(200, { pending: true })
          }
          return send(200, { pending: false })
        }

        if (method === 'deliver') {
          const b64 = String(body.base64 || '')
          if (b64 === '') throw new Error('空图像数据')
          const stamp = new Date().toISOString().replace(/[:.]/g, '-')
          const path = OUTDIR + '/self-look-' + stamp + '.png'
          await sh('mkdir -p ' + OUTDIR)
          const w = await sh('base64 -d > ' + path, b64)
          if (w && w.exitCode !== 0) throw new Error('写入失败 exit=' + w.exitCode)
          await sh('echo ' + path + ' > ' + LAST)
          inFlightAt = 0
          console.log('[selflook] saved ' + path)
          return send(200, { ok: true, path: path })
        }

        if (method === 'log') {
          const msg = String(body.message || '').slice(0, 2000)
          const line = '[' + new Date().toISOString() + '] ' + msg + '\n'
          // 用 stdin 传入内容，避免任何 shell 转义/注入问题
          await sh('cat >> ' + HOME + '/.dsh-look-client.log', line)
          return send(200, { ok: true })
        }

        return send(200, { ok: false, error: '未知方法 ' + method })
      } catch (e) {
        inFlightAt = 0
        return send(500, { ok: false, error: (e && e.message) ? e.message : String(e) })
      }
    },
  }))

  console.log('[selflook] host ready')
}

apply.inject = inject
export default apply
