"""Compare resolved third-party dependencies of a module between Gradle and Maven.

Maven remains the source of truth for the Gradle migration. This script resolves the
dependencies of a single module with both build tools and diffs the set of third-party
(non `io.camunda`) `group:artifact` coordinates, so you can spot dependencies that are
missing from — or extra in — the Gradle build.

It intentionally stays simple:
  * Third-party deps are compared by `group:artifact`. Internal deps (Maven `io.camunda:*`
    reactor modules vs Gradle `project :...`) are compared by name — this relies on the
    repo convention that a Gradle project name equals its Maven artifactId. If they ever
    diverge, fix the Gradle project name (this tool will flag it as a false diff).
  * Add `--versions` to also diff the resolved version of third-party coordinates present
    on both sides.

Scope mapping (Maven scope -> Gradle configuration):
  runtime -> runtimeClasspath      (Maven includeScope=runtime, i.e. compile + runtime)
  compile -> compileClasspath      (Maven includeScope=compile)
  test    -> testRuntimeClasspath  (Maven includeScope=test)

Usage:
  python scripts/compare-module-deps.py <gradle-project> [--scope runtime] [--versions]
  python scripts/compare-module-deps.py --dir clients/java
  python scripts/compare-module-deps.py --list           # list gradle-project -> dir map

Examples:
  python scripts/compare-module-deps.py camunda-client-java
  python scripts/compare-module-deps.py camunda-client-java --scope test --versions
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from pathlib import Path

# This script lives at .claude/skills/gradle-build-parity/. Walk back to the
# repository root instead of assuming the skill directory contains settings.gradle.kts.
REPO_ROOT = Path(__file__).resolve().parents[3]
SETTINGS = REPO_ROOT / "settings.gradle.kts"

# Maven scope -> (Maven includeScope, Gradle configuration)
SCOPES = {
    "runtime": ("runtime", "runtimeClasspath"),
    "compile": ("compile", "compileClasspath"),
    "test": ("test", "testRuntimeClasspath"),
}

INTERNAL_GROUP = "io.camunda"

# project(":name").projectDir = file("path")
_PROJECT_DIR_RE = re.compile(r'project\("(:[^"]+)"\)\.projectDir\s*=\s*file\("([^"]+)"\)')
# include(":name")
_INCLUDE_RE = re.compile(r'include\("(:[^"]+)"\)')


def gradle_project_dirs() -> dict[str, str]:
    """Map gradle project name (without leading ':') to its module directory."""
    text = SETTINGS.read_text()
    dirs: dict[str, str] = {}
    # default dir == project name for plain includes
    for name in _INCLUDE_RE.findall(text):
        dirs[name.lstrip(":")] = name.lstrip(":")
    # explicit projectDir overrides win
    for name, path in _PROJECT_DIR_RE.findall(text):
        dirs[name.lstrip(":")] = path
    return dirs


def run(cmd: list[str]) -> str:
    result = subprocess.run(cmd, cwd=REPO_ROOT, capture_output=True, text=True)
    if result.returncode != 0:
        sys.stderr.write(f"command failed: {' '.join(cmd)}\n{result.stdout}\n{result.stderr}\n")
        sys.exit(1)
    return result.stdout


# Maven:  "   group:artifact:jar:[classifier:]version:scope -- ..."
_MVN_RE = re.compile(r"^\s+([\w.-]+):([\w.-]+):[\w.-]+:.*?:(\w+)")


def maven_deps(module_dir: str, include_scope: str, self_name: str, reactor: set[str]):
    """Return (external {group:artifact -> version}, internal {artifactId}).

    Internal deps are `io.camunda:*` reactor modules (artifactId in `reactor`), keyed by
    artifactId (which equals the Gradle project name by repo convention); the module's own
    artifact is excluded. Other `io.camunda:*` artifacts (separately-released libs such as
    `camunda-security-library-*`) are treated as ordinary third-party deps.
    """
    out = run(["./mvnw", "dependency:list", "-pl", module_dir,
               f"-DincludeScope={include_scope}", "-o", "-B"])
    external: dict[str, str] = {}
    internal: set[str] = set()
    for line in out.splitlines():
        line = line.replace("[INFO]", "")
        m = _MVN_RE.match(line)
        if not m:
            continue
        group, artifact, _scope = m.groups()
        if group == INTERNAL_GROUP and artifact in reactor:
            if artifact != self_name:
                internal.add(artifact)
            continue
        # version is the field right before the scope; recover it robustly
        coord = line.strip().split(" ")[0]
        parts = coord.split(":")
        version = parts[-2]  # ...:version:scope
        external[f"{group}:{artifact}"] = version
    return external, internal


# Gradle tree lines: "+--- group:artifact:req -> res (*)" etc.
_GRADLE_RE = re.compile(r"---\s+([\w.-]+):([\w.-]+)(?::([\w.\-]+))?\s*(?:->\s*([\w.\-]+))?")
# Internal deps show as "+--- project :some-module"
# Gradle 9 renders project dependencies as `project ':module'`; older output used
# `project :module`. Accept both forms.
_GRADLE_PROJECT_RE = re.compile(r"---\s+project ['\"]?:([\w.-]+)['\"]?")


def gradle_deps(project: str, configuration: str):
    """Return (external {group:artifact -> version}, internal {project name})."""
    out = run(["./gradlew", f":{project}:dependencies",
               "--configuration", configuration, "-q"])
    external: dict[str, str] = {}
    internal: set[str] = set()
    for line in out.splitlines():
        stripped = line.strip()
        if stripped.endswith("(c)") or stripped.endswith("(n)"):
            # (c) = version constraint only, (n) = not resolved
            continue
        pm = _GRADLE_PROJECT_RE.search(line)
        if pm:
            internal.add(pm.group(1))
            continue
        m = _GRADLE_RE.search(line)
        if not m:
            continue
        group, artifact, req_ver, res_ver = m.groups()
        # Reactor modules appear as `project :name` (handled above); any io.camunda coord
        # here is a separately-released lib, so keep it as a third-party dep.
        # BOMs / platform imports aren't real jars and Maven never lists them
        if artifact == "bom" or artifact.endswith("-bom") or artifact.endswith("-dependencies"):
            continue
        external[f"{group}:{artifact}"] = res_ver or req_ver or "?"
    return external, internal


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__,
                                     formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("project", nargs="?", help="gradle project name (e.g. camunda-client-java)")
    parser.add_argument("--dir", help="module directory (e.g. clients/java); resolves the gradle project")
    parser.add_argument("--scope", choices=SCOPES, default="runtime")
    parser.add_argument("--versions", action="store_true", help="also diff resolved versions")
    parser.add_argument("--list", action="store_true", help="print gradle-project -> dir map and exit")
    args = parser.parse_args()

    dirs = gradle_project_dirs()

    if args.list:
        for name, path in sorted(dirs.items()):
            print(f"{name}\t{path}")
        return 0

    if args.dir:
        matches = [n for n, p in dirs.items() if p == args.dir.rstrip("/")]
        if not matches:
            sys.stderr.write(f"no gradle project maps to dir '{args.dir}'\n")
            return 1
        project = matches[0]
        module_dir = args.dir.rstrip("/")
    elif args.project:
        project = args.project
        if project not in dirs:
            sys.stderr.write(f"unknown gradle project '{project}'. Use --list to see options.\n")
            return 1
        module_dir = dirs[project]
    else:
        parser.error("provide a gradle project name or --dir")

    include_scope, configuration = SCOPES[args.scope]
    print(f"module: {project}  (dir: {module_dir})  scope: {args.scope}\n")

    mvn, mvn_int = maven_deps(module_dir, include_scope, self_name=project, reactor=set(dirs))
    grd, grd_int = gradle_deps(project, configuration)

    only_mvn = sorted(set(mvn) - set(grd))
    only_grd = sorted(set(grd) - set(mvn))
    common = sorted(set(mvn) & set(grd))
    int_only_mvn = sorted(mvn_int - grd_int)
    int_only_grd = sorted(grd_int - mvn_int)

    print(f"third-party deps: maven={len(mvn)} gradle={len(grd)} common={len(common)}")
    print(f"internal deps:    maven={len(mvn_int)} gradle={len(grd_int)} "
          f"common={len(mvn_int & grd_int)}\n")

    if only_mvn:
        print(f"MISSING in Gradle ({len(only_mvn)}) — third-party, Maven only:")
        for c in only_mvn:
            print(f"  - {c}  (maven {mvn[c]})")
        print()
    if only_grd:
        print(f"EXTRA in Gradle ({len(only_grd)}) — third-party, Gradle only:")
        for c in only_grd:
            print(f"  + {c}  (gradle {grd[c]})")
        print()
    if int_only_mvn:
        print(f"MISSING in Gradle ({len(int_only_mvn)}) — internal project, Maven only:")
        for c in int_only_mvn:
            print(f"  - project :{c}")
        print()
    if int_only_grd:
        print(f"EXTRA in Gradle ({len(int_only_grd)}) — internal project, Gradle only:")
        for c in int_only_grd:
            print(f"  + project :{c}")
        print()

    mismatches = [(c, mvn[c], grd[c]) for c in common if mvn[c] != grd[c]]
    if args.versions and mismatches:
        print(f"VERSION mismatches ({len(mismatches)}):")
        for c, mv, gv in mismatches:
            print(f"  ~ {c}  maven={mv}  gradle={gv}")
        print()

    ok = not (only_mvn or only_grd or int_only_mvn or int_only_grd)
    if args.versions:
        ok = ok and not mismatches
    print("OK — no differences" if ok else "DIFFERENCES FOUND")
    return 0 if ok else 2


if __name__ == "__main__":
    sys.exit(main())
