"""Unit tests for generate-db-versions.py.

Run with:
    python3 -m unittest .github/actions/read-db-versions/test_generate_db_versions.py
"""

import importlib.util
import io
import json
import os
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path

# The module file name contains hyphens, so load it explicitly.
_SPEC = importlib.util.spec_from_file_location(
    "generate_db_versions", Path(__file__).with_name("generate-db-versions.py")
)
gen = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(gen)

FIXTURE_DB_VERSIONS_YML = """
elasticsearch:
  es8:
    - "8.19.16"
  es9:
    - "9.4.0"
  saas: "8.19.16"

opensearch:
  os2:
    - "2.19.6"
  os3:
    - "3.5.0"
    - "3.6.0"
    - "3.7.0"

postgresql:
  - "17-alpine"

mysql:
  - "8.4"

mariadb:
  - "11.8"

mssql:
  - "2022-latest"

azure-sql:
  - "1.0.7"

oracle:
  - "23.26.1-slim-faststart"
"""


class TestMinMax(unittest.TestCase):
    def test_single_entry_list_returns_one_item(self):
        self.assertEqual(gen.min_max(["8.19.16"]), ["8.19.16"])

    def test_multi_entry_list_returns_first_and_last(self):
        self.assertEqual(gen.min_max(["3.5.0", "3.6.0", "3.7.0"]), ["3.5.0", "3.7.0"])


class TestVersionSlug(unittest.TestCase):
    def test_replaces_dots_and_dashes(self):
        self.assertEqual(gen.version_slug("3.7.0"), "3_7_0")
        self.assertEqual(gen.version_slug("15-alpine"), "15_alpine")


class TestMainEndToEnd(unittest.TestCase):
    """Runs the real main() against a fixture db-versions.yml and checks the
    printed GITHUB_OUTPUT lines parse as the expected JSON matrix shapes."""

    def setUp(self):
        self._tmpdir = tempfile.TemporaryDirectory()
        ci_dir = Path(self._tmpdir.name) / ".ci"
        ci_dir.mkdir()
        (ci_dir / "db-versions.yml").write_text(FIXTURE_DB_VERSIONS_YML)
        self._original_cwd = os.getcwd()
        os.chdir(self._tmpdir.name)

    def tearDown(self):
        os.chdir(self._original_cwd)
        self._tmpdir.cleanup()

    def _run_main(self):
        buf = io.StringIO()
        with redirect_stdout(buf):
            gen.main()
        outputs = {}
        for line in buf.getvalue().splitlines():
            name, _, value = line.partition("=")
            outputs[name] = value
        return outputs

    def test_single_version_outputs(self):
        outputs = self._run_main()
        self.assertEqual(outputs["elasticsearch-8"], "8.19.16")
        self.assertEqual(outputs["opensearch-3"], "3.7.0")
        self.assertEqual(outputs["saas"], "8.19.16")

    def test_es_os_matrix_covers_min_and_max_only(self):
        outputs = self._run_main()
        matrix = json.loads(outputs["es-os-matrix"])
        os3_entries = [
            e for e in matrix["include"] if e["database-type"].startswith("opensearch3_")
        ]
        self.assertEqual(
            [e["database-image-version"] for e in os3_entries], ["3.5.0", "3.7.0"]
        )

    def test_rdbms_matrix_includes_every_configured_engine(self):
        outputs = self._run_main()
        matrix = json.loads(outputs["rdbms-matrix"])
        database_types = {e["database-type"] for e in matrix["include"]}
        self.assertEqual(
            database_types, {"postgres", "mysql", "mariadb", "mssql", "azure-sql", "oracle"}
        )


if __name__ == "__main__":
    unittest.main()
