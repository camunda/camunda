import importlib.util
import tempfile
import unittest
from pathlib import Path
from zipfile import ZipFile


SCRIPT = Path(__file__).with_name("compare-dist.py")
SPEC = importlib.util.spec_from_file_location("compare_dist", SCRIPT)
assert SPEC and SPEC.loader
MODULE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(MODULE)


class DistributionComparisonTest(unittest.TestCase):
    def create_archive(self, directory: Path, name: str, root: str, jars: list[str]) -> Path:
        path = directory / name
        with ZipFile(path, "w") as archive:
            for jar in jars:
                archive.writestr(f"{root}/lib/{jar}", b"payload")
        return path

    def test_should_inventory_archive_names_without_reading_payloads(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            archive = self.create_archive(
                Path(temporary_directory),
                "distribution.zip",
                "camunda-zeebe-1.0.0",
                ["example-1.0.0.jar"],
            )

            root, files = MODULE.archive_inventory(str(archive))

            self.assertEqual(root, "camunda-zeebe-1.0.0")
            self.assertEqual(files, ["lib/example-1.0.0.jar"])

    def test_should_accept_equal_jar_inventories(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            directory = Path(temporary_directory)
            gradle = self.create_archive(
                directory, "gradle.zip", "camunda-zeebe-1.0.0", ["example-1.0.0.jar"]
            )
            maven = self.create_archive(
                directory, "maven.zip", "camunda-zeebe-1.0.0", ["example-1.0.0.jar"]
            )

            self.assertEqual(MODULE.compare(str(gradle), str(maven)), 0)

    def test_should_report_jar_and_root_mismatches(self):
        with tempfile.TemporaryDirectory() as temporary_directory:
            directory = Path(temporary_directory)
            gradle = self.create_archive(
                directory, "gradle.zip", "camunda-zeebe-1.0.0", ["example-1.1.0.jar"]
            )
            maven = self.create_archive(
                directory, "maven.zip", "camunda-zeebe-2.0.0", ["example-1.0.0.jar"]
            )

            self.assertEqual(MODULE.compare(str(gradle), str(maven)), 2)


if __name__ == "__main__":
    unittest.main()
