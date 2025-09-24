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

## How do I implement a new feature?

### Basic module structure

- Processors
- Appliers
- State classes

[Developer handpook](../../docs/zeebe/developer_handbook.md)

- TODO: example feature

### Testing guidelines

### Follow-up tasks

- Zeebe QA, Camunda QA, clients...
