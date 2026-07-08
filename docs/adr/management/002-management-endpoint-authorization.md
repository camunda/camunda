# Authorization model for management endpoints in multi-physical-tenant clusters

**DRI**: Lena Schönburg (Zeebe Distributed Platform)

**Status**: Draft (8.10)

**Purpose**: Fix the authentication/authorization model for the three
management surfaces of a multi-physical-tenant cluster: the actuator
endpoints, the per-tenant management REST endpoints, and the new
cluster-wide `/cluster/v2` REST endpoints.

**Audience**: Engineers working on the distribution, gateway REST, and
Identity; operators securing multi-tenant clusters.

Relates to: camunda/camunda#54898 (Identity Slice 4 — cluster admin
support), docs/adr/security/001 (endpoint → required-permission mapping),
ADR 001 (health/status), ADR 003 (endpoint inventory), kickoff doc
"Decisions taken → Backup endpoints in Rest".

## Context

Three management surfaces exist or are being added:

1. **Actuator endpoints** (management server, port 9600). Today they have
   *no* authentication at all: Spring Security auto-configuration is
   explicitly excluded for the management context
   ([`dist/src/main/resources/application.properties:81-83`](https://github.com/camunda/camunda/blob/ddd2c002bdb38d8963c5eaa5162bbcad8b0d57b8/dist/src/main/resources/application.properties#L81-L83)), and
   `/actuator/**` is in the unprotected path list
   ([`authentication/.../spi/SecurityPathAdapter.java:43`](https://github.com/camunda/camunda/blob/7f03a1cbce653a56c7734c01a86f8dc8e9dd9bbe/authentication/src/main/java/io/camunda/authentication/config/spi/SecurityPathAdapter.java#L43)). Access control
   is purely network-level reachability of port 9600.
2. **Per-tenant management REST endpoints** (new in 8.10: backup, exporting
   control — see ADR 003) under `/physical-tenants/{id}/v2/...` on the
   application port. The PT-prefixed REST surface already has per-tenant
   security chains (`PhysicalTenantHandlerFactory`,
   `PhysicalTenantFilter` at order -101 ahead of Spring Security), backed
   by the tenant's secondary storage (users, authorizations).
3. **Cluster-wide REST endpoints** (new in 8.10: `/cluster/v2/...`). These
   act across all tenants, so no single tenant's authorization data can
   govern them. Identity Slice 4 (camunda/camunda#54898) delivers a
   dedicated **cluster-admin** security chain for exactly this: Basic auth
   with users defined statically in configuration, or OIDC restricted to
   the `client_credentials` grant.

The kickoff decision record states: cluster-wide backup/restore operations
are "authenticated via the default mandatory IdP with cluster-admin
authorization before the request reaches the controller; downstream is
treated as pre-authorized."

A note on tenant selection mechanics, since it interacts with security:
per-tenant selection on the *authorized* REST surface must be part of the
URL path (`/physical-tenants/{id}/...`) because the security filter chains
dispatch on the path. Query parameters are not a safe selector there. On
the *actuator* surface no authorization exists, so a
`?physicalTenant=` query parameter is acceptable and is what ADR 003 uses.

## Decision

**D1. Three tiers, one rule each.**

|                         Surface                          |               Authentication                |                    Authorization                     |                       Tenant scope                       |
|----------------------------------------------------------|---------------------------------------------|------------------------------------------------------|----------------------------------------------------------|
| Actuator (port 9600)                                     | None (status quo, reaffirmed)               | None — full access to all tenants                    | All PTs; optional `?physicalTenant=` narrowing (ADR 003) |
| Per-PT management REST (`/physical-tenants/{id}/v2/...`) | The tenant's existing security chain        | The tenant's authorization model (secondary storage) | Exactly one PT                                           |
| Cluster-wide REST (`/cluster/v2/...`)                    | Pre-configured cluster-admin chain (#54898) | Cluster-admin = allowed; downstream pre-authorized   | All PTs                                                  |

**D2. The actuator surface stays unauthenticated and cluster-scoped.**

Actuators remain the operator escape hatch: reachable only via the
management port, no credentials, full access to every tenant. This is a
deliberate security-posture statement, not an omission — the trust boundary
is network access to port 9600, exactly as today. Operators of multi-tenant
clusters must treat management-port access as cluster-admin-equivalent
(documentation consequence). Nothing in 8.10 narrows this; per-tenant
actuator restrictions would require an authenticated management context and
are out of scope.

**D3. Per-PT management REST endpoints reuse the tenant's own chain — no
new authorization concept.**

The new per-tenant endpoints (backup, exporting control) are ordinary
PT-prefixed v2 endpoints: authenticated by that tenant's security chain and
authorized against that tenant's authorization data. Each operation
declares its required permission via `x-required-permissions`
(docs/adr/security/001); the concrete resource-type/permission pairs are
fixed in the OpenAPI review per ADR 003. No management-specific permission
scheme is introduced in 8.10 — a tenant principal with the declared
permissions can manage *its* tenant, nothing else.

**D4. Cluster-wide REST endpoints require the pre-configured
cluster-admin; fan-out is pre-authorized.**

`/cluster/v2/...` operations are protected by the dedicated cluster-admin
chain from #54898 (config-defined Basic users, or OIDC
`client_credentials`). Once the chain has admitted the request, the
implementation fans out to per-tenant internals *without* re-checking
per-tenant permissions — cluster-admin strictly dominates per-tenant
management rights. This avoids the failure mode where a cluster backup
partially succeeds because the admin lacked a permission in one tenant's
authorization store (which may not even be reachable — see D5).

Exception per ADR 001 D7: `GET /cluster/v2/status` is unauthenticated,
mirroring `/v2/status` as a monitoring signal.

**D5. Tenants without usable secondary storage are managed via the
cluster-wide or actuator surface only.**

Per-PT REST authorization requires the tenant's secondary storage. When a
tenant has none (or it is degraded — ADR 001 D2), its per-PT management
endpoints cannot authenticate anyone and return 503 with a problem detail
pointing to the cluster-wide alternative. Cluster-admin (config-defined,
storage-independent) and the actuator surface remain fully functional for
such tenants by construction.

**D6. No fine-grained management RBAC in 8.10.**

We deliberately do not introduce per-operation management permissions
(e.g. "may trigger backup but not purge") on the cluster-admin tier.
Cluster-admin is all-or-nothing. Finer roles can be layered onto the same
chain later without breaking the API surface.

## Consequences

- **Hard dependency on #54898** (Identity Slice 4, state Define, target
  8.10.0-alpha4). The `/cluster/v2` write endpoints cannot ship *enabled*
  before the cluster-admin chain exists. Mitigation if it slips: the
  actuator surface (D2) already provides every operation cluster-wide, so
  functionality is not blocked — only the authorized REST facade is.
  Sequencing: build controllers against the chain's contract; wire the
  chain when Slice 4 lands.
- Documentation must state plainly: management-port access =
  cluster-admin-equivalent; secure port 9600 accordingly (network policy),
  and how to configure cluster-admin credentials.
- The OpenAPI specs for `/cluster/v2` need a security scheme for the
  cluster-admin chain; per-PT management endpoints need
  `x-required-permissions` entries (gap guard from security/001 enforces
  this at lint time).
- Per-tenant principals get self-service management of their own tenant
  (backup, exporting) without any operator involvement — new capability.
- D5 gives degraded tenants a defined, documented behavior instead of
  undefined 401/500s.

## Alternatives considered

- **Authenticate the actuator surface.** Rejected for 8.10: breaks every
  existing probe/tooling integration on port 9600, and the K8s probes
  (ADR 001) must remain credential-free anyway. Revisit only with a
  dedicated epic.
- **Fine-grained per-operation permissions for cluster operations.**
  Rejected (meeting decision 2026-07-06): requires new resource types,
  permission seeding, and configuration for marginal benefit; cluster
  operators hold full power today via actuators regardless.
- **Authorize cluster-wide operations against every tenant's authorization
  store (intersection/union).** Rejected: unreachable or degraded tenant
  stores would make cluster operations fail exactly when they are needed
  most (disaster recovery), and semantics (all? any?) are arbitrary.
- **Tenant selection via query parameter on the authorized REST surface.**
  Rejected: the per-tenant security chains dispatch on the URL path; a
  query-parameter selector would bypass chain selection and re-introduce
  confused-deputy risks. (Fine on the unauthorized actuator surface.)

