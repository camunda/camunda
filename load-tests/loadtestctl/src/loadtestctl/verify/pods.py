"""Wait for pods of a load test to be ready."""

from .context import Context
from .context import VerifyError
from .context import kubectl
from .context import log


def wait_for_pods(ctx: Context, label: str, description: str) -> None:
    """Retry `kubectl wait`: pods may be rescheduled while waiting, which makes the watch fail with NotFound."""
    for attempt in range(1, ctx.wait_retries + 1):
        log(
            f"Waiting for {description} to be ready (attempt {attempt}/{ctx.wait_retries}, timeout: {ctx.wait_timeout}s)..."
        )
        result = kubectl(
            "wait",
            "--for=condition=ready",
            "pod",
            "-l",
            label,
            f"--timeout={ctx.wait_timeout}s",
            "-n",
            ctx.namespace,
            merge_stderr=True,
        )
        log(result.stdout.rstrip())
        if result.returncode == 0:
            log(f"{description} are ready in {ctx.namespace}")
            return

        log(f"Not all {description} are ready in {ctx.namespace}")
        pods = kubectl("get", "pod", "--no-headers", "-o", "wide", "-n", ctx.namespace)
        log(pods.stdout.rstrip())
        delete_out_of_cpu_pods(ctx, pods.stdout)
        if attempt < ctx.wait_retries:
            log(f"Pods weren't ready yet. Retrying in {ctx.retry_delay}s...")
            ctx.sleep(ctx.retry_delay)

    raise VerifyError(f"Not all {description} are ready in {ctx.namespace} after {ctx.wait_retries} attempts")


def delete_out_of_cpu_pods(ctx: Context, pods_output: str) -> None:
    """Delete pods the kubelet refused with `OutOfcpu`, matched on the STATUS column of `kubectl get pod -o wide --no-headers`.

    Many pods scheduled on the same node can fail to start with this status. They stay around and `kubectl wait`
    never succeeds while they exist.
    """
    names = [
        fields[0]
        for line in pods_output.splitlines()
        if len(fields := line.split()) > 2 and fields[2].lower() == "outofcpu"
    ]
    if not names:
        return
    log(f"Deleting pods with 'OutOfcpu' status: {' '.join(names)}")
    kubectl("delete", "pod", *names, "-n", ctx.namespace)
