# ADR-0006: Physical-Tenant-Scoped gRPC Authentication

## Status

Accepted

## Deciders

- Sebastian Bathke ([@megglos](https://github.com/megglos)) — proposer
- Lena Schoenburg ([@lenaschoenburg](https://github.com/lenaschoenburg))
- Patrick Wunderlich ([@p-wunderlich](https://github.com/p-wunderlich))

## Context

The physical-tenant (PT) effort routes each request's authentication and authorization to the
configuration of the tenant it targets. For the REST/webapp surfaces this is established:
[ADR-0003](0003-physical-tenant-request-scoping-via-pre-security-filter.md) stamps the PT id on the
request before Spring Security runs; [ADR-0005](0005-physical-tenant-routing-of-authorization-reads.md)
routes the authorization layer per PT; and basic-auth credential validation already resolves
`PhysicalTenantContext.current()` and selects `serviceRegistry.userServices(physicalTenantId)` in
`BasicAuthUserDetailsAdapter`. OIDC token validation for REST is built per PT by CSL's
`ScopedJwtDecoderFactory`, driven by the OC-side `PhysicalTenantScopeProvider` /
`PhysicalTenantAuthConfigurations.forPhysicalTenant(id, env)`.

The **gRPC** gateway is the remaining surface, and it does not share REST's mechanism:

- It has **no Spring Security filter chain**. Authentication happens in
  `AuthenticationInterceptor`, which delegates to a sealed `AuthenticationHandler` (`Oidc` or
  `BasicAuth`). Today `Oidc` holds a **single** `JwtDecoder` and `BasicAuth` a **single**
  `UserServices` — both pinned to the cluster/`default` configuration.
- Routing is **header-based** (`Camunda-Physical-Tenant`), not path-based. REST's per-PT decoders
  are built inline inside CSL's HTTP `SecurityFilterChain` closures (`ScopedApiChainRegistrar`),
  keyed by request path — they are not exposed as beans and are not reusable from gRPC.
- **Two interceptors read the same header today.** A separate server-side `PhysicalTenantInterceptor`
  reads `Camunda-Physical-Tenant`, validates it against the known PTs, and stamps the id into the gRPC
  `Context`, where `EndpointManager` reads it to set the broker request's partition group
  (`mapToBrokerRequest` → `setPartitionGroup`). `AuthenticationInterceptor` is the **outermost**
  interceptor and runs **before** it (per gRPC's `ServerInterceptors.intercept` contract — "the last
  interceptor will have its `interceptCall` called first" — and it is added last in
  `Gateway.applyInterceptors()`), so the PT id is **not** in the `Context` when auth runs; auth must
  read the header itself. This makes the standalone interceptor redundant once auth reads the header
  (see Decision B).

The per-PT primitives this needs already exist on `main`: `ServiceRegistry.userServices(pt)` (the
basic-auth user store, used by the REST adapter above) and the
`ScopedJwtDecoderFactory` + `PhysicalTenantAuthConfigurations` construction path (the OIDC decoder
builder REST uses).

## Decision

Authenticate gRPC requests against the targeted PT's configuration, selecting the PT from the
`Camunda-Physical-Tenant` header. The cluster's authentication **method** stays global (OIDC *or*
BASIC, as today); per PT we vary the whole method-specific configuration — for OIDC the decoder
**and** its claim mappings, for BASIC the user store. Per-PT method selection is out of scope.

The unit selected per PT is a fully-constructed **`AuthenticationHandler`**, not a bare decoder. A
gRPC request authenticates exactly as today; the only addition is choosing *which* handler runs,
keyed by the request's PT.

### A. A gRPC-owned per-PT handler registry, decoupled from REST

Introduce a `Map<String, AuthenticationHandler>` registry (keyed by PT id), built eagerly for each
configured PT plus the synthesized `default`. **`AuthenticationHandler` lives in `zeebe/gateway-grpc`
and depends on gRPC types, so the registry is assembled by the gRPC gateway wiring, not the
`authentication` module** — that module must not depend on `gateway-grpc` (it does not today, and a
dependency the other way would also be a cycle, since the gateway consumes the registry). The split:

- The **`authentication` module** exposes only the per-PT, framework-neutral
  `AuthenticationConfiguration` it already resolves
  (`PhysicalTenantAuthConfigurations.forPhysicalTenant(id, env)` / `PhysicalTenantScopeProvider` —
  the same config source REST uses). No gRPC types cross this boundary.
- The **gRPC gateway wiring** (the standalone `GatewayModuleConfiguration` and the embedded broker
  path) turns each per-PT `AuthenticationConfiguration` into an `AuthenticationHandler` and holds the
  resulting map. It already depends on both `gateway-grpc` and the `authentication` module, so the
  dependency direction stays `gateway → authentication`.

Each handler is constructed from its PT's config:

- **OIDC** → `new AuthenticationHandler.Oidc(decoder, claimsProvider, oidcConfig)` where **all three
  arguments are that PT's**: the decoder from
  `ScopedJwtDecoderFactory.buildIssuerAwareDecoder(authConfig)`, `oidcConfig` = that PT's
  `OidcConfiguration`, and `claimsProvider` = that PT's `OidcClaimsProvider`. This is the key reason
  the per-PT unit is a handler, not a decoder:
  - The handler derives its `OidcPrincipalLoader` / `OidcGroupsExtractor` and prefer-username /
    groups-claim behavior from `oidcConfig` at construction time, so a PT on a different IdP (with
    different username/client-id/groups claim names) needs its own handler.
  - The handler invokes `claimsProvider` between `JwtDecoder.decode(...)` and
    `OidcPrincipalLoader.load(...)` to augment claims from the IdP's `/userinfo` endpoint
    ([identity ADR-0006](../../identity/adr/0006-userinfo-claim-augmentation-for-bearer-tokens.md) —
    the bearer-token flow, which includes gRPC). That endpoint and its client config are per-IdP,
    so the claims provider is per-PT as well.

  Swapping only the decoder inside a shared handler would decode the token but extract/augment
  claims with the wrong PT's config.
- **BASIC** → `new AuthenticationHandler.BasicAuth(serviceRegistry.userServices(id), passwordEncoder)`
  — the same per-PT user-store routing `BasicAuthUserDetailsAdapter` already uses for REST
  (ADR-0005). `passwordEncoder` is global.

gRPC and REST therefore share the **factory and config-resolution path, not the constructed
instances**. CSL's `ScopedApiChainRegistrar` is left building REST chains inline; this keeps the gRPC
change isolated and avoids a CSL release on the critical path.

Build failures **fail cluster startup** — for *any* configured PT, not only `default`. A handler
that cannot be built signals a misconfiguration; starting anyway would either silently drop that
PT's API surface or risk a non-isolated fallback, both of which contradict the PT effort's
hard-isolation / no-silent-fallback principle (ADR-0005). Surfacing it loudly at boot is preferred
over discovering it when a tenant's requests start failing. (`PhysicalTenantScopeProvider`, the
REST/webapp scoped-chain side, currently logs-and-skips a failed PT instead; aligning it to the same
fail-fast policy is tracked in #55606.)

### B. One interceptor: read the header once, stamp the `Context`, select the handler

The standalone server-side `PhysicalTenantInterceptor` is **removed**. Its only job was to read
`Camunda-Physical-Tenant` and stamp the resolved PT id into the gRPC `Context`, where
`EndpointManager` reads it to set the broker request's **partition group** (`mapToBrokerRequest` →
`setPartitionGroup`). Since the authentication interceptor must read the same header anyway (to pick
the handler), folding both into one interceptor removes a redundant header parse and one interceptor
from the chain. The `AuthenticationHandler` interface is **unchanged**
(`authenticate(String authorizationHeader)`).

The merged interceptor, for every call:

1. resolves the PT from the header (absent → `default`), validates it against the known PTs, and
   stamps it into the downstream `Context` for `EndpointManager`'s partition-group routing;
2. when the API is protected, selects that PT's handler from the registry and authenticates.

```java
final String ptId = resolvePhysicalTenantId(headers);   // absent → "default"
// (1) stamp for EndpointManager partition-group routing
final Context ctx = Context.current().withValue(getPhysicalTenantIdKey(), ptId);
// (2) authenticate only when the API is protected
if (apiProtected) {
  final AuthenticationHandler handler = handlersByTenant.get(ptId);
  if (handler == null) {                                 // unknown / unconfigured PT
    return deny(call, Status.UNAUTHENTICATED);
  }
  // run handler.authenticate(authorization) within ctx; Left → deny, Right → proceed with its Context
}
```

Because the PT stamp is needed even when the API is unprotected, the interceptor is installed
**unconditionally**; only its authentication step is gated on `isUnprotectedApi()` (today the auth
interceptor is skipped entirely when unprotected, while `PhysicalTenantInterceptor` always runs — the
merge preserves both behaviors in one place). Known-PT validation uses `PhysicalTenantIds.known()`,
as the old interceptor did, so it does not depend on the handler registry (which need not be built
when the API is unprotected).

An **absent** header resolves to the `default` PT, so it authenticates normally — identical to a
non-PT deployment. On the **protected** path an unknown/unconfigured PT has no handler and fails
closed with `UNAUTHENTICATED` (not `NOT_FOUND`), so unauthenticated callers cannot probe PT
existence; on the **unprotected** path an unknown PT is rejected with `NOT_FOUND` exactly as the old
interceptor did (there is no auth secret to protect there).

### C. Both gateway construction sites build and supply the registry

The standalone gateway (`GatewayModuleConfiguration`) and the embedded gateway
(`EmbeddedGatewayServiceStep` → `BrokerStartupContext` → `EmbeddedGatewayService`) each **build** the
handler registry — consuming the `authentication` module's per-PT `AuthenticationConfiguration` and
CSL's `ScopedJwtDecoderFactory` (both already available in these contexts) — and pass it into
`Gateway`, which uses it in `applyInterceptors()` to build the `AuthenticationInterceptor`. When no
PTs are configured the registry is `{ default → <handler built from the cluster's root config> }`, so
non-PT deployments are unchanged.

## Consequences

### Positive

- gRPC reaches per-PT authentication parity with REST: OIDC tokens are validated against the
  targeted PT's issuer/audience **and** principal/group claims are extracted with that PT's claim
  mappings, and basic-auth credentials are validated against the targeted PT's user store.
- Selecting a whole per-PT `AuthenticationHandler` (rather than swapping a decoder inside a shared
  handler) keeps each PT's full method config self-contained, leaves the `AuthenticationHandler`
  interface unchanged, and makes each PT's handler independently testable.
- No implicit `default` routing — the PT is always explicit (from the header, defaulting to
  `default` only when the header is absent), and an unresolvable PT fails closed, in line with the
  PT effort's "never silently fall back to `default`" direction.
- CSL stays physical-tenant-agnostic (Slice 1 principle): all PT resolution lives OC-side, reusing
  `ScopedJwtDecoderFactory`, `PhysicalTenantAuthConfigurations`, and `ServiceRegistry`.
- The gRPC change is isolated — REST's `ScopedApiChainRegistrar` is untouched.
- One interceptor instead of two: the header is parsed once, and the standalone
  `PhysicalTenantInterceptor` is removed (its `Context` stamping moves into the merged interceptor,
  so `EndpointManager`'s partition-group routing is unaffected).

### Negative / trade-offs

- A **second per-PT auth construction** exists (gRPC's handler registry plus REST's inline chains).
  Both derive from one config source (`PhysicalTenantAuthConfigurations`), so drift risk is low;
  consolidating onto a shared registry remains a possible fast-follow.
- Per-PT handlers are built **eagerly** at startup (one decoder / JWKS client per PT for OIDC). This
  matches REST's per-scope construction; if startup cost becomes a concern, lazy construction is a
  later refinement.
- The merged interceptor now carries two concerns (PT resolution/stamping + authentication). They
  share a single input (the header), and the authentication step is cleanly gated on
  `isUnprotectedApi()`, so the coupling is shallow.

### Neutral

- The cluster authentication method stays global; the handler-selection `switch` is unchanged.
- Single/default-PT deployments are unchanged (everything resolves to the one `default` PT).
- `EndpointManager`'s partition-group routing is unchanged — it still reads the PT id from the
  `Context`; only the producer of that value changes (the merged interceptor instead of the removed
  `PhysicalTenantInterceptor`).

## Alternatives Considered

1. **Keep two interceptors and reorder so `PhysicalTenantInterceptor` precedes auth**, letting auth
   read the PT id from `Context`. Rejected in favour of merging them (Decision B): reordering keeps a
   redundant second header parse, and having `PhysicalTenantInterceptor` validate first would let
   unauthenticated callers distinguish `NOT_FOUND` vs. `UNAUTHENTICATED` (a PT-existence probe) on
   the protected path. The merged interceptor reads the header once and only emits `NOT_FOUND` on the
   unprotected path.

2. **Share REST decoder instances with gRPC** by exposing CSL's per-chain decoders as beans.
   Rejected for this slice: REST decoders are path-keyed and captive inside filter-chain closures,
   and exposing them pushes PT/scope concepts deeper into CSL, widening blast radius. Sharing the
   construction path gives the same correctness without the coupling.

3. **Per-PT authentication method** (each PT independently OIDC or BASIC, from its merged
   `AuthenticationConfiguration`). Deferred: significant interceptor complexity for a capability the
   slice does not require — the issue asks only to route to the corresponding decoder / user store.

## References

- [ADR-0003](0003-physical-tenant-request-scoping-via-pre-security-filter.md) — pre-security filter
  that stamps the PT id on REST requests; the contrast that motivates header-reading on gRPC.
- [ADR-0005](0005-physical-tenant-routing-of-authorization-reads.md) — per-PT routing of the
  authorization layer; establishes the `serviceRegistry.userServices(pt)` basic-auth routing this
  ADR mirrors on gRPC.
- [identity ADR-0006](../../identity/adr/0006-userinfo-claim-augmentation-for-bearer-tokens.md) — the
  bearer-token `/userinfo` claim-augmentation flow (`OidcClaimsProvider` invoked inside
  `AuthenticationHandler.Oidc`), which applies to gRPC and is why the claims provider is per-PT.
- #54896 — Physical Tenants Identity, Slice 2 (gRPC) — the issue this ADR addresses.
- #54728 — Physical Tenants Identity, Slice 1 (parent); the principle that CSL stays PT-agnostic.
- `AuthenticationInterceptor` / `AuthenticationHandler`
  (`zeebe/gateway-grpc/.../interceptors/impl/`) — the gRPC auth entry point and sealed handler this
  ADR makes PT-aware.
- `PhysicalTenantInterceptor` (`zeebe/gateway-grpc/.../interceptors/impl/`) — the existing
  server-side interceptor that reads/validates the `Camunda-Physical-Tenant` header and stamps the
  `Context`; **removed** by this ADR, its responsibility folded into the merged interceptor.
- `EndpointManager` (`zeebe/gateway-grpc/.../EndpointManager.java`) — reads the PT id from the
  `Context` (`getPhysicalTenantIdKey`) to set the broker request's partition group; the reason the
  stamp must be preserved.
- `ScopedJwtDecoderFactory` (CSL) and `PhysicalTenantAuthConfigurations` /
  `PhysicalTenantScopeProvider` (`authentication/.../pt/`) — the per-PT decoder construction path
  shared with REST.
