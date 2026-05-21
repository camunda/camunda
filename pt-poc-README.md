# Physical-Tenant Security PoC — Local Run

Walking-skeleton OIDC login against a per-tenant Spring Security chain, backed by two Keycloak realms in Testcontainers and an in-memory H2 database. Two helper scripts at the repo root drive everything.

## Design and plan

For background, rationale, and the full implementation roadmap, start here — both files are the source of truth for what this PoC is, why each decision was made, and what is or isn't in scope:

- **Spec** — [`docs/superpowers/specs/2026-05-20-physical-tenant-spring-security-poc-design.md`](docs/superpowers/specs/2026-05-20-physical-tenant-spring-security-poc-design.md): architectural decisions (D1–D6), component design, configuration shape, end-to-end demo path, and open questions.
- **Plan** — [`docs/superpowers/plans/2026-05-20-physical-tenant-spring-security-poc.md`](docs/superpowers/plans/2026-05-20-physical-tenant-spring-security-poc.md): the 16-task walking-skeleton implementation roadmap that this PoC follows. Each task is bite-sized with code, tests, and commands.

The [Status](#status) section below tracks per-task progress against the plan.

## Prerequisites

- Docker running (the Keycloak runner pulls `quay.io/keycloak/keycloak`; first run takes ~30–60s).
- Java 21 on `PATH`.
- The repo's Maven wrapper (`./mvnw`) — no separate Maven install needed.
- A free port `8080` (OC), `8081` (Keycloak default realm), `8082` (Keycloak tenanta realm), `9600` (OC management).

## Quick start

Two terminals.

### Terminal 1 — Keycloak

```bash
./pt-poc-idp.sh
```

Wait for the banner:

```
=== PT-PoC local IdPs ready ===
default issuer:   http://localhost:8081/realms/default
tenanta issuer:   http://localhost:8082/realms/tenanta

default client:   camunda-pt-default-client / default-secret
tenanta client:   camunda-pt-tenanta-client / tenanta-secret

default user:     alice / alice
tenanta user:     bob / bob

Press <enter> to stop.
```

Leave it running. Press `Enter` in this terminal to stop both Keycloaks at the end of the session.

### Terminal 2 — OC

```bash
./pt-poc-oc.sh
```

The script:

1. Rebuilds `dist` and every upstream module (`-pl dist -am`) so any local code changes pick up.
2. Skips spotless/license/code-style checks (`-DskipChecks`) and the webapp npm build (`-PskipFrontendBuild`) for speed.
3. Boots OC under the `pt-poc` Spring profile, which activates:
   - `consolidated-auth` — the existing host security graph
   - `pt-security` — the per-tenant `SecurityFilterChain` wiring (this PoC)
   - `rdbmsH2` — in-memory H2 secondary storage (no Elasticsearch required)
4. Streams logs to the terminal AND tees them to `/tmp/oc.log`.

Wait for:

```
Tomcat started on port 8080
```

### One-tenant browser sanity check

Open `http://localhost:8080/physical-tenant/tenanta/app`. Expected flow:

1. Redirect to `http://localhost:8082/realms/tenanta/protocol/openid-connect/auth?...`
2. Log in as `bob` / `bob`
3. Return to OC and end up on the SPA-style demo page at `/physical-tenant/tenanta/app` (server-rendered principal: `bob`, plus a few buttons that exercise the webapp-aligned API URL with the session cookie and the direct API-client URL without it).

The full bi-directional smoke (both webapp chains + both API URL schemes, plus the cross-tenant 403 case) lives in [Smoke testing](#smoke-testing) below.

### Multi-IdP picker (default tenant)

The default tenant is configured with **two** assigned OIDC providers (Task 17 / spec D8):

- `oidc` — default realm at `:8081`, client `camunda-pt-default-client`, audience `pt-default-aud`.
- `default-via-tenanta` — the **tenanta** realm at `:8082` (shared with tenant A), client `camunda-pt-default-via-tenanta-client`, audience `pt-default-via-tenanta-aud`.

Open `http://localhost:8080/app` while logged out. Spring Security's `DefaultLoginPageGeneratingFilter` detects two registrations and serves a picker page with one `/oauth2/authorization/<regId>` link per provider. Picking `default-via-tenanta` routes to the tenanta realm; log in as `bob`; the callback returns to `/app` with `bob` as the default tenant's session principal. A curl probe of the picker page should list two registrations:

```bash
curl -sS -L -c /tmp/picker-jar -b /tmp/picker-jar http://localhost:8080/app \
  | grep -oE "/oauth2/authorization/[a-z-]+" | sort -u
# /oauth2/authorization/default-via-tenanta
# /oauth2/authorization/oidc
```

Same realm `tenanta` therefore backs **two** PT-API entry points. The issuer-allowlist mechanism (existing) can't tell the two PTs apart on a shared IdP — that's what the audience-allowlist mechanism (new) is for.

## Smoke testing

Two chains per tenant: an OAuth2-login webapp chain at `/physical-tenant/<id>/**` and a session-or-bearer API chain at `/physical-tenant/<id>/v2/**` (sub-pattern of the webapp matcher; the API chain is ordered first via `@Order` so requests for `/v2/...` paths hit it before the webapp chain's broader matcher). The smoke matrix covers both tenants on both chains, plus the cross-tenant rejection case the PoC exists to prove.

### Webapp (browser, OAuth2 authorization code)

For each tenant, open the SPA-style demo page in a fresh browser tab (or different browser profile to keep sessions independent). The page is served from the OAuth2-protected webapp chain — landing on it means OAuth2 login succeeded, the session cookie is in place, and the buttons on the page exercise the API chains from that authenticated browser tab.

|  Tenant   |                              URL                            | Login as          | Expected after login                                                                                            |
|-----------|-------------------------------------------------------------|-------------------|-----------------------------------------------------------------------------------------------------------------|
| `tenanta` (prefixed)                | `http://localhost:8080/physical-tenant/tenanta/app`         | `bob` / `bob`     | Page renders with `Session principal: bob` and three buttons (see [SPA-style page](#spa-style-page-webapp-session--api-call-from-the-browser) below). Button 1 calls `/physical-tenant/tenanta/v2/whoami`. |
| `default` (prefixed)                | `http://localhost:8080/physical-tenant/default/app`         | `alice` / `alice` | Page renders with `Session principal: alice`. Button 1 calls `/physical-tenant/default/v2/whoami`.                                                                                                          |
| `default` (unprefixed, like operate/tasklist) | `http://localhost:8080/app`                                 | `alice` / `alice` | Same page, but accessed via the default-unprefixed access path. Button 1 calls bare `/v2/whoami` (cookie at `Path=/`).                                                                                      |

Two simultaneous tab logins coexist — neither tenant's session cookie is sent to the other tenant's URLs (cookie `Path` scoping). To inspect cookies in DevTools: each tab should see only its own `camunda-session-<tenant>` at `Path=/physical-tenant/<tenant>`.

### SPA-style page (webapp session → API call from the browser)

This is the realistic shape of a Camunda webapp: the user logs in via OAuth2 (gets a session cookie), and the JavaScript running in that tab calls the API directly. Open `http://localhost:8080/physical-tenant/<tenantId>/app` (the page is served by the webapp chain, so the OAuth2 dance kicks in automatically; once logged in the page renders the session principal server-side and exposes the buttons below).

1. **`GET /physical-tenant/<id>/v2/whoami` (no Authorization header)** — the SPA call against the API chain on the **webapp-aligned** API URL (spec D7). Expected: **200** with the same principal as the session. The cookie at `Path=/physical-tenant/<id>` covers this URL too, and the API chain installs the same per-tenant `SessionRepositoryFilter` as the webapp chain, so it reuses the `SecurityContext` saved at OAuth2 login.
2. **`GET /v2/physical-tenants/<id>/whoami` (no Authorization header)** — same API endpoint reached via the **direct API-client** URL (spec D7). Expected: **401**. This URL sits outside the cookie's `Path=/physical-tenant/<id>` scope, so the browser does not send the session cookie, and with no Authorization header the API chain returns 401 via `oauth2ResourceServer`'s entry point. This confirms the two URL schemes are isolated by purpose — the SPA uses the webapp-aligned one; external API clients use the API-client one with their own bearer token.
3. **Show `document.cookie`** — the session cookie is `HttpOnly`, so JavaScript can't see it; this confirms the browser-side scoping is in effect.

**OQ-1 is resolved by this layout** (URL move + session-aware API chain). Two complementary mechanisms:

- **Cookie `Path` scope covers the API URL.** `camunda-session-<id>` at `Path=/physical-tenant/<id>` matches `/physical-tenant/<id>/v2/*`. The browser sends the cookie on API calls automatically.
- **API chain reads the session.** `SessionCreationPolicy.STATELESS` (read-only — the chain never creates a session) + the shared per-tenant `SessionRepositoryFilter` + an authorization manager that accepts `OAuth2AuthenticationToken` (session-derived) or `JwtAuthenticationToken` (bearer, allowlist-checked). `oauth2ResourceServer.jwt()` stays wired so non-browser API clients with `Authorization: Bearer <jwt>` keep working unchanged.

### API (bearer token, cross-tenant isolation)

The API chains accept either the per-tenant session cookie (SPA flow above) or a Bearer token (this section). For non-browser clients, each request carries `Authorization: Bearer <token>`. Acquire the token from the tenant's Keycloak realm via the password grant (Direct Access Grants are enabled on both PoC realms), then call the API.

**Dual URL scheme (D7).** The PT REST API is reachable under two URL prefixes that hit the same per-tenant API chain:

- `/physical-tenant/<id>/v2/...` — webapp/SPA URL, inside the per-tenant cookie's `Path` scope. Use this from a browser SPA logged in via OAuth2; the session cookie covers it. Bearer tokens also work.
- `/v2/physical-tenants/<id>/...` — direct API-client URL, the REST-conventional shape (also what the pre-PoC PT REST infrastructure registers). Outside the cookie scope, so bearer-only in practice. Use this from external API clients that bring their own JWT.

**One-shot script** — runs the full 10-cell smoke matrix (the 5-cell matrix repeated against each URL scheme):

```bash
./pt-poc-api-smoke.sh
```

Expected output:

```
=== Acquiring tokens ===
tenanta token: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIs...
default token: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIs...

=== Cross-tenant matrix — webapp-aligned URL (cookie covers this) ===
tenanta -> tenanta     200 /physical-tenant/tenanta/v2/whoami  OK
tenanta -> default     403 /physical-tenant/default/v2/whoami  OK
default -> default     200 /physical-tenant/default/v2/whoami  OK
default -> tenanta     403 /physical-tenant/tenanta/v2/whoami  OK
no token -> tenanta    401 /physical-tenant/tenanta/v2/whoami  OK

=== Cross-tenant matrix — direct API client URL (bearer-only) ===
tenanta -> tenanta     200 /v2/physical-tenants/tenanta/whoami  OK
tenanta -> default     403 /v2/physical-tenants/default/whoami  OK
default -> default     200 /v2/physical-tenants/default/whoami  OK
default -> tenanta     403 /v2/physical-tenants/tenanta/whoami  OK
no token -> tenanta    401 /v2/physical-tenants/tenanta/whoami  OK

=== Default unprefixed URL (cookie scoped at Path=/) ===
default -> default     200 /v2/whoami  OK
tenanta -> default     403 /v2/whoami  OK
no token -> default    401 /v2/whoami  OK

=== Session cross-tenant (logged in via /app as default; call tenanta's API) ===
default session -> /v2/whoami              200 /v2/whoami                          OK
default session -> tenanta webapp-aligned  401 /physical-tenant/tenanta/v2/whoami  OK
default session -> tenanta API-client URL  401 /v2/physical-tenants/tenanta/whoami OK

=== Audience isolation (shared tenanta realm; aud-based PT separation) ===
dvta -> default (webapp)        200 /physical-tenant/default/v2/whoami       OK
dvta -> default (apiclient)     200 /v2/physical-tenants/default/whoami      OK
dvta -> default (unpref.)       200 /v2/whoami                               OK
dvta -> tenanta (webapp)        403 /physical-tenant/tenanta/v2/whoami       OK
dvta -> tenanta (apiclient)     403 /v2/physical-tenants/tenanta/whoami      OK
```

**Audience isolation cells (Task 17 / spec D8).** The `dvta` token is minted via Keycloak's password grant against `camunda-pt-default-via-tenanta-client` on the **tenanta** realm. Its `iss` claim is `http://localhost:8082/realms/tenanta` — the same issuer tenant A's tokens carry. So if the per-chain authorization manager only checked `iss`, the dvta token would pass tenant A's allowlist (same realm ⇒ same issuer) and grant a cross-tenant API call. The audience claim is what separates them: dvta tokens carry `aud=pt-default-via-tenanta-aud`, which is in default's expected list but not tenant A's. Hence 200 against default, 403 against tenant A.

**Manual curl** — get a token and call the API by hand (useful when iterating on a single chain):

```bash
# Tenant A token (bob)
TOKEN=$(curl -fsS -X POST http://localhost:8082/realms/tenanta/protocol/openid-connect/token \
  -d grant_type=password \
  -d client_id=camunda-pt-tenanta-client \
  -d client_secret=tenanta-secret \
  -d username=bob -d password=bob | jq -r .access_token)

# Tenant A's API: 200 + JSON
curl -sS -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/physical-tenant/tenanta/v2/whoami

# Default's API with tenant A's token: 403 (signature valid, issuer not in default's allowlist)
curl -sS -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/physical-tenant/default/v2/whoami | head -1
```

Same shape for the default tenant — swap host `:8082`→`:8081`, realm `tenanta`→`default`, client + credentials accordingly.

The 403 on cross-tenant calls comes from the per-chain `AuthorizationManager`: the JWT is decoded and signature-validated by the shared issuer-aware decoder (it knows both realms), but the chain's authorization rule rejects tokens whose `iss` claim isn't in that tenant's allowed-issuer set.

## What's where

|                             Path                             |                                       Purpose                                       |
|--------------------------------------------------------------|-------------------------------------------------------------------------------------|
| `pt-poc-idp.sh`                                              | Boots two `KeycloakContainer`s on fixed host ports 8081/8082 from the realm exports |
| `pt-poc-oc.sh`                                               | Rebuilds + boots OC under the `pt-poc` profile                                      |
| `pt-poc-api-smoke.sh`                                        | Runs the cross-tenant API matrix (200/403/401) end-to-end via curl + jq             |
| `dist/src/test/resources/pt-poc/*.json`                      | Keycloak realm exports (one client + one test user each)                            |
| `dist/src/main/resources/application-pt-poc.yaml`            | OC's PoC-specific Spring config (tenant A's OIDC provider, security DEBUG logging)  |
| `dist/src/main/resources/application.properties`             | `spring.profiles.group.pt-poc=consolidated-auth,pt-security,rdbmsH2` lives here     |
| `authentication/src/main/java/io/camunda/authentication/pt/` | Per-tenant Spring Security wiring                                                   |
| `dist/src/main/java/io/camunda/application/commons/pt/`      | PoC controllers (PT whoami REST + SPA-style HTML page) — REST endpoint declares `/v2/...` only; auto-prefixed by `PhysicalTenantRequestMappingHandlerMapping` |
| `/tmp/oc.log`                                                | OC's stdout/stderr, including Spring Security DEBUG + FilterChainProxy TRACE        |

## Logs

`./pt-poc-oc.sh` tees stdout/stderr to `/tmp/oc.log` and also configures `logging.file.name: /tmp/oc.log` in `application-pt-poc.yaml`. To follow live:

```bash
tail -f /tmp/oc.log
```

To grep one request's filter chain trace:

```bash
grep -A 30 "Securing GET /physical-tenant/tenanta/v2/whoami" /tmp/oc.log | head -50
```

## Troubleshooting

**Keycloak fails to start with `File name / realm name mismatch`** — the realm JSON's basename must match `<realmName>-realm.json`. The committed files are correct (`default-realm.json`, `tenanta-realm.json`); if you copy one, keep the naming.

**OC starts but boots a different security graph than expected** — confirm the active profile set is `pt-poc,consolidated-auth,pt-security,rdbmsH2`:

```bash
grep "Started Camunda using profiles" /tmp/oc.log
```

**Browser redirect loop on `/physical-tenant/tenanta/oauth2/authorization/tenanta`** — the chain's custom `OAuth2AuthorizationRequestResolver` failed to extract the registration id. Check `/tmp/oc.log` for `OAuth2AuthorizationRequestRedirectFilter (7/16)` followed by `Set SecurityContextHolder to anonymous` — that's the silent fail.

**`No qualifying bean of type 'X'`** at boot — some SPI port that `WebSecurityConfig` previously provided is consumed by non-CSL OC code. Add a stub to `PhysicalTenantHostStubs.java`.

**Port already in use** — another OC or Keycloak instance is up. `lsof -iTCP:8080 -sTCP:LISTEN` (or `:8081`, `:8082`, `:9600`) and kill it.

## Stopping everything

- Terminal 2: `Ctrl-C` to stop OC.
- Terminal 1: press `Enter` to stop both Keycloaks (the runner blocks on stdin and shuts the containers down cleanly).

Docker `ps` should show no `quay.io/keycloak/keycloak` containers after both terminals exit.

## Status

Tracking implementation tasks defined in the [plan](docs/superpowers/plans/2026-05-20-physical-tenant-spring-security-poc.md) (designed against the [spec](docs/superpowers/specs/2026-05-20-physical-tenant-spring-security-poc-design.md)). The current task is **bolded**.

| #  |                                 Task                                  |     State      |
|----|-----------------------------------------------------------------------|----------------|
| 1  | Profile scaffold + verify CSL opts out                                | ✅ done         |
| 2  | Keycloak realm exports                                                | ✅ done         |
| 3  | `PtPocLocalIdpRunner` standalone `main()`                             | ✅ done         |
| 4  | Walking skeleton — one tenant, end-to-end login                       | ✅ done         |
| 5  | Add default tenant prefixed chain + per-chain cookie isolation        | ✅ done         |
| 6  | Extract `TenantSecuritySlice` + `PerTenantSecurityChainFactory`       | ✅ done         |
| 7  | Extract `PhysicalTenantRedirectUriRewriter` + unit test               | ✅ done         |
| 8  | Extract `PerTenantOidcRegistry` + consume `providers.assigned`        | ✅ done         |
| 9  | Wire per-tenant `WebSessionRepository`                                | ✅ done         |
| 10 | Extract `PhysicalTenantCookieSerializer` + unit test                  | ✅ done         |
|    | Checkpoint C — components extracted                                   | ✅ done         |
| 11 | API chain — shared decoder + per-chain issuer allowlist               | ✅ done         |
| 12 | Default tenant unprefixed access-path chains                          | ✅ done         |
| 13 | Generalise registration via `PhysicalTenantResolver.getAll()`         | ✅ done         |
|    | Checkpoint D — full functional surface                                | ✅ done         |
| 16 | Manual browser smoke test                                             | ✅ done         |
| 17 | **Multi-IdP picker + audience-based PT isolation for shared IdPs**    | 🔄 in progress |
|    | Checkpoint E — PoC acceptance                                         | ⏳ pending      |
| 14 | `PhysicalTenantSecurityIT` happy path                                 | ⏳ deferred     |
| 15 | `PhysicalTenantSecurityIT` full flow + isolation                      | ⏳ deferred     |

**What currently works:**

- Two Keycloak realms boot on `:8081` (default) and `:8082` (tenanta).
- OC boots under `pt-poc` against in-memory H2 (no Elasticsearch dependency).
- Four `SecurityFilterChain`s — one webapp + one API per tenant. API chains at `/physical-tenant/{tenanta,default}/v2/**` are `@Order`ed before the webapp chains at `/physical-tenant/{tenanta,default}/**` so the narrower matcher wins. The API URL move puts API requests under the webapp cookie's `Path` scope, and the API chain shares the same per-tenant `SessionRepositoryFilter`, resolving spec **OQ-1** (session-or-bearer auth on the API chain).
- Browser flow reaches Keycloak, returns to the callback, and reaches the token+userinfo exchange for both tenants.
- Per-chain session cookies: `camunda-session-tenanta` (Path `/physical-tenant/tenanta`) and `camunda-session-default` (Path `/physical-tenant/default`). Two simultaneous tab logins coexist; neither chain sees the other's session cookie.
- API chain enforces per-chain issuer allowlist: tenant A's token on tenant A's API → 200; same token on default's API → 403. Shared issuer-aware decoder validates signatures across all known issuers; authorization is per-chain.

**What is not yet wired:**

- End-to-end `PhysicalTenantSecurityIT` — Tasks 14–15.

**Audience-based isolation for shared IdPs (Task 17 / D8).** When the same IdP backs more than one PT (multi-IdP shape exercised by the default tenant against the tenanta realm), the issuer allowlist alone cannot separate tenants — `iss` collides. Each client at the IdP is given a hardcoded audience mapper emitting a tenant-specific `aud` claim, and each tenant declares an `expected-audiences` allowlist under `camunda.physical-tenants.<id>.security.authentication.expected-audiences`. The per-tenant API chain now enforces BOTH `iss` ∈ allowed-issuers AND (when expected-audiences is non-empty) `aud` ∩ expected-audiences ≠ ∅. Session-derived auth is not re-audience-checked — the per-tenant `ClientRegistrationRepository` on the webapp chain already scopes which IdPs can mint a session for this tenant.

Per-tenant durable secondary storage is intentionally out of scope: each tenant's `WebSessionRepository` is backed by a private in-memory `PersistentWebSessionClient`. Storage isolation is structural (no shared backend, no key-prefixing), but sessions die with the process.

**Verified cookie surface** (re-checked end-to-end on 2026-05-21 via curl-driven OAuth flow against both tenants):

- The only session cookies emitted are the per-chain ones, scoped correctly:
  - `camunda-session-tenanta` at `Path=/physical-tenant/tenanta`
  - `camunda-session-default` at `Path=/physical-tenant/default`
- No bare `camunda-session` cookie at `Path=/` and no `JSESSIONID` anywhere. (An earlier README note observed one; that observation was stale — possibly a leftover from a browser cache at a point during Task 5 development. The current code does not emit it.)
- `server.servlet.session.cookie.name=camunda-session` is set only by `WebappsConfigurationInitializer` and only when one of the webapp profiles (`operate`, `tasklist`, `identity`, `admin`, `tmp_webapp`) is active — none of which `pt-poc` activates.

