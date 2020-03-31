# Internal Processing

Internally, Zeebe is implemented as a collection of _stream processors_ working on record streams \(partitions\). The stream processing model is used since it is a unified approach to provide:

* Command Protocol \(Request-Response\),
* Record Export \(Streaming\),
* Workflow Evaluation \(Asynchronous Background Tasks\)

Record export solves the history problem: The stream provides exactly the kind of exhaustive audit log that a workflow engine needs to produce.

## State Machines

Zeebe manages stateful entities: Jobs, Workflows, etc. Internally, these entities are implemented as _State Machines_ managed by a stream processor.

The concept of the state machine pattern is simple: An instance of a state machine is always in one of several logical states. From each state, a set of transitions defines the next possible states. Transitioning into a new state may produce outputs/side effects.

Let's look at the state machine for jobs. Simplified, it looks as follows:

![partition](/basics/internal-processing-job.png)

Every oval is a state. Every arrow is a state transition. Note how each state transition is only applicable in a specific state. For example, it is not possible to complete a job when it is in state `CREATED`.

## Events and Commands

Every state change in a state machine is called an *event*. Zeebe publishes every event as a record on the stream.

State changes can be requested by submitting a *command*. A Zeebe broker receives commands from two sources:

1. Clients send commands remotely. Examples: Deploying workflows, starting workflow instances, creating and completing jobs, etc.
2. The broker itself generates commands. Examples: Locking a job for exclusive processing by a worker, etc.

Once received, a command is published as a record on the addressed stream.

## Stateful Stream Processing

A stream processor reads the record stream sequentially and interprets the commands with respect to the addressed entity's lifecycle. More specifically, a stream processor repeatedly performs the following steps:

1. Consume the next command from the stream.
1. Determine whether the command is applicable based on the state lifecycle and the entity's current state.
1. If the command is applicable: Apply it to the state machine. If the command was sent by a client, send a reply/response.
1. If the command is not applicable: Reject it. If it was sent by a client, send an error reply/response.
1. Publish an event reporting the entity's new state.

For example, processing the _Create Job_ command produces the event _Job Created_.

## Command Triggers

A state change which occurred in one entity can automatically trigger a command for another entity. Example: When a job is completed, the corresponding workflow instance shall continue with the next step. Thus, the Event _Job Completed_ triggers the command _Complete Activity_.
