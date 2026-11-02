# Zeebe ADRs

Architecture Decision Records scoped to the Zeebe module (engine, protocol, exporters, and the
process-execution data path). These are module-scoped decisions; see the
[top-level ADR README](../../../docs/adr/README.md) for the tier structure and cross-cutting ADRs.

## Index

### 8.10

|                                 ADR                                 |                                                   Decision                                                    |
|---------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| [0001](0001-810-message-correlation-business-id-cross-partition.md) | Business ID message correlation: `P_K` owns messages, `P_B` enforces uniqueness, `P_K` pulls for lock release |
| [0002](0002-810-message-start-rejection-retry.md)                   | Retry rejected message-starts until they start or their TTL expires                                           |
| [0003](0003-810-business-id-call-activity-propagation.md)           | Call activity child Business ID: single `businessId` attribute on `zeebe:calledElement`                       |
| [0004](0004-810-physical-tenant-job-streaming.md)                   | Physical-tenant-aware job streaming: per-group broker services, group-scoped control topics, 8.9-compatible   |
| [0005](0005-810-job-lease.md)                                       | Job lease: opt-in random opaque fencing token per activation, monotonic, fencing worker lifecycle commands    |
| [0006](0006-810-late-business-id-assignment.md)                     | Late Business ID assignment: one irreversible forward-only assignment on a running instance (uniqueness off)  |
| [0007](0007-810-job-waiting-for-secret-resolution-state.md)         | Persisted `WAITING_FOR_SECRET_RESOLUTION` job state for jobs parked while their secret references resolve     |
| [0008](0008-810-suspended-job-state.md)                             | Persisted `SUSPENDED` job state that withholds the jobs of a suspended process instance from hand-out         |
| [0009](0009-810-suspended-timer-buffering.md)                       | Buffer a due timer trigger while suspended, drop its due-date index, fire it in the drain chain               |
| [0010](0010-810-agent-execution-in-engine-records.md)               | Agent execution recorded as engine records: storage-independent pipeline, content passes through              |
| [0011](0011-810-agent-definition-from-bpmn-marker.md)               | Agent definitions derived at deploy time from a `zeebe:agentDefinition` BPMN marker, no marker no agent       |
| [0012](0012-810-agent-instance-written-by-agent-runtime.md)         | Agent instance as the anchor for one agent run, written by the agent runtime, engine owns completion          |
| [0013](0013-810-agent-history-commit-under-job-lease.md)            | Agent history committed per job activation under the job lease, superseded items discarded at commit          |

### 8.11

|                         ADR                         |                                                                          Decision                                                                           |
|-----------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| [0014](0014-811-tag-scoped-run-isolation.md)        | Tag-scoped run isolation: run scope rides a reserved instance tag, copied onto subscriptions, filtered locally                                              |
| [0015](0015-811-per-instance-recording-switches.md) | Per-instance recording switches set at create time via one `CONFIGURE_EXECUTION` instruction, read at root, withheld from workers, REST-only                |
| [0016](0016-811-reserved-jobs.md)                   | Reserved jobs: a caller-chosen token hides an instance's jobs from workers; token separate from the lease token; one-way release                            |
| [0017](0017-811-stubbed-call-activities.md)         | Stubbed call activities: activate without a child and wait on a stand-in job carrying its own `JobKind.CALL_ACTIVITY_STUB`; `runCalledProcess` escape hatch |
| [0018](0018-811-held-timers.md)                     | Held timers: kept out of the due-date scheduler, fired on demand by `(instance, elementId)`, authorized not tokened                                         |

