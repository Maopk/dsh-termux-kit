#!/data/data/com.termux/files/usr/bin/python3
"""dsh_common — one place that answers "where is DSH actually running?".

WHY THIS EXISTS
---------------
`dsh-status-pub` and `dsh-tasksd` each used to hard-code port 8080 when deciding whether DSH is
up. That is a guess dressed as a fact, and it broke loudly on 2026-09-27: DSH was running on 8099
(a sandbox instance) and *both* detectors reported "DSH is not running" — the Console app's lamp
and the DSH page panel were answering a different question than the user was asking.

`~/.dsh-url` is the record of where DSH really is: the start path writes it once the readiness
check passes (token line in the log + the URL following redirects to 200). Read the port from
there, and fall back to 8080 only when the file is missing or unreadable.

Kept as one module on purpose — the port question had two answers that disagreed, which is the
same failure mode as the task ids written down in four places. Callers do:

    sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
    import dsh_common
    port = dsh_common.dsh_port()
"""
import os
import re

HOME = os.path.expanduser('~')
DEFAULT_PORT = 8080
# Same override the start scripts honour, so a sandbox can never be mistaken for the real thing.
URL_FILE = os.environ.get('DSH_URL_FILE') or os.path.join(HOME, '.dsh-url')


def dsh_url():
    """The URL DSH recorded for itself, or '' when there is none."""
    try:
        with open(URL_FILE, encoding='utf-8') as fh:
            return fh.readline().strip()
    except Exception:  # noqa: BLE001 - a missing or unreadable ~/.dsh-url simply means "no URL yet"
        return ''


def dsh_port():
    """The port DSH is on according to ~/.dsh-url; DEFAULT_PORT when that cannot be read."""
    m = re.search(r'127\.0\.0\.1:(\d+)', dsh_url())
    return int(m.group(1)) if m else DEFAULT_PORT


def dsh_base():
    """Base URL for probing, e.g. http://127.0.0.1:8099/"""
    return f'http://127.0.0.1:{dsh_port()}/'


def boot_lock():
    """Path of the boot lock for the current port (the lock is per port by design)."""
    return os.path.join(HOME, f'.dsh-boot-{dsh_port()}.lock')
