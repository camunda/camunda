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

Only <dependencies> blocks (top-level and inside <profiles>) are edited.
<dependencyManagement> entries are never added, removed, or modified -- on
their own they are just version/scope templates, not graph edges -- but a
<dependencies> entry that omits <scope> inherits its scope from a matching
<dependencyManagement> entry (its own, or an ancestor POM's), so this script
first scans every POM's <dependencyManagement> to build a best-effort
groupId:artifactId -> scope map, then uses it to resolve scope-less entries.
This is a heuristic, not real Maven inheritance resolution (it does not
follow parent/import chains), so if two unrelated POMs manage the same
coordinate under different scopes, whichever is scanned last wins. That is an
acceptable approximation here: this script only ever touches an ephemeral CI
checkout used solely to generate FOSSA's license graph, never a real build.

The edit is applied to the ephemeral CI checkout only and is idempotent.
"""

import sys
from pathlib import Path
from xml.etree import ElementTree as ET

NAMESPACE = "http://maven.apache.org/POM/4.0.0"
EXCLUDED_SCOPES = {"test", "provided", "system"}

ET.register_namespace("", NAMESPACE)


def _q(tag: str) -> str:
    return f"{{{NAMESPACE}}}{tag}"


def _dependency_containers(root: ET.Element, parent_tag: str):
    """Yield every <dependencies> found directly under `parent_tag`, at the top level and inside <profiles>."""
    if parent_tag == "dependencies":
        top = root.find(_q("dependencies"))
    else:
        management = root.find(_q(parent_tag))
        top = management.find(_q("dependencies")) if management is not None else None
    if top is not None:
        yield top
    profiles = root.find(_q("profiles"))
    if profiles is not None:
        for profile in profiles.findall(_q("profile")):
            if parent_tag == "dependencies":
                deps = profile.find(_q("dependencies"))
            else:
                management = profile.find(_q(parent_tag))
                deps = management.find(_q("dependencies")) if management is not None else None
            if deps is not None:
                yield deps


def _coordinate(dep: ET.Element):
    group = dep.find(_q("groupId"))
    artifact = dep.find(_q("artifactId"))
    if group is None or artifact is None or not (group.text and artifact.text):
        return None
    return (group.text.strip(), artifact.text.strip())


def scan_managed_scopes(trees: dict) -> dict:
    """Build a best-effort {(groupId, artifactId): scope} map from every <dependencyManagement> block."""
    managed = {}
    for tree in trees.values():
        for deps in _dependency_containers(tree.getroot(), "dependencyManagement"):
            for dep in deps.findall(_q("dependency")):
                coordinate = _coordinate(dep)
                scope = dep.find(_q("scope"))
                if coordinate and scope is not None and (scope.text or "").strip():
                    managed[coordinate] = scope.text.strip()
    return managed


def strip_pom(tree: ET.ElementTree, managed_scopes: dict) -> int:
    removed = 0
    for deps in _dependency_containers(tree.getroot(), "dependencies"):
        for dep in list(deps.findall(_q("dependency"))):
            scope = dep.find(_q("scope"))
            if scope is not None:
                effective_scope = (scope.text or "").strip()
            else:
                effective_scope = managed_scopes.get(_coordinate(dep))
            if effective_scope in EXCLUDED_SCOPES:
                deps.remove(dep)
                removed += 1
    return removed


def find_poms(root: str):
    return [p for p in Path(root).rglob("pom.xml") if "target" not in p.parts]


def main(argv: list[str]) -> int:
    root = argv[0] if argv else "."
    paths = find_poms(root)

    trees = {}
    failures = 0
    for path in paths:
        try:
            trees[path] = ET.parse(path)
        except Exception as exc:  # keep going; a single bad POM must not abort the sweep
            print(f"WARN could not parse {path}: {exc}", file=sys.stderr)
            failures += 1

    managed_scopes = scan_managed_scopes(trees)

    files_touched = 0
    deps_removed = 0
    for path, tree in trees.items():
        removed = strip_pom(tree, managed_scopes)
        if removed:
            tree.write(path, encoding="UTF-8", xml_declaration=True)
            files_touched += 1
            deps_removed += removed

    print(f"stripped {deps_removed} test/provided/system dependencies from {files_touched} pom.xml file(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
