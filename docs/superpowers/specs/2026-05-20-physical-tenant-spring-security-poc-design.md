# Physical-Tenant Spring Security PoC — Design

**Status:** **PoC closed 2026-05-22.** Functional surface complete and verified via `pt-poc-api-smoke.sh` matrix + manual browser smoke. Open follow-ups: Task 18 (real Operate + Tasklist webapps on PT chains); Tasks 14/15 (Testcontainers ITs — skipped, deferred to the eventual CSL upstreaming work where the multi-IdP test infrastructure already exists).
**Scope:** OC (`dist/`) application only. Optimize out of scope.
**Audience:** sebastian.bathke@camunda.com / identity squad.

---

## Context

Camunda 8.10 introduces the **Physical Tenant (PT)** concept: a single OC process serves multiple tenants behind dedicated URL prefixes, each with its own OIDC providers, sessions, and (eventually) secondary storage. PR #52529 plus the follow-ups on `identity-pt-poc` land the PT plumbing on the REST side (`PhysicalTenantRequestMappingHandlerMapping`, `PhysicalTenantInterceptor`, `PhysicalTenantContext`, `PhysicalTenantResolver`). The Spring Security half does not exist yet — that is the gap this PoC closes.

URL scheme (final):

| Surface  |                  Prefixed                  | Default-only fallback[^csl-default] |
|----------|--------------------------------------------|-------------------------------------|
| REST API | `/physical-tenant/<id>/v2/...`             | `/v2/...`                           |
| Webapps  | `/physical-tenant/<id>/<webcomponent>/...` | `/<webcomponent>/...`               |

[^csl-default]: The default-only fallback URLs are served by CSL's standard `OidcWebapp` and `OidcApi` chains (Stage 2), not by PoC-owned chains. The PoC contributes only the prefixed chains; CSL handles the unprefixed default tenant through its standard configuration at the root `camunda.security.authentication.oidc.*` slot.

