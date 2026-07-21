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
- `OptimizeSessionStoreAdapter` + `WebSessionDto` — real Elasticsearch-backed `SessionStorePort`
  (get/upsert/delete/getAll via `OptimizeElasticsearchClient`), a `@Component` active under ES +
  `csl.enabled`. Backed by a new `web-session` index: `WebSessionIndex` / `WebSessionIndexES` in
  `optimize-commons`, registered in `ElasticSearchSchemaManager.getAllNonDynamicMappings()` and
  `DatabaseConstants.WEB_SESSION_INDEX_NAME`. `OptimizeCamundaSecurityConfig` imports CSL's
  `WebSessionConfiguration` (self-gated on `session.persistent.enabled`, which the bridge sets
  true). ES-only; an OpenSearch mirror (`WebSessionIndexOS` + `OpenSearchSchemaManager` entry + an
  OS adapter) is the remaining follow-up.

No custom webapp chain is needed: CSL now orders the API chain before the webapp chain (ADR-0036),
so the stock webapp chain with a `/**` matcher is the catch-all below the bearer API chain.

## How to run / manually test

1. Build the CSL spike branch (`camunda-security-library`, branch
   `spike/csl-optimize-webapp-support`) with `mvn install` to publish the local `0.1.0-SNAPSHOT`
   build that contains the deny-chain toggle and the API/webapp order split.
2. `version.camunda-security-library` in `parent/pom.xml` is already pinned to `0.1.0-SNAPSHOT`,
   so no further change is needed — the local install from step 1 resolves.
3. Set `optimize.security.csl.enabled=true`. The compat bridge sets the `camunda.security.*`
   defaults (`method=oidc`, `unhandled-paths-chain.enabled=false`, `oidc.redirect-uri`) and maps
   the existing `CAMUNDA_OPTIMIZE_IDENTITY_*` env vars to the OIDC client config, so no extra
   `camunda.security.*` config is required. See `CONFIG-COMPAT.md` for the full mapping.
4. Build the frontend so the SPA is served: `cd optimize/client && yarn build` (Vite, outputs
   `dist/`), then rebuild `optimize-backend` so `../client/dist` is copied to the `webapp`
   classpath. Without it, `GET /` returns `NoResourceFoundException` (no `index.html`) after login.
   This is unrelated to CSL; a plain backend run does not build the SPA.
5. Start Optimize against an OIDC provider (Identity/Keycloak for CCSM, Auth0 for CCSaaS) and
   verify: `GET /` redirects to the IdP; after login the app loads; `GET /api/readyz`,
   `/api/ui-configuration`, `/actuator/**`, `/external/**` are reachable without auth;
   `GET /api/public/**` requires a bearer token.

The OIDC callback reuses Optimize's existing path `{baseUrl}/api/authentication/callback`, which the
Identity-provisioned Keycloak `optimize` client already registers as a valid redirect URI, so no
IdP client change is needed. CSL derives its redirection-endpoint listener path from
`camunda.security.authentication.oidc.redirect-uri` (ADR-0036), so the sent `redirect_uri` and the
listener stay aligned on `/api/authentication/callback`.

Note: Optimize's `URLRedirectFilter` (SPA routing) runs before the security chain and redirects
unknown paths to `/#`. It is patched to pass the CSL login-initiation endpoints (`/oauth2/**`,
`/sso-callback`, `/login`, `/logout`) through to Spring Security; the callback
`/api/authentication/callback` already passes because `/api` is in the filter's allow-list. Without
that passthrough, OIDC login initiation gets bounced to `/#` in a redirect loop.

Single-node in-memory sessions are used unless a `SessionStorePort` bean is added and
`camunda.security.session.persistent.enabled=true`.

## Status

- Compiles against the released CSL API (all host code uses stable CSL SPIs). Correct RUNTIME
  behavior needs the CSL spike build from step 1 (the toggle + order split are behavioral, not new
  API). Not yet booted end to end in this spike.
- The `SessionStorePort` (Elasticsearch) and `MembershipPort` (groups claim) are stubs.
- Legacy security code is not deleted, only switched off by the flag, so the two can be compared.

## Testing findings (running log)

