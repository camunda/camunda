#!/usr/bin/env python3
"""
Compare packaged distributions produced by Gradle and Maven.

The original mode compares JAR names in a Gradle distTar with an exploded Maven
`dist/target/camunda-zeebe` directory:

    python3 compare-dist.py <gradle-tar.gz> <maven-dist-dir>

When both arguments are ZIP archives, the tool compares the versioned distribution
root and JAR names/versions under `lib/`; other file contents are intentionally ignored:

    python3 compare-dist.py <gradle-zip> <maven-zip>
"""

import re
import sys
import tarfile
from pathlib import Path
from zipfile import ZipFile


def strip_version(name: str) -> str:
    return re.sub(r"-[\d][\w.\-]*\.jar$", ".jar", name)


KOTLIN_MULTIPLATFORM_METADATA_VARIANTS = {
    "okhttp.jar": "okhttp-jvm.jar",
    "okio.jar": "okio-jvm.jar",
}


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


def archive_files(path: str) -> tuple[str, dict[str, bytes]]:
    with ZipFile(path) as archive:
        files = {
            name.rstrip("/"): archive.read(name)
            for name in archive.namelist()
            if not name.endswith("/")
        }

    roots = {name.split("/", 1)[0] for name in files}
    if len(roots) != 1:
        raise ValueError(f"expected one distribution root in {path}, found {sorted(roots)}")

    root = roots.pop()
    relative_files = {
        name.split("/", 1)[1] if "/" in name else "": content
        for name, content in files.items()
    }
    return root, relative_files


def jars_from_archive(files: dict[str, bytes]) -> dict[str, list[str]]:
    result: dict[str, list[str]] = {}
    for path in files:
        if path.startswith("lib/") and path.endswith(".jar"):
            name = path.removeprefix("lib/")
            base = strip_version(name)
            result.setdefault(base, []).append(name)
    return result


def ignore_maven_metadata_variants(
    gradle: dict[str, list[str]], maven: dict[str, list[str]]
) -> list[str]:
    ignored = []
    for metadata_variant, jvm_variant in KOTLIN_MULTIPLATFORM_METADATA_VARIANTS.items():
        if (
            metadata_variant in maven
            and metadata_variant not in gradle
            and jvm_variant in gradle
            and jvm_variant in maven
        ):
            ignored.append(maven[metadata_variant][0])
            del maven[metadata_variant]
    return ignored


def compare_archives(gradle_path: str, maven_path: str) -> int:
    gradle_root, gradle_files = archive_files(gradle_path)
    maven_root, maven_files = archive_files(maven_path)
    gradle = jars_from_archive(gradle_files)
    maven = jars_from_archive(maven_files)
    ignored_metadata_variants = ignore_maven_metadata_variants(gradle, maven)

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
    print(f"Distribution roots: Gradle={gradle_root}, Maven={maven_root}")
    print(f"Total JARs: Gradle={total_g}, Maven={total_m}")
    print(
        f"Artifact-level: {len(version_diffs)} version mismatches, "
        f"{len(gradle_only)} Gradle-only, {len(maven_only)} Maven-only"
    )
    if ignored_metadata_variants:
        print(f"Ignored Maven-only Kotlin metadata variants: {sorted(ignored_metadata_variants)}")

    if gradle_root != maven_root:
        print("VERSION MISMATCH: distribution root")
    if version_diffs:
        print("=== Version mismatches ===")
        for base, gv, mv in version_diffs:
            print(f"  {base}: Gradle={gv}, Maven={mv}")
    if gradle_only:
        print("=== Only in Gradle ===")
        for base, jars in gradle_only:
            print(f"  {jars}")
    if maven_only:
        print("=== Only in Maven ===")
        for base, jars in maven_only:
            print(f"  {jars}")

    if gradle_root != maven_root or version_diffs or gradle_only or maven_only:
        print("DIFFERENCES FOUND")
        return 2

    print("OK — no JAR version differences")
    return 0


def compare(gradle_path: str, maven_path: str) -> int:
    if gradle_path.lower().endswith(".zip") and maven_path.lower().endswith(".zip"):
        return compare_archives(gradle_path, maven_path)

    gradle = jars_from_tar(gradle_path)
    maven = jars_from_dir(maven_path)
    ignored_metadata_variants = ignore_maven_metadata_variants(gradle, maven)

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
    if ignored_metadata_variants:
        print(f"Ignored Maven-only Kotlin metadata variants: {sorted(ignored_metadata_variants)}")

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

    if version_diffs or gradle_only or maven_only:
        print("DIFFERENCES FOUND")
        return 2

    print("OK — no differences")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    sys.exit(compare(sys.argv[1], sys.argv[2]))
