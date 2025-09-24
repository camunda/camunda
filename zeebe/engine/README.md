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

The engine operates in two modes: processing and replay.

- In **processing** mode, the engine processes **commands** and **events**. During command processing, the engine
  may append events, rejections, and follow-up commands to the stream.
- In **replay** mode, the engine processes only **events** from the stream to reconstruct its state.
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

### Do's and Don'ts

#### The stream processor is single threaded

- Only one command is processed at a time.
- **Don't** block the thread unnecessarily.
- **Do** take small steps by breaking up the processing into smaller chunks by appending follow-up commands.

#### State changes should be reflected on the log stream

- This allows events to be replayed on restart to rebuild the state.
- **Don't** change state from a processor.
- **Don't** use a mutable state class from a processor.
- **Do** only produce state changes from an event applier.
- **Do** avoid changing state of an unrelated entity in another entity's event applier. In some cases this is fine, but not in all. For example, `Incident:CREATED` also disables a job.
- **Do** consider whether the state change should be visible on the logstream. For example, extract `Job:DISABLED` from `Incident:CREATED`  to highlight the change on the logstream.
- Keep documentation up to date
- **Do** update config templates when adding new configuration options.

#### We prefer composition over inheritance

- **Do** create behavior classes to reuse code.
- **Don't** use subclasses for code reuse.

#### Processors do not have access to the secondary storage

- **Do** access the immutable state from a processor.
- **Do** access the secondary storage from a scheduled task.

#### Event appliers are not allowed to be changed after having been released

- Events must be applied in the same way on replay as when they were initially appended.
- **Don't** change the event applier implementation.
- **Do** register a new version of an event applier.
- **Don't** change code in methods called by event appliers, like state class implementations.
- It's fine to change it while unreleased. No need to register a new version in this case.
- Not all data in a command value can be trusted. It may be out of date by the time it's processed. 
- **Don't** trust data in a command's `RecordValue` as up to date.
- **Do** gain an understanding which command data is the requested change, e.g. Assign to `assignee`.
- **Do** read the latest entity data from the state.
- **Do** use the state data to decide whether the request is possible.
- **Do** use the state data as the basis for modifications.

#### Construction of records is expensive

- **Do** reuse and modify them for performance.
- **Do** use `wrap` to quickly pass data from one object to another.
- **Do** use `copyFrom` if you need a deep copy for more safety.

#### Record values are data objects that are often reused across different records of the same value type

- **Do** use a different value type when only a subset of properties are relevant to a specific intent. But if you need to, please document the properties in the record that are only provided on specific intents.

#### State classes provide references that may be modified without your knowledge

- **Do** use the `Supplier` parameter to create safe copies directly if needed. Comes at the cost of performance.

#### The state and rejection writers do not take care of writing a response to the request

- The command may belong to a request that's awaiting a response.
- **Do** use the response writer to send a response.

#### Side-effects are not guaranteed to be executed

- Side-effects are executed before the next command is picked up, but if execution fails or if failover occurs the side-effect may not be executed.
- **Don't** do things that must happen in a side-effect.
- **Do** use side-effects for things like updating caches or sending responses.

#### Changes to transformers may result in differences during replay

- Transformers are not versioned like event appliers.
- **Do** be careful about changing existing transformers.

#### Scheduled tasks can flood the logstream

- The stream processor may not be fast enough to process all the commands appended by a scheduled task.
- **Do** yield the thread after some time
- **Do** yield the thread after some number of actions taken, e.g. commands appended.

#### The `RecordingExporter` keeps the stream open unless short-circuited

- **Do** short-circuit the `RecordingExporter`  using `.limit`, `.getFirst` , `.exists`.

### Testing guidelines

### Follow-up tasks

- Zeebe QA, Camunda QA, clients...
