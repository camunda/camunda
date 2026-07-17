#!/usr/bin/env python3
"""Compute a content-based dependency fingerprint for the Maven cache key.

Emits a single deterministic sha256 hex digest over the dependency-relevant
content of every tracked pom.xml in the reactor, plus the Maven wrapper /
extensions config. This replaces `hashFiles('**/pom.xml')` in
`.github/actions/setup-maven-cache/action.yaml`.

Why not hash raw pom bytes? Hashing every pom's raw bytes rotates the cache
key on ANY pom edit — even a leaf module's `<name>`, comment, or unrelated
build-config tweak that adds/removes zero jars. Each rotation forces an exact-
key miss and a cold Maven dependency-resolution run on `build-distball`
(~155s warm -> ~330s cold; the mechanism behind INC-5949). See issue #56950.

Why not just hash a curated subset of poms? This repo does NOT centralize all
version declarations in root/bom/parent poms: 18 leaf poms declare dependency
versions directly and 26 more define their own `*.version` properties. Because
`actions/cache` never re-saves on an exact key hit, a static narrowed file set
would let a leaf-pom version bump go undetected (key unchanged) -> false cache
hit -> new jars downloaded every run but never persisted -> perpetual partial
miss. Worse than the status quo.

So instead we extract, from EVERY pom, only the XML that actually determines
what Maven resolves/downloads — `<dependencies>`, `<dependencyManagement>`,
`<properties>`, `<build><plugins>` — canonicalise it (so pure reformats and
comment edits don't count) and hash it in a deterministic order. The digest
is stable unless something that changes resolution actually changes.

Determinism guarantees:
- poms enumerated via `git ls-files` and sorted (filesystem-order independent,
  and untracked/generated poms such as `.flattened-pom.xml` are excluded);
- Maven XML namespace stripped (localname compare);
- fixed section order per pom, with an empty-marker for absent sections;
- each block canonicalised with sorted attributes, stripped comments, and
  normalised whitespace;
- each block prefixed with its pom path so identical blocks in different
  modules do not collide.

Fails loud (non-zero exit) on any parse error — a silent wrong hash would
poison the cache for the whole fleet.

Usage:
    python3 .github/scripts/dep-fingerprint.py   # prints the hex digest
"""

import hashlib
import subprocess
import sys
import xml.etree.ElementTree as ET

# Dependency-relevant top-level sections, in a fixed hashing order. Each entry
# is a path of localnames from the <project> root to the subtree we extract.
SECTION_PATHS = (
    ("dependencies",),
    ("dependencyManagement",),
    ("properties",),
    ("build", "plugins"),
)

# Extra files (raw bytes) that also influence dependency resolution/download:
# committed extensions and the pinned Maven wrapper version. NOT hashed:
# `.mvn/maven.config` (generated per-run by the composite action -> would
# rotate the key every run).
EXTRA_FILES = (
    ".mvn/extensions.xml",
    ".mvn/wrapper/maven-wrapper.properties",
)

EMPTY_MARKER = b"\x00<absent>\x00"


def local(tag: str) -> str:
    """Strip any XML namespace, returning the bare local tag name."""
    return tag.rsplit("}", 1)[-1] if "}" in tag else tag


def canonicalize(elem: ET.Element) -> bytes:
    """Serialise an element deterministically.

    - attributes sorted by name;
    - comments/processing-instructions dropped;
    - all text/tail whitespace normalised away (only significant text kept),
      so a pure reindent/reformat of a pom does not change the digest.
    """
    parts: list[str] = []

    def emit(e: ET.Element) -> None:
        tag = local(e.tag)
        attrs = "".join(
            f" {local(k)}={v!r}" for k, v in sorted(e.attrib.items())
        )
        text = (e.text or "").strip()
        parts.append(f"<{tag}{attrs}>")
        if text:
            parts.append(text)
        for child in e:
            # ElementTree represents comments/PIs with a callable .tag; skip.
            if callable(child.tag):
                continue
            emit(child)
        parts.append(f"</{tag}>")

    emit(elem)
    return "".join(parts).encode("utf-8")


def find_child(parent: ET.Element, name: str) -> ET.Element | None:
    for child in parent:
        if not callable(child.tag) and local(child.tag) == name:
            return child
    return None


def extract_section(root: ET.Element, path: tuple[str, ...]) -> ET.Element | None:
    node: ET.Element | None = root
    for name in path:
        if node is None:
            return None
        node = find_child(node, name)
    return node


def list_poms() -> list[str]:
    out = subprocess.run(
        ["git", "ls-files", "pom.xml", "**/pom.xml"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout
    return sorted(line for line in out.splitlines() if line.strip())


def compute_fingerprint(poms: list[str], extra_files: tuple[str, ...]) -> str:
    """Return the sha256 hex digest over the given poms + extra files.

    `poms` is hashed in the exact order given, so the caller is responsible
    for sorting (`list_poms` does). Raises on any pom parse error.
    """
    digest = hashlib.sha256()

    for pom in poms:
        root = ET.parse(pom).getroot()
        digest.update(pom.encode("utf-8"))
        for path in SECTION_PATHS:
            digest.update(b"\x01" + "/".join(path).encode("utf-8"))
            section = extract_section(root, path)
            digest.update(EMPTY_MARKER if section is None else canonicalize(section))

    for extra in extra_files:
        digest.update(b"\x02" + extra.encode("utf-8"))
        try:
            with open(extra, "rb") as fh:
                digest.update(fh.read())
        except FileNotFoundError:
            digest.update(EMPTY_MARKER)

    return digest.hexdigest()


def main() -> int:
    try:
        print(compute_fingerprint(list_poms(), EXTRA_FILES))
    except (ET.ParseError, OSError) as exc:
        print(f"error: failed to compute dependency fingerprint: {exc}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
