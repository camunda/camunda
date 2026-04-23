import pytest
from unittest.mock import patch
from camunda_load_test_mcp import state as state_module
from camunda_load_test_mcp import github as github_module
from camunda_load_test_mcp.server import (
    start_load_test,
    update_load_test,
    get_load_test_status,
    list_load_tests,
    stop_load_test,
)


@pytest.fixture(autouse=True)
def isolated_state(tmp_path, monkeypatch):
    state_file = tmp_path / ".camunda-load-tests.json"
    monkeypatch.setattr(state_module, "STATE_FILE", state_file)


# ── start_load_test ──────────────────────────────────────────────────────────

def test_start_load_test_dispatches_workflow():
    with patch("camunda_load_test_mcp.server.github.dispatch_workflow") as mock_dispatch, \
         patch("camunda_load_test_mcp.server._find_run_after_dispatch", return_value=None):
        result = start_load_test(branch="feature/my-thing", scenario="typical", ttl_days=1)

    mock_dispatch.assert_called_once()
    call_args = mock_dispatch.call_args
    assert call_args.args[0] == github_module.WORKFLOW_LOAD_TEST
    inputs = call_args.args[1]
    assert inputs["ref"] == "feature/my-thing"
    assert inputs["scenario"] == "typical"
    assert inputs["ttl"] == "1"


def test_start_load_test_derives_namespace_from_branch():
    with patch("camunda_load_test_mcp.server.github.dispatch_workflow"), \
         patch("camunda_load_test_mcp.server._find_run_after_dispatch", return_value=None):
        result = start_load_test(branch="feature/my-thing")

    assert "my-thing" in result
    assert "dashboard.benchmark.camunda.cloud" in result


def test_start_load_test_writes_state():
    with patch("camunda_load_test_mcp.server.github.dispatch_workflow"), \
         patch("camunda_load_test_mcp.server._find_run_after_dispatch", return_value=42):
        start_load_test(branch="feature/x", scenario="latency", ttl_days=3)

    entries = state_module.list_entries(limit=10)
    assert len(entries) == 1
    ns, data = entries[0]
    assert data["branch"] == "feature/x"
    assert data["scenario"] == "latency"
    assert data["run_id"] == 42


def test_start_load_test_uses_custom_name():
    with patch("camunda_load_test_mcp.server.github.dispatch_workflow") as mock_dispatch, \
         patch("camunda_load_test_mcp.server._find_run_after_dispatch", return_value=None):
        start_load_test(branch="feature/x", name="my-custom-name")

    inputs = mock_dispatch.call_args.args[1]
    assert inputs["name"] == "my-custom-name"


# ── update_load_test ─────────────────────────────────────────────────────────

def test_update_load_test_reuses_image_when_requested():
    state_module.write_entry("c8-test-20260416", {
        "run_id": 1,
        "branch": "feature/x",
        "scenario": "typical",
        "image_tag": "8.9.0-abc123",
        "started_at": "2026-04-16T10:00:00Z",
        "ttl_days": 1,
    })

    with patch("camunda_load_test_mcp.server.github.dispatch_workflow") as mock_dispatch, \
         patch("camunda_load_test_mcp.server._find_run_after_dispatch", return_value=None):
        update_load_test(namespace="c8-test-20260416", reuse_image=True)

    inputs = mock_dispatch.call_args.args[1]
    assert inputs["reuse-tag"] == "8.9.0-abc123"


def test_update_load_test_rebuilds_when_reuse_image_false():
    state_module.write_entry("c8-test-20260416", {
        "run_id": 1,
        "branch": "feature/x",
        "scenario": "typical",
        "image_tag": "8.9.0-abc123",
        "started_at": "2026-04-16T10:00:00Z",
        "ttl_days": 1,
    })

    with patch("camunda_load_test_mcp.server.github.dispatch_workflow") as mock_dispatch, \
         patch("camunda_load_test_mcp.server._find_run_after_dispatch", return_value=None):
        update_load_test(namespace="c8-test-20260416", reuse_image=False)

    inputs = mock_dispatch.call_args.args[1]
    assert inputs.get("reuse-tag", "") == ""


