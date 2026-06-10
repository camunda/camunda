# Zeebe ADRs

Architecture Decision Records scoped to the Zeebe module (engine, protocol, exporters, and the
process-execution data path). These are module-scoped decisions; see the
[top-level ADR README](../../../docs/adr/README.md) for the tier structure and cross-cutting ADRs.

## Index

### 8.10

|                                 ADR                                 |                                                   Decision                                                    |
|---------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| [0001](0001-810-message-correlation-business-id-cross-partition.md) | Business ID message correlation: `P_K` owns messages, `P_B` enforces uniqueness, `P_K` pulls for lock release |
| [0002](0002-810-message-start-rejection-retry.md)                   | A way out for rejected cross-partition message-starts: retry the ask with back-off                            |

