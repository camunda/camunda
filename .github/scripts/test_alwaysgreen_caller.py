"""Test the Docker/Helm triage caller without credentials or API requests.

Run with: pytest .github/scripts/test_alwaysgreen_caller.py
"""

import json
from pathlib import Path
import shutil
import subprocess
import sys

import pytest
import yaml


ROOT = Path(__file__).resolve().parents[2]
WORKFLOW_PATH = ".github/workflows/docker-build-helm-integration.yml"
WORKFLOW = yaml.safe_load((ROOT / WORKFLOW_PATH).read_text())
JOB = WORKFLOW["jobs"]["alwaysgreen-triage"]
GUARD = next(step for step in JOB["steps"] if step["name"].startswith("Refuse"))
DISPATCH = next(step for step in JOB["steps"] if step["name"].startswith("Hand"))
RECEIVER = "repos/camunda/c8-cross-component-e2e-tests"
GET = ["api", f"{RECEIVER}/actions/workflows/alwaysgreen-triage.yml", "--jq", ".state"]
POST = [
    "api", "--method", "POST", "-H", "Accept: application/vnd.github+json",
    f"/{RECEIVER}/dispatches", "--input", "-",
]


@pytest.fixture
def caller(tmp_path):
    # Only the fake CLI and the real JSON encoder are available to the scripts.
    tools = tmp_path / "bin"
    tools.mkdir()
    (tools / "jq").symlink_to(shutil.which("jq"))
    cli = tools / "gh"
    cli.write_text(
        f"#!{sys.executable}\n"
        "import json, os, sys\n"
        f"get, post = {GET!r}, {POST!r}\n"
        "args = sys.argv[1:]\n"
        "if args not in (get, post):\n"
        "    sys.exit('Unexpected API command: ' + repr(args))\n"
        "with open(os.environ['CAPTURE'], 'a') as capture:\n"
        "    capture.write(json.dumps({'args': args, 'stdin': "
        "sys.stdin.read() if args == post else ''}) + '\\n')\n"
        "if args == get:\n"
        "    print(os.environ['RECEIVER_STATE'])\n"
        "    sys.exit(int(os.environ['GET_EXIT']))\n"
        "sys.exit(int(os.environ['POST_EXIT']))\n"
    )
    cli.chmod(0o700)
    env = {
        "PATH": str(tools),
        "HOME": str(tmp_path),
        "CAPTURE": str(tmp_path / "calls"),
        "GITHUB_STEP_SUMMARY": str(tmp_path / "summary"),
        "GITHUB_REPOSITORY": "camunda/camunda",
        "GITHUB_RUN_ID": "123456789",
        "GITHUB_REF_NAME": "stable/8.10",
        "GH_TOKEN": "test-only",
        "RECEIVER_STATE": "active",
        "GET_EXIT": "0",
        "POST_EXIT": "0",
    }

    def run(**overrides):
        result = subprocess.run(
            [shutil.which("bash"), "--noprofile", "--norc", "-c",
             GUARD["run"] + "\n" + DISPATCH["run"]],
            env={**env, **overrides},
            cwd=tmp_path,
            stdin=subprocess.DEVNULL,
            capture_output=True,
            text=True,
            timeout=10,
        )
        capture = Path(env["CAPTURE"])
        calls = [json.loads(line) for line in capture.read_text().splitlines()] if capture.exists() else []
        summary = Path(env["GITHUB_STEP_SUMMARY"])
        return result, calls, summary.read_text() if summary.exists() else ""

    return run


@pytest.mark.parametrize("base_ref", [
    "main", "stable/8.10", "gh-readonly-queue/stable/8.10/pr-42-abc",
    'branch/$(exit 99)"quoted',
])
def test_should_dispatch_exact_payload(caller, base_ref):
    result, calls, summary = caller(
        GITHUB_REF_NAME=base_ref, SLACK_TS="123.456", SLACK_CHANNEL="C123",
    )
    assert result.returncode == 0, result.stderr
    assert [call["args"] for call in calls] == [GET, POST]
    assert json.loads(calls[1]["stdin"]) == {
        "event_type": "alwaysgreen-triage",
        "client_payload": {
            "source_repo": "camunda/camunda", "run_id": "123456789",
            "base_ref": base_ref, "slack_ts": "123.456", "slack_channel": "C123",
        },
    }
    assert summary == "Handed run 123456789 to the central triage.\n"


