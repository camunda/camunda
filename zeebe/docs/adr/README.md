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

### Proposed

|                   ADR                    |                                 Decision                                 |
|------------------------------------------|--------------------------------------------------------------------------|
| [0007](0007-TBD-concurrent-exporters.md) | One actor per exporter: decouple readers, positions, and failure domains |

