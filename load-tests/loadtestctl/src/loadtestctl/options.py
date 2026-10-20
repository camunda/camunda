from collections.abc import Callable
from functools import partial
from typing import Any

import click

# Options are also read from LOADTESTCTL_<COMMAND>_<OPTION>.
option = partial(click.option, show_envvar=True)


def prometheus_options(url_help: str) -> Callable[[Callable[..., Any]], Callable[..., Any]]:
    """The options that select and authenticate against Prometheus, shared by the commands that query it.

    Every command reads them from the same environment variables, so a single set serves all of them.
    """

    def decorator(command: Callable[..., Any]) -> Callable[..., Any]:
        decorators = [
            option(
                "-e",
                "--prometheus-url",
                envvar="LOADTESTCTL_PROMETHEUS_URL",
                default="http://localhost:9090",
                show_default=True,
                help=url_help,
            ),
            option(
                "-u",
                "--prometheus-user",
                envvar="LOADTESTCTL_PROMETHEUS_USER",
                default="",
                help="Basic auth user for Prometheus. No authentication if empty.",
            ),
            option(
                "-p",
                "--prometheus-password",
                envvar="LOADTESTCTL_PROMETHEUS_PASSWORD",
                default="",
                help="Basic auth password for Prometheus.",
            ),
        ]
        for apply in reversed(decorators):
            command = apply(command)
        return command

    return decorator
