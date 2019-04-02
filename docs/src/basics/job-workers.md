# Job Workers

A job worker is a component capable of performing a particular step in a workflow.

## What is a Job?

A job is a *work item* in a workflow. For example:

* Processing a payment
* Generating a PDF document
* Updating customer data in a backend system

A job has the following properties:

* **Type**: Describes the work item and is defined in each task in the workflow. The type is referenced by workers to request the jobs they are able to perform.
* **Variables**: The contextual/business data of the workflow instance that is required by the worker to do its work.
* **Custom Headers**: Additional static metadata defined in the workflow. Mostly used to configure a worker which is used for more than one workflow step.

## Requesting Jobs from the Broker

Job workers request jobs of a certain type from the broker on a regular interval (i.e. polling). This interval and the number of jobs requested are configurable in the Zeebe client.

If one or more jobs of the requested type are available, the broker will stream jobs to the worker. Upon receiving jobs, a worker performs them and sends back a *complete* or *fail* message for each job depending on whether the job could be completed successfully or not.

For example, the following workflow might generate three different types of jobs: `process-payment`, `fetch-items`, and `ship-parcel`:

![order-workflow-model](/basics/order-process.png)

Three different job workers, one for each job type, could request jobs from Zeebe:

![zeebe-job-workers-requesting-jobs](/basics/zeebe-job-workers-graphic.png)

Many workers can request the same job type in order to scale up processing. In this scenario, the broker ensures that each job is sent to only one of the workers.

On requesting jobs, the following properties can be set:

* **Worker**: The identifier of the worker. Used for auditing purposes.
* **Timeout**: The time a job is assigned to the worker. If a job is not completed within this time then it can be requested again from a worker.
* **MaxJobsToActivate**: The maximum number of jobs which should be activated by this request.
* **FetchVariables**: A list of variables names which are required. If the list is empty, all variables of the workflow instance are requested.

## Job Queueing

Zeebe decouples creation of jobs from performing the work on them. It is always possible to create jobs at the highest possible rate, regardless of whether or not there's a worker available to work on them. This is possible because Zeebe queues jobs until workers request them. If no job worker is currently requesting jobs, jobs remain queued. Because workers request jobs from the broker, the workers have control over the rate at which they take on new jobs.

This allows the broker to handle bursts of traffic and effectively act as a _buffer_ in front of the job workers.
