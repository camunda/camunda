import typer

from .report import cli as report_cli

app = typer.Typer(
    name="loadtestctl",
    help="A helper CLI for controlling and operating on load tests",
    epilog="\b\nExamples:\n  loadtestctl report c8-ck-baseline-20260814 --duration-seconds 1800",
    rich_markup_mode=None,
)


@app.callback()
def root() -> None:
    pass


report_cli.register(app)


def main() -> None:
    app()
