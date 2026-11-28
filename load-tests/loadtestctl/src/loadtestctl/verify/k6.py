"""Checks of the k6 StatefulSets deployed by the load test chart."""

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


def list_k6_statefulsets(ctx: Context) -> list[str]:
    """Return the names of the k6 tests that run as a StatefulSet. A failing kubectl must not be reported as "none"."""
    result = kubectl("get", "statefulsets", "-n", ctx.namespace, "-l", "app.kubernetes.io/component=k6", "-o", "json")
    if result.returncode != 0:
        raise VerifyError(f"Unable to list k6 StatefulSets in {ctx.namespace}: {result.stderr.strip()}")
    return [item["metadata"]["name"] for item in json.loads(result.stdout)["items"]]


def wait_for_k6_statefulsets(ctx: Context) -> None:
    """Wait until the pods of every k6 StatefulSet are ready, if there are any.

    The readiness probe queries the k6 REST API, so a ready pod only shows that k6 started: verify_metrics checks
    that the test sends requests.
    """
    statefulsets = list_k6_statefulsets(ctx)
    if not statefulsets:
        log(f"No k6 StatefulSet deployed in {ctx.namespace}, nothing to verify.")
        return

    for name in statefulsets:
        wait_for_pods(ctx, f"app.kubernetes.io/name={name}", f"k6 pods of StatefulSet {name}")


def verify_metrics(ctx: Context, new_client: Callable[[], PrometheusClient], timeout: int) -> None:
    """Wait until each k6 StatefulSet reports k6_http_reqs_total >= 1.

    The k6 tests report metrics with the StatefulSet name as `testrun_name`. The client is only created when a
    test is checked, so load tests without k6 never need Prometheus credentials.
    """
    expected = set(list_k6_statefulsets(ctx))
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