Matters found while manually testing the spike against a local CCSM/Keycloak setup
(`optimize/docker-compose.ccsm-without-optimize.yml`), and how each was addressed:

1. **Config bridge not loading.** The `EnvironmentPostProcessor` was registered via the
   auto-configuration `...EnvironmentPostProcessor.imports` file, which Spring Boot does not read
   for EPPs. It never ran, so `method`/`unhandled-paths-chain.enabled` stayed unset and two `/**`
   chains (basic-auth webapp via `matchIfMissing` + deny chain) collided at startup. Fixed by
   registering the bridge in `META-INF/spring.factories`.
2. **Missing OIDC redirect-uri.** CSL's authorization-code `ClientRegistration` requires a
   redirect-uri; startup failed with `redirectUri cannot be empty`. The bridge now sets it.
3. **Redirect loop via `URLRedirectFilter`.** Optimize's SPA-routing filter runs before the
   security chain and bounced `/oauth2/authorization/**` to `/#` (confirmed by HAR: the `/#` 302
   carried no CSL security headers). Fixed by passing the CSL login-initiation endpoints through.
4. **`redirect_uri` mismatch with the pre-provisioned IdP client.** Identity registers the Keycloak
   `optimize` client with the legacy callback `/api/authentication/callback`, but CSL used
   `/sso-callback`. Rather than force operators to change their IdP client, CSL was made to derive
   its redirection-endpoint path from the configured `redirect-uri` (CSL spike, ADR-0036), and the
   bridge points Optimize at `{baseUrl}/api/authentication/callback`.

5. **Session-authenticated API calls 401'd** (`Could not extract request user!`), causing the SPA
   to loop (`/api/identity/current/user`, `/api/process/overview`). Optimize's
   `SessionService.getRequestUserOrFailNotAuthorized` resolved the user only from a bearer
   `JwtAuthenticationToken` or the legacy `X-Optimize-Authorization` cookie; under CSL the browser
   is authenticated via the OIDC session (`OAuth2AuthenticationToken` in the `SecurityContext`).
   Added a branch that resolves the user id (principal name = `sub`) from that token. Broader
   follow-up: other user-resolution sites in Optimize (and the `MembershipPort` stub) likely need
   the same CSL-principal bridge for full functionality.

