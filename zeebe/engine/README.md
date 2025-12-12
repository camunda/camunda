# The Zeebe Workflow Engine

The workflow engine is a core component of Zeebe.

It is responsible for the correct execution of BPMN processes and the evaluation of DMN decisions.
It also manages all system entities like users, roles, and authorizations for Identity, as well as the user tasks and forms for Tasklist.

This README provides an overview of the engine's architecture, module structure, and guidelines for contributing to its development.

## High-level engine architecture

The Zeebe Workflow Engine is a state machine built as a stream of records.
It keeps track of the state of all entities related to process orchestration (like processes, process instances, jobs, and user tasks), as well as system related entities (like users, and batch operations).

The state of these entities is stored in a key-value store (RocksDB).
State transitions are driven by a stream of records that are appended to an append-only logstream.

The engine is implemented as a collection of command processors and event appliers.

- **Commands** are requests to the engine to perform some action, e.g. create a new process instance or trigger a timer.
- **Events** are the result of processing commands describing that the state has changed, e.g. a process instance was created or a timer was triggered.
- **Command rejections** are indications that a command could not be processed, e.g. because the requested change was not allowed.

The engine operates in two modes: processing and replay.

### Replay mode

In **replay** mode, the engine applies **events** to reconstruct its state.
The engine always starts in replay mode first.
Events are applied deterministically and in the order that they were added to the logstream.

To avoid unnecessary work, replay can start from a snapshot of the state.
Replay then starts with the first event that has not yet been applied to that snapshot.

