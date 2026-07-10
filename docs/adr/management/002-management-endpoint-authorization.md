# Authorization model for management endpoints in multi-physical-tenant clusters

**DRI**: Lena Schönburg

**Status**: Draft (8.10)

**Purpose**: Fix the authentication/authorization model for the three management surfaces of a multi-physical-tenant cluster:
- the actuator endpoints
- the per-PT management REST endpoints
- the new cluster-wide `/cluster/v2/` REST endpoints.

**Audience**: Engineers working on the distribution or the management API; operators securing multi-tenant clusters.

## Context

Three management surfaces exist or are being added:

1. **Actuator endpoints**. Today they have *no* authentication at all, access control is purely network-level reachability of the management port (usually 9600).
2. **Per-tenant management endpoints** Already has per-PT security chains, backed by the tenant's secondary storage.
3. **Cluster-wide management endpoints**. Protected by a dedicated **cluster-admin** security chain.

| Endpoint                          | Authentication                               | Authorization                      | Tenant scope                                             |
|-----------------------------------|----------------------------------------------|------------------------------------|----------------------------------------------------------|
| Actuator endpoints                | None                                         | None                               | All PTs; optional `?physicalTenant=` narrowing (ADR 003) |
| Per-tenant management endpoints   | The tenant's existing security chain         | Via PT's secondary storage         | Exactly one PT                                           |
| Cluster-wide management endpoints | OIDC: any cluster-trusted IdP, or Basic auth | OIDC: token claim, Basic: username | All PTs                                                  |

## Decision

**D1. The actuator endpoints stay unauthenticated and cluster-scoped.**

Actuators remain the operator escape hatch: reachable only via the management port, no credentials, full access to every tenant.
Operators of multi-tenant clusters must treat management-port access as cluster-admin-equivalent.

**D2. Per-PT management endpoints use the per-PT authentication and authorization.**

New per-PT endpoints (backup, exporting control) are ordinary PT-prefixed v2 endpoints: authenticated by that tenant's security chain and authorized against that tenant's authorization data.
Because these endpoints are not implemented via engine commands, the authorization is performed against the tenant's authorization data in secondary storage, as if this was a search query.

Some endpoints might require new authorization resources, for example `BACKUP`.

**D3. Cluster-wide REST endpoints require the pre-configured cluster-admin**

`/cluster/v2/...` operations are protected by the dedicated cluster-admin chain from #54898 (config-defined Basic users, or OIDC `client_credentials`).
No authorization for individual physical tenants is performed.

Exception per ADR 001: `GET /cluster/v2/status` is unauthenticated to allow operational monitoring. No sensitive information is exposed.

**D4. No fine-grained cluster-management authorization.**

We deliberately do not introduce per-operation management permissions (e.g. "may trigger backup but not purge") on the cluster-wide endpoints.
Per-PT management endpoints require and enforce fine-grained authorizations and can be used instead.

## Consequences

- Tenants without usable secondary storage must be managed via the cluster-wide or actuator surface only.
  - Per-PT management endpoint authorization requires the tenant's secondary storage. When a tenant has none, or it is unavailable, its per-PT management endpoints cannot authorize requests and fail.
  - Cluster-wide management endpoints or actuator endpoints must be used instead
- Cluster-wide management endpoints do not support fine-grained authorization.
  - Cluster admins are allowed to use all cluster-wide management endpoints or actuator endpoints, across all physical tenants.

## Alternatives considered

- **Authenticate the actuator surface.** Breaks every existing probe/tooling integration on port 9600, and K8s probes must remain credential-free anyway.
- **Fine-grained per-operation permissions for cluster operations.** Risks that cluster-wide operations are not possible when the cluster is degraded, difficult to configure for operators.
