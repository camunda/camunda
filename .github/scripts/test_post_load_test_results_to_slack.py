"""Unit tests for post-load-test-results-to-slack.py — specifically that the metrics-table
`mrkdwn` block it builds always stays under Slack's ~3000-char text limit, no matter how much
data (queries, variants) it's built from.

Slack rejects the *entire* `blocks` payload with `invalid_blocks` if any single block's text
exceeds that limit — this is exactly what broke the "Notify Slack with results" job on the
2026-08-27 and 2026-08-31 daily runs, once profiling 3 variants x 3 pods x 3 profile types
produced 27 flamegraph links in one block. Per-artifact flamegraph links are no longer posted to
Slack at all (see the module docstring), which sidesteps that specific growth path, but the
metrics table remains unbounded (more queries, more/wider variants), so `_cap_mrkdwn` still
guards it.

The script executes its whole body at import time (env vars in, a Slack POST out), so each test
loads a fresh copy via importlib with env vars set and `urlopen` mocked out — no real network
call is made.

Run with:
    pytest .github/scripts/test_post_load_test_results_to_slack.py
"""

import importlib.util
import json
import os
import sys
from unittest import mock

import pytest

_SCRIPT = os.path.join(os.path.dirname(__file__), "post-load-test-results-to-slack.py")


def _variant(key, label, **result_overrides):
    results = {
        "process-instances-started": 123456,
        "process-instances-completed": 123400,
        "throughput-per-second": 42.5,
        "completed-pi-rate-per-second": 42.4,
        "completion-ratio": 99.98,
        "backpressure-percent": 0.01,
        "starter-rate-per-second": 42.6,
        "data-availability": 0.512,
        "request-response-latency": 0.128,
    }
    results.update(result_overrides)
    return {
        "key": key,
        "label": label,
        "namespace": f"c8-medic-daily-2026-08-31-abc1234-test-{key}",
        "soakEndEpoch": 1798700000,
        "results": results,
    }


def _load(monkeypatch, variants, queries_yaml=None):
    """Run the script end-to-end with the given inputs and return the loaded module so tests
    can inspect the `blocks` it built. `urlopen` is mocked — nothing is sent over the network.
    """
    monkeypatch.setenv("VARIANTS_JSON", json.dumps(variants))
    monkeypatch.setenv("BENCHMARK", "medic-daily-2026-08-31-abc1234-test")
    monkeypatch.setenv("REPO", "camunda/camunda")
    monkeypatch.setenv("RUN_ID", "33350392886")
    monkeypatch.setenv("SLACK_WEBHOOK_URL", "https://hooks.slack.example/test")
    if queries_yaml is not None:
        monkeypatch.setenv("QUERIES_YAML", queries_yaml)
    else:
        monkeypatch.delenv("QUERIES_YAML", raising=False)

    spec = importlib.util.spec_from_file_location("post_load_test_results_to_slack", _SCRIPT)
    module = importlib.util.module_from_spec(spec)
    sys.modules["post_load_test_results_to_slack"] = module
    with mock.patch("urllib.request.urlopen") as urlopen:
        urlopen.return_value.__enter__.return_value.status = 200
        spec.loader.exec_module(module)
    return module


def _block_text(module, marker):
    return next(b["text"]["text"] for b in module.blocks if marker in b["text"]["text"])


class TestCapMrkdwn:
    """Direct tests of the helper, independent of which block it's guarding."""

    def test_short_input_passes_through(self, monkeypatch):
        module = _load(monkeypatch, [_variant("grpc", "gRPC")])
        assert module._cap_mrkdwn("header", ["a", "b"]) == "header\na\nb"

    def test_truncates_and_notes_how_many_were_dropped(self, monkeypatch):
        module = _load(monkeypatch, [_variant("grpc", "gRPC")])
        lines = [f"line-{i}" * 50 for i in range(50)]  # comfortably over the limit
        result = module._cap_mrkdwn("header", lines, limit=200, noun="line")
        assert len(result) <= 200
        assert "more line" in result
        assert result.startswith("header\n")

    def test_caps_even_when_the_header_alone_exceeds_the_limit(self, monkeypatch):
        # given: a header so long that even dropping every line (or having none to begin with)
        # can't bring the result under the limit on its own — e.g. a metrics-table header row
        # widened by many/wide variant labels. Regression test for the case Copilot flagged on
        # PR #61506: the old implementation returned `header` (or `header + note`) unmodified
        # here, silently breaking the "never exceeds `limit`" guarantee.
        module = _load(monkeypatch, [_variant("grpc", "gRPC")])
        huge_header = "H" * 500

        assert len(module._cap_mrkdwn(huge_header, [], limit=200)) <= 200
        assert len(module._cap_mrkdwn(huge_header, ["a", "b", "c"], limit=200)) <= 200

    def test_caps_when_header_plus_note_exceeds_the_limit_with_lines_present(self, monkeypatch):
        # given: the header alone fits, but once every line is dropped, header + the trailing
        # "...and N more" note still doesn't.
        module = _load(monkeypatch, [_variant("grpc", "gRPC")])
        header = "H" * 190  # fits comfortably under 200 by itself
        lines = ["x" * 50 for _ in range(10)]  # each far too big to ever be kept

        result = module._cap_mrkdwn(header, lines, limit=200, noun="line")
        assert len(result) <= 200


class TestNoFlamegraphBlock:
    def test_slack_message_never_contains_a_flamegraphs_block(self, monkeypatch):
        # Per-artifact flamegraph links used to be posted as their own block and, with enough
        # profiled pods/variants, blew past Slack's mrkdwn limit (see module docstring). They're
        # no longer posted at all — the "Workflow run" link in the header block covers it.
        module = _load(
            monkeypatch,
            [_variant("grpc", "gRPC"), _variant("rest", "REST"), _variant("none", "None")],
        )
        assert not any("Flamegraphs" in b["text"]["text"] for b in module.blocks)


class TestMetricsTableBlockCap:
    def test_current_headline_queries_pass_through_unchanged(self, monkeypatch):
        module = _load(
            monkeypatch,
            [_variant("grpc", "gRPC"), _variant("rest", "REST"), _variant("none", "None")],
        )
        text = _block_text(module, "```")
        assert len(text) <= module.SLACK_TEXT_LIMIT
        assert "more metric row" not in text

    def test_many_queries_and_variants_still_stays_under_slack_limit(self, monkeypatch, tmp_path):
        # given: a queries.yaml with far more rows than today's 9 headline metrics, to simulate
        # future growth (more queries, or more variants widening every column)
        many_queries = {
            "queries": [
                {
                    "name": f"metric-{i}",
                    "description": f"Some fairly long metric description number {i}",
                    "format": "float",
                }
                for i in range(80)
            ]
        }
        queries_yaml = tmp_path / "queries.yaml"
        queries_yaml.write_text(json.dumps(many_queries))  # valid YAML is valid JSON
        variants = [
            _variant(key, label, **{f"metric-{i}": 1.2345 for i in range(80)})
            for key, label in [("grpc", "gRPC"), ("rest", "REST"), ("none", "None")]
        ]

        # when
        module = _load(monkeypatch, variants, queries_yaml=str(queries_yaml))
        text = _block_text(module, "```")

        # then
        assert len(text) <= module.SLACK_TEXT_LIMIT
        assert "more metric row" in text


if __name__ == "__main__":
    sys.exit(pytest.main([__file__, "-v"]))
