import pytest
from click.testing import CliRunner

from loadtestctl.cli import cli


@pytest.mark.parametrize("command", ["report", "verify"])
def test_should_share_the_prometheus_environment_variables(command: str) -> None:
    result = CliRunner().invoke(cli, [command, "--help"], terminal_width=200)

    assert "LOADTESTCTL_PROMETHEUS_URL" in result.output
    assert "LOADTESTCTL_PROMETHEUS_USER" in result.output
    assert "LOADTESTCTL_PROMETHEUS_PASSWORD" in result.output
    assert f"LOADTESTCTL_{command.upper()}_PROMETHEUS" not in result.output
