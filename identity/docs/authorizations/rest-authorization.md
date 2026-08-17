# REST Layer Authorization Checks

This document describes how authorization is enforced in the REST API layer, for requests that query secondary storage (Elasticsearch/OpenSearch) and for post-filtering of results.

**Location:** the check itself is CSL's. What is local is the port implementation behind it: `SearchAuthorizationScopeRepository` (`security/security-services/.../security/impl/`) implements CSL's `AuthorizationScopeRepositoryPort` and does the actual search-index querying. It is wired in via `DefaultResourceAccessProvider.forScopeRepository(...)` (`search/search-client-query-transformer/.../auth/DefaultResourceAccessProvider.java`). Some services (for example `DocumentServices`, `SecretServices`) hold a per-physical-tenant check directly, built by `AuthorizationCheckerFactory.forPhysicalTenant(...)` (`security/security-services/.../security/impl/`), bypassing the `ResourceAccessProvider`/data-plane path.

## Overview

The REST-side check operates after data has been written to secondary storage. While the [engine authorization checks](engine-authorization.md) gate command processing against RocksDB, OC uses this one for three purposes:

1. **Filtering search results** -- the authorized scopes define which resource IDs (or wildcard) are visible.
2. **Point checks** -- may this caller act on this specific resource, before the REST layer forwards the request to the engine.
3. **Collecting permissions** -- which permission types the caller holds on a resource, used for UI feature toggling.

This is a **query-time / pre-forwarding check** that reads authorization data from the search index. For how CSL reaches its decision, see the [CSL architecture documentation](https://github.com/camunda/camunda-security-library/blob/main/docs/architecture/05-building-block-view.md), section 5.4.

The check class itself is deliberately not named on this page -- only the port OC implements behind it. See the [naming convention](../architecture.md#csl-extension-points-and-ocs-adapters) for which CSL types these docs name and why.

## Data Source

Unlike the engine checks, which read authorization state from RocksDB directly, the REST-side check reads everything through its `AuthorizationScopeRepositoryPort`. OC's implementation of that port for the REST/search-filtering path, `SearchAuthorizationScopeRepository`, queries authorization records through an `AuthorizationReader` against the secondary storage index. This means it operates on eventually-consistent data -- there is a small delay between an authorization being granted in the engine and it becoming visible in the search index.

Unlike the engine, the REST layer does not resolve memberships itself: it receives group, role, and mapping rule memberships pre-resolved in the `CamundaAuthentication` object.

## Query Pattern

Each of the three purposes above maps to one port method that `SearchAuthorizationScopeRepository` implements -- `findAuthorizedScopes`, `hasAuthorizedScope`, and `findPermissionTypes` -- and all three follow the same high-level pattern:

1. Collect owner type to owner IDs from `CamundaAuthentication`.
2. If the map is empty (no authenticated identity), return a default (empty list, `false`, or empty set) without querying.
3. Otherwise, build an `AuthorizationQuery` and execute it against the `AuthorizationReader`.

For **point checks** (`hasAuthorizedScope`) and **permission collection** (`findPermissionTypes`), the queries include both the specific resource ID and the **wildcard** (`*`) as alternatives, so wildcard grants are matched alongside specific resource ID grants. For **scope retrieval** (`findAuthorizedScopes`), the query does not filter on resource IDs at all; it returns all matching scopes for the given owners, resource type, and permission type, and those scopes may themselves represent wildcard or specific matches.

The `AuthorizationReader` is called with `ResourceAccessChecks.disabled()` -- authorization checks on the authorization records themselves are not applied, since otherwise you would need permission to read your own permissions, creating a circular dependency.
