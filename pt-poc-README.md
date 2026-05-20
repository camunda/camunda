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

### Browser

Open `http://localhost:8080/physical-tenant/tenanta/whoami`. Expected flow:

1. Redirect to `http://localhost:8082/realms/tenanta/protocol/openid-connect/auth?...`
2. Log in as `bob` / `bob`
3. Return to OC and end up at `/physical-tenant/tenanta/whoami`
4. JSON body: `{"tenantId":"tenanta","principal":"bob"}` (the principal claim depends on the Keycloak realm config)

## What's where

| Path | Purpose |
|---|---|
| `pt-poc-idp.sh` | Boots two `KeycloakContainer`s on fixed host ports 8081/8082 from the realm exports |
| `pt-poc-oc.sh` | Rebuilds + boots OC under the `pt-poc` profile |
| `dist/src/test/resources/pt-poc/*.json` | Keycloak realm exports (one client + one test user each) |
| `dist/src/main/resources/application-pt-poc.yaml` | OC's PoC-specific Spring config (tenant A's OIDC provider, security DEBUG logging) |
| `dist/src/main/resources/application.properties` | `spring.profiles.group.pt-poc=consolidated-auth,pt-security,rdbmsH2` lives here |
| `authentication/src/main/java/io/camunda/authentication/pt/` | Per-tenant Spring Security wiring |
| `/tmp/oc.log` | OC's stdout/stderr, including Spring Security DEBUG + FilterChainProxy TRACE |

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

| #  | Task                                                         | State       |
|----|--------------------------------------------------------------|-------------|
| 1  | Profile scaffold + verify CSL opts out                       | ✅ done      |
| 2  | Keycloak realm exports                                       | ✅ done      |
| 3  | `PtPocLocalIdpRunner` standalone `main()`                    | ✅ done      |
| 4  | Walking skeleton — one tenant, end-to-end login              | ✅ done      |
| 5  | **Add default tenant prefixed chain + per-chain cookie isolation** | 🔄 in progress |
| 6  | Extract `TenantSecuritySlice` + `PerTenantSecurityChainFactory` | ⏳ pending   |
| 7  | Extract `PhysicalTenantRedirectUriRewriter` + unit test      | ⏳ pending   |
| 8  | Extract `PerTenantOidcRegistry` + consume `providers.assigned` | ⏳ pending   |
| 9  | Wire per-tenant `WebSessionRepository` against per-tenant storage | ⏳ pending   |
| 10 | Extract `PhysicalTenantCookieSerializer` + unit test         | ⏳ pending   |
| 11 | API chain — shared decoder + per-chain issuer allowlist      | ⏳ pending   |
| 12 | Default tenant unprefixed access-path chains                 | ⏳ pending   |
| 13 | Generalise registration via `PhysicalTenantResolver.getAll()` | ⏳ pending   |
| 14 | `PhysicalTenantSecurityIT` happy path                        | ⏳ pending   |
| 15 | `PhysicalTenantSecurityIT` full flow + isolation             | ⏳ pending   |
| 16 | Manual browser smoke test                                    | ⏳ pending   |

**What currently works:**

- Two Keycloak realms boot on `:8081` (default) and `:8082` (tenanta).
- OC boots under `pt-poc` against in-memory H2 (no Elasticsearch dependency).
- Single `SecurityFilterChain` for tenant A wired at `/physical-tenant/tenanta/**`.
- Browser flow reaches Keycloak, returns to the callback, and reaches the token+userinfo exchange.

**What is not yet wired** (Tasks 5–16):

- Default-tenant chain.
- Per-chain session cookie isolation (`name=camunda-session-<tenant>; Path=/physical-tenant/<tenant>`).
- Per-tenant `WebSessionRepository` against tenant-dedicated secondary storage.
- API chain (`/v2/physical-tenants/<tenant>/**`) with cross-tenant issuer allowlist.
- Default tenant's unprefixed access-path chains.
- End-to-end `PhysicalTenantSecurityIT`.
