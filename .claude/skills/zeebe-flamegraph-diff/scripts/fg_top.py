"""Per-function SELF-time top list for one async-profiler flamegraph.

Self time = a frame's width minus the width of its direct children, aggregated by
title across the whole tree. Good first look at "what is this node burning CPU on".

Usage: python3 fg_top.py <flamegraph.html> [topN]
"""
import sys
from fg_common import load_frames


def self_times(path):
    total, frames = load_frames(path)
    stack = {}
    self_by = {}
    for lvl, title, width in frames:
        stack[lvl] = title
        self_by[title] = self_by.get(title, 0) + width
        if lvl > 0 and stack.get(lvl - 1) is not None:
            self_by[stack[lvl - 1]] = self_by.get(stack[lvl - 1], 0) - width
    return total, self_by


if __name__ == "__main__":
    top = int(sys.argv[2]) if len(sys.argv) > 2 else 30
    total, s = self_times(sys.argv[1])
    print(f"total samples: {total}")
    for t, v in sorted(s.items(), key=lambda x: -x[1])[:top]:
        if v <= 0:
            continue
        print(f"  {100 * v / total:6.2f}%  {t[:90]}")
