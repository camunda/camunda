from pathlib import Path
from unittest import mock

import pytest
from click.testing import CliRunner

from loadtestctl.cli import cli
from loadtestctl.nodes.score import NodeResult
from loadtestctl.nodes.score import format_table
from loadtestctl.nodes.score import parse_events_per_second
from loadtestctl.nodes.score import slow_nodes
from loadtestctl.verify.context import VerifyError

BENCH = "loadtestctl.nodes.cli"


def test_should_parse_events_per_second() -> None:
    # given
    output = "    events per second:  2603.41\n"

    # when / then
    assert parse_events_per_second(output) == pytest.approx(2603.41)


def test_should_return_none_without_events() -> None:
    assert parse_events_per_second("sh: sysbench: not found") is None


def test_should_flag_only_nodes_below_the_threshold() -> None:
    # given
    results = [NodeResult("b", 980.0), NodeResult("a", 2600.0), NodeResult("c", None)]

    # when / then
    assert slow_nodes(results, 1500.0) == ["b"]


def test_should_format_a_table_with_unknown_nodes() -> None:
    # given
    results = [NodeResult("b", 980.0), NodeResult("a", 2600.0), NodeResult("c", None)]

    # when
    table = format_table(results, 1500.0)

    # then
    assert table.splitlines() == ["node | events/s | class", "a | 2600 | ok", "b | 980 | slow", "c | n/a | unknown"]


def invoke(results: list[NodeResult] | Exception, *args: str):  # type: ignore[no-untyped-def]
    patch_run = (
        mock.patch(f"{BENCH}.run_benchmark", side_effect=results)
        if isinstance(results, Exception)
        else mock.patch(f"{BENCH}.run_benchmark", return_value=results)
    )
    with mock.patch(f"{BENCH}.broker_nodes", return_value=["a", "b"]), patch_run:
        return CliRunner().invoke(cli, ["nodes", "c8-test", *args])


def test_should_report_healthy() -> None:
    # when
    result = invoke([NodeResult("a", 2600.0), NodeResult("b", 2590.0)])

    # then
    assert result.exit_code == 0
    assert result.stdout.splitlines() == ["status=healthy"]


def test_should_report_degraded_with_slow_nodes_without_failing(tmp_path: Path) -> None:
    # given
    result_file = tmp_path / "out"

    # when
    result = invoke([NodeResult("a", 2600.0), NodeResult("b", 980.0)], "--result-file", str(result_file))

    # then
    assert result.exit_code == 0
    assert result_file.read_text().splitlines() == ["status=degraded", "slow_nodes=b"]


def test_should_fail_when_the_benchmark_cannot_run() -> None:
    # when
    result = invoke(VerifyError("no brokers"))

    # then
    assert result.exit_code == 1
    assert result.stdout.splitlines() == ["status=failure"]
