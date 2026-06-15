# Physical-Tenant API Smoke Harness

> **INTERIM — drop before review.**
> This harness is local scaffolding to exercise the per-physical-tenant API security chains
> implemented in `authentication/src/main/java/io/camunda/authentication/pt/`. It is NOT part
> of the production deliverable and MUST be removed in a dedicated drop commit before the PR
> is marked review-ready.

## What it demonstrates

Per-PT **API** chain isolation via the CSL `CamundaSecurityScopeProvider` (`PhysicalTenantScopeProvider`):

- Two physical tenants (`default`, `tenanta`) are declared in `application-pt-smoke-test.yaml`.
- `PhysicalTenantScopeProvider` emits one `ScopedSecurityDescriptor` per tenant; CSL builds one
  `SecurityFilterChain` per descriptor, matching the tenant-first path prefix
  `/physical-tenants/<id>/v2/...`.
- A token minted for tenant A's Keycloak client is accepted on tenant A's chain and rejected (401)
  on tenant B's chain — the chain's JWT decoder is configured with only that tenant's issuer and
  audiences, so cross-tenant tokens fail validation.
- Audience-based isolation: when the same Keycloak realm serves multiple tenants, each tenant gets
  its own client and audience claim. A token carrying the wrong audience is rejected even when the
  issuer matches.

## Architecture alignment

| Config key | Value | Effect |
|---|---|---|
| `camunda.physical-tenants.default.security.authentication.providers` | `{}` (no `assigned`) | default PT implicitly carries the full provider set (`oidc` + `tenanta`); declaring `assigned` under `default` is rejected at startup |
| `camunda.physical-tenants.tenanta.security.authentication.providers.assigned` | `[tenanta]` | tenanta PT is narrowed to one provider (the inherited default-slot `oidc` is dropped — see the `[#54730]` issuer-isolation cell) with its own clientId/audience override |
| `camunda.security.authorizations.enabled` | `false` | authorizations disabled (orthogonal to chain isolation) |

The `pt-smoke-test` profile group (`spring.profiles.group.pt-smoke-test=consolidated-auth,elasticsearch,broker`)
activates the host security graph, Elasticsearch secondary storage, and the embedded Zeebe broker.
Elasticsearch is required because `PhysicalTenantSearchClientReadersConfiguration` (which creates
per-PT search clients) is `@ConditionalOnSecondaryStorageType(elasticsearch, opensearch)` — with
rdbms the multi-PT readers do not activate and OC fails to start with more than one physical tenant.

## Prerequisites

- Docker with at least 4 GB memory available (ES needs ~1 GB heap; Keycloak adds ~500 MB each)
  - First run pulls `quay.io/keycloak/keycloak:26.2` (~500 MB) and
    `docker.elastic.co/elasticsearch/elasticsearch:8.19.13` (~1.5 GB)
- Java 21 on `PATH`
- `./mvnw` (repo Maven wrapper)
- `curl`, `jq`, and `python3` (used by the ES health-check in `pt-smoke-test-idp.sh`)
- Free ports: 8080 (OC), 8081 (Keycloak default realm), 8082 (Keycloak tenanta realm),
  9200 (Elasticsearch), 9600 (OC management)

## Running

Three terminals. Run steps in order — each step depends on the previous.

### Terminal 1 — Keycloak + Elasticsearch

```bash
./pt-smoke-test-idp.sh
```

Boots one Elasticsearch container (`:9200`) and two Keycloak containers (`:8081`, `:8082`) via
`docker run`. Wait for `=== PT-PoC local IdPs + ES ready ===`.
Press Ctrl-C to stop all three containers.

### Terminal 2 — OC

```bash
./pt-smoke-test-oc.sh
```

Rebuilds dist + upstream modules then boots OC under `--spring.profiles.active=pt-smoke-test`.
Wait for `Tomcat started on port 8080`. Logs tee to `/tmp/oc.log`. Press Ctrl-C to stop.

### Terminal 3 — smoke

```bash
./pt-smoke-test-api.sh
```

Runs 12 assertion cells against `GET /physical-tenants/<id>/v2/authentication/me` and
`GET /v2/authentication/me`. Exits 0 (all PASS) or 1 (at least one FAIL).

## Smoke matrix

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

**dvta** = `camunda-pt-default-via-tenanta-client` token — issued by the tenanta Keycloak realm
but carrying `aud=pt-default-via-tenanta-aud` (default's audience, not tenanta's).

## `providers.assigned` scenarios (#54730)

There are three runnable smoke scenarios. Each boots OC (Terminal 2), then runs its API script
(Terminal 3). The two `assigned` scenarios just layer a thin overlay profile
(`application-<profile>.yaml`) on the base config to flip one selection knob:

| Scenario | What it verifies | Boot (Terminal 2) | Run (Terminal 3) |
|---|---|---|---|
| **A — Base** | Core per-PT isolation: own-chain accept, cross-tenant 401, shared-issuer audience isolation, the cluster `/v2` + `/physical-tenants/default` surfaces, the **`[#54730]` cross-issuer cell** (`tenanta` is `[tenanta]`, so a default-realm token is rejected on `/pt/tenanta`), and the `/v2 ≡ /pt/default` identity. | `./pt-smoke-test-oc.sh` | `./pt-smoke-test-api.sh` |
| **B — Default narrowing** | The default tenant's own selection limits the **cluster** surface: `default` is `[tenanta]`, dropping the inherited root default slot, so a default-realm token is rejected on **both** `/v2` and `/pt/default` — proving they resolve from one config (`forPhysicalTenant("default")`). | `./pt-smoke-test-oc.sh pt-smoke-test,pt-smoke-test-default-narrowed` | `./pt-smoke-test-api-default-narrowed.sh` |
| **C — Reserved-`oidc` keep** | A non-default tenant can re-include the default slot: `tenanta` is `[oidc, tenanta]`, so it KEEPS the inherited default slot and a default-realm token is **accepted** on `/pt/tenanta` — the inverse of the base `[#54730]` cell. | `./pt-smoke-test-oc.sh pt-smoke-test,pt-smoke-test-oidc-keep` | `./pt-smoke-test-api-oidc-keep.sh` |

> Validation failures (e.g. a non-default tenant with no `assigned`, or an unknown id) fail OC
> startup — that path is covered by unit tests (`PhysicalTenantAssignedProvidersValidationTest`),
> not a smoke scenario.

## Files

| Path | Purpose |
|---|---|
| `pt-smoke-test-idp.sh` | Boots ES (`:9200`) and two Keycloak containers (default on :8081, tenanta on :8082) |
| `pt-smoke-test-oc.sh` | Rebuilds + boots OC; optional arg = Spring profiles (default `pt-smoke-test`) for the scenario variants below |
| `pt-smoke-test-api.sh` | **Scenario A** — base API isolation matrix (incl. `/v2` ≡ `/pt/default` identity cells) |
| `pt-smoke-test-api-default-narrowed.sh` | **Scenario B** — default narrowed; default-realm token rejected on **both** `/v2` and `/pt/default` |
| `pt-smoke-test-api-oidc-keep.sh` | **Scenario C** — tenanta keeps `oidc`; default-realm token accepted on `/pt/tenanta` |
| `dist/src/test/resources/pt-smoke-test/default-realm.json` | Keycloak realm export — default realm |
| `dist/src/test/resources/pt-smoke-test/tenanta-realm.json` | Keycloak realm export — tenanta realm |
| `dist/src/main/resources/application-pt-smoke-test.yaml` | Base PT provider config + trimmed diagnostics |
| `dist/src/main/resources/application-pt-smoke-test-default-narrowed.yaml` | Scenario B overlay — `default` assigned `[tenanta]` |
| `dist/src/main/resources/application-pt-smoke-test-oidc-keep.yaml` | Scenario C overlay — `tenanta` assigned `[oidc, tenanta]` |
| `dist/src/main/resources/application.properties` | `spring.profiles.group.pt-smoke-test{,-basic}=...` entries |

## BASIC-auth variant

A parallel harness validates the same per-tenant chains under `method=basic` (no Keycloak). Users
are seeded per tenant via `camunda.security.initialization`, and isolation is by per-tenant user
store: `alice` lives only in the default store, `bob` only in the tenanta store.

```bash
# Terminal 1: Elasticsearch only (no IdP needed)
#   reuse pt-smoke-test-idp.sh's ES, or run your own on :9200
# Terminal 2:
./pt-smoke-test-oc-basic.sh      # boots OC under the pt-smoke-test-basic profile (application-pt-smoke-test-basic.yaml)
# Terminal 3 (after the broker exporter has seeded the initialization users):
./pt-smoke-test-basic.sh
```

Matrix: `alice` (default store) is accepted on `/v2` and `/pt/default`, 401 with a wrong/unknown
password, and — the per-tenant routing isolation — **rejected on `/pt/tenanta`** because that chain
resolves against a separate store (`userServices("tenanta")`).

> **Scope:** seeding a *non-default* tenant's user store (`bob` on `/pt/tenanta`) needs per-PT
> engine/store provisioning, which is separate from this PR's API-security-chain scope. This
> single-engine harness only provisions the default store, so `bob` is never seeded — that cell is
> informational (401 today, 200 once provisioning lands).

| Path | Purpose |
|---|---|
| `pt-smoke-test-oc-basic.sh` | Rebuilds + boots OC under the `pt-smoke-test-basic` profile |
| `pt-smoke-test-basic.sh` | Runs the basic-auth per-tenant user-isolation matrix |
| `dist/src/main/resources/application-pt-smoke-test-basic.yaml` | `method=basic` + per-tenant initialization users |