6. **Logout did nothing, then logged the user straight back in.** The SPA called Optimize's `GET
   /api/authentication/logout`, which ran only the legacy cookie cleanup (revoke refresh token,
   delete `X-Optimize-Authorization`) and never touched the CSL server session. A first fix added a
   `SecurityContextLogoutHandler` to that endpoint, but that only invalidates the *local* session —
   the Keycloak SSO session stayed alive, so the next `/oauth2/authorization/oidc` hop silently
   re-authenticated. Legacy CCSM had ended the IdP session by revoking the refresh token at
   Keycloak's logout endpoint; under CSL there is no refresh-token cookie, so that path was a no-op.
   Fixed by aligning with OC: the SPA (`Logout.js`) now POSTs CSL's server-side `/logout` (CSRF
   header from the request wrapper). For fetch/XHR CSL responds `200 {"url": <IdP end-session URL>}`
   (or `204` when the IdP publishes no `end_session_endpoint`); the frontend navigates the browser
   to that URL, which ends the Keycloak SSO session and returns to the CSL-configured
   `post_logout_redirect_uri`. This invalidates the server session (removed from the
   `SessionStorePort`) *and* the IdP session. Optimize's `URLRedirectFilter` already passes `/logout`
   through to Spring Security. The legacy `GET /api/authentication/logout` endpoint stays for the
   flag-off path.
7. **`CCSMUserCache` could not load users** (`WARN … Could not retrieve user because no user token
   present`). `ccsmTokenService.getCurrentUserAuthToken()` read the user access token from the old
   Optimize cookie; under CSL that token lives in the OIDC session's `OAuth2AuthorizedClient`.
   Fixed: `getCurrentUserAuthToken()` now prefers the CSL access token (loaded from the injected
   `OAuth2AuthorizedClientRepository` keyed by the current `OAuth2AuthenticationToken`) and falls
   back to the cookie for the legacy setup. Same Keycloak access token the Identity SDK expects, so
   user lookups work. The repository is injected as an optional `ObjectProvider` (absent in legacy
   CCSM). This is the concrete instance of the host-side token/user-resolution rewiring called out
   in ADR-0036; a production adoption should apply the same bridge wherever the user token is read.
8. **After logout the SPA hung on a spinner.** Once the session was gone, the SPA's XHR calls
   (`/api/token`, `/api/dashboard/management`, `/api/process/overview`) were answered by the webapp
   chain with `302 -> /oauth2/authorization/oidc -> Keycloak`; the browser followed that redirect
   cross-origin as a `fetch`, CORS blocked it (`OPTIONS 405`, `GET` status 0), so the frontend's
   `PrivateRoute` 401 handler never fired. CSL's default OIDC entry point returns 401 only for
   requests carrying an `Authorization` header (bearer); Optimize's cookie-session XHR has none.
   Fixed host-side via CSL's `OidcAuthenticationEntryPoint` SPI:
   `OptimizeOidcAuthenticationEntryPoint` returns 401 for `/api/**` (the SPA's XHR surface) and keeps
   the 302-to-IdP redirect for real navigations. This restores Optimize's legacy contract (API ->
   401 -> `PrivateRoute` reloads -> navigation -> login). Worth feeding back to CSL: its default
   entry point could content-negotiate (XHR / `Sec-Fetch-Dest: empty` -> 401) so cookie-session SPAs
   work without a host override.
9. **`CCSMUserCache` user *search* fails with 403 from Identity.** After #7, `getUserById` works but
   `users().search(userToken)` returns 403 (authenticated, not authorized). The decoded token is a
   normal `optimize` token (`iss` correct, `azp=optimize`, `scope=openid email profile` — a superset
   of the legacy Identity-SDK `openid email`; `aud=[optimize, account]`,
   `resource_access=[account]`), so scope is not the gap. Key point (confirmed with the team): **OC
   does not resolve arbitrary users under OIDC** — it only searches/lists users when it stores them
   itself (basic auth). So searching Identity for users with the requesting user's token is not an
   OIDC-aligned pattern. Treat this as a design follow-up (how Optimize should resolve/search users
   under OIDC — e.g. a service-account token, or no user search in OIDC mode), not a token patch. A
   temporary `SPIKE-DEBUG` claim-decode log is in `CCSMUserCache.searchUsersInIdentity` and must be
   removed before merge.

## Follow-ups (tracked in ADR-0036)

1. `SessionStorePort` over Optimize's Elasticsearch + `web-session` index — **done (ES-only)**.
   Remaining: an OpenSearch mirror (`WebSessionIndexOS` + `OpenSearchSchemaManager` registration +
   an OS-backed adapter, `@Conditional(OpenSearchCondition.class)`), and removing the old
   auth-storage index (the terminated-session store) on upgrade.
2. Real `MembershipPort` (OIDC groups-claim extraction).
3. Config backwards-compatibility layer: prototype present as
   `OptimizeSecurityConfigCompatibilityPostProcessor` (a Spring `EnvironmentPostProcessor`,
   registered in `META-INF/spring.factories`, mirroring OC's
   `PersistentWebSessionPropertiesPostProcessor`). It bridges Optimize's existing auth config to
   `camunda.security.*` at low precedence so operators are not forced to migrate. The full
   key-by-key mapping is in `CONFIG-COMPAT.md`; the skeleton implements the representative OIDC /
   API / HSTS keys and deprecation warnings, with the remaining rows marked TODO (and the
   `environment-config.yaml`-only path, vs env vars, as a follow-up).
4. CSRF: enabled for alignment with OC (`camunda.security.csrf.enabled=true`). The frontend part is
   done in this spike — `optimize/client/src/modules/request.ts` now reads the `X-CSRF-TOKEN`
   response header into `sessionStorage` and sends it back on POST/PUT/PATCH/DELETE, mirroring
   Operate/Tasklist (single central wrapper, ~half a day incl. tests in `request.test.ts`). Public
   `/external` share endpoints are anonymous GETs and are CSRF-exempt via `unprotectedPaths()`.
5. Chain-level boot test (see below).

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
