"""Unit tests for post-load-test-results-to-slack.py

Regression coverage for the "400 invalid_blocks" failure: Slack caps a section block's
mrkdwn text.text at 3000 characters. The flamegraph-links block used to itemize one bullet
line per flamegraph artifact, which grew with (variants x pods x profiler events) -- 27
lines (one third daily variant added on top of the existing two) pushed it past that cap.
The fix has two parts: the calling workflow now renders the flamegraph block as a single
link to the run's artifacts page instead of one line per artifact (structurally incapable
of hitting the cap), and this script's text_blocks() defensively splits any block that
still manages to exceed the cap (dash_links, the results table) into multiple blocks on
line boundaries.

Run with:
    pytest .github/scripts/test_post_load_test_results_to_slack.py
"""

import importlib.util
import json
import os
import tempfile
from unittest import mock

import yaml

_SCRIPT = os.path.join(os.path.dirname(__file__), "post-load-test-results-to-slack.py")

# A couple of small queries.yaml entries are enough to drive the table -- the
# real file's row/column formatting isn't what's under test here.
_QUERIES_YAML = {
    "queries": [
        {"name": "throughput", "description": "Throughput", "format": "float", "decimals": 1, "unit": "PI/s"},
        {"name": "completed", "description": "Completed instances", "format": "integer"},
    ]
}

_SLACK_TEXT_LIMIT = 3000


def _run(variants, flamegraph_links="", benchmark="medic-daily-2026-08-31-bd6cbb8f-test"):
    """Execute the script fresh with the given inputs and return the JSON payload
    it would have posted to Slack (urlopen is patched to capture, not send)."""
    with tempfile.NamedTemporaryFile("w", suffix=".yaml", delete=False) as f:
        yaml.safe_dump(_QUERIES_YAML, f)
        queries_path = f.name

    env = {
        "VARIANTS_JSON": json.dumps(variants),
        "BENCHMARK": benchmark,
        "REPO": "camunda/camunda",
        "RUN_ID": "33350392886",
        "SLACK_WEBHOOK_URL": "https://hooks.example.invalid/fake",
        "FLAMEGRAPH_LINKS": flamegraph_links,
        "QUERIES_YAML": queries_path,
    }

    captured = {}

    class _FakeResponse:
        status = 200

        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

    def _fake_urlopen(req, timeout=30):
        captured["body"] = req.data
        return _FakeResponse()

    try:
        with mock.patch.dict(os.environ, env, clear=False), \
                mock.patch("urllib.request.urlopen", _fake_urlopen):
            # A fresh module object per call: the script runs its whole payload-
            # building-and-posting flow at import time (no `if __name__` guard),
            # so each scenario needs its own exec rather than a cached import.
            spec = importlib.util.spec_from_file_location("post_load_test_results_to_slack", _SCRIPT)
            module = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(module)
    finally:
        os.unlink(queries_path)

    return json.loads(captured["body"])


def _variant(key, label, namespace_suffix, soak_end_epoch=1756612345, results=None):
    return {
        "key": key,
        "label": label,
        "namespace": f"c8-medic-daily-2026-08-31-bd6cbb8f-test-{namespace_suffix}",
        "soakEndEpoch": soak_end_epoch,
        "results": results if results is not None else {"throughput": 123.4, "completed": 1234000},
    }


def _flamegraph_link():
    # Matches what the `List flamegraph artifacts` workflow step actually emits: one link to
    # the run's artifacts page, not one line per flamegraph artifact (see module docstring).
    run_url = "https://github.com/camunda/camunda/actions/runs/33350392886"
    return f"<{run_url}|View in this run's artifacts>"


def _assert_all_blocks_valid(blocks, max_blocks=50):
    assert len(blocks) <= max_blocks, f"{len(blocks)} blocks exceeds Slack's {max_blocks}-block limit"
    for i, block in enumerate(blocks):
        text = block.get("text", {}).get("text", "")
        assert text, f"block {i} ({block.get('type')}) has empty text -- Slack rejects this"
        assert len(text) <= _SLACK_TEXT_LIMIT, (
            f"block {i} ({block.get('type')}) text is {len(text)} chars, "
            f"exceeds Slack's {_SLACK_TEXT_LIMIT}-char limit"
        )


