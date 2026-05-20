# Physical-Tenant Spring Security PoC — Design

**Status:** Draft for review
**Scope:** OC (`dist/`) application only. Optimize, Operate, Tasklist out of scope.
**Audience:** sebastian.bathke@camunda.com / identity squad.

---

## Context

Camunda 8.10 introduces the **Physical Tenant (PT)** concept: a single OC process serves multiple tenants behind dedicated URL prefixes, each with its own OIDC providers, sessions, and (eventually) secondary storage. PR #52529 plus the follow-ups on `identity-pt-poc` land the PT plumbing on the REST side (`PhysicalTenantRequestMappingHandlerMapping`, `PhysicalTenantInterceptor`, `PhysicalTenantContext`, `PhysicalTenantResolver`). The Spring Security half does not exist yet — that is the gap this PoC closes.

URL scheme (final):

| Surface | Prefixed | Default-only fallback |
|---|---|---|
| REST API | `/v2/physical-tenants/<id>/...` | `/v2/...` |
| Webapps | `/physical-tenant/<id>/<webcomponent>/...` | `/<webcomponent>/...` |

Requirements:

1. Each PT has an isolated Spring Security setup: its own OIDC providers, its own `authorization_code` flow, its own session.
2. Logging in on PT *A* must not authenticate the user against PT *B*. A single browser can hold N PT sessions simultaneously.
3. The special `default` PT is reachable via both prefixed and unprefixed paths. We accept that switching access path may force a re-login (simplification agreed by the user).
4. PT session storage is bound to a per-PT secondary storage (already routed for RDBMS via `0626a6540c9`).

---

## Architectural decisions

### D1. Use Spring Security's `SecurityFilterChain` as the isolation primitive

Spring Security's filter chain is already a per-`securityMatcher` unit with its own auth-state collaborators (`ClientRegistrationRepository`, `OAuth2AuthorizedClientRepository`, `SecurityContextRepository`, `LogoutSuccessHandler`, …). Multiplying this per tenant gives us isolation without inventing new mechanisms.

**Reject** the alternative of one chain set with runtime tenant-resolving wrappers: it forces a single session cookie at the servlet level and pushes tenant attribute keys into the same `HttpSession`, which is exactly the cross-bleed surface we want to avoid.

**Defer** the alternative of per-tenant child Spring contexts + per-tenant `DispatcherServlet`s (call it approach C). C buys genuinely stronger server-side isolation — bean graph, property binding, and configuration-error containment all become structural rather than disciplinary — but does not add to browser-side isolation, which still rides on cookie `Path`. For a PoC whose job is to validate the cookie-`Path` hypothesis, C's ~2× code cost and the unretired risk of CSL having hidden root-context assumptions (auto-configuration is loaded at the root by Spring Boot and would need explicit `@Import` into each child plus verification of any static/listener-based wiring) is not warranted. Graduating to C is a viable future step; see the portability constraint in [D6](#d6-design-as-portable-to-c).

### D2. Use cookie `Path` attribute as the browser-side isolation primitive

For each PT we emit a session cookie scoped to the PT's path. The browser then refuses to send tenant A's cookie on tenant B's URLs — no server-side tenant fan-out required. The cookie set per chain:

