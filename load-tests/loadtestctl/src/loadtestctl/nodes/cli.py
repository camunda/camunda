from pathlib import Path

import click

from loadtestctl.options import option
from loadtestctl.report.cli import NamespaceType
from loadtestctl.verify.context import Context
from loadtestctl.verify.context import VerifyError
from loadtestctl.verify.context import log

from .bench import broker_nodes
from .bench import run_benchmark
from .score import DEFAULT_MIN_EVENTS_PER_SECOND
from .score import format_table
from .score import slow_nodes

NODES_HELP = (
    "Benchmark the CPU of the nodes that host the brokers of a load test and report slow ones. Some hosts of the "
    "benchmark cluster are about 2.7x slower per core than others, which can decide whether a saturated cluster "
    "gets stuck at around 50% rejected requests."
)

NODES_EPILOG = """\b
Output:
  The table is logged to stderr. The result is `status=healthy` or `status=degraded` plus `slow_nodes=<list>`,
  printed to stdout or appended to --result-file. Slow nodes do not fail the command: the exit code is 1 only
  if the benchmark could not run (`status=failure`).

\b
Run it before the load starts: the benchmark pods use one core of each broker node.

\b
Examples:
  loadtestctl nodes c8-ck-baseline-20260814
  loadtestctl nodes c8-ck-baseline-20260814 --result-file "$GITHUB_OUTPUT"
"""


def write_result(lines: list[str], result_file: Path | None) -> None:
    text = "\n".join(lines)
    if result_file is None:
        click.echo(text)
        return
    try:
        with result_file.open("a", encoding="utf-8") as file:
            file.write(f"{text}\n")
    except OSError as error:
        log(f"Could not write result file '{result_file}': {error}")
        click.echo(text)


@click.command("nodes", help=NODES_HELP, epilog=NODES_EPILOG)
@click.argument("namespace", type=NamespaceType())
@option(
    "--min-events-per-second",
    default=DEFAULT_MIN_EVENTS_PER_SECOND,
    type=click.FloatRange(min=0),
    show_default=True,
    help="`sysbench cpu` events per second (1 thread) below which a node counts as slow.",
)
@option(
    "--timeout",
    default=180,
    type=click.IntRange(min=1),
    show_default=True,
    help="Time to wait for each benchmark pod in seconds.",
)
@option(
    "--result-file",
    type=click.Path(dir_okay=False, path_type=Path),
    help="File to append the result to instead of printing it.",
)
def nodes(namespace: str, min_events_per_second: float, timeout: int, result_file: Path | None) -> None:
    ctx = Context(namespace=namespace, wait_timeout=timeout, wait_retries=1)
    lines = ["status=failure"]
    try:
        results = run_benchmark(ctx, broker_nodes(ctx), timeout)
        log(format_table(results, min_events_per_second))
        slow = slow_nodes(results, min_events_per_second)
        lines = ["status=degraded", f"slow_nodes={','.join(slow)}"] if slow else ["status=healthy"]
    except VerifyError as error:
        log(f"Error: {error}")
    except Exception as error:  # noqa: BLE001
        log(f"Unexpected error: {error!r}")

    write_result(lines, result_file)
    if lines[0] == "status=failure":
        raise click.exceptions.Exit(1)
