#!/usr/bin/env python3
"""Audit Gradle catalog versions against Maven effective POMs.

This script compares versions from `gradle/libs.versions.toml` (plus hard-coded Gradle
coordinates with literal versions) against the Maven build, using `help:effective-pom`
for the POMs that are most likely to define or override dependency versions.

It is intended as a helper for the Gradle migration, where Maven remains the source of truth.

Usage:
  python scripts/audit-gradle-catalog-versions.py
  python scripts/audit-gradle-catalog-versions.py --json
  python scripts/audit-gradle-catalog-versions.py --refresh
"""

from __future__ import annotations

import argparse
import json
import logging
import re
import shutil
import subprocess
import sys
import tempfile
import tomllib
import xml.etree.ElementTree as ET
from collections import defaultdict
from dataclasses import asdict, dataclass
from pathlib import Path

POM_NS = {"m": "http://maven.apache.org/POM/4.0.0"}
ROOT = Path(__file__).resolve().parent.parent
CATALOG_PATH = ROOT / "gradle" / "libs.versions.toml"
MVNW = ROOT / "mvnw"
DEFAULT_CACHE_DIR = ROOT / "build" / "version-audit"
GRADLE_FILES = [
    *ROOT.rglob("build.gradle.kts"),
    *ROOT.rglob("settings.gradle.kts"),
]

# Plugin version aliases are not first-class libraries in the catalog. Add the few mappings we
# care about here so they can still be audited against Maven.
PLUGIN_COORDINATE_MAP = {
    "spring-boot": ("org.springframework.boot", "spring-boot"),
}

# Some multi-version setups are intentional or necessary during migration.
INTENTIONAL_MULTI_KEY_SUFFIXES = ("-x1",)


@dataclass(frozen=True)
class Coordinate:
    group: str
    artifact: str

    def gav(self, version: str) -> str:
        return f"{self.group}:{self.artifact}:{version}"

    def ga(self) -> str:
        return f"{self.group}:{self.artifact}"


@dataclass
class CatalogEntry:
    kind: str  # library | plugin | literal
    key: str
    coordinate: Coordinate
    version: str
    source: str
    version_key: str | None = None


@dataclass
class MavenOccurrence:
    version: str
    pom: str
    section: str


@dataclass
class AuditItem:
    coordinate: str
    catalog: list[dict]
    maven_versions: list[str]
    maven_occurrences: list[dict]
    status: str
    note: str | None = None


@dataclass
class SyncResult:
    updates: dict[str, str]
    warnings: list[str]


LITERAL_DEP_RE = re.compile(
    r"(?P<prefix>platform\(|enforcedPlatform\()?['\"](?P<group>[A-Za-z0-9_.-]+):(?P<artifact>[A-Za-z0-9_.-]+):(?P<version>[^'\"]+)['\"]\)?"
)


def run(cmd: list[str], *, cwd: Path | None = None, capture: bool = True) -> subprocess.CompletedProcess:
    return subprocess.run(
        cmd,
        cwd=cwd or ROOT,
        text=True,
        capture_output=capture,
        check=False,
    )


def load_catalog() -> list[CatalogEntry]:
    with CATALOG_PATH.open("rb") as f:
        data = tomllib.load(f)

    versions = data.get("versions", {})
    libraries = data.get("libraries", {})
    plugins = data.get("plugins", {})

    entries: list[CatalogEntry] = []

    for key, spec in libraries.items():
        module = spec.get("module")
        if not module:
            group = spec["group"]
            artifact = spec["name"]
        else:
            group, artifact = module.split(":", 1)

        version = spec.get("version")
        version_ref = spec.get("version.ref")
        if isinstance(version, dict):
            version_ref = version.get("ref", version_ref)
            version = version.get("require")
        if version is None and version_ref is not None:
            version = versions[version_ref]
        if version is None:
            continue

        entries.append(
            CatalogEntry(
                kind="library",
                key=key,
                coordinate=Coordinate(group, artifact),
                version=str(version),
                source="gradle/libs.versions.toml",
                version_key=version_ref,
            )
        )

    for key, spec in plugins.items():
        coord = PLUGIN_COORDINATE_MAP.get(key)
        if coord is None:
            continue
        version = spec.get("version")
        version_ref = spec.get("version.ref")
        if isinstance(version, dict):
            version_ref = version.get("ref", version_ref)
            version = version.get("require")
        if version is None and version_ref is not None:
            version = versions[version_ref]
        if version is None:
            continue
        entries.append(
            CatalogEntry(
                kind="plugin",
                key=key,
                coordinate=Coordinate(*coord),
                version=str(version),
                source="gradle/libs.versions.toml",
                version_key=version_ref,
            )
        )

    return entries


