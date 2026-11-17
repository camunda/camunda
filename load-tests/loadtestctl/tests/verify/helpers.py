import subprocess
from collections.abc import Callable

from loadtestctl.verify.context import Context

type Responder = Callable[[tuple[str, ...]], subprocess.CompletedProcess[str]]


def completed(stdout: str = "", returncode: int = 0) -> subprocess.CompletedProcess[str]:
    return subprocess.CompletedProcess(args=[], returncode=returncode, stdout=stdout, stderr="")


class FakeClock:
    """A clock that only advances when the code under test sleeps."""

    def __init__(self) -> None:
        self.now = 0.0

    def sleep(self, seconds: float) -> None:
        self.now += seconds

    def __call__(self) -> float:
        return self.now


def make_context(wait_retries: int = 2, wait_timeout: int = 1) -> Context:
    clock = FakeClock()
    return Context(
        namespace="c8-test", wait_timeout=wait_timeout, wait_retries=wait_retries, sleep=clock.sleep, clock=clock
    )
