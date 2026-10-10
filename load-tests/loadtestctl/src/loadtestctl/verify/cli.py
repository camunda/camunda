from functools import partial

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

DEFAULT_PROMETHEUS_URL = "https://ci-monitor.benchmark.camunda.cloud"

VERIFY_HELP = (
    "Wait for the pods and k6 TestRuns of a load test to be healthy, for the client to be "
    "connected to the gateway, and for k6 to report metrics to Prometheus."
)

VERIFY_EPILOG = """\b
Output:
  Progress is logged to stderr. Stdout only gets `status=success` or `status=failure`, and the exit code is
  0 on success and 1 on failure.

\b
Examples:
  loadtestctl verify c8-ck-baseline-20260814
  loadtestctl verify c8-ck-baseline-20260814 --connectivity-timeout 1800 >> "$GITHUB_OUTPUT"
"""


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
    "--prometheus-url",
    default=DEFAULT_PROMETHEUS_URL,
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
    help="Basic auth user for Prometheus.",
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
) -> None:
    ctx = Context(namespace=namespace, wait_timeout=wait_timeout, wait_retries=wait_retries)
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
        click.echo("status=failure")
        raise click.exceptions.Exit(1) from error
    except Exception as error:
        log(f"Unexpected error: {error!r}")
        click.echo("status=failure")
        raise click.exceptions.Exit(1) from error
    click.echo("status=success")
