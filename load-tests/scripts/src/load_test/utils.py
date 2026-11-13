import asyncio
import logging

from rich.console import Console

console = Console()

logger = logging.getLogger("load-test-tool")


class TaskGroup:
    """Like `asyncio.TaskGroup` but with early exit.

    The upstream `asyncio.TaskGroup` waits for all tasks to finish before exiting.

    This `TaskGroup` waits until a task finishes and cancels all the other tasks.

    This allows to run multiple tasks in concurrently, and if one of them fail,
    all the other tasks can be retried somehow.
    """

    def __init__(self):
        self.tasks: list[asyncio.Task] = []

    async def __aenter__(self):
        return self

    async def __aexit__(self, exc_type, exc_val, exc_tb):
        await self.wait()

    def add(self, name, coro):
        task = asyncio.create_task(coro, name=name)
        self.tasks.append(task)
        return task

    async def wait(self):
        try:
            _, pending = await asyncio.wait(
                self.tasks, return_when=asyncio.FIRST_COMPLETED
            )
        except asyncio.CancelledError:
            pending = self.tasks

        for task in pending:
            logger.debug(f"cancelling task {task}")
            task.cancel()
            try:
                await task
            except asyncio.CancelledError:
                pass
