import json
import os
import tempfile
from pathlib import Path

STATE_FILE = Path.home() / ".camunda-load-tests.json"


def _load() -> dict:
    if not STATE_FILE.exists():
        return {}
    try:
        with open(STATE_FILE) as f:
            return json.load(f)
    except json.JSONDecodeError as e:
        raise RuntimeError(
            f"State file {STATE_FILE} is corrupt and cannot be parsed: {e}"
        ) from e


def _save(data: dict) -> None:
    dir_ = STATE_FILE.parent
    dir_.mkdir(parents=True, exist_ok=True)
    with tempfile.NamedTemporaryFile("w", dir=dir_, delete=False, suffix=".tmp") as tmp:
        json.dump(data, tmp, indent=2)
        tmp_path = tmp.name
    os.replace(tmp_path, STATE_FILE)


def write_entry(namespace: str, entry: dict) -> None:
    data = _load()
    data.pop(namespace, None)  # remove to reset insertion order
    data[namespace] = entry
    _save(data)


def read_entry(namespace: str) -> dict | None:
    return _load().get(namespace)


def list_entries(limit: int = 10) -> list[tuple[str, dict]]:
    data = _load()
    items = list(data.items())
    return items[-limit:]


def delete_entry(namespace: str) -> None:
    data = _load()
    data.pop(namespace, None)
    _save(data)
