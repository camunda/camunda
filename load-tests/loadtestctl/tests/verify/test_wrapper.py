import os
import subprocess
from pathlib import Path

import pytest

WRAPPER = Path(__file__).resolve().parents[3] / "docs" / "scripts" / "verify-test.sh"

FAKE_UV = """#!/usr/bin/env bash
printf '%s\\n' "$@" > "$FAKE_UV_ARGS"
echo "status=${FAKE_UV_STATUS}"
exit "${FAKE_UV_EXIT}"
"""


def run_wrapper(tmp_path: Path, args: list[str], status: str, exit_code: int) -> tuple[int, str, list[str]]:
    bin_dir = tmp_path / "bin"
    bin_dir.mkdir()
    uv = bin_dir / "uv"
    uv.write_text(FAKE_UV)
    uv.chmod(0o755)
    github_output = tmp_path / "github-output"
    env = {
        **os.environ,
        "PATH": f"{bin_dir}:{os.environ['PATH']}",
        "GITHUB_OUTPUT": str(github_output),
        "FAKE_UV_ARGS": str(tmp_path / "uv-args"),
        "FAKE_UV_STATUS": status,
        "FAKE_UV_EXIT": str(exit_code),
    }

    result = subprocess.run([str(WRAPPER), *args], env=env, capture_output=True, text=True, check=False)

    uv_args = (tmp_path / "uv-args").read_text().split() if (tmp_path / "uv-args").exists() else []
    output = github_output.read_text() if github_output.exists() else ""
    return result.returncode, output, uv_args


def test_should_map_positional_arguments_to_flags_and_write_the_status(tmp_path: Path) -> None:
    # when
    code, output, uv_args = run_wrapper(tmp_path, ["c8-test", "10", "5", "60", "9700"], "success", 0)

    # then
    assert code == 0
    assert output == "status=success\n"
    assert uv_args[-10:] == [
        "verify",
        "c8-test",
        "--wait-timeout",
        "10",
        "--wait-retries",
        "5",
        "--connectivity-timeout",
        "60",
        "--metrics-port",
        "9700",
    ]


def test_should_propagate_the_failure_of_the_verification(tmp_path: Path) -> None:
    code, output, _ = run_wrapper(tmp_path, ["c8-test"], "failure", 1)

    assert code == 1
    assert output == "status=failure\n"


def test_should_fail_without_namespace(tmp_path: Path) -> None:
    code, output, uv_args = run_wrapper(tmp_path, [], "success", 0)

    assert code == 1
    assert output == ""
    assert uv_args == []


@pytest.mark.parametrize("flag", ["-h", "--help"])
def test_should_print_usage_for_help(tmp_path: Path, flag: str) -> None:
    code, _, uv_args = run_wrapper(tmp_path, [flag], "success", 0)

    assert code == 0
    assert uv_args == []
