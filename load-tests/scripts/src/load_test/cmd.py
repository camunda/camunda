import asyncio
import logging
import shlex
from asyncio.subprocess import PIPE
from dataclasses import dataclass

from rich.console import Console

console = Console()

logger = logging.getLogger("load-test-tool")


@dataclass
class CommandResult:
    returncode: int
    stdout: str
    stderr: str


async def run(*cmd: str) -> CommandResult:
    logger.debug("Running command: %s", shlex.join(cmd))
    process = await asyncio.create_subprocess_exec(*cmd, stdout=PIPE, stderr=PIPE)
    stdout, stderr = await process.communicate()
    returncode = await process.wait()

    return CommandResult(returncode, stdout.decode(), stderr.decode())
