# Spike: adopt CSL for Optimize (ADR-0036)

This package is a **prototype**, not production code. It shows how Optimize adopts the Camunda
Security Library (CSL) stateful OIDC webapp chain and drops its custom stateless cookie stack.
See ADR-0036 in the camunda-security-library repo for the decision and the pros/cons.

## What this prototype contains

- `OptimizeSecurityPathAdapter` — the `SecurityPathPort`, with the agreed path split
  (bearer-only API vs public-under-/api vs public-non-api vs `/**` webapp catch-all). Concrete.
- `OptimizeWebappSecurityConfiguration` — Optimize's webapp chain. Reuses the CSL
  `ScopedWebappSecurityChainBuilder.buildOidcWebappChain` (the same chain OC uses) but registers it
  at a catch-all order **below** the bearer API chain. Concrete.
- `OptimizeCamundaSecurityConfig` — the opt-in wiring. Imports the CSL pieces and the SPI beans.
- `OptimizeSessionStoreAdapter` — `SessionStorePort` backed by Optimize's Elasticsearch. **Stub.**
- `OptimizeMembershipAdapter` — `MembershipPort`. **Stub** (groups-claim extraction to be wired).
- `application-csl-spike.yaml` — example properties.

## Key spike findings

### 1. Ordering: a `/**` webapp chain must sort below the bearer API chain
CSL's stock `OidcWebappSecurityConfiguration` runs at `ORDER_WEBAPP_API = 1`, the same order as the
API chain. That is fine for OC (disjoint matchers) but not for Optimize, whose webapp matcher is
`/**` and overlaps the API paths. If the `/**` webapp chain sorts first it shadows the bearer API
chain. So Optimize registers its own webapp chain at order 2 (below the API chain) instead of using
the stock one. **Recommended CSL follow-up:** make the webapp chain's order configurable (or add a
catch-all webapp variant) so a host can opt into "catch-all below API" without re-declaring the bean.
Until then, Optimize imports the CSL pieces individually rather than via the
`CamundaSecurityAutoConfiguration` umbrella (the umbrella bundles the order-1 webapp chain).

### 2. Deny chain must be suppressible (DONE in CSL this spike)
The always-on `protectedUnhandledPathsSecurityFilterChain` also matches `/**` and would collide with
Optimize's `/**` webapp chain at startup. CSL now supports
`camunda.security.unhandled-paths-chain.enabled=false` (added in the CSL spike branch). A follow-up
should investigate removing the deny chain entirely (tech debt).

### 3. CSL version bump
The camunda monorepo pins CSL `0.1.0-alpha50`. The deny-chain toggle and the
`unprotectedApiPaths()` Javadoc fix are newer, so adopting this needs a CSL release bump.

### 4. Session store reuses existing Elasticsearch
Optimize already runs Elasticsearch and already writes session state to it
(`TerminatedSessionService`). The `SessionStorePort` adapter writes the server-side session to a
dedicated Optimize index, mirroring OC's `SessionStoreAdapter` (retry transient search failures on
upsert). A shared store keeps scaling affinity-free (no sticky load balancer). This is the piece that
retires the split cookie, the terminated-session blacklist, and the SameSite-only CSRF.

## Code that this adoption REMOVES from Optimize (not deleted in this spike)

- `rest/security/AbstractSecurityConfigurerAdapter` and the `ccsm` / `cloud` subclasses
- `service/security/SessionService`, `AuthCookieService`, `TerminatedSessionService`, `CCSMTokenService`
- `rest/security/AuthenticationCookieFilter`, `ccsm/CCSMAuthenticationCookieFilter`,
  `CustomPreAuthenticatedAuthenticationProvider`
- the custom Camunda Identity SDK login flow (CCSM) and the Auth0 success-handler cookie minting

## Test blind spot to close (important)

Optimize's current auth tests are almost all pure unit tests of helper services and individual
filters. **No test boots the real `SecurityFilterChain` beans and drives HTTP through the assembled
chain.** The one adapter-touching IT builds the adapter with `null` collaborators and reflects a
decoder method. This is why the Spring Boot 4.0 / Spring Security 7 wiring break passed CI.

Adopting CSL is the moment to close this. Add a chain-level boot test
(`@SpringBootTest(webEnvironment = RANDOM_PORT)` or MockMvc that imports the real security beans)
that asserts, against the framework-assembled chain:

1. Unauthenticated `GET /` (a webapp path) -> 302 redirect to the IdP authorize URL.
2. Unauthenticated `GET /api/public/**` with no/invalid/wrong-audience bearer -> 401
   (the missing NEGATIVE case), and with a valid bearer -> 2xx.
3. Unauthenticated `GET /api/...` (a non-public API path served by the webapp catch-all) -> 302,
   and with a valid session -> reaches the handler (proves the session is honoured).
4. Each permitAll path (`/api/readyz`, `/api/ui-configuration`, `/api/localization`,
   `/actuator/**`, `/external/**`, static) reachable without auth -> 2xx.
5. A request with a valid session cookie reaches a protected endpoint (proves the chain populates
   the SecurityContext in-chain).

Because these run against the assembled chain, any change in matcher semantics, entry-point
registration, filter ordering, or session/CSRF defaults from a future framework upgrade flips at
least one assertion. Much of this is now CSL's responsibility (CSL has its own chain tests), but the
host-specific path categorisation and the catch-all-below-API ordering still need this host boot
test.

## What is NOT done in this spike

- The session store adapter is a stub (no Elasticsearch index/read/write).
- The membership adapter is a stub (no groups-claim extraction).
- The `pom.xml` CSL dependency is not added and the module is not compiled (full monorepo build of
  Optimize-on-CSL is beyond a single spike; the artifacts above show the shape and the integration
  points).
- Legacy Optimize security code is not deleted.
