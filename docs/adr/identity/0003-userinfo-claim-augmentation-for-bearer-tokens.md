# ADR-0003: UserInfo Claim Augmentation for OIDC Bearer Tokens

## Status

Proposed

## Context

Camunda's OIDC authentication extracts authorization-relevant claims (`groups`, `roles`, tenants,
etc.) from the validated JWT access token. Some Identity Providers do **not** place these claims
on the JWT that is issued as the access token — they only return them from the `/userinfo`
endpoint when queried with that token. Common cases include SaaS IdPs with claim-emission
policies that differ between ID tokens, access tokens, and the userinfo response; and IdPs where
the operator has intentionally kept access tokens compact.

For the **browser login flow** (webapp), Spring Security's OIDC login handler already calls
`/userinfo` when `camunda.security.authentication.oidc.userInfoEnabled=true` (default) and merges
the response into the principal. So this works today — for login.

For the **bearer-token flow** (REST `/v1`, `/v2`, gRPC via Zeebe gateway), the claims map passed
to `TokenClaimsConverter` is `JwtAuthenticationToken.getToken().getClaims()` — i.e. JWT claims
only. No `/userinfo` call is made. If the `groups` claim isn't on the JWT, group-based
authorization never triggers, even though the IdP would return groups if asked.

This breaks a customer scenario where their IdP is configured to emit group membership only via
`/userinfo`. Before adopting this feature, operators should confirm they cannot configure the IdP
to emit the missing claim on the access token directly (most major IdPs — Keycloak, Okta, Auth0,
Azure AD, PingFederate — support this via scopes, claim mappers, or access-token policies).
Fixing at the IdP is strictly cheaper because it avoids the new runtime dependency described
below; this feature exists for deployments where that path is not available.

## Decision

Introduce an **opt-in** augmentation layer for the bearer-token flow. When enabled, a shared
`OidcClaimsProvider` bean calls the IdP's `/userinfo` endpoint with the bearer token and
**additively** merges the response onto the JWT claims: the JWT wins on every conflict; UserInfo
contributes only claims absent from the JWT. The result is cached per-token.

### Architecture

- New interface `io.camunda.security.oidc.OidcClaimsProvider`:
  `Map<String, Object> claimsFor(Map<String, Object> jwtClaims, String tokenValue)`.
- Default implementation `NoopOidcClaimsProvider` returns the JWT claims unchanged — used
  whenever augmentation is disabled. No runtime cost.
- Production implementation `CachingOidcClaimsProvider`:
  - On cache miss, calls `/userinfo` via `OidcUserInfoClient` (JDK `HttpClient`, Jackson for JSON).
  - Uses Caffeine's atomic `cache.get(key, loader)` so concurrent misses on the same key
    coalesce — exactly one `/userinfo` call per (key, TTL) window even under burst load.
  - Keyed by `jti:{iss}:{jti}` when the JWT has a `jti`, otherwise `sie:{iss}:{sub}:{iat}:{exp}`.
    The `iss` prefix is required because `jti` is only unique per-issuer (RFC 7519); without it,
    two providers in a multi-provider deployment can legitimately issue tokens with identical
    `jti` values and collide. The fallback of `sub+iat+exp` is chosen deliberately over
    `SHA-256(token)`: a heap dump or metrics leak of the key itself reveals only auth metadata,
    not a token-correlatable fingerprint.
  - Effective TTL for successful entries is `min(configuredTtl, tokenExp − clockSkew − now)`;
    cache entries never outlive the bearer token.
  - Emits Micrometer metrics: `camunda.oidc.userinfo.cache` tagged `hit` / `miss`, and
    `camunda.oidc.userinfo.fetch` as a timer plus a `failure` counter.

### Merge semantics — JWT wins, additive only

UserInfo responses are HTTP payloads, not signed JWTs. Letting `/userinfo` override JWT claims
would let a compromised or misconfigured IdP shadow the signed token's authorization-relevant
fields — `sub`, `iss`, `aud`, `exp`, `jti`, etc. The merge instead preserves every JWT claim and
adds UserInfo claims only where the JWT is silent. This keeps the cryptographic guarantee of the
signed token load-bearing for identity and lifetime while still benefiting from `/userinfo`'s
richer (optional) group data.