def scan_gradle_literals() -> list[CatalogEntry]:
    literals: list[CatalogEntry] = []
    seen: set[tuple[str, str, str, str]] = set()

    for path in GRADLE_FILES:
        rel = path.relative_to(ROOT).as_posix()
        text = path.read_text(encoding="utf-8")
        for match in LITERAL_DEP_RE.finditer(text):
            group = match.group("group")
            artifact = match.group("artifact")
            version = match.group("version")
            key = f"{group}:{artifact}:{version}"
            dedupe = (rel, group, artifact, version)
            if dedupe in seen:
                continue
            seen.add(dedupe)
            literals.append(
                CatalogEntry(
                    kind="literal",
                    key=key,
                    coordinate=Coordinate(group, artifact),
                    version=version,
                    source=rel,
                )
            )
    return literals


def raw_pom_candidates() -> list[Path]:
    """Pick POMs likely to define or override versions.

    We avoid running effective-pom for every module. Instead, we look for POMs with version
    properties, explicit dependency versions, or dependency management entries.
    """

    candidates: set[Path] = {ROOT / "parent" / "pom.xml"}

    for pom in ROOT.rglob("pom.xml"):
        try:
            root = ET.parse(pom).getroot()
        except ET.ParseError:
            continue

        props = root.find("m:properties", POM_NS)
        has_version_props = False
        if props is not None:
            for child in list(props):
                tag = child.tag.split("}", 1)[-1]
                if tag.startswith("version.") or tag.startswith("plugin.version."):
                    has_version_props = True
                    break

        has_explicit_dep_versions = False
        for dep in root.findall(".//m:dependency", POM_NS):
            ver = dep.find("m:version", POM_NS)
            if ver is not None and (ver.text or "").strip():
                has_explicit_dep_versions = True
                break

        if has_version_props or has_explicit_dep_versions:
            candidates.add(pom)

    return sorted(candidates)


def ensure_mvnw() -> None:
    if not MVNW.exists():
        raise SystemExit(f"Missing Maven wrapper: {MVNW}")


def effective_pom(pom: Path, cache_dir: Path, refresh: bool) -> Path:
    ensure_mvnw()
    cache_dir.mkdir(parents=True, exist_ok=True)
    rel = pom.relative_to(ROOT).as_posix().replace("/", "__")
    out = cache_dir / f"{rel}.effective.xml"
    if out.exists() and not refresh:
        return out

    with tempfile.NamedTemporaryFile(prefix="effective-pom-", suffix=".xml", delete=False) as tmp:
        tmp_path = Path(tmp.name)

    cmd = [str(MVNW), "-q", "-f", str(pom), "-N", "help:effective-pom", f"-Doutput={tmp_path}"]
    result = run(cmd)
    if result.returncode != 0:
        stderr = (result.stderr or "").strip()
        stdout = (result.stdout or "").strip()
        raise RuntimeError(
            f"Failed to build effective POM for {pom.relative_to(ROOT)}\nSTDOUT:\n{stdout}\nSTDERR:\n{stderr}"
        )

    shutil.move(tmp_path, out)
    return out


def collect_versions_from_effective_pom(effective: Path, coords: set[Coordinate]) -> dict[Coordinate, list[MavenOccurrence]]:
    root = ET.parse(effective).getroot()
    results: dict[Coordinate, list[MavenOccurrence]] = defaultdict(list)

    def consider(dep: ET.Element, section: str) -> None:
        gid = dep.find("m:groupId", POM_NS)
        aid = dep.find("m:artifactId", POM_NS)
        ver = dep.find("m:version", POM_NS)
        if gid is None or aid is None or ver is None:
            return
        coord = Coordinate(gid.text or "", aid.text or "")
        if coord not in coords:
            return
        results[coord].append(
            MavenOccurrence(
                version=ver.text or "",
                pom=effective.name.replace("__", "/").removesuffix(".effective.xml"),
                section=section,
            )
        )

    for dep in root.findall("./m:dependencyManagement/m:dependencies/m:dependency", POM_NS):
        consider(dep, "dependencyManagement")
    for dep in root.findall("./m:dependencies/m:dependency", POM_NS):
        consider(dep, "dependencies")

    return results


