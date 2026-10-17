"""Diff two async-profiler flamegraphs by INCLUSIVE per-function %.

Inclusive % = share of total samples whose stack contains the function anywhere.
Per-run volatile identifiers (lambda addresses, native .so temp names) are
normalized (see fg_common.normalize) so the diff reflects real code, not noise.

Prints functions that grew the most in FILE_B (e.g. today / regressed) and those
that shrank vs FILE_A (e.g. baseline / healthy).

Usage: python3 fg_diff.py <baseline.html> <candidate.html> [min_delta_pct]

IMPORTANT for comparability: only diff like-for-like profiles. In the Camunda
daily benchmark each run produces BOTH a gRPC and a REST flamegraph for the same
node — never diff gRPC against REST (the whole request-handling stack differs).
Identify which is which first (see SKILL.md).
"""
import sys
from fg_common import load_frames, normalize


def inclusive_pct(path):
    total, frames = load_frames(path)
    incl = {}
    for _, title, width in frames:
        k = normalize(title)
        incl[k] = incl.get(k, 0) + width
    return {k: 100 * v / total for k, v in incl.items()}


if __name__ == "__main__":
    a = inclusive_pct(sys.argv[1])
    b = inclusive_pct(sys.argv[2])
    thr = float(sys.argv[3]) if len(sys.argv) > 3 else 1.5
    keys = set(a) | set(b)
    d = sorted(((b.get(k, 0) - a.get(k, 0), k) for k in keys), reverse=True)
    print(f"### Grew in {sys.argv[2].split('/')[-1][:30]} (candidate):")
    for v, k in d:
        if v < thr:
            break
        print(f"  +{v:6.2f}  (base {a.get(k, 0):6.2f} -> cand {b.get(k, 0):6.2f})  {k[:76]}")
    print(f"### Shrank vs {sys.argv[1].split('/')[-1][:30]} (baseline):")
    for v, k in d[::-1]:
        if -v < thr:
            break
        print(f"  {v:7.2f}  (base {a.get(k, 0):6.2f} -> cand {b.get(k, 0):6.2f})  {k[:76]}")
