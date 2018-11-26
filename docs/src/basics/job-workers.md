# Job Workers

A job worker is a component capable of performing a particular step in a workflow.

## What is a Job?

A job is a _work item_ in a workflow. For example:

* Sending an email
* Generating a PDF document
* Updating customer data in a backend system

A job has the following properties:

* **Type**: Describes the kind of work item this is, defined in the workflow. The type allows workers to specify which jobs they are able to perform.
* **Payload**: The business data the worker should work on in the form of a JSON document/object.
* **Headers**: Additional metadata defined in the workflow, e.g. describing the structure of the payload.

## Connecting to the Broker

To start executing jobs, a worker must connect to the Zeebe broker declaring the specific job type it can handle. The Zeebe broker will then notify the worker whenever new jobs are created based on a publish-subsribe protocol. Upon receiving a notification, a worker performs the job and sends back a _complete_ or _fail_ message depending on whether the job could be completed successfully or not.

![task-subscriptions](/basics/task-workers-subscriptions.png)

Many workers can subscribe to the same job type in order to scale up processing. In this scenario, the Zeebe broker ensures that each job is published exclusively to only one of the workers. It eventually reassigns the job in case the assigned worker is unresponsive. This means, job processing has *at least once* semantics.

## Job Queueing

An important aspect of Zeebe is that a broker does not assume that a worker is always available or that it can process jobs at any particular rate.

Zeebe decouples creation of jobs from performing the work on them. It is always possible to create jobs at the highest possible rate, regardless of whether there is currently a worker available to work on them or not. This is possible as Zeebe queues jobs until it can push them out to workers. If no job worker is currently subscribed, jobs remain queued. If a worker is subscribed, Zeebe's backpressure protocols ensure that workers can control the rate at which they receive jobs.

This allows the system to take in bursts of traffic and effectively act as a _buffer_ in front of the job workers.
