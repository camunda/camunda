# Task Workers

A task worker is a component capable of performing a particular task.

## What is a Task?

A task is a _work item_. For example: sending an email or other kind of notification, generating a document, updating customer data in a backend system, etc.

A task has the following properties:

* **Type**: Describes the kind of work item this is. The type allows workers to specify which tasks they are able to perform.
* **Payload**: The business data the worker should work on in the form of a JSON document/object.
* **Headers**: Additional metadata or configuration which can be read by the worker in the form of a JSON document/object.

## Task Subscriptions

To start executing tasks, workers must open task _subscriptions._ Task subscriptions allow the workers to get notified as new tasks are created on the broker side. Upon receiving a notification, a worker performs the task and sends back a _complete_ or _fail_ message \(depending on whether it could successfully complete the task or not\).

\[TODO: image\]

## Decoupling / Buffering

An important aspect of Zeebe is that it does not assume that a task worker is able to process tasks right away or at any particular rate.

Zeebe decouples creating of tasks from performing the work on these tasks. It is always possible to create tasks at the highest possible rate, regardless of whether there is currently a worker available to work on them or not. This is possible as Zeebe queues tasks until it can push them out to the task workers. If no task workers have currently opened subscriptions, tasks remain queued. If a subscription is open, the backpressure protocols ensure that workers can control the rate at which tasks get assigned to them.

\[TODO: image \(buffer\)\]

This allows the system to take in bursts of traffic and effectively act as a kind of _buffer_ in front of the task workers.

## Tasks and Workflows

An individual step in a workflow represents a task. The task's type, headers and payload can be specified through the workflow. The payload is based on the workflow's data flow, making it possible to use the output of one task worker as the input to another task worker \(intermediary payload transformations are supported as well\).

\[TODO: image\]

## Standalone Tasks

Tasks can also be created standalone \(i.e., without an orchestrating workflow\). In that case, Zeebe acts as an Asynchronous Task or Job Queue.

