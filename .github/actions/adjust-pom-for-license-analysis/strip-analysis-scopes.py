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

Only <dependencies> blocks (top-level and inside <profiles>) are touched;
<dependencyManagement> is intentionally left alone as it declares versions
rather than real graph edges. The edit is applied to the ephemeral CI checkout
only and is idempotent.
"""

import sys
import xml.etree.ElementTree as ET

NAMESPACE = "http://maven.apache.org/POM/4.0.0"
EXCLUDED_SCOPES = {"test", "provided", "system"}

ET.register_namespace("", NAMESPACE)


def _q(tag: str) -> str:
    return f"{{{NAMESPACE}}}{tag}"


def _dependency_containers(root: ET.Element):
    """Yield every <dependencies> that contributes real graph edges."""
    top = root.find(_q("dependencies"))
    if top is not None:
        yield top
    profiles = root.find(_q("profiles"))
    if profiles is not None:
        for profile in profiles.findall(_q("profile")):
            deps = profile.find(_q("dependencies"))
            if deps is not None:
                yield deps


def strip_pom(path: str) -> int:
    tree = ET.parse(path)
    removed = 0
    for deps in _dependency_containers(tree.getroot()):
        for dep in list(deps.findall(_q("dependency"))):
            scope = dep.find(_q("scope"))
            if scope is not None and (scope.text or "").strip() in EXCLUDED_SCOPES:
                deps.remove(dep)
                removed += 1
    if removed:
        tree.write(path, encoding="UTF-8", xml_declaration=True)
    return removed


def main(argv: list[str]) -> int:
    files_touched = 0
    deps_removed = 0
    failures = 0
    for path in argv:
        try:
            removed = strip_pom(path)
        except Exception as exc:  # keep going; a single bad POM must not abort the sweep
            print(f"WARN could not process {path}: {exc}", file=sys.stderr)
            failures += 1
            continue
        if removed:
            files_touched += 1
            deps_removed += removed
    print(f"stripped {deps_removed} test/provided/system dependencies from {files_touched} pom.xml file(s)")
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
