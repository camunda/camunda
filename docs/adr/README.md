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
- `clients/0001-unify-spring-starter-on-multi-client-config-path.md` — collapse
  the `camunda-spring-boot-starter` onto a single (multi-client) auto-config
  path, remapping `camunda.client.*` to `camunda.clients.default.*`, with a
  `defaultCamundaClient` `@Primary` bean plus a `camundaClient` alias for
  backward compatibility (camunda/camunda#57344).
- `gradle/001-gradle-experimental-ci-integration.md` — how the experimental
  Gradle build is exercised in CI while requiring Maven tests for Java and Maven
  changes, with a non-blocking PR-only Gradle parity job and deferred nightly
  and automated-repair workflows.

