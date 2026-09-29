
from camunda_orchestration_sdk import CamundaAsyncClient, ConnectedJobContext, WorkerConfig
import asyncio
from pydantic import BaseModel
from datetime import datetime
from load_test_report.cli import run

class JobVariables(BaseModel):
    startTime: str
    namespace: str

async def handle_job(job_context: ConnectedJobContext) -> dict[str, object]:
    variables = JobVariables.model_validate(job_context.variables.to_dict())
    job_context.log.info(f"Processing job {job_context.job_key}: {variables}")

    start = datetime.fromisoformat(variables.startTime)
    run(("--start", start.isoformat(), variables.namespace))
    return {"result": "processed"}

async def main() -> None:
    async with CamundaAsyncClient() as client:
        topology = await client.get_topology()
        print(topology)

        print("Creating worker...")
        config = WorkerConfig(
            job_type="extract-result",
            job_timeout_milliseconds=30_000,
        )
        client.create_job_worker(config=config, callback=handle_job)
        print("Worker created successfully.")
        print("Awaiting jobs...")
        # Keep workers running until cancelled
        await client.run_workers()

asyncio.run(main())






