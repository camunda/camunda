"""Checks of the k6 TestRuns deployed by the load test chart."""

import json
from collections.abc import Callable
from urllib.error import URLError

from loadtestctl.report.errors import PrometheusError
from loadtestctl.report.errors import ReportError
from loadtestctl.report.prometheus import PrometheusClient

from .context import Context
from .context import VerifyError
from .context import kubectl
from .context import log
from .pods import wait_for_pods

HEALTHY_STAGES = ("started", "stopped", "finished")


def list_testruns(ctx: Context) -> dict[str, str]:
    """Return the stage of each TestRun by name. A failing kubectl must not be reported as "no TestRun"."""
    result = kubectl("get", "testruns.k6.io", "-n", ctx.namespace, "-o", "json")
    if result.returncode != 0:
        raise VerifyError(f"Unable to list k6 TestRuns in {ctx.namespace}: {result.stderr.strip()}")
    items = json.loads(result.stdout)["items"]
    return {item["metadata"]["name"]: item.get("status", {}).get("stage", "") for item in items}


def failed_k6_pods(ctx: Context) -> list[str]:
    result = kubectl(
        "get", "pod", "-n", ctx.namespace, "-l", "app=k6", "--field-selector=status.phase=Failed", "-o", "name"
    )
    if result.returncode != 0:
        raise VerifyError(f"Unable to list k6 pods in {ctx.namespace}: {result.stderr.strip()}")
    return result.stdout.split()


def wait_for_testruns(ctx: Context) -> None:
    """Wait until every k6 TestRun is healthy, if there are any.

    TestRuns are healthy in the stages started, stopped and finished, and no k6 pod stopped unexpectedly (the
    initializer pods run briefly and exit successfully, they are not Failed).
    """
    if not list_testruns(ctx):
        log(f"No k6 TestRun deployed in {ctx.namespace}, nothing to verify.")
        return

    max_attempts = ctx.wait_retries * (ctx.wait_timeout + ctx.retry_delay) // ctx.retry_delay
    for attempt in range(1, max_attempts + 1):
        log(f"Waiting for k6 TestRuns to be healthy (attempt {attempt}/{max_attempts})...")
        testruns = list_testruns(ctx)
        log("\n".join(f"{name} {stage}" for name, stage in testruns.items()))

        if "error" in testruns.values():
            raise VerifyError(f"A k6 TestRun in {ctx.namespace} is unhealthy")

        # The k6 pods may fail and never come back ready, so wait_for_pods alone would wait until its timeout.
        # The TestRun status does not reveal a crashed runner either: k6-operator moves the TestRun to
        # stopped/finished even when a runner failed.
        if failed := failed_k6_pods(ctx):
            raise VerifyError(f"Some k6 pods stopped unexpectedly in {ctx.namespace}: {' '.join(failed)}")

        if all(stage in HEALTHY_STAGES for stage in testruns.values()):
            log(f"k6 TestRuns are healthy in {ctx.namespace}")
            break
        if attempt == max_attempts:
            raise VerifyError(f"Not all k6 TestRuns are healthy in {ctx.namespace} after {max_attempts} attempts")
        log(f"k6 TestRuns not healthy yet. Retrying in {ctx.retry_delay}s...")
        ctx.sleep(ctx.retry_delay)

    # A TestRun stays "started" while its runner pods are still pending or restarting, so the stage alone does
    # not show that they run. Pods of stopped or finished TestRuns are Completed and never become Ready, so
    # only the started TestRuns are waited for.
    for name, stage in testruns.items():
        if stage == "started":
            wait_for_pods(ctx, f"app=k6,runner=true,k6_cr={name}", f"k6 runner pods of TestRun {name}")
        else:
            log(f"k6 TestRun {name} is {stage}, skipping its runner pods.")


def verify_metrics(ctx: Context, new_client: Callable[[], PrometheusClient], timeout: int) -> None:
    """Wait until k6 reports metrics to Prometheus (k6_http_reqs_total >= 1), if there are k6 TestRuns.

    The client is only created for k6 TestRuns, so load tests without k6 never need Prometheus credentials.
    """
    if not list_testruns(ctx):
        return

    client = new_client()

    query = f'sum(k6_http_reqs_total{{namespace="{ctx.namespace}", expected_response="true"}})'
    deadline = ctx.clock() + timeout
    attempt = 0
    while ctx.clock() < deadline:
        attempt += 1
        log(f"Checking k6 metrics (attempt {attempt}, {max(0, deadline - ctx.clock()):.0f}s left)")
        http_reqs = query_value(client, query)
        if http_reqs >= 1:
            log(f"Namespace {ctx.namespace}: k6 reports metrics (k6_http_reqs_total={http_reqs:g})")
            return
        ctx.sleep(min(ctx.retry_delay, max(0, deadline - ctx.clock())))

    raise VerifyError(
        f"Namespace {ctx.namespace} k6 did not report metrics to Prometheus within {timeout}s "
        "(k6_http_reqs_total never reached 1)"
    )


def query_value(client: PrometheusClient, query: str) -> float:
    """Return the value of a single-sample query, 0 if there is none or Prometheus cannot be queried (yet)."""
    try:
        result = client.query(query).data.result
    except (PrometheusError, ReportError, URLError, OSError, ValueError) as error:
        log(f"Prometheus query failed: {error}")
        return 0
    return result[0].value if result else 0
