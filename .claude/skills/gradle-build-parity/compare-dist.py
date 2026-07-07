#!/usr/bin/env python3
"""
Compare JARs between a Gradle distTar and a Maven dist directory.

Usage:
    python3 compare-dist.py <gradle-tar.gz> <maven-dist-dir>

Example:
    python3 dist/compare-dist.py \
        dist/build/distributions/camunda-zeebe-*.tar.gz \
        dist/target/camunda-zeebe
"""

import re
import sys
import tarfile
from pathlib import Path


def strip_version(name: str) -> str:
    return re.sub(r"-[\d][\w.\-]*\.jar$", ".jar", name)


def jars_from_tar(path: str) -> dict[str, list[str]]:
    result: dict[str, list[str]] = {}
    with tarfile.open(path, "r:gz") as tf:
        for m in tf.getmembers():
            if "/lib/" in m.name and m.name.endswith(".jar"):
                name = m.name.split("/lib/", 1)[1]
                base = strip_version(name)
                result.setdefault(base, []).append(name)
    return result


def jars_from_dir(path: str) -> dict[str, list[str]]:
    result: dict[str, list[str]] = {}
    for f in Path(path).glob("lib/*.jar"):
        base = strip_version(f.name)
        result.setdefault(base, []).append(f.name)
    return result


def compare(gradle_path: str, maven_path: str) -> None:
    gradle = jars_from_tar(gradle_path)
    maven = jars_from_dir(maven_path)

    all_bases = sorted(set(gradle) | set(maven))

    version_diffs: list[tuple[str, list[str], list[str]]] = []
    gradle_only: list[tuple[str, list[str]]] = []
    maven_only: list[tuple[str, list[str]]] = []

    for base in all_bases:
        if base in gradle and base in maven:
            gv = sorted(gradle[base])
            mv = sorted(maven[base])
            if gv != mv:
                version_diffs.append((base, gv, mv))
        elif base in gradle:
            gradle_only.append((base, sorted(gradle[base])))
        else:
            maven_only.append((base, sorted(maven[base])))

    total_g = sum(len(v) for v in gradle.values())
    total_m = sum(len(v) for v in maven.values())
    print(f"Total JARs: Gradle={total_g}, Maven={total_m}")
    print(
        f"Artifact-level: {len(version_diffs)} version mismatches, "
        f"{len(gradle_only)} Gradle-only, {len(maven_only)} Maven-only"
    )

    if version_diffs:
        print("\n=== Version mismatches ===")
        for base, gv, mv in version_diffs:
            print(f"  G:{gv}")
            print(f"  M:{mv}")
            print()

    if gradle_only:
        print("=== Only in Gradle ===")
        for base, jars in gradle_only:
            print(f"  {jars}")

    if maven_only:
        print("=== Only in Maven ===")
        for base, jars in maven_only:
            print(f"  {jars}")


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    compare(sys.argv[1], sys.argv[2])
