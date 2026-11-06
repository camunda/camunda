# Physical-Tenant Smoke Harness (API + Webapp)

> **INTERIM — drop before review.**
> This harness is local scaffolding to exercise per-physical-tenant identity isolation end-to-end:
> the API security chains (`authentication/src/main/java/io/camunda/authentication/pt/`), the
> PT-prefixed webapp routing/serving (#55682), and per-PT durable web sessions (#55777). It is NOT
> part of the production deliverable and MUST be removed in a dedicated drop commit before the PR is
> marked review-ready.

## What it demonstrates

Two surfaces, both functional now:

1. **API chain isolation** via the CSL `CamundaSecurityScopeProvider` (`PhysicalTenantScopeProvider`):
   - Each physical tenant (`default`, `tenanta`, …) gets one `ScopedSecurityDescriptor`; CSL builds
     one `SecurityFilterChain` per descriptor, matching the tenant-first prefix
     `/physical-tenants/<id>/v2/...`.
   - A token minted for tenant A is accepted on A's chain and rejected (401) on tenant B's — each
     chain's JWT decoder is scoped to only that tenant's issuer and audiences.
   - Audience-based isolation: when one Keycloak realm serves multiple tenants, each tenant carries
     its own audience claim; a token with the wrong audience is rejected even when the issuer matches.

2. **Webapp routing + serving** (#55682, #55777):
   - `PhysicalTenantRequestMappingHandlerMapping` registers `/operate`, `/tasklist`, `/admin` PT
     sibling routes alongside `/v2`, so `/physical-tenants/<id>/operate` reaches the real index
     controller instead of falling through to a 404.
   - The served SPA bootstrap config is rewritten so `baseName`/`contextPath` carry the PT prefix
     (`PhysicalTenantWebappClientConfigRewriteAdvice` + the context-path interceptor), so the
     in-browser router accepts the PT-prefixed entry path.
   - Web sessions are persisted per physical tenant (`PhysicalTenantScopedPersistentWebSessionClient`),
     so a login under one tenant is isolated to that tenant's session store.

## Secondary storage — in-memory H2 (RDBMS)

Secondary storage is **in-memory H2 (RDBMS), one database per physical tenant** — no external
datastore. The per-PT RDBMS path (#52027) runs per-tenant Liquibase on startup, so each tenant's
schema is auto-created and isolated (`jdbc:h2:mem:ptdefault`, `…:pttenanta`, …). This sidesteps the
ES per-PT schema-init gap (#51996/#51736); Elasticsearch is not used by this harness.

## Architecture alignment

| Config key | Value | Effect |
|---|---|---|
| `camunda.data.secondary-storage.type` | `rdbms` (H2) | per-PT in-memory store, schema auto-provisioned (#52027) |
| `camunda.physical-tenants.default.security.authentication.providers` | `{}` (no `assigned`) | default PT implicitly carries the full provider set (`oidc` + named providers); declaring `assigned` under `default` is rejected at startup |
| `camunda.physical-tenants.tenanta.security.authentication.providers.assigned` | `[tenanta]` | tenanta PT narrowed to one provider (the inherited default-slot `oidc` is dropped — see the `[#54730]` issuer-isolation cell) with its own clientId/audience override |
| `camunda.security.authorizations.enabled` | `true` | admin role granted to seeded users via `security.initialization` |

The `pt-smoke-test` profile group (`spring.profiles.group.pt-smoke-test=consolidated-auth,broker,identity,tasklist,operate`)
activates the host security graph, the embedded Zeebe broker, and the identity/tasklist/operate
webapps (so the UIs are served).

## Prerequisites

- Docker (only Keycloak now — no Elasticsearch). First run pulls `quay.io/keycloak/keycloak:26.2`
  (~500 MB); ~500 MB RAM per realm.
- Java 21 on `PATH`
- `./mvnw` (repo Maven wrapper)
- `curl` and `jq`
- Free ports: 8080 (OC), 8081/8082/8083 (Keycloak default/tenanta/tenantb realms), 9600 (OC management)

## Running

Three terminals. Run steps in order — each depends on the previous.

### Terminal 1 — Keycloak realms

```bash
./pt-smoke-test-idp.sh
```

Boots three Keycloak containers (`:8081` default, `:8082` tenanta, `:8083` tenantb) via `docker run`.
Wait for `=== PT smoke-test local IdPs ready ===`. Press Ctrl-C to stop them. No datastore container —
secondary storage is in-memory H2 inside the OC process.

### Terminal 2 — OC

```bash
./pt-smoke-test-oc.sh
```

Rebuilds dist + upstream modules **with the frontend build** (`-Dskip.fe.build=false`, so the webapp
assets are bundled) then boots OC under `--spring.profiles.active=pt-smoke-test`. Wait for
`Tomcat started on port 8080`. Logs tee to `/tmp/oc.log`. Press Ctrl-C to stop.

### Terminal 3 — smoke

```bash
./pt-smoke-test-api.sh        # API isolation matrix (Scenario A)
./pt-smoke-test-webapp.sh     # Webapp route dispatching (PT-prefix routing)
```

`pt-smoke-test-api.sh` runs the API assertion cells against `…/v2/authentication/me`; exits 0 (all
PASS) or 1. `pt-smoke-test-webapp.sh` asserts PT-prefixed webapp routing (see below).

## API smoke matrix (Scenario A)

| # | Token | Path | Expected |
|---|---|---|---|
| 1 | default | `/physical-tenants/default/v2/authentication/me` | NOT 401 (own chain) |
| 2 | tenanta | `/physical-tenants/tenanta/v2/authentication/me` | NOT 401 (own chain) |
| 3 | default | `/physical-tenants/tenanta/v2/authentication/me` | 401 (cross-tenant) |
| 4 | tenanta | `/physical-tenants/default/v2/authentication/me` | 401 (cross-tenant) |
| 5 | none | `/physical-tenants/default/v2/authentication/me` | 401 (unauthenticated) |
| 6 | none | `/physical-tenants/tenanta/v2/authentication/me` | 401 (unauthenticated) |
| 7 | dvta | `/physical-tenants/default/v2/authentication/me` | NOT 401 (default's aud) |
| 8 | dvta | `/physical-tenants/tenanta/v2/authentication/me` | 401 (wrong aud) |
| 9 | default | `/v2/authentication/me` | NOT 401 (cluster path) |
| 10 | tenanta | `/v2/authentication/me` | NOT 401 (cluster path) |
| 11 | none | `/v2/authentication/me` | 401 (unauthenticated) |
| 12 | dvta | `/v2/authentication/me` | NOT 401 (cluster path) |

**dvta** = `camunda-pt-default-via-tenanta-client` token — issued by the tenanta Keycloak realm but
carrying `aud=pt-default-via-tenanta-aud` (default's audience, not tenanta's).

## Webapp smoke

### Automated routing check — `pt-smoke-test-webapp.sh`

Unauthenticated, so it asserts **routing**, not a rendered page: a known PT + webapp path must return
something other than 404 (a handler was found; the security chain then redirects 302 → `/login`),
while an unknown PT must 404 (CSL's catch-all rejects the tenant). Cells:

| Path | Expected |
|---|---|
| `/physical-tenants/tenanta/operate` | NOT 404 (OperateIndexController found → 302 to login) |
| `/physical-tenants/tenanta/tasklist` | NOT 404 (TasklistIndexController found) |
| `/physical-tenants/default/operate` | NOT 404 (default PT alias) |
| `/physical-tenants/nosuchpt/operate` | 404 (unknown PT rejected by catch-all chain) |
| `/operate`, `/tasklist` (unprefixed) | NOT 404 (regression guard — existing routes unaffected) |
| `/physical-tenants/tenanta/operate/processes` | NOT 404 (SPA sub-route forwarded to index) |

### Manual visual check (full end-to-end)

The automated script stops at routing. To confirm the SPA actually loads and the session is
PT-scoped, log in through the browser:

1. Open `http://localhost:8080/physical-tenants/tenanta/operate`.
2. You are redirected to the tenanta Keycloak realm; log in as `bob` / `bob`.
3. After `sso-callback` you land back under `/physical-tenants/tenanta/operate` with the SPA loaded —
   the page's `baseName` is PT-prefixed (rewrite advice) and the session cookie is the PT-scoped
   `camunda-session-physical-tenants-tenanta` (per-PT session store, #55777).
4. `http://localhost:8080/physical-tenants/default/operate` (log in as `alice` / `alice`) is a
   separate session under the default tenant.

## `providers.assigned` scenarios (#54730)

Four runnable API scenarios. Each boots OC (Terminal 2), then runs its API script (Terminal 3). The
`assigned` scenarios layer a thin overlay profile on the base config to flip one selection knob:

| Scenario | What it verifies | Boot (Terminal 2) | Run (Terminal 3) |
|---|---|---|---|
| **A — Base** | Core per-PT isolation: own-chain accept, cross-tenant 401, shared-issuer audience isolation, the cluster `/v2` + `/physical-tenants/default` surfaces, the **`[#54730]` cross-issuer cell** (`tenanta` is `[tenanta]`, so a default-realm token is rejected on `/pt/tenanta`), and the `/v2 ≡ /pt/default` identity. | `./pt-smoke-test-oc.sh` | `./pt-smoke-test-api.sh` |
| **B — Default narrowing** | The default tenant's own selection limits the **cluster** surface: `default` is `[tenanta]`, dropping the inherited root default slot, so a default-realm token is rejected on **both** `/v2` and `/pt/default` — proving they resolve from one config (`forPhysicalTenant("default")`). | `./pt-smoke-test-oc.sh pt-smoke-test,pt-smoke-test-default-narrowed` | `./pt-smoke-test-api-default-narrowed.sh` |
| **C — Reserved-`oidc` keep** | A non-default tenant can re-include the default slot: `tenanta` is `[oidc, tenanta]`, so it KEEPS the inherited default slot and a default-realm token is **accepted** on `/pt/tenanta` — the inverse of the base `[#54730]` cell. | `./pt-smoke-test-oc.sh pt-smoke-test,pt-smoke-test-oidc-keep` | `./pt-smoke-test-api-oidc-keep.sh` |
| **D — Two non-default tenants** | Selection among multiple *named* cluster providers + PT-to-PT isolation: `tenanta=[tenanta]` and `tenantb=[tenantb]` (distinct Keycloak realms :8082/:8083, each with its own H2 store), so each rejects the **other's** token even though both are valid cluster providers. Requires the tenantb realm (`pt-smoke-test-idp.sh` starts it on :8083). | `./pt-smoke-test-oc.sh pt-smoke-test,pt-smoke-test-two-tenants` | `./pt-smoke-test-api-two-tenants.sh` |

> Validation failures (e.g. a non-default tenant with no `assigned`, or an unknown id) fail OC
> startup — that path is covered by unit tests (`PhysicalTenantAssignedProvidersValidationTest`),
> not a smoke scenario.

## Files

| Path | Purpose |
|---|---|
| `pt-smoke-test-idp.sh` | Boots three Keycloak containers (default :8081, tenanta :8082, tenantb :8083) — no datastore |
| `pt-smoke-test-oc.sh` | Rebuilds (incl. frontend) + boots OC; optional arg = Spring profiles (default `pt-smoke-test`) for the scenario variants |
| `pt-smoke-test-api.sh` | **Scenario A** — base API isolation matrix (incl. `/v2` ≡ `/pt/default` identity cells) |
| `pt-smoke-test-webapp.sh` | Webapp route dispatching — PT-prefixed routes resolve (302), unknown PT 404s, unprefixed routes unaffected |
| `pt-smoke-test-api-default-narrowed.sh` | **Scenario B** — default narrowed; default-realm token rejected on **both** `/v2` and `/pt/default` |
| `pt-smoke-test-api-oidc-keep.sh` | **Scenario C** — tenanta keeps `oidc`; default-realm token accepted on `/pt/tenanta` |
| `pt-smoke-test-api-two-tenants.sh` | **Scenario D** — two non-default tenants; each rejects the other's (valid cluster) provider |
| `dist/src/test/resources/pt-smoke-test/default-realm.json` | Keycloak realm export — default realm (:8081) |
| `dist/src/test/resources/pt-smoke-test/tenanta-realm.json` | Keycloak realm export — tenanta realm (:8082) |
| `dist/src/test/resources/pt-smoke-test/tenantb-realm.json` | Keycloak realm export — tenantb realm (:8083, Scenario D) |
| `dist/src/main/resources/application-pt-smoke-test.yaml` | Base PT provider config + per-PT H2 secondary storage |
| `dist/src/main/resources/application-pt-smoke-test-default-narrowed.yaml` | Scenario B overlay — `default` assigned `[tenanta]` |
| `dist/src/main/resources/application-pt-smoke-test-oidc-keep.yaml` | Scenario C overlay — `tenanta` assigned `[oidc, tenanta]` |
| `dist/src/main/resources/application-pt-smoke-test-two-tenants.yaml` | Scenario D overlay — adds cluster provider `tenantb` + PT `tenantb` (own H2 store) assigned `[tenantb]` |
| `dist/src/main/resources/application.properties` | `spring.profiles.group.pt-smoke-test{,-basic}=...` entries |

## BASIC-auth variant

A parallel harness validates the same per-tenant chains under `method=basic` (no Keycloak). Users
are seeded per tenant via `camunda.security.initialization`, and isolation is by per-tenant user
store: `alice` in the default store, `bob` in the tenanta store. Each tenant gets its own
auto-provisioned in-memory H2 store (#52027), so no Keycloak and no datastore container are needed.

```bash
# Terminal 1: nothing — no IdP, no datastore
# Terminal 2:
./pt-smoke-test-oc-basic.sh      # boots OC under the pt-smoke-test-basic profile (application-pt-smoke-test-basic.yaml)
# Terminal 3 (after the broker exporter has seeded the initialization users):
./pt-smoke-test-basic.sh
```

Matrix: `alice` (default store) is accepted on `/v2` and `/pt/default`, 401 with a wrong/unknown
password, and — the per-tenant routing isolation — **rejected on `/pt/tenanta`** because that chain
resolves against a separate store (`userServices("tenanta")`). With per-PT H2 provisioning now in
place, `bob` is seeded into tenanta's own store and should authenticate on `/pt/tenanta` — confirm on
the live run (the per-tenant seeding/exporter path).

| Path | Purpose |
|---|---|
| `pt-smoke-test-oc-basic.sh` | Rebuilds + boots OC under the `pt-smoke-test-basic` profile |
| `pt-smoke-test-basic.sh` | Runs the basic-auth per-tenant user-isolation matrix |
| `dist/src/main/resources/application-pt-smoke-test-basic.yaml` | `method=basic` + per-tenant H2 stores + initialization users |
