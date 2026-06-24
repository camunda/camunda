# Engine Authorization Checks

This document describes how authorization is enforced in the Zeebe engine, before any command is applied and before it mutates the primary storage (which is backed by RocksDB).
**Location:** `zeebe/engine/.../processing/identity/authorization/AuthorizationCheckBehavior.java`

## Overview

The `AuthorizationCheckBehavior` is the engine-side authorization gate. Every command that the stream processor handles passes through this behavior to determine whether the caller has permission to perform the requested action. If the check fails, the command is rejected before any state mutation or command application is performed, even though the behavior may read from RocksDB-backed state to make its decision.
This is a **pre-execution check**: it may read existing state but always runs before any state mutation or command application occurs.

## Configuration

The behavior is controlled by two flags:

|                     Flag                      |           Effect when disabled           |
|-----------------------------------------------|------------------------------------------|
| `camunda.security.authorizations.enabled`     | All permission checks are skipped        |
| `camunda.security.multiTenancy.checksEnabled` | All tenant assignment checks are skipped |

When **both** are disabled, `shouldSkipAllChecks()` returns `true` and no authorization work is performed at all. Callers can use this method to avoid constructing `AuthorizationRequest` objects when the result would always be authorized.

## Authorization Flow

When `isAuthorized(request)` is called, the check proceeds through a three-step cascade. Each step can grant full access, short-circuiting the remaining steps.

```
┌──────────────────────────────────────────┐
│            isAuthorized(request)          │
├──────────────────────────────────────────┤
│ Skip all checks?  ──yes──►  AUTHORIZED   │
│       │ no                               │
│       ▼                                  │
│ Step 1: Check primary entity             │
│   (user or client)                       │
│   ├─ resource access (permission check)  │
│   └─ tenant access (tenant assignment)   │
│       │                                  │
│  Both pass? ──yes──►  AUTHORIZED         │
│       │ no                               │
│       ▼                                  │
│ Step 2: Check mapping rules              │
│   (supplements primary entity)           │
│   ├─ tenant access (if still missing)    │
│   └─ resource access (if tenant passes)  │
│       │                                  │
│  Both pass? ──yes──►  AUTHORIZED         │
│       │ no                               │
│       ▼                                  │
│ Step 3: Property-based authorization     │
│   (only if tenant access granted and     │
│    request has resource properties)      │
│   ├─ match properties via evaluator      │
│   └─ check authorized scopes match       │
│       │                                  │
│  Match? ──yes──►  AUTHORIZED             │
│       │ no                               │
│       ▼                                  │
│     REJECTED (aggregated reasons)        │
└──────────────────────────────────────────┘
```

### Step 1: Primary Entity Check

The system extracts the authenticated identity from the request claims:
- If a **client ID** is present, it checks authorization for entity type `CLIENT`.
- Otherwise, if a **username** is present, it checks authorization for entity type `USER`.

For the identified entity, two checks run:
1. **Resource access** -- does this entity have the required permission on the requested resource? This is resolved through `AuthorizationScopeResolver`, which looks up granted scopes (wildcard, specific IDs, or properties) for the entity, including scopes inherited via roles and groups.
2. **Tenant access** -- is this entity assigned to the tenant that owns the resource? Only checked when multi-tenancy is enabled and the resource is tenant-owned.

If both pass, the request is authorized immediately.

### Step 2: Mapping Rule Check

If the primary entity check didn't grant full access, the system looks up **mapping rules** that match the request's token claims. Mapping rules allow authorization to be granted based on external identity attributes (e.g. from an IdP).

This step supplements the primary result:
- If tenant access is still missing, it checks tenant assignment via matching mapping rules.
- If tenant access is now granted but resource access is still missing, it checks resource permissions via the mapping rules.

### Step 3: Property-Based Authorization

As a final fallback, if the request carries resource properties (e.g. user task properties like assignee and candidate information) and the caller already has tenant access, the system checks whether the authenticated user matches any of the resource's properties.

This uses the `PropertyAuthorizationEvaluatorRegistry`, which dispatches to type-specific evaluators. Currently the only evaluator is `UserTaskPropertyAuthorizationEvaluator`, which checks:
- Is the user the **assignee** of the task?
- Is the user in the **candidate users** list?
- Does the user belong to a **candidate group**?

The matched properties are then cross-checked against the entity's authorized scopes with `AuthorizationResourceMatcher.PROPERTY` to confirm that a property-based authorization grant exists.

## Scope Resolution

The `AuthorizationScopeResolver` is responsible for looking up what scopes an entity has been granted for a given resource type and permission type. A scope can be:

- **Wildcard** -- access to all resources of the type
- **ID-based** -- access to a specific resource by ID
- **Property-based** -- access via resource property matching (e.g. task assignee)

When checking ID-based authorization, the logic is:
1. If any granted scope is `WILDCARD`, access is granted.
2. If the request has no specific resource IDs (meaning it requires wildcard), access is denied (wildcard was already checked).
3. Otherwise, check if any granted ID scope matches any requested resource ID.

## Caching

Authorization results are cached using a Guava `LoadingCache` keyed by `AuthorizationRequest`. The cache has configurable TTL and capacity via `EngineConfiguration`:
- `authorizationsCacheTtl` -- how long results are cached
- `authorizationsCacheCapacity` -- maximum number of cached entries

The cache can be invalidated via `clearAuthorizationsCache()`.

## Internal Commands

Some commands are triggered internally by the engine (e.g. follow-up commands from a process instance). These bypass authorization entirely via `isAuthorizedOrInternalCommand()` / `isAnyAuthorizedOrInternalCommand()`, which check `request.isTriggeredByInternalCommand()` before evaluating permissions.

## Disjunctive (OR) Authorization

The `isAnyAuthorized(requests...)` method supports checking multiple authorization requests where **any one** passing is sufficient. This is used when an action can be authorized through different resource type / permission type combinations. If all requests fail, the rejections are aggregated into a composite rejection message.

## Rejection Types

When authorization fails, the rejection type depends on what failed:
- **`FORBIDDEN`** -- the user lacks the required permission
- **`NOT_FOUND`** -- the user is not assigned to the required tenant (for existing resources). This prevents information leakage by not revealing whether a resource exists in a tenant the user doesn't have access to.

For new resources, tenant check failures use `FORBIDDEN` instead of `NOT_FOUND` since there is no pre-existing resource to hide.

## Anonymous Users

If the request claims indicate an anonymous user (`AUTHORIZED_ANONYMOUS_USER`), all authorization checks are skipped. This is used for operations that don't require authentication.
