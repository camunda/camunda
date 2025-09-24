# The Zeebe Workflow Engine

The workflow engine is the core component of Zeebe. It is responsible for executing process
instances, managing state, and ensuring the correct execution of BPMN processes, as well as the
evaluation of decision instances.

This README will provide an overview of the engine's architecture, key components, and guidelines
for contributing to its development.

## High-level engine architecture

The Zeebe Workflow Engine is a state machine for entities like jobs and processes.

The engine is implemented as a state machine that maintains the state of entities (jobs, processes, etc.) in a key-value store (RocksDB).
State transitions are driven by a stream of records that are appended to an append-only log.

The engine is implemented as a collection of stream processors. It is designed to be able to recover its state by replaying the record stream.
For this reason, records in the stream are split into commands, events, and rejections.

- **Commands** are requests to the engine to perform some action, e.g. create a new process instance or complete a job.
- **Events** are the result of processing commands, e.g. a process instance was created or a job was completed.
- **Rejections** are indications that a command could not be processed.

During **command** processing, the engine may append events and follow-up commands to the stream.
State changes are applied when processing **events** from the stream.

To learn more about replay, see [ZEP004](https://github.com/zeebe-io/enhancements/blob/master/ZEP004-wf-stream-processing.md).

## Basic module structure

At the root of the `zeebe/engine` module, you will find the `Engine` class where you can map the
high-level engine architecture concepts you were introduced to in the section above.

There is also the `EngineConfiguration` class which is used to configure the engine. It allows you
to adjust the behavior of the engine through various parameters such as cache sizes, timeouts, and
other settings.

The following are the main packages that make up the engine's core functionality.

### `processing` package

The `processing` package is where the core processing logic of the engine resides. This is where
the journey of a command record begins, once it enters the engine domain.

- Browse the `EngineProcessor` and `BpmnProcessor` classes to understand how the engine processes
  different types of records.
- Processor classes output results through various classes in the `streamprocessor/writers` package.
  A processor may use a `stateWriter` to modify the state, a `responseWriter` to send a response back
  to the client, or a `rejectionWriter` to reject a command. It can also add new command records to
  itself using a `commandWriter`.
- Shared logic used by multiple `Processor` classes is contained withing `Behavior` classes. As an
  example, have a look at the `BpmnJobActivationBehavior` class to see how job activation is handled.
- In the `EngineProcessor` and `BpmnProcessor` classes, you may notice `Checker` classes. These are
  usually "listener"-type classes that react to the changes of the Engine modes (i.e. `processing`,
  `replay`), and perform actions accordingly. For example, the `JobTimeoutChecker` checks for jobs
  that have timed out when the engine is in `processing` mode, and writes a `JobTimedOut` command
  using a `commandWriter`.

### `state` package

// TODO: add state package description

### `metrics` package

// TODO: add metrics package description

## How do I implement a new feature?

[Developer handpook](../../docs/zeebe/developer_handbook.md)

- TODO: example feature

### Engine Do's and Don'ts

### Testing guidelines

### Follow-up tasks

- Zeebe QA, Camunda QA, clients...
