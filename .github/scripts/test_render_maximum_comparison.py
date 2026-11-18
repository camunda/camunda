"""Unit tests for render-maximum-comparison.py.

Run with:
    pytest .github/scripts/test_render_maximum_comparison.py
"""

import importlib.util
import json
import os
import sys

# Load the hyphen-named module via importlib and register it so mock.patch works.
_SCRIPT = os.path.join(os.path.dirname(__file__), "render-maximum-comparison.py")
_spec = importlib.util.spec_from_file_location("render_maximum_comparison", _SCRIPT)
rmc = importlib.util.module_from_spec(_spec)
sys.modules["render_maximum_comparison"] = rmc
_spec.loader.exec_module(rmc)

QUERY = {
    "name": "completed-pi-rate-per-second",
    "description": "Completed PI/s",
    "format": "integer",
    "higher_is_better": True,
}


def runs(*rates):
    return {f"ns-{i}": {"completed-pi-rate-per-second": rate} for i, rate in enumerate(rates)}


class TestVerdict:
    def test_difference_below_limit_is_within_noise(self):
        # given: 5% faster, below the 10% detection limit
        # when
        result = rmc.verdict(700, 735, higher_is_better=True)
        # then
        assert result == "≈ within noise"

    def test_difference_above_limit_is_better_when_higher_is_better(self):
        # given / when
        result = rmc.verdict(700, 800, higher_is_better=True)
        # then
        assert result == "✅ better"

    def test_difference_above_limit_is_worse_when_higher_is_better(self):
        # given / when
        result = rmc.verdict(700, 600, higher_is_better=True)
        # then
        assert result == "⚠️ worse"

    def test_lower_latency_above_limit_is_better(self):
        # given / when
        result = rmc.verdict(10, 5, higher_is_better=False)
        # then
        assert result == "✅ better"

    def test_missing_side_is_unknown(self):
        # given / when / then
        assert rmc.verdict(None, 700, higher_is_better=True) == "❔"
        assert rmc.verdict(700, None, higher_is_better=True) == "❔"


class TestOutliers:
    def test_cluster_far_from_the_median_is_flagged(self):
        # given: median 700, one cluster at 500 (about 29% below)
        side = runs(700, 710, 500)
        # when
        flagged = rmc.outliers(side, "completed-pi-rate-per-second")
        # then
        assert flagged == ["ns-2"]

    def test_normal_spread_is_not_flagged(self):
        # given: all within about 5% of the median
        side = runs(680, 700, 720)
        # when / then
        assert rmc.outliers(side, "completed-pi-rate-per-second") == []

    def test_side_without_values_flags_nothing(self):
        # given / when / then
        assert rmc.outliers({}, "completed-pi-rate-per-second") == []


class TestRenderRow:
    def test_row_shows_medians_ranges_and_delta(self):
        # given
        base = runs(657, 696, 726)
        pr = runs(780, 790, 800)
        # when
        row = rmc.render_row(QUERY, base, pr)
        # then: base median 696, PR median 790, +13.5%
        assert "696 (657 to 726)" in row
        assert "790 (780 to 800)" in row
        assert "+13.5%" in row
        assert "✅ better" in row

    def test_missing_results_render_as_n_a(self):
        # given / when
        row = rmc.render_row(QUERY, {}, {})
        # then
        assert "n/a (n/a)" in row
        assert "❔" in row


class TestLoadRuns:
    def test_clusters_without_results_are_left_out(self, tmp_path):
        # given: only the first namespace has a results file
        (tmp_path / "load-test-results-ns-a").mkdir()
        (tmp_path / "load-test-results-ns-a" / "results.json").write_text(
            json.dumps({"throughput-per-second": 1})
        )
        # when
        loaded = rmc.load_runs(str(tmp_path), "ns-a ns-b", "Base")
        # then
        assert list(loaded) == ["ns-a"]


class TestRenderBody:
    def test_body_names_the_setup_rate_and_detection_limit(self):
        # given
        ctx = {
            "queries": [QUERY],
            "storage_type": "none",
            "protocol": "rest",
            "rate": "900",
            "base_sha": "aaaaaaaaaaaa",
            "head_sha": "bbbbbbbbbbbb",
            "base_runs": runs(690, 700, 710),
            "pr_runs": runs(700, 705, 715),
        }
        # when
        body = rmc.render_body(ctx)
        # then
        assert "Maximum Load Test - none - rest" in body
        assert "900 PI/s" in body
        assert "within noise" in body
        assert "10%" in body
        assert "Outliers" not in body