def audit(entries: list[CatalogEntry], maven_versions: dict[Coordinate, list[MavenOccurrence]]) -> list[AuditItem]:
    grouped_entries: dict[Coordinate, list[CatalogEntry]] = defaultdict(list)
    for entry in entries:
        grouped_entries[entry.coordinate].append(entry)

    items: list[AuditItem] = []
    for coord in sorted(grouped_entries, key=lambda c: c.ga()):
        catalog_entries = sorted(grouped_entries[coord], key=lambda e: (e.kind, e.key, e.source))
        occurrences = maven_versions.get(coord, [])
        maven_set = sorted({occ.version for occ in occurrences})
        catalog_set = sorted({entry.version for entry in catalog_entries})

        status = "exact"
        note = None

        if not occurrences:
            status = "unmapped"
            note = "No matching version found in scanned Maven effective POMs"
        elif len(catalog_set) == 1 and len(maven_set) == 1 and catalog_set[0] == maven_set[0]:
            status = "exact"
        elif set(catalog_set) == set(maven_set):
            status = "covered-multi"
            note = "Multiple Maven versions detected and all are represented in Gradle"
        elif len(catalog_entries) > 1 and all(entry.key.endswith(INTENTIONAL_MULTI_KEY_SUFFIXES) or not any(other.key == entry.key for other in catalog_entries[1:]) for entry in catalog_entries):
            status = "partial-multi"
            note = "Multiple Maven versions detected but Gradle does not cover the full set"
        elif any(version in maven_set for version in catalog_set):
            status = "partial-match"
            note = "At least one Gradle version matches, but Maven uses additional versions"
        else:
            status = "mismatch"
            note = "Gradle and Maven versions do not overlap"

        items.append(
            AuditItem(
                coordinate=coord.ga(),
                catalog=[asdict(entry) for entry in catalog_entries],
                maven_versions=maven_set,
                maven_occurrences=[asdict(occ) for occ in occurrences],
                status=status,
                note=note,
            )
        )

    return items


def parent_version(item: AuditItem) -> str | None:
    versions = {
        occ["version"]
        for occ in item.maven_occurrences
        if occ["pom"] == "parent/pom.xml" and occ["section"] == "dependencyManagement"
    }
    if len(versions) == 1:
        return next(iter(versions))
    return None


def plan_sync(items: list[AuditItem]) -> SyncResult:
    updates: dict[str, str] = {}
    warnings: list[str] = []

    for item in items:
        parent_managed_version = parent_version(item)
        if parent_managed_version is None:
            if item.status not in {"exact", "covered-multi"}:
                warnings.append(
                    f"{item.coordinate}: no single parent-managed version found; skipping"
                )
            continue

        catalog_entries = item.catalog
        has_literal = any(entry["kind"] == "literal" for entry in catalog_entries)
        has_x1 = any(
            entry["key"].endswith(INTENTIONAL_MULTI_KEY_SUFFIXES) for entry in catalog_entries
        )
        missing_version_key = any(
            entry["kind"] != "literal" and not entry.get("version_key")
            for entry in catalog_entries
        )

        if has_literal or has_x1 or missing_version_key:
            reasons = []
            if has_literal:
                reasons.append("hard-coded Gradle literals present")
            if has_x1:
                reasons.append("intentional x1 catalog entries present")
            if missing_version_key:
                reasons.append("non-ref catalog entries present")
            if item.status not in {"exact", "covered-multi"}:
                warnings.append(
                    f"{item.coordinate}: strange catalog shape ({'; '.join(reasons)}); skipping"
                )
            continue

        extra_versions = [
            version for version in item.maven_versions if version != parent_managed_version
        ]
        if extra_versions:
            warnings.append(
                f"{item.coordinate}: module-specific Maven overrides detected "
                f"({', '.join(extra_versions)}); syncing only parent-managed version {parent_managed_version}"
            )

        target_version_keys = {
            entry["version_key"]
            for entry in catalog_entries
            if entry["kind"] in {"library", "plugin"} and entry.get("version_key")
        }
        for version_key in sorted(target_version_keys):
            existing = updates.get(version_key)
            if existing is not None and existing != parent_managed_version:
                warnings.append(
                    f"version key {version_key}: conflicting target versions {existing} and {parent_managed_version}; skipping"
                )
                continue
            updates[version_key] = parent_managed_version

    return SyncResult(updates=updates, warnings=warnings)


def apply_version_updates(updates: dict[str, str]) -> list[str]:
    if not updates:
        return []

    text = CATALOG_PATH.read_text(encoding="utf-8")
    changed: list[str] = []

    for version_key, version in sorted(updates.items()):
        pattern = re.compile(
            rf'^(?P<indent>\s*){re.escape(version_key)}\s*=\s*"(?P<current>[^"]+)"\s*$',
            re.MULTILINE,
        )
        match = pattern.search(text)
        if match is None:
            raise RuntimeError(
                f"Could not find version key {version_key!r} in {CATALOG_PATH.relative_to(ROOT)}"
            )
        current = match.group("current")
        if current == version:
            continue
        text = pattern.sub(
            lambda m: f'{m.group("indent")}{version_key} = "{version}"',
            text,
            count=1,
        )
        changed.append(f"{version_key}: {current} -> {version}")

    if changed:
        CATALOG_PATH.write_text(text, encoding="utf-8")

    return changed


