import json
from pathlib import Path
import pytest
from camunda_load_test_mcp import state as state_module


@pytest.fixture(autouse=True)
def isolated_state_file(tmp_path, monkeypatch):
    state_file = tmp_path / ".camunda-load-tests.json"
    monkeypatch.setattr(state_module, "STATE_FILE", state_file)
    return state_file


def test_write_and_read_entry():
    entry = {
        "run_id": 123,
        "branch": "feature/test",
        "scenario": "typical",
        "image_tag": "1.0.0-abc",
        "started_at": "2026-04-16T10:00:00Z",
        "ttl_days": 1,
    }
    state_module.write_entry("c8-test-20260416", entry)
    result = state_module.read_entry("c8-test-20260416")
    assert result == entry


def test_read_entry_returns_none_when_missing():
    result = state_module.read_entry("c8-nonexistent")
    assert result is None


def test_write_entry_persists_to_file(isolated_state_file):
    state_module.write_entry("c8-ns1", {"branch": "main"})
    data = json.loads(isolated_state_file.read_text())
    assert "c8-ns1" in data
    assert data["c8-ns1"]["branch"] == "main"


def test_list_entries_returns_most_recent(isolated_state_file):
    for i in range(15):
        state_module.write_entry(f"c8-ns{i}", {"branch": f"branch-{i}"})
    entries = state_module.list_entries(limit=10)
    assert len(entries) == 10
    namespaces = [ns for ns, _ in entries]
    assert "c8-ns14" in namespaces


def test_delete_entry_removes_namespace():
    state_module.write_entry("c8-to-delete", {"branch": "feature/x"})
    state_module.delete_entry("c8-to-delete")
    assert state_module.read_entry("c8-to-delete") is None


def test_delete_entry_is_idempotent():
    state_module.delete_entry("c8-never-existed")
    assert state_module.read_entry("c8-never-existed") is None


def test_load_raises_on_corrupt_state_file(isolated_state_file):
    isolated_state_file.write_text("{ not valid json")
    with pytest.raises(RuntimeError, match="corrupt"):
        state_module.read_entry("anything")
