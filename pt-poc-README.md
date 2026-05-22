# Physical-Tenant Security PoC

OIDC-backed per-physical-tenant Spring Security chains, demonstrated end-to-end against two Keycloak realms and an in-memory H2 database. Two helper scripts at the repo root boot the IdPs and OC; a third runs the API smoke matrix.

The PoC validates the security wiring (chain shape, session/cookie scoping, audience-aware authorization, multi-IdP picker, CSRF parity with CSL) and shows the real Operate webapp loading under a PT-prefixed URL.

## Design and plan

For background, rationale, and the full implementation roadmap — both files are the source of truth:

- **Spec** — [`docs/superpowers/specs/2026-05-20-physical-tenant-spring-security-poc-design.md`](docs/superpowers/specs/2026-05-20-physical-tenant-spring-security-poc-design.md)
- **Plan** — [`docs/superpowers/plans/2026-05-20-physical-tenant-spring-security-poc.md`](docs/superpowers/plans/2026-05-20-physical-tenant-spring-security-poc.md)

## Scope — what works

**Per-tenant security chains.** One webapp chain (`@Order(0)`) and one API chain (`@Order(-1)`) per PT, programmatically registered by `PhysicalTenantSecurityChainRegistrar`. Co-exist with CSL's `BaseSecurityConfiguration` chains (unprotected-paths, OidcWebapp/OidcApi, catch-all 404) — no separate profile gate; activation is driven by `camunda.physical-tenants.*` being non-empty.

**Per-tenant session/cookie isolation.** `camunda-session-tenanta` at `Path=/physical-tenant/tenanta`, `camunda-session-default` at `Path=/physical-tenant/default`. Two simultaneous browser tabs (one per tenant) coexist without leaking cookies. The same per-tenant `SessionRepositoryFilter` is installed on both the webapp and API chain so the API chain can read the session the webapp chain saved at OAuth2 login.

**Dual API URL scheme (spec D7).** `/physical-tenant/<id>/v2/...` (webapp-aligned, inside the cookie scope, session-or-bearer) and `/v2/physical-tenants/<id>/...` (API-client conventional, bearer-only). Same per-tenant API chain serves both.

**Audience-aware authorization (spec D8).** When the same IdP backs multiple PTs (the PoC's default tenant has both the local realm and the shared tenanta realm assigned), the per-chain authorization manager enforces both `iss ∈ allowed-issuers` AND `aud ∩ expected-audiences ≠ ∅`. Cross-tenant tokens return 403 even when they share an issuer — provided each PT declares its own distinct audience set on the shared registration (PoC config: tenanta gets `pt-tenanta-aud`, default's view of the tenanta realm gets `pt-default-via-tenanta-aud`). When two PTs share both issuer AND audience, the audience check passes for both and cross-tenant access is not blocked at this layer; audience isolation is a configuration discipline, not an automatic property of registering the same IdP twice.

