# ADR-0003: UserInfo Claim Augmentation for OIDC Bearer Tokens

## Status

Proposed

## Context

Camunda's OIDC authentication extracts authorization-relevant claims (`groups`, `roles`, tenants, etc.)
from the validated JWT access token. Some Identity Providers do **not** place these claims on the JWT
that is issued as the access token — they only return them from the `/userinfo` endpoint when queried
with that token. Common cases include SaaS IdPs with claim-emission policies that differ between ID
tokens, access tokens, and the userinfo response; and IdPs where the operator has intentionally kept
access tokens compact.

For the **browser login flow** (webapp), Spring Security's OIDC login handler already calls
`/userinfo` when `camunda.security.authentication.oidc.userInfoEnabled=true` (default) and merges the
response into the principal. So this works today — for login.

For the **bearer-token flow** (REST `/v1`, `/v2`, gRPC via Zeebe gateway), the claims map passed to
`TokenClaimsConverter` is `JwtAuthenticationToken.getToken().getClaims()` — i.e. JWT claims only. No
`/userinfo` call is made. If the `groups` claim isn't on the JWT, group-based authorization never
triggers, even though the IdP would return groups if asked.

This breaks a customer scenario where their IdP is configured to emit group membership only via
`/userinfo`.

## Decision

Introduce an **opt-in** augmentation layer for the bearer-token flow. When enabled, a shared
`OidcClaimsProvider` bean calls the IdP's `/userinfo` endpoint with the bearer token, merges the
response onto the JWT claims (UserInfo wins on conflict), and caches the result.

### Architecture

- New interface `io.camunda.security.oidc.OidcClaimsProvider`:
  `Map<String, Object> claimsFor(Map<String, Object> jwtClaims, String tokenValue)`.
- Default implementation `NoopOidcClaimsProvider` returns the JWT claims unchanged — used whenever
  augmentation is disabled. No runtime cost.
- Production implementation `CachingOidcClaimsProvider`:
  - On cache miss, calls `/userinfo` via `OidcUserInfoClient` (JDK `HttpClient`, Jackson for JSON).
  - Caches via Caffeine, keyed by JWT `jti` (or SHA-256 of the raw token if `jti` is absent).
  - Effective TTL is `min(configuredTtl, tokenExp - clockSkew - now)`; cache entries never outlive
    the bearer token.
  - Fails closed on `/userinfo` errors: the request fails with 401 / UNAUTHENTICATED. A short (5s)
    negative cache prevents IdP storm-retries during an outage.
  - Emits Micrometer metrics: `camunda.oidc.userinfo.cache` tagged `hit` / `miss`, and
    `camunda.oidc.userinfo.fetch` as a timer plus a `failure` counter.

### Routing through existing code paths

- REST (`OidcTokenAuthenticationConverter`): resolves claims through `OidcClaimsProvider` before
  handing to `TokenClaimsConverter`.
- gRPC (`AuthenticationHandler.Oidc`): calls `OidcClaimsProvider` once between `JwtDecoder.decode(...)`
  and `OidcPrincipalLoader.load(...)`, so both the groups loader and the principal loader see the
  merged claims.
- The broker's embedded gateway threads the provider through the same chain that `JwtDecoder` travels
  today: `BrokerModuleConfiguration` → `SystemContext` → `BrokerStartupContext(Impl)` →
  `EmbeddedGatewayServiceStep` → `EmbeddedGatewayService` → `Gateway`. With
  `@Autowired(required = false)` + a `NoopOidcClaimsProvider` fallback in
  `BrokerModuleConfiguration`, the chain degrades safely when the authentication module's Spring
  context isn't loaded.

### Userinfo URL source

The `/userinfo` URL is read from Spring's `ClientRegistration.getProviderDetails().getUserInfoEndpoint().getUri()`.
Spring populates it from the IdP's OIDC discovery document (`.well-known/openid-configuration`) at
startup, so it's accurate for every IdP — not a `{issuer}/userinfo` string-stitch. A null URL means
either the IdP doesn't publish a userinfo endpoint, or the operator set `userInfoEnabled=false`.
Either legitimately disables augmentation for that registration; if augmentation is otherwise
enabled but no registration yields a URL, startup fails with a clear message.

### Configuration

```yaml
camunda:
  security:
    authentication:
      oidc:
        userInfoAugmentation:
          enabled: false       # default; opt-in
          cacheTtl: PT5M       # capped by token exp − clockSkew
          cacheMaxSize: 10000  # hard bound on cached entries
```

- **Default is `enabled: false`.** Augmentation is opt-in because it introduces a per-request network
  dependency on the IdP and is a behavioural change for existing deployments on upgrade.
- The `userInfoEnabled` flag (existing, defaults `true`) remains separate — it continues to gate
  Spring's OIDC login-flow userinfo call. When `userInfoAugmentation.enabled=true` and
  `userInfoEnabled=false`, startup fails because no userinfo URL is available on the
  ClientRegistration.

## Consequences

### Positive

- Group-based authorization works for bearer-token requests even when the IdP only returns groups from
  `/userinfo`. This unblocks deployments that previously required IdP-side configuration changes that
  the customer couldn't make.
- Opt-in via a single config flag — no behaviour change for existing deployments on upgrade.
- `NoopOidcClaimsProvider` is the default bean, so the hot path for disabled deployments is a
  zero-allocation pass-through.
- Cache + metrics give operators visibility and bound the IdP load.

### Negative

- When enabled, the first bearer request per `jti` adds one IdP round-trip (~tens of ms in steady
  state; more if the IdP is distant). Worker patterns that reuse the same token amortise this
  trivially via the cache.
- Fail-closed on IdP errors: if `/userinfo` is down, all bearer-authenticated API requests fail
  during the outage. The negative cache prevents retry storms but does not mask the dependency. An
  alternative of "fall back to JWT-only claims on error" was considered and rejected because it
  silently downgrades authorization — a security surprise.
- Single userinfo endpoint is picked when multiple OIDC providers are configured (the first
  registration that exposes one). Multi-provider selection by the token's `iss` claim is a follow-up.

### Out of scope for v1

- Multi-provider userinfo URL selection by `iss`.
- Making the negative cache TTL configurable (currently hard-coded at 5s).
- Exposing a `userInfoUri` override config for IdPs whose discovery document is broken.

## References

- Implementation plan: `docs/superpowers/plans/2026-04-20-oidc-userinfo-claim-augmentation.md`
- Failing-test baseline: `authentication/src/test/java/io/camunda/authentication/oidc/OidcBearerUserInfoClaimGapIT.java`

