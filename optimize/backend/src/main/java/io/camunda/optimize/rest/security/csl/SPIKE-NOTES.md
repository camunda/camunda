# Spike: adopt CSL for Optimize (ADR-0036)

Prototype for adopting the Camunda Security Library (CSL) stateful OIDC webapp chain in Optimize,
dropping the custom stateless JWT-cookie stack. See ADR-0036 in camunda-security-library.

## Single switch: legacy vs CSL (no collision)

The legacy Optimize security config and the CSL config both register `SecurityFilterChain` beans
and `@EnableWebSecurity`, so only one may be active. A single flag selects which:

- `optimize.security.csl.enabled=false` (default, absent) -> legacy setup runs
  (`CCSMSecurityConfigurerAdapter` / `CCSaaSSecurityConfigurerAdapter`). Existing deployments are
  unaffected.
- `optimize.security.csl.enabled=true` -> the legacy adapters back off (they carry the inverse
  `@ConditionalOnProperty`) and `OptimizeCamundaSecurityConfig` activates the CSL chains.

## What this prototype contains

- `OptimizeCamundaSecurityConfig` — opt-in wiring. Imports the CSL umbrella
  `CamundaSecurityAutoConfiguration` and provides the SPI beans. Gated on the flag above.
- `OptimizeSecurityPathAdapter` — `SecurityPathPort` with the agreed path split (`/**` webapp
  catch-all, bearer-only `apiPaths()`, empty `unprotectedApiPaths()`, everything else public in
  `unprotectedPaths()` — the bucket the order-0 chain actually matches).
- `OptimizeMembershipAdapter` — `MembershipPort`. Stub returning empty memberships (enough to log
  in; wire the groups-claim extraction for real authorization).
- `OptimizeSessionStoreAdapter` — `SessionStorePort` backed by Optimize's Elasticsearch. Stub;
  NOT registered as a bean (single-node manual testing uses in-memory `HttpSession`).
- `application-csl-spike.yaml` — example config.

No custom webapp chain is needed: CSL now orders the API chain before the webapp chain (ADR-0036),
so the stock webapp chain with a `/**` matcher is the catch-all below the bearer API chain.

## How to run / manually test

1. Build the CSL spike branch (`camunda-security-library`, branch
   `spike/csl-optimize-webapp-support`) with `mvn install` to publish a local build that contains
   the deny-chain toggle and the API/webapp order split.
2. Point the camunda monorepo at that build: bump `version.camunda-security-library` in
   `parent/pom.xml` to the installed version.
3. Set `optimize.security.csl.enabled=true` plus the `camunda.security.*` config in
   `application-csl-spike.yaml` (OIDC issuer/client for your IdP, `method=oidc`,
   `unhandled-paths-chain.enabled=false`).
4. Start Optimize against an OIDC provider (Identity/Keycloak for CCSM, Auth0 for CCSaaS) and
   verify: `GET /` redirects to the IdP; after login the app loads; `GET /api/readyz`,
   `/api/ui-configuration`, `/actuator/**`, `/external/**` are reachable without auth;
   `GET /api/public/**` requires a bearer token.

Single-node in-memory sessions are used unless a `SessionStorePort` bean is added and
`camunda.security.session.persistent.enabled=true`.

## Status

- Compiles against the released CSL API (all host code uses stable CSL SPIs). Correct RUNTIME
  behavior needs the CSL spike build from step 1 (the toggle + order split are behavioral, not new
  API). Not yet booted end to end in this spike.
- The `SessionStorePort` (Elasticsearch) and `MembershipPort` (groups claim) are stubs.
- Legacy security code is not deleted, only switched off by the flag, so the two can be compared.

## Follow-ups (tracked in ADR-0036)

1. Real `SessionStorePort` over Optimize's Elasticsearch + a session index; remove the old
   auth-storage index (the terminated-session store) on upgrade.
2. Real `MembershipPort` (OIDC groups-claim extraction).
3. Config backwards-compatibility layer: prototype present as
   `OptimizeSecurityConfigCompatibilityPostProcessor` (a Spring `EnvironmentPostProcessor`,
   registered in `META-INF/spring/...EnvironmentPostProcessor.imports`, mirroring OC's
   `PersistentWebSessionPropertiesPostProcessor`). It bridges Optimize's existing auth config to
   `camunda.security.*` at low precedence so operators are not forced to migrate. The full
   key-by-key mapping is in `CONFIG-COMPAT.md`; the skeleton implements the representative OIDC /
   API / HSTS keys and deprecation warnings, with the remaining rows marked TODO (and the
   `environment-config.yaml`-only path, vs env vars, as a follow-up).
4. Chain-level boot test (see below).

## Test blind spot to close (important)

Optimize's current auth tests are almost all pure unit tests of helper services and individual
filters. No test boots the real `SecurityFilterChain` beans and drives HTTP through the assembled
chain (the one adapter IT builds the adapter with `null` collaborators and reflects a decoder
method). This is why the Spring Boot 4.0 / Spring Security 7 wiring break passed CI.

Add a chain-level boot test (`@SpringBootTest(webEnvironment = RANDOM_PORT)` or MockMvc importing
the real security beans) asserting, against the assembled chain:

1. Unauthenticated `GET /` -> 302 to the IdP authorize URL.
2. `GET /api/public/**` with no/invalid/wrong-audience bearer -> 401; with a valid bearer -> 2xx.
3. Unauthenticated `GET /api/...` (non-public API served by the catch-all) -> 302; with a valid
   session -> reaches the handler.
4. Each permitAll path (`/api/readyz`, `/api/ui-configuration`, `/api/localization`,
   `/actuator/**`, `/external/**`, static) reachable without auth -> 2xx.
5. A request with a valid session reaches a protected endpoint (proves the SecurityContext is
   populated in-chain).
