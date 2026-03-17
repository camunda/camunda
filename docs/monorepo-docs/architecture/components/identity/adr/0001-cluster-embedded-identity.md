# ADR-0001: Cluster-Embedded Identity Instead of External Component

## Status

Accepted

## Context

Before Camunda 8.8, runtime components (Zeebe, Operate, Tasklist) depended on Management Identity
plus Keycloak and a dedicated Postgres database for authentication and authorization. This increased
operational overhead, introduced additional infrastructure dependencies, and created tight coupling
between the runtime cluster and the Management Identity service.

## Decision

Embed Identity directly inside the Orchestration Cluster and treat it as the source of truth for
runtime IAM. Identity runs as part of the Orchestration Cluster artifact (JAR/container) and
stores its entities using Zeebe's existing primary (RocksDB) and secondary (ES/OS/RDBMS) storage
instead of a separate identity database.

## Consequences

### Positive

- Fewer moving parts for runtime deployments; simpler high availability and disaster recovery.
- Runtime access no longer depends on Management Identity availability.
- Identity lifecycle (startup, scaling, failover) follows the Orchestration Cluster lifecycle.
- No dedicated identity database required for runtime.

### Negative

- Additional migration complexity when upgrading from pre-8.8 clusters; handled by the dedicated
  Identity Migration App.
- Management Identity remains the source of truth for Web Modeler, Console, and Optimize
  (Self-Managed), resulting in a temporary dual identity model during the transition period.

