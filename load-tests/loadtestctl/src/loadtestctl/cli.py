import click

from .report.cli import report
from .verify.cli import verify


@click.group(
    help="A helper CLI for controlling and operating on load tests",
    epilog="\b\nExamples:\n  loadtestctl report c8-ck-baseline-20260814 --duration-seconds 1800\n  loadtestctl verify c8-ck-baseline-20260814",
)
def cli() -> None:
    pass


cli.add_command(report)
cli.add_command(verify)


def main() -> None:
    cli()
