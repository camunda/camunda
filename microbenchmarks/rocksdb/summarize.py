#!/usr/bin/env python3
#
# Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
# one or more contributor license agreements. See the NOTICE file distributed
# with this work for additional information regarding copyright ownership.
# Licensed under the Camunda License 1.0. You may not use this file
# except in compliance with the Camunda License 1.0.
#
# Summarizes windows.csv written by JobQueueWorkload: one row per label (and phase), computed over
# the steady-state windows (the second half of each run's "measure" phase by default) using the
# median across windows, which is robust against a single window hit by a flush or compaction.
#
# Usage: summarize.py windows.csv [--from-fraction 0.5]
import csv
import statistics
import sys
from collections import OrderedDict, defaultdict

path = sys.argv[1]
from_fraction = float(sys.argv[sys.argv.index("--from-fraction") + 1]) if "--from-fraction" in sys.argv else 0.5

rows = defaultdict(list)
order = OrderedDict()
with open(path) as f:
    for row in csv.DictReader(f):
        key = (row["label"], row["phase"])
        rows[key].append(row)
        order[key] = None


def med(values):
    values = [v for v in values if v is not None]
    return statistics.median(values) if values else float("nan")


def num(row, col):
    try:
        return float(row[col])
    except (KeyError, ValueError):
        return None


cols = [
    ("polls/s", lambda r: num(r, "polls_per_s")),
    ("jobs/s", lambda r: num(r, "jobs_per_s")),
    ("poll mean us", lambda r: num(r, "poll_mean_us")),
    ("poll p99 us", lambda r: num(r, "poll_p99_us")),
    ("iter create us", lambda r: num(r, "iter_create_mean_us")),
    ("get mean us", lambda r: num(r, "get_mean_us")),
    ("write mean us", lambda r: num(r, "write_mean_us")),
    ("deadline scan us", lambda r: num(r, "deadline_scan_mean_us")),
    ("skips/poll", lambda r: (num(r, "number_iter_skip") or 0) / max(1.0, num(r, "poll_n") or 1)),
    ("L0 files", lambda r: num(r, "num-files-at-level0")),
    ("SST deletes", lambda r: num(r, "sst_deletions")),
    ("mem deletes", lambda r: (num(r, "num-deletes-active-mem-table") or 0) + (num(r, "num-deletes-imm-mem-tables") or 0)),
    ("compact write MB/s", lambda r: (num(r, "compact_write_bytes") or 0) / 1e6 / 5),
]

print("| label | phase | windows | " + " | ".join(c for c, _ in cols) + " |")
print("|---|---|---|" + "---|" * len(cols))
for key in order:
    label, phase = key
    windows = rows[key]
    if phase == "measure":
        windows = windows[int(len(windows) * from_fraction):]
    values = [med([fn(r) for r in windows]) for _, fn in cols]
    print(f"| {label} | {phase} | {len(windows)} | " + " | ".join(f"{v:,.1f}" for v in values) + " |")
