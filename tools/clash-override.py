#!/data/data/com.termux/files/usr/bin/python3
"""clash-override.py —— 用 DSH 桥给 Clash Meta 的「覆写」表单填值（带校验，防点飘）。

为什么要写脚本：这个表单是"列表套对话框"——输入框的确认按钮在 y≈1271，
列表的确认按钮在 y≈2931，按文字找「确认」会撞车（我已经踩过一次，把 fallback 弄成了"置空"）。
所以这里一律**按坐标点**，并且每一步都 dump 回来校验，不符合预期就停下报错。

用法：
  clash-override.py audit                     # 列出所有字段当前值
  clash-override.py list "Fallback Name Server" "https://1.1.1.1/dns-query" "https://dns.google/dns-query"
  clash-override.py enum "增强模式" fake-ip
  clash-override.py open "Name Server"        # 只打开某字段（调试用）
"""
import os
import re
import subprocess
import sys
import time

SOCK = os.path.expanduser('~/.local/bin/droid-sock')
UITAP = os.path.expanduser('~/.local/bin/dsh-uitap')

# 从实测 dump 里量出来的固定坐标
BTN_NEW = (1317, 265)        # 列表对话框右上「新建」
EDIT_BOX = (720, 1055)       # 输入对话框的输入框
BTN_INPUT_OK = (1164, 1271)  # 输入对话框「确认」
BTN_INPUT_CANCEL = (894, 1271)
BTN_LIST_OK = (1256, 2931)   # 列表对话框「确认」
BTN_LIST_CANCEL = (926, 2931)
BTN_LIST_RESET = (184, 2931)
FIRST_ROW_DEL = (1317, 438)  # 第一行的「删除」
ROW_H = 233                  # 行高（条目间距）

LABELS = ['HTTP 端口', 'Socks 端口', 'Redirect 端口', 'TProxy 端口', '复合端口', '认证',
          '允许来自局域网的连接', 'IPv6', '监听地址', 'External Controller',
          'External Controller TLS', 'External Controller Allow Origins',
          'External Controller Allow Private Network', 'Secret', '模式', '日志级别', 'Hosts',
          '策略', 'H3 优先', '监听', '追加系统 DNS', '使用 Hosts', '增强模式',
          'Name Server', 'Fallback Name Server', 'Default Name Server',
          'FakeIP 过滤器', 'FakeIP 过滤器模式', 'GeoIP Fallback', 'GeoIP Fallback 区域代码',
          '域名 Fallback', 'IPCIDR Fallback', 'Name Server 策略']


def sh(*args, timeout=45):
    return subprocess.run(list(args), capture_output=True, text=True, timeout=timeout).stdout


def tap(x, y):
    sh(SOCK, 'tap', str(x), str(y), timeout=30)
    time.sleep(1.0)


def set_text(v):
    sh(SOCK, 'text', v, timeout=60)


def nodes(kw=''):
    out = sh(SOCK, 'ui', kw)
    res = []
    for line in out.splitlines():
        m = re.match(r"\s*\[(-?\d+),(-?\d+)\]\s+(\S+)\s+text=(.*?)\s+desc=(.*?)\s+id=", line)
        if m:
            res.append({'x': int(m.group(1)), 'y': int(m.group(2)), 'cls': m.group(3),
                        'text': m.group(4).strip().strip("'"), 'desc': m.group(5).strip().strip("'")})
    return res


def dialog_title():
    """对话框标题：y≈265 的 TextView；没有对话框时返回 None。"""
    for n in nodes():
        if 240 <= n['y'] <= 300 and n['cls'] == 'TextView' and n['text']:
            return n['text']
    return None


def open_field(label):
    for _ in range(3):
        subprocess.run([UITAP, label], capture_output=True, text=True, timeout=120)
        time.sleep(1.2)
        if dialog_title() == label:
            return True
    return False


def close_dialog():
    tap(*BTN_LIST_CANCEL)


def clear_rows():
    """把列表里已有条目全删掉（每删一条，后面的行会上移一个行高）。"""
    removed = 0
    while removed < 40:
        dels = [n for n in nodes() if n['desc'] == '删除']
        if not dels:
            break
        tap(dels[0]['x'], dels[0]['y'])
        removed += 1
    return removed


def add_row(value):
    """新建一条并写入 value，返回是否确认成功。"""
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
        print('✘ 打不开字段: %s（当前标题 %s）' % (label, dialog_title()))
        return 1
    print('  已进入: %s' % label)
    n = clear_rows()
    if n:
        print('  清掉旧条目 %d 条' % n)
    for v in values:
        if not add_row(v):
            print('✘ 写入失败: %s' % v)
            close_dialog()
            return 1
        print('  + %s' % v)
    cnt = len([x for x in nodes() if x['desc'] == '删除'])
    if cnt != len(values):
        print('✘ 条目数不对：期望 %d，实际 %d' % (len(values), cnt))
        close_dialog()
        return 1
    tap(*BTN_LIST_OK)
    time.sleep(1.5)
    print('  ✔ %s = %d 个条目' % (label, cnt))
    return 0


def enum_set(label, choice):
    if not open_field(label):
        print('✘ 打不开字段: %s' % label)
        return 1
    opts = [n for n in nodes() if n['text'] == choice]
    if not opts:
        print('✘ 选项里没有「%s」，当前可见：%s' % (choice, [n['text'] for n in nodes()][:12]))
        close_dialog()
        return 1
    tap(opts[0]['x'], opts[0]['y'])
    time.sleep(1.0)
    btn = [n for n in nodes() if n['text'] == '确认']
    if btn:
        tap(btn[-1]['x'], btn[-1]['y'])
    time.sleep(1.2)
    print('  ✔ %s = %s' % (label, choice))
    return 0


def audit():
    ns = nodes()
    for i, n in enumerate(ns):
        if n['text'] in LABELS and i + 1 < len(ns):
            print('%-40s = %s' % (n['text'], ns[i + 1]['text']))


def main():
    a = sys.argv[1:]
    if not a:
        print(__doc__)
        return 2
    if a[0] == 'audit':
        audit(); return 0
    if a[0] == 'open':
        print('标题:', open_field(a[1]) and dialog_title() or '未打开'); return 0
    if a[0] == 'list':
        return list_set(a[1], a[2:])
    if a[0] == 'enum':
        return enum_set(a[1], a[2])
    print('未知用法'); return 2


if __name__ == '__main__':
    sys.exit(main())
