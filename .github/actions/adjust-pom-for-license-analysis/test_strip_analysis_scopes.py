"""Unit tests for strip-analysis-scopes.py.

Run with:
    python3 -m unittest .github/actions/adjust-pom-for-license-analysis/test_strip_analysis_scopes.py
"""

import importlib.util
import io
import json
import tempfile
import unittest
from contextlib import redirect_stdout
from pathlib import Path
from xml.etree import ElementTree as ET

# The module file name contains hyphens, so load it explicitly.
_SPEC = importlib.util.spec_from_file_location(
    "strip_analysis_scopes", Path(__file__).with_name("strip-analysis-scopes.py")
)
strip = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(strip)

NS = "http://maven.apache.org/POM/4.0.0"

POM = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <groupId>io.camunda.test</groupId>
  <artifactId>{aid}</artifactId>
  <version>1.0.0</version>
{body}
</project>
"""


def dep(group="g", artifact="a", scope=None, dtype=None, classifier=None):
    parts = [f"<groupId>{group}</groupId>", f"<artifactId>{artifact}</artifactId>"]
    if dtype:
        parts.append(f"<type>{dtype}</type>")
    if classifier:
        parts.append(f"<classifier>{classifier}</classifier>")
    if scope:
        parts.append(f"<scope>{scope}</scope>")
    return "<dependency>" + "".join(parts) + "</dependency>"


def deps_block(*deps):
    return "<dependencies>" + "".join(deps) + "</dependencies>"


def mgmt_block(*deps):
    return "<dependencyManagement><dependencies>" + "".join(deps) + "</dependencies></dependencyManagement>"


def profile_block(*deps):
    return "<profiles><profile><id>p</id>" + deps_block(*deps) + "</profile></profiles>"


def remaining(path):
    """Set of (group, artifact) in the top-level <dependencies> of a POM."""
    root = ET.parse(path).getroot()
    block = root.find(f"{{{NS}}}dependencies")
    out = set()
    if block is not None:
        for d in block.findall(f"{{{NS}}}dependency"):
            g = d.find(f"{{{NS}}}groupId").text
            a = d.find(f"{{{NS}}}artifactId").text
            out.add((g, a))
    return out


def managed_remaining(path):
    root = ET.parse(path).getroot()
    mgmt = root.find(f"{{{NS}}}dependencyManagement")
    out = set()
    if mgmt is not None:
        for d in mgmt.find(f"{{{NS}}}dependencies").findall(f"{{{NS}}}dependency"):
            out.add((d.find(f"{{{NS}}}groupId").text, d.find(f"{{{NS}}}artifactId").text))
    return out


class StripTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.root = Path(self._tmp.name)

    def tearDown(self):
        self._tmp.cleanup()

    def write(self, rel, body):
        path = self.root / rel / "pom.xml"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(POM.format(aid=rel.replace("/", "-"), body=body))
        return path

    def run_strip(self):
        with redirect_stdout(io.StringIO()):
            return strip.main([str(self.root)])

    def dry_run(self):
        buf = io.StringIO()
        with redirect_stdout(buf):
            strip.main(["--dry-run", str(self.root)])
        return json.loads(buf.getvalue())

    # --- explicit scopes --------------------------------------------------
    def test_explicit_excluded_scopes_removed(self):
        p = self.write("m", deps_block(
            dep(artifact="keep-compile"),
            dep(artifact="keep-runtime", scope="runtime"),
            dep(artifact="drop-test", scope="test"),
            dep(artifact="drop-provided", scope="provided"),
            dep(artifact="drop-system", scope="system"),
        ))
        self.run_strip()
        self.assertEqual(remaining(p), {("g", "keep-compile"), ("g", "keep-runtime")})

    # --- inherited scope --------------------------------------------------
    def test_inherited_scope_removed(self):
        self.write("bom", mgmt_block(dep(group="x", artifact="managed-test", scope="test")))
        p = self.write("m", deps_block(dep(group="x", artifact="managed-test")))  # no explicit scope
        self.run_strip()
        self.assertEqual(remaining(p), set())

    def test_scopeless_unmanaged_kept(self):
        p = self.write("m", deps_block(dep(group="x", artifact="unmanaged")))
        self.run_strip()
        self.assertEqual(remaining(p), {("x", "unmanaged")})  # default compile -> kept

    def test_explicit_overrides_management(self):
        self.write("bom", mgmt_block(
            dep(group="x", artifact="mgmt-test", scope="test"),
            dep(group="x", artifact="mgmt-compile", scope="compile"),
        ))
        p = self.write("m", deps_block(
            dep(group="x", artifact="mgmt-test", scope="compile"),   # explicit compile wins -> keep
            dep(group="x", artifact="mgmt-compile", scope="test"),   # explicit test wins -> drop
        ))
        self.run_strip()
        self.assertEqual(remaining(p), {("x", "mgmt-test")})

    # --- fail closed on conflicts ----------------------------------------
    def test_conflicting_management_fails_closed(self):
        self.write("bomA", mgmt_block(dep(group="x", artifact="conflict", scope="test")))
        self.write("bomB", mgmt_block(dep(group="x", artifact="conflict", scope="compile")))
        p = self.write("m", deps_block(dep(group="x", artifact="conflict")))  # no explicit scope
        self.run_strip()
        # conflicting managed scopes => unknown => never stripped (keep the possibly-shipped dep)
        self.assertEqual(remaining(p), {("x", "conflict")})

    # --- type / classifier keying ----------------------------------------
    def test_type_and_classifier_are_part_of_key(self):
        self.write("bom", mgmt_block(
            dep(group="x", artifact="lib", dtype="test-jar", scope="test"),
        ))
        p = self.write("m", deps_block(
            dep(group="x", artifact="lib"),                    # jar, unmanaged coord -> keep
            dep(group="x", artifact="lib", dtype="test-jar"),  # matches managed test-jar -> drop
        ))
        self.run_strip()
        self.assertEqual(remaining(p), {("x", "lib")})  # the plain jar survives

    # --- profiles ---------------------------------------------------------
    def test_profile_dependencies_stripped(self):
        p = self.write("m", deps_block(dep(artifact="keep")) + profile_block(
            dep(artifact="drop", scope="test"),
            dep(artifact="keep-profile-compile"),
        ))
        self.run_strip()
        root = ET.parse(p).getroot()
        prof_deps = root.find(f"{{{NS}}}profiles").find(f"{{{NS}}}profile").find(f"{{{NS}}}dependencies")
        arts = {d.find(f"{{{NS}}}artifactId").text for d in prof_deps.findall(f"{{{NS}}}dependency")}
        self.assertEqual(arts, {"keep-profile-compile"})

    # --- dependencyManagement is never edited ----------------------------
    def test_dependency_management_untouched(self):
        p = self.write("bom", mgmt_block(
            dep(group="x", artifact="managed-test", scope="test"),
            dep(group="x", artifact="managed-provided", scope="provided"),
        ))
        self.run_strip()
        self.assertEqual(managed_remaining(p), {("x", "managed-test"), ("x", "managed-provided")})

    # --- idempotence ------------------------------------------------------
    def test_idempotent(self):
        p = self.write("m", deps_block(dep(artifact="keep"), dep(artifact="drop", scope="test")))
        self.run_strip()
        first = p.read_text()
        self.run_strip()
        self.assertEqual(p.read_text(), first)
        self.assertEqual(remaining(p), {("g", "keep")})

    # --- malformed input --------------------------------------------------
    def test_malformed_pom_reports_failure_but_processes_others(self):
        (self.root / "bad").mkdir(parents=True)
        (self.root / "bad" / "pom.xml").write_text("<project><this is not xml")
        good = self.write("good", deps_block(dep(artifact="drop", scope="test"), dep(artifact="keep")))
        rc = self.run_strip()
        self.assertEqual(rc, 1)  # non-zero because a POM failed to parse
        self.assertEqual(remaining(good), {("g", "keep")})  # the valid POM was still processed

    # --- dry run ----------------------------------------------------------
    def test_dry_run_changes_nothing_and_reports(self):
        p = self.write("m", deps_block(dep(artifact="drop", scope="test"), dep(artifact="keep")))
        before = p.read_text()
        report = self.dry_run()
        self.assertEqual(p.read_text(), before)  # unchanged
        self.assertEqual(len(report), 1)
        self.assertEqual(report[0]["artifactId"], "drop")
        self.assertEqual(report[0]["scope"], "test")
        self.assertEqual(report[0]["source"], "explicit")

    def test_target_directories_are_ignored(self):
        p = self.write("m/target/generated", deps_block(dep(artifact="drop", scope="test")))
        self.run_strip()
        self.assertEqual(remaining(p), {("g", "drop")})  # under target/ -> untouched


if __name__ == "__main__":
    unittest.main()