def test_update_load_test_raises_when_namespace_unknown():
    with pytest.raises(ValueError, match="c8-unknown"):
        update_load_test(namespace="c8-unknown")


def test_update_load_test_raises_when_reuse_image_but_no_tag():
    state_module.write_entry("c8-test-20260416", {
        "run_id": 1,
        "branch": "feature/x",
        "scenario": "typical",
        "image_tag": "",  # this is what start_load_test writes
        "started_at": "2026-04-16T10:00:00Z",
        "ttl_days": 1,
    })

    with pytest.raises(ValueError, match="image tag is not tracked"):
        update_load_test(namespace="c8-test-20260416", reuse_image=True)


# ── get_load_test_status ─────────────────────────────────────────────────────

def test_get_load_test_status_returns_run_info():
    state_module.write_entry("c8-test-20260416", {
        "run_id": 99,
        "branch": "feature/x",
        "scenario": "typical",
        "image_tag": "1.0.0",
        "started_at": "2026-04-16T10:00:00Z",
        "ttl_days": 1,
    })

    with patch("camunda_load_test_mcp.server.github.get_run_by_id") as mock_run:
        mock_run.return_value = {
            "id": 99,
            "status": "in_progress",
            "conclusion": None,
            "html_url": "https://github.com/camunda/camunda/actions/runs/99",
        }
        result = get_load_test_status(namespace="c8-test-20260416")

    assert "in_progress" in result
    assert "dashboard.benchmark.camunda.cloud" in result
    assert "c8-test-20260416" in result


def test_get_load_test_status_raises_when_namespace_unknown():
    with pytest.raises(ValueError, match="c8-unknown"):
        get_load_test_status(namespace="c8-unknown")


# ── list_load_tests ──────────────────────────────────────────────────────────

def test_list_load_tests_shows_entries():
    state_module.write_entry("c8-ns1", {
        "run_id": 1,
        "branch": "feature/a",
        "scenario": "typical",
        "image_tag": "1.0",
        "started_at": "2026-04-16T10:00:00Z",
        "ttl_days": 1,
    })
    state_module.write_entry("c8-ns2", {
        "run_id": 2,
        "branch": "feature/b",
        "scenario": "latency",
        "image_tag": "1.0",
        "started_at": "2026-04-16T11:00:00Z",
        "ttl_days": 3,
    })

    with patch("camunda_load_test_mcp.server.github.get_run_by_id") as mock_run:
        mock_run.return_value = {
            "status": "completed",
            "conclusion": "success",
            "html_url": "https://github.com/actions/runs/1",
        }
        result = list_load_tests(limit=10)

    assert "c8-ns1" in result
    assert "c8-ns2" in result
    assert "feature/a" in result


def test_list_load_tests_empty_state():
    result = list_load_tests(limit=10)
    assert "no load tests" in result.lower()


def test_list_load_tests_shows_unknown_status_when_api_fails():
    state_module.write_entry("c8-ns1", {
        "run_id": 1,
        "branch": "feature/a",
        "scenario": "typical",
        "image_tag": "1.0",
        "started_at": "2026-04-16T10:00:00Z",
        "ttl_days": 1,
    })

    with patch("camunda_load_test_mcp.server.github.get_run_by_id") as mock_run:
        mock_run.side_effect = RuntimeError("API error")
        result = list_load_tests(limit=10)

    assert "c8-ns1" in result
    assert "unknown" in result


# ── stop_load_test ───────────────────────────────────────────────────────────

def test_stop_load_test_triggers_cleanup_workflow():
    with patch("camunda_load_test_mcp.server.github.dispatch_workflow") as mock_dispatch:
        result = stop_load_test(namespace="c8-test-20260416")

    mock_dispatch.assert_called_once()
    assert mock_dispatch.call_args.args[0] == github_module.WORKFLOW_CLEANUP


def test_stop_load_test_result_contains_warning():
    with patch("camunda_load_test_mcp.server.github.dispatch_workflow"):
        result = stop_load_test(namespace="c8-test-20260416")

    assert "all" in result.lower() or "namespaces" in result.lower()
