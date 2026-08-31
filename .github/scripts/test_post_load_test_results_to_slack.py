"""Unit tests for post-load-test-results-to-slack.py — specifically that the metrics-table and
flamegraph-links `mrkdwn` blocks it builds always stay under Slack's ~3000-char text limit, no
matter how much data (queries, variants, profiled pods) they're built from.

Slack rejects the *entire* `blocks` payload with `invalid_blocks` if any single block's text
exceeds that limit — this is exactly what broke the "Notify Slack with results" job on the
2026-08-27 and 2026-08-31 daily runs, once profiling 3 variants x 3 pods x 3 profile types
produced 27 flamegraph links in one block.

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


def _flamegraph_bullets(n):
    return "\n".join(
        f"• <https://github.com/camunda/camunda/actions/runs/33350392886/artifacts/{9700000000 + i}"
        f"|flamegraph-medic-daily-2026-08-31-abc1234-test-grpc-cpu-camunda-{i % 3}-20260831>"
        for i in range(n)
    )


def _load(monkeypatch, variants, flamegraph_links="", queries_yaml=None):
    """Run the script end-to-end with the given inputs and return the loaded module so tests
    can inspect the `blocks` it built. `urlopen` is mocked — nothing is sent over the network.
    """
    monkeypatch.setenv("VARIANTS_JSON", json.dumps(variants))
    monkeypatch.setenv("BENCHMARK", "medic-daily-2026-08-31-abc1234-test")
    monkeypatch.setenv("REPO", "camunda/camunda")
    monkeypatch.setenv("RUN_ID", "33350392886")
    monkeypatch.setenv("SLACK_WEBHOOK_URL", "https://hooks.slack.example/test")
    monkeypatch.setenv("FLAMEGRAPH_LINKS", flamegraph_links)
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


class TestFlamegraphBlockCap:
    def test_few_links_pass_through_unchanged(self, monkeypatch):
        links = _flamegraph_bullets(3)
        module = _load(monkeypatch, [_variant("grpc", "gRPC")], flamegraph_links=links)
        text = _block_text(module, "Flamegraphs")
        assert text == f":fire: *Flamegraphs:*\n{links}"

    def test_27_links_like_the_broken_daily_runs_stays_under_slack_limit(self, monkeypatch):
        # given: the exact shape that broke runs 33080825631 (08-27) and 33350392886 (08-31) —
        # 3 variants x 3 pods x 3 profile types (cpu/wall/alloc) = 27 flamegraph artifacts
        links = _flamegraph_bullets(27)
        assert len(f":fire: *Flamegraphs:*\n{links}") > 3000  # sanity: this used to break Slack

        # when
        module = _load(
            monkeypatch,
            [_variant("grpc", "gRPC"), _variant("rest", "REST"), _variant("none", "None")],
            flamegraph_links=links,
        )
        text = _block_text(module, "Flamegraphs")

        # then
        assert len(text) <= module.SLACK_TEXT_LIMIT
        assert "more flamegraph link" in text

    def test_never_exceeds_limit_across_a_range_of_artifact_counts(self, monkeypatch):
        for n in (0, 1, 26, 27, 28, 60, 200):
            links = _flamegraph_bullets(n)
            module = _load(monkeypatch, [_variant("grpc", "gRPC")], flamegraph_links=links)
            has_flamegraph_block = any(
                "Flamegraphs" in b["text"]["text"] for b in module.blocks
            )
            if n == 0:
                assert not has_flamegraph_block  # empty FLAMEGRAPH_LINKS omits the block
            else:
                assert len(_block_text(module, "Flamegraphs")) <= module.SLACK_TEXT_LIMIT


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
