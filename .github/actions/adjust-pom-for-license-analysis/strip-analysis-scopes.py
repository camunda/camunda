#!/usr/bin/env python3
"""Strip non-shipped dependency scopes from Maven POMs before FOSSA analysis.

FOSSA's Maven strategy runs `depgraph:aggregate` with
`-DmergeScopes -DrepeatTransitiveDependenciesInTextGraph=true`, which folds
test/provided/system dependencies into the dependency graph and repeats every
transitive dependency under every path. On a monorepo the size of this one the
resulting text graph can exceed the JVM's maximum array length (~2 GiB),
crashing the analyzer with `Required array length ... is too large ->
OutOfMemoryError` (see the FOSSA `check-licenses` workflow).

These scopes are not part of any shipped artifact and are already listed under
`maven.scope-exclude` in `.fossa.yml`, so they are dropped from FOSSA's results
anyway -- but that filter is applied after the graph is generated, too late to
prevent the crash. Removing them from the POMs up front keeps the generated
graph small and deterministic while producing identical license results.

Scope resolution
----------------
Only <dependencies> blocks (top-level and inside <profiles>) are ever edited.
<dependencyManagement> entries are never added, removed, or modified: on their
own they are version/scope templates, not graph edges. But a <dependencies>
entry that omits <scope> inherits its scope from a matching
<dependencyManagement> entry, so this script first scans every POM's
<dependencyManagement> to build a best-effort scope map, then uses it to
resolve the effective scope of scope-less entries.

The map is keyed by (groupId, artifactId, type, classifier) -- the same
coordinate Maven uses to match a dependency to its management entry -- so a
managed `test-jar` never leaks its scope onto a plain `jar`, etc.

This is a heuristic, not full Maven resolution: it does not follow the exact
parent/import inheritance order. To stay safe it *fails closed*:

* a coordinate managed with a single, consistent scope -> that scope is used;
* a coordinate managed under two or more DIFFERENT scopes anywhere in the repo
  -> treated as unknown, so a scope-less entry for it is NEVER stripped;
* a coordinate not managed anywhere -> unknown -> never stripped
  (Maven's default scope is `compile`, which we must keep).

In other words the only dependencies removed are those we can positively prove
are test/provided/system -- when in doubt we keep the dependency. Removing a
shipped (compile/runtime) dependency would be a license-analysis false
negative, which must never happen; leaving an extra test dependency in only
makes the graph marginally larger, which is harmless.

The edit is applied to the ephemeral CI checkout only and is idempotent.

Usage:
    strip-analysis-scopes.py [ROOT]            # edit POMs in place under ROOT (default ".")
    strip-analysis-scopes.py --dry-run [ROOT]  # print the removal plan as JSON, change nothing
"""

import json
import sys
from pathlib import Path
from xml.etree import ElementTree as ET

NAMESPACE = "http://maven.apache.org/POM/4.0.0"
EXCLUDED_SCOPES = {"test", "provided", "system"}
DEFAULT_TYPE = "jar"

ET.register_namespace("", NAMESPACE)


def _q(tag: str) -> str:
    return f"{{{NAMESPACE}}}{tag}"


def _text(element, tag, default=None):
    child = element.find(_q(tag))
    if child is None or child.text is None:
        return default
    return child.text.strip()


def _coordinate(dep: ET.Element):
    """(groupId, artifactId, type, classifier) -- Maven's dependency management key."""
    group = _text(dep, "groupId")
    artifact = _text(dep, "artifactId")
    if not group or not artifact:
        return None
    return (group, artifact, _text(dep, "type", DEFAULT_TYPE), _text(dep, "classifier", ""))


def _dependency_blocks(root: ET.Element, parent_tag: str):
    """Yield every <dependencies> under `parent_tag`, at the top level and inside <profiles>."""

    def under(node):
        if parent_tag == "dependencies":
            return node.find(_q("dependencies"))
        management = node.find(_q(parent_tag))
        return management.find(_q("dependencies")) if management is not None else None

    top = under(root)
    if top is not None:
        yield top
    profiles = root.find(_q("profiles"))
    if profiles is not None:
        for profile in profiles.findall(_q("profile")):
            block = under(profile)
            if block is not None:
                yield block


_CONFLICT = object()  # sentinel: coordinate managed under conflicting scopes -> never infer


def build_managed_scopes(trees) -> dict:
    """Best-effort {coordinate: scope} from every <dependencyManagement>, failing closed on conflicts."""
    managed: dict = {}
    for tree in trees.values():
        for block in _dependency_blocks(tree.getroot(), "dependencyManagement"):
            for dep in block.findall(_q("dependency")):
                coordinate = _coordinate(dep)
                scope = _text(dep, "scope")
                if not coordinate or not scope:
                    continue
                existing = managed.get(coordinate, None)
                if existing is None:
                    managed[coordinate] = scope
                elif existing is not _CONFLICT and existing != scope:
                    managed[coordinate] = _CONFLICT  # fail closed
    return managed


def _effective_scope(dep: ET.Element, managed: dict):
    """Explicit <scope> wins; otherwise inherit from management (unless unknown/conflicting)."""
    explicit = _text(dep, "scope")
    if explicit is not None:
        return explicit, "explicit"
    inherited = managed.get(_coordinate(dep))
    if inherited is None or inherited is _CONFLICT:
        return None, "unknown"
    return inherited, "inherited"


def plan_removals(trees, managed: dict):
    """Return {path: [dep_elements_to_remove]} and a JSON-serialisable report."""
    removals: dict = {}
    report = []
    for path, tree in trees.items():
        for block in _dependency_blocks(tree.getroot(), "dependencies"):
            for dep in block.findall(_q("dependency")):
                scope, source = _effective_scope(dep, managed)
                if scope in EXCLUDED_SCOPES:
                    removals.setdefault(path, []).append((block, dep))
                    group, artifact, dtype, classifier = _coordinate(dep)
                    report.append(
                        {
                            "path": str(path),
                            "groupId": group,
                            "artifactId": artifact,
                            "type": dtype,
                            "classifier": classifier,
                            "scope": scope,
                            "source": source,
                        }
                    )
    return removals, report


def main(argv) -> int:
    dry_run = "--dry-run" in argv
    positional = [a for a in argv if not a.startswith("--")]
    root = positional[0] if positional else "."

    paths = [p for p in Path(root).rglob("pom.xml") if "target" not in p.parts]

    trees = {}
    failures = 0
    for path in paths:
        try:
            trees[path] = ET.parse(path)
        except Exception as exc:  # keep going; a single bad POM must not abort the sweep
            print(f"WARN could not parse {path}: {exc}", file=sys.stderr)
            failures += 1

    managed = build_managed_scopes(trees)
    removals, report = plan_removals(trees, managed)

    if dry_run:
        json.dump(report, sys.stdout, indent=2, sort_keys=True)
        sys.stdout.write("\n")
        return 1 if failures else 0

    files_touched = 0
    deps_removed = 0
    for path, pairs in removals.items():
        for block, dep in pairs:
            block.remove(dep)
        trees[path].write(path, encoding="UTF-8", xml_declaration=True)
        files_touched += 1
        deps_removed += len(pairs)

    print(f"stripped {deps_removed} test/provided/system dependencies from {files_touched} pom.xml file(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
