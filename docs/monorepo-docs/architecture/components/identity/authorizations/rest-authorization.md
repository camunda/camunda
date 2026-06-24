# REST Layer Authorization Checks

This document describes how authorization is enforced in the REST API layer, for requests that query secondary storage (Elasticsearch/OpenSearch) and for post-filtering of results.

**Location:** `security/security-services/.../security/impl/AuthorizationChecker.java`

## Overview

The `AuthorizationChecker` operates in the REST/search layer, after data has been written to secondary storage. While the [engine authorization checks](engine-authorization.md) gate command processing against RocksDB, this checker is responsible for:

1. **Filtering search results** -- ensuring users only see resources they are authorized to access.
2. **Checking write permissions** -- verifying a user can perform an action on a specific resource before the REST layer forwards it to the engine.
3. **Collecting permissions** -- determining what permission types a user has on a specific resource (used for UI feature toggling).

This is a **query-time / pre-forwarding check** that reads authorization data from the search index.

## Data Source

Unlike the engine checks which read authorization state from RocksDB directly, the `AuthorizationChecker` queries authorization records through an `AuthorizationReader` that searches the secondary storage index. This means it operates on eventually-consistent data -- there is a small delay between an authorization being granted in the engine and it becoming visible in the search index.

## Identity Resolution

The `AuthorizationChecker` resolves the calling identity from `CamundaAuthentication`, which carries pre-resolved identity information:

|             Field             |  Entity Type   |
|-------------------------------|----------------|
| `authenticatedUsername`       | `USER`         |
| `authenticatedClientId`       | `CLIENT`       |
| `authenticatedGroupIds`       | `GROUP`        |
| `authenticatedRoleIds`        | `ROLE`         |
| `authenticatedMappingRuleIds` | `MAPPING_RULE` |

All non-null identity fields are collected into a `Map<EntityType, Set<String>>` (owner type to owner IDs). This map is used to query for authorization records belonging to **any** of these owners.

This is a key difference from the engine checks: the REST layer receives pre-resolved group, role, and mapping rule memberships in the `CamundaAuthentication` object, whereas the engine resolves these from its own state.

## Core Operations

### Retrieving Authorized Scopes

```java
retrieveAuthorizedAuthorizationScopes(authentication, authorization)
```

Returns all `AuthorizationScope` entries that the authenticated user (or any of their groups/roles/mapping rules) has been granted for a given resource type and permission type.

The query filters by:
- Owner IDs (user, client, groups, roles, mapping rules)
- Resource type
- Permission type

Results are converted to `AuthorizationScope` objects, which carry the matcher type (ID, PROPERTY, or ANY/wildcard), the resource ID, and optionally a resource property name.

This method is the foundation for **search result filtering** -- the returned scopes define which resource IDs (or wildcard) the user can see.

### Checking Specific Authorization

```java
isAuthorized(authorizationScope, authentication, authorization)
```

Checks whether the authenticated user is authorized for a **specific** resource scope. The query looks for authorization records matching:
- The user's owner IDs
- The resource type and permission type
- Either the specific resource ID **or** the wildcard (`*`)

Returns `true` if at least one matching record exists (uses `page(size=1)` for efficiency).

This method is used for **point checks** -- "can this user perform this action on this specific resource?"

### Collecting Permission Types

```java
collectPermissionTypes(resourceId, resourceType, authentication)
```

Returns the set of all permission types the authenticated user has on a specific resource. Queries for authorization records matching the user's owner IDs, the resource type, and either the specific resource ID or wildcard.

This is used to determine **what actions are available** to a user on a given resource, for example to control which buttons or actions are shown in the UI.

## Query Pattern

All three operations follow the same high-level pattern via `getOrElseDefaultResult`:

1. Collect owner type to owner IDs from `CamundaAuthentication`.
2. If the map is empty (no authenticated identity), return a default (empty list, `false`, or empty set).
3. Otherwise, build an `AuthorizationQuery` and execute it against the `AuthorizationReader`.

For **specific checks** (`isAuthorized`) and **permission collection** (`collectPermissionTypes`), the queries include both the specific resource ID and the **wildcard** (`*`) as alternatives, so that wildcard grants are matched alongside specific resource ID grants. For **scope retrieval** (`retrieveAuthorizedAuthorizationScopes`), the query does not filter on resource IDs at all; instead, it returns all matching scopes for the given owners, resource type, and permission type, and those scopes may themselves represent wildcard or specific matches.
The `AuthorizationReader` is called with `ResourceAccessChecks.disabled()` -- authorization checks on the authorization records themselves are not applied (otherwise you'd need permission to read your own permissions, creating a circular dependency).
