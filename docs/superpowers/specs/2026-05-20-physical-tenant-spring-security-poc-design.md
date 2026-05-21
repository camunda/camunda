# Physical-Tenant Spring Security PoC — Design

**Status:** Draft for review
**Scope:** OC (`dist/`) application only. Optimize, Operate, Tasklist out of scope.
**Audience:** sebastian.bathke@camunda.com / identity squad.

---

## Context

Camunda 8.10 introduces the **Physical Tenant (PT)** concept: a single OC process serves multiple tenants behind dedicated URL prefixes, each with its own OIDC providers, sessions, and (eventually) secondary storage. PR #52529 plus the follow-ups on `identity-pt-poc` land the PT plumbing on the REST side (`PhysicalTenantRequestMappingHandlerMapping`, `PhysicalTenantInterceptor`, `PhysicalTenantContext`, `PhysicalTenantResolver`). The Spring Security half does not exist yet — that is the gap this PoC closes.

URL scheme (final):

| Surface  |                  Prefixed                  | Default-only fallback |
|----------|--------------------------------------------|-----------------------|
| REST API | `/physical-tenant/<id>/v2/...`             | `/v2/...`             |
| Webapps  | `/physical-tenant/<id>/<webcomponent>/...` | `/<webcomponent>/...` |

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
- `Path=/physical-tenant/<tenantId>` — a single cookie covers both the webapp URLs (`/physical-tenant/<t>/...`) and the API URLs (`/physical-tenant/<t>/v2/...`). The API URL space is mounted under the tenant prefix so the cookie's `Path` scope covers both surfaces; see [OQ-1 resolution](#open-questions).
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

Each chain matches a fixed-prefix `securityMatcher` containing the tenant id literal. No per-request "which tenant am I" lookup inside a filter is required for the security stack itself — the matcher resolves it.

The PoC controller takes the tenant id from `@PathVariable` directly and does not depend on the ambient `PhysicalTenantContext.current()` accessor. The existing `PhysicalTenantInterceptor` continues to populate the request attribute for `/v2/…` REST routes via `PhysicalTenantRequestMappingHandlerMapping`. Webapp paths (`/physical-tenant/<id>/<webcomponent>/…`) do not flow through that interceptor today, and no PoC consumer needs them to — `PhysicalTenantContext.current()` has no production callers in the repo at this scope. If a downstream service called from a webapp-route controller starts depending on ambient tenant context, a small filter can be added then (~10 lines, deferred to YAGNI).

### D6. Design as portable to approach C

We don't build C now, but we deliberately keep the per-tenant chain factory portable to a child-context model so the migration cost is bounded if C becomes warranted later. Concretely:

- `PerTenantSecurityChainFactory` and `TenantSecuritySlice` take all dependencies as constructor arguments. No `@Autowired` field injection from a root context. No `@ConditionalOnMissingBean` games between tenant chains.
- Per-tenant bean names are derived from the tenant id and never collide with a "global" bean name CSL would create. This keeps each tenant's bean graph addressable as a unit.
- `PhysicalTenantSecurityConfiguration` is the only place that touches `PhysicalTenantResolver.getAll()` and root-context bean registration. To graduate to C, this class is replaced; the chain factory and slice are reused verbatim inside each child context's config class.

