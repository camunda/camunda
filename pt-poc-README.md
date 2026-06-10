# Physical-Tenant API Smoke Harness

> **INTERIM — drop before review.**
> This harness is local scaffolding to exercise the per-physical-tenant API security chains
> implemented in `authentication/src/main/java/io/camunda/authentication/pt/`. It is NOT part
> of the production deliverable and MUST be removed in a dedicated drop commit before the PR
> is marked review-ready.

## What it demonstrates

Per-PT **API** chain isolation via the CSL `CamundaSecurityScopeProvider` (`PhysicalTenantScopeProvider`):

- Two physical tenants (`default`, `tenanta`) are declared in `application-pt-poc.yaml`.
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
| `camunda.physical-tenants.default.security.authentication.providers.assigned` | `[oidc, tenanta]` | default PT uses two providers |
| `camunda.physical-tenants.tenanta.security.authentication.providers.assigned` | `[tenanta]` | tenanta PT uses one provider with its own clientId/audience override |
| `camunda.security.authorizations.enabled` | `false` | authorizations disabled (orthogonal to chain isolation) |

The `pt-poc` profile group (`spring.profiles.group.pt-poc=consolidated-auth,elasticsearch,broker`)
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
- `curl`, `jq`, and `python3` (used by the ES health-check in `pt-poc-idp.sh`)
- Free ports: 8080 (OC), 8081 (Keycloak default realm), 8082 (Keycloak tenanta realm),
  9200 (Elasticsearch), 9600 (OC management)

## Running

Three terminals. Run steps in order — each step depends on the previous.

### Terminal 1 — Keycloak + Elasticsearch

```bash
./pt-poc-idp.sh
```

Boots one Elasticsearch container (`:9200`) and two Keycloak containers (`:8081`, `:8082`) via
`docker run`. Wait for `=== PT-PoC local IdPs + ES ready ===`.
Press Ctrl-C to stop all three containers.

### Terminal 2 — OC

```bash
./pt-poc-oc.sh
```

Rebuilds dist + upstream modules then boots OC under `--spring.profiles.active=pt-poc`.
Wait for `Tomcat started on port 8080`. Logs tee to `/tmp/oc.log`. Press Ctrl-C to stop.

### Terminal 3 — smoke

```bash
./pt-poc-api-smoke.sh
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

## Files

| Path | Purpose |
|---|---|
| `pt-poc-idp.sh` | Boots ES (`:9200`) and two Keycloak containers (default on :8081, tenanta on :8082) |
| `pt-poc-oc.sh` | Rebuilds + boots OC under the pt-poc profile |
| `pt-poc-api-smoke.sh` | Runs the 12-cell API isolation matrix |
| `dist/src/test/resources/pt-poc/default-realm.json` | Keycloak realm export — default realm |
| `dist/src/test/resources/pt-poc/tenanta-realm.json` | Keycloak realm export — tenanta realm |
| `dist/src/main/resources/application-pt-poc.yaml` | PT provider config + trimmed diagnostics |
| `dist/src/main/resources/application.properties` | `spring.profiles.group.pt-poc=...` entry |