- `name=camunda-session-<tenantId>` (avoids name collisions when multiple chains' cookies could overlap on the default tenant's `/`)
- `Path=/physical-tenant/<tenantId>` (and `/v2/physical-tenants/<tenantId>` for the API chain, or one cookie covering both via a common ancestor — see [Open question OQ-1](#open-questions))
- For the default tenant's unprefixed chains: `Path=/` and `Path=/v2`. A user navigating from `/physical-tenant/default/…` to `/…` sees a different cookie and will re-auth. Per requirement-3 this is acceptable.

CSRF cookies follow the same Path scoping.

### D3. Profile-gate the new chain set, opt out of CSL chains for PT paths

The library (CSL) is a singleton-style auto-config that assumes one chain per (auth-method × api/webapp). We don't fight CSL on this. Instead:

- Introduce a Spring profile `pt-security` that:
  - Excludes `CamundaSecurityAutoConfiguration` from the import (or makes our chains take precedence by `@Order`).
  - Activates `PhysicalTenantSecurityConfiguration` (new).
- Without `pt-security`, OC boots unchanged. With `pt-security`, our chains take over.

This keeps the blast radius small and the rollback path obvious.

### D4. Reuse OC's OIDC machinery; bind it per tenant

OC already owns the OIDC primitives needed (`OidcAuthenticationConfigurationRepository`, `ClientRegistrationFactory`, `ClientAwareOAuth2AuthorizationRequestResolver`, `OidcIdTokenDecoderFactory`, `TokenValidatorFactory`, `IssuerAwareJWSKeySelector`, `CamundaOidcLogoutSuccessHandler`). The PoC does not rebuild any of these — it parameterises them per tenant by feeding the tenant's `SecurityConfiguration` slice.

### D5. Tenant resolution at the chain-matcher level

Each chain matches a fixed-prefix `securityMatcher` containing the tenant id literal. No per-request "which tenant am I" lookup inside a filter is required for the security stack itself — the matcher resolves it. Application code keeps reading `PhysicalTenantContext.current()` (already populated by the existing `PhysicalTenantInterceptor` for REST). For webapp paths we add a parallel `PhysicalTenantWebInterceptor` that does the same thing for `/physical-tenant/<id>/<webcomponent>/…`.

### D6. Design as portable to approach C

We don't build C now, but we deliberately keep the per-tenant chain factory portable to a child-context model so the migration cost is bounded if C becomes warranted later. Concretely:

- `PerTenantSecurityChainFactory` and `TenantSecuritySlice` take all dependencies as constructor arguments. No `@Autowired` field injection from a root context. No `@ConditionalOnMissingBean` games between tenant chains.
- Per-tenant bean names are derived from the tenant id and never collide with a "global" bean name CSL would create. This keeps each tenant's bean graph addressable as a unit.
- `PhysicalTenantSecurityConfiguration` is the only place that touches `PhysicalTenantResolver.getAll()` and root-context bean registration. To graduate to C, this class is replaced; the chain factory and slice are reused verbatim inside each child context's config class.

Cost of this constraint inside A: essentially zero. It is good Spring hygiene anyway.

---

## Component design

```
                          ┌─────────────────────────────────────────────────────────┐
                          │              PhysicalTenantSecurityConfiguration        │
                          │  iterates PhysicalTenantResolver.getAll(), registers:   │
                          └────────────────────────────┬────────────────────────────┘
                                                       │
                ┌──────────────────────────────────────┼──────────────────────────────────────┐
                │                                      │                                      │
        per-tenant beans                       per-tenant chains                      shared beans
                │                                      │                                      │
                ▼                                      ▼                                      ▼
  ┌──────────────────────────────┐    ┌────────────────────────────────┐    ┌─────────────────────────────┐
  │ PerTenantOidc                │    │ webapp chain                   │    │ JwsKeySelectorFactory       │
  │   • ClientRegistrationRepo   │    │   securityMatcher: prefix      │    │ (already shared today)      │
  │   • AuthorizedClientRepo     │    │   loginPage:                   │    │                             │
  │   • TokenClaimsConverter     │    │     /physical-tenant/{t}/login │    │ AssertionJwkProvider        │
  │   • LogoutSuccessHandler     │    │   redirect-uri template:       │    │   (cached, multi-tenant)    │
  │   • JwtDecoder               │    │     /physical-tenant/{t}/      │    │                             │
  │   • SecurityContextRepo      │    │     login/oauth2/code/{regId}  │    │ OidcUserInfoClient          │
  │                              │    ├────────────────────────────────┤    │                             │
  │                              │    │ api chain                      │    └─────────────────────────────┘
  │                              │    │   securityMatcher:             │
  │                              │    │     /v2/physical-tenants/{t}/* │
  │                              │    │   resource-server JwtDecoder   │
  │                              │    │   no session                   │
  │                              │    └────────────────────────────────┘
  │ PerTenantSession             │
  │   • WebSessionRepository(t)  │
  │     keyspace-prefixed        │
  │   • CookieHttpSessionIdRes.  │
  │     name=camunda-session-{t} │
  │     path=/physical-tenant/{t}│
  └──────────────────────────────┘
```

### Bean catalogue

New classes / configurations introduced by the PoC:

| Class | Responsibility |
|---|---|
| `PhysicalTenantSecurityConfiguration` (`authentication/.../config/`) | Top-level `@Configuration`, profile-gated. Pulls `PhysicalTenantResolver`, iterates tenants, exposes `SecurityFilterChain` beans via `BeanDefinitionRegistryPostProcessor`. |
| `PerTenantSecurityChainFactory` | Builds the pair of `SecurityFilterChain` (webapp + api) for one tenant. Takes a `TenantSecuritySlice` and produces the chains. |
| `TenantSecuritySlice` (record) | Per-tenant collaborator bundle: tenant id, `SecurityConfiguration` slice, `ClientRegistrationRepository`, `OAuth2AuthorizedClientRepository`, `JwtDecoder`, `OidcClaimsProvider`, `OAuth2AuthorizationRequestResolver`, `LogoutSuccessHandler`, `SessionRepository<WebSession>`, `CookieHttpSessionIdResolver`. Built once per tenant at startup. |
| `PerTenantOidcRegistry` | Builds the per-tenant `OidcAuthenticationConfigurationRepository` by applying `providers.assigned` to the tenant's OIDC providers map. |
| `PhysicalTenantRedirectUriRewriter` | Stamps the per-tenant prefix into each `ClientRegistration.redirectUri` template at registration build time. |
| `PerTenantSessionRepository` | Decorator over the existing `WebSessionRepository`; prefixes session ids with `t:` to keep keyspaces disjoint in shared secondary storage. |
| `PhysicalTenantCookieSerializer` (helper) | Produces a Spring Session `CookieSerializer` configured with the tenant's cookie name + Path for use by `CookieHttpSessionIdResolver`. |
| `PhysicalTenantWebInterceptor` | Webapp counterpart to `PhysicalTenantInterceptor` — resolves the tenant id from `/physical-tenant/<id>/…` and pushes it onto `PhysicalTenantContext`. |

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

API chain (`/v2/physical-tenants/{t}/**`):
- `oauth2ResourceServer().jwt().decoder(perTenantJwtDecoder)`
- `sessionCreationPolicy(NEVER)`
- CSRF off (Bearer token only)

Default tenant gets a duplicate of each chain with the unprefixed `securityMatcher`s (`/<webcomponent>/**`, `/v2/**`) and cookie `Path=/`. These are wired from the same `TenantSecuritySlice` so OIDC config is shared.

### Storage isolation

`PerTenantSessionRepository` prefixes session ids with the tenant id when calling `PersistentWebSessionClient`. For the PoC the underlying storage stays shared; the prefix avoids collisions. A follow-up will move per-tenant sessions to per-tenant secondary storage once `PhysicalTenantSearchClientReadersConfiguration` / `RdbmsDataSources` per-tenant clients are exposed end-to-end (already partially landed).

### Configuration consumed

```yaml
camunda:
  security:
    authentication:
      method: oidc            # not overridable per PT (validated at startup)
      oidc: { ... }           # default-tenant config under camunda.security.* per existing rules
  physical-tenants:
    tenanta:
      security:
        authentication:
          providers:
            assigned: [idpOne]
            oidc:
              idpOne:
                client-id: ...
                client-secret: ...
                issuer-uri: ...
                redirect-uri: "{baseUrl}/physical-tenant/tenanta/login/oauth2/code/{registrationId}"
    default:
      security:
        authentication:
          providers:
            assigned: [oidc]
```

`providers.assigned` is the **new property** this PoC consumes. Resolution rule: for tenant `t`, the active provider set is `assigned ∩ keys(authentication.oidc.providers)`; missing providers fail startup with an explicit error.

The `cluster-admin` block, CSRF, multi-tenancy, and `http-headers` are out of scope for the PoC and remain global (validated as non-overridable per the existing rules in #52680).

---

## End-to-end demo path

The PoC does **not** require a working OC frontend bundle. The OC webapp ships with absolute paths (`/v2/…`, `/<webcomponent>/…`) and would need either `<base href>` injection at serve time or a publicPath-aware rebuild to live under `/physical-tenant/<id>/…`. That is webapp work, separate from security; out of scope here.

Instead, the PoC ships a minimal PT-scoped controller `PhysicalTenantWhoamiController` that exposes:

- `GET /physical-tenant/{t}/whoami` (webapp chain — session/OIDC login)
- `GET /v2/physical-tenants/{t}/whoami` (api chain — bearer token)

Both return `{ tenantId, principal, providers, accessPath }`. That is sufficient to demonstrate login flow, session isolation, multi-IdP, and tenant resolution.

Demo:

1. Start OC with the `pt-security` profile and the config above (two tenants, two IdPs — Keycloak realms `default` and `tenanta`).
2. Browser → `https://localhost:8080/physical-tenant/tenanta/whoami`.
3. PT webapp chain matches; user is unauthenticated; redirect to `/physical-tenant/tenanta/oauth2/authorization/idpOne` → tenant A's Keycloak.
4. Login → callback at `/physical-tenant/tenanta/login/oauth2/code/idpOne` → session created with cookie `camunda-session-tenanta; Path=/physical-tenant/tenanta`. `whoami` returns `{tenantId:"tenanta", principal:"alice@tenanta", providers:["idpOne"], accessPath:"prefixed"}`.
5. New tab → `https://localhost:8080/physical-tenant/default/whoami` — different cookie scope, unauthenticated, redirected to default's IdP.
6. Both tabs hold valid, independent sessions simultaneously.
7. Tab 1 logs out (`POST /physical-tenant/tenanta/logout`) — only tenant A's session is invalidated; tab 2 stays logged in.
8. (Default-tenant access-path check) Open `https://localhost:8080/whoami` — different cookie Path, unauthenticated, fresh login flow against the default IdP. Confirms the agreed "relogin acceptable across access paths" behaviour.

Verification points:
- Browser cookie inspector shows two cookies with disjoint `Path` attributes.
- `PersistentWebSessionClient` rows are prefixed with the tenant id (`t:tenanta:…`, `t:default:…`).
- Replaying tenant A's OIDC `state` parameter against `/physical-tenant/default/login/oauth2/code/...` is rejected (per-chain `OAuth2AuthorizationRequestRepository` keys state in tenant A's session only).
- The `/v2/physical-tenants/tenanta/whoami` endpoint requires a Bearer token issued by an IdP listed in tenant A's `providers.assigned`; a tenant-B-issued token returns 401.

---

## Local testing setup

Two Keycloak instances stood up via `dasniko/testcontainers-keycloak` — the same Testcontainers library the existing OIDC tests in this repo use (`OidcAuthOverRestIT`, `SecurityHeadersOidcIT`, `PrivateKeyJwtTest`, `DefaultTestContainers.createDefaultKeycloak()`). Reusing the established pattern means no new dependency, no Docker-vs-in-process trade-off discussion, and IT and local-dev share the same IdP shape.

Two complementary entry points:

### 1. Standalone local runner (developer iteration loop)

`dist/src/test/java/io/camunda/application/pt/PtPocLocalIdpRunner.java` — a `main()` that:

- Boots two `KeycloakContainer` instances on fixed host ports (`8081`, `8082`) so the OC config can use stable issuer URIs.
- Imports realm JSON from `dist/src/test/resources/pt-poc/keycloak-default-realm.json` and `keycloak-tenanta-realm.json`.
- Prints both issuer URIs and the test user credentials to stdout.
- Blocks on stdin so the dev can leave it running across many OC restarts.

Realm contents:

| Realm | Client id | Test users |
|---|---|---|
| `default` (`localhost:8081/realms/default`) | `camunda-pt-default-client` | `alice@default / alice` |
| `tenanta` (`localhost:8082/realms/tenanta`) | `camunda-pt-tenanta-client` | `bob@tenanta / bob` |

Both clients pre-configured with redirect URIs `http://localhost:8080/physical-tenant/<id>/login/oauth2/code/*` plus the unprefixed `http://localhost:8080/login/oauth2/code/*` for the default tenant's secondary access path. Standard auth-code flow, `client_secret_basic`, no PKCE requirement (PoC scope; PKCE is a follow-up).

### 2. Bundled config for the OC process

`dist/src/main/resources/application-pt-poc.yaml` — activates with `--spring.profiles.active=pt-poc,pt-security` and references the local Keycloak issuer URIs by default (overridable via standard Spring property indirection). Contains the two-tenant config from the [Configuration consumed](#configuration-consumed) section pre-filled for the local runner's realms.

### 3. Integration test (CI verification)

`dist/src/test/java/io/camunda/application/pt/PhysicalTenantSecurityIT.java` — a single IT that:

- Boots two `KeycloakContainer`s (random ports, isolated per-test).
- Boots OC in-JVM with the `pt-security` profile and tenant config wired to those issuer URIs.
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
- Per-PT secondary storage end-to-end for session repository (storage routing is already in place for RDBMS; wiring it here is a follow-up).
- Forbidden-override validation (#52680) for the PT properties this PoC touches.

---

## Open questions

**OQ-1.** Single cookie covering both the webapp and the api path for a tenant (Path = `/`-rooted at a common ancestor like `/physical-tenant/tenanta`, with the API path mounted underneath), or two cookies? The API chain is sessionless so an API cookie is moot for OIDC bearer auth, but the webapp may need to call `/v2/…` with session cookies for unauthenticated bootstrap. **Initial answer:** one webapp cookie scoped to `/physical-tenant/<t>`, no session cookie on `/v2/physical-tenants/<t>` (bearer-only). Revisit if the webapp can't call `/v2/…` without a bearer.

**OQ-2.** The default tenant's unprefixed chains: do we ever want the same session to be valid on both access paths, or always treat them as separate? **PoC stance:** always separate (cookie Path differs), per the user's "we may accept relogin" relaxation. Revisit when the UX is closer.

**OQ-3.** Logout: do we tear down the session at the OIDC provider (`OidcClientInitiatedLogoutSuccessHandler` behaviour) or only locally? **PoC stance:** reuse `CamundaOidcLogoutSuccessHandler` unchanged — IdP-initiated logout on the tenant's IdP only.

**OQ-4.** Path-pattern collision: a tenant id literally named `physical-tenants` (API path) or `physical-tenant` (webapp path) would collide. **Stance:** `PhysicalTenantResolver` already restricts to `[a-z0-9]+`, so these are unreachable. Validated.
