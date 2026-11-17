import importlib.util
import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).with_name("compare-module-deps.py")
SPEC = importlib.util.spec_from_file_location("compare_module_deps", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ModuleDependencyComparisonTest(unittest.TestCase):
    def gradle_entry(self, project: str, **overrides) -> dict:
        report = {
            "project": project,
            "directory": project,
            "scope": "runtime",
            "third_party": {},
            "direct_third_party": [],
            "internal": [],
        }
        report.update(overrides)
        return report

    def maven_entry(self) -> dict:
        return {"third_party": {}, "internal": []}

    def run_all(self) -> tuple[int, dict]:
        output = io.StringIO()
        with mock.patch.object(MODULE.sys, "argv", [str(SCRIPT), "--all", "--json"]):
            with redirect_stdout(output):
                code = MODULE.main()
        return code, json.loads(output.getvalue())

    def run_all_with_reports(
        self,
        projects: dict[str, str],
        maven_report: dict[str, dict],
        gradle_report: dict[str, dict],
        *,
        default_projects: dict[str, str] | None = None,
        packaging: str = "jar",
    ) -> tuple[int, dict]:
        if default_projects is None:
            default_projects = projects
        with (
            mock.patch.object(
                MODULE,
                "maven_project_dirs",
                side_effect=lambda include_profiles=True: (
                    projects if include_profiles else default_projects
                ),
            ),
            mock.patch.object(MODULE, "maven_project_packaging", return_value=packaging),
            mock.patch.object(MODULE, "maven_dependency_report", return_value=maven_report),
            mock.patch.object(MODULE, "gradle_dependency_report", return_value=gradle_report),
        ):
            return self.run_all()

    def compare_versions(
        self,
        maven: str,
        gradle: str,
        versions: bool = True,
        gradle_direct: bool = False,
        maven_direct: bool = False,
    ) -> dict:
        coordinate = "com.example:library"
        return MODULE.compare_project(
            project="example",
            module_dir="example",
            scope="runtime",
            include_scope="runtime",
            configuration="runtimeClasspath",
            versions=versions,
            reactor={"example"},
            global_maven_report={
                "example": {
                    "third_party": {coordinate: maven},
                    "direct_third_party": [coordinate] if maven_direct else [],
                    "internal": [],
                }
            },
            global_gradle_report={
                "example": {
                    "project": "example",
                    "scope": "runtime",
                    "third_party": {coordinate: gradle},
                    "direct_third_party": [coordinate] if gradle_direct else [],
                    "internal": [],
                }
            },
        )

    def test_should_report_patch_only_version_mismatches_as_ignored(self):
        result = self.compare_versions("1.2.3", "1.2.4")

        self.assertEqual(result["status"], "ok")
        self.assertEqual(result["differences"]["version_mismatches"], [])
        self.assertEqual(
            result["ignored_differences"]["version_mismatches"],
            [{"coordinate": "com.example:library", "maven": "1.2.3", "gradle": "1.2.4"}],
        )

    def test_should_report_major_and_minor_version_mismatches_as_blocking(self):
        result = self.compare_versions("1.2.3", "1.3.3")

        self.assertEqual(result["status"], "differences")
        self.assertEqual(
            result["differences"]["version_mismatches"],
            [{"coordinate": "com.example:library", "maven": "1.2.3", "gradle": "1.3.3"}],
        )
        self.assertEqual(result["ignored_differences"]["version_mismatches"], [])

    def test_should_report_patch_only_direct_version_mismatches_as_blocking(self):
        result = self.compare_versions("1.2.3", "1.2.4", gradle_direct=True)

        self.assertEqual(result["status"], "differences")
        self.assertEqual(
            result["differences"]["version_mismatches"],
            [{"coordinate": "com.example:library", "maven": "1.2.3", "gradle": "1.2.4"}],
        )
        self.assertEqual(result["ignored_differences"]["version_mismatches"], [])

    def test_should_not_compare_versions_without_versions_flag(self):
        result = self.compare_versions("1.2.3", "1.3.3", versions=False)

        self.assertEqual(result["status"], "ok")
        self.assertEqual(result["differences"]["version_mismatches"], [])
        self.assertEqual(result["ignored_differences"]["version_mismatches"], [])

    def test_should_count_ignored_version_mismatches_in_summary(self):
        result = self.compare_versions("1.2.3", "1.2.4")

        summary = MODULE.report("runtime", [result])["summary"]

        self.assertEqual(summary["ok"], 1)
        self.assertEqual(summary["differences"], 0)
        self.assertEqual(summary["ignored_version_mismatches"], 1)

    def test_should_only_report_observed_maven_sections(self):
        output = """\
[INFO] --- maven-dependency-plugin:3.8.1:list (default-cli) @ observed ---
[INFO]    com.example:library:jar:1.0:runtime -- module
"""
        with mock.patch.object(MODULE, "run_maven_dependency_report", return_value=output):
            reports = MODULE.maven_dependency_report("runtime", {"observed", "unobserved"})

        self.assertEqual(
            reports,
            {"observed": {"third_party": {"com.example:library": "1.0"}, "internal": []}},
        )

    def test_should_keep_an_observed_empty_maven_section(self):
        output = """\
[INFO] --- maven-dependency-plugin:3.8.1:list (default-cli) @ empty-module ---
[INFO] Nothing to display
"""
        with mock.patch.object(MODULE, "run_maven_dependency_report", return_value=output):
            reports = MODULE.maven_dependency_report("runtime", {"empty-module"})

        self.assertEqual(reports, {"empty-module": self.maven_entry()})

    def test_should_discover_profile_modules_only_when_requested(self):
        pom = """\
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>{artifact}</artifactId>
  {modules}
</project>
"""
        with tempfile.TemporaryDirectory() as temporary_directory:
            root = Path(temporary_directory)
            (root / "pom.xml").write_text(
                pom.format(
                    artifact="root",
                    modules="""<packaging>pom</packaging>
  <modules><module>base</module></modules>
  <profiles><profile><id>optional</id><modules>
    <module>profile-module</module>
  </modules></profile></profiles>""",
                )
            )
            for directory, artifact in (("base", "base"), ("profile-module", "profile-module")):
                module = root / directory
                module.mkdir()
                (module / "pom.xml").write_text(pom.format(artifact=artifact, modules=""))

            with mock.patch.object(MODULE, "REPO_ROOT", root):
                default_projects = MODULE.maven_project_dirs(include_profiles=False)
                all_projects = MODULE.maven_project_dirs()
                root_packaging = MODULE.maven_project_packaging(".")
                default_packaging = MODULE.maven_project_packaging("base")

        self.assertEqual(root_packaging, "pom")
        self.assertEqual(default_packaging, "jar")
        self.assertEqual(default_projects, {"root": ".", "base": "base"})
        self.assertEqual(
            all_projects,
            {"root": ".", "base": "base", "profile-module": "profile-module"},
        )

    def test_should_fail_all_when_a_default_maven_report_is_unobserved(self):
        code, result = self.run_all_with_reports(
            {"module": "module"},
            maven_report={},
            gradle_report={"module": self.gradle_entry("module")},
        )

        self.assertEqual(code, 1)
        self.assertEqual(result["summary"]["errors"], 1)
        self.assertIn(
            "Maven dependency report has no project",
            result["results"][0]["error"]["message"],
        )

    def test_should_fail_all_for_a_jar_module_without_a_gradle_project_or_build_file(self):
        code, result = self.run_all_with_reports(
            {"unported": "modules/unported"},
            maven_report={"unported": self.maven_entry()},
            gradle_report={},
        )

        self.assertEqual(code, 2)
        self.assertEqual(result["summary"]["missing_gradle_projects"], 1)
        self.assertEqual(result["results"][0]["status"], "missing-gradle-project")
        self.assertEqual(result["results"][0]["project"], "unported")

    def test_should_omit_maven_pom_aggregators_from_all_results(self):
        code, result = self.run_all_with_reports(
            {"aggregate": "."}, maven_report={}, gradle_report={}, packaging="pom"
        )

        self.assertEqual(code, 0)
        self.assertEqual(result["results"], [])
        self.assertEqual(result["summary"]["missing_gradle_projects"], 0)

    def test_should_ignore_inactive_profile_modules_and_compare_observed_active_ones(self):
        all_projects = {"default": "default", "profile-only": "profile-only"}
        default_projects = {"default": "default"}
        report = {"default": self.gradle_entry("default")}
        inactive_code, inactive_result = self.run_all_with_reports(
            all_projects,
            maven_report={"default": self.maven_entry()},
            gradle_report=report,
            default_projects=default_projects,
        )

        self.assertEqual(inactive_code, 0)
        self.assertEqual([item["project"] for item in inactive_result["results"]], ["default"])

        report["profile-only"] = self.gradle_entry("profile-only")
        active_code, active_result = self.run_all_with_reports(
            all_projects,
            maven_report={"default": self.maven_entry(), "profile-only": self.maven_entry()},
            gradle_report=report,
            default_projects=default_projects,
        )

        self.assertEqual(active_code, 0)
        self.assertEqual(
            [item["project"] for item in active_result["results"]],
            ["default", "profile-only"],
        )

    def test_should_fail_all_when_a_gradle_report_is_missing_required_fields(self):
        malformed_report = {"project": "module", "scope": "runtime", "third_party": {}}
        code, result = self.run_all_with_reports(
            {"module": "module"},
            maven_report={"module": self.maven_entry()},
            gradle_report={"module": malformed_report},
        )

        self.assertEqual(code, 1)
        self.assertEqual(result["summary"]["errors"], 1)
        self.assertIn("'internal' field", result["results"][0]["error"]["message"])

    def test_should_compare_a_covered_module_without_dependency_differences(self):
        coordinate = "com.example:library"
        maven_entry = {"third_party": {coordinate: "1.0"}, "internal": []}
        gradle_entry = self.gradle_entry("covered", third_party={coordinate: "1.0"})
        code, result = self.run_all_with_reports(
            {"covered": "covered"},
            maven_report={"covered": maven_entry},
            gradle_report={"covered": gradle_entry},
        )

        self.assertEqual(code, 0)
        self.assertEqual(result["summary"]["ok"], 1)
        self.assertEqual(result["results"][0]["status"], "ok")

    def test_should_reject_a_single_gradle_report_with_missing_required_fields(self):
        malformed_report = {"project": "module", "scope": "runtime", "third_party": {}}
        with mock.patch.object(MODULE, "run", return_value=json.dumps(malformed_report)):
            with self.assertRaisesRegex(MODULE.ProjectError, "has no 'internal' field"):
                MODULE.gradle_deps("module", "runtime")

    def test_should_fail_all_when_the_global_gradle_report_omits_a_project_entry(self):
        projects = {"module": "module"}
        with (
            mock.patch.object(
                MODULE,
                "maven_project_dirs",
                side_effect=lambda include_profiles=True: projects,
            ),
            mock.patch.object(
                MODULE, "maven_dependency_report", return_value={"module": self.maven_entry()}
            ),
            mock.patch.object(
                MODULE,
                "gradle_project_dirs",
                return_value={"first": "first", "second": "second"},
            ),
            mock.patch.object(MODULE, "run", return_value=json.dumps(self.gradle_entry("first"))),
        ):
            code, result = self.run_all()

        self.assertEqual(code, 1)
        self.assertEqual(result["summary"]["errors"], 1)
        self.assertIn("no entries for projects: second", result["results"][0]["error"]["message"])


if __name__ == "__main__":
    unittest.main()
