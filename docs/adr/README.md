# Architecture Decision Records

This directory contains ADRs for decisions that affect multiple modules across the monorepo.

## Tiers

|                            Scope                            |                    Location                     |
|-------------------------------------------------------------|-------------------------------------------------|
| Monorepo-wide (affects the whole repo or its core design)   | `docs/adr/` — this directory                    |
| Domain (spans a few related components, not the whole repo) | `docs/adr/<domain>/` — e.g. `docs/adr/storage/` |
| Module-scoped                                               | `<module>/docs/adr/`                            |

A **domain** is a logical subsystem that groups a few related components — for example, `storage`
(covers `db/`, `search/`, `webapps-schema/`, `schema-manager/`) or `security` (covers `security/`,
`authentication/`, `identity/`). A decision belongs at domain level when it is too broad for a
single module but does not affect the entire monorepo. Create the domain directory when the first
ADR for that domain is written.

## Index

- `security/001-endpoint-required-permission-mapping.md` — canonical v2 REST
  endpoint → required-permission mapping via the `x-required-permissions`
  OpenAPI vendor extension, with Spectral gap guard and engine drift guard
  (camunda/camunda#54727).
- `management/001-physical-tenant-health-status-topology.md` — health,
  readiness, and status semantics for multi-physical-tenant clusters;
  new `/cluster/v2/status` and `/cluster/v2/topology` endpoints
  (camunda/camunda#54299).
- `management/002-management-endpoint-authorization.md` — three-tier
  authorization model for management endpoints: unauthenticated actuators,
  per-tenant REST via the tenant's security chain, cluster-wide REST via
  the pre-configured cluster-admin (camunda/camunda#54898).
- `management/003-physical-tenant-management-endpoint-inventory.md` —
  authoritative inventory of management endpoints in 8.10: per-tenant and
  cluster-wide surfaces, actuator query-parameter selection, cluster-wide
  backup contract, backwards compatibility.
- `management/004-cluster-wide-history-backup.md` -
  Response contract of the cluster-wide history backup endpoints: per-tenant
  outcomes with no cluster-level aggregate, absence treated as success rather
  than failure, an all-or-nothing fan-out, and why the `backupHistory`
  actuator's backwards-compatible shape is not inherited
  (camunda/camunda#57738).
- `management/005-per-physical-tenant-schema-initialization.md` — per-tenant
  secondary-storage schema initialization: concurrent per-tenant init with
  background retry, a startup gate released once every tenant has settled and
  one is serviceable (held on gateway nodes for Elasticsearch/OpenSearch, on
  every node for RDBMS), and a context abort only when every tenant fails
  terminally (camunda/camunda#57025, camunda/camunda#54299).
- `clients/0001-unify-spring-starter-on-multi-client-config-path.md` — collapse
  the `camunda-spring-boot-starter` onto a single (multi-client) auto-config
  path, remapping `camunda.client.*` to `camunda.clients.default.*`, with a
  `defaultCamundaClient` `@Primary` bean plus a `camundaClient` alias for
  backward compatibility (camunda/camunda#57344).
- `storage/001-remove-numeric-key-from-identity-entity-filters.md` — drop the
  internal numeric `key` filter fields from `UserFilter`, `GroupFilter`,
  `TenantFilter`, and `MappingRuleFilter`; Identity entities are filtered by their
  business string IDs only, with `AuthorizationFilter.authorizationKey` and sort
  fields explicitly out of scope (camunda/camunda#41657).
- `security/002-tenant-access-provider-ownership-and-seam.md` — CSL `core` owns
  the concrete tenant-access provider and the `TenantOwnedEntity` contract; a
  uniform `TenantAccessProvider` seam across the read (search) and write
  (engine) paths, behavior-preserving, with the engine keeping its own
  anonymous/mt-off resolver rather than a decorator over the core provider
  (camunda-security-library#582).
- `orchestration-cluster/` — decisions spanning the broker, gateways, and
  auth/authz layer: JVM/JRE image strategy, physical-tenant request scoping,
  provider selection, authorization routing, gRPC authentication, configuration
  resolution, exporter assignment, and async context propagation. See
  [`orchestration-cluster/README.md`](orchestration-cluster/README.md).

