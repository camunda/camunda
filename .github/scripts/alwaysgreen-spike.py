"""Temporary spike safety checks and scenario verdict; do not merge."""

import json
import importlib.util
import os
from pathlib import Path
import socket
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[2]


def drift():
    import yaml

    production = yaml.safe_load(
        (ROOT / ".github/workflows/docker-build-helm-integration.yml").read_text()
    )
    spike = yaml.safe_load(
        (ROOT / ".github/workflows/test-alwaysgreen-caller.yml").read_text()
    )
    assert set(spike[True]) == {"pull_request"}, "Only PR events are allowed"
    assert spike[True]["pull_request"]["branches"] == ["triage-66384-precursor"]
    gate = production["jobs"]["alwaysgreen-triage"]["if"]
    for job in ("caller-after-success", "caller-after-failure"):
        assert spike["jobs"][job]["if"] == gate, f"Gate drift: {job}"
    assert spike["permissions"] == {}
    for job in spike["jobs"].values():
        assert job.get("permissions", {}) in ({}, {"contents": "read"})
        assert "uses" not in job, "No reusable workflows allowed"
        for step in job["steps"]:
            action = step.get("uses", "")
            assert not action or action.startswith(("actions/checkout@", "./.github/actions/print-metadata"))
            assert "secrets." not in json.dumps(step)
    print("Gate drift check passed; production scripts are loaded directly by persistent tests.")


def isolated():
    interfaces = {
        line.split(":")[0].strip()
        for line in Path("/proc/net/dev").read_text().splitlines()[2:]
    }
    assert interfaces == {"lo"}, f"Unexpected network interfaces: {interfaces}"
    with socket.socket() as connection:
        connection.settimeout(1)
        try:
            connection.connect(("1.1.1.1", 443))
        except OSError:
            pass
        else:
            raise AssertionError("Network isolation failed")
    assert not any(key in os.environ for key in ("GH_TOKEN", "GITHUB_TOKEN", "ACTIONS_RUNTIME_TOKEN"))
    print("NETWORK_ISOLATION_CONFIRMED", flush=True)
    subprocess.run(
        [sys.executable, "-m", "pytest", "-q", ".github/scripts/test_alwaysgreen_caller.py"],
        cwd=ROOT, check=True,
    )
    spec = importlib.util.spec_from_file_location(
        "caller_test", ROOT / ".github/scripts/test_alwaysgreen_caller.py"
    )
    tests = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(tests)
    with tempfile.TemporaryDirectory() as directory:
        run = tests.caller.__wrapped__(Path(directory))
        result, calls, _ = run()
        assert result.returncode == 0, result.stderr
        print("CAPTURED_FAKE_DISPATCH=" + calls[-1]["stdin"], flush=True)


def verdict():
    results = json.loads(os.environ["RESULTS"])
    is_draft = os.environ["IS_DRAFT"] == "true"
    expected = {
        "contract-tests": "success",
        "upstream-success": "success",
        "upstream-failure": "failure",
        "caller-after-success": "skipped",
        "caller-after-failure": "skipped" if is_draft else "success",
    }
    actual = {job: value["result"] for job, value in results.items()}
    assert actual == expected, f"Unexpected job results: {actual}; expected: {expected}"
    report = json.dumps({
        "verdict": "PASS",
        "event": os.environ["EVENT_NAME"],
        "draft": is_draft,
        "results": actual,
        "limits": "No authenticated dispatch, fix agent, deployment, or stable rollout was exercised.",
    }, indent=2)
    print(report)
    with open(os.environ["GITHUB_STEP_SUMMARY"], "a") as summary:
        summary.write("```json\n" + report + "\n```\n")


if __name__ == "__main__":
    {"drift": drift, "isolated": isolated, "verdict": verdict}[sys.argv[1]]()
