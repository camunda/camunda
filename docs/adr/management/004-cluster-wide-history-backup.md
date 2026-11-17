# Cluster-wide history backup endpoints report per-tenant outcomes

**DRI**: Houssain Barouni

**Status**: Proposed (8.10)

**Purpose**: Define the response and status-code contract of the cluster-wide history backup endpoints under `/cluster/v2`, and why it does not follow the shape of the `backupHistory` actuator.

**Audience**: Engineers working on the management API and the backup surface; operators of multi-physical-tenant clusters.

## Context

[ADR 003](./003-physical-tenant-management-endpoint-inventory.md) D2 lists four cluster-wide history backup endpoints under `/cluster/v2`, and D4 fixes their semantics: a cluster-wide backup is a set of independent per-tenant backups sharing one caller-supplied `backupId`. What it leaves open is how a fan-out over N tenants that only partly succeeds is reported — which status code, which body, and what counts as a failure at all.

The obvious template is the `backupHistory` actuator, which already fans out over every tenant. It is the wrong one. The actuator folds every tenant's state into one top-level `state` through a precedence table, because ADR 003 commits to keeping its schemas backwards compatible for clients written against single-tenant clusters. `/cluster/v2` is new in 8.10 and has no installed clients.

The contract below therefore reports what happened on each tenant: per-tenant outcomes, no aggregate, absence treated as a normal outcome rather than an error, and a request that cannot be served on every targeted tenant failing as a whole.

## Decision

**D1. The cluster contract follows the per-physical-tenant `/v2` endpoints, not the actuator.**

The cluster endpoints inherit the actuator's *behaviour* — one caller-supplied id, independent per-tenant backups, per ADR 003 D4 — but take their schemas, status ladder and query parameters from `/v2/backups/history`, adding only what a fan-out needs. The actuator's shape follows from a backwards-compatibility constraint that does not apply to a surface new in 8.10 with no installed clients.

**D2. A tenant that does not hold a backup was observed successfully, not failed.**

A backup existing on one physical tenant and not another is normal: D5 lets a cluster admin take a single-tenant backup, and the per-PT endpoints let a tenant operator do the same. So the contract separates *absence* — the tenant was reached and holds nothing — from *failure to observe*, where its state is unknown. Absence is an ordinary per-tenant state inside a successful response; only failure to observe makes the request fail.

**D3. Responses carry per-tenant outcomes and no aggregated cluster-level state.**

Given D2, "is this cluster backup complete?" has no answer: a backup taken on one tenant on purpose is neither complete nor failed cluster-wide. An aggregate would need a precedence rule with nothing to justify it, and any folded value can hide the tenant an operator needed to see. Callers wanting one verdict apply their own rule to the list.

**D4. A cluster-wide operation serves every targeted tenant or fails as a whole; a partial result is never reported as success.**

If any targeted tenant cannot be observed, or refuses the requested backup id, the request fails with a `ProblemDetail` instead of returning a per-tenant mix under a success code, which a caller who asked for a cluster-wide backup would have to inspect to discover the gap. Where the fan-out mutates state it validates every targeted tenant before acting on any of them, but it is not transactional: a failure during the fan-out itself leaves the tenants already reached changed. Narrowing (D5) is what turns "one tenant is broken" from a cluster-wide outage into a per-tenant one.

**D5. All four endpoints accept `?physicalTenantId=` to narrow the fan-out to one tenant.**

Same parameter and same absent-or-blank handling as the cluster recovery endpoints. It is not redundant with the per-PT endpoints: the cluster-admin credential is verified against an isolated, parent-less user store bound to `/cluster/v2/**`, so under [ADR 002](./002-management-endpoint-authorization.md) a cluster admin has no route to `/physical-tenants/{id}/v2/...` at all. It is also the only way to reach the healthy tenants once D4 makes one unobservable tenant fail every cluster-wide call.

## Consequences

- No single field carries a cluster-level backup status: a caller wanting one verdict has to define its own rule over the per-tenant states.
- One unobservable tenant fails every cluster-wide call for as long as it lasts; `?physicalTenantId=` is the only way to keep working with the rest.
- A failed mutation can still leave the tenants it already reached changed: a `POST` that fails during the dispatch leaves snapshots behind, so the id has to be deleted before the call can be retried, and a failed `DELETE` leaves the backup gone from the tenants it reached.
- `DELETE` answers `404` when no targeted tenant holds the id, matching `GET` and the per-PT endpoint; a tenant that merely does not hold it is still a success, per D2.
- The per-PT `GET`/`DELETE /v2/backups/history/{backupId}` answer 400 instead of 404 for a snapshot repository absent from the store. Under D2 the existing conflation would report an unreachable tenant as ordinary absence, and under D4 that turns a failed request into a silent success. Both are pre-GA on 8.10-alpha. The cluster endpoints answer 400 for the same condition, per D1 — it is the likeliest way for a tenant to become unobservable, so the two surfaces must not disagree on it.
- ADR 003 D2's history-backup rows are superseded on acceptance: the list filter is `?prefix=&verbose=`, matching the per-PT endpoint that shipped after 003 was written, and all four rows gain `?physicalTenantId=`.
- Schemas are shared with `/v2` — backup id, prefix, snapshot detail — so those shapes cannot drift. The query parameters are not, because the per-PT endpoints declare theirs inline.

## Alternatives considered

- **Report a mixed outcome as `207 Multi-Status` carrying the same body as success.** Rejected: a partial cluster backup is not what the caller asked for, and a success code invites them not to look.
- **Fold a cluster-level state out of the per-tenant states, as the actuator does.** Rejected: once single-tenant backups are legitimate, the precedence rule has nothing to justify it, and any folded value can mask a tenant. The actuator carries one only because installed clients read that field.
- **Treat a tenant that holds no backup as a failure.** Rejected: under D4 that would fail every request touching a legitimately single-tenant backup, including the deletion of one.

## Source

- [camunda/camunda#57738](https://github.com/camunda/camunda/issues/57738) — Cluster-wide history backup REST endpoints

