import subprocess
import sys
from functools import partial

import click

PROMETHEUS_PORT = 9090

option = partial(click.option, show_envvar=True)


def port_forward_command(namespace: str, service: str, local_port: int) -> list[str]:
    return ["kubectl", "port-forward", "-n", namespace, f"svc/{service}", f"{local_port}:{PROMETHEUS_PORT}"]


@click.command(
    "prometheus",
    help="Forward a local port to the load-test Prometheus until interrupted.",
    epilog="\b\nExamples:\n  loadtestctl prometheus\n  loadtestctl prometheus --local-port 19090",
)
@option("-n", "--namespace", default="monitoring", show_default=True, help="Namespace of the Prometheus service.")
@option(
    "-s",
    "--service",
    default="kube-prometheus-stack-prometheus",
    show_default=True,
    help="Name of the Prometheus service.",
)
@option(
    "-p",
    "--local-port",
    type=click.IntRange(min=1, max=65535),
    default=PROMETHEUS_PORT,
    show_default=True,
    help="Local port to listen on.",
)
def prometheus(namespace: str, service: str, local_port: int) -> None:
    command = port_forward_command(namespace, service, local_port)
    try:
        completed = subprocess.run(command, check=False)
    except FileNotFoundError as error:
        print("Error: kubectl was not found on the PATH.", file=sys.stderr)
        raise click.exceptions.Exit(1) from error
    except KeyboardInterrupt:
        return
    if completed.returncode != 0:
        raise click.exceptions.Exit(completed.returncode)
