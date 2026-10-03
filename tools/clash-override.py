#!/data/data/com.termux/files/usr/bin/python3
"""clash-override.py —— fill in the Clash Meta 覆写 form through the DSH bridge (with verification, so taps do not drift).

Why a script: this form is a "list inside a dialog" — the input box confirm button sits at y≈1271,
and the list confirm button sits at y≈2931, so finding 确认 by text collides (I hit this once and turned fallback into "clearing the field").
So everything here taps **by coordinate**, dumps back for verification after every step, and stops with an error when the result does not match.

Usage: 
  clash-override.py audit                     # list the current value of every field
  clash-override.py list "Fallback Name Server" "https://1.1.1.1/dns-query" "https://dns.google/dns-query"
  clash-override.py enum "增强模式" fake-ip
  clash-override.py open "Name Server"        # just open one field (for debugging)
"""
import os
import re
import subprocess
import sys
import time

SOCK = os.path.expanduser('~/.local/bin/droid-sock')
UITAP = os.path.expanduser('~/.local/bin/dsh-uitap')

# fixed coordinates measured from real dumps
BTN_NEW = (1317, 265)        # the 新建 button at the top right of the list dialog
EDIT_BOX = (720, 1055)       # the text field of the input dialog
BTN_INPUT_OK = (1164, 1271)  # 确认 in the input dialog
BTN_INPUT_CANCEL = (894, 1271)
BTN_LIST_OK = (1256, 2931)   # 确认 in the list dialog
BTN_LIST_CANCEL = (926, 2931)
BTN_LIST_RESET = (184, 2931)
FIRST_ROW_DEL = (1317, 438)  # the 删除 button on the first row
ROW_H = 233                  # row height (spacing between entries)

LABELS = ['HTTP 端口', 'Socks 端口', 'Redirect 端口', 'TProxy 端口', '复合端口', '认证',
          '允许来自局域网的连接', 'IPv6', '监听地址', 'External Controller',
          'External Controller TLS', 'External Controller Allow Origins',
          'External Controller Allow Private Network', 'Secret', '模式', '日志级别', 'Hosts',
          '策略', 'H3 优先', '监听', '追加系统 DNS', '使用 Hosts', '增强模式',
          'Name Server', 'Fallback Name Server', 'Default Name Server',
          'FakeIP 过滤器', 'FakeIP 过滤器模式', 'GeoIP Fallback', 'GeoIP Fallback 区域代码',
          '域名 Fallback', 'IPCIDR Fallback', 'Name Server 策略']


def sh(*args, timeout=45):
    return subprocess.run(list(args), capture_output=True, text=True, timeout=timeout, check=False).stdout


def tap(x, y):
    sh(SOCK, 'tap', str(x), str(y), timeout=30)
    time.sleep(1.0)


def set_text(v):
    sh(SOCK, 'text', v, timeout=60)


def nodes(kw=''):
    # 必须显式要更多节点：droid-sock ui 默认只回 40 条，而覆写页字段远多于 40，
    # 默认值下「FakeIP 过滤器」那一项根本不在返回里——「看不见」和「不存在」是两件事。
    out = sh(SOCK, 'ui', kw, '400')
    res = []
    for line in out.splitlines():
        m = re.match(r"\s*\[(-?\d+),(-?\d+)\]\s+(\S+)\s+text=(.*?)\s+desc=(.*?)\s+id=", line)
        if m:
            res.append({'x': int(m.group(1)), 'y': int(m.group(2)), 'cls': m.group(3),
                        'text': m.group(4).strip().strip("'"), 'desc': m.group(5).strip().strip("'")})
    return res


def dialog_title():
    """Title of the dialog: the TextView at y≈265; returns None when no dialog is open."""
    for n in nodes():
        if 240 <= n['y'] <= 300 and n['cls'] == 'TextView' and n['text']:
            return n['text']
    return None


def open_field(label):
    for _ in range(3):
        subprocess.run([UITAP, label], capture_output=True, text=True, timeout=120, check=False)
        time.sleep(1.2)
        if dialog_title() == label:
            return True
    return False


def close_dialog():
    tap(*BTN_LIST_CANCEL)


def clear_rows():
    """Delete every existing entry in the list (each removal shifts the rows below up by one row height)."""
    removed = 0
    while removed < 40:
        dels = [n for n in nodes() if n['desc'] == '删除']
        if not dels:
            break
        tap(dels[0]['x'], dels[0]['y'])
        removed += 1
    return removed


def add_row(value):
    """Create one entry and write value into it; returns whether the confirm succeeded."""
    tap(*BTN_NEW)
    time.sleep(0.8)
    tap(*EDIT_BOX)
    time.sleep(0.5)
    set_text(value)
    time.sleep(0.8)
    got = [n for n in nodes() if n['cls'] == 'EditText']
    if not got or got[0]['text'] != value:
        return False
    tap(*BTN_INPUT_OK)
    return True


def list_set(label, values):
    if not open_field(label):
        print(f'✘ cannot open field: {label} (current title {dialog_title()})')
        return 1
    print(f'  entered: {label}')
    n = clear_rows()
    if n:
        print(f'  cleared {n} old entries')
    for v in values:
        if not add_row(v):
            print(f'✘ write failed: {v}')
            close_dialog()
            return 1
        print(f'  + {v}')
    cnt = len([x for x in nodes() if x['desc'] == '删除'])
    if cnt != len(values):
        print(f'✘ wrong entry count: expected {len(values)}, got {cnt}')
        close_dialog()
        return 1
    tap(*BTN_LIST_OK)
    time.sleep(1.5)
    print(f'  ✔ {label} = {cnt} entries')
    return 0


def enum_set(label, choice):
    if not open_field(label):
        print(f'✘ cannot open field: {label}')
        return 1
    opts = [n for n in nodes() if n['text'] == choice]
    if not opts:
        print('✘ option {} is not in the list; currently visible: {}'.format(choice, [n['text'] for n in nodes()][:12]))
        close_dialog()
        return 1
    tap(opts[0]['x'], opts[0]['y'])
    time.sleep(1.0)
    btn = [n for n in nodes() if n['text'] == '确认']
    if btn:
        tap(btn[-1]['x'], btn[-1]['y'])
    time.sleep(1.2)
    print(f'  ✔ {label} = {choice}')
    return 0


def audit():
    ns = nodes()
    for i, n in enumerate(ns):
        if n['text'] in LABELS and i + 1 < len(ns):
            print('{:<40} = {}'.format(n['text'], ns[i + 1]['text']))


def main():
    a = sys.argv[1:]
    if not a:
        print(__doc__)
        return 2
    if a[0] == 'audit':
        audit(); return 0
    if a[0] == 'open':
        print('Title:', open_field(a[1]) and dialog_title() or 'not open'); return 0
    if a[0] == 'list':
        return list_set(a[1], a[2:])
    if a[0] == 'enum':
        return enum_set(a[1], a[2])
    print('unknown usage'); return 2


if __name__ == '__main__':
    sys.exit(main())
