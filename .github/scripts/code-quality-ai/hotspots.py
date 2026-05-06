#!/usr/bin/env python3
"""Hotspot identification for the code quality AI pipeline.

For the hackday MVP this returns a hardcoded list. The interface mirrors
what a real code-maat integration would expose so the implementation can
be swapped in later without changing callers.
"""
from __future__ import annotations

import argparse
import json
import sys

# Replace with code-maat output once Stage 1 integration lands.
_HACKDAY_HOTSPOTS: list[str] = [
    "zeebe/engine",
]


def get_hotspots(top_n: int | None = None) -> list[str]:
    """Return hotspot paths (modules or files) in priority order."""
    if top_n is None:
        return list(_HACKDAY_HOTSPOTS)
    return _HACKDAY_HOTSPOTS[:top_n]


def _main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--top", type=int, default=None,
        help="Limit output to top N hotspots.",
    )
    parser.add_argument("--format", choices=("lines", "json"), default="lines")
    args = parser.parse_args(argv)

    hotspots = get_hotspots(args.top)
    if args.format == "json":
        json.dump(hotspots, sys.stdout)
        sys.stdout.write("\n")
    else:
        for path in hotspots:
            print(path)
    return 0


if __name__ == "__main__":
    sys.exit(_main())