@pytest.mark.parametrize("state", [
    "disabled_manually", "disabled_inactivity", "deleted", "unknown", "",
])
def test_should_reject_inactive_receiver(caller, state):
    result, calls, summary = caller(RECEIVER_STATE=state)
    assert result.returncode != 0
    assert "::error::" in result.stdout
    assert [call["args"] for call in calls] == [GET]
    assert not summary


def test_should_fail_closed_on_receiver_api_error(caller):
    result, calls, summary = caller(GET_EXIT="1", RECEIVER_STATE="")
    assert result.returncode != 0
    assert "::error::" in result.stdout
    assert [call["args"] for call in calls] == [GET]
    assert not summary


def test_should_not_report_success_on_dispatch_error(caller):
    result, calls, summary = caller(POST_EXIT="1")
    assert result.returncode != 0
    assert [call["args"] for call in calls] == [GET, POST]
    assert not summary


@pytest.mark.parametrize("slack", ["", 'thread"\\\n$(exit 99)'])
def test_should_preserve_empty_or_special_slack_context(caller, slack):
    result, calls, _ = caller(SLACK_TS=slack, SLACK_CHANNEL=slack)
    assert result.returncode == 0, result.stderr
    payload = json.loads(calls[1]["stdin"])["client_payload"]
    assert payload["slack_ts"] == slack
    assert payload["slack_channel"] == slack


def test_should_default_missing_slack_context_to_empty_strings(caller):
    result, calls, _ = caller()
    assert result.returncode == 0, result.stderr
    payload = json.loads(calls[1]["stdin"])["client_payload"]
    assert payload["slack_ts"] == payload["slack_channel"] == ""


def test_should_preserve_failure_gate_and_slack_precedence():
    assert " ".join(JOB["if"].split()) == (
        "${{ always() && contains(needs.*.result, 'failure') "
        "&& !github.event.pull_request.draft }}"
    )
    assert JOB["needs"] == [
        "should-run", "preflight", "build-operate-frontend", "build-unified-webapp-frontend",
        "build-identity-frontend", "build-and-push", "format-identifier", "helm-deploy-status",
        "trigger-saas-e2e",
    ]
    for variable, output in [("SLACK_TS", "slack_ts"), ("SLACK_CHANNEL", "slack_channel")]:
        assert DISPATCH["env"][variable] == (
            "${{ needs.helm-deploy-status.outputs." + output
            + " || needs.trigger-saas-e2e.outputs." + output + " }}"
        )


def test_should_not_interpolate_actions_expressions_into_shell_scripts():
    for step in (GUARD, DISPATCH):
        assert "${{" not in step["run"]


def test_should_use_restricted_token_and_observable_failure():
    assert not ({"uses", "secrets", "with"} & JOB.keys())
    assert JOB["permissions"] == {"contents": "read"}
    assert JOB["timeout-minutes"] == 5
    for item in (JOB, GUARD, DISPATCH):
        assert not item.get("continue-on-error", False)
    checkout = JOB["steps"][0]
    assert checkout["with"]["persist-credentials"] is False
    assert checkout["with"]["sparse-checkout"] == ".github"
    token = next(step for step in JOB["steps"] if step.get("id") == "github-token")
    assert token["with"]["owner"] == "camunda"
    assert token["with"]["repositories"] == "c8-cross-component-e2e-tests"
    assert GUARD["env"]["GH_TOKEN"] == DISPATCH["env"]["GH_TOKEN"] == (
        "${{ steps.github-token.outputs.token }}"
    )
    observer = JOB["steps"][-1]
    assert observer["uses"] == "./.github/actions/observe-build-status"
    assert observer["if"] == "always()"


def test_should_run_caller_tests_through_required_ci_gate():
    ci = yaml.safe_load((ROOT / ".github/workflows/ci.yml").read_text())
    assert "scripts-tests" in ci["jobs"]["check-results"]["needs"]
    runner = ci["jobs"]["scripts-tests"]
    assert "needs.detect-changes.outputs.scripts-changes == 'true'" in runner["if"]
    assert any("pytest " in step.get("run", "") and ".github/scripts" in step["run"]
               for step in runner["steps"])
    filters = yaml.safe_load((ROOT / ".github/actions/paths-filter/action.yml").read_text())
    output = filters["outputs"]["scripts-changes"]["value"]
    assert "steps.ci-github-actions.outputs.ci-relevant == 'true'" in output
    relevance = subprocess.run(
        ["bash", ".github/scripts/ci-relevance/check.sh"],
        input=WORKFLOW_PATH + "\n", text=True, capture_output=True, cwd=ROOT, timeout=10,
    )
    assert relevance.returncode == 0, relevance.stderr
