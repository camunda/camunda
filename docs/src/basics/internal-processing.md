# Internal Processing

Internally, Zeebe is implemented as a collection of _stream processors_ working on top of Zeebe's persistent, replicated streams \(topics\). The stream processing model is chosen since it allows you to unify:

* Command-Oriented APIs \(Request-Response Style\),
* Subscription Protocols \(Streaming\),
* Asynchronous Background \ Batch Processing  \(Continuations in Workflows, Timers, etc...\).

In addition, it solves the history problem: The streams of commands and events provides exactly the kind of exhaustive audit log a workflow engine needs to produce.

## State Machines

Primarily, Zeebe manages stateful entities: Tasks, Workflows, Timers, ...Internally, these entities are modeled as _State Machines_ which live inside the broker.

The concept of the state machine is simple \(in fact it is so simple yet fundamental that it is usually one of the first things undergraduate students in computer science learn\): State machines are used to describe entities which can be one of several logical _states_. From each state, there is a set \(possibly empty\) of transitions allowing it to go into another state. Transitioning into a new state may also produce outputs\side effects.

In Zeebe, a client or the broker itself can create a new entity \(state machine\) or request an existing entity to transition into a new state. This request is called a _Command_ in Zeebe lingo.

Let's look at the example of the state machine for tasks. Simplified, it looks as follows:

|  | NONE | Created | Locked | Completed |
| :--- | :--- | :--- | :--- | :--- |
| **Create** | Created | - | - | - |
| **Lock** | - | Locked | - | - |
| **Complete** | - | - | Completed | - |

\(The left column contains commands. The other columns describe in which states the command is applicable and if it is which is the new state into which it causes the state machine to transition.\)

## Commands & Streams

As pointed out, commands request the state machines from going from one state into another. The Zeebe broker receives commands from two sources:

1. Clients send commands over the network protocol. Examples: deploying workflows, starting workflows, creating and completing tasks, etc... 
2. The broker itself generates commands. Examples: unlocking a task as the lock time has expired and the client has failed to complete the task, ...

As commands are received from one of these sources, they get inserted into a stream which is represented as a partitioned topic in Zeebe. The stream assigns an ordering to commands, controlling in which order they are applied to the state machines. Since the stream is persistent and replicated across machines, it is possible to replay the stream or fail over to another machine in the event of a failure.

## Stream Processors

Stream processors are responsible for processing streams of commands. A stream processor roughly does the following:

* Consume the next command from the stream.
* Decide whether the command is valid given the current state of the state machine.
* If the command is valid: apply the command to the state machine. If the command was sent by a client, send a reply/response.
* If the command is not valid: Reject it. If it was sent by a client, send an error reply/response.
* Publish an event reporting the outcome of the operation. For example, processing the _Create Task_ command produces the event _Task Created_.

## State, Replay and Snapshots

As pointed out, stream processors internally maintain the state of state machines. The state describes the result of applying all the commands in the order in which they occur in the stream. State is usually structured in the form of a \(Hash\) Map: entries are indexed by key, the key describing the identity of the entity \(task, workflow instance ...\).

The state can be reconstructed by replaying the commands from the stream. When replaying, only the state is updated, no side effects or other outputs are produced.

Periodically the state is snapshotted. A snapshot is a copy of the state tagged with the position of the last command in the stream which was applied to it. In the event of a system restart or failure, replaying can be done based on the last snapshot.

## Events

Events are produced by some stream processors to report on the outcome of applying commands.

## Event -&gt; Command Translation

An event which occurred in one entity can lead to a command being created for another entity. Example: the Event _Task Completed_ is translated into an _Complete Activity_ command requesting the current step in a workfow to be completed as well so that the workflow processing can continue. The event-&gt;command translation is done by a special kind of stream processor consuming the stream of events produced by the task processor and producing a stream of commands for the workflow stream processor.

