# Management endpoint inventory for multi-physical-tenant clusters (8.10)

**DRI**: Lena Schönburg

**Status**: Draft (8.10)

**Purpose**: The authoritative list of management endpoints in 8.10.

**Audience**: Engineers working on management endpoints, operators.

## Context

### Tenant selection mechanics

- **REST**: path prefix `/physical-tenants/{id}/v2/...`; the unprefixed path resolves to the default physical tenant.
- **gRPC**: `Camunda-Physical-Tenant` header, fallback to the default physical tenant.
- **Actuator**: Currently not scoped to any particular physical tenant, could support a `?physicalTenant=<id>` query parameter.

## Decision

### D1. PT-scoped REST management endpoints

Per-PT endpoints in both unprefixed `/v2/` (fallback to default physical tenant) and `/physical-tenants/{id}/v2/` forms.
The only exception to that rule is the `/v2/status` endpoint, see [ADR 001 D3](./001-physical-tenant-health-status-topology.md).

#### Health and Topology

| Endpoint           | Behavior                                                                                   |
|--------------------|--------------------------------------------------------------------------------------------|
| `GET /v2/status`   | Status of the physical tenant. **Special case: no authentication, no PT-prefix supported** |
| `GET /v2/topology` | Topology of the physical tenant.                                                           |

#### Backup

Exporting pause/resume is included here because it is part of the overall backup procedure.
Restore endpoints are not fully implemented yet but should follow the same pattern.

| Endpoint                                      | Behavior                                             |
|-----------------------------------------------|------------------------------------------------------|
| `POST /v2/exporting/pause?soft=`              | Pause exporting.                                     |
| `POST /v2/exporting/resume`                   | Resume exporting.                                    |
| `POST /v2/backups/runtime`                    | Trigger runtime backup.                              |
| `GET /v2/backups/runtime/{backupId}`          | Status of one runtime backup.                        |
| `GET /v2/backups/runtime?prefix=`             | List runtime backups, optionally filtered by prefix. |
| `DELETE /v2/backups/runtime/{backupId}`       | Delete runtime backup.                               |
| `GET /v2/backups/runtime/state`               | Query runtime backup ranges.                         |
| `POST /v2/backups/runtime/state/sync`         | Force write state to the backup store.               |
| `DELETE /v2/backups/runtime/state`            | Delete the runtime backup state.                     |
| `POST /v2/backups/history`                    | Trigger history backup                               |
| `GET /v2/backups/history/{backupId}`          | Status of history backup.                            |
| `GET /v2/backups/history/{backupId}?pattern=` | List history backups.                                |
| `DELETE /v2/backups/history/{backupId}`       | Delete history backup.                               |

### D2. Cluster-wide REST management endpoints

#### Health and Topology

|          Endpoint          | Behavior                                                              |
|----------------------------|-----------------------------------------------------------------------|
| `GET /cluster/v2/status`   | Status of the entire cluster, aggregated over all physical tenants.   |
| `GET /cluster/v2/topology` | Topology of the entire cluster, aggregated over all physical tenants. |

#### Backup

| Endpoint                                              | Behavior                                                                         |
|-------------------------------------------------------|----------------------------------------------------------------------------------|
| `POST /cluster/v2/exporting/pause?soft=`              | Pause exporting across all physical tenants.                                     |
| `POST  /cluster/v2/exporting/resume`                  | Resume exporting across all physical tenants.                                    |
| `POST  /cluster/v2/backups/runtime`                   | Trigger runtime backup across all physical tenants.                              |
| `GET  /cluster/v2/backups/runtime/{backupId}`         | Status of runtime backups with the same id across all physical tenants.          |
| `GET  /cluster/v2/backups/runtime?prefix=`            | List runtime backups across all physical tenants, optionally filtered by prefix. |
| `DELETE  /cluster/v2/backups/runtime/{backupId}`      | Delete runtime backups with the same id across all physical tenants.             |
| `GET  /cluster/v2/backups/runtime/state`              | Query runtime backup ranges across all physical tenants.                         |
| `POST /cluster/v2/backups/runtime/state/sync`         | Force write state to the backup store for each physical tenant.                  |
| `DELETE /cluster/v2/backups/runtime/state`            | Delete the runtime backup state for all physical tenants.                        |
| `POST /cluster/v2/backups/history`                    | Trigger history backup across all physical tenants.                              |
| `GET /cluster/v2/backups/history/{backupId}`          | Status of history backups of the same id across all physical tenants.            |
| `GET /cluster/v2/backups/history/{backupId}?pattern=` | List history backups across all physical tenants.                                |
| `DELETE /cluster/v2/backups/history/{backupId}`       | Delete history backups of the same id across all physical tenants.               |

### D3. Actuator management endpoints

| Actuator                                          | No `physicalTenant` param | With `?physicalTenant={id}`                 |
|---------------------------------------------------|---------------------------|---------------------------------------------|
| `backupRuntime` (`BackupEndpoint`)                | Cluster-wide              | Specified physical tenant only              |
| `backupHistory` (`BackupController`)              | Cluster-wide              | Specified physical tenant only              |
| `exporting` pause/resume                          | Cluster-wide              | Specified physical tenant only              |
| `exporters` (list/enable/disable/delete)          | Cluster-wide              | Specified physical tenant only              |
| `cluster`                                         | Cluster-wide              | Specified physical tenant only              |
| `cluster/purge`                                   | Cluster-wide              | Specified physical tenant only              |
| `rebalance`                                       | Cluster-wide              | Not supported                               |
| `flowControl`                                     | Cluster-wide              | Not supported                               |
| `clock`                                           | Node                      | Not supported                               |
| `banning`                                         | Default physical tenant   | Specified physical tenant only              |
| `partitions`                                      | Node                      | Specified physical tenant only              |
| `jobstreams`                                      | Node                      | Node, filtered to specified physical tenant |
| `loggers`, `health*`, `prometheus`, `configprops` | Node-scoped               | Not supported                               |

### D2. Cluster-wide backup contract

A cluster-wide backup is a *set of independent per-tenant backups*, not an atomic snapshot of the entire cluster.
The caller-supplied `backupId` is used for **every** tenant's backup; backups are stored separately so there's no risk of collisions.
The response body always lists the tenants whose backups *were* triggered so the operator can monitor or delete them.
If any tenant cannot be reached/triggered, the status code indicates an error, but the body still contains all backups that _were_ triggered.

## Consequences
- Single-tenant clusters see no change, all endpoints keep their existing behavior.
- Existing actuator request/response schemas are extended with per-PT information but remain backwards compatible.
- **Scheduled Backups**
  - When a tenant has scheduled backups enabled, backup ids are generated and an explicit id is rejected.
  - Since backup configuration is per-PT, tenants of one cluster may be in different modes.
  - Requesting a backup with an explicit id fails if *any* tenant is using scheduled backups.
  - Requesting a backup without an explicit id fails if *any* tenant is not using scheduled backups.
  - Mixed configurations, where one physical tenant uses scheduled backups and the other does not, must fall back to per-PT API.

## Alternatives considered

- No-param actuator = default tenant instead of all tenants, breaks the actuator's established whole-cluster meaning.
