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

from collections.abc import Iterable
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


PATCH_VERSION_RE = re.compile(r"-(\d+)\.(\d+)\.(\d+)\.jar$")


def is_patch_only_version_difference(gradle: list[str], maven: list[str]) -> bool:
    """Return whether matching JARs differ only in their numeric patch version."""
    if len(gradle) != len(maven):
        return False

    for gradle_jar, maven_jar in zip(sorted(gradle), sorted(maven)):
        gradle_version = PATCH_VERSION_RE.search(gradle_jar)
        maven_version = PATCH_VERSION_RE.search(maven_jar)
        if gradle_version is None or maven_version is None:
            return False
        if gradle_jar[: gradle_version.start()] != maven_jar[: maven_version.start()]:
            return False
        if gradle_version.groups()[:2] != maven_version.groups()[:2]:
            return False

    return True


def jars_from_tar(path: str) -> dict[str, list[str]]:
    result: dict[str, list[str]] = {}
    with tarfile.open(path, "r:gz") as tf:
        for member in tf.getmembers():
            if "/lib/" in member.name and member.name.endswith(".jar"):
                name = member.name.split("/lib/", 1)[1]
                base = strip_version(name)
                result.setdefault(base, []).append(name)
    return result


def jars_from_dir(path: str) -> dict[str, list[str]]:
    result: dict[str, list[str]] = {}
    for file in Path(path).glob("lib/*.jar"):
        base = strip_version(file.name)
        result.setdefault(base, []).append(file.name)
    return result


def archive_inventory(path: str) -> tuple[str, list[str]]:
    """Return the archive root and member names without reading member payloads."""
    with ZipFile(path) as archive:
        files = [name.rstrip("/") for name in archive.namelist() if not name.endswith("/")]

    roots = {name.split("/", 1)[0] for name in files}
    if len(roots) != 1:
        raise ValueError(f"expected one distribution root in {path}, found {sorted(roots)}")

    root = roots.pop()
    relative_files = [name.split("/", 1)[1] if "/" in name else "" for name in files]
    return root, relative_files


def jars_from_archive(files: Iterable[str]) -> dict[str, list[str]]:
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


def compare_inventories(
    gradle: dict[str, list[str]],
    maven: dict[str, list[str]],
    gradle_root: str | None = None,
    maven_root: str | None = None,
) -> int:
    ignored_metadata_variants = ignore_maven_metadata_variants(gradle, maven)

    all_bases = sorted(set(gradle) | set(maven))
    version_diffs: list[tuple[str, list[str], list[str]]] = []
    ignored_patch_diffs: list[tuple[str, list[str], list[str]]] = []
    gradle_only: list[tuple[str, list[str]]] = []
    maven_only: list[tuple[str, list[str]]] = []

    for base in all_bases:
        if base in gradle and base in maven:
            gv = sorted(gradle[base])
            mv = sorted(maven[base])
            if gv != mv:
                difference = (base, gv, mv)
                if is_patch_only_version_difference(gv, mv):
                    ignored_patch_diffs.append(difference)
                else:
                    version_diffs.append(difference)
        elif base in gradle:
            gradle_only.append((base, sorted(gradle[base])))
        else:
            maven_only.append((base, sorted(maven[base])))

    total_g = sum(len(values) for values in gradle.values())
    total_m = sum(len(values) for values in maven.values())
    if gradle_root is not None and maven_root is not None:
        print(f"Distribution roots: Gradle={gradle_root}, Maven={maven_root}")
    print(f"Total JARs: Gradle={total_g}, Maven={total_m}")
    total_version_diffs = len(version_diffs) + len(ignored_patch_diffs)
    ignored_suffix = (
        f" ({len(ignored_patch_diffs)} patch-only ignored)" if ignored_patch_diffs else ""
    )
    print(
        f"Artifact-level: {total_version_diffs} version mismatches{ignored_suffix}, "
        f"{len(gradle_only)} Gradle-only, {len(maven_only)} Maven-only"
    )
    if ignored_metadata_variants:
        print(f"Ignored Maven-only Kotlin metadata variants: {sorted(ignored_metadata_variants)}")

    if gradle_root is not None and maven_root is not None and gradle_root != maven_root:
        print("VERSION MISMATCH: distribution root")
    if ignored_patch_diffs:
        print("=== Ignored patch-only version mismatches ===")
        for base, gv, mv in ignored_patch_diffs:
            print(f"  {base}: Gradle={gv}, Maven={mv}")
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

    differences = version_diffs or gradle_only or maven_only
    if gradle_root is not None and maven_root is not None:
        differences = differences or gradle_root != maven_root
    if differences:
        print("DIFFERENCES FOUND")
        return 2

    print("OK — no blocking JAR differences")
    return 0


def compare_archives(gradle_path: str, maven_path: str) -> int:
    gradle_root, gradle_files = archive_inventory(gradle_path)
    maven_root, maven_files = archive_inventory(maven_path)
    return compare_inventories(
        jars_from_archive(gradle_files),
        jars_from_archive(maven_files),
        gradle_root,
        maven_root,
    )


def compare(gradle_path: str, maven_path: str) -> int:
    if gradle_path.lower().endswith(".zip") and maven_path.lower().endswith(".zip"):
        return compare_archives(gradle_path, maven_path)

    return compare_inventories(jars_from_tar(gradle_path), jars_from_dir(maven_path))


if __name__ == "__main__":
    if len(sys.argv) != 3:
        print(__doc__)
        sys.exit(1)
    sys.exit(compare(sys.argv[1], sys.argv[2]))
