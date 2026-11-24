"""Checks of the k6 TestRuns and StatefulSets deployed by the load test chart."""

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


def list_k6_statefulsets(ctx: Context) -> list[str]:
    """Return the names of the k6 tests that run as a StatefulSet. A failing kubectl must not be reported as "none"."""
    result = kubectl("get", "statefulsets", "-n", ctx.namespace, "-l", "app.kubernetes.io/component=k6", "-o", "json")
    if result.returncode != 0:
        raise VerifyError(f"Unable to list k6 StatefulSets in {ctx.namespace}: {result.stderr.strip()}")
    return [item["metadata"]["name"] for item in json.loads(result.stdout)["items"]]


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


def wait_for_k6_statefulsets(ctx: Context) -> None:
    """Wait until the pods of every k6 StatefulSet are ready, if there are any.

    The k6 container has no readiness probe, so a ready pod only shows that k6 started: verify_metrics checks
    that the test sends requests.
    """
    statefulsets = list_k6_statefulsets(ctx)
    if not statefulsets:
        log(f"No k6 StatefulSet deployed in {ctx.namespace}, nothing to verify.")
        return

    for name in statefulsets:
        wait_for_pods(ctx, f"app.kubernetes.io/name={name}", f"k6 pods of StatefulSet {name}")


def verify_metrics(ctx: Context, new_client: Callable[[], PrometheusClient], timeout: int) -> None:
    """Wait until each started k6 TestRun and each k6 StatefulSet reports k6_http_reqs_total >= 1.

    The k6 tests report metrics with the TestRun or StatefulSet name as `testrun_name`. A stopped or finished
    TestRun sends no new samples, so it is not checked. The client is only created when a test is checked, so
    load tests without k6 never need Prometheus credentials.
    """
    testruns = list_testruns(ctx)
    expected = {name for name, stage in testruns.items() if stage == "started"} | set(list_k6_statefulsets(ctx))
    if not expected:
        return

    client = new_client()

    query = f'sum by (testrun_name) (k6_http_reqs_total{{namespace="{ctx.namespace}", expected_response="true"}}) >= 1'
    deadline = ctx.clock() + timeout
    attempt = 0
    missing = sorted(expected)
    while ctx.clock() < deadline:
        attempt += 1
        log(f"Checking k6 metrics (attempt {attempt}, {max(0, deadline - ctx.clock()):.0f}s left)")
        missing = sorted(expected - reporting_tests(client, query))
        if not missing:
            log(f"Namespace {ctx.namespace}: k6 reports metrics for {', '.join(sorted(expected))}")
            return
        log(f"No k6 metrics yet for {', '.join(missing)}")
        ctx.sleep(min(ctx.retry_delay, max(0, deadline - ctx.clock())))

    raise VerifyError(
        f"Namespace {ctx.namespace}: the k6 tests {', '.join(missing)} did not report metrics to Prometheus within "
        f"{timeout}s (k6_http_reqs_total never reached 1)"
    )


def reporting_tests(client: PrometheusClient, query: str) -> set[str]:
    """Return the `testrun_name` of each series of the query, none if Prometheus cannot be queried (yet)."""
    try:
        result = client.query(query).data.result
    except (PrometheusError, ReportError, URLError, OSError, ValueError) as error:
        log(f"Prometheus query failed: {error}")
        return set()
    return {str(series.metric["testrun_name"]) for series in result if "testrun_name" in series.metric}
