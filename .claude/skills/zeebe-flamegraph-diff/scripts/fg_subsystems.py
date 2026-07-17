"""Attribute one Zeebe flamegraph's CPU to Camunda subsystems (leaf-based).

Each leaf sample is assigned to the FIRST matching subsystem found while walking
its stack from root to leaf, using the ordered SUBSYSTEMS table below. Order
matters: more-specific / outer-owner categories come first. Leaves matching
nothing land in 'other'. Native/syscall scatter means coverage is typically
~70-75%, which is fine for a directional split.

Usage: python3 fg_subsystems.py <flamegraph.html>
"""
import re
import sys
from fg_common import load_frames

# Ordered (name, regex-over-joined-stack). See references/zeebe-contributors.md
# for what each subsystem means and why it shows up. Tuned for Zeebe broker
# profiles with the Elasticsearch/Camunda exporter enabled.
SUBSYSTEMS = [
    ("exporter-es-client", r"org/apache/http|broker/exporter|co/elastic/clients|elasticsearch|RestClient"),
    ("replay",             r"ReplayStateMachine|Engine\.replay"),
    ("processing",         r"ProcessingStateMachine|TypedRecordProcessor|processing/bpmn|EventAppliers|ResultBuilderBacked"),
    ("rocksdb-state",      r"rocksdb|TransactionalColumnFamily|ZeebeTransaction|ColumnFamilyContext"),
    ("journal-flush",      r"journal/file|RaftLogFlusher|MappedMemoryUtils\.force|Sequencer"),
    ("raft-netty",         r"io/atomix/raft|io/netty|SocketChannel|epoll"),
    ("grpc",               r"io/grpc"),
    ("gc",                 r"G1|GCTask|CMTask|CollectedHeap|markOop|ConcurrentGC"),
    ("scheduler-idle",     r"ActorThread\.waitOnRunnable|Unsafe\.park|park\b"),
]


def attribute(path):
    total, frames = load_frames(path)
    pathstack = {}
    agg = {name: 0 for name, _ in SUBSYSTEMS}
    agg["other"] = 0
    n = len(frames)
    for i, (lvl, title, w) in enumerate(frames):
        pathstack[lvl] = title
        is_leaf = (i + 1 >= n) or (frames[i + 1][0] <= lvl)
        if not is_leaf:
            continue
        joined = " ".join(pathstack[l] for l in range(0, lvl + 1) if l in pathstack)
        for name, pat in SUBSYSTEMS:
            if re.search(pat, joined):
                agg[name] += w
                break
        else:
            agg["other"] += w
    return total, agg


if __name__ == "__main__":
    total, agg = attribute(sys.argv[1])
    print(f"total samples: {total}")
    for k, v in sorted(agg.items(), key=lambda x: -x[1]):
        print(f"  {100 * v / total:6.2f}%  {k}")