Per OIDC Core §5.3.2 the UserInfo `sub` MUST equal the JWT `sub`. If they differ (IdP
misconfiguration, mis-federation, or worse), the provider logs ERROR, increments the failure
counter, and returns JWT-only claims without merging — the UserInfo response is treated as a
degradation event.

### Routing through existing code paths

- REST (`OidcTokenAuthenticationConverter`): resolves claims through `OidcClaimsProvider` before
  handing to `TokenClaimsConverter`.
- gRPC (`AuthenticationHandler.Oidc`): calls `OidcClaimsProvider` once between
  `JwtDecoder.decode(...)` and `OidcPrincipalLoader.load(...)`, so both the groups loader and
  the principal loader see the merged claims. Catch blocks return a generic 401 status and log
  the underlying exception server-side rather than attaching it via `.withCause(e)` — so IdP
  URLs and internal diagnostic strings don't leak to gRPC clients.
- The broker's embedded gateway threads the provider through the same chain that `JwtDecoder`
  travels today: `BrokerModuleConfiguration` → `SystemContext` → `BrokerStartupContext(Impl)` →
  `EmbeddedGatewayServiceStep` → `EmbeddedGatewayService` → `Gateway`. With
  `@Autowired(required = false)` + a `NoopOidcClaimsProvider` fallback in
  `BrokerModuleConfiguration`, the chain degrades safely when the authentication module's
  Spring context isn't loaded.

`OidcTokenAuthenticationConverter.supports(...)` checks for `JwtAuthenticationToken`
specifically. Camunda does not configure Spring's `OpaqueTokenIntrospector` anywhere, so every
bearer-authenticated request produces a `JwtAuthenticationToken`; if opaque-token introspection
is ever added, `supports()` would need to widen to `AbstractOAuth2TokenAuthenticationToken`.

### Userinfo URL source — per-issuer map

`CachingOidcClaimsProvider` holds a `Map<issuer, URI>` built at startup from the Spring-managed
`ClientRegistrationRepository`. Each registration contributes its
`ProviderDetails.getIssuerUri()` → `UserInfoEndpoint.getUri()` — both populated by Spring from
the IdP's OIDC discovery document. At request time, the provider looks up the URL by the JWT's
`iss` claim. This guarantees tokens are always sent back to the same IdP that signed them; a
token from provider A cannot be routed to provider B's `/userinfo` in a multi-provider
deployment.

A null URL at startup (no registration publishes a userinfo endpoint, or the operator set
`userInfoEnabled=false` on all providers) fails application startup — aligned with the existing
`ClientRegistrations.fromIssuerLocation(...)` failure precedent for unreachable IdPs. At
request time, a token with an unknown `iss` degrades to JWT-only claims rather than guessing an
endpoint or sending the bearer token to the wrong IdP.

### Failure-mode asymmetry — startup fail-fast, runtime fail-open

The two failure modes are orthogonal and treated differently on purpose:

- **Startup** is a configuration surface. Camunda already fails fast when
  `ClientRegistrations.fromIssuerLocation(...)` can't reach the IdP (see
  `WebSecurityConfig.createClientRegistration`). The UserInfo bean follows suit: if augmentation
  is enabled but no registration exposes a userinfo URL, `IllegalStateException` aborts startup
  with an operator-facing message. Permanent misconfiguration is surfaced loudly.
- **Runtime** is a liveness surface. A `/userinfo` call that fails (network, non-2xx, parse
  failure, body too large, `application/jwt` signed response, or UserInfo `sub` mismatch) logs
  ERROR, increments `camunda.oidc.userinfo.fetch{outcome=failure}`, and returns JWT-only claims
  for that request. The degraded outcome is cached negatively for
  `negativeCacheTtl` (default 5s; configurable) to prevent retry storms during an IdP outage.
  Operators who want tighter recovery after an outage can shorten the negative TTL; those who
  want heavier dampening can extend it.

An earlier version of the design proposed fail-closed at runtime (reject with 401 on any IdP
error). That was changed because it turned every IdP blip into an estate-wide bearer-auth
outage — unacceptable for a stable-line release. The operational trade-off of the current
design: during a `/userinfo` outage, authorization checks that depend on claims *only* present
in UserInfo (e.g. `groups`) will evaluate against JWT-only claims and may deny operations that
would otherwise succeed. This is a strict degradation: no privilege escalation, only loss of
capability. Operators should monitor the failure counter and alert on sustained non-zero
values.

