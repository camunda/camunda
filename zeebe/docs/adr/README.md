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
| [0009](0009-810-agent-execution-in-engine-records.md)               | Agent execution recorded as engine records: storage-independent pipeline, content passes through              |
| [0010](0010-810-agent-definition-from-bpmn-marker.md)               | Agent definitions derived at deploy time from a `zeebe:agentDefinition` BPMN marker, no marker no agent       |
| [0011](0011-810-agent-instance-written-by-agent-runtime.md)         | Agent instance as the anchor for one agent run, written by the agent runtime, engine owns completion          |
| [0012](0012-810-agent-history-commit-under-job-lease.md)            | Agent history committed per job activation under the job lease, superseded items discarded at commit          |

