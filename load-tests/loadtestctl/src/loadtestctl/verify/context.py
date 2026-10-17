"""Shared state and helpers of the verification checks."""

import subprocess
import sys
import time
from collections.abc import Callable
from dataclasses import dataclass
from dataclasses import field

RETRY_DELAY_SECONDS = 2


class VerifyError(Exception):
    """A verification check failed."""


def log(message: str) -> None:
    print(message, file=sys.stderr, flush=True)


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
