#!/usr/bin/env python3
"""Check each class under a `legacy` package equals its original at a base ref.

Usage: .agents/skills/migrate-to-msgpack-pojo/scripts/verify_legacy_copies.py [BASE_REF] [-v]
  BASE_REF  ref holding the originals (default: merge-base of HEAD and main).
            Pass `main` to compare against current main head.

Run from inside the repo. Legacy files = files added on HEAD vs BASE_REF whose
path contains `/legacy/`. Original path = same path without `legacy/`, with
src/test -> src/main (falls back to src/test).

Normalization applied to both sides before comparing (everything else must match
byte for byte):
  - javadoc blocks (/** ... */) removed
  - `.legacy` package segment removed in package/import lines and in fully
    qualified names
  - the `final` modifier on the top-level type removed
  - `public` modifier at the start of a line removed (widened for cross-package use)
  - imports of the original's own package removed (the original saw those classes
    without an import, the legacy copy sits one package deeper)
  - whitespace runs collapsed (adding `final`/imports makes the formatter rewrap lines)
"""
import re
import subprocess
import sys


def git(*args, check=True):
    r = subprocess.run(["git", *args], capture_output=True, check=False)
    if check and r.returncode:
        sys.exit(r.stderr.decode())
    return r


def normalize(src: str) -> str:
    pkg = re.search(r"^package ([\w.]+?)(?:\.legacy)?;", src, re.M)
    if pkg:
        src = re.sub(r"^import %s\.[A-Z]\w*;\n" % re.escape(pkg.group(1)), "", src, flags=re.M)
    src = re.sub(r"/\*\*.*?\*/\n?", "", src, flags=re.S)
    src = re.sub(r"\.legacy\b", "", src)
    src = re.sub(
        r"^((?:public\s+)?(?:abstract\s+)?)final\s+((?:class|record)\b)", r"\1\2", src, flags=re.M
    )
    src = re.sub(r"^(\s*)public\s+", r"\1", src, flags=re.M)
    return src


def squash(src: str) -> str:
    return re.sub(r"\s+", " ", src)


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("-")]
    verbose = "-v" in sys.argv
    base = args[0] if args else git("merge-base", "HEAD", "main").stdout.decode().strip()
    print(f"base: {base} ({git('rev-parse', '--short', base).stdout.decode().strip()})")

    added = git("diff", "--name-only", "--diff-filter=A", f"{base}...HEAD").stdout.decode().split()
    legacy = [p for p in added if "/legacy/" in p]
    ok, bad = 0, []
    for p in legacy:
        orig_main = p.replace("/legacy/", "/").replace("/src/test/", "/src/main/")
        orig_test = p.replace("/legacy/", "/")
        r = git("show", f"{base}:{orig_main}", check=False)
        if r.returncode:
            r = git("show", f"{base}:{orig_test}", check=False)
        if r.returncode:
            bad.append((p, "original not found at base"))
            continue
        a = normalize(r.stdout.decode())
        b = normalize(open(p, encoding="utf-8").read())
        if squash(a) == squash(b):
            ok += 1
        else:
            import difflib

            d = "".join(difflib.unified_diff(a.splitlines(True), b.splitlines(True), "base", "legacy"))
            bad.append((p, d))
    for p, d in bad:
        print(f"MISMATCH {p}")
        if verbose:
            print(d)
    print(f"{ok}/{len(legacy)} identical, {len(bad)} mismatched")
    sys.exit(1 if bad else 0)


main()
