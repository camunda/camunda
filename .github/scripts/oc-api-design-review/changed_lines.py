#!/usr/bin/env python3
"""List every line a pull request adds, with its line number in the head file.

    python3 changed_lines.py --diff <pr.diff> --out <changed-lines.txt>

Reads the pull request diff as GitHub serves it and writes one line per added line as
`path:line: text`, so the reviewer gets the exact line for an inline comment without mapping the
hunks itself. Removed lines are not counted and context lines advance the counter without being
listed. Deleted files and binary files give no lines. An empty diff, which is what GitHub serves for
very large pull requests, gives an empty file.
"""

import argparse
import re

HUNK = re.compile(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@")


def list_changed_lines(diff_path, out_path):
    path = None
    line = 0
    in_hunk = False
    with open(diff_path, errors="replace") as diff, \
         open(out_path, "w") as out:
        for raw in diff:
            raw = raw.rstrip("\n")
            if raw.startswith("diff --git "):
                path, in_hunk = None, False
            elif not in_hunk and raw.startswith("+++ "):
                target = raw[4:]
                path = target[2:] if target.startswith("b/") else None
            elif (m := HUNK.match(raw)):
                line, in_hunk = int(m.group(1)), True
            elif not in_hunk or path is None or raw.startswith("\\"):
                continue
            elif raw.startswith("+"):
                out.write(f"{path}:{line}: {raw[1:]}\n")
                line += 1
            elif raw.startswith(" "):
                line += 1


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__.split("\n", 1)[0])
    parser.add_argument("--diff", required=True, help="the pull request diff to read")
    parser.add_argument("--out", required=True, help="the file to write the changed lines to")
    args = parser.parse_args(argv)
    list_changed_lines(args.diff, args.out)


if __name__ == "__main__":
    main()