def summarize(items: list[AuditItem]) -> dict[str, int]:
    counts: dict[str, int] = defaultdict(int)
    for item in items:
        counts[item.status] += 1
    counts["total"] = len(items)
    return dict(sorted(counts.items()))


def print_text_report(items: list[AuditItem], candidates: list[Path]) -> None:
    counts = summarize(items)
    print("Gradle ↔ Maven version audit")
    print(f"Catalog file: {CATALOG_PATH.relative_to(ROOT)}")
    print(f"Maven POMs scanned via effective-pom: {len(candidates)}")
    print(f"Coordinates audited: {counts.get('total', 0)}")
    for key in ("exact", "covered-multi", "partial-match", "partial-multi", "mismatch", "unmapped"):
        if key in counts:
            print(f"  {key:14} {counts[key]}")

    interesting = [
        item for item in items if item.status not in {"exact", "covered-multi"}
    ]
    if not interesting:
        print("\nEverything scanned matches Maven.")
        return

    print("\nFindings:")
    for item in interesting:
        catalog_desc = ", ".join(f"{entry['key']}={entry['version']}" for entry in item.catalog)
        maven_desc = ", ".join(item.maven_versions) if item.maven_versions else "<none>"
        print(f"- [{item.status}] {item.coordinate}")
        print(f"    Gradle: {catalog_desc}")
        print(f"    Maven:  {maven_desc}")
        if item.note:
            print(f"    Note:   {item.note}")
        shown = 0
        seen_occ = set()
        for occ in item.maven_occurrences:
            line = f"{occ['pom']} ({occ['section']}) -> {occ['version']}"
            if line in seen_occ:
                continue
            seen_occ.add(line)
            print(f"      - {line}")
            shown += 1
            if shown == 5 and len(item.maven_occurrences) > shown:
                print(f"      - ... {len(item.maven_occurrences) - shown} more")
                break


def main() -> int:
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")

    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--json", action="store_true", help="Emit JSON instead of text")
    parser.add_argument(
        "--refresh",
        action="store_true",
        help="Rebuild cached effective POMs instead of reusing existing files",
    )
    parser.add_argument(
        "--sync",
        action="store_true",
        help="Synchronize simple parent-managed version keys in gradle/libs.versions.toml",
    )
    parser.add_argument(
        "--cache-dir",
        type=Path,
        default=DEFAULT_CACHE_DIR,
        help=f"Directory for effective POM cache (default: {DEFAULT_CACHE_DIR.relative_to(ROOT)})",
    )
    args = parser.parse_args()

    catalog_entries = load_catalog()
    literal_entries = scan_gradle_literals()
    entries = catalog_entries + literal_entries
    coords = {entry.coordinate for entry in entries}

    candidates = raw_pom_candidates()
    cache_dir = args.cache_dir.resolve()

    maven_versions: dict[Coordinate, list[MavenOccurrence]] = defaultdict(list)
    for pom in candidates:
        effective = effective_pom(pom, cache_dir, args.refresh)
        per_pom = collect_versions_from_effective_pom(effective, coords)
        for coord, occurrences in per_pom.items():
            maven_versions[coord].extend(occurrences)

    items = audit(entries, maven_versions)
    sync_result: SyncResult | None = None
    applied_updates: list[str] = []
    if args.sync:
        sync_result = plan_sync(items)
        for warning in sync_result.warnings:
            logging.warning(warning)
        applied_updates = apply_version_updates(sync_result.updates)
        if applied_updates:
            logging.info(
                "Updated %s version key(s) in %s",
                len(applied_updates),
                CATALOG_PATH.relative_to(ROOT),
            )
            for change in applied_updates:
                logging.info("  %s", change)
            catalog_entries = load_catalog()
            entries = catalog_entries + literal_entries
            items = audit(entries, maven_versions)

    result = {
        "summary": summarize(items),
        "candidates": [str(path.relative_to(ROOT)) for path in candidates],
        "items": [asdict(item) for item in items],
    }
    if sync_result is not None:
        result["sync"] = {
            "plannedUpdates": sync_result.updates,
            "appliedUpdates": applied_updates,
            "warnings": sync_result.warnings,
        }

    if args.json:
        json.dump(result, sys.stdout, indent=2, sort_keys=True)
        sys.stdout.write("\n")
    else:
        print_text_report(items, candidates)
        if args.sync:
            print("\nSync summary:")
            print(f"  planned updates: {len(sync_result.updates) if sync_result else 0}")
            print(f"  applied updates: {len(applied_updates)}")
            print(f"  warnings:        {len(sync_result.warnings) if sync_result else 0}")

    if args.sync:
        return 1 if sync_result and sync_result.warnings else 0

    failing_statuses = {"mismatch", "partial-match", "partial-multi"}
    return 1 if any(item.status in failing_statuses for item in items) else 0


if __name__ == "__main__":
    raise SystemExit(main())
