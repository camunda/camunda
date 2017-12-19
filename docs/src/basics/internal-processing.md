# Internal Processing

Internally, Zeebe is implemented as a collection of _stream processors_ working on event streams \(partitions\). The stream processing model is used since it is a unified approach to:

* Command Protocol \(Request-Response\),
* Subscription Protocol \(Streaming\),
* Workflow Evaluation \(Asynchronous Background Tasks\)

In addition, it solves the history problem: The streams of commands and events provides exactly the kind of exhaustive audit log that a workflow engine needs to produce.

## State Machines

Zeebe manages stateful entities: Tasks, Workflows, etc. Internally, these entities are implemented as _State Machines_ managed by a broker.

The concept of the state machine pattern is simple: An instance of a state machine is always in one of several logical states. From each state, a set of transitions defines the next possible states. Transitioning into a new state may produce outputs/side effects.

Let's look at the state machine for tasks. Simplified, it looks as follows:

![partition](/basics/internal-processing-task.png)

Every oval is a state. Every arrow is a state transition. Note how each state transition is only applicable in a specific state. For example, it is not possible to complete a task when it is in state `CREATED`.

## Stateful Stream Processing

Zeebe maps state changes to events in the event stream model. That means, there is an event for every state change of an entity.

State changes can be requested by submitting a *command*. A broker receives commands from two sources:

1. Clients sends commands remotely. Examples: deploying workflows, starting workflows, creating and completing tasks, etc.
2. The broker itself generates commands. Examples: Locking a task for exclusive processing by a task worker, etc.

Once received, a command is published as an event on the addressed event stream. A stream processor interprets the command with respect to the addressed entity's lifecycle. More specifically, a stream processor repeatedly performs the following steps:

1. Consume the next command from the stream.
1. Determine whether the command is applicable based on  the state lifecycle and the entity's current state.
1. If the command is applicable: Apply it to the state machine. If the command was sent by a client, send a reply/response.
1. If the command is not applicable: Reject it. If it was sent by a client, send an error reply/response.
1. Publish an event reporting the entity's new state.

For example, processing the _Create Task_ command produces the event _Task Created_.

## Command Triggers

A state change which occurred in one entity can automatically trigger a command for another entity. Example: When a task is completed, the corresponding workflow instance should continue to the next step. Thus, the Event _Task Completed_ triggers the command _Complete Activity_.
