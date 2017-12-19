# Task Workers

A task worker is a component capable of performing a particular task.

## What is a Task?

A task is a _work item_ in a workflow. For example:

* Sending an email
* Generating a PDF document
* Updating customer data in a backend system

A task has the following properties:

* **Type**: Describes the kind of work item this is, defined in the workflow. The type allows workers to specify which tasks they are able to perform.
* **Payload**: The business data the worker should work on in the form of a JSON document/object.
* **Headers**: Additional metadata defined in the workflow, e.g. describing the structure of the payload.

## Task Subscriptions

To start executing tasks, a worker must open a *task subscription* for a specific task type. Task subscriptions allow workers to get notified as new tasks are created on broker side. Upon receiving a notification, a worker performs the task and sends back a _complete_ or _fail_ message depending on whether the task could be completed successfully or not.

![task-subscriptions](/basics/task-workers-subscriptions.png)

Many workers can subscribe to the same task type in order to scale up processing. In this scenario, the Zeebe broker ensures that each task is published exclusively to only one of the subscribers. It eventually reassigns the task in case the assigned worker is unresponsive. This means, task processing has *at least once* semantics.

## Task Queueing

An important aspect of Zeebe is that a broker does not assume that a task worker is able to process tasks right away or at any particular rate.

Zeebe decouples creation of tasks from performing the work on these tasks. It is always possible to create tasks at the highest possible rate, regardless of whether there is currently a worker available to work on them or not. This is possible as Zeebe queues tasks until it can push them out to the task workers. If no task worker is currently subscribed, tasks remain queued. If a worker is subscribed, Zeebe's backpressure protocols ensure that workers can control the rate at which they receive tasks.

This allows the system to take in bursts of traffic and effectively act as a _buffer_ in front of the task workers.

## Standalone Tasks

Tasks can also be created standalone \(i.e., without an orchestrating workflow\). In that case, Zeebe acts as a task queue.
