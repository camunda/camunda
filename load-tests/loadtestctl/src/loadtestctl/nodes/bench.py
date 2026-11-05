"""Run a short CPU benchmark pod on each broker node."""

import json

from loadtestctl.verify.context import Context
from loadtestctl.verify.context import VerifyError
from loadtestctl.verify.context import kubectl
from loadtestctl.verify.context import log

from .score import NodeResult
from .score import parse_events_per_second

LABEL = "app=nodebench"
IMAGE = "alpine:3.20"
BROKER_LABEL = "app=camunda-platform"
SCRIPT = (
    "apk add --no-cache sysbench >/dev/null 2>&1 && sysbench cpu --threads=1 --time=10 run | grep 'events per second'"
)
POLL_SECONDS = 5


def broker_nodes(ctx: Context) -> list[str]:
    result = kubectl(
        "get",
        "pod",
        "-l",
        BROKER_LABEL,
        "-n",
        ctx.namespace,
        "-o",
        "jsonpath={range .items[*]}{.spec.nodeName}{'\\n'}{end}",
    )
    if result.returncode != 0:
        raise VerifyError(f"Could not list the broker pods in {ctx.namespace}: {result.stderr.strip()}")
    nodes = sorted({line for line in result.stdout.splitlines() if line})
    if not nodes:
        raise VerifyError(f"No scheduled broker pods ({BROKER_LABEL}) found in {ctx.namespace}")
    return nodes


def pod_name(index: int) -> str:
    return f"nodebench-{index}"


def start_pod(ctx: Context, index: int, node: str) -> None:
    """Pin the pod with `nodeName`: it bypasses the scheduler and the node taints, and needs no CPU request."""
    overrides = {
        "spec": {
            "nodeName": node,
            "containers": [{"name": "bench", "image": IMAGE, "command": ["sh", "-c", SCRIPT]}],
        }
    }
    result = kubectl(
        "run",
        pod_name(index),
        f"--labels={LABEL}",
        "--restart=Never",
        f"--image={IMAGE}",
        f"--overrides={json.dumps(overrides)}",
        "-n",
        ctx.namespace,
    )
    if result.returncode != 0:
        raise VerifyError(f"Could not start the benchmark pod on {node}: {result.stderr.strip()}")


def wait_for_pod(ctx: Context, index: int, timeout: int) -> None:
    deadline = ctx.clock() + timeout
    while ctx.clock() < deadline:
        result = kubectl("get", "pod", pod_name(index), "-n", ctx.namespace, "-o", "jsonpath={.status.phase}")
        if result.stdout.strip() in ("Succeeded", "Failed"):
            return
        ctx.sleep(POLL_SECONDS)
    log(f"Benchmark pod {pod_name(index)} did not finish within {timeout}s")


def run_benchmark(ctx: Context, nodes: list[str], timeout: int) -> list[NodeResult]:
    """Start one pod per node, so the nodes are measured at the same time, then read the logs and clean up."""
    try:
        for index, node in enumerate(nodes):
            start_pod(ctx, index, node)
        results = []
        for index, node in enumerate(nodes):
            wait_for_pod(ctx, index, timeout)
            logs = kubectl("logs", pod_name(index), "-n", ctx.namespace)
            results.append(NodeResult(node, parse_events_per_second(logs.stdout)))
        return results
    finally:
        kubectl("delete", "pod", "-l", LABEL, "-n", ctx.namespace, "--wait=false")
