# Engine Authorization Checks

This document describes how authorization is enforced in the Zeebe engine, before any command is applied and before it mutates the primary storage (which is backed by RocksDB).
**Location:** `zeebe/engine/.../processing/identity/authorization/CslAuthorizationCheck.java` and `CslTenantCheck.java`, backed by `zeebe/engine/.../processing/identity/adapter/AuthorizationScopeStateAdapter.java` and `MembershipStateAdapter.java`.

## Overview

`CslAuthorizationCheck` and `CslTenantCheck` are thin engine-side gates: every command the stream processor handles passes through one of them, but the authorization decision itself is made by the Camunda Security Library (CSL) through its `AuthorizationCheckPort`. What the engine owns is:

1. the pre-check skip logic -- internal command, anonymous principal, authorizations disabled;
2. supplying CSL with RocksDB-backed data through two port implementations, `AuthorizationScopeStateAdapter` (`AuthorizationScopeRepositoryPort`) and `MembershipStateAdapter` (`MembershipPort`);
3. mapping CSL's rejection back to a Zeebe `Rejection` via `AuthorizationRejectionMapper`.

This is a **pre-execution check**: it may read existing state, but always runs before any state mutation or command application.

How CSL reaches its decision is deliberately not described here -- see the [CSL architecture documentation](https://github.com/camunda/camunda-security-library/blob/main/docs/architecture/05-building-block-view.md), sections 5.4 (hexagonal architecture) and 5.5 (engine authorization integration). Note that the order in which CSL evaluates a principal's grants is CSL-internal and is not documented in either repository.

For the same reason, the CSL classes behind those ports are not named on this page. Only the ports the engine implements and the types it constructs are -- see the [naming convention](../architecture.md#csl-extension-points-and-ocs-adapters) for which CSL types these docs name and why.

`CslTenantCheck#checkTenant` checks tenant assignment on its own, independently of the RBAC step. Engine command processors use it when a command needs a tenant-membership gate at a different granularity than its resource-permission check.

## Configuration

The check is controlled by two flags:

|                     Flag                      |           Effect when disabled           |
|-----------------------------------------------|------------------------------------------|
| `camunda.security.authorizations.enabled`     | All permission checks are skipped        |
| `camunda.security.multiTenancy.checksEnabled` | All tenant assignment checks are skipped |

## Caching

`authorizationsCacheTtl` and `authorizationsCacheCapacity` (`EngineConfiguration`) configure cache TTL and capacity, but this is no longer a single decision cache keyed by a check request. The cache now lives in the port implementations: `AuthorizationScopeStateAdapter` and `MembershipStateAdapter` each hold a Caffeine `LoadingCache` over the RocksDB-backed state they expose to CSL. There is no `clearAuthorizationsCache()` method; entries expire via TTL, but both adapters also call `invalidateAll()` from the corresponding create/update/delete processor (`AuthorizationCreateProcessor`, `AuthorizationUpdateProcessor`, `AuthorizationDeleteProcessor`, `RoleAddEntityProcessor`/`RoleRemoveEntityProcessor`, `GroupAddEntityProcessor`/`GroupRemoveEntityProcessor`, `TenantAddEntityProcessor`/`TenantRemoveEntityProcessor`), plus `TenantDeleteProcessor`, which invalidates both adapters, so an authorization, membership, or tenant change takes effect immediately rather than waiting out the TTL.

## Internal Commands

Some commands are triggered internally by the engine, for example follow-up commands from a process instance. `CslAuthorizationCheck`'s single-check entry points (`check`, `checkAuth`, `checkForDistributedCommand`) skip authorization for these automatically as part of their resolution logic; there is no separate `isAuthorizedOrInternalCommand()`-style method to call.

## Anonymous Users

If the request claims indicate an anonymous user (`AUTHORIZED_ANONYMOUS_USER`), authorization checks are skipped (see `CslTenantCheck#isAnonymousCommand`). This is used for operations that don't require authentication.

## Disjunctive (OR) Authorization

CSL's `RequiredAuthorization` expresses a single `(resourceType, permissionType)` pair per call, so "any of these checks passes" is composed locally where it is needed. The only place that happens is user task authorization: `UserTaskAuthorizationCheck` (`zeebe/engine/.../processing/usertask/processors/`) evaluates alternatives such as `PROCESS_DEFINITION.<permission>` or `USER_TASK.<permission>` -- by resource ID or by task property (assignee, candidate users, candidate groups) -- in order, and returns on the first match. Each alternative is still evaluated by CSL via `CslAuthorizationCheck#checkAuth`; `UserTaskAuthorizationCheck` only combines the outcomes. If all fail, it builds a single `FORBIDDEN` rejection itself, joining every failed alternative's reason with `"; and "`.

## Rejection Types

`AuthorizationRejectionMapper.toRejection` maps the CSL-originated rejections -- permission, tenant, and property -- to `RejectionType.FORBIDDEN` throughout. The `FORBIDDEN`/`NOT_FOUND` distinction for tenant mismatches has **not** been dropped; it moved to the **caller**, which passes the finished `Rejection` (including its `RejectionType`) into `CslTenantCheck#checkTenant` or `CslAuthorizationCheck#checkAuthorizationAndTenant`. The convention:

- **`FORBIDDEN`** -- the principal lacks the permission, or is not assigned to the tenant of a resource it is creating.
- **`NOT_FOUND`** -- masks the existence of an already-existing resource, looked up by key, that lives in a tenant the caller cannot access. This is what keeps cross-tenant existence from leaking; `ProcessInstanceCancelProcessor` is a representative caller, and `AuthorizationRequest#getTenantErrorMessage` selects the message by the same rule.

When both a permission and a tenant check would fail on the same command, `checkAuthorizationAndTenant` lets the permission rejection win, so a principal with no permission at all never sees a tenant-shaped rejection that would hint at the resource's existence.
