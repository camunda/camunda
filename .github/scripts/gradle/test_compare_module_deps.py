import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("compare-module-deps.py")
SPEC = importlib.util.spec_from_file_location("compare_module_deps", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class ModuleDependencyComparisonTest(unittest.TestCase):
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


if __name__ == "__main__":
    unittest.main()
