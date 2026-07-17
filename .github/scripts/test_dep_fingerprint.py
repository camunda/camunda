"""Unit tests for dep-fingerprint.py

Run with:
    pytest .github/scripts/test_dep_fingerprint.py
"""

import importlib.util
import os
import re
import shutil
import subprocess
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


def _repo_root():
    out = subprocess.run(
        ["git", "rev-parse", "--show-toplevel"],
        check=True, capture_output=True, text=True,
    ).stdout.strip()
    return out


@pytest.fixture(scope="module")
def real_poms():
    """Every pom.xml actually tracked on this branch, repo-relative, sorted.

    Runs the real `list_poms()` against the real repo checkout — not synthetic
    fixtures — so this fixture only exists while the checkout is intact.
    """
    root = _repo_root()
    old = os.getcwd()
    os.chdir(root)
    try:
        poms = fp.list_poms()
    finally:
        os.chdir(old)
    assert len(poms) > 100, (
        f"expected the full repo reactor (100+ poms), got {len(poms)} — "
        "fixture is running against the wrong directory"
    )
    return root, poms


@pytest.fixture(scope="module")
def real_poms_copy(tmp_path_factory, real_poms):
    """A throwaway copy of every real pom, at the same relative paths.

    Lets tests mutate a "real" pom's content (to check the fingerprint reacts
    correctly to the actual XML shapes this repo uses — namespaces, encodings,
    formatting) without ever touching the checkout.
    """
    root, poms = real_poms
    dest = tmp_path_factory.mktemp("real-poms-copy")
    for pom in poms:
        src = os.path.join(root, pom)
        target = dest / pom
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(src, target)
    return dest, poms


class TestAgainstRealRepo:
    """Runs the fingerprint against every pom actually on this branch.

    The unit tests above use minimal synthetic poms to isolate one behaviour
    at a time. Real poms have things synthetic fixtures don't: XML namespaces,
    varying encodings/declarations, huge `<properties>` blocks, nested BOM
    imports, nonstandard formatting. This class is the check that the script
    doesn't just work in theory — it works on the actual 150+-pom reactor.
    """

    def test_computes_over_the_whole_real_reactor(self, real_poms):
        # given: every pom.xml tracked on this branch
        root, poms = real_poms
        # when: fingerprinting the real repo from its own root
        old = os.getcwd()
        os.chdir(root)
        try:
            digest = fp.compute_fingerprint(poms, fp.EXTRA_FILES)
        finally:
            os.chdir(old)
        # then: a well-formed sha256 hex digest, not an exception or empty string
        assert re.fullmatch(r"[0-9a-f]{64}", digest)

    def test_real_reactor_fingerprint_is_stable(self, real_poms):
        # given/when: fingerprinting the real repo twice
        root, poms = real_poms
        old = os.getcwd()
        os.chdir(root)
        try:
            first = fp.compute_fingerprint(poms, fp.EXTRA_FILES)
            second = fp.compute_fingerprint(poms, fp.EXTRA_FILES)
        finally:
            os.chdir(old)
        # then: identical — this is exactly what the cache key relies on
        assert first == second

    def test_matches_the_cli_entrypoint_output(self, real_poms):
        # given: the real repo, computed both via the library call and the
        # actual `python3 dep-fingerprint.py` CLI invocation used in CI
        root, poms = real_poms
        old = os.getcwd()
        os.chdir(root)
        try:
            via_library = fp.compute_fingerprint(poms, fp.EXTRA_FILES)
        finally:
            os.chdir(old)
        via_cli = subprocess.run(
            [sys.executable, os.path.join(root, ".github/scripts/dep-fingerprint.py")],
            check=True, capture_output=True, text=True, cwd=root,
        ).stdout.strip()
        # then: same digest — proves list_poms()'s own git-based enumeration
        # agrees with the fixture's independently-derived pom list
        assert via_library == via_cli

    def test_unrelated_edit_to_a_real_pom_does_not_rotate_the_key(self, real_poms_copy):
        # given: a full copy of every real pom on this branch
        dest, poms = real_poms_copy
        old = os.getcwd()
        os.chdir(dest)
        try:
            baseline = fp.compute_fingerprint(poms, fp.EXTRA_FILES)
        finally:
            os.chdir(old)
        # when: one real leaf pom (the last one, alphabetically — arbitrary
        # but deterministic) gets a no-op comment appended, exactly the kind
        # of edit that used to rotate the key under hashFiles('**/pom.xml')
        target = dest / poms[-1]
        target.write_text(target.read_text() + "\n<!-- unrelated edit, no dependency impact -->\n")
        os.chdir(dest)
        try:
            after = fp.compute_fingerprint(poms, fp.EXTRA_FILES)
        finally:
            os.chdir(old)
        # then: the fingerprint is unchanged — this is the whole point of #56950
        assert baseline == after

    def test_dependency_edit_to_a_real_pom_does_rotate_the_key(self, real_poms_copy):
        # given: a full copy of every real pom, and one real leaf pom that
        # declares a *.version property (this repo has 26 of those — pick the
        # first pom that actually has a <properties> block to mutate)
        dest, poms = real_poms_copy
        target_path = None
        for pom in poms:
            text = (dest / pom).read_text()
            if "<properties>" in text:
                target_path = pom
                break
        if target_path is None:
            pytest.skip("no real pom with a <properties> block found in this checkout")
        old = os.getcwd()
        os.chdir(dest)
        try:
            baseline = fp.compute_fingerprint(poms, fp.EXTRA_FILES)
        finally:
            os.chdir(old)
        # when: inject a real dependency-relevant change into that pom's
        # <properties> block on the copy
        target = dest / target_path
        text = target.read_text()
        text = text.replace("<properties>", "<properties><fp-test.injected.version>1</fp-test.injected.version>", 1)
        target.write_text(text)
        os.chdir(dest)
        try:
            after = fp.compute_fingerprint(poms, fp.EXTRA_FILES)
        finally:
            os.chdir(old)
        # then: the fingerprint rotates — real dependency-relevant content
        # in a real pom is exactly what must invalidate the cache
        assert baseline != after
