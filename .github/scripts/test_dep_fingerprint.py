"""Unit tests for dep-fingerprint.py

Run with:
    pytest .github/scripts/test_dep_fingerprint.py
"""

import importlib.util
import os
import sys

import pytest

# Load the hyphen-named module via importlib so it is importable as a module.
_SCRIPT = os.path.join(os.path.dirname(__file__), "dep-fingerprint.py")
_spec = importlib.util.spec_from_file_location("dep_fingerprint", _SCRIPT)
fp = importlib.util.module_from_spec(_spec)
sys.modules["dep_fingerprint"] = fp
_spec.loader.exec_module(fp)


# A minimal but realistic pom skeleton. Callers fill in the body.
_POM = """<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
  <modelVersion>4.0.0</modelVersion>
  <artifactId>{artifact}</artifactId>
  <name>{name}</name>
{body}
</project>
"""


def _write_pom(root, filename, *, artifact="mod", name="A Module", body=""):
    """Write a pom under `root` at the logical relative path `filename`.

    Returns the logical (relative) path — the same string the script hashes and
    opens in production (repo-relative, run from repo root). Tests that need two
    variants of "a/pom.xml" must pass different `root` dirs so the on-disk files
    are independent while the hashed path stays `a/pom.xml`.
    """
    path = root / filename
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(_POM.format(artifact=artifact, name=name, body=body))
    return filename


def _fingerprint(root, poms):
    # No extra files in these unit tests — the pom content is what matters.
    # Run from `root` so the relative logical paths resolve to the fixtures.
    old = os.getcwd()
    os.chdir(root)
    try:
        return fp.compute_fingerprint(poms, extra_files=())
    finally:
        os.chdir(old)


class TestUnrelatedContentIgnored:
    def test_name_only_difference_produces_same_fingerprint(self, tmp_path):
        # given: two poms with identical deps but a different <name>
        deps = "  <dependencies><dependency><groupId>g</groupId>" \
               "<artifactId>a</artifactId><version>1.0.0</version></dependency></dependencies>"
        ra, rb = tmp_path / "a", tmp_path / "b"
        pa = _write_pom(ra, "a/pom.xml", name="Original", body=deps)
        pb = _write_pom(rb, "a/pom.xml", name="Renamed Module", body=deps)
        # when / then: the fingerprint is unchanged
        assert _fingerprint(ra, [pa]) == _fingerprint(rb, [pb])

    def test_reformat_and_comments_produce_same_fingerprint(self, tmp_path):
        # given: same deps, one reindented and with comments added
        tight = "<dependencies><dependency><groupId>g</groupId>" \
                "<artifactId>a</artifactId><version>1.0.0</version></dependency></dependencies>"
        loose = """  <dependencies>
    <!-- a comment that must not affect the hash -->
    <dependency>
        <groupId>g</groupId>
        <artifactId>a</artifactId>
        <version>1.0.0</version>
    </dependency>
  </dependencies>"""
        ra, rb = tmp_path / "a", tmp_path / "b"
        pa = _write_pom(ra, "a/pom.xml", body=tight)
        pb = _write_pom(rb, "a/pom.xml", body=loose)
        # when / then
        assert _fingerprint(ra, [pa]) == _fingerprint(rb, [pb])