### Defences in OidcUserInfoClient

- Response body size capped at 1 MiB (`OidcUserInfoClient.MAX_BODY_BYTES`). Oversized responses
  are rejected before parsing. Note: `HttpResponse.BodyHandlers.ofByteArray()` buffers the full
  response before the check, so worst-case heap is `MAX_BODY_BYTES` per concurrent uncached
  fetch. A streaming `BodySubscriber` with an in-stream cap is a documented follow-up for a
  future iteration.
- `Content-Type: application/jwt` rejected with a clear error — signed UserInfo responses per
  OIDC Core §5.3.2 are not supported in this version.
- Request + connect timeouts are 2 seconds each, tightened from an earlier 5-second default to
  limit the synchronous blocking exposure on the gRPC interceptor thread.
- Clients without `openid` scope (typically M2M / client-credentials tokens) skip augmentation
  silently — UserInfo is only defined for openid-scope tokens per OIDC Core §5.3.

### Configuration

```yaml
camunda:
  security:
    authentication:
      oidc:
        userInfoAugmentation:
          enabled: false              # default; opt-in
          cacheTtl: PT5M              # capped by (tokenExp − clockSkew)
          cacheMaxSize: 10000         # hard bound on cached entries
          negativeCacheTtl: PT5S      # TTL for degraded (fail-open) entries
```

- **Default is `enabled: false`.** Augmentation is opt-in because it introduces a per-request
  network dependency on the IdP and is a behavioural change for existing deployments on
  upgrade.
- The `userInfoEnabled` flag (existing, defaults `true`) remains separate — it continues to
  gate Spring's OIDC login-flow userinfo call. When `userInfoAugmentation.enabled=true` and
  `userInfoEnabled=false`, startup fails because no userinfo URL is populated on the
  `ClientRegistration`.
- An `HttpClient` bean `oidcUserInfoHttpClient` is registered with Spring (not constructed
  inline); it inherits the `oidc-userinfo` entry from `spring.ssl.bundle.*` when configured,
  so enterprise deployments with custom CA trust apply automatically. The client can be
  overridden by registering another bean with the same name.

## Consequences

### Positive

- Group-based authorization works for bearer-token requests even when the IdP only returns
  groups from `/userinfo`. This unblocks deployments that previously required IdP-side
  configuration changes they couldn't make.
- Opt-in via a single config flag — no behaviour change for existing deployments on upgrade.
- `NoopOidcClaimsProvider` is the default bean, so the hot path for disabled deployments is a
  zero-allocation pass-through.
- JWT's signed claims remain authoritative. The design deliberately cannot escalate privilege
  via UserInfo; only claims absent from the JWT are contributed.
- Cache + metrics give operators visibility and bound the IdP load; per-issuer URL routing and
  the atomic loader keep the feature correct under multi-provider deployments and burst
  concurrency.

### Negative

- When enabled, the first bearer request per (iss, jti) adds one IdP round-trip (~tens of ms
  in steady state; more if the IdP is distant). Worker patterns that reuse the same token
  amortise this trivially via the cache.
- During a `/userinfo` outage, authorization checks that depend on UserInfo-only claims degrade
  to "missing" rather than 401. This is documented fail-open behaviour, not a bug; operators
  monitoring the fetch-failure counter are the primary alerting surface.
- The gRPC interceptor call is synchronous; under sustained IdP latency the 2s timeout is the
  primary bound on thread-tie-up. A future improvement could introduce a circuit breaker that
  short-circuits augmentation entirely after N consecutive failures in a window, but that's
  out of scope for this PR.

### Out of scope for v1

- A circuit breaker around the fetch path (supplementary to the negative cache).
- Supporting signed UserInfo responses (`application/jwt`).
- An explicit `userInfoUri` override config for IdPs whose discovery document is broken.
- Async I/O on the gRPC interceptor thread.

## References

- Failing-test baseline: `authentication/src/test/java/io/camunda/authentication/oidc/OidcBearerUserInfoClaimGapIT.java`