Cost of this constraint inside A: essentially zero. It is good Spring hygiene anyway.

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
| `PhysicalTenantWebSessionRepositoryConfiguration` (in `dist/`)       | `@Profile("pt-security")` `@Configuration` exposing `Map<String, WebSessionRepository>` keyed by tenant id. Lives in `dist/` because it needs `PhysicalTenantResolver` (not on `authentication/`'s classpath). The chain config injects this map directly — no registry wrapper class.                                                                                                                                                                                                                                                                   |
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

1. Start OC with the `pt-security` profile and the config above (two tenants, two IdPs — Keycloak realms `default` and `tenanta`).
2. Browser → `https://localhost:8080/physical-tenant/tenanta/whoami`.
3. PT webapp chain matches; user is unauthenticated; redirect to `/physical-tenant/tenanta/oauth2/authorization/tenanta` → tenant A's Keycloak (tenant A binds its Keycloak realm to a *named* OIDC provider `tenanta` under `authentication.providers.oidc.tenanta.*`).
4. Login → callback at `/physical-tenant/tenanta/login/oauth2/code/tenanta` → session created with cookie `camunda-session-tenanta; Path=/physical-tenant/tenanta`. `whoami` returns `{tenantId:"tenanta", principal:"bob@tenanta", providers:["tenanta"], accessPath:"prefixed"}`.
5. New tab → `https://localhost:8080/physical-tenant/default/whoami` — different cookie scope, unauthenticated, redirected to `/physical-tenant/default/oauth2/authorization/oidc` (the default tenant binds to the cluster-default `authentication.oidc.*` slot, registration id `oidc`). The PoC exercises both resolution paths — default-slot for the default tenant, named-slot for tenant A.
6. Both tabs hold valid, independent sessions simultaneously.
7. Tab 1 logs out (`POST /physical-tenant/tenanta/logout`) — only tenant A's session is invalidated; tab 2 stays logged in.
8. (Default-tenant access-path check) Open `https://localhost:8080/whoami` — different cookie Path, unauthenticated, fresh login flow against the default IdP. Confirms the agreed "relogin acceptable across access paths" behaviour.

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
- Per-PT *durable* secondary storage for the session repository. The PoC uses in-memory per-tenant `PersistentWebSessionClient` instances. Wiring per-tenant RDBMS/search (per-tenant `SqlSessionFactory` + `PersistentWebSessionMapper`, or per-tenant `SearchClients`-backed `PersistentWebSessionSearchImpl`) is a separate follow-up: it requires fanning out `MyBatisConfiguration` (or its search counterpart) per tenant and adding per-tenant rdbms overlays in `application-pt-poc.yaml`. That work is unrelated to the security wiring this PoC validates.
- Forbidden-override validation (#52680) for the PT properties this PoC touches.

---

## Open questions

**OQ-1.** Single cookie covering both the webapp and the api path for a tenant (Path = `/`-rooted at a common ancestor like `/physical-tenant/tenanta`, with the API path mounted underneath), or two cookies? **Resolved:** one webapp cookie scoped to `Path=/physical-tenant/<t>`, with the API URL space moved from `/v2/physical-tenants/<t>/...` to `/physical-tenant/<t>/v2/...` so it sits inside the cookie's `Path` scope. The API chain becomes session-or-bearer: it installs the same per-tenant `SessionRepositoryFilter` instance the webapp chain uses (against the same per-tenant `WebSessionRepository`) and its authorization manager admits `OAuth2AuthenticationToken` (session, login already validated on the same tenant's webapp chain) or `JwtAuthenticationToken` (bearer, allowlist-checked). `oauth2ResourceServer.jwt()` stays wired so non-browser API clients are unaffected and unauthenticated requests still get 401 with `WWW-Authenticate: Bearer` via that entry point. Chain registration order: the API chain (`/physical-tenant/<t>/v2/**`) is `@Order`ed before the webapp chain (`/physical-tenant/<t>/**`) because the API matcher is a sub-pattern of the webapp matcher.

**OQ-2.** The default tenant's unprefixed chains: do we ever want the same session to be valid on both access paths, or always treat them as separate? **PoC stance:** always separate (cookie Path differs), per the user's "we may accept relogin" relaxation. Revisit when the UX is closer.

**OQ-3.** Logout: do we tear down the session at the OIDC provider (`OidcClientInitiatedLogoutSuccessHandler` behaviour) or only locally? **PoC stance:** reuse `CamundaOidcLogoutSuccessHandler` unchanged — IdP-initiated logout on the tenant's IdP only.

**OQ-4.** Path-pattern collision: a tenant id literally named `physical-tenants` (API path) or `physical-tenant` (webapp path) would collide. **Stance:** `PhysicalTenantResolver` already restricts to `[a-z0-9]+`, so these are unreachable. Validated.

**OQ-5.** Per-PT Spring sub-`ApplicationContext`s — would isolating per-tenant beans inside their own child context be a stronger primitive than the current `Map<String, T>` injection pattern? Per-PT context would let each PT's `ClientRegistrationRepository`, `WebSessionRepository`, allowed-issuer `Set`, etc. live as plain singletons; cross-tenant injection becomes structurally impossible at the bean-resolution layer. **PoC stance:** stay with `Map<String, T>` — three per-PT beans is below the threshold where sub-contexts pay off. **Follow-up:** worth a separate design spike against the broader PT roadmap (data-layer / service-layer / scheduler isolation), including how Spring Security 7 handles child-context `SecurityFilterChain` collection, where cross-cutting beans (union-of-issuers decoder) live, and what the per-tenant lifecycle/refresh story looks like.
