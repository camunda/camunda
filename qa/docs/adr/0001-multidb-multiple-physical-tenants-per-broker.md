# MultiDb integration tests provision multiple physical tenants per broker

**DRI**: Sebastian Bathke

**Status**: Proposed

**Purpose**: Defines how the `CamundaMultiDBExtension` test framework provisions more than one
physical tenant within a single broker, so cross-physical-tenant isolation can be asserted by
`@MultiDbTest` classes instead of a bespoke parallel harness.

**Audience**: Engineers and AI agents writing physical-tenant integration tests or maintaining the
MultiDb test framework (`qa/util`).

## Context

The physical-tenant (PT) authorization ITs (`MultiPhysicalTenantAuthorizationIT`,
`PhysicalTenantAuthorizationEnablementIT`, `PhysicalTenantLogicalTenantScopingIT`) assert
*cross-tenant* isolation: an identity or grant created in one PT must not resolve in another, and
authorization enablement is per-PT. Expressing this needs **two or more non-default physical
tenants live in one broker at once**, each backed by its own isolated secondary storage.

Today these ITs run on a bespoke helper (`PhysicalTenantsITHelper`) that boots one `static`
`TestStandaloneBroker` over `@ZeebeIntegration` and stamps per-PT RDBMS-H2 storage by hand. A
recent fix ([#56006](https://github.com/camunda/camunda/issues/56006)) had to re-implement, in that
helper, fresh-database-per-run isolation to stop in-memory H2 leaking across failsafe reruns in the
same JVM — a property the maintained `CamundaMultiDBExtension` already provides for free.

`CamundaMultiDBExtension` already supports **exactly one** physical tenant per class
(`@MultiDbTest(physicalTenantId = …)` or `-Dtest.integration.camunda.physical-tenant`). Its
`configurePhysicalTenant()` mints a single fresh
`jdbc:h2:mem:<pt>-<uuid>;DB_CLOSE_DELAY=-1;MODE=PostgreSQL` URL,
applies it via `withPtConfig`, copies the root `security.initialization` into that PT, scopes the
single injected `CamundaClient` to it, and guards that PT-mode is **RDBMS-only**. It does not model
N PTs, nor inject a client per PT, nor seed a distinct admin identity per PT — and its single-client
entity provisioning (`EntityManager.await` against one schema) does not generalize to N schemas.

This is the same single-vs-many extension that [#55768](https://github.com/camunda/camunda/pull/55768)
does *not* cover: that PR runs the existing acceptance suite under one custom PT; this decision is
about hosting several PTs in one broker for cross-tenant assertions.

ES/OS cannot host non-default PTs until per-PT secondary-storage schema init
([#51996](https://github.com/camunda/camunda/issues/51996)) and the per-PT writer
([#51736](https://github.com/camunda/camunda/issues/51736)) land
([epic #51949](https://github.com/camunda/camunda/issues/51949)); the existing RDBMS-only guard
already enforces this.

## Decision

**D1. The MultiDb framework provisions N physical tenants per broker via an additive, class-level
declaration.** A new annotation `@MultiDbPhysicalTenants({"tenanta", "tenantb"})` lists the
non-default PTs; the `default` PT is always implicit (it is the broker's root config). The existing
single-PT path (`physicalTenantId` attribute / system property) is unchanged and is treated as the
one-element case of the same mechanism. Classes with neither annotation are entirely unaffected.

**D2. `configurePhysicalTenant()` is generalized from one PT to a loop, each PT given its own
isolated storage namespace — single-PT becomes the one-element case of that loop, so there is one
provisioning path, not two.** On most dialects each PT is isolated by a dedicated database schema (a
separate namespace inside the matrix-provided database server) rather than by a table prefix in a
shared schema. A schema per PT mirrors a schema-isolated multi-PT deployment more closely, and
each PT exercises its own `DataSource` exactly as production wires one `DataSource` per PT. The
extension creates the namespace before broker start and points the PT's connection at it; the
concrete primitive is dialect-specific, since "schema" has no single cross-dialect spelling:

- **H2** (`RDBMS_H2`): a dedicated in-memory database per PT
  (`jdbc:h2:mem:<run>-<ptId>-<uuid>;DB_CLOSE_DELAY=-1;MODE=PostgreSQL`).
- **PostgreSQL / Aurora**: a `CREATE SCHEMA` in the matrix-provided database; the PT connection
  targets it via `currentSchema`.
- **MySQL / MariaDB**: schema is synonymous with database, so a `CREATE DATABASE`; the PT URL
  targets that database.
- **SQL Server (MSSQL)**: a dedicated database per PT (`CREATE DATABASE`), since MSSQL has no
  connection-URL schema selector; the PT URL targets it via `databaseName`.
- **Oracle**: a dedicated **user** per PT (`CREATE USER`), which on Oracle *is* a dedicated schema;
  the PT connects as that user with no table prefix, exactly as production isolates Oracle PTs by
  schema-per-user. This requires a privileged (DBA-grade) base connection for the `CREATE USER` /
  `DROP USER … CASCADE` bootstrap and an Oracle CI matrix grant. The PT's `database-vendor-id` is
  pinned to `oracle` so the production `SecondaryStorageIsolationValidation` keys the storage
  location on the connecting user and recognizes distinct Oracle users on the same JDBC URL as
  distinct locations ([#56402](https://github.com/camunda/camunda/issues/56402)).

  > Historical note: Oracle was originally the exception, isolated by a per-PT **table prefix** in
  > the shared `camunda` schema, because the validator keyed a location on
  > `(type, connection-url, table-prefix)` and deliberately ignored the connection user — so two
  > Oracle users on the same URL collapsed to the *same* location and the broker refused to start.
  > [#56402](https://github.com/camunda/camunda/issues/56402) taught the validator to key on the
  > Oracle user (when `database-vendor-id: oracle`), removing that limitation and letting the harness
  > use schema-per-user like every other dialect.

The namespace name embeds a run-unique token (the same token the framework already uses for
parallel-test isolation) plus the PT id, so concurrent CI runs never collide. RDBMS schema auto-DDL
then creates each PT's tables inside its own namespace on broker startup. This builds on the
production per-physical-tenant RDBMS support — a per-PT `DataSource` built from a per-PT
URL/credentials block and an independent Liquibase migration per PT
([#52043](https://github.com/camunda/camunda/issues/52043), shipped) — so multi-PT works on every
supported RDBMS dialect, not just H2.

Fresh-storage-per-run isolation — the property [#56006](https://github.com/camunda/camunda/issues/56006)
added by hand — thus holds for every PT for free (a fresh in-memory DB on H2, a fresh per-run
schema/database on the other dialects, a fresh per-run user/schema on Oracle).

Constraint: the namespace name (`<run>_<ptId>`) must stay within the dialect's identifier-length
limit — the binding constraint being Oracle's 30-character identifier cap (far stricter than, e.g.,
MySQL/MariaDB's 64-character database name); the run token and PT id are kept short accordingly
(a 10-character run token plus a separator leaves a 19-character cap on the PT id).

**D3. Each PT is seeded with its own admin identity.** The extension seeds a `<ptId>-admin` user and
the `admin` default-role into *that PT's* `security.initialization`, folding in what
`PhysicalTenantsITHelper.seedBasicAuthAdminUser` + `configureAdminRoles` do today. Per-PT
`security.initialization` ownership is already required by the resolver
(`PhysicalTenantRequiredOverrideValidation`), so this matches the single-PT path's existing copy of
root init into the PT.

**D4. In multi-PT mode the single-client entity provisioning is skipped; a per-PT client accessor is
injected instead.** There is no single "the" schema to provision `@UserDefinition`/`@TenantDefinition`
entities against, so the `EntityManager.await` single-admin-client provisioning is bypassed when
`@MultiDbPhysicalTenants` is present. The extension instead injects a small accessor (e.g.
`MultiPhysicalTenantContext`) exposing `adminClient(ptId)`, `newClientBuilder(ptId)`, and
`awaitAdminReady(ptId)` — the surviving useful surface of `PhysicalTenantsITHelper`, now
framework-provided. Tests create their restricted users and grants at runtime through the per-PT
admin client, exactly as they already do.

**D5. Per-PT config overrides beyond storage/admin stay with the test, via the broker it already
owns.** The enablement IT needs `authorizations.enabled=false` on one PT. No new API is added for
this: the test owns the `@MultiDbTestApplication` broker and calls
`BROKER.withPtConfig(TENANT_OFF, c -> c.getSecurity().getAuthorizations().setEnabled(false))`.
`withPtConfig` merges into the same
per-PT `Camunda` via `computeIfAbsent`, so the test override composes with the extension's
storage/admin stamping regardless of order.

**D6. Multi-PT is RDBMS-only (all dialects); ES/OS are skipped, not errored.** The extension throws
if a PT is requested on non-RDBMS storage. The three converted ITs are gated (JUnit
`@EnabledIfSystemProperty` on `test.integration.camunda.database.type`, matching `rdbms.*$` — the
lowercase value the matrix supplies) so they
run across the RDBMS matrix and skip cleanly on ES/OS. ES/OS cannot host non-default PTs until the
per-PT writer / schema-init land
([#51736](https://github.com/camunda/camunda/issues/51736) /
[#51996](https://github.com/camunda/camunda/issues/51996)); adding ES/OS cross-tenant coverage once
they do is the only follow-up.

## Alternatives considered

- **First-class multi-PT entity model (Option B).** Thread a `physicalTenant` attribute through
  `@UserDefinition`, `@TenantDefinition`, grant definitions, etc., so the entity machinery
  provisions each entity into the right PT schema and a PT-aware client registry is injected.
  Rejected for now (YAGNI/KISS): the three ITs create their users and grants at runtime via the
  admin client and need none of this; it would rework `TestEntityConfigurer`/`EntityManager` deeply
  for no current consumer. D1–D4 leave this open as a later, separately-motivated step.

- **Keep the bespoke `PhysicalTenantsITHelper`.** Rejected: it is a parallel harness that
  re-implements isolation the maintained framework already guarantees (the #56006 fix is exactly
  such hand-rolled isolation), and it does not run across the supported database matrix.

- **A separate extension dedicated to multi-PT.** Rejected: it would duplicate the bootstrap,
  storage wiring, client factories, and readiness handling already in `CamundaMultiDBExtension`.
  Generalizing the existing single-PT path (D2) is strictly smaller and keeps one code path.

## Consequences

- The three PT authorization ITs move to `qa/acceptance-tests`, run via `@MultiDbTest` +
  `@MultiDbPhysicalTenants`, and the bespoke `PhysicalTenantsITHelper` setup is retired (or reduced
  to what the framework cannot yet express). They gain fresh-DB-per-run isolation from the framework,
  removing the rerun-state-leak class of flakiness #56006 patched by hand.
- `CamundaMultiDBExtension` (shared `qa/util`) gains a multi-PT branch. The single-PT and no-PT
  paths used by the rest of the acceptance matrix are unchanged — the new behavior is gated on the
  new annotation.
- Cross-tenant isolation assertions run across the supported RDBMS matrix (H2 via per-PT in-memory
  databases, shared RDBMS via per-PT schemas). Adding ES/OS coverage (gated on
  [#51736](https://github.com/camunda/camunda/issues/51736) /
  [#51996](https://github.com/camunda/camunda/issues/51996)) is the only follow-up; the RDBMS gate
  (D6) keeps the matrix from running multi-PT on ES/OS in the meantime.

## Source

- [Issue #56116 — Convert PT authorization ITs to MultiDbTest and extend MultiDb for multi-PT-per-broker isolation](https://github.com/camunda/camunda/issues/56116)
- [PR #55831 comment — move PT tests to `@MultiDbTest`](https://github.com/camunda/camunda/pull/55831#issuecomment-4797136899)
- [Issue #56006 — hand-rolled per-PT H2 isolation this supersedes](https://github.com/camunda/camunda/issues/56006)
- [Epic #51949 — per-PT secondary storage (ES/OS prerequisite)](https://github.com/camunda/camunda/issues/51949)

