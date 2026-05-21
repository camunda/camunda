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

Open `http://localhost:8080/physical-tenant/tenanta/whoami`. Expected flow:

1. Redirect to `http://localhost:8082/realms/tenanta/protocol/openid-connect/auth?...`
2. Log in as `bob` / `bob`
3. Return to OC and end up at `/physical-tenant/tenanta/whoami`
4. JSON body: `{"tenantId":"tenanta","principal":"bob"}` (the principal claim depends on the Keycloak realm config)

The full bi-directional smoke (both webapp chains + both API chains, plus the cross-tenant 403 case) lives in [Smoke testing](#smoke-testing) below.

## Smoke testing

Two chains per tenant: an OAuth2-login webapp chain at `/physical-tenant/<id>/**` and a bearer-token API chain at `/v2/physical-tenants/<id>/**`. The smoke matrix covers both tenants on both chains, plus the cross-tenant rejection case the PoC exists to prove.

### Webapp (browser, OAuth2 authorization code)

For each tenant, open the `whoami` endpoint in a fresh browser tab (or different browser profile to keep sessions independent):

|  Tenant   |                              URL                              | Login as      | Expected JSON                                |
|-----------|---------------------------------------------------------------|---------------|----------------------------------------------|
| `tenanta` | `http://localhost:8080/physical-tenant/tenanta/whoami`        | `bob` / `bob` | `{"tenantId":"tenanta","principal":"bob"}`   |
| `default` | `http://localhost:8080/physical-tenant/default/whoami`        | `alice` / `alice` | `{"tenantId":"default","principal":"alice"}` (or the Keycloak `sub` UUID, depending on the realm's preferred-username mapping) |

Two simultaneous tab logins coexist — neither tenant's session cookie is sent to the other tenant's URLs (cookie `Path` scoping). To inspect cookies in DevTools: each tab should see only its own `camunda-session-<tenant>` at `Path=/physical-tenant/<tenant>`.

### SPA-style page (webapp session → API call from the browser)

This is the realistic shape of a Camunda webapp: the user logs in via OAuth2 (gets a session cookie), and the JavaScript running in that tab calls the API directly. Open `http://localhost:8080/physical-tenant/<tenantId>/app` after logging in (the page is served by the webapp chain, so the OAuth2 dance kicks in automatically). It exposes three buttons:

1. **`GET /physical-tenant/<id>/whoami`** — same chain, uses the session cookie. Expected: **200** with the principal.
2. **`GET /v2/physical-tenants/<id>/whoami` (no Authorization header)** — the SPA call. Expected with the current setup: **401**, `WWW-Authenticate: Bearer`.
3. **Show `document.cookie`** — the session cookie is `HttpOnly`, so JavaScript can't see it; this confirms the browser-side scoping is in effect.

The 401 is intentional in the current setup and surfaces spec **OQ-1**. Two reinforcing reasons:

- **Cookie `Path` scope.** `camunda-session-<id>` is at `Path=/physical-tenant/<id>` — the browser does not send it to `/v2/physical-tenants/<id>/*`. (You can verify in DevTools' Network tab: the API request has no `Cookie` header.)
- **API chain is bearer-only.** `SessionCreationPolicy.NEVER` + `oauth2ResourceServer.jwt()`. Even if the cookie reached the server, the chain wouldn't process it.

For real webapps this needs a resolution; see [Open question OQ-1](docs/superpowers/specs/2026-05-20-physical-tenant-spring-security-poc-design.md). Candidate directions (none implemented yet — needs design call):

1. **Widen the cookie's `Path`** to a common prefix that covers both the webapp and the API URL space (e.g., mount the API under `/physical-tenant/<id>/v2/...` and keep `Path=/physical-tenant/<id>`).
2. **Add session-aware auth to the API chain** (accept the session cookie as an alternative to Bearer). Means the API chain needs the per-tenant `SessionRepositoryFilter` too.
3. **Token-broker endpoint** under the webapp chain (`/physical-tenant/<id>/api/token`) that exchanges the session for an OIDC access token; the SPA puts that token in `Authorization: Bearer` on API calls.

The current PoC implements **none** of these — surfacing the gap is the point of the SPA-style page.

### API (bearer token, cross-tenant isolation)

The API chains are stateless: each request needs an `Authorization: Bearer <token>` header. Acquire the token from the tenant's Keycloak realm via the password grant (Direct Access Grants are enabled on both PoC realms), then call the API.

**One-shot script** — runs the full 5-cell smoke matrix (2 same-tenant successes, 2 cross-tenant rejections, 1 unauthenticated probe):

```bash
./pt-poc-api-smoke.sh
```

Expected output:

```
=== Acquiring tokens ===
tenanta token: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIs...
default token: eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIs...

=== Cross-tenant matrix ===
tenanta -> tenanta     200 /v2/physical-tenants/tenanta/whoami  OK
tenanta -> default     403 /v2/physical-tenants/default/whoami  OK
default -> default     200 /v2/physical-tenants/default/whoami  OK
default -> tenanta     403 /v2/physical-tenants/tenanta/whoami  OK

=== Unauthenticated probe (should be 401) ===
no token -> tenanta    401 /v2/physical-tenants/tenanta/whoami  OK
```

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
  http://localhost:8080/v2/physical-tenants/tenanta/whoami

# Default's API with tenant A's token: 403 (signature valid, issuer not in default's allowlist)
curl -sS -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/v2/physical-tenants/default/whoami | head -1
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
| `/tmp/oc.log`                                                | OC's stdout/stderr, including Spring Security DEBUG + FilterChainProxy TRACE        |

## Logs

`./pt-poc-oc.sh` tees stdout/stderr to `/tmp/oc.log` and also configures `logging.file.name: /tmp/oc.log` in `application-pt-poc.yaml`. To follow live:

```bash
tail -f /tmp/oc.log
```

To grep one request's filter chain trace:

```bash
grep -A 30 "Securing GET /physical-tenant/tenanta/whoami" /tmp/oc.log | head -50
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
| 12 | **Default tenant unprefixed access-path chains**                      | 🔄 in progress |
| 13 | Generalise registration via `PhysicalTenantResolver.getAll()`         | ⏳ pending      |
| 14 | `PhysicalTenantSecurityIT` happy path                                 | ⏳ pending      |
| 15 | `PhysicalTenantSecurityIT` full flow + isolation                      | ⏳ pending      |
| 16 | Manual browser smoke test                                             | ⏳ pending      |
| 17 | Multi-IdP verification for default tenant (picker page)               | ⏳ pending      |

**What currently works:**

- Two Keycloak realms boot on `:8081` (default) and `:8082` (tenanta).
- OC boots under `pt-poc` against in-memory H2 (no Elasticsearch dependency).
- Four `SecurityFilterChain`s — one webapp + one API per tenant: `/physical-tenant/{tenanta,default}/**` and `/v2/physical-tenants/{tenanta,default}/**`.
- Browser flow reaches Keycloak, returns to the callback, and reaches the token+userinfo exchange for both tenants.
- Per-chain session cookies: `camunda-session-tenanta` (Path `/physical-tenant/tenanta`) and `camunda-session-default` (Path `/physical-tenant/default`). Two simultaneous tab logins coexist; neither chain sees the other's session cookie.
- API chain enforces per-chain issuer allowlist: tenant A's token on tenant A's API → 200; same token on default's API → 403. Shared issuer-aware decoder validates signatures across all known issuers; authorization is per-chain.

**What is not yet wired:**

- Default tenant's unprefixed access-path chains — Task 12.
- End-to-end `PhysicalTenantSecurityIT` — Tasks 14–15.
- Multi-IdP per tenant (picker page) — Task 17.

Per-tenant durable secondary storage is intentionally out of scope: each tenant's `WebSessionRepository` is backed by a private in-memory `PersistentWebSessionClient`. Storage isolation is structural (no shared backend, no key-prefixing), but sessions die with the process.

**Verified cookie surface** (re-checked end-to-end on 2026-05-21 via curl-driven OAuth flow against both tenants):

- The only session cookies emitted are the per-chain ones, scoped correctly:
  - `camunda-session-tenanta` at `Path=/physical-tenant/tenanta`
  - `camunda-session-default` at `Path=/physical-tenant/default`
- No bare `camunda-session` cookie at `Path=/` and no `JSESSIONID` anywhere. (An earlier README note observed one; that observation was stale — possibly a leftover from a browser cache at a point during Task 5 development. The current code does not emit it.)
- `server.servlet.session.cookie.name=camunda-session` is set only by `WebappsConfigurationInitializer` and only when one of the webapp profiles (`operate`, `tasklist`, `identity`, `admin`, `tmp_webapp`) is active — none of which `pt-poc` activates.

