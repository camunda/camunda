"""Check that the load test client is connected to the gateway."""

import re
import socket
import subprocess
from http.client import HTTPException
from urllib.request import urlopen

from .context import Context
from .context import VerifyError
from .context import log

APP_CONNECTED_PATTERN = re.compile(r"^app_connected\s+(\S+)", re.MULTILINE)
PORT_FORWARD_SETUP_SECONDS = 2


def free_local_port() -> int:
    with socket.socket() as sock:
        sock.bind(("127.0.0.1", 0))
        port: int = sock.getsockname()[1]
        return port


def read_app_connected(ctx: Context, metrics_port: int) -> float | None:
    """Read `app_connected` from the clients service through a short-lived port-forward."""
    local_port = free_local_port()
    log(f"Opening port-forward to svc/clients via port {local_port}...")
    port_forward = subprocess.Popen(
        ["kubectl", "port-forward", "svc/clients", f"{local_port}:{metrics_port}", "-n", ctx.namespace],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    try:
        ctx.sleep(PORT_FORWARD_SETUP_SECONDS)
        with urlopen(f"http://localhost:{local_port}/metrics", timeout=10) as response:
            body = response.read().decode("utf-8", errors="replace")
    except (OSError, HTTPException) as error:
        log(f"Could not read the clients metrics: {error}")
        return None
    finally:
        log("Stopping the port-forward...")
        port_forward.terminate()
        port_forward.wait()

    match = APP_CONNECTED_PATTERN.search(body)
    try:
        return float(match.group(1)) if match else None
    except ValueError:
        return None


def verify_connected(ctx: Context, metrics_port: int, timeout: int) -> None:
    """Wait until `app_connected >= 1`, which confirms that the client received the topology, i.e. it authenticated
    and connected successfully, regardless of REST or gRPC."""
    deadline = ctx.clock() + timeout
    attempt = 0
    while ctx.clock() < deadline:
        attempt += 1
        log(f"Checking clients connectivity (attempt {attempt}, {max(0, deadline - ctx.clock()):.0f}s left)")
        app_connected = read_app_connected(ctx, metrics_port)
        if app_connected is not None and app_connected >= 1:
            log(f"Namespace {ctx.namespace}: client connected to gateway (app.connected={app_connected:g})")
            return
        log(f"Namespace {ctx.namespace}: waiting for gateway connectivity")
        ctx.sleep(min(ctx.retry_delay, max(0, deadline - ctx.clock())))

    raise VerifyError(
        f"Namespace {ctx.namespace} client did not connect to gateway within {timeout}s "
        "(app.connected metric never reached 1)"
    )
