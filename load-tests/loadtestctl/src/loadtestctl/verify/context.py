"""Shared state and helpers of the verification checks."""

import os
import subprocess
import sys
import time
from collections.abc import Callable
from collections.abc import Iterator
from contextlib import contextmanager
from dataclasses import dataclass
from dataclasses import field

RETRY_DELAY_SECONDS = 2


class VerifyError(Exception):
    """A verification check failed."""


def log(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


def github_actions() -> bool:
    return os.environ.get("GITHUB_ACTIONS") == "true"


def log_error(message: str) -> None:
    """Log an error, as an annotation when running in GitHub Actions."""
    if github_actions():
        escaped = message.replace("%", "%25").replace("\r", "%0D").replace("\n", "%0A")
        log(f"::error::{escaped}")
    else:
        log(f"Error: {message}")


@contextmanager
def group(title: str) -> Iterator[None]:
    """Log what is written inside as a collapsed group when running in GitHub Actions."""
    if not github_actions():
        log(title)
        yield
        return
    log(f"::group::{title}")
    try:
        yield
    finally:
        log("::endgroup::")


def kubectl(*args: str, merge_stderr: bool = False) -> subprocess.CompletedProcess[str]:
    return subprocess.run(
        ["kubectl", *args],
        stdout=subprocess.PIPE,
        text=True,
        check=False,
        stderr=subprocess.STDOUT if merge_stderr else subprocess.PIPE,
    )


@dataclass(frozen=True)
class Context:
    namespace: str
    wait_timeout: int
    wait_retries: int
    retry_delay: int = RETRY_DELAY_SECONDS
    sleep: Callable[[float], None] = field(default=time.sleep, compare=False)
    clock: Callable[[], float] = field(default=time.monotonic, compare=False)
