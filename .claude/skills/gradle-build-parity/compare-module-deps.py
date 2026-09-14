"""Compare resolved third-party dependencies of a module between Gradle and Maven.

Maven remains the source of truth for the Gradle migration. This script resolves the
dependencies of a single module with both build tools and diffs the set of third-party
(non-reactor) `group:artifact` coordinates, so you can spot dependencies that are missing
from — or extra in — the Gradle build.

It intentionally stays simple:
  * Third-party deps are compared by `group:artifact`. Internal deps (Maven reactor modules
    vs Gradle `project :...`) are compared by name — this relies on the repo convention that a
    Gradle project name equals its Maven artifactId. If they ever diverge, fix the Gradle project
    name (this tool will flag it as a false diff).
  * Add `--versions` to also diff the resolved version of third-party coordinates present
    on both sides.

The comparison uses resolved dependency graphs, including transitive dependencies; it does
not compare only the dependencies declared directly in each build file:
  * `MISSING in Gradle` means Maven resolved the coordinate for the selected scope, but Gradle
    did not resolve it on the corresponding classpath/configuration.
  * `EXTRA in Gradle` means Gradle resolved the coordinate, but Maven did not resolve it for
    the selected scope.
  * These messages describe the declaring module's resolved classpaths. They do not by
    themselves prove the published consumer API boundary is correct; use a consumer compile
    or publication/variant metadata check for that.

Scope mapping (Maven scope -> Gradle configuration):
  runtime -> runtimeClasspath      (Maven includeScope=runtime, i.e. compile + runtime)
  compile -> compileClasspath      (Maven includeScope=compile)
  test    -> testRuntimeClasspath  (Maven includeScope=test)

Usage:
  python .claude/skills/gradle-build-parity/compare-module-deps.py <gradle-project> [--scope runtime] [--versions]
  python .claude/skills/gradle-build-parity/compare-module-deps.py --dir clients/java
  python .claude/skills/gradle-build-parity/compare-module-deps.py --all --json
  python .claude/skills/gradle-build-parity/compare-module-deps.py --list           # list gradle-project -> dir map

Examples:
  python .claude/skills/gradle-build-parity/compare-module-deps.py camunda-client-java
  python .claude/skills/gradle-build-parity/compare-module-deps.py camunda-client-java --scope test --versions
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path
from xml.etree import ElementTree

# This script lives at .claude/skills/gradle-build-parity/. Walk back to the
# repository root instead of assuming the skill directory contains build.gradle.kts.
REPO_ROOT = Path(__file__).resolve().parents[3]

# Maven scope -> (Maven includeScope, Gradle configuration)
SCOPES = {
    "runtime": ("runtime", "runtimeClasspath"),
    "compile": ("compile", "compileClasspath"),
    "test": ("test", "testRuntimeClasspath"),
}

MAVEN_NAMESPACE = "http://maven.apache.org/POM/4.0.0"


class CommandError(RuntimeError):
    def __init__(self, command: list[str], returncode: int, stdout: str, stderr: str):
        self.command = command
        self.returncode = returncode
        self.stdout = stdout
        self.stderr = stderr
        super().__init__(f"command failed with exit code {returncode}: {' '.join(command)}")


class ProjectError(RuntimeError):
    pass


def gradle_project_dirs() -> dict[str, str]:
    """Ask Gradle for the active project-to-directory mapping."""
    out = run(
        [
            "./gradlew",
            "--no-daemon",
            "--console=plain",
            "--quiet",
            "printGradleProjectInventory",
        ]
    )
    dirs: dict[str, str] = {}
    for line in out.splitlines():
        if not line.strip():
            continue
        try:
            name, path = line.split("\t", 1)
        except ValueError as error:
            raise RuntimeError(f"invalid Gradle project inventory line: {line!r}") from error
        dirs[name] = path
    return dirs


def run(cmd: list[str]) -> str:
    result = subprocess.run(cmd, cwd=REPO_ROOT, capture_output=True, text=True)
    if result.returncode != 0:
        raise CommandError(cmd, result.returncode, result.stdout, result.stderr)
    return result.stdout


def maven_project_dirs() -> dict[str, str]:
    """Discover Maven reactor projects by walking module declarations in all POMs."""
    def tag(name: str) -> str:
        return f"{{{MAVEN_NAMESPACE}}}{name}"

    pending = [REPO_ROOT / "pom.xml"]
    projects: dict[str, str] = {}
    visited: set[Path] = set()

    while pending:
        pom_path = pending.pop()
        pom_path = pom_path.resolve()
        if pom_path in visited:
            continue
        visited.add(pom_path)
        if not pom_path.is_file():
            raise ProjectError(f"Maven module POM does not exist: {pom_path}")

        root = ElementTree.parse(pom_path).getroot()
        artifact_id = root.findtext(tag("artifactId"))
        if not artifact_id:
            raise ProjectError(f"Maven POM has no artifactId: {pom_path}")
        directory = pom_path.parent.relative_to(REPO_ROOT).as_posix() or "."
        previous = projects.get(artifact_id)
        if previous is not None and previous != directory:
            raise ProjectError(
                f"Maven artifactId {artifact_id!r} is declared in both {previous!r} and {directory!r}"
            )
        projects[artifact_id] = directory

        module_containers = []
        modules = root.find(tag("modules"))
        if modules is not None:
            module_containers.append(modules)
        for profile in root.findall(f"{tag('profiles')}/{tag('profile')}"):
            profile_modules = profile.find(tag("modules"))
            if profile_modules is not None:
                module_containers.append(profile_modules)

        for container in module_containers:
            for module in container.findall(tag("module")):
                module_path = (pom_path.parent / (module.text or "").strip()).resolve()
                child_pom = module_path if module_path.name == "pom.xml" else module_path / "pom.xml"
                pending.append(child_pom)

    return projects


def run_maven_dependency_list(module_dir: str, include_scope: str) -> str:
    """Resolve Maven deps offline first, then fall back to online if the local cache is incomplete."""
    base_cmd = [
        "./mvnw",
        "dependency:list",
        "-pl",
        module_dir,
        f"-DincludeScope={include_scope}",
        "-B",
    ]
    offline_cmd = [*base_cmd, "-o"]

    offline_result = subprocess.run(offline_cmd, cwd=REPO_ROOT, capture_output=True, text=True)
    if offline_result.returncode == 0:
        return offline_result.stdout

    online_result = subprocess.run(base_cmd, cwd=REPO_ROOT, capture_output=True, text=True)
    if online_result.returncode == 0:
        sys.stderr.write("offline Maven dependency resolution failed; retrying without -o\n")
        return online_result.stdout

    raise CommandError(
        base_cmd,
        online_result.returncode,
        online_result.stdout,
        online_result.stderr,
    )


# Maven:  "   group:artifact:jar:[classifier:]version:scope -- ..."
_MVN_RE = re.compile(r"^\s+([\w.-]+):([\w.-]+):[\w.-]+:.*?:(\w+)")
_MVN_MODULE_RE = re.compile(
    r"(?:maven-dependency-plugin|dependency):[^\s]+:list\b.*?@\s+([^\s]+)\s+---"
)


def parse_maven_dependency_lines(
    lines: list[str], self_name: str, reactor: set[str]
) -> tuple[dict[str, str], set[str]]:
    external: dict[str, str] = {}
    internal: set[str] = set()
    for line in lines:
        line = line.replace("[INFO]", "")
        m = _MVN_RE.match(line)
        if not m:
            continue
        group, artifact, _scope = m.groups()
        # Any artifact declared by the Maven reactor is an internal module, regardless of
        # groupId. Optimize modules use io.camunda.optimize while the main reactor uses io.camunda.
        if artifact in reactor:
            if artifact != self_name:
                internal.add(artifact)
            continue
        # version is the field right before the scope; recover it robustly
        coord = line.strip().split(" ")[0]
        parts = coord.split(":")
        version = parts[-2]  # ...:version:scope
        external[f"{group}:{artifact}"] = version
    return external, internal


def run_maven_dependency_report(include_scope: str) -> str:
    """Resolve dependency:list for the active Maven reactor in one invocation."""
    base_cmd = ["./mvnw", "dependency:list", f"-DincludeScope={include_scope}", "-B"]
    offline_cmd = [*base_cmd, "-o"]
    offline_result = subprocess.run(offline_cmd, cwd=REPO_ROOT, capture_output=True, text=True)
    if offline_result.returncode == 0:
        return offline_result.stdout

    online_result = subprocess.run(base_cmd, cwd=REPO_ROOT, capture_output=True, text=True)
    if online_result.returncode == 0:
        sys.stderr.write("offline Maven reactor resolution failed; retrying without -o\n")
        return online_result.stdout
    raise CommandError(base_cmd, online_result.returncode, online_result.stdout, online_result.stderr)


def maven_dependency_report(include_scope: str, reactor: set[str]) -> dict[str, dict]:
    """Parse one reactor-wide dependency:list output into per-project reports."""
    output = run_maven_dependency_report(include_scope)
    sections: dict[str, list[str]] = {project: [] for project in reactor}
    current_project: str | None = None
    for line in output.splitlines():
        module_match = _MVN_MODULE_RE.search(line)
        if module_match:
            current_project = module_match.group(1)
            sections.setdefault(current_project, [])
            continue
        if current_project is not None:
            sections[current_project].append(line)

    reports: dict[str, dict] = {}
    for project, lines in sections.items():
        third_party, internal = parse_maven_dependency_lines(lines, project, reactor)
        reports[project] = {"third_party": third_party, "internal": sorted(internal)}
    return reports


def maven_deps(module_dir: str, include_scope: str, self_name: str, reactor: set[str]):
    """Return (external {group:artifact -> version}, internal {artifactId}).

    Internal deps are Maven reactor modules (artifactId in `reactor`), keyed by artifactId
    (which equals the Gradle project name by repo convention); the module's own artifact is
    excluded. Separately released artifacts such as `camunda-security-library-*` are treated as
    ordinary third-party deps.
    """
    out = run_maven_dependency_list(module_dir, include_scope)
    return parse_maven_dependency_lines(out.splitlines(), self_name, reactor)


# Gradle tree lines: "+--- group:artifact:req -> res (*)" etc.
_GRADLE_RE = re.compile(r"---\s+([\w.-]+):([\w.-]+)(?::([\w.\-]+))?\s*(?:->\s*([\w.\-]+))?")
# Internal deps show as "+--- project :some-module"
# Gradle 9 renders project dependencies as `project ':module'`; older output used
# `project :module`. Accept both forms.
_GRADLE_PROJECT_RE = re.compile(r"---\s+project ['\"]?:([\w.-]+)['\"]?")


def gradle_deps(project: str, configuration: str):
    """Return (external {group:artifact -> version}, internal {project name})."""
    out = run(
        ["./gradlew", f":{project}:dependencies", "--configuration", configuration, "-q"]
    )
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


def gradle_dependency_report(scope: str) -> dict[str, dict]:
    """Resolve one dependency configuration for all active Gradle projects in one invocation."""
    out = run(
        [
            "./gradlew",
            "--no-daemon",
            "--no-configuration-cache",
            "--console=plain",
            "--quiet",
            f"-Pdependency.report.scope={scope}",
            "printGradleDependencyReport",
        ]
    )
    projects: dict[str, dict] = {}
    for line in out.splitlines():
        if not line.strip():
            continue
        try:
            project = json.loads(line)
        except json.JSONDecodeError as error:
            raise ProjectError(f"Gradle dependency report line was not valid JSON: {error}") from error
        if project.get("scope") != scope:
            raise ProjectError(
                f"Gradle dependency report scope mismatch: expected {scope!r}, got {project.get('scope')!r}"
            )
        name = project.get("project")
        if not name:
            raise ProjectError("Gradle dependency report entry has no project name")
        projects[name] = project
    return projects


def compare_project(
    *,
    project: str,
    module_dir: str,
    scope: str,
    include_scope: str,
    configuration: str,
    versions: bool,
    reactor: set[str],
    global_gradle_report: dict[str, dict] | None = None,
    global_maven_report: dict[str, dict] | None = None,
) -> dict:
    if global_maven_report is None:
        mvn, mvn_int = maven_deps(module_dir, include_scope, self_name=project, reactor=reactor)
    else:
        maven_project = global_maven_report.get(project)
        if maven_project is None:
            raise ProjectError(f"Maven dependency report has no project: {project}")
        mvn = maven_project["third_party"]
        mvn_int = set(maven_project["internal"])
    if global_gradle_report is None:
        grd, grd_int = gradle_deps(project, configuration)
    else:
        gradle_project = global_gradle_report.get(project)
        if gradle_project is None:
            raise ProjectError(f"Gradle dependency report has no project: {project}")
        grd = gradle_project["third_party"]
        grd_int = set(gradle_project["internal"])

    only_mvn = sorted(set(mvn) - set(grd))
    only_grd = sorted(set(grd) - set(mvn))
    common = sorted(set(mvn) & set(grd))
    int_only_mvn = sorted(mvn_int - grd_int)
    int_only_grd = sorted(grd_int - mvn_int)
    mismatches = [
        {"coordinate": coordinate, "maven": mv, "gradle": gv}
        for coordinate in common
        if (mv := mvn[coordinate]) != (gv := grd[coordinate])
    ]
    if not versions:
        mismatches = []

    differences = {
        "third_party_missing": [{"coordinate": coordinate, "version": mvn[coordinate]} for coordinate in only_mvn],
        "third_party_extra": [{"coordinate": coordinate, "version": grd[coordinate]} for coordinate in only_grd],
        "internal_missing": sorted(int_only_mvn),
        "internal_extra": sorted(int_only_grd),
        "version_mismatches": mismatches,
    }
    has_differences = any(differences.values())
    return {
        "project": project,
        "directory": module_dir,
        "scope": scope,
        "status": "differences" if has_differences else "ok",
        "counts": {
            "maven_third_party": len(mvn),
            "gradle_third_party": len(grd),
            "common_third_party": len(common),
            "maven_internal": len(mvn_int),
            "gradle_internal": len(grd_int),
            "common_internal": len(mvn_int & grd_int),
        },
        "dependencies": {
            "maven": {"third_party": mvn, "internal": sorted(mvn_int)},
            "gradle": {"third_party": grd, "internal": sorted(grd_int)},
        },
        "differences": differences,
    }


def error_result(project: str, module_dir: str, scope: str, error: Exception) -> dict:
    result = {
        "project": project,
        "directory": module_dir,
        "scope": scope,
        "status": "error",
        "error": {"message": str(error)},
    }
    if isinstance(error, CommandError):
        result["error"].update(
            {
                "command": error.command,
                "returncode": error.returncode,
                "stderr": error.stderr,
            }
        )
    return result


def missing_gradle_result(
    project: str, module_dir: str, scope: str, maven_project: dict | None = None
) -> dict:
    result = {
        "project": project,
        "directory": module_dir,
        "scope": scope,
        "status": "missing-gradle-project",
        "error": {"message": "No matching Gradle project was found."},
    }
    if maven_project is not None:
        result["dependencies"] = {"maven": maven_project, "gradle": None}
    return result


def report(scope: str, results: list[dict]) -> dict:
    summary = {
        "total": len(results),
        "ok": sum(result["status"] == "ok" for result in results),
        "differences": sum(result["status"] == "differences" for result in results),
        "missing_gradle_projects": sum(
            result["status"] == "missing-gradle-project" for result in results
        ),
        "errors": sum(result["status"] == "error" for result in results),
    }
    return {"scope": scope, "summary": summary, "results": results}


def print_result(result: dict) -> None:
    project = result["project"]
    module_dir = result["directory"]
    print(f"module: {project}  (dir: {module_dir})  scope: {result['scope']}\n")
    if result["status"] == "missing-gradle-project":
        print("MISSING Gradle project")
    elif result["status"] == "error":
        print(f"ERROR: {result['error']['message']}")
    else:
        counts = result["counts"]
        print(
            "third-party deps: "
            f"maven={counts['maven_third_party']} "
            f"gradle={counts['gradle_third_party']} "
            f"common={counts['common_third_party']}"
        )
        print(
            "internal deps:    "
            f"maven={counts['maven_internal']} "
            f"gradle={counts['gradle_internal']} "
            f"common={counts['common_internal']}\n"
        )
        differences = result["differences"]
        for key, heading, source in (
            ("third_party_missing", "MISSING in Gradle", "maven"),
            ("third_party_extra", "EXTRA in Gradle", "gradle"),
        ):
            entries = differences[key]
            if entries:
                print(f"{heading} ({len(entries)}) — third-party, {source} only:")
                for entry in entries:
                    print(f"  {'-' if source == 'maven' else '+'} {entry['coordinate']}  ({source} {entry['version']})")
                print()
        for key, heading, prefix in (
            ("internal_missing", "MISSING in Gradle", "-"),
            ("internal_extra", "EXTRA in Gradle", "+"),
        ):
            entries = differences[key]
            if entries:
                print(f"{heading} ({len(entries)}) — internal project:")
                for entry in entries:
                    print(f"  {prefix} project :{entry}")
                print()
        if differences["version_mismatches"]:
            print(f"VERSION mismatches ({len(differences['version_mismatches'])}):")
            for entry in differences["version_mismatches"]:
                print(
                    f"  ~ {entry['coordinate']}  "
                    f"maven={entry['maven']} gradle={entry['gradle']}"
                )
            print()
    print("OK — no differences" if result["status"] == "ok" else result["status"].upper())


def exit_code(results: list[dict]) -> int:
    if any(result["status"] == "error" for result in results):
        return 1
    return 0 if all(result["status"] == "ok" for result in results) else 2


def main() -> int:
    parser = argparse.ArgumentParser(
        description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter
    )
    parser.add_argument("project", nargs="?", help="Gradle project name (e.g. camunda-client-java)")
    parser.add_argument("--dir", help="Maven/Gradle module directory (e.g. clients/java)")
    parser.add_argument("--scope", choices=SCOPES, default="runtime")
    parser.add_argument("--versions", action="store_true", help="also diff resolved versions")
    parser.add_argument("--list", action="store_true", help="print Gradle project -> directory map and exit")
    parser.add_argument("--all", action="store_true", help="compare every Maven reactor project")
    parser.add_argument("--json", action="store_true", help="emit structured JSON output")
    args = parser.parse_args()

    if args.all and (args.project or args.dir):
        parser.error("--all cannot be combined with a project name or --dir")
    if args.list and (args.all or args.project or args.dir):
        parser.error("--list cannot be combined with --all, a project name, or --dir")

    dirs = None
    if args.list or not args.all:
        dirs = gradle_project_dirs()
    if args.list:
        projects = [{"project": name, "directory": path} for name, path in sorted(dirs.items())]
        if args.json:
            print(json.dumps({"projects": projects}, indent=2, sort_keys=True))
        else:
            for project in projects:
                print(f"{project['project']}\t{project['directory']}")
        return 0

    maven_dirs = maven_project_dirs()
    include_scope, configuration = SCOPES[args.scope]
    reactor = set(maven_dirs)
    results: list[dict] = []
    global_gradle_report = None
    global_maven_report = None

    if args.all:
        try:
            global_gradle_report = gradle_dependency_report(args.scope)
        except (CommandError, ProjectError) as error:
            results.append(error_result("gradle-global-report", ".", args.scope, error))
        try:
            global_maven_report = maven_dependency_report(include_scope, reactor)
        except (CommandError, ProjectError) as error:
            results.append(error_result("maven-global-report", ".", args.scope, error))

    if args.all:
        if global_gradle_report is not None and global_maven_report is not None:
            for project, module_dir in sorted(maven_dirs.items()):
                if project not in global_gradle_report:
                    results.append(
                        missing_gradle_result(
                            project, module_dir, args.scope, global_maven_report.get(project)
                        )
                    )
                    continue
                try:
                    results.append(
                        compare_project(
                            project=project,
                            module_dir=module_dir,
                            scope=args.scope,
                            include_scope=include_scope,
                            configuration=configuration,
                            versions=args.versions,
                            reactor=reactor,
                            global_gradle_report=global_gradle_report,
                            global_maven_report=global_maven_report,
                        )
                    )
                except (CommandError, ProjectError) as error:
                    results.append(error_result(project, module_dir, args.scope, error))
    else:
        assert dirs is not None
        if args.dir:
            module_dir = args.dir.rstrip("/")
            matches = [name for name, path in dirs.items() if path == module_dir]
            if not matches:
                sys.stderr.write(f"no Gradle project maps to dir '{args.dir}'\n")
                return 1
            project = matches[0]
        elif args.project:
            project = args.project
            if project not in dirs:
                sys.stderr.write(f"unknown Gradle project '{project}'. Use --list to see options.\n")
                return 1
            module_dir = dirs[project]
        else:
            parser.error("provide a Gradle project name, --dir, or --all")

        try:
            results.append(
                compare_project(
                    project=project,
                    module_dir=module_dir,
                    scope=args.scope,
                    include_scope=include_scope,
                    configuration=configuration,
                    versions=args.versions,
                    reactor=reactor,
                )
            )
        except (CommandError, ProjectError) as error:
            results.append(error_result(project, module_dir, args.scope, error))

    result_report = report(args.scope, results)
    if args.json:
        print(json.dumps(result_report, indent=2, sort_keys=True))
    else:
        for result in results:
            print_result(result)
        if args.all:
            print(json.dumps(result_report["summary"], indent=2, sort_keys=True))
    return exit_code(results)


if __name__ == "__main__":
    sys.exit(main())
