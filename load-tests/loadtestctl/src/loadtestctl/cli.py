import click

from .report.cli import report


@click.group(
    context_settings={"auto_envvar_prefix": "LOADTESTCTL"},
    help="A helper CLI for controlling and operating on load tests",
    epilog="\b\nExamples:\n  loadtestctl report c8-ck-baseline-20260814 --duration-seconds 1800",
)
def cli() -> None:
    pass


cli.add_command(report)


def main() -> None:
    cli()
