from functools import partial
from pathlib import Path

import click

from loadtestctl.report.cli import NamespaceType
from loadtestctl.report.errors import ReportError
from loadtestctl.report.prometheus import PrometheusClient

from .connectivity import verify_connected
from .context import Context
from .context import VerifyError
from .context import kubectl
from .context import log
from .k6 import verify_metrics
from .k6 import wait_for_testruns
from .pods import wait_for_pods

option = partial(click.option, show_envvar=True)

VERIFY_HELP = (
    "Wait for the pods and k6 TestRuns of a load test to be healthy, for the client to be "
    "connected to the gateway, and for k6 to report metrics to Prometheus."
)

VERIFY_EPILOG = """\b
Output:
  Progress is logged to stderr. The result is `status=success` or `status=failure`, and the exit code is
  0 on success and 1 on failure. The result is printed to stdout, or appended to --result-file if given.

\b
Examples:
  loadtestctl verify c8-ck-baseline-20260814
  loadtestctl verify c8-ck-baseline-20260814 --result-file "$GITHUB_OUTPUT"

\b
Prometheus:
  Queries http://localhost:9090 without authentication by default, for example through
  `kubectl port-forward -n monitoring svc/kube-prometheus-stack-prometheus 9090:9090`.
"""


def report_result(status: str, result_file: Path | None) -> None:
    line = f"status={status}"
    if result_file is None:
        click.echo(line)
        return
    try:
        with result_file.open("a", encoding="utf-8") as file:
            file.write(f"{line}\n")
    except OSError as error:
        log(f"Could not write result file '{result_file}': {error}")
        click.echo(line)


def run_verify(
    ctx: Context,
    metrics_port: int,
    connectivity_timeout: int,
    k6_metrics_timeout: int,
    prometheus_url: str,
    prometheus_user: str,
    prometheus_password: str,
) -> None:
    log(f"--- Checking namespace: {ctx.namespace} ---")
    if kubectl("get", "ns", ctx.namespace).returncode != 0:
        raise VerifyError(f"Namespace {ctx.namespace} does not exist")

    wait_for_pods(ctx, "app=camunda-platform", "Camunda platform pods")
    wait_for_pods(ctx, "app.kubernetes.io/component=zeebe-client", "load test client pods")
    wait_for_testruns(ctx)
    verify_connected(ctx, metrics_port, connectivity_timeout)
    verify_metrics(
        ctx,
        lambda: PrometheusClient(prometheus_url, prometheus_user, prometheus_password, ""),
        k6_metrics_timeout,
    )


@click.command("verify", help=VERIFY_HELP, epilog=VERIFY_EPILOG)
@click.argument("namespace", type=NamespaceType())
@option(
    "--wait-timeout",
    default=30,
    type=click.IntRange(min=1),
    show_default=True,
    help="Timeout of each wait attempt in seconds.",
)
@option(
    "--wait-retries",
    default=30,
    type=click.IntRange(min=1),
    show_default=True,
    help="Number of attempts for each wait (pods and k6 TestRuns).",
)
@option(
    "--connectivity-timeout",
    default=900,
    type=click.IntRange(min=1),
    show_default=True,
    help="Timeout for the client to connect to the gateway in seconds.",
)
@option(
    "--metrics-port",
    default=9600,
    type=click.IntRange(min=1),
    show_default=True,
    help="Port on which the client exposes metrics.",
)
@option(
    "--k6-metrics-timeout",
    default=300,
    type=click.IntRange(min=1),
    envvar="K6_METRICS_TIMEOUT",
    show_default=True,
    show_envvar=True,
    help="Timeout for k6 to report metrics to Prometheus in seconds.",
)
@option(
    "--result-file",
    type=click.Path(dir_okay=False, path_type=Path),
    help="File to append the result (`status=success` or `status=failure`) to instead of printing it.",
)
@option(
    "--prometheus-url",
    default="http://localhost:9090",
    envvar="PROMETHEUS_URL",
    show_default=True,
    show_envvar=True,
    help="Base URL of the Prometheus queried for k6 metrics.",
)
@option(
    "--prometheus-user",
    default="",
    envvar="PROMETHEUS_USER",
    show_envvar=True,
    help="Basic auth user for Prometheus. No authentication if empty.",
)
@option(
    "--prometheus-password",
    default="",
    envvar="PROMETHEUS_PASSWORD",
    show_envvar=True,
    help="Basic auth password for Prometheus.",
)
def verify(
    namespace: str,
    wait_timeout: int,
    wait_retries: int,
    connectivity_timeout: int,
    metrics_port: int,
    k6_metrics_timeout: int,
    prometheus_url: str,
    prometheus_user: str,
    prometheus_password: str,
    result_file: Path | None,
) -> None:
    ctx = Context(namespace=namespace, wait_timeout=wait_timeout, wait_retries=wait_retries)
    status = "success"
    try:
        run_verify(
            ctx,
            metrics_port,
            connectivity_timeout,
            k6_metrics_timeout,
            prometheus_url,
            prometheus_user,
            prometheus_password,
        )
    except (VerifyError, ReportError) as error:
        log(f"Error: {error}")
        status = "failure"
    except Exception as error:  # noqa: BLE001
        log(f"Unexpected error: {error!r}")
        status = "failure"

    report_result(status, result_file)
    if status != "success":
        raise click.exceptions.Exit(1)