class TestDependencyChangesDetected:
    def _two(self, tmp_path, body_a, body_b):
        ra, rb = tmp_path / "a", tmp_path / "b"
        pa = _write_pom(ra, "a/pom.xml", body=body_a)
        pb = _write_pom(rb, "a/pom.xml", body=body_b)
        return _fingerprint(ra, [pa]), _fingerprint(rb, [pb])

    def test_dependency_version_bump_changes_fingerprint(self, tmp_path):
        # given: two poms differing only in a <dependencies> version
        dep = "  <dependencies><dependency><groupId>g</groupId>" \
              "<artifactId>a</artifactId><version>{v}</version></dependency></dependencies>"
        fa, fb = self._two(tmp_path, dep.format(v="1.0.0"), dep.format(v="2.0.0"))
        # when / then
        assert fa != fb

    def test_property_version_bump_changes_fingerprint(self, tmp_path):
        # given: a leaf pom that declares a *.version property (topology this repo has)
        prop = "  <properties><foo.version>{v}</foo.version></properties>"
        fa, fb = self._two(tmp_path, prop.format(v="1.0.0"), prop.format(v="2.0.0"))
        # when / then
        assert fa != fb

    def test_managed_dependency_change_detected(self, tmp_path):
        # given: change inside <dependencyManagement>
        mgmt = "  <dependencyManagement><dependencies><dependency>" \
               "<groupId>g</groupId><artifactId>a</artifactId>" \
               "<version>{v}</version></dependency></dependencies></dependencyManagement>"
        fa, fb = self._two(tmp_path, mgmt.format(v="1.0.0"), mgmt.format(v="1.0.1"))
        # when / then
        assert fa != fb

    def test_plugin_version_change_detected(self, tmp_path):
        # given: change inside <build><plugins> (plugins are downloaded/cached too)
        build = "  <build><plugins><plugin><groupId>g</groupId>" \
                "<artifactId>p</artifactId><version>{v}</version></plugin></plugins></build>"
        fa, fb = self._two(tmp_path, build.format(v="3.0.0"), build.format(v="3.1.0"))
        # when / then
        assert fa != fb


class TestDeterminism:
    def test_fingerprint_is_stable_across_repeated_runs(self, tmp_path):
        # given: a fixed set of poms
        p1 = _write_pom(tmp_path, "m1/pom.xml", artifact="m1",
                        body="  <properties><x.version>1</x.version></properties>")
        p2 = _write_pom(tmp_path, "m2/pom.xml", artifact="m2",
                        body="  <dependencies><dependency><groupId>g</groupId>"
                             "<artifactId>a</artifactId><version>1</version></dependency></dependencies>")
        poms = sorted([p1, p2])
        # when / then: two runs over the same input agree
        assert _fingerprint(tmp_path, poms) == _fingerprint(tmp_path, poms)

    def test_ordering_is_handled_by_caller_sorting(self, tmp_path):
        # given: the same poms enumerated in different order
        p1 = _write_pom(tmp_path, "m1/pom.xml", artifact="m1",
                        body="  <properties><x.version>1</x.version></properties>")
        p2 = _write_pom(tmp_path, "m2/pom.xml", artifact="m2",
                        body="  <properties><y.version>2</y.version></properties>")
        # when: caller sorts (as list_poms does) before hashing
        forward = _fingerprint(tmp_path, sorted([p1, p2]))
        reverse = _fingerprint(tmp_path, sorted([p2, p1]))
        # then: sorted input yields a filesystem-order-independent digest
        assert forward == reverse

    def test_identical_blocks_in_different_modules_do_not_collide(self, tmp_path):
        # given: two modules with byte-identical dep blocks (same version)
        body = "  <properties><x.version>1</x.version></properties>"
        base = tmp_path / "base"
        m1 = _write_pom(base, "m1/pom.xml", artifact="m1", body=body)
        m2 = _write_pom(base, "m2/pom.xml", artifact="m2", body=body)
        baseline = _fingerprint(base, sorted([m1, m2]))
        # and: the same reactor but with m2's version bumped
        bumped = tmp_path / "bumped"
        b1 = _write_pom(bumped, "m1/pom.xml", artifact="m1", body=body)
        b2 = _write_pom(bumped, "m2/pom.xml", artifact="m2",
                        body="  <properties><x.version>2</x.version></properties>")
        changed = _fingerprint(bumped, sorted([b1, b2]))
        # when / then: the path prefix keeps modules distinct, so the bump shows
        assert baseline != changed


class TestFailsLoud:
    def test_parse_error_raises(self, tmp_path):
        # given: a malformed pom
        bad = tmp_path / "bad" / "pom.xml"
        bad.parent.mkdir(parents=True)
        bad.write_text("<project><dependencies></project>")  # unclosed tag
        # when / then: a silent wrong hash must never happen — it raises instead
        with pytest.raises(fp.ET.ParseError):
            _fingerprint(tmp_path, ["bad/pom.xml"])
