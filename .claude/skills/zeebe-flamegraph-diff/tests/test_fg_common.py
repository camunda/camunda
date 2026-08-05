"""Golden test for the async-profiler HTML parser in fg_common.load_frames.

Regression guard for the f()-frame width bug: async-profiler emits
`f(key, level, left, width)`, so width is the 4th arg. Reading the 3rd (`left`)
instead silently corrupts every f-frame width (and any u/n frames that inherit
width0 from it). Runnable standalone: `python3 test_fg_common.py`.
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "scripts"))
import fg_common  # noqa: E402

FIXTURE = os.path.join(os.path.dirname(__file__), "fixture.html")


def test_load_frames():
    total, frames = fg_common.load_frames(FIXTURE)
    assert total == 100, total
    assert frames == [
        (0, "all", 100),
        (1, "root", 60),
        (1, "aa", 40),  # from f(16,1,30,40): width is a[3]=40, NOT left a[2]=30
        (1, "bb", 10),
    ], frames


if __name__ == "__main__":
    test_load_frames()
    print("ok")
