import subprocess
from collections.abc import Sequence

import pytest
from click.testing import CliRunner

from loadtestctl.cli import cli


def run_command(
    monkeypatch: pytest.MonkeyPatch, args: Sequence[str], returncode: int = 0
) -> tuple[list[str], int, str]:
    commands: list[list[str]] = []

    def fake_run(command: list[str], check: bool) -> subprocess.CompletedProcess[str]:
        commands.append(command)
        return subprocess.CompletedProcess(command, returncode)

    monkeypatch.setattr(subprocess, "run", fake_run)
    result = CliRunner().invoke(cli, ["prometheus", *args])
    return commands[0], result.exit_code, result.output


def test_should_port_forward_prometheus_with_defaults(monkeypatch: pytest.MonkeyPatch) -> None:
    command, exit_code, _ = run_command(monkeypatch, [])

    assert exit_code == 0
    assert command == [
        "kubectl",
        "port-forward",
        "-n",
        "monitoring",
        "svc/kube-prometheus-stack-prometheus",
        "9090:9090",
    ]


def test_should_use_custom_namespace_service_and_local_port(monkeypatch: pytest.MonkeyPatch) -> None:
    command, _, _ = run_command(monkeypatch, ["-n", "obs", "-s", "prom", "-p", "19090"])

    assert command == ["kubectl", "port-forward", "-n", "obs", "svc/prom", "19090:9090"]


def test_should_propagate_kubectl_exit_code(monkeypatch: pytest.MonkeyPatch) -> None:
    _, exit_code, _ = run_command(monkeypatch, [], returncode=1)

    assert exit_code == 1


def test_should_report_missing_kubectl(monkeypatch: pytest.MonkeyPatch) -> None:
    def fake_run(command: list[str], check: bool) -> subprocess.CompletedProcess[str]:
        raise FileNotFoundError

    monkeypatch.setattr(subprocess, "run", fake_run)

    result = CliRunner().invoke(cli, ["prometheus"])

    assert result.exit_code == 1
    assert "kubectl was not found" in result.output


def test_should_exit_cleanly_on_interrupt(monkeypatch: pytest.MonkeyPatch) -> None:
    def fake_run(command: list[str], check: bool) -> subprocess.CompletedProcess[str]:
        raise KeyboardInterrupt

    monkeypatch.setattr(subprocess, "run", fake_run)

    assert CliRunner().invoke(cli, ["prometheus"]).exit_code == 0
