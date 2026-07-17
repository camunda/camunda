"""Shared async-profiler HTML flamegraph parser for Zeebe/Camunda benchmark profiles.

async-profiler emits frames as a sequence of f()/u()/n() JS calls over a
prefix-compressed `cpool` string array. This module unpacks the cpool and walks
the call sequence into (level, title, width) frames. Width is in sample units;
the level-0 frame width is the total sample count.

Frame call semantics (from the generated HTML):
  f(key, level, left, width)  -> frame at absolute `level`, sets width0=width
  u(key, width)               -> child: level0+1, inherits width0 if width omitted/0
  n(key, width)               -> sibling: same level0, inherits width0 if omitted/0
`key >>> 3` indexes into the unpacked cpool to get the frame title.
"""
import re


def load_frames(path):
    """Return (total_samples, [(level, title, width), ...]) for a flamegraph HTML."""
    txt = open(path, encoding="utf-8").read()
    m = re.search(r"const cpool = \[(.*?)\];", txt, re.S)
    items = re.findall(r"'((?:[^'\\]|\\.)*)'", m.group(1))
    # prefix decompression: first char of each entry encodes shared-prefix length
    cp = [items[0]]
    for i in range(1, len(items)):
        s = items[i]
        cp.append(cp[i - 1][: ord(s[0]) - 32] + s[1:])
    body = txt[txt.rindex("unpack(cpool);"):]
    frames = []
    total = 0
    width0 = 0
    lvl = 0
    for line in body.splitlines():
        line = line.strip()
        mm = re.match(r"([fun])\(([\d,\-]+)\)", line)
        if not mm:
            continue
        fn = mm.group(1)
        a = [int(x) for x in mm.group(2).split(",")]
        if fn == "f":
            key = a[0]
            lvl = a[1]
            # f(key, level, left, width): a[2] is `left`, a[3] is width.
            width = a[3] if len(a) > 3 and a[3] != 0 else width0
        elif fn == "u":
            lvl += 1
            key = a[0]
            width = a[1] if len(a) > 1 and a[1] != 0 else width0
        else:  # n
            key = a[0]
            width = a[1] if len(a) > 1 and a[1] != 0 else width0
        width0 = width
        title = cp[key >> 3]
        if lvl == 0:
            total = width
        frames.append((lvl, title, width))
    return total, frames


def normalize(title):
    """Collapse per-JVM-run volatile identifiers so two runs are comparable.

    JIT lambda/proxy class names embed a per-run address (…$$Lambda.0x00000000abc.run)
    and native .so temp files embed a random suffix (librocksdbjni<random>.so). These
    differ every run for identical code, so strip them before diffing."""
    t = re.sub(r"0x[0-9a-f]+", "0xLAMBDA", title)
    t = re.sub(r"librocksdbjni\d+\.so", "librocksdbjni.so", t)
    t = re.sub(r"libnetty_[a-z_0-9]+\.so", "libnetty.so", t)
    return t