class TestBlockLimits:
    def test_three_variants_with_full_results_produce_valid_blocks(self):
        variants = [
            _variant("grpc", "gRPC", "grpc"),
            _variant("rest", "REST", "rest"),
            _variant("none", "None", "none"),
        ]
        payload = _run(variants)
        _assert_all_blocks_valid(payload["blocks"])

    def test_variant_with_empty_results_still_produces_non_empty_blocks(self):
        # given: a variant that scraped no data at all (e.g. no-secondary-storage
        # queries that all came back empty) -- results is {}, not omitted.
        variants = [
            _variant("grpc", "gRPC", "grpc"),
            _variant("none", "None", "none", results={}),
        ]
        payload = _run(variants)
        _assert_all_blocks_valid(payload["blocks"])

    def test_zero_variants_omits_dash_links_block_but_stays_valid(self):
        # given: the defensive path -- the calling workflow already gates on a
        # non-empty variants-json, but the script guards against it too.
        payload = _run([])
        _assert_all_blocks_valid(payload["blocks"])

    def test_flamegraph_link_renders_as_a_single_short_block_regardless_of_variant_count(self):
        # given: the real shape FLAMEGRAPH_LINKS now takes -- a single link to the run's
        # artifacts page, built once by the workflow regardless of how many variants, pods,
        # or profiler events actually ran (see module docstring for what itemizing every
        # artifact used to cost). Three variants here to also confirm the link's size is
        # independent of variant count, not just small by coincidence.
        variants = [
            _variant("grpc", "gRPC", "grpc"),
            _variant("rest", "REST", "rest"),
            _variant("none", "None", "none"),
        ]
        flamegraph_link = _flamegraph_link()

        payload = _run(variants, flamegraph_links=flamegraph_link)

        _assert_all_blocks_valid(payload["blocks"])
        flamegraph_blocks = [
            b for b in payload["blocks"] if "Flamegraphs" in b.get("text", {}).get("text", "")
        ]
        assert len(flamegraph_blocks) == 1, "a single link must never need splitting"
        assert flamegraph_link in flamegraph_blocks[0]["text"]["text"]

    def test_no_flamegraph_links_omits_the_flamegraphs_block(self):
        payload = _run([_variant("grpc", "gRPC", "grpc")], flamegraph_links="")
        _assert_all_blocks_valid(payload["blocks"])
        assert not any("Flamegraphs" in b.get("text", {}).get("text", "") for b in payload["blocks"])

    def test_oversized_table_splits_into_independently_fenced_blocks(self):
        # given: enough metric rows that the table itself (not just the flamegraph list)
        # exceeds the 3000-char cap -- each resulting block must carry its own opening and
        # closing ``` fence, since a chunk that only got one half of the fence would render
        # as a broken/unterminated code block in Slack.
        many_queries = {
            "queries": [
                {"name": f"metric-{i}", "description": f"Metric number {i} description", "format": "integer"}
                for i in range(80)
            ]
        }
        with tempfile.NamedTemporaryFile("w", suffix=".yaml", delete=False) as f:
            yaml.safe_dump(many_queries, f)
            queries_path = f.name

        variants = [
            _variant("grpc", "gRPC", "grpc", results={f"metric-{i}": i for i in range(80)}),
            _variant("rest", "REST", "rest", results={f"metric-{i}": i for i in range(80)}),
            _variant("none", "None", "none", results={f"metric-{i}": i for i in range(80)}),
        ]

        env = {
            "VARIANTS_JSON": json.dumps(variants),
            "BENCHMARK": "medic-daily-2026-08-31-bd6cbb8f-test",
            "REPO": "camunda/camunda",
            "RUN_ID": "33350392886",
            "SLACK_WEBHOOK_URL": "https://hooks.example.invalid/fake",
            "FLAMEGRAPH_LINKS": "",
            "QUERIES_YAML": queries_path,
        }
        captured = {}

        class _FakeResponse:
            status = 200

            def __enter__(self):
                return self

            def __exit__(self, *exc):
                return False

        def _fake_urlopen(req, timeout=30):
            captured["body"] = req.data
            return _FakeResponse()

        try:
            with mock.patch.dict(os.environ, env, clear=False), \
                    mock.patch("urllib.request.urlopen", _fake_urlopen):
                spec = importlib.util.spec_from_file_location("post_load_test_results_to_slack", _SCRIPT)
                module = importlib.util.module_from_spec(spec)
                spec.loader.exec_module(module)
        finally:
            os.unlink(queries_path)

        payload = json.loads(captured["body"])
        _assert_all_blocks_valid(payload["blocks"])

        table_blocks = [
            b for b in payload["blocks"] if "```" in b.get("text", {}).get("text", "")
        ]
        assert len(table_blocks) >= 2, "fixture must reproduce a table that needs splitting"
        for block in table_blocks:
            text = block["text"]["text"]
            assert text.startswith("```\n") and text.endswith("\n```"), (
                "each split table chunk must carry its own opening and closing fence: "
                f"{text[:20]!r}...{text[-20:]!r}"
            )