To learn more about replay, see [ZEP004](https://github.com/zeebe-io/enhancements/blob/master/ZEP004-wf-stream-processing.md).

### Processing mode

In **processing** mode, the engine processes **commands**.
Commands are processed in the order that they were added to the logstream.

During command processing, the engine can append new records to the logstream, respond to requests, and execute side-effects.

Command processors can:
- reject commands by appending command rejections,
- append and apply events to change the state,
- append follow-up commands to request next steps to do,
- respond to requests with a rejection or an event,
- append side-effects to be executed when processing successfully completes.

There are three sources of commands.

- Clients can request changes by sending commands, e.g. create a new process instance.
- Command processors can request next steps by appending commands, e.g. flow to the next element in the process after completing a task.
- Scheduled tasks that run periodically inside the engine can append commands, e.g. to remove a message from the buffer after its TTL has expired.

### Relation to other components

The workflow engine is build on top of the `protocol` and `stream-platform` modules.

The `procotol` module defines the available records.
This includes their schema as well as the available value types which specify the data schema of records of that particular type.
For example, records of the value type `JOB` contain data specific to jobs.

The `stream-platform` module offers the replicated logstream.
It instructs the engine to process commands and replay events.
The stream platform can keep multiple engines, one for each partition (a shard of the data).
The stream platform provides access to the state that the engine can modify.
This access is split into two parts.
- a processing state which is modifable, which is used during processing and replay.
- a scheduled task state which is immutable, which is used by the scheduled tasks.

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

The `state` package contains the state model of the engine. This is the next stop after a command
record is processed by a processor class. Processor classes use the `stateWriter` to output event
records. These event records are then applied to the various Zeebe `State` classes using `Applier`
classes, both contained withing this package.

You can find all the applier classes listed in the `EventAppliers` class. `EventApplier` classes
extract the data from the event record and pass it to a `State` class. One example is the
`UserCreatedApplier` class, which passes the user information from a `User` event record to the
`UserState` class. It is important to not that if the behavior of an `EventApplier` class is changed,
a follow-up version of that `EventApplier` class must be created to ensure that the engine can
correctly apply the event record in the future.

The various `State` classes are instantiated in the `ProcessingDbState` class, which is used by
the `EngineProcessor` and `BpmnProcessor` classes to access the state of the engine, as well as the
`EventApplier` classes to apply event records to the state.

However, the `Processor` classes may only access the read-only interfaces of the `State` classes,
while the `EventApplier` classes should use the `MutableState` interfaces of a State implementation.
This is to ensure that the state is only modified by event records, and not by processor classes. As
an example, consider that in the `UserCreateProcessor` class, the `UserState` interface is used to
access the read-only `DbUserState` methods, but the `UserCreatedApplier` class uses the
`MutableUserState` interface to access the `DbUserState` methods that modify the state.

### `metrics` package

The `metrics` package contains the classes used by the engine to collect processing-related metrics.
When adding a new feature, consider if it would be useful to add new metrics to monitor its behavior.

## How do I implement a new feature?

[Developer handpook](../../docs/zeebe/developer_handbook.md)

### Example feature

To illustrate how to implement a new feature in the engine, let's consider the following example.

**Feature Description**: Camunda has introduced a user management system that allows managing users and their roles within the orchestration cluster. The engine needs to support a new command to create a user.

**Steps to Implement the Feature**:

1. **Decide whether you need a new record and value type**.
   Records are defined in the [Zeebe protocol](../protocol) and implemented in the ['zeebe-protocol-impl'](../protocol-impl) module.
   In this case, we do need a new record [`UserRecord`](../protocol/src/main/java/io/camunda/zeebe/protocol/record/value/UserRecordValue.java).
   Follow [this guide](../../docs/zeebe/developer_handbook.md#how-to-create-a-new-record) on creating a new record.

2. **Introduce new state if needed**. Consider whether you need to add a new state type or if you can add new data to an existing state type.
   In this case, we need a new state type [`UserState`](src/main/java/io/camunda/zeebe/engine/state/immutable/UserState.java) to persist users.
   Start by defining immutable & mutable versions of the interface, then implement them in a single class, e.g. [`DbUserState`](src/main/java/io/camunda/zeebe/engine/state/user/DbUserState.java).
   You might need to break down your state class into multiple objects depending on the complexity.
   Add your new state to [`ProcessingState`](src/main/java/io/camunda/zeebe/engine/state/immutable/ProcessingState.java).

3. **Add a new Intent**. You normally need to introduce a new intent when you introduce a new value type.
   In this case, we need a new intent [`UserIntent`](../protocol/src/main/java/io/camunda/zeebe/protocol/record/intent/UserIntent.java).

4. **Add a new Processor**. You need to add a new processor to handle the new command.
   In this case, we need a new processor [`UserCreateProcessor`](src/main/java/io/camunda/zeebe/engine/processing/user/UserCreateProcessor.java) to handle the `CREATE` command.
   Register your new processor in [`EngineProcessors`](src/main/java/io/camunda/zeebe/engine/processing/EngineProcessors.java).

5. **Add a new EventApplier**.
   You need to add a new event applier to apply the new event to the state.
   In this case, we need a new event applier [`UserCreatedApplier`](src/main/java/io/camunda/zeebe/engine/state/appliers/UserCreatedApplier.java) to apply the `CREATED` event.
   Register your new event applier in [`EventAppliers`](src/main/java/io/camunda/zeebe/engine/state/appliers/EventAppliers.java).

### Testing guidelines

The `zeebe-worklow-engine` module contains various types of tests to ensure the quality of the
engine code is maintained. The module itself contains unit tests, while integration tests are
located in the [`zeebe/qa` module](../qa). Have a look at the [unit testing guidelines](../../docs/testing/unit.md)
for more information on how to structure your unit tests.

#### Testing the Zeebe state and event appliers

Zeebe `State` and `EventApplier` classes can be covered with JUnit5 test using the
`ProcessingStateExtension` class. Have a look at the [`UserStateTest` class](https://github.com/camunda/camunda/blob/3425483f300f638f8fe4e8b471b5b60eecfa5c44/zeebe/engine/src/test/java/io/camunda/zeebe/engine/state/user/UserStateTest.java#L29)
and [`TenantAppliersTest` class](https://github.com/camunda/camunda/blob/58257ce06621bd2bb9b7af43fb55e84bf24f0403/zeebe/engine/src/test/java/io/camunda/zeebe/engine/state/appliers/TenantAppliersTest.java#L36) as examples.

#### Testing the Zeebe processors

Zeebe `Processor` classes can be covered with JUnit4 tests using the `EngineRule` class. Have a look
at the [`JobCompleteAuthorizationTest` class](https://github.com/camunda/camunda/blob/fdac437c76893d2c8884ebefb7458b9c955caa56/zeebe/engine/src/test/java/io/camunda/zeebe/engine/processing/job/JobCompleteAuthorizationTest.java#L33)
as an example.

You may notice that the `EngineRule` instance provides `job()`, `user()`, and `authorization()`
methods. These provide instances of internal test clients that can be used to interact with the
engine and submit command records to it. When adding a new feature, consider adding (or expand) a
test client in the `util` test package. Have a look at the [`JobClient` class](https://github.com/camunda/camunda/blob/e472173bdb593798875f4e47eaf090b33742d491/zeebe/engine/src/test/java/io/camunda/zeebe/engine/util/client/JobClient.java#L29)
for inspiration.

You will also notice that the `EngineRule` is used in combination with the [`RecordingExporter` class](https://github.com/camunda/camunda/blob/63734638878c03e34889adc64c95fb17258a506d/zeebe/test-util/src/main/java/io/camunda/zeebe/test/util/record/RecordingExporter.java#L117)
to assert the state of the engine after processing a command. The `RecordingExporter` class allows
you to query the log stream for records of a specific type, intent, or value. Make sure you put a
`limit()` on your `RecordingExporter` query to avoid slowing down your tests with long-running
queries.

Finally, if your test fails, you will get a nicely formatted output of all the records that have been
processed by the test engine until the failure. This is provided by the [`CompactRecordLogger` class](https://github.com/camunda/camunda/blob/860df5667ef1e4ffa49b764360b25914b386d949/zeebe/test-util/src/main/java/io/camunda/zeebe/test/util/record/CompactRecordLogger.java#L146),
and is very useful for debugging test failures. You can also get this output by adding an
`assert false` statement at the end of your test.

Here is an example of a failure output from a test that:
1. Deploys a simple process with a service task
2. Creates a process instance of that process
3. And updates a job that was created for the service task.

The `CompactRecordLogger` output always provides a detailed legend of the abbreviations used:

<details>

        ['C'ommand/'E'event/'R'ejection] [valueType] [intent] - #[position]->#[source record position] K[key] - [summary of value]
        P9K999 - key; #999 - record position; "ID" element/process id; @"elementid"/[P9K999] - element with ID and key
        Keys are decomposed into partition id and per partition key (e.g. 2251799813685253 -> P1K005). If single partition, the partition is omitted.
        Long IDs are shortened (e.g. 'startEvent_5d56488e-0570-416c-ba2d-36d2a3acea78' -> 'star..acea78'
        Headers defined in 'Protocol' are abbreviated (e.g. 'io.camunda.zeebe:userTaskKey:2251799813685253' -> 'uTK:K005').

</details>

It will provide an abbreviated log of all the records that were processed until the failure:

<details>

```
C USG_MTRC  EXPORT     #01-> -1  -1 NONE:ACTIVE start[-1] end[-1] reset[-1] (no metricValues)
E USG_MTRC  EXPORTED   #02->#01 K01 NONE:ACTIVE start[-1] end[-1] reset[T08:33:10.737] (no metricValues)
C DPLY      CREATE     #03-> -1  -1
E PROC      CREATED    #04->#03 K03 process.bpmn -> "process" (version:1)
E DPLY      CREATED    #05->#03 K02 process.bpmn
C CREA      CREATE     #06-> -1  -1 new <process "process"> (default start)  (no vars)
C PI        ACTIVATE   #07->#06 K04 PROCESS "process" in <process "process"[K04]>
E CREA      CREATED    #08->#06 K05 new <process "process"> (default start)  (no vars)
E PI        ACTIVATING #09->#06 K04 PROCESS "process" in <process "process"[K04]> EI:[K04] PD:[K03]
E PI        ACTIVATED  #10->#06 K04 PROCESS "process" in <process "process"[K04]>
C PI        ACTIVATE   #11->#06  -1 START_EVENT "start" in <process "process"[K04]>
E PI        ACTIVATING #12->#06 K06 START_EVENT "start" in <process "process"[K04]> EI:[K04->K06] PD:[K03]
E PI        ACTIVATED  #13->#06 K06 START_EVENT "start" in <process "process"[K04]>
C PI        COMPLETE   #14->#06 K06 START_EVENT "start" in <process "process"[K04]>
E PI        COMPLETING #15->#06 K06 START_EVENT "start" in <process "process"[K04]>
E PI        COMPLETED  #16->#06 K06 START_EVENT "start" in <process "process"[K04]>
E PI        SQ_FLW_TKN #17->#06 K07 SEQUENCE_FLOW "sequenc..e88de26" in <process "process"[K04]>
C PI        ACTIVATE   #18->#06 K08 SERVICE_TASK "task" in <process "process"[K04]>
E PI        ACTIVATING #19->#06 K08 SERVICE_TASK "task" in <process "process"[K04]> EI:[K04->K08] PD:[K03]
E JOB       CREATED    #20->#06 K09 "id-7..d76e" @"task"[K08] 3 retries, (no vars)
E PI        ACTIVATED  #21->#06 K08 SERVICE_TASK "task" in <process "process"[K04]>
C JOB_BATCH ACTIVATE   #22-> -1  -1 "id-7921b6c2-24cf-402d-84a2-9e8defc6d76e" max: 10
E JOB_BATCH ACTIVATED  #23->#22 K10 "id-7921b6c2-24cf-402d-84a2-9e8defc6d76e" 1/10
                K09 "id-7..d76e" @"task"[K08] 3 retries, (no vars)
C JOB       UPDATE     #24-> -1 K09  5 retries, (no vars)
E JOB       UPDATED    #25->#24 K09 "id-7..d76e" @"task"[K08] 5 retries, (no vars)
```

</details>

And it will also provide the full XML of the deployed process for reference:

<details>

```
-------------- Deployed Processes ----------------------
process.bpmn -> "process" (version:1)[K03] ------
<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<definitions xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:ns0="http://camunda.org/schema/zeebe/1.0" exporter="Zeebe BPMN Model" exporterVersion="8.8.0-SNAPSHOT" id="definitions_c6519115-ec71-4fe4-a37f-761fca8f548d" xmlns:modeler="http://camunda.org/schema/modeler/1.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.8.0-SNAPSHOT" targetNamespace="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
  <process id="process" isExecutable="true">
    <startEvent id="start" name="start">
      <outgoing>sequenceFlow_dae74325-d938-4453-a09d-bb1f2e88de26</outgoing>
    </startEvent>
    <serviceTask id="task" name="task">
      <extensionElements>
        <ns0:taskDefinition type="id-7921b6c2-24cf-402d-84a2-9e8defc6d76e"/>
      </extensionElements>
      <incoming>sequenceFlow_dae74325-d938-4453-a09d-bb1f2e88de26</incoming>
      <outgoing>sequenceFlow_c1078aaf-1ff7-4d5b-bbee-0a143ae391da</outgoing>
    </serviceTask>
    <sequenceFlow id="sequenceFlow_dae74325-d938-4453-a09d-bb1f2e88de26" sourceRef="start" targetRef="task"/>
    <endEvent id="end" name="end">
      <incoming>sequenceFlow_c1078aaf-1ff7-4d5b-bbee-0a143ae391da</incoming>
    </endEvent>
    <sequenceFlow id="sequenceFlow_c1078aaf-1ff7-4d5b-bbee-0a143ae391da" sourceRef="task" targetRef="end"/>
  </process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_f56e5cde-79a6-4431-9a9e-94ae37d48ee8">
    <bpmndi:BPMNPlane bpmnElement="process" id="BPMNPlane_4bed1564-ef35-43cf-ace3-1ef28ba13e53">
      <bpmndi:BPMNShape bpmnElement="start" id="BPMNShape_5500c2e5-cba0-4637-9810-4afe66584c5c">
        <dc:Bounds height="36.0" width="36.0" x="100.0" y="100.0"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape bpmnElement="task" id="BPMNShape_1dd0acd6-4461-4d16-862d-7cd68086ed7e">
        <dc:Bounds height="80.0" width="100.0" x="186.0" y="78.0"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge bpmnElement="sequenceFlow_dae74325-d938-4453-a09d-bb1f2e88de26" id="BPMNEdge_1ce279c3-cfb7-4267-8e7e-648eca14ee9c">
        <di:waypoint x="136.0" y="118.0"/>
        <di:waypoint x="186.0" y="118.0"/>
      </bpmndi:BPMNEdge>
      <bpmndi:BPMNShape bpmnElement="end" id="BPMNShape_84dc5fa7-8608-4bb1-b3da-d5d216ae4faa">
        <dc:Bounds height="36.0" width="36.0" x="336.0" y="100.0"/>
      </bpmndi:BPMNShape>
      <bpmndi:BPMNEdge bpmnElement="sequenceFlow_c1078aaf-1ff7-4d5b-bbee-0a143ae391da" id="BPMNEdge_890babbd-622d-4eba-9e5d-4f4fff79d5e6">
        <di:waypoint x="286.0" y="118.0"/>
        <di:waypoint x="336.0" y="118.0"/>
      </bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</definitions>
```

</details>

And finally, it will provide a breakdown of the keys used in the test for easier debugging:

<details>

```
--------------- Decomposed keys (for debugging) -----------------
 -1 <-> -1
K01 <-> 2251799813685249
K02 <-> 2251799813685250
K03 <-> 2251799813685251
K04 <-> 2251799813685252
K05 <-> 2251799813685253
K06 <-> 2251799813685254
K07 <-> 2251799813685255
K08 <-> 2251799813685256
K09 <-> 2251799813685257
K10 <-> 2251799813685258
```

</details>

### Follow-up tasks

- Zeebe QA, clients...
- Consider adding [acceptance tests](../../docs/testing/acceptance.md) in the [Camunda QA module](../../qa/).

## Do's and Don'ts

### The stream processor is single threaded

- Only one command is processed at a time.
- **Don't** block the thread unnecessarily.
- **Do** take small steps by breaking up the processing into smaller chunks by appending follow-up commands.

### State changes should be reflected on the log stream

- This allows events to be replayed on restart to rebuild the state.
- **Don't** change state from a processor.
- **Don't** use a mutable state class from a processor.
- **Do** only produce state changes from an event applier.
- **Do** avoid changing state of an unrelated entity in another entity's event applier. In some cases this is fine, but not in all. For example, `Incident:CREATED` also disables a job.
- **Do** consider whether the state change should be visible on the logstream. For example, extract `Job:DISABLED` from `Incident:CREATED`  to highlight the change on the logstream.
- Keep documentation up to date
- **Do** update config templates when adding new configuration options.

### We prefer composition over inheritance

- **Do** create behavior classes to reuse code.
- **Don't** use subclasses for code reuse.

### Processors do not have access to the secondary storage

- **Do** access the immutable state from a processor.
- **Do** access the secondary storage from a scheduled task.

### Event appliers are not allowed to be changed after having been released

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

### Construction of records is expensive

- **Do** reuse and modify them for performance.
- **Do** use `wrap` to quickly pass data from one object to another.
- **Do** use `copyFrom` if you need a deep copy for more safety.

### Record values are data objects that are often reused across different records of the same value type

- **Do** use a different value type when only a subset of properties are relevant to a specific intent. But if you need to, please document the properties in the record that are only provided on specific intents.

### State classes provide references that may be modified without your knowledge

- **Do** use the `Supplier` parameter to create safe copies directly if needed. Comes at the cost of performance.

### The state and rejection writers do not take care of writing a response to the request

- The command may belong to a request that's awaiting a response.
- **Do** use the response writer to send a response.

### Side-effects are not guaranteed to be executed

- Side-effects are executed before the next command is picked up, but if execution fails or if failover occurs the side-effect may not be executed.
- **Don't** do things that must happen in a side-effect.
- **Do** use side-effects for things like updating caches or sending responses.

### Changes to transformers may result in differences during replay

- Transformers are not versioned like event appliers.
- **Do** be careful about changing existing transformers.

### Scheduled tasks can flood the logstream

- The stream processor may not be fast enough to process all the commands appended by a scheduled task.
- **Do** yield the thread after some time
- **Do** yield the thread after some number of actions taken, e.g. commands appended.

### The `RecordingExporter` keeps the stream open unless short-circuited

- **Do** short-circuit the `RecordingExporter`  using `.limit`, `.getFirst` , `.exists`.