The REST API is **additionally** reachable under the existing API-client scheme
`/v2/physical-tenants/<id>/...` — both schemes hit the same per-tenant security chain. See
[D7](#d7-url-scheme-dual-api-prefix-webapp-aligned-and-existing-api-client).

Requirements:

1. Each PT has an isolated Spring Security setup: its own OIDC providers, its own `authorization_code` flow, its own session.
2. Logging in on PT *A* must not authenticate the user against PT *B*. A single browser can hold N PT sessions simultaneously.
3. The special `default` PT is reachable via both prefixed and unprefixed paths. We accept that switching access path may force a re-login (simplification agreed by the user).
4. PT session storage is structurally isolated per tenant (each tenant owns a private session store; no shared backend, no key-prefixing). The PoC uses in-memory backends; binding to durable per-PT secondary storage is a deliberate follow-up (see "Out of scope").

---

## Architectural decisions

### D1. Use Spring Security's `SecurityFilterChain` as the isolation primitive

Spring Security's filter chain is already a per-`securityMatcher` unit with its own auth-state collaborators (`ClientRegistrationRepository`, `OAuth2AuthorizedClientRepository`, `SecurityContextRepository`, `LogoutSuccessHandler`, …). Multiplying this per tenant gives us isolation without inventing new mechanisms.

**Reject** the alternative of one chain set with runtime tenant-resolving wrappers: it forces a single session cookie at the servlet level and pushes tenant attribute keys into the same `HttpSession`, which is exactly the cross-bleed surface we want to avoid.

**Defer** the alternative of per-tenant child Spring contexts + per-tenant `DispatcherServlet`s (call it approach C). C buys genuinely stronger server-side isolation — bean graph, property binding, and configuration-error containment all become structural rather than disciplinary — but does not add to browser-side isolation, which still rides on cookie `Path`. For a PoC whose job is to validate the cookie-`Path` hypothesis, C's ~2× code cost and the unretired risk of CSL having hidden root-context assumptions (auto-configuration is loaded at the root by Spring Boot and would need explicit `@Import` into each child plus verification of any static/listener-based wiring) is not warranted. Graduating to C is a viable future step; see the portability constraint in [D6](#d6-design-as-portable-to-c).

### D2. Use cookie `Path` attribute as the browser-side isolation primitive

For each PT we emit a session cookie scoped to the PT's path. The browser then refuses to send tenant A's cookie on tenant B's URLs — no server-side tenant fan-out required. The cookie set per chain:

- `name=camunda-session-<tenantId>` (avoids name collisions when multiple chains' cookies could overlap on the default tenant's `/`)
- `Path=/physical-tenant/<tenantId>` — a single cookie covers both the webapp URLs (`/physical-tenant/<t>/...`) and the webapp-aligned API URLs (`/physical-tenant/<t>/v2/...`). The API URL space is mounted under the tenant prefix so the cookie's `Path` scope covers both surfaces; see [OQ-1 resolution](#open-questions). The existing API-client scheme `/v2/physical-tenants/<t>/...` is supported alongside the webapp-aligned scheme ([D7](#d7-url-scheme-dual-api-prefix-webapp-aligned-and-existing-api-client)) but sits outside the cookie's `Path`, so cookie auth doesn't apply there — that scheme is bearer-only.

The unprefixed default access path is served by CSL's standard `OidcWebapp` and `OidcApi` chains (Stage 2). Those chains use CSL's standard `camunda-session` cookie at CSL's standard Path; the cookie name does not overlap with any prefixed PT chain so the per-tenant isolation property still holds (a browser logged in to the unprefixed default access path holds `camunda-session`; a browser logged in to prefixed default holds `camunda-session-default`; switching access paths forces a re-auth as agreed under requirement 3).

CSRF cookies follow the same Path scoping.

### D3. Config-driven activation; PT chains co-exist with CSL chains

The library (CSL) is a singleton-style auto-config that assumes one chain per (auth-method × api/webapp). The PT chains do not displace CSL's chains — they interleave with them via `@Order` precedence.

- `PhysicalTenantSecurityConfiguration` and the supporting bean overlays in `OidcOverrideBeansConfiguration` (`ptClientRegistrationRepositories`, `ptAllowedIssuersPerTenant`, `ptExpectedAudiencesPerTenant`) are always available; activation is config-driven.
- `PhysicalTenantSecurityChainRegistrar` self-gates on the tenant set bound from `camunda.physical-tenants.*`. An empty map ⇒ no chains registered, OC boots as a single-tenant deployment served by CSL's standard chains. A non-empty map ⇒ one prefixed API chain + one prefixed webapp chain per tenant get registered.
- Ordering layout (low number wins): PT API chains at `@Order(-1)` (sub-pattern of `/v2/**`, must beat CSL's OidcApi at `@Order(1)`); PT webapp chains at `@Order(0)` (disjoint matchers from CSL's unprotected-paths chain at the same order); CSL's chains stay at `0/1/2`. See the `PhysicalTenantSecurityChainRegistrar` javadoc for the full table.
- The session machinery cooperates: `WebSessionRepositoryConfiguration.SingleTenant` (with `@EnableSpringHttpSession` and the cluster-wide `WebSessionRepository`) loads only when no PTs are configured; `PerPhysicalTenant` produces the per-tenant `Map<String, WebSessionRepository>` only when PTs are configured. Both nested configs are gated by `PhysicalTenantsConfiguredCondition` / `NoPhysicalTenantsConfiguredCondition` (a `Binder`-backed `@Conditional` — the property is a structured map, not a flat value).

This keeps the blast radius small: a deployment with no PT entries is byte-for-byte equivalent in security behaviour to the pre-PT codebase.

### D4. Reuse OC's OIDC machinery; bind it per tenant

OC already owns the OIDC primitives needed (`OidcAuthenticationConfigurationRepository`, `ClientRegistrationFactory`, `ClientAwareOAuth2AuthorizationRequestResolver`, `OidcIdTokenDecoderFactory`, `TokenValidatorFactory`, `IssuerAwareJWSKeySelector`, `CamundaOidcLogoutSuccessHandler`). The PoC does not rebuild any of these — it parameterises them per tenant by feeding the tenant's `SecurityConfiguration` slice.

### D5. Tenant resolution at the chain-matcher level

Each chain matches a fixed-prefix `securityMatcher` containing the tenant id literal. No per-request "which tenant am I" lookup inside a filter is required for the security stack itself — the matcher resolves it.

The PoC controller takes the tenant id from `@PathVariable` directly and does not depend on the ambient `PhysicalTenantContext.current()` accessor. The existing `PhysicalTenantInterceptor` continues to populate the request attribute for `/v2/…` REST routes via `PhysicalTenantRequestMappingHandlerMapping`. Webapp paths (`/physical-tenant/<id>/<webcomponent>/…`) do not flow through that interceptor today, and no PoC consumer needs them to — `PhysicalTenantContext.current()` has no production callers in the repo at this scope. If a downstream service called from a webapp-route controller starts depending on ambient tenant context, a small filter can be added then (~10 lines, deferred to YAGNI).

### D6. Design as portable to approach C

We don't build C now, but we deliberately keep the per-tenant chain factory portable to a child-context model so the migration cost is bounded if C becomes warranted later. Concretely:

- `PerTenantSecurityChainFactory` and `TenantSecuritySlice` take all dependencies as constructor arguments. No `@Autowired` field injection from a root context. No `@ConditionalOnMissingBean` games between tenant chains.
- Per-tenant bean names are derived from the tenant id and never collide with a "global" bean name CSL would create. This keeps each tenant's bean graph addressable as a unit.
- `PhysicalTenantSecurityConfiguration` is the only place that touches `PhysicalTenantResolver.getAll()` and root-context bean registration. To graduate to C, this class is replaced; the chain factory and slice are reused verbatim inside each child context's config class.

Cost of this constraint inside A: essentially zero. It is good Spring hygiene anyway.

### D7. URL scheme: dual API prefix (webapp-aligned and existing API-client)

The PT REST API is reachable under two prefixes:

- `/physical-tenant/<id>/v2/...` — **webapp/SPA addressing**, introduced by this PoC. Lives
  inside the per-tenant webapp cookie's `Path=/physical-tenant/<id>` scope, so a SPA logged in
  via the webapp chain can call the API directly with the session cookie (no Bearer header
  needed). Resolves [OQ-1](#open-questions).
- `/v2/physical-tenants/<id>/...` — **direct API client addressing**, already established by
  the pre-existing PT REST infrastructure (`PhysicalTenantRequestMappingHandlerMapping`,
  `PhysicalTenantInterceptor`). Outside the cookie's `Path` scope, so cookie auth doesn't
  apply; clients authenticate with `Authorization: Bearer <jwt>`.

Both prefixes hit the same per-tenant API `SecurityFilterChain` — the chain's `securityMatcher`
takes both patterns. The chain accepts session auth (`OAuth2AuthenticationToken` — only present
on the webapp-aligned URL because the cookie isn't sent to the other) OR bearer auth
(`JwtAuthenticationToken` — applies to either URL). The same `PhysicalTenantInterceptor`
resolves the URI template variable `physicalTenantId` for both prefixes and stores the resolved
id in `PhysicalTenantContext`, so downstream services see the tenant regardless of which prefix
the client used. `PhysicalTenantRequestMappingHandlerMapping` registers each
`CamundaRestController`'s `/v2/...` route under both prefixed siblings.

**Why both:** browser-side cookie scoping is the load-bearing isolation primitive (D2), and
keeping it correct argues for the webapp-aligned scheme. The existing API-client scheme stays
because (a) it's already in production-shape code and clients, (b) it's the REST-conventional
shape (`/v2/<resource-collection>/<id>/...`), and (c) honest API clients send bearer tokens
explicitly anyway — they don't need cookies. PoC and production controllers declare their paths
relative to `/v2` only; `PhysicalTenantRequestMappingHandlerMapping` handles the prefixing, so
the dual URL scheme stays an infrastructure concern that never leaks into controller mappings.

Multi-IdP per tenant is exercised by the default tenant (Task 17 / D8). When two or more
`ClientRegistration`s live on a webapp chain, Spring Security's `DefaultLoginPageGeneratingFilter`
renders a picker page listing one `<form action="/oauth2/authorization/<regId>">` per
registration. The picker lives on the chain itself — no separate webapp work, no custom login
template. Picking either registration completes the standard auth-code flow against that IdP.

### D8. Audience-based isolation for shared IdPs

The issuer allowlist ([D6](#d6-design-as-portable-to-c) / per-chain `iss` ∈ tenant's assigned
issuers) separates tenants whose IdPs are distinct. It does **not** separate tenants that share
an IdP — and as soon as a tenant assigns an IdP that another tenant also assigns (the multi-IdP
shape from [D7](#d7-url-scheme-dual-api-prefix-webapp-aligned-and-existing-api-client)
exercised in Task 17, where the default tenant's secondary IdP backs onto the tenanta realm
shared with tenant A), `iss` alone collides.

To separate tenants on a shared IdP we use each provider's existing `audiences` field on CSL's `OidcConfiguration` (the standard Spring/CSL property; no PoC-specific config key) for two coupled responsibilities:

1. **Per-registration token routing.** CSL's stock `IssuerAwareTokenValidator` matches purely by `iss` and serves the FIRST registration sharing that issuer — so when two registrations share a realm, the first one's audience validator fires for both tokens and rejects the other's `aud`. The host overrides CSL's `OidcAccessTokenDecoderFactory` with `IssuerAndAudienceAwareOidcDecoderFactory` (and its delegate `IssuerAndAudienceAwareTokenValidator`): when more than one registration matches `iss`, it picks the registration whose declared `audiences` intersect the token's `aud` claim, then delegates to that registration's per-validator (which, in turn, enforces the audience as part of its standard validation). A token whose `iss` matches but whose `aud` matches no registration is rejected. Validators are cached by the (iss, audiences) pair — the same pair the router uses to pick a registration — so distinct (iss, aud) registrations get distinct cached validators even when they share a registration id.
2. **Per-tenant audience allowlist.** The bearer branch on the per-tenant API chain ANDs an audience check on top of the issuer check — the union of `audiences` over the tenant's `assigned` providers (`ptExpectedAudiencesPerTenant`). An empty union skips the check (back-compat).

Each client at the IdP is configured with a hardcoded audience mapper that emits a tenant-specific `aud` claim on its access tokens. Concretely in the PoC:

|           Client at Keycloak            |   Realm   | Issued to (PT) |         `aud` claim          |
|-----------------------------------------|-----------|----------------|------------------------------|
| `camunda-pt-default-client`             | `default` | `default`      | `pt-default-aud`             |
| `camunda-pt-default-via-tenanta-client` | `tenanta` | `default`      | `pt-default-via-tenanta-aud` |
| `camunda-pt-tenanta-client`             | `tenanta` | `tenanta`      | `pt-tenanta-aud`             |

#### Root declares providers; PTs override per-field including audiences

Root declares each OIDC provider once under `camunda.security.authentication.oidc.*` (default slot, registration id `oidc`) and `camunda.security.authentication.providers.oidc.<id>.*` (named slots). The root entry for any slot holds the **default tenant's view** of that IdP — its client at that realm and the audience the default tenant cares about. Both PTs that use a shared IdP refer to the **same registration id** but may diverge on individual fields under `camunda.physical-tenants.<id>.security.authentication.providers.oidc.<id>.<field>` (or `...oidc.<field>` for the default slot). PT-side non-null/non-empty fields beat root; absent overrides inherit. Concretely in the PoC, root declares `oidc` (default realm) and `tenanta` (the shared tenanta realm, as default sees it: `camunda-pt-default-via-tenanta-client` / `pt-default-via-tenanta-aud`). The tenanta tenant assigns `[tenanta]` and overrides `clientId` / `clientSecret` / `audiences` to `camunda-pt-tenanta-client` / `pt-tenanta-aud`; `issuerUri` inherits from root. The default tenant assigns `[oidc, tenanta]` with no overrides — it uses root's values verbatim. The merge is implemented in `PerTenantClientRegistrations` (helper `resolveMergedProvider` re-used by `ptAllowedIssuersPerTenant` / `ptExpectedAudiencesPerTenant`) and never mutates the root bean.

#### Audiences travel with the ClientRegistration

The host validator factory looks up audiences by registration id, so when two PTs register the same id (`tenanta`) with different audiences, a id-keyed lookup would always reflect ROOT's audiences and silently discard PT-side overrides. To fix this we stash audiences directly on each `ClientRegistration` via the standard `providerConfigurationMetadata` map (key `audiences`, value `List<String>`). PT-side registrations carry their (possibly-overridden) audiences because `PerTenantClientRegistrations#buildRegistration` writes the merged audiences into the builder's metadata. Root-side registrations carry their audiences because `OidcOverrideBeansConfiguration#enhanceWithAudiencesMetadata` walks each root registration produced by `ClientRegistrationFactory` and rebuilds it via `ClientRegistration.withClientRegistration(...).providerConfigurationMetadata(...)` to include the audiences from the corresponding `OidcConfiguration`. The metadata is preserved across `withClientRegistration` builder copies, so existing entries (e.g. `end_session_endpoint`) are not lost.

A custom `MetadataAwareTokenValidatorFactory` (subclass of the host's stock `TokenValidatorFactory`) composes the per-registration validator by reading audiences from the registration's metadata rather than from the id-keyed root map. It mirrors the host's existing composition order — `JwtTimestampValidator` (always), `AudienceValidator` (when metadata declares audiences), SaaS `OrganizationValidator`/`ClusterValidator` (when SaaS is configured). `IssuerAndAudienceAwareTokenValidator#audiencesOf` reads from the same metadata key, so the router uses one source of truth for picking a registration and the validator factory uses the same source of truth for composing the audience validator.

#### addPtRegistrations no longer dedups by id

The cluster-shared decoder receives the LIST of all registrations across root + all PTs. Two PTs registering the same id with different audiences are logically distinct (the router picks by (iss, aud), not by id), so dedup-by-id would silently drop a PT's override. `addPtRegistrations` therefore appends every PT registration without dedup. This affects only the shared decoder's validator list — each PT's own `InMemoryClientRegistrationRepository` is independent and still holds at most one entry per id.

The bearer branch on the per-tenant API chain now performs two allowlist checks:

- `iss` claim ∈ tenant's allowed-issuers (existing). Empty / unmatched ⇒ deny.
- `aud` claim ∩ tenant's expected-audiences ≠ ∅ (new). Empty union (no provider in `assigned` declares `audiences`) means "skip the audience check" (back-compat for pre-Task-17 setups without audience mappers); non-empty union requires intersection.

Cross-tenant bearer access on a shared IdP fails on the audience check even when issuer would have matched. A token issued by tenant A's client (`aud=pt-tenanta-aud`) presented to default's API chain is rejected (default's union is `{pt-default-aud, pt-default-via-tenanta-aud}`), and vice versa.

Session-derived auth (`OAuth2AuthenticationToken`) on the API chain is **not** re-audience-checked. The per-tenant `ClientRegistrationRepository` on the webapp chain only knows about that tenant's assigned providers, so a session can only be created via a client this tenant owns — the audience claim was implicitly validated at the underlying access token's issuance. Adding an explicit `OAuth2AuthenticationToken.getAuthorizedClientRegistrationId()` check against the tenant's registration ids would be strictly redundant given that scoping.

The audience-allowlist set is built as a sibling Spring bean to `ptAllowedIssuersPerTenant` (`Map<String, Set<String>> ptExpectedAudiencesPerTenant`) in `OidcOverrideBeansConfiguration` — both iterate the same per-tenant `assigned` list and the same `PerTenantClientRegistrations.resolveMergedProvider` helper, just collecting `getIssuerUri()` vs `getAudiences()`. Both maps flow through `PhysicalTenantSecurityChainRegistrar` into `PerTenantSecurityChainFactory.buildApiChain`. Nothing about D8 changes the chain shape, the session machinery, or the URL scheme — it is the audience-via-metadata change above plus the validator override that together make per-registration audience overrides viable when two registrations share both an issuer and a registration id.

---

## Component design

```
┌──────────────────────────────────────────────────────────────────────┐
│                  PhysicalTenantSecurityConfiguration                 │
│  iterates PhysicalTenantResolver.getAll(), builds slices, wires:     │
└─────────────────────────────────┬────────────────────────────────────┘
                                  │
   ┌──────────────────────────────┼─────────────────────────────────────┐
   │                              │                                     │
per-tenant beans            per-tenant chains                  cluster-shared beans
   │                              │                                     │
   ▼                              ▼                                     ▼
┌────────────────────────┐  ┌────────────────────────────┐  ┌─────────────────────────────────┐
│ ClientRegistration-    │  │ webapp chain               │  │ Issuer-aware JwtDecoder         │
│ Repository             │  │   /physical-tenant/{t}/**  │  │   (union of all tenants'        │
│   (only providers      │  │   oauth2Login              │  │    issuer URIs; per-issuer      │
│    'assigned' to {t})  │  │   cookie name+Path per {t} │  │    validators)                  │
└────────────────────────┘  ├────────────────────────────┤  │                                 │
┌────────────────────────┐  │ api chain                  │  │ CamundaOidcLogoutSuccessHandler │
│ SessionRepositoryFilter│  │   /physical-tenant/{t}/v2/ │  │   (reads tenant IdP from active │
│   + CookieHttpSession- │  │   {t}/**                   │  │    session; OQ-3)               │
│     IdResolver         │  │   resource-server JWT      │  │                                 │
│     (name+Path per {t})│  │   iss allowlist:           │  │ HttpSessionOAuth2Authorized-    │
│   + WebSessionRepo({t})│  │     {t}'s assigned issuers │  │ ClientRepository                │
│     bound to {t}'s     │  │   no session               │  │   (Spring default; session-     │
│     dedicated          │  └────────────────────────────┘  │    derived)                     │
│     secondary storage  │                                  │                                 │
└────────────────────────┘                                  │ SecurityContextRepository       │
                                                            │   (Spring default; reads from   │
                                                            │    the per-chain session)       │
                                                            │                                 │
                                                            │ JwsKeySelectorFactory,          │
                                                            │ AssertionJwkProvider,           │
                                                            │ OidcUserInfoClient              │
                                                            │   (shared today; unchanged)     │
                                                            └─────────────────────────────────┘
```

### Bean catalogue

New classes / configurations introduced by the PoC:

|                                Class                                 |                                                                                                                                                                                                                                                                      Responsibility                                                                                                                                                                                                                                                                      |
|----------------------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `PhysicalTenantSecurityConfiguration` (`authentication/.../config/`) | Top-level `@Configuration`, profile-gated. Pulls `PhysicalTenantResolver`, iterates tenants, exposes `SecurityFilterChain` beans via `BeanDefinitionRegistryPostProcessor`.                                                                                                                                                                                                                                                                                                                                                                              |
| `PerTenantSecurityChainFactory`                                      | Builds the pair of `SecurityFilterChain` (webapp + api) for one tenant. Takes a `TenantSecuritySlice` and produces the chains.                                                                                                                                                                                                                                                                                                                                                                                                                           |
| `TenantSecuritySlice` (record)                                       | Per-tenant collaborator bundle: tenant id, access path, `ClientRegistrationRepository`, `SessionRepositoryFilter`, `CookieHttpSessionIdResolver`. Built once per tenant at startup. `JwtDecoder`, `LogoutSuccessHandler`, `AuthorizedClientRepository`, `SecurityContextRepository` are intentionally NOT in the slice — they are cluster-shared (issuer-aware decoder validates any known issuer; the chains' per-tenant authorization rule enforces `iss` ∈ tenant's assigned issuers; logout/authorized-client/security-context are session-derived). |
| `PerTenantOidcRegistry`                                              | Builds the per-tenant `OidcAuthenticationConfigurationRepository` by applying `providers.assigned` to the tenant's OIDC providers map.                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `PhysicalTenantRedirectUriRewriter`                                  | Stamps the per-tenant prefix into each `ClientRegistration.redirectUri` template at registration build time.                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `PhysicalTenantWebSessionRepositoryConfiguration` (in `dist/`)       | `@Conditional(PhysicalTenantsConfiguredCondition.class)` nested `@Configuration` exposing `Map<String, WebSessionRepository>` keyed by tenant id. Lives in `dist/` because it needs `PhysicalTenantResolver` (not on `authentication/`'s classpath). The chain config injects this map directly — no registry wrapper class.                                                                                                                                                                                                                            |
| `InMemoryPersistentWebSessionClient` (in `dist/`)                    | Per-tenant `PersistentWebSessionClient` backed by a private `ConcurrentHashMap`. One instance per tenant; structurally unreachable from other tenants. PoC scope only — see "Out of scope" below for the durable-storage follow-up.                                                                                                                                                                                                                                                                                                                      |
| `PhysicalTenantCookieSerializer` (helper)                            | Produces a Spring Session `CookieSerializer` configured with the tenant's cookie name + Path for use by `CookieHttpSessionIdResolver`.                                                                                                                                                                                                                                                                                                                                                                                                                   |

Reused as-is:

- `OidcAuthenticationConfigurationRepository`, `ClientRegistrationFactory`
- `ClientAwareOAuth2AuthorizationRequestResolver` (already supports custom base URI)
- `OidcIdTokenDecoderFactory`, `TokenValidatorFactory`, `JWSKeySelectorFactory`
- `CamundaOidcLogoutSuccessHandler`
- `WebSessionRepository`, `WebSessionMapper`, `PersistentWebSessionClient`
- `PhysicalTenantResolver`, `PhysicalTenantContext`, `PhysicalTenantRegistry`

### Filter chain shape (per tenant)

Webapp chain (`/physical-tenant/{t}/**`):
- `oauth2Login()`:
- `authorizationEndpoint().baseUri("/physical-tenant/{t}/oauth2/authorization")`
- `redirectionEndpoint().baseUri("/physical-tenant/{t}/login/oauth2/code/*")`
- `loginPage("/physical-tenant/{t}/login")` (multi-provider picker; for single-provider tenant, redirect straight to `oauth2/authorization/{regId}`)
- `csrf()` with `CookieCsrfTokenRepository` (Path=tenant path)
- `securityContext().securityContextRepository(perTenantContextRepo)`
- `sessionManagement().sessionFixation().newSession()` and `maximumSessions(...)` left at defaults for the PoC
- `logout()` with per-tenant `LogoutSuccessHandler` and `deleteCookies("camunda-session-{t}", "X-CSRF-TOKEN-{t}")`
- `addFilterBefore(perTenantSessionRepositoryFilter, …)` so each chain pulls its own `SessionRepository`

API chain (`/physical-tenant/{t}/v2/**`) — session-or-bearer:
- `oauth2ResourceServer().jwt().decoder(perTenantJwtDecoder)` — bearer-token clients
- `addFilterBefore(perTenantSessionRepositoryFilter, SecurityContextHolderFilter.class)` — same per-tenant filter instance as the webapp chain, so the `SecurityContext` saved at OAuth2 login is restored for SPA flows that send only the session cookie
- `sessionCreationPolicy(STATELESS)` — the chain reads sessions but never creates or modifies one (login still happens exclusively on the webapp chain)
- Authorization rule: `OAuth2AuthenticationToken` (session-derived) ⇒ allow; `JwtAuthenticationToken` (bearer) ⇒ apply the per-chain issuer allowlist; anything else (anonymous) ⇒ deny, which `oauth2ResourceServer`'s entry point turns into 401 with `WWW-Authenticate: Bearer`
- CSRF off (API surface)
- **Chain order**: this API matcher is a sub-pattern of the webapp matcher, so the API chain `@Bean` is `@Order`ed before the webapp chain to win the match for `/physical-tenant/{t}/v2/...`

Default tenant gets a duplicate of each chain with the unprefixed `securityMatcher`s (`/<webcomponent>/**`, `/v2/**`) and cookie `Path=/`. These are wired from the same `TenantSecuritySlice` so OIDC config is shared.

### Storage isolation

Each tenant has a dedicated `PersistentWebSessionClient` instance with a private `ConcurrentHashMap`. The `PhysicalTenantWebSessionRepositoryConfiguration` in `dist/` builds a `Map<String, WebSessionRepository>` keyed by tenant id and exposes it as a single bean; the chain config injects the map and looks the right instance up per chain. Tenant A's sessions live in tenant A's map; tenant B's never touch it. No shared backend, no key-prefixing, no cross-tenant lookup path — isolation is structural. Sessions die with the process; per-tenant *durable* storage is intentionally out of scope (see "Out of scope" below).

### Configuration consumed

```yaml
camunda:
  security:
    authentication:
      method: oidc            # not overridable per PT (validated at startup)
      oidc: { ... }           # cluster-wide default OIDC provider (implicit registration id "oidc")
  physical-tenants:
    tenanta:
      security:
        authentication:
          # Option A: override the default "oidc" provider for this tenant
          oidc:
            client-id: ...
            client-secret: ...
            issuer-uri: ...
          # Option B: define one or more named providers
          providers:
            assigned: ["oidc", "idpOne"]   # references both the default slot and a named provider
            oidc:
              idpOne:
                client-id: ...
                client-secret: ...
                issuer-uri: ...
    default:
      security:
        authentication:
          oidc:
            client-id: ...
            issuer-uri: ...
          providers:
            assigned: ["oidc"]
```

`providers.assigned` is the **new property** this PoC consumes. Resolution rule for each entry `id` in `assigned`:
- If `id == "oidc"`: resolves to `authentication.oidc.*` (the default provider slot). Must be configured.
- Otherwise: resolves to `authentication.providers.oidc.<id>.*` (a named provider slot). Must exist under that key.

A missing or unconfigured provider fails startup with an explicit error.

The `cluster-admin` block, CSRF, multi-tenancy, and `http-headers` are out of scope for the PoC and remain global (validated as non-overridable per the existing rules in #52680).

### Multi-IdP per tenant

`providers.assigned` is a list, not a singleton — a tenant can declare more than one provider. The PoC exercises this with the **default tenant assigning both its primary IdP and a second one**:

```yaml
camunda:
  physical-tenants:
    default:
      security:
        authentication:
          oidc:                      # default slot — registration id "oidc"
            client-id: camunda-pt-default-client
            issuer-uri: http://localhost:8081/realms/default
          providers:
            assigned: [oidc, defaultIdpAlt]
            oidc:
              defaultIdpAlt:         # named slot — registration id "defaultIdpAlt"
                client-id: camunda-pt-default-alt-client
                issuer-uri: http://localhost:8082/realms/tenanta
```

When a chain's `ClientRegistrationRepository` contains more than one registration, Spring Security's `oauth2Login()` does **not** redirect unauthenticated requests directly to an IdP. Instead it sends the user to the chain's login page (`<tenant-prefix>/login`), which `DefaultLoginPageGeneratingFilter` renders with one clickable link per registration (`<tenant-prefix>/oauth2/authorization/<registrationId>`). The user picks; the standard OAuth flow proceeds against their choice. The picker page itself is served by the chain — no separate webapp work.

This shape is what the PoC needs to demonstrate: tenants can have any number of IdPs assigned, and `oauth2Login`'s default behavior with multiple registrations gives the picker for free.

---

## End-to-end demo path

The PoC does **not** require a working OC frontend bundle. The OC webapp ships with absolute paths (`/v2/…`, `/<webcomponent>/…`) and would need either `<base href>` injection at serve time or a publicPath-aware rebuild to live under `/physical-tenant/<id>/…`. That is webapp work, separate from security; out of scope here.

Instead, the PoC ships a minimal PT-scoped controller `PhysicalTenantWhoamiController` that exposes:

- `GET /physical-tenant/{t}/whoami` (webapp chain — session/OIDC login)
- `GET /physical-tenant/{t}/v2/whoami` (api chain — session cookie or bearer token)

Both return `{ tenantId, principal, providers, accessPath }`. That is sufficient to demonstrate login flow, session isolation, multi-IdP, and tenant resolution.

Demo:

1. Start OC with the config above (two tenants, two IdPs — Keycloak realms `default` and `tenanta`). The presence of `camunda.physical-tenants.*` entries activates the PT chains; no separate profile is needed.
2. Browser → `https://localhost:8080/physical-tenant/tenanta/whoami`.
3. PT webapp chain matches; user is unauthenticated; redirect to `/physical-tenant/tenanta/oauth2/authorization/tenanta` → tenant A's Keycloak (tenant A binds its Keycloak realm to a *named* OIDC provider `tenanta` under `authentication.providers.oidc.tenanta.*`).
4. Login → callback at `/physical-tenant/tenanta/login/oauth2/code/tenanta` → session created with cookie `camunda-session-tenanta; Path=/physical-tenant/tenanta`. `whoami` returns `{tenantId:"tenanta", principal:"bob@tenanta", providers:["tenanta"], accessPath:"prefixed"}`.
5. New tab → `https://localhost:8080/physical-tenant/default/whoami` — different cookie scope, unauthenticated, redirected to `/physical-tenant/default/oauth2/authorization/oidc` (the default tenant binds to the cluster-default `authentication.oidc.*` slot, registration id `oidc`). The PoC exercises both resolution paths — default-slot for the default tenant, named-slot for tenant A.
6. Both tabs hold valid, independent sessions simultaneously.
7. Tab 1 logs out (`POST /physical-tenant/tenanta/logout`) — only tenant A's session is invalidated; tab 2 stays logged in.
8. (Default-tenant access-path check) Open `https://localhost:8080/whoami` — different cookie Path, unauthenticated, fresh login flow against the default IdP. Confirms the agreed "relogin acceptable across access paths" behaviour.
9. (Multi-IdP picker, Task 17 / D8) The default tenant is configured with two assigned providers — `oidc` (its primary IdP, default realm at `:8081`) and `tenanta` (the tenanta realm at `:8082` shared with tenant A; root declares it with default's client `camunda-pt-default-via-tenanta-client` and audience `pt-default-via-tenanta-aud`). Open `https://localhost:8080/app`. The chain has two `ClientRegistration`s, so `DefaultLoginPageGeneratingFilter` renders a picker page listing both options as `/oauth2/authorization/<regId>` links. Picking `tenanta` routes to the tenanta realm; login as `bob`; return to `/app` with `bob` as the default tenant's session principal. The picker is exercised on the unprefixed access path so the URL matches what a real Camunda webapp user would see.
10. (Audience-isolation cross-tenant bearer, D8) Mint a token via `camunda-pt-default-via-tenanta-client` (realm tenanta, audience `pt-default-via-tenanta-aud`). Present it to default's API → **200** (audience matches default's expected list). Present the same token to tenant A's API → **403** (issuer would match tenant A's allowlist — same realm — but the audience claim is NOT in tenant A's expected list). This is the case the audience allowlist is needed for; the issuer allowlist alone would have admitted the cross-tenant token.

Verification points:
- Browser cookie inspector shows two cookies with disjoint `Path` attributes.
- Each tenant's session entities live in that tenant's own in-memory map (private to its `WebSessionRepository`). No row-level partitioning, no shared backend. Durable per-tenant storage is intentionally out of scope.
- Replaying tenant A's OIDC `state` parameter against `/physical-tenant/default/login/oauth2/code/oidc` is rejected (per-chain `OAuth2AuthorizationRequestRepository` keys state in tenant A's session only).
- The `/physical-tenant/tenanta/v2/whoami` endpoint accepts the per-tenant session cookie (SPA flow) or a Bearer token whose `iss` is in tenant A's allowed-issuer set (the issuer of tenant A's named `tenanta` provider). A token issued by the default tenant's `oidc` IdP returns **403** (signature is valid against the shared decoder, but the issuer allowlist on tenant A's chain rejects it).
- **Multi-IdP picker**: when the default tenant's `providers.assigned` contains more than one provider, hitting `/physical-tenant/default/whoami` unauthenticated redirects to `/physical-tenant/default/login` (Spring Security's default picker URL) — NOT directly to either IdP. The picker page lists each assigned registration as a clickable link, and clicking either completes a full OAuth flow against the chosen IdP.

---

## Local testing setup

Two Keycloak instances stood up via `dasniko/testcontainers-keycloak` — the same Testcontainers library the existing OIDC tests in this repo use (`OidcAuthOverRestIT`, `SecurityHeadersOidcIT`, `PrivateKeyJwtTest`, `DefaultTestContainers.createDefaultKeycloak()`). Reusing the established pattern means no new dependency, no Docker-vs-in-process trade-off discussion, and IT and local-dev share the same IdP shape.

Two complementary entry points:

### 1. Standalone local runner (developer iteration loop)

`dist/src/test/java/io/camunda/application/pt/PtPocLocalIdpRunner.java` — a `main()` that:

- Boots two `KeycloakContainer` instances on fixed host ports (`8081`, `8082`) so the OC config can use stable issuer URIs.
- Imports realm JSON from `dist/src/test/resources/pt-poc/default-realm.json` and `tenanta-realm.json`.
- Prints both issuer URIs and the test user credentials to stdout.
- Blocks on stdin so the dev can leave it running across many OC restarts.

Realm contents:

|                    Realm                    |          Client id          |       Test users        |
|---------------------------------------------|-----------------------------|-------------------------|
| `default` (`localhost:8081/realms/default`) | `camunda-pt-default-client` | `alice@default / alice` |
| `tenanta` (`localhost:8082/realms/tenanta`) | `camunda-pt-tenanta-client` | `bob@tenanta / bob`     |

Both clients pre-configured with redirect URIs `http://localhost:8080/physical-tenant/<id>/login/oauth2/code/*` plus the unprefixed `http://localhost:8080/login/oauth2/code/*` for the default tenant's secondary access path. Standard auth-code flow, `client_secret_basic`, no PKCE requirement (PoC scope; PKCE is a follow-up).

### 2. Bundled config for the OC process

`dist/src/main/resources/application-pt-poc.yaml` — activates with `--spring.profiles.active=pt-poc` (which expands to `consolidated-auth,rdbmsH2` via `spring.profiles.group` in `application.properties`) and references the local Keycloak issuer URIs by default (overridable via standard Spring property indirection). Contains the two-tenant config from the [Configuration consumed](#configuration-consumed) section pre-filled for the local runner's realms; the presence of those entries is what activates the PT chains.

### 3. Integration test (CI verification)

`dist/src/test/java/io/camunda/application/pt/PhysicalTenantSecurityIT.java` — a single IT that:

- Boots two `KeycloakContainer`s (random ports, isolated per-test).
- Boots OC in-JVM with the `consolidated-auth` profile and tenant config wired to those issuer URIs (the tenant config alone activates the PT chains).
- Drives the OIDC flow via a programmatic `RestClient` that handles the `302` → IdP login form → `302` callback chain (the pattern used in `OidcAuthOverRestIT`).
- Asserts each [verification point](#end-to-end-demo-path) above.

Both tenants live in a single IT so cross-tenant-leakage assertions (cookie path scoping, state-parameter cross-replay rejection, bearer-token cross-issuance rejection) can run against a single hot OC.

### Why Keycloak rather than an in-process mock

OC's OIDC stack includes Camunda-specific machinery — `IssuerAwareJWSKeySelector`, `OidcAccessTokenDecoderFactory`, `private_key_jwt` support, `CachingOidcClaimsProvider`. We want the PoC exercised against a real OIDC implementation so we don't bury bugs behind a mock that almost-implements the spec. The startup cost (~5–10s once per dev session) is paid against a long-lived IdP container.

If iteration speed becomes the bottleneck, swapping to `no.nav.security:mock-oauth2-server` is a contained change — it speaks the same OIDC protocol and the PoC's chain factory has no Keycloak-specific dependency.

---

## Out of scope (PoC follow-ups)

- Basic-auth method per PT.
- `cluster-admin` block (separate concern from tenant chains).
- Webapp UI affordances (logo, tenant name in nav). PoC ships the URL routing and security; the bundled webapp keeps current UX.
- Migration of cookie `name` and `Path` for non-PT users — backwards compat for existing deployments stays on the default tenant's unprefixed chains with `Path=/` and existing cookie name.
- Per-PT *durable* secondary storage for the session repository. The PoC uses in-memory per-tenant `PersistentWebSessionClient` instances. Wiring per-tenant RDBMS/search (per-tenant `SqlSessionFactory` + `PersistentWebSessionMapper`, or per-tenant `SearchClients`-backed `PersistentWebSessionSearchImpl`) is a separate follow-up: it requires fanning out `MyBatisConfiguration` (or its search counterpart) per tenant and adding per-tenant rdbms overlays in `application-pt-poc.yaml`. That work is unrelated to the security wiring this PoC validates.
- Forbidden-override validation (#52680) for the PT properties this PoC touches.
- The `PhysicalTenantLoginPageController` was removed. Unprefixed `/login` is served by CSL's `OidcWebapp` chain whose `DefaultLoginPageGeneratingFilter` lists every registration in the standard `ClientRegistrationRepository` — meaning the unprefixed picker is unfiltered and does not consult `providers.assigned`. The prefixed PT entry point always redirects to the first assigned registration's authorization URL (no picker on prefixed paths). The earlier `setLoginPageUrl` post-build attempt on the PT chain was a no-op because `DefaultLoginPageGeneratingFilter` is not installed on the PT chains we register — investigating why `DefaultLoginPageConfigurer` is not auto-applied to the prototype `HttpSecurity` bean the PT chain factory receives is recorded as a follow-up (relates to Task 19). Real production impl that wants per-PT IdP filtering on the picker will need either an explicit picker filter on the PT chain or CSL to expose a per-chain login page URL via configuration.

---

## Open questions

**OQ-1.** Single cookie covering both the webapp and the api path for a tenant (Path = `/`-rooted at a common ancestor like `/physical-tenant/tenanta`, with the API path mounted underneath), or two cookies? **Resolved:** one webapp cookie scoped to `Path=/physical-tenant/<t>`, with a new webapp-aligned API URL `/physical-tenant/<t>/v2/...` mounted under that path so the cookie scope covers it. The pre-existing API-client URL `/v2/physical-tenants/<t>/...` stays supported alongside (bearer-only) — see [D7](#d7-url-scheme-dual-api-prefix-webapp-aligned-and-existing-api-client). The API chain matches BOTH URL patterns via `securityMatcher(String...)` and is session-or-bearer: it installs the same per-tenant `SessionRepositoryFilter` instance the webapp chain uses (against the same per-tenant `WebSessionRepository`) and its authorization manager admits `OAuth2AuthenticationToken` (session, login already validated on the same tenant's webapp chain — fires only on the webapp-aligned URL because the cookie doesn't reach the API-client URL) or `JwtAuthenticationToken` (bearer, allowlist-checked — applies to either URL). `oauth2ResourceServer.jwt()` stays wired so non-browser API clients are unaffected and unauthenticated requests still get 401 with `WWW-Authenticate: Bearer` via that entry point. Chain registration order: the API chain is `@Order`ed before the webapp chain because the webapp-aligned API matcher (`/physical-tenant/<t>/v2/**`) is a sub-pattern of the webapp matcher (`/physical-tenant/<t>/**`).

**OQ-2.** The default tenant's unprefixed chains: do we ever want the same session to be valid on both access paths, or always treat them as separate? **Resolved:** the unprefixed default access path is served by CSL's standard chains (Stage 2), which use the standard `camunda-session` cookie at CSL's standard Path. The prefixed PT default chain uses `camunda-session-default` at `Path=/physical-tenant/default`. Different cookie names → different sessions; switching access paths forces a re-auth, per requirement 3. The previously documented `Path=/` side effect (the unprefixed-default cookie surfacing on every request to the host) is also resolved: CSL's catch-all at `@Order(2)` is `denyAll → 404`, not an OAuth2-redirect-with-cookie, so background fetches of random URLs no longer seed an unprefixed session cookie.

**OQ-3.** Logout: do we tear down the session at the OIDC provider (`OidcClientInitiatedLogoutSuccessHandler` behaviour) or only locally? **PoC stance:** reuse `CamundaOidcLogoutSuccessHandler` unchanged — IdP-initiated logout on the tenant's IdP only.

**OQ-4.** Path-pattern collision: a tenant id literally named `physical-tenants` (API path) or `physical-tenant` (webapp path) would collide. **Stance:** `PhysicalTenantResolver` already restricts to `[a-z0-9]+`, so these are unreachable. Validated.

**OQ-5.** Per-PT Spring sub-`ApplicationContext`s — would isolating per-tenant beans inside their own child context be a stronger primitive than the current `Map<String, T>` injection pattern? Per-PT context would let each PT's `ClientRegistrationRepository`, `WebSessionRepository`, allowed-issuer `Set`, etc. live as plain singletons; cross-tenant injection becomes structurally impossible at the bean-resolution layer. **PoC stance:** stay with `Map<String, T>` — three per-PT beans is below the threshold where sub-contexts pay off. **Follow-up:** worth a separate design spike against the broader PT roadmap (data-layer / service-layer / scheduler isolation), including how Spring Security 7 handles child-context `SecurityFilterChain` collection, where cross-cutting beans (union-of-issuers decoder) live, and what the per-tenant lifecycle/refresh story looks like.