**Per-PT multi-IdP picker.** Spring Security's `DefaultLoginPageGeneratingFilter` installed explicitly on each PT webapp chain at `prefix + "/login"`, populated from that PT's own `ClientRegistrationRepository` (so the picker shows the PT's `providers.assigned` set, with PT-prefixed authorization URLs).

**Real Operate webapp loading under a PT prefix.** Five host-side pieces make it work:

- `PhysicalTenantWebappRequestMappingHandlerMapping` registers PT-prefixed siblings for plain Spring MVC `@Controller` mappings under `/operate`, `/tasklist`, `/admin`.
- `PhysicalTenantWebappContextPathInterceptor` rewrites the `contextPath` model attribute so the rendered `<base href>` carries the PT prefix (handles both direct hits and internally forwarded requests via `RequestDispatcher.FORWARD_REQUEST_URI`).
- `PhysicalTenantWebappAssetRewriteFilter` URL-rewrites PT-prefixed static assets (`/physical-tenant/<id>/operate/assets/...`) and cluster-scoped REST endpoints (`/physical-tenant/<id>/v(1|2)/(license|status|topology|setup/user|rest-api.yaml)`) to their unprefixed equivalents so the standard handlers serve them.
- `PhysicalTenantWebappClientConfigRewriteAdvice` rewrites the `baseName` and `contextPath` fields in `client-config.js` so the SPA's `react-router` basename matches the PT-prefixed URL.
- `defaultSuccessUrl(..., alwaysUse=false)` preserves the original target URL after login (deep-link to `/physical-tenant/<id>/operate/processes` survives the OAuth roundtrip).

**CSRF parity with CSL.** `PerTenantSecurityChainFactory#applyCsrfConfiguration` mirrors CSL's `SecurityFilterChainSupport#applyCsrfConfiguration` (package-private upstream, replicated inline): `CookieCsrfTokenRepository` with cookie name `X-CSRF-TOKEN`, `CsrfProtectionRequestMatcher` over `SecurityPathPort`'s unprotected paths + login/logout + the PT-prefixed login + the configured ignored patterns, and a response-header filter for SPAs that read the token from `response.headers.get('X-CSRF-TOKEN')`. Identical behaviour on PT and CSL chains.

**Audience isolation cells, multi-IdP picker, and the full curl matrix all pass** — see [Testing the PoC](#testing-the-poc) below.

## Scope — what's not (yet)

**Per-PT broker partitioning + RDBMS exporter race.** Each PT gets its own `PartitionManager`, but the rdbms exporter is wired as a single bean against the default PT's data source. With two `PartitionManager`s both writing to the same `EXPORTER_POSITION` row, the second one races and fails every flush — `expected -1 but found N` errors. **Workaround in the PoC**: `BrokerModuleConfiguration` narrows `physicalTenantIds` to `[default]` so only the default engine runs. Tenanta's security chain still works (routing, auth, cookies); reads of process/instance data go through the default engine's tables. The right fix is making the rdbms exporter PT-aware (separate factory + data source per PartitionManager), which is upstream work beyond the PoC.

**Webapp logout flow under a PT prefix.** No `.logout(...)` on the PT webapp chain. `POST /physical-tenant/<id>/logout` returns 403 — CSRF rejects the POST before `LogoutFilter` (which is bound to the default `/logout` URL) gets a chance to match. Tracked as Task 19.

**Authorizations are disabled** (`camunda.security.authorizations.enabled: false`). Seeding admin grants via `camunda.security.initialization.mappingRules` needs the broker engine to process `IDENTITY_SETUP` records, which would fan out per-PT under the broker partitioning above and never succeed. The PoC bypasses the WebAppAuthorizationCheckFilter denial so users reach `/operate` without grant rows.

**Some SPA-internal API calls still hit unprefixed URLs.** The asset filter rewrites known cluster-scoped endpoints; other endpoints the SPA composes from `/v2/...` (rather than from the PT-aware `contextPath`) may slip through. Watch the browser dev-tools for any 404s and add the path to the filter's regex.

**Unprefixed `/login` is host-rendered**, not Spring-auto-generated — see [CSL#269](https://github.com/camunda/camunda-security-library/issues/269). The PoC keeps `PhysicalTenantLoginPageController` for the unprefixed picker only; PT-prefixed `/login` uses the auto-picker as described above.

**Per-PT CSRF cookie isolation.** The CSRF cookie is shared at `Path=/` across all chains. Tenant boundaries are enforced by the per-PT session cookie's path, so this is acceptable security-wise (the CSRF token is a same-origin proof, not a tenant credential), but cross-tab UX papers cuts apply on logout + auth rotation (Task 45 follow-up).

## Prerequisites

- Docker running (the Keycloak runner pulls `quay.io/keycloak/keycloak`).
- Java 21 on `PATH`.
- The repo's Maven wrapper (`./mvnw`).
- Free ports: 8080 (OC), 8081 (Keycloak default realm), 8082 (Keycloak tenanta realm), 9600 (OC management).

## Running the PoC

Two terminals.

### Terminal 1 — Keycloak

```bash
./pt-poc-idp.sh
```

Wait for the banner listing realm URLs, client credentials, and test users (`alice` / `alice` on default, `bob` / `bob` on tenanta). Leave the script running; press Enter at the end of the session to stop both Keycloaks cleanly.

### Terminal 2 — OC

```bash
./pt-poc-oc.sh
```

Wait for `Tomcat started on port 8080`. The script rebuilds `dist` and upstream modules and boots OC under the `pt-poc` profile, which expands via `spring.profiles.group.pt-poc` into `consolidated-auth,rdbmsH2,operate,tasklist,broker,admin` — security graph + in-memory H2 + the real Operate/Tasklist webapps + the broker engine + admin webapp. PT chain registration triggers automatically from `camunda.physical-tenants.*` (no separate profile).

Logs stream to the terminal and also tee to `/tmp/oc.log`.

## Testing the PoC

### Browser smoke

Three flows cover the interesting cases:

1. **Tenant A prefixed webapp (single-IdP, no picker).** Open `http://localhost:8080/physical-tenant/tenanta/app`. Tenanta has only one assigned provider (`tenanta`), so the entry point redirects straight to Keycloak. Log in as `bob`. Demo page renders with `Session principal: bob`.
2. **Default tenant prefixed webapp (multi-IdP picker).** Open `http://localhost:8080/physical-tenant/default/app`. Default has two assigned providers (`oidc` + `tenanta`). The PT chain's installed `DefaultLoginPageGeneratingFilter` renders a picker at `/physical-tenant/default/login` listing both, with PT-prefixed authorization URLs. Pick `oidc`; log in as `alice`.
3. **Real Operate under a PT prefix.** Open `http://localhost:8080/physical-tenant/default/operate`. After login, Operate's SPA loads with `<base href="/physical-tenant/default/operate/">`, the asset URLs resolve under the PT prefix, `client-config.js` returns `"baseName":"/physical-tenant/default/operate"`, and `react-router` accepts the URL. Deep-linking works: `http://localhost:8080/physical-tenant/default/operate/processes` survives the OAuth round-trip and lands back on `/processes`.

### Cookie + session isolation

Open the two PT entry paths in separate browser tabs. Each tab should see only its own `camunda-session-<tenant>` at `Path=/physical-tenant/<tenant>` in DevTools → Application → Cookies. The unprefixed `/operate` URL won't surface a session cookie if you haven't logged in via the CSL chain.

### API matrix

```bash
./pt-poc-api-smoke.sh
```

Runs the full smoke matrix end-to-end via curl + jq. Expected cells (all `OK`):

- Webapp-aligned URL scheme: same-tenant 200, cross-tenant 403, no-token 401.
- Direct API-client URL scheme: same-tenant 200, cross-tenant 403, no-token 401.
- Default-unprefixed URL: same shape, 200/403/401.
- Session cross-tenant: default's session cookie reaches `/v2/whoami` (cookie at `Path=/` for CSL chain), but cannot reach `/physical-tenant/tenanta/v2/whoami` or `/v2/physical-tenants/tenanta/whoami` (cookie path mismatch + no bearer → 401).
- Audience isolation (spec D8): a `dvta` token (same iss as tenanta, but `aud=pt-default-via-tenanta-aud`) is accepted at default's URLs and rejected at tenanta's.

A single failing cell is enough to break the PoC's central claim — investigate immediately.

### CSRF check

After login, open DevTools → Network → pick a GET response. It should carry an `X-CSRF-TOKEN` response header (the value is BREACH-masked, so each GET response surfaces a different value — that's `XorCsrfTokenRequestAttributeHandler` working). Then trigger a state-changing call (Operate's logout button or any POST). The request should carry an `X-CSRF-TOKEN` request header.

Note: GET requests do NOT carry the `X-CSRF-TOKEN` request header — the SPA only attaches it on POST/PUT/PATCH/DELETE.

## Open questions and follow-ups

| Where | What | Status |
|--|--|--|
| Task 19 | Wire `.logout(...)` on the PT webapp chain (no LogoutFilter matcher under PT prefix; CSRF 403 today). Spec note + PoC scope. | This repo |
| Task 45 | Multi-tab CSRF cookie invalidation on logout / auth-rotate. Path-scoped or per-PT-name cookie options outlined in spec. | This repo |
| Task 18 | Tasklist end-to-end (Operate is verified; Tasklist follows the same pattern but hasn't been smoke-tested). | This repo |
| [camunda-security-library#269](https://github.com/camunda/camunda-security-library/issues/269) | CSL installs `DefaultLoginPageGeneratingFilter` on its `OidcWebapp` chain so the unprefixed `/login` picker doesn't need a host-side controller. | Upstream CSL |
| [camunda/camunda#53810](https://github.com/camunda/camunda/issues/53810) | Resolve `physicalTenantId` from the unified webapp's request context for `WebappIndexController` (same shape as #52572 for the REST gateway). | Upstream camunda |
| Spec OQ-5 | Per-PT Spring sub-`ApplicationContext`s as an isolation primitive vs the current `Map<String, T>` injection pattern. Worth a design spike when the PT roadmap revisits data-layer / service-layer isolation. | Future design |
| Out of scope | PT-aware RDBMS exporter wiring (`RdbmsExporterConfiguration` + `PartitionManagerStep` per-PT factories) so each PartitionManager writes to its own schema. | Upstream broker |
| Out of scope | Per-PT durable secondary storage for `WebSessionRepository`. The PoC uses in-memory `PersistentWebSessionClient` per tenant; structurally isolated but non-durable. | Beyond PoC |
| Out of scope | PT-aware operate/tasklist SPA API client (not just `contextPath` / `baseName` — the actual `/v2/...` URL composition). | Upstream operate/tasklist |
| Out of scope | Tasks 14/15 (Testcontainers ITs) — bootstrap obstacle in `dist/` test scope; deferred to upstream CSL hardening where the test infrastructure already exists. | Upstream CSL |

## What's where

| Path | Purpose |
|--|--|
| `pt-poc-idp.sh` | Boots two `KeycloakContainer`s on fixed host ports 8081/8082 from the realm exports |
| `pt-poc-oc.sh` | Rebuilds + boots OC under the `pt-poc` profile |
| `pt-poc-api-smoke.sh` | Runs the cross-tenant API matrix end-to-end |
| `dist/src/test/resources/pt-poc/*.json` | Keycloak realm exports (one client + one test user each) |
| `dist/src/main/resources/application-pt-poc.yaml` | PoC-specific Spring config (PT providers, security DEBUG, rdbms logging silenced) |
| `dist/src/main/resources/application.properties` | `spring.profiles.group.pt-poc=consolidated-auth,rdbmsH2,operate,tasklist,broker,admin` |
| `authentication/src/main/java/io/camunda/authentication/pt/` | Per-tenant Spring Security chain factory + registrar + slice/context |
| `dist/src/main/java/io/camunda/application/commons/pt/` | PoC controllers + the webapp-routing wiring (RMHM, interceptor, filter, advice, login-page controller) |
| `dist/src/main/java/io/camunda/zeebe/broker/BrokerModuleConfiguration.java` | Broker startup wiring with the `physicalTenantIds = [default]` workaround for the RDBMS exporter race |
| `/tmp/oc.log` | OC's stdout/stderr, Spring Security DEBUG + FilterChainProxy TRACE |

## Logs and troubleshooting

`./pt-poc-oc.sh` tees stdout/stderr to `/tmp/oc.log`. Follow live:

```bash
tail -f /tmp/oc.log
```

Grep one request's filter chain trace:

```bash
grep -A 30 "Securing GET /physical-tenant/tenanta/v2/whoami" /tmp/oc.log | head -50
```

Common issues:

- **`No qualifying bean of type ...` at boot** — usually an SPI port the host expected. Check the stack trace; the broker engine's PT partitioning workaround in `BrokerModuleConfiguration` is a known soft spot.
- **404 on `/physical-tenant/<id>/v2/<something>`** — probably a `@ClusterScoped` controller that isn't yet in the asset filter's `PT_CLUSTER_API` regex. Add the path or the SPA endpoint to the pattern.
- **`<Router basename="/operate"> is not able to match the URL ...`** — the `client-config.js` rewrite didn't fire. Check the advice supports() method matches the controller class name; class names changed at some point upstream may need adjustment.
- **Repeated `Exporter position mismatch for partition 1` log lines** — the broker partitioning workaround isn't applied (or you removed it). The PoC currently silences `io.camunda.db.rdbms` + `io.camunda.exporter.rdbms` to `OFF` so these don't flood the log; if you see them, check `BrokerModuleConfiguration#broker` is still narrowing PT IDs to `[default]`.
- **Port already in use** — `lsof -iTCP:8080 -sTCP:LISTEN` (or 8081/8082/9600) and kill the offender.

## Stopping everything

- Terminal 2: `Ctrl-C` to stop OC.
- Terminal 1: press `Enter` to stop both Keycloaks (the runner blocks on stdin and shuts containers down cleanly).

`docker ps` should show no `quay.io/keycloak/keycloak` containers after both terminals exit.

## Status

Tracking implementation tasks defined in the [plan](docs/superpowers/plans/2026-05-20-physical-tenant-spring-security-poc.md) (designed against the [spec](docs/superpowers/specs/2026-05-20-physical-tenant-spring-security-poc-design.md)).

| # | Task | State |
|--|--|--|
| 1 | Profile scaffold + verify CSL opts out | ✅ done |
| 2 | Keycloak realm exports | ✅ done |
| 3 | `PtPocLocalIdpRunner` standalone `main()` | ✅ done |
|   | Checkpoint A — infra-ready review | ✅ done |
| 4 | Walking skeleton — one tenant, end-to-end login | ✅ done |
|   | Checkpoint B — first login works | ✅ done |
| 5 | Add default tenant prefixed chain + per-chain cookie isolation | ✅ done |
| 6 | Extract `PhysicalTenantChainContext` + `PerTenantSecurityChainFactory` | ✅ done |
| 7 | Extract `PhysicalTenantRedirectUriRewriter` + unit test | ✅ done |
| 8 | Extract `PerTenantOidcRegistry` + consume `providers.assigned` | ✅ done |
| 9 | Wire per-tenant `WebSessionRepository` | ✅ done |
| 10 | Extract `PhysicalTenantCookieSerializer` + unit test | ✅ done |
|    | Checkpoint C — components extracted | ✅ done |
| 11 | API chain — shared decoder + per-chain issuer allowlist | ✅ done |
| 12 | Default tenant unprefixed access-path chains | ✅ done |
| 13 | Generalise registration via `PhysicalTenantResolver.getAll()` | ✅ done |
|    | Checkpoint D — full functional surface | ✅ done |
| 16 | Manual browser smoke test | ✅ done |
| 17 | Multi-IdP picker + audience-based PT isolation for shared IdPs | ✅ done |
|    | Checkpoint E — PoC acceptance | ✅ closed |
| 18 | Operate + Tasklist webapps end-to-end with PT chains | 🟡 Operate ✅, Tasklist not smoke-tested yet |
| 19 | Investigate broken webapp logout flow on PT chains | ⏳ follow-up |
| 20 | CSL: install `DefaultLoginPageGeneratingFilter` on OidcWebapp chain ([CSL#269](https://github.com/camunda/camunda-security-library/issues/269)) | ⏳ follow-up (upstream) |
| 45 | PT CSRF cookie cross-tab invalidation on logout / auth-rotate | ⏳ follow-up |
|    | Webapp PT awareness ([#53810](https://github.com/camunda/camunda/issues/53810)) | ⏳ follow-up (upstream) |
| 14 | `PhysicalTenantSecurityIT` happy path | ⏭ skipped (deferred to CSL upstream hardening) |
| 15 | `PhysicalTenantSecurityIT` full flow + isolation | ⏭ skipped (deferred to CSL upstream hardening) |
