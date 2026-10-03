"""Shared setup for the unit tests in tests/unit/.

report_check.py is a script in tests/, not a module in a package, so the tests put that
directory on sys.path and import it by name.
"""

from __future__ import annotations

import pathlib
import sys

TESTS = pathlib.Path(__file__).resolve().parents[1]
if str(TESTS) not in sys.path:
    sys.path.insert(0, str(TESTS))
