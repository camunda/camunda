#!/usr/bin/env python3
"""Remove unused protocol model imports from adapter files."""
import re
import os
import sys

adapter_dir = "/Users/joshua.wulf/workspace/camunda/zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/controller/generated"

removed_total = 0

for fname in sorted(os.listdir(adapter_dir)):
    if not fname.startswith("Default") or not fname.endswith("ServiceAdapter.java"):
        continue
    fpath = os.path.join(adapter_dir, fname)
    with open(fpath) as f:
        content = f.read()

    lines = content.split("\n")
    body = "\n".join(l for l in lines if not l.strip().startswith("import "))

    new_lines = []
    removed = 0
    for line in lines:
        m = re.match(r"^import\s+io\.camunda\.gateway\.protocol\.model\.(\w+);", line.strip())
        if m:
            classname = m.group(1)
            if not re.search(r"\b" + re.escape(classname) + r"\b", body):
                removed += 1
                continue
        new_lines.append(line)

    if removed > 0:
        result = "\n".join(new_lines)
        result = re.sub(r"\n{3,}", "\n\n", result)
        with open(fpath, "w") as f:
            f.write(result)
        print(f"  {fname}: removed {removed} unused protocol imports")
        removed_total += removed

print(f"\nTotal: {removed_total} unused imports removed")
