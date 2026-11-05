"""Unit tests for check.py, the OC REST API breaking-change check.

Run with:
    pytest .github/scripts/oas-breaking-change-check/test_oas_breaking_change_check.py

git and oasdiff are replaced with monkeypatched functions: what these tests pin
is the check's own logic, which decides what gets reported to the PR.
"""

import importlib.util
import json
import subprocess
import sys
from pathlib import Path

import pytest

_SCRIPT = Path(__file__).parent / "check.py"
_spec = importlib.util.spec_from_file_location("oas_breaking_change_check", _SCRIPT)
check = importlib.util.module_from_spec(_spec)
sys.modules["oas_breaking_change_check"] = check
_spec.loader.exec_module(check)

PROMOTABLE = frozenset({"request-property-became-required"})


def finding(fingerprint, *, id="request-property-became-required", level=3, disclaimers=(),
            file="/w/head/zeebe/gateway-protocol/src/main/proto/v2/jobs.yaml", line=10,
            operation="POST", path="/jobs", text="property `x` became required"):
    f = {"id": id, "level": level, "fingerprint": fingerprint, "operation": operation, "path": path,
         "text": text, "disclaimers": list(disclaimers)}
    if file:
        f["revisionSource"] = {"file": file, "line": line, "column": 1}
    return f


# ── which findings count ────────────────────────────────────────────────────

class TestIsError:
    def test_error_level_counts(self):
        assert check.is_error(finding("a"), PROMOTABLE)

    def test_warning_inside_allof_counts_when_its_default_level_is_error(self):
        # given: oasdiff downgraded a default-error check because it sits in an allOf
        f = finding("a", level=2, disclaimers=["all-of-not-flattened"])
        # then
        assert check.is_error(f, PROMOTABLE)

    def test_warning_inside_allof_does_not_count_when_its_default_level_is_not_error(self):
        f = finding("a", id="response-property-enum-value-added", level=2, disclaimers=["all-of-not-flattened"])
        assert not check.is_error(f, PROMOTABLE)

    def test_plain_warning_does_not_count(self):
        assert not check.is_error(finding("a", level=2), PROMOTABLE)


class TestPromotableIds:
    def test_default_error_checks_minus_the_ones_severity_levels_decides(self, monkeypatch):
        # given: oasdiff's defaults, one of which severity-levels.txt overrides
        defaults = [
            {"id": "request-property-became-required", "level": "error"},
            {"id": "response-optional-property-removed", "level": "error"},
            {"id": "response-property-enum-value-added", "level": "warn"},
        ]
        monkeypatch.setattr(check.subprocess, "run",
                            lambda *a, **k: subprocess.CompletedProcess(a, 0, stdout=json.dumps(defaults)))
        # when / then
        assert check.promotable_ids("oasdiff") == frozenset({"request-property-became-required"})


# ── new = ERR(baseline -> head) - ERR(baseline -> base) ─────────────────────

class TestCheck:
    @pytest.fixture
    def run_check(self, monkeypatch, tmp_path):
        def run(base, head):
            monkeypatch.setattr(check, "resolve_baseline", lambda ref: "8.9.0")
            monkeypatch.setattr(check.subprocess, "run", lambda *a, **k: subprocess.CompletedProcess(a, 0))
            monkeypatch.setattr(check, "export_spec", lambda ref, target: None)
            monkeypatch.setattr(check, "normalize", lambda directory: None)
            monkeypatch.setattr(check, "promotable_ids", lambda binary: PROMOTABLE)
            monkeypatch.setattr(check, "oasdiff",
                                lambda binary, baseline, tree, label: {"base": base, "head": head}[label])
            return check.check("main", "oasdiff", tmp_path)
        return run

    def test_a_break_only_in_head_is_reported(self, run_check):
        result = run_check(base=[], head=[finding("new")])
        assert result.status == "breaking"
        assert result.baseline == "8.9.0"
        assert len(result.groups) == 1

    def test_a_break_already_on_the_base_branch_is_not_reported(self, run_check):
        result = run_check(base=[finding("old")], head=[finding("old")])
        assert result.status == "none"
        assert result.groups == []

    def test_a_fingerprint_in_the_base_diff_at_any_level_is_not_new(self, run_check):
        # given: the base diff has it as a warning, the head diff as an error
        result = run_check(base=[finding("same", level=2)], head=[finding("same")])
        assert result.status == "none"

    def test_a_new_warning_that_is_not_promoted_is_not_reported(self, run_check):
        result = run_check(base=[], head=[finding("w", level=2)])
        assert result.status == "none"


