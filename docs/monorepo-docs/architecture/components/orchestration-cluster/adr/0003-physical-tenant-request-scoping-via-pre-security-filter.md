# ADR-0003: Single Pre-Security Filter for Physical-Tenant Request Scoping

## Status

Accepted

## Deciders

- Sebastian Bathke ([@megglos](https://github.com/megglos))
- Ben Sheppard ([@Ben-Sheppard](https://github.com/Ben-Sheppard))
- Tobias Metzke ([@tmetzke](https://github.com/tmetzke))
- Deepthi Akkoorath ([@deepthidevaki](https://github.com/deepthidevaki))
- Patrick Wunderlich ([@p-wunderlich](https://github.com/p-wunderlich))

## Context

Physical-tenant API requests carry the tenant id in the URL path
(`/physical-tenants/{id}/v2/...`). Several components need the resolved id during a request:
**controllers** (via `@PhysicalTenantId`), downstream services (`PhysicalTenantContext.current()`),
and — once CSL's per-scope security chains are adopted — **components running _inside_ Spring
Security's filter chain** (per-tenant basic-auth user resolution, per-tenant OIDC, and webapp
concerns such as tenant-specific session storage). The id is held on the request as an attribute
through `PhysicalTenantContext`.

A single Spring MVC component resolves it: **`PhysicalTenantInterceptor`** (a
`HandlerInterceptor` in gateway-rest). In `preHandle` it reads the matched URI template variable,
**validates** the id against the configured tenants (unknown → HTTP 404 before the controller),
defaults to `default` when no prefix is present, and stamps `PhysicalTenantContext`.
`PhysicalTenantRequestMappingHandlerMapping` registers the tenant-prefixed sibling routes, and
`PhysicalTenantIdArgumentResolver` enables `@PhysicalTenantId` injection.

This interceptor-based approach has a fundamental limitation under CSL adoption: a
`HandlerInterceptor.preHandle` runs during MVC dispatch, **after** Spring Security's filter chain.
The tenant id is consumed _inside_ that chain (per-tenant user resolution, per-tenant OIDC, webapp
session storage), so it must already be on the request when the chain executes — i.e. it must be
stamped **before** the chain runs, which an interceptor cannot do.

CSL adoption also changes how unknown tenants are handled. CSL registers security filter chains in a
fixed order:

| Order | Chain | Matcher |
|---|---|---|
| 0 | unprotected paths | specific unprotected paths |
| 1 | cluster API chain | `apiPaths()` → `/v2/**` |
| 1 | per-scope chains | `basePath + apiPaths` → `/physical-tenants/<configured>/v2/**` |
| 1 | webapp chains | webapp paths |
| **2** | **catch-all "unhandled" chain** | **`/**`, `denyAll()`** |

The catch-all (`BaseSecurityConfiguration.protectedUnhandledPathsSecurityFilterChain` in CSL) denies
all requests and returns **HTTP 404** from both its authentication entry point and its access-denied
handler. A request for an _unconfigured_ tenant (`/physical-tenants/unknownX/v2/...`) matches no
scope chain and no cluster chain, so it falls through to the catch-all and is rejected with 404 —
and because an interceptor only runs once a chain has _permitted_ the request and dispatch has
begun, such a request never reaches the interceptor at all. With CSL active, the interceptor's
existence check is therefore redundant with CSL's catch-all.

## Decision

Replace `PhysicalTenantInterceptor` with a servlet `Filter`, `PhysicalTenantPreSecurityFilter`, as
the **single** physical-tenant id extraction point.

- The pre-security filter runs before Spring Security's `FilterChainProxy`, parses the tenant id
  from the raw request URI for `/physical-tenants/*` paths, and stamps `PhysicalTenantContext` — so
  the id is on the request before the chain executes and is available to the in-chain components
  that consume it. Because it sits at the servlet layer it covers both the REST
  (`/physical-tenants/{id}/v2/...`) and MCP (`/physical-tenants/{id}/mcp/...`) surfaces uniformly. It
  is **permissive**: it does not validate the id and needs no configured-tenant set.
- **Unknown-tenant rejection (404) is delegated to CSL's catch-all chain.** The OC no longer
  performs its own physical-tenant existence check.
- **Remove `PhysicalTenantInterceptor`** and its bean wiring: with CSL active its validation is
  redundant (see Context) and its population is superseded by the filter.
- `PhysicalTenantRequestMappingHandlerMapping` is **kept** — it is responsible for _routing_ (making
  the tenant-prefixed paths reach controllers), a separate concern from id extraction.
- `PhysicalTenantIdArgumentResolver` (`@PhysicalTenantId`) is **kept** — it reads the context the
  filter populates.
- Non-prefixed cluster paths (`/v2/...`) set no attribute; `PhysicalTenantContext.current()` and the
  argument resolver fall back to `default`, preserving the existing default behaviour.

This decision is made in the context of CSL adoption, where the catch-all chain is present; the
unknown-tenant 404 it provides is what makes the interceptor's validation removable.

## Consequences

### Positive

- One extraction point and one source of truth for the physical tenant id; no duplicate population
  and no risk of the two copies diverging (e.g. the earlier divergent request-attribute-key hazard
  addressed by consolidating `PhysicalTenantContext`).
- Servlet-layer extraction covers REST and MCP uniformly, rather than being tied to MVC dispatch.
- Less code: removes an interceptor whose validation is unreachable and whose population is
  redundant, plus its bean wiring in `UnifiedConfigurationModule` and `PhysicalTenantWebMvcConfig`.
- Unknown-tenant handling is centralised in CSL's chain set rather than duplicated in an OC MVC
  component.

### Negative / trade-offs

- The unknown-tenant **response body** changes from the interceptor's descriptive
  `"Unknown physical tenant: <id>"` to CSL's bare 404. (In practice the descriptive message was
  already unreachable once CSL's chains were active, so this is not an observable change on this
  configuration.)
- Behaviour now **depends on CSL's catch-all chain being present**. This holds wherever the OC
  activates CSL's always-on `BaseSecurityConfiguration`, which this work does; a deployment that
  somehow disabled it would lose deterministic unknown-tenant handling.
- The known-tenant (`401`, via the scope chain) vs unknown-tenant (`404`, via the catch-all)
  status-code distinction is a property of CSL's chain set and remains an enumeration signal. It is
  unchanged by this decision (the interceptor did not affect it) but is recorded here for awareness.

## Alternatives Considered

1. **Keep the interceptor and add the pre-security filter alongside it.** This was the intermediate
   state on this PR's branch before this decision. Rejected: redundant population, validation that is
   dead under CSL (see Context), and two mechanisms maintaining the same request state.
2. **Move validation into the pre-security filter** (it could take the configured-tenant set and
   reject early). Rejected: it would reject _before_ authentication, forcing a choice between leaking
   tenant existence to anonymous callers (pre-auth 404) or a non-descriptive early 401, and it would
   re-couple the filter to tenant configuration — all to reproduce a 404 that CSL's catch-all already
   provides deterministically.
3. **Consolidate everything into the interceptor.** Not viable: a `HandlerInterceptor` runs after the
   security chain, so it cannot provide the tenant id to the authentication layer.

## References

- [camunda/camunda-security-library#378](https://github.com/camunda/camunda-security-library/pull/378)
  — the `CamundaSecurityScopeProvider` SPI and per-scope API chains (incl. the catch-all chain).
- OC #54729 — per-physical-tenant API security chains (the work this ADR is part of).
- OC #54651 — tenant-first physical-tenant API routing (`PhysicalTenantRequestMappingHandlerMapping`).
- OC #54730 — per-tenant provider selection (`providers.assigned`), deferred.
