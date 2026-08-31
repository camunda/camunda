"""Unit tests for post-load-test-results-to-slack.py

Regression coverage for the "400 invalid_blocks" failure: Slack caps a section
block's mrkdwn text.text at 3000 characters, and the flamegraph-links block
grows with (variants x pods x profiler events) -- 27 lines (one third daily
variant added on top of the existing two) pushed it past that cap. See
text_blocks() in the script under test.

Also covers the two posting modes: an incoming webhook (SLACK_WEBHOOK_URL,
one message, no threading -- webhooks don't return a message `ts`) and a
chat.postMessage bot token (SLACK_BOT_TOKEN + SLACK_CHANNEL, which does
return a `ts`, letting the flamegraph list go out as a threaded reply
instead of lengthening the results message).

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


def _write_queries(queries_doc):
    with tempfile.NamedTemporaryFile("w", suffix=".yaml", delete=False) as f:
        yaml.safe_dump(queries_doc, f)
        return f.name


def _post_and_capture(env, respond=None):
    """Execute the script fresh against `env` and return the list of outgoing calls as
    [{"url", "payload", "headers"}, ...] (urlopen is patched to capture, not send).

    `respond(url, payload)`, if given, returns the raw response body bytes for a call;
    the default simulates a real Slack response: {"ok": true, "ts": "<n>"} for a
    chat.postMessage call (each call gets its own increasing ts, like Slack does), and
    the plain "ok" text an incoming webhook replies with.
    """
    calls = []
    ts_seq = iter(f"1700000000.{i:06d}" for i in range(1, 1000))

    def _default_respond(url, payload):
        if url.startswith("https://slack.com/api/"):
            return json.dumps({"ok": True, "ts": next(ts_seq)}).encode()
        return b"ok"

    respond = respond or _default_respond

    class _FakeResponse:
        def __init__(self, body):
            self.status = 200
            self._body = body

        def __enter__(self):
            return self

        def __exit__(self, *exc):
            return False

        def read(self):
            return self._body

    def _fake_urlopen(req, timeout=30):
        payload = json.loads(req.data)
        calls.append({"url": req.full_url, "payload": payload, "headers": dict(req.header_items())})
        return _FakeResponse(respond(req.full_url, payload))

    with mock.patch.dict(os.environ, env, clear=False), \
            mock.patch("urllib.request.urlopen", _fake_urlopen):
        # A fresh module object per call: the script runs its whole payload-
        # building-and-posting flow at import time (no `if __name__` guard),
        # so each scenario needs its own exec rather than a cached import.
        spec = importlib.util.spec_from_file_location("post_load_test_results_to_slack", _SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)

    return calls


def _base_env(variants, flamegraph_links, benchmark, queries_path):
    return {
        "VARIANTS_JSON": json.dumps(variants),
        "BENCHMARK": benchmark,
        "REPO": "camunda/camunda",
        "RUN_ID": "33350392886",
        "FLAMEGRAPH_LINKS": flamegraph_links,
        "QUERIES_YAML": queries_path,
    }


def _run(variants, flamegraph_links="", benchmark="medic-daily-2026-08-31-bd6cbb8f-test", queries=None):
    """Webhook mode: exactly one outgoing call. Returns that call's JSON payload, i.e.
    what the script would have posted to Slack."""
    queries_path = _write_queries(queries or _QUERIES_YAML)
    try:
        env = _base_env(variants, flamegraph_links, benchmark, queries_path)
        env["SLACK_WEBHOOK_URL"] = "https://hooks.example.invalid/fake"
        calls = _post_and_capture(env)
    finally:
        os.unlink(queries_path)
    assert len(calls) == 1, "webhook mode must post exactly one message"
    return calls[0]["payload"]


def _run_bot(
    variants,
    flamegraph_links="",
    benchmark="medic-daily-2026-08-31-bd6cbb8f-test",
    channel="C0123456789",
    queries=None,
    respond=None,
):
    """Bot-token + channel mode: returns the full list of outgoing chat.postMessage calls."""
    queries_path = _write_queries(queries or _QUERIES_YAML)
    try:
        env = _base_env(variants, flamegraph_links, benchmark, queries_path)
        env["SLACK_BOT_TOKEN"] = "xoxb-fake-token"
        env["SLACK_CHANNEL"] = channel
        return _post_and_capture(env, respond=respond)
    finally:
        os.unlink(queries_path)


def _variant(key, label, namespace_suffix, soak_end_epoch=1756612345, results=None):
    return {
        "key": key,
        "label": label,
        "namespace": f"c8-medic-daily-2026-08-31-bd6cbb8f-test-{namespace_suffix}",
        "soakEndEpoch": soak_end_epoch,
        "results": results if results is not None else {"throughput": 123.4, "completed": 1234000},
    }


def _flamegraph_lines(n):
    run_url = "https://github.com/camunda/camunda/actions/runs/33350392886"
    return "\n".join(
        f"• <{run_url}/artifacts/{9000000000 + i}|flamegraph-medic-daily-2026-08-31-bd6cbb8f-test-variant-{i}-cpu-camunda-0-20260831>"
        for i in range(n)
    )


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
    """Webhook-mode block-splitting/validity coverage (posting mechanism itself is
    covered separately below)."""

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

    def test_oversized_flamegraph_list_is_split_across_multiple_blocks(self):
        # given: the exact regression scenario -- 3 variants x 3 pods x 3 profiler
        # events = 27 flamegraph lines, which is what actually broke production
        # (2 variants/18 lines = 2937 chars, just under the cap; 3 variants/27
        # lines = 4395 chars, over it).
        variants = [
            _variant("grpc", "gRPC", "grpc"),
            _variant("rest", "REST", "rest"),
            _variant("none", "None", "none"),
        ]
        flamegraph_links = _flamegraph_lines(27)
        assert len(flamegraph_links) > _SLACK_TEXT_LIMIT, "test fixture must reproduce the over-limit case"

        payload = _run(variants, flamegraph_links=flamegraph_links)

        _assert_all_blocks_valid(payload["blocks"])
        # and: the flamegraph content must actually have been split into more
        # than one block, not silently truncated or dropped.
        flamegraph_blocks = [
            b for b in payload["blocks"]
            if "Flamegraphs" in b.get("text", {}).get("text", "")
            or "flamegraph-medic-daily" in b.get("text", {}).get("text", "")
        ]
        assert len(flamegraph_blocks) >= 2
        rebuilt = "\n".join(b["text"]["text"] for b in flamegraph_blocks)
        for i in range(27):
            assert f"flamegraph-medic-daily-2026-08-31-bd6cbb8f-test-variant-{i}-cpu-camunda-0-20260831" in rebuilt

    def test_small_flamegraph_list_stays_in_one_block(self):
        # given: the pre-regression, under-the-cap case (2 variants worth of links)
        variants = [_variant("grpc", "gRPC", "grpc")]
        flamegraph_links = _flamegraph_lines(18)
        assert len(flamegraph_links) <= _SLACK_TEXT_LIMIT

        payload = _run(variants, flamegraph_links=flamegraph_links)

        _assert_all_blocks_valid(payload["blocks"])
        flamegraph_blocks = [
            b for b in payload["blocks"] if "Flamegraphs" in b.get("text", {}).get("text", "")
        ]
        assert len(flamegraph_blocks) == 1

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
        variants = [
            _variant("grpc", "gRPC", "grpc", results={f"metric-{i}": i for i in range(80)}),
            _variant("rest", "REST", "rest", results={f"metric-{i}": i for i in range(80)}),
            _variant("none", "None", "none", results={f"metric-{i}": i for i in range(80)}),
        ]

        payload = _run(variants, queries=many_queries)

        _assert_all_blocks_valid(payload["blocks"])
        table_blocks = [b for b in payload["blocks"] if "```" in b.get("text", {}).get("text", "")]
        assert len(table_blocks) >= 2, "fixture must reproduce a table that needs splitting"
        for block in table_blocks:
            text = block["text"]["text"]
            assert text.startswith("```\n") and text.endswith("\n```"), (
                "each split table chunk must carry its own opening and closing fence: "
                f"{text[:20]!r}...{text[-20:]!r}"
            )


class TestPostingModes:
    """Covers the webhook-vs-bot-token posting mechanism itself, including threading
    the flamegraph list as a reply under the results message in bot-token mode."""

    def test_webhook_mode_posts_everything_in_one_message(self):
        variants = [_variant("grpc", "gRPC", "grpc")]

        # _run only returns the parsed payload; capture the raw call here too, to also
        # confirm it went to the webhook URL with no Authorization header.
        env_queries_path = _write_queries(_QUERIES_YAML)
        try:
            env = _base_env(variants, _flamegraph_lines(3), "medic-daily-2026-08-31-bd6cbb8f-test", env_queries_path)
            env["SLACK_WEBHOOK_URL"] = "https://hooks.example.invalid/fake"
            calls = _post_and_capture(env)
        finally:
            os.unlink(env_queries_path)

        assert len(calls) == 1
        assert calls[0]["url"] == "https://hooks.example.invalid/fake"
        assert "Authorization" not in calls[0]["headers"]
        text = "\n".join(b["text"]["text"] for b in calls[0]["payload"]["blocks"])
        assert "```" in text  # the table
        assert "flamegraph-medic-daily" in text  # the flamegraph links, same message

    def test_bot_token_mode_threads_flamegraphs_under_the_results_message(self):
        variants = [
            _variant("grpc", "gRPC", "grpc"),
            _variant("rest", "REST", "rest"),
            _variant("none", "None", "none"),
        ]
        calls = _run_bot(variants, flamegraph_links=_flamegraph_lines(3), channel="C0BTN9X04PP")

        assert len(calls) == 2, "results message and threaded flamegraph reply are two separate calls"

        first, second = calls
        assert first["url"] == "https://slack.com/api/chat.postMessage"
        assert first["payload"]["channel"] == "C0BTN9X04PP"
        assert "thread_ts" not in first["payload"], "the results message must not itself be a reply"
        assert first["headers"]["Authorization"] == "Bearer xoxb-fake-token"
        first_text = "\n".join(b["text"]["text"] for b in first["payload"]["blocks"])
        assert "```" in first_text  # the results table ships in the first message
        assert "flamegraph-medic-daily" not in first_text  # but not the flamegraph links

        assert second["url"] == "https://slack.com/api/chat.postMessage"
        assert second["payload"]["channel"] == "C0BTN9X04PP"
        # and: the reply is threaded under the first message's returned ts specifically.
        assert second["payload"]["thread_ts"] == "1700000000.000001"
        second_text = "\n".join(b["text"]["text"] for b in second["payload"]["blocks"])
        assert "flamegraph-medic-daily" in second_text

        _assert_all_blocks_valid(first["payload"]["blocks"])
        _assert_all_blocks_valid(second["payload"]["blocks"])

    def test_bot_token_mode_without_flamegraphs_posts_only_the_results_message(self):
        # given: no flamegraph links at all -- nothing to thread, so no second call.
        variants = [_variant("grpc", "gRPC", "grpc")]
        calls = _run_bot(variants, flamegraph_links="")
        assert len(calls) == 1
        assert "thread_ts" not in calls[0]["payload"]

    def test_bot_token_mode_raises_when_slack_rejects_the_results_message(self):
        variants = [_variant("grpc", "gRPC", "grpc")]

        def _reject_first_call(url, payload):
            return json.dumps({"ok": False, "error": "not_in_channel"}).encode()

        try:
            _run_bot(variants, flamegraph_links=_flamegraph_lines(3), respond=_reject_first_call)
            assert False, "expected a SystemExit when Slack rejects the results message"
        except SystemExit as e:
            assert "not_in_channel" in str(e)

    def test_bot_token_mode_raises_when_slack_rejects_the_threaded_reply(self):
        variants = [_variant("grpc", "gRPC", "grpc")]
        calls_seen = {"n": 0}

        def _reject_second_call(url, payload):
            calls_seen["n"] += 1
            if calls_seen["n"] == 1:
                return json.dumps({"ok": True, "ts": "1700000000.000001"}).encode()
            return json.dumps({"ok": False, "error": "thread_not_found"}).encode()

        try:
            _run_bot(variants, flamegraph_links=_flamegraph_lines(3), respond=_reject_second_call)
            assert False, "expected a SystemExit when Slack rejects the threaded reply"
        except SystemExit as e:
            assert "thread_not_found" in str(e)

    def test_missing_both_posting_modes_fails_fast(self):
        queries_path = _write_queries(_QUERIES_YAML)
        try:
            env = _base_env([_variant("grpc", "gRPC", "grpc")], "", "medic-daily-2026-08-31-bd6cbb8f-test", queries_path)
            # given: neither SLACK_WEBHOOK_URL nor SLACK_BOT_TOKEN/SLACK_CHANNEL is set
            try:
                _post_and_capture(env)
                assert False, "expected a SystemExit when no posting mode is configured"
            except SystemExit as e:
                assert "SLACK_WEBHOOK_URL" in str(e)
        finally:
            os.unlink(queries_path)