# ── grouping and report ─────────────────────────────────────────────────────

class TestGroup:
    def test_one_shared_schema_change_is_one_group_listing_every_operation(self):
        # given: the same location fans out to two operations
        findings = [
            finding("a", operation="POST", path="/jobs", text="x became required for the status `200`"),
            finding("b", operation="GET", path="/jobs"),
        ]
        # when
        [group] = check.group(findings)
        # then: the status of one member would be misleading for the group
        assert group["operations"] == ["GET /jobs", "POST /jobs"]
        assert group["text"] == "x became required"
        assert group["source"] == "v2/jobs.yaml"

    def test_a_single_finding_keeps_its_status_code(self):
        [group] = check.group([finding("a", text="x became required for status `200`")])
        assert group["text"] == "x became required for status `200`"

    def test_findings_without_a_location_are_grouped_by_fingerprint(self):
        groups = check.group([finding("a", file=None), finding("b", file=None)])
        assert len(groups) == 2
        assert all(g["source"] == "" for g in groups)

    def test_largest_group_first(self):
        findings = [finding("a", line=1), finding("b", line=2, operation="GET"), finding("c", line=2)]
        assert [len(g["operations"]) for g in check.group(findings)] == [2, 1]


def result_with(groups):
    return check.Result(status="breaking" if groups else "none", baseline="8.9.0", groups=groups)


def big_group(i, operations=1, text_length=10):
    return {"id": f"id-{i}", "source": "v2/jobs.yaml", "text": "t" * text_length,
            "operations": [f"POST /p{i}/{n}" for n in range(operations)]}


class TestReport:
    def test_no_breaks(self):
        report = check.render_report(result_with([]))
        assert report.startswith(check.MARKER)
        assert "No new breaking changes relative to `8.9.0`." in report
        assert check.GOVERNANCE_FOOTER[0] not in report

    def test_breaks_list_operations_up_to_the_cap(self):
        report = check.render_report(result_with([big_group(1, operations=12)]))
        assert "**1 breaking change(s)** relative to `8.9.0`" in report
        assert report.count("  - `POST /p1/") == check.MAX_OPERATIONS_SHOWN
        assert "  - …and 2 more" in report
        assert check.GOVERNANCE_FOOTER[0] in report

    def test_a_report_that_fits_is_posted_whole(self):
        result = result_with([big_group(1)])
        assert check.fit_report(result) == check.render_report(result)

    def test_a_too_long_report_drops_the_operation_lists_first(self):
        # given: long only because of the operation lists
        result = result_with([big_group(i, operations=10, text_length=10) for i in range(800)])
        assert len(check.render_report(result)) > check.MAX_REPORT_LENGTH
        # when
        report = check.fit_report(result)
        # then
        assert len(report) <= check.MAX_REPORT_LENGTH
        assert check.TRUNCATED_LINE in report
        assert "…and" not in report

    def test_a_report_still_too_long_shows_a_prefix_of_the_groups(self):
        result = result_with([big_group(i, text_length=500) for i in range(200)])
        report = check.fit_report(result)
        assert len(report) <= check.MAX_REPORT_LENGTH
        assert "more breaking changes" in report
        assert "`id-0`" in report


# ── spec normalization ──────────────────────────────────────────────────────

