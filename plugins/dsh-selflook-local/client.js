// dsh-selflook-local —— 客户端半部：既能「自看」，也能「遥控」（bundle 规范 CJS 工厂）
//
// 通道 A（自看）：每 2s 轮询宿主 /__dsh__/selflook/rpc；宿主侧 touch ~/.dsh-look-request 即截图回传。
// 通道 B（遥控）：每 2s 读 ~/.dsh-look-cmd.json（借 filepanel 的读写 RPC），
//   执行 {action: probe|click|swipe|scroll|capture|eval}，结果写回 ~/.dsh-look-cmd-result.json 并清空命令文件。
//
// 手势与滚动的实测边界（2026-09-26）：
//   · JS 驱动的拖拽（鲸鱼挂件、滑块、滑动删除）→ 合成 pointer/touch 事件有效 ✓
//   · 原生滚动容器 → **合成 touchmove 有效（还带惯性：300px 拖拽滚了 582px）** ✓，合成 pointer 基本无效
//   · 合成 wheel → 无效 ✗（untrusted wheel 不触发默认动作），要精确滚动请用 {action:'scroll'}
//
// 渲染兜底：①整页+CSS ②视口+CSS ③整页无CSS ④文字快照（必定成功）
//
// ⚠️ 四个已经踩过的坑（2026-09-26 全部实测定案）：
// 1) CSS 里含裸 `<`（如 url("data:image/svg+xml,<svg…>")）会让 SVG 解析失败 → 必须用 CDATA 包裹。
// 2) **blob: URL 加载的 SVG 只要含 <foreignObject>，Chromium 就判为不透明源 → canvas 污染、导出被拒。**
//    实测：blob:+纯SVG=干净、blob:+foreignObject=污染、**data:+foreignObject=干净** → 必须用 data: URL。
// 3) 外链资源（含“同源”）在 SVG-as-image 里也可能算跨域 → 一律本地化成 data:，外部引用元素整个删掉。
// 4) 合成指针事件时 setPointerCapture 会抛 NotFoundError，拖拽类组件（鲸鱼挂件）的 pointerdown
//    会因此中断、点不动 → 点击时临时把 setPointerCapture 打桩成空函数。
window.__ModuleLoader__.load({
  id: 'dsh-selflook-local',
  factory: (require) => {
    var module = { exports: {} };
    var exports = module.exports;

    const inject = ['timer'];
    const BLANK = 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
    const HOME = '/data/data/com.termux/files/home';
    const CMD_PATH = HOME + '/.dsh-look-cmd.json';
    const RES_PATH = HOME + '/.dsh-look-cmd-result.json';
    const MAX_RESULT = 60000;

    // ---------- 通道工具 ----------
    async function post(url, body) {
      const resp = await fetch(url, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(body),
      });
      return await resp.json();
    }

    const rpc = (method, payload) => post('/__dsh__/selflook/rpc', Object.assign({ method: method }, payload || {}));
    const fp = (method, args) => post('/__dsh__/filepanel/rpc', { method: method, args: args || {} });

    async function report(message) {
      try { await rpc('log', { message: String(message).slice(0, 800) }); } catch (e) { /* ignore */ }
    }

    async function readCmd() {
      try {
        const r = await fp('panel.readText', { root: HOME, path: CMD_PATH });
        if (!r || r.ok !== true || r.tooLarge === true) return '';
        return String(r.content || '').trim();
      } catch (e) { return ''; }
    }

    async function writeResult(obj) {
      let text = '';
      try { text = JSON.stringify(obj, null, 1); } catch (e) { text = '{"error":"结果无法序列化"}'; }
      if (text.length > MAX_RESULT) text = text.slice(0, MAX_RESULT) + '\n…（已截断，原始 ' + text.length + ' 字符）';
      try { await fp('panel.writeText', { root: HOME, path: RES_PATH, content: text }); } catch (e) { /* ignore */ }
    }

    async function clearCmd() {
      try {
        const r = await fp('panel.writeText', { root: HOME, path: CMD_PATH, content: '' });
        return !!(r && r.ok === true);
      } catch (e) { return false; }
    }

    // ---------- DOM 工具（穿透 shadow DOM） ----------
    function deepAll(root) {
      const out = [];
      const walk = (node) => {
        let list = [];
        try { list = Array.from(node.querySelectorAll('*')); } catch (e) { return; }
        for (const el of list) {
          out.push(el);
          if (el.shadowRoot) walk(el.shadowRoot);
        }
      };
      walk(root || document);
      return out;
    }

    function describe(el) {
      if (!el || !el.tagName) return '(无)';
      const r = el.getBoundingClientRect();
      const cls = (el.getAttribute && el.getAttribute('class')) || '';
      const id = (el.getAttribute && el.getAttribute('id')) || '';
      let vis = 'visible';
      try {
        const cs = getComputedStyle(el);
        if (cs.display === 'none' || cs.visibility === 'hidden' || Number(cs.opacity) === 0) vis = 'hidden';
      } catch (e) { /* ignore */ }
      return el.tagName.toLowerCase() + (id ? '#' + id : '') + (cls ? '.' + String(cls).split(/\s+/).slice(0, 3).join('.') : '') +
        ' @' + Math.round(r.left) + ',' + Math.round(r.top) + ' ' + Math.round(r.width) + 'x' + Math.round(r.height) + ' ' + vis;
    }

    function ownText(el) {
      let s = '';
      for (const n of el.childNodes) if (n.nodeType === 3) s += n.textContent;
      return s.replace(/\s+/g, ' ').trim();
    }

    function isVisible(el) {
      const r = el.getBoundingClientRect();
      if (r.width < 2 || r.height < 2) return false;
      try {
        const cs = getComputedStyle(el);
        if (cs.display === 'none' || cs.visibility === 'hidden' || Number(cs.opacity) === 0) return false;
      } catch (e) { return false; }
      return true;
    }

    function findTarget(spec) {
      const all = deepAll();
      if (spec.selector) {
        for (const el of all) {
          try { if (el.matches(spec.selector)) return el; } catch (e) { /* ignore */ }
        }
      }
      if (spec.text) {
        const t = String(spec.text);
        const loose = [];
        for (const el of all) {
          const own = ownText(el);
          if (own && own.indexOf(t) >= 0) { if (isVisible(el)) return el; loose.push(el); }
        }
        if (loose.length) return loose[loose.length - 1];
      }
      if (spec.title) {
        for (const el of all) {
          const a = (el.getAttribute && (el.getAttribute('title') || el.getAttribute('aria-label'))) || '';
          if (a.indexOf(String(spec.title)) >= 0) return el;
        }
      }
      if (typeof spec.x === 'number' && typeof spec.y === 'number') {
        return document.elementFromPoint(spec.x, spec.y);
      }
      return null;
    }

    // 点击：要骗过拖拽类组件，必须先把 setPointerCapture 打桩（合成 pointerId 不是活动指针）
    function tap(el) {
      const r = el.getBoundingClientRect();
      const x = r.left + r.width / 2;
      const y = r.top + r.height / 2;
      const base = {
        bubbles: true, cancelable: true, composed: true, view: window,
        clientX: x, clientY: y, screenX: x, screenY: y, button: 0,
      };
      const P = { pointerId: 1, pointerType: 'touch', isPrimary: true };
      const fire = (name, Ctor, extra) => {
        try { el.dispatchEvent(new Ctor(name, Object.assign({}, base, extra || {}))); return true; } catch (e) { return false; }
      };
      const proto = window.Element && window.Element.prototype;
      const saved = {};
      for (const k of ['setPointerCapture', 'releasePointerCapture', 'hasPointerCapture']) {
        if (proto && typeof proto[k] === 'function') { saved[k] = proto[k]; proto[k] = function () { return false; }; }
      }
      try {
        fire('pointerover', window.PointerEvent, Object.assign({ buttons: 0 }, P));
        fire('mouseover', window.MouseEvent, { buttons: 0 });
        fire('pointerdown', window.PointerEvent, Object.assign({ buttons: 1 }, P));
        fire('mousedown', window.MouseEvent, { buttons: 1 });
        fire('pointerup', window.PointerEvent, Object.assign({ buttons: 0 }, P));
        fire('mouseup', window.MouseEvent, { buttons: 0 });
        fire('click', window.MouseEvent, { buttons: 0 });
      } finally {
        for (const k in saved) proto[k] = saved[k];
      }
      return { at: [Math.round(x), Math.round(y)] };
    }

    function fingerprint() {
      return document.getElementsByTagName('*').length + ':' + (document.body.innerText || '').length;
    }

    // 外部引用清单：用来定位 canvas 污染源
    function externalInventory() {
      const inv = { img: 0, imgData: 0, svgImage: 0, use: 0, video: 0, audio: 0, iframe: 0, object: 0, embed: 0, inlineBg: 0, samples: [] };
      for (const el of deepAll()) {
        const tag = (el.tagName || '').toLowerCase();
        const attr = (n) => (el.getAttribute ? (el.getAttribute(n) || '') : '');
        const note = (kind, val) => { if (inv.samples.length < 8) inv.samples.push(kind + ': ' + String(val).slice(0, 90)); };
        if (tag === 'img') {
          const s = attr('src');
          if (s.startsWith('data:')) inv.imgData++; else { inv.img++; note('img', s); }
        }
        if (tag === 'image') {
          const h = attr('href') || attr('xlink:href');
          if (!h.startsWith('data:')) { inv.svgImage++; note('svg-image', h); }
        }
        if (tag === 'use') {
          const h = attr('href') || attr('xlink:href');
          if (h && !h.startsWith('#')) { inv.use++; note('use', h); }
        }
        if (tag === 'video') { inv.video++; note('video', attr('src') || attr('poster')); }
        if (tag === 'audio') { inv.audio++; note('audio', attr('src')); }
        if (tag === 'iframe') { inv.iframe++; note('iframe', attr('src')); }
        if (tag === 'object') { inv.object++; note('object', attr('data')); }
        if (tag === 'embed') { inv.embed++; note('embed', attr('src')); }
        const st = attr('style');
        if (st && st.indexOf('url(') >= 0 && !/url\(\s*['"]?\s*data:/i.test(st)) { inv.inlineBg++; note('inline-url', st.slice(0, 60)); }
      }
      return inv;
    }

    // ---------- 渲染 ----------
    function repairXml(text) {
      return String(text).replace(/&(?!(#[0-9]+|#x[0-9a-fA-F]+|[a-zA-Z][a-zA-Z0-9]*);)/g, '&amp;');
    }

    function cdata(text) {
      return '<![CDATA[' + String(text).split(']]>').join(']]]]><![CDATA[>') + ']]>';
    }

    function stripExternalUrls(css) {
      return String(css).replace(/url\(([^)]*)\)/g, (m, u) => (/^\s*['"]?\s*data:/i.test(u) ? m : 'none'));
    }

    // ★ 关键：SVG 必须走 data: URL。blob: + foreignObject 会被 Chromium 判为不透明源（污染 canvas）
    function svgToDataUrl(svg) {
      const bytes = new window.TextEncoder().encode(svg);
      let bin = '';
      for (let i = 0; i < bytes.length; i += 8192) {
        bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 8192));
      }
      return 'data:image/svg+xml;base64,' + window.btoa(bin);
    }

    function hardenHtml(html, level) {
      let out = String(html);
      out = out.replace(/<(video|audio|source|track|iframe|object|embed)\b[^>]*>[\s\S]*?<\/\1>/gi, '');
      out = out.replace(/<(video|audio|source|track|iframe|object|embed)\b[^>]*\/?>/gi, '');
      out = out.replace(/\s(?:xlink:)?(href|src|poster|data|srcset)\s*=\s*("([^"]*)"|'([^']*)')/gi,
        (m, name, _all, dq, sq) => {
          const v = dq !== undefined ? dq : (sq || '');
          return (v.startsWith('data:') || v.startsWith('#')) ? m : '';
        });
      if (level >= 2) {
        out = out.replace(/<(svg|img|picture|canvas|image)\b[^>]*>[\s\S]*?<\/\1>/gi, '');
        out = out.replace(/<(img|image|source)\b[^>]*\/?>/gi, '');
      }
      return out;
    }

    function blobToDataUrl(blob) {
      return new Promise((resolve, reject) => {
        const fr = new window.FileReader();
        fr.onload = () => resolve(fr.result);
        fr.onerror = () => reject(new Error('FileReader 失败'));
        fr.readAsDataURL(blob);
      });
    }

    async function localizeResources(clone) {
      let inlined = 0;
      let dropped = 0;
      for (const img of Array.from(clone.querySelectorAll('img'))) {
        const src = img.getAttribute('src') || '';
        if (src.startsWith('data:')) { inlined++; continue; }
        if (src === '') { img.setAttribute('src', BLANK); dropped++; continue; }
        let abs = null;
        try { abs = new window.URL(src, window.location.href); } catch (e) { abs = null; }
        if (!abs || abs.origin !== window.location.origin) { img.setAttribute('src', BLANK); dropped++; continue; }
        try {
          const resp = await fetch(abs.href);
          img.setAttribute('src', await blobToDataUrl(await resp.blob()));
          inlined++;
        } catch (e) { img.setAttribute('src', BLANK); dropped++; }
      }
      clone.querySelectorAll('[srcset]').forEach((n) => n.removeAttribute('srcset'));
      clone.querySelectorAll('[style]').forEach((n) => {
        const s = n.getAttribute('style') || '';
        if (s.indexOf('url(') >= 0) n.setAttribute('style', stripExternalUrls(s));
      });
      return '图片内联' + inlined + '/占位' + dropped;
    }

    function snapshotCanvases(clone) {
      const srcs = document.querySelectorAll('canvas');
      const dsts = clone.querySelectorAll('canvas');
      let n = 0;
      for (let i = 0; i < dsts.length && i < srcs.length; i++) {
        try {
          const r = srcs[i].getBoundingClientRect();
          if (r.width < 2 || r.height < 2) continue;
          const img = document.createElement('img');
          img.setAttribute('src', srcs[i].toDataURL('image/png'));
          img.setAttribute('width', String(Math.round(r.width)));
          img.setAttribute('height', String(Math.round(r.height)));
          dsts[i].parentNode.replaceChild(img, dsts[i]);
          n++;
        } catch (e) { /* 污染或空 canvas */ }
      }
      return n;
    }

    function collectCss() {
      let css = '';
      for (const sheet of Array.from(document.styleSheets)) {
        if (css.length > 240000) break;
        try {
          for (const rule of Array.from(sheet.cssRules)) css += rule.cssText + '\n';
        } catch (e) { /* 跨域样式表跳过 */ }
      }
      return stripExternalUrls(css);
    }

    async function prepare() {
      const w = document.documentElement.clientWidth;
      const fullH = Math.min(document.documentElement.scrollHeight, 2400);
      const viewH = document.documentElement.clientHeight;
      const bg = getComputedStyle(document.body).backgroundColor || '#ffffff';
      const clone = document.body.cloneNode(true);
      clone.querySelectorAll('script').forEach((n) => n.remove());
      const imgInfo = await localizeResources(clone);
      const nCanvas = snapshotCanvases(clone);
      const raw = new window.XMLSerializer().serializeToString(clone);
      return { w, fullH, viewH, bg, raw, imgInfo, nCanvas, css: collectCss() };
    }

    async function renderToPng(html, css, w, h, bg) {
      const win = window;
      const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="' + w + '" height="' + h + '">' +
        '<foreignObject x="0" y="0" width="100%" height="100%">' +
        '<div xmlns="http://www.w3.org/1999/xhtml" style="width:' + w + 'px;background:' + bg + '">' +
        (css ? '<style>' + cdata(css) + '</style>' : '') +
        repairXml(html) +
        '</div></foreignObject></svg>';
      const img = new win.Image();
      // 用 data: 而不是 blob:（见文件头注释第 2 条）
      await new Promise((resolve, reject) => {
        img.onload = resolve;
        img.onerror = () => reject(new Error('SVG解析失败 svg=' + svg.length));
        img.src = svgToDataUrl(svg);
      });
      const canvas = document.createElement('canvas');
      canvas.width = w; canvas.height = h;
      const c = canvas.getContext('2d', { willReadFrequently: true });
      c.fillStyle = bg; c.fillRect(0, 0, w, h);
      c.drawImage(img, 0, 0);
      const pngBlob = await new Promise((resolve) => canvas.toBlob(resolve, 'image/png'));
      if (pngBlob) {
        const bytes = new Uint8Array(await pngBlob.arrayBuffer());
        let bin = '';
        for (let i = 0; i < bytes.length; i += 8192) bin += String.fromCharCode.apply(null, bytes.subarray(i, i + 8192));
        return win.btoa(bin);
      }
      const dataUrl = canvas.toDataURL('image/png');
      return dataUrl.slice(dataUrl.indexOf(',') + 1);
    }

    function textSnapshot(limit) {
      const lines = [];
      lines.push('VIEWPORT ' + window.innerWidth + 'x' + window.innerHeight +
        ' scrollY=' + Math.round(window.scrollY) + ' title=' + document.title);
      let n = 0;
      const max = Number(limit || 400);
      for (const el of document.body.querySelectorAll('*')) {
        if (n >= max) break;
        const r = el.getBoundingClientRect();
        if (r.width < 2 || r.height < 2) continue;
        if (r.bottom < -20 || r.top > window.innerHeight + 20) continue;
        const own = ownText(el);
        if (own === '') continue;
        lines.push('[' + el.tagName.toLowerCase() + ' @' + Math.round(r.left) + ',' + Math.round(r.top) +
          ' ' + Math.round(r.width) + 'x' + Math.round(r.height) + '] ' + own.slice(0, 140));
        n++;
      }
      return lines.join('\n');
    }

    async function sendTextSnapshot(text) {
      await report('TEXT-SNAPSHOT 开始，共 ' + text.length + ' 字符');
      const CH = 1500;
      for (let i = 0; i < text.length; i += CH) {
        await rpc('log', { message: 'SNAP|' + text.slice(i, i + CH) });
      }
      await report('TEXT-SNAPSHOT 结束');
    }

    async function capture(quiet) {
      const p = await prepare();
      if (!quiet) {
        await report('收到触发：DOM=' + document.getElementsByTagName('*').length +
          ' 视口=' + p.w + 'x' + p.viewH + ' 整页高=' + p.fullH +
          ' css=' + p.css.length + ' raw=' + p.raw.length + ' | ' + p.imgInfo + ' canvas=' + p.nCanvas);
      }
      const attempts = [
        ['整页+CSS', hardenHtml(p.raw, 1), p.css, p.w, p.fullH],
        ['视口+CSS', hardenHtml(p.raw, 1), p.css, p.w, p.viewH],
        ['整页无CSS', hardenHtml(p.raw, 1), '', p.w, p.fullH],
      ];
      for (const a of attempts) {
        try {
          const b64 = await renderToPng(a[1], a[2], a[3], a[4], p.bg);
          await report(a[0] + ' 成功，base64=' + b64.length);
          return await rpc('deliver', { base64: b64 });
        } catch (e) {
          await report(a[0] + ' 失败: ' + ((e && e.message) || e));
        }
      }
      await report('三种渲染都失败 → 文字快照保底');
      await sendTextSnapshot(textSnapshot(400));
      return { ok: true, mode: 'text' };
    }

    // ---------- 手势（滑动）与滚动 ----------
    function sleep(ms) { return new Promise((r) => setTimeout(r, ms)); }

    function atPoint(x, y) {
      return document.elementFromPoint(Math.round(x), Math.round(y)) || document.body;
    }

    function firePointer(target, type, x, y) {
      const opts = {
        bubbles: true, cancelable: true, composed: true, view: window,
        clientX: x, clientY: y, screenX: x, screenY: y, button: 0,
        pointerId: 1, pointerType: 'touch', isPrimary: true,
      };
      try {
        if (type.indexOf('touch') === 0) {
          const t = new window.Touch({ identifier: 1, target: target, clientX: x, clientY: y, pageX: x, pageY: y });
          const list = type === 'touchend' ? [] : [t];
          target.dispatchEvent(new window.TouchEvent(type, Object.assign({}, opts, {
            touches: list, targetTouches: list, changedTouches: [t],
          })));
        } else if (type.indexOf('pointer') === 0) {
          target.dispatchEvent(new window.PointerEvent(type, Object.assign({}, opts, { buttons: type === 'pointerup' ? 0 : 1 })));
        } else {
          target.dispatchEvent(new window.MouseEvent(type, Object.assign({}, opts, { buttons: type === 'mouseup' ? 0 : 1 })));
        }
      } catch (e) { /* ignore */ }
    }

    // 元素可能是不规则形状（鲸鱼是 clip-path 多边形）：包围盒中心不一定命中，
    // 必须在矩形内扫描出「elementFromPoint 真能返回它」的点，否则事件到不了它身上。
    function grabPoint(spec) {
      if (Array.isArray(spec)) return { x: spec[0], y: spec[1], el: atPoint(spec[0], spec[1]), grabbed: null };
      if (typeof spec === 'string' && spec.indexOf(',') > 0 && !isNaN(spec.split(',')[0])) {
        const p = spec.split(',');
        return { x: Number(p[0]), y: Number(p[1]), el: atPoint(Number(p[0]), Number(p[1])), grabbed: null };
      }
      if (typeof spec !== 'string') return null;
      const el = findTarget({ selector: spec });
      if (!el) return null;
      const r = el.getBoundingClientRect();
      for (let fy = 0.15; fy <= 0.86; fy += 0.14) {
        for (let fx = 0.15; fx <= 0.86; fx += 0.14) {
          const x = Math.round(r.left + r.width * fx);
          const y = Math.round(r.top + r.height * fy);
          const hit = document.elementFromPoint(x, y);
          if (hit && (hit === el || el.contains(hit))) return { x: x, y: y, el: hit, grabbed: true };
        }
      }
      return { x: Math.round(r.left + r.width / 2), y: Math.round(r.top + r.height / 2), el: el, grabbed: false };
    }

    async function doSwipe(cmd) {
      const A = grabPoint(cmd.from);
      const B = grabPoint(cmd.to);
      if (!A || !B) return { ok: false, error: '起点或终点找不到', from: cmd.from, to: cmd.to };
      const steps = Number(cmd.steps || 16);
      const stepMs = Number(cmd.stepMs || 16);
      const startEl = A.el || document.body;
      const proto = window.Element && window.Element.prototype;
      const saved = {};
      for (const k of ['setPointerCapture', 'releasePointerCapture', 'hasPointerCapture']) {
        if (proto && typeof proto[k] === 'function') { saved[k] = proto[k]; proto[k] = function () { return false; }; }
      }
      try {
        firePointer(startEl, 'pointerover', A.x, A.y);
        firePointer(startEl, 'mouseover', A.x, A.y);
        firePointer(startEl, 'touchstart', A.x, A.y);
        firePointer(startEl, 'pointerdown', A.x, A.y);
        firePointer(startEl, 'mousedown', A.x, A.y);
        if (cmd.holdMs) await sleep(Number(cmd.holdMs));
        for (let i = 1; i <= steps; i++) {
          const x = A.x + (B.x - A.x) * i / steps;
          const y = A.y + (B.y - A.y) * i / steps;
          const t = atPoint(x, y);
          firePointer(t, 'touchmove', x, y);
          firePointer(t, 'pointermove', x, y);
          firePointer(t, 'mousemove', x, y);
          await sleep(stepMs);
        }
        const endEl = atPoint(B.x, B.y);
        firePointer(endEl, 'touchend', B.x, B.y);
        firePointer(endEl, 'pointerup', B.x, B.y);
        firePointer(endEl, 'mouseup', B.x, B.y);
      } finally {
        for (const k in saved) proto[k] = saved[k];
      }
      if (cmd.settleMs !== 0) await sleep(Number(cmd.settleMs || 600));
      const out = { ok: true, from: [A.x, A.y], to: [B.x, B.y], steps: steps, grabbed: A.grabbed, startEl: describe(startEl) };
      if (cmd.probe) {
        const p = document.querySelector(cmd.probe);
        if (p) out.probe = describe(p);
      }
      return out;
    }

    async function doScroll(cmd) {
      const el = (cmd.selector && cmd.selector !== 'window')
        ? findTarget({ selector: cmd.selector })
        : document.scrollingElement;
      if (!el) return { ok: false, error: '找不到滚动容器: ' + cmd.selector };
      const maxY = el.scrollHeight - el.clientHeight;
      const before = [Math.round(el.scrollLeft), Math.round(el.scrollTop)];
      let target = el.scrollTop;
      if (cmd.mode === 'top') target = 0;
      else if (cmd.mode === 'bottom') target = maxY;
      else if (cmd.mode === 'by') target = el.scrollTop + Number(cmd.value || 0);
      else if (cmd.mode === 'to') target = Number(cmd.value || 0);
      target = Math.max(0, Math.min(maxY, target));
      if (cmd.smooth !== false && el.scrollTo) el.scrollTo({ top: target, behavior: 'smooth' });
      else el.scrollTop = target;
      await sleep(Number(cmd.settleMs || 800));
      return {
        ok: true, selector: cmd.selector || 'window', mode: cmd.mode,
        before: before, after: [Math.round(el.scrollLeft), Math.round(el.scrollTop)], maxY: maxY,
      };
    }

    // ---------- 遥控动作 ----------
    async function runAction(cmd) {
      const action = String((cmd && cmd.action) || 'capture');
      if (action === 'capture') {
        return { action: action, out: await capture(false), tail: textSnapshot(60) };
      }
      if (action === 'probe') {
        const kw = String(cmd.keyword || '').toLowerCase();
        const hits = [];
        for (const el of deepAll()) {
          const tag = (el.tagName || '').toLowerCase();
          const id = ((el.getAttribute && el.getAttribute('id')) || '').toLowerCase();
          const cls = ((el.getAttribute && el.getAttribute('class')) || '').toLowerCase();
          const text = ownText(el).toLowerCase();
          const hay = tag + ' ' + id + ' ' + cls + ' ' +
            ((el.getAttribute && el.getAttribute('title')) || '') + ' ' + ((el.getAttribute && el.getAttribute('aria-label')) || '');
          const hitKeyword = kw === '' ? false : (hay.indexOf(kw) >= 0 || text.indexOf(kw) >= 0);
          const hitSelector = cmd.selector ? (() => { try { return el.matches(cmd.selector); } catch (e) { return false; } })() : false;
          if (hitKeyword || hitSelector) {
            hits.push(describe(el) + (text ? ' | 文本: ' + text.slice(0, 40) : ''));
            if (hits.length >= Number(cmd.limit || 25)) break;
          }
        }
        return { action: action, keyword: kw, count: hits.length, hits: hits, inventory: externalInventory() };
      }
      if (action === 'click') {
        const el = findTarget(cmd);
        if (!el) return { action: action, ok: false, error: '没找到目标', spec: cmd };
        const before = fingerprint();
        const target = (el.closest && el.closest('button,[role=button],a')) || el;
        try { target.scrollIntoView({ block: 'center', inline: 'center' }); } catch (e) { /* ignore */ }
        const info = tap(target);
        const res = { action: action, ok: true, target: describe(el), clicked: describe(target), at: info.at };
        if (cmd.after !== false) {
          await new Promise((r) => setTimeout(r, Number(cmd.waitMs || 900)));
          res.domChanged = before !== fingerprint();
          res.after = await capture(true);
          res.afterText = textSnapshot(Number(cmd.textLimit || 200));
        }
        return res;
      }
      if (action === 'swipe') {
        const res = await doSwipe(cmd);
        if (cmd.capture !== false) {
          res.after = await capture(true);
        }
        return res;
      }
      if (action === 'scroll') {
        const res = await doScroll(cmd);
        if (cmd.capture) res.after = await capture(true);
        return res;
      }
      if (action === 'eval') {
        const code = String(cmd.code || '');
        const fn = new Function('"use strict";return (async () => { ' + code + ' })()');
        const value = await fn();
        let safe = value;
        if (value && value.tagName) safe = describe(value);
        else if (value && value.nodeType === 9) safe = 'document';
        return { action: action, ok: true, value: safe };
      }
      return { action: action, ok: false, error: '未知动作: ' + action };
    }

    function apply(ctx) {
      let busy = false;
      let lastCmd = '';
      let lastPoll = 0;

      const stop = ctx.interval(async () => {
        if (busy) return;
        busy = true;
        try {
          const raw = await readCmd();
          if (raw !== '' && raw !== lastCmd) {
            lastCmd = raw;
            let cmd = null;
            try { cmd = JSON.parse(raw); } catch (e) { cmd = null; }
            if (cmd === null) {
              await report('命令文件不是合法 JSON，已忽略');
              await writeResult({ ok: false, error: '命令文件不是合法 JSON' });
              await clearCmd();
            } else {
              let out = null;
              try { out = await runAction(cmd); }
              catch (e) { out = { ok: false, error: (e && e.message) || String(e), stack: String(e && e.stack).slice(0, 400) }; }
              await writeResult(Object.assign({ ts: new Date().toISOString(), cmd: cmd }, out));
              const cleared = await clearCmd();
              // 只有确认清空成功才重置基线，否则同一条命令会被反复执行
              lastCmd = cleared ? '' : raw;
              await report('遥控命令完成: ' + String(cmd.action || '') + ' ' + JSON.stringify(out).slice(0, 200));
            }
          }
          const now = Date.now();
          if (now - lastPoll > 3000) {
            lastPoll = now;
            const res = await rpc('poll', {});
            if (res && res.pending === true) {
              const out = await capture(false);
              await report('本轮完成: ' + JSON.stringify(out));
            }
          }
        } catch (e) {
          await report('本轮异常: ' + ((e && e.message) || e) + ' | ' + String(e && e.stack).slice(0, 200));
        } finally {
          busy = false;
        }
      }, 2000);

      ctx.effect(() => stop);
      console.log('[selflook] client ready (capture + remote, data-url render)');
    }

    exports.apply = apply;
    exports.inject = inject;
    return module.exports;
  },
});