class TestNormalize:
    def test_custom_string_formats_are_removed_and_standard_ones_kept(self):
        node = {"properties": {
            "key": {"type": "string", "format": "ProcessInstanceKey"},
            "ref": {"format": "TenantId"},
            "at": {"type": "string", "format": "date-time"},
            "count": {"type": "integer", "format": "Int64Custom"},
        }}
        assert check._normalize_node(node)
        assert "format" not in node["properties"]["key"]
        assert "format" not in node["properties"]["ref"]
        assert node["properties"]["at"]["format"] == "date-time"
        assert node["properties"]["count"]["format"] == "Int64Custom"

    def test_a_property_named_format_is_kept(self):
        node = {"properties": {"format": {"type": "string"}}}
        assert not check._normalize_node(node)

    def test_multipart_encoding_headers_are_removed_and_the_rest_kept(self):
        node = {"content": {"multipart/form-data": {"encoding": {"file": {"contentType": "x", "headers": {"h": {}}}}}}}
        assert check._normalize_node(node)
        assert node["content"]["multipart/form-data"]["encoding"]["file"] == {"contentType": "x"}

    def test_a_schema_property_named_encoding_is_kept(self):
        node = {"content": {"encoding": {"encoding": {"file": {"headers": {"h": {}}}}}}}
        assert not check._normalize_node(node)

    def test_files_are_rewritten_only_when_changed_and_keep_comments_and_quotes(self, tmp_path):
        # given
        changed = tmp_path / "keys.yaml"
        changed.write_text("# keys\nKey:\n  type: string\n  format: ProcessInstanceKey\n  example: 'yes'\n")
        untouched = tmp_path / "plain.yml"
        untouched.write_text("At:   {type: string, format: date-time}\n")
        # when
        check.normalize(tmp_path)
        # then
        assert changed.read_text() == "# keys\nKey:\n  type: string\n  example: 'yes'\n"
        assert untouched.read_text() == "At:   {type: string, format: date-time}\n"


class TestRelativeSource:
    def test_work_tree_paths_are_shown_relative_to_the_proto_dir(self):
        path = "/out/work/head/zeebe/gateway-protocol/src/main/proto/v2/jobs.yaml"
        assert check.relative_source(path) == "v2/jobs.yaml"

    def test_other_paths_are_kept(self):
        assert check.relative_source("elsewhere/jobs.yaml") == "elsewhere/jobs.yaml"


# ── baseline ────────────────────────────────────────────────────────────────

class TestResolveBaseline:
    @pytest.fixture(autouse=True)
    def repo(self, monkeypatch, tmp_path):
        (tmp_path / "parent").mkdir()
        (tmp_path / "parent" / "pom.xml").write_text(
            "<project><backwards.compat.version> 8.8.0 </backwards.compat.version></project>")
        monkeypatch.chdir(tmp_path)
        return tmp_path

    def test_a_stable_branch_uses_its_first_release(self, monkeypatch):
        monkeypatch.setattr(check, "release_tag_exists", lambda tag: tag == "8.9.0")
        assert check.resolve_baseline("stable/8.9") == "8.9.0"

    def test_an_unreleased_stable_branch_falls_back_to_backwards_compat_version(self, monkeypatch):
        monkeypatch.setattr(check, "release_tag_exists", lambda tag: False)
        assert check.resolve_baseline("stable/8.10") == "8.8.0"

    def test_main_uses_backwards_compat_version_without_listing_tags(self, monkeypatch):
        monkeypatch.setattr(check, "release_tag_exists", lambda tag: pytest.fail("must not list tags"))
        assert check.resolve_baseline("main") == "8.8.0"

    def test_a_missing_backwards_compat_version_fails_the_check(self, repo):
        (repo / "parent" / "pom.xml").write_text("<project/>")
        with pytest.raises(SystemExit):
            check.resolve_baseline("main")


class TestReleaseTagExists:
    @pytest.mark.parametrize("returncode, expected", [(0, True), (2, False)])
    def test_exit_code_decides(self, monkeypatch, returncode, expected):
        monkeypatch.setattr(check.subprocess, "run", lambda *a, **k: subprocess.CompletedProcess(a, returncode))
        assert check.release_tag_exists("8.9.0") is expected

    def test_a_failed_call_is_not_mistaken_for_a_missing_tag(self, monkeypatch):
        monkeypatch.setattr(check.subprocess, "run", lambda *a, **k: subprocess.CompletedProcess(a, 128))
        with pytest.raises(SystemExit):
            check.release_tag_exists("8.9.0")
