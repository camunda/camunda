# Spike: adopt CSL for Optimize (ADR-0036)

Prototype for adopting the Camunda Security Library (CSL) stateful OIDC webapp chain in Optimize,
dropping the custom stateless JWT-cookie stack. See ADR-0036 in camunda-security-library.

## Single switch: legacy vs CSL (no collision)

Both the legacy config and the CSL config register `SecurityFilterChain` beans + `@EnableWebSecurity`,
so only one may be active. One flag selects which:

- `optimize.security.csl.enabled=false` (default/absent) -> legacy setup runs
  (`CCSMSecurityConfigurerAdapter` / `CCSaaSSecurityConfigurerAdapter`); existing deployments
  unaffected.
- `optimize.security.csl.enabled=true` -> legacy adapters back off (inverse `@ConditionalOnProperty`)
  and `OptimizeCamundaSecurityConfig` activates the CSL chains.

No custom webapp chain is needed: CSL orders the API chain before the webapp chain (ADR-0036), so the
stock webapp chain with a `/**` matcher is the catch-all below the bearer API chain.

## What this prototype contains

- `OptimizeCamundaSecurityConfig` — opt-in wiring: imports the CSL umbrella
  `CamundaSecurityAutoConfiguration`, provides the SPI beans, gated on the flag.
- `OptimizeSecurityPathAdapter` — `SecurityPathPort`: `/**` webapp catch-all, bearer-only
  `apiPaths()`, empty `unprotectedApiPaths()`, everything else public in `unprotectedPaths()` (the
  bucket the order-0 chain matches). `postLogoutRedirectPath()="/"` (see finding 6).
- `OptimizeOidcAuthenticationEntryPoint` — `OidcAuthenticationEntryPoint` SPI: 401 for `/api/**`
  XHR, 302-to-IdP for navigations (see finding 8).
- `OptimizeMembershipAdapter` — `MembershipPort` stub (empty memberships; enough to log in). Real
  groups-claim extraction is a follow-up.
- `OptimizeSessionStoreAdapter` + `WebSessionDto` — Elasticsearch-backed `SessionStorePort`
  (get/upsert/delete/getAll via `OptimizeElasticsearchClient`), `@Component` under ES + `csl.enabled`.
  Backed by a new `web-session` index (`WebSessionIndex`/`WebSessionIndexES` in `optimize-commons`,
  registered in `ElasticSearchSchemaManager.getAllNonDynamicMappings()` + `DatabaseConstants`).
  `OptimizeCamundaSecurityConfig` imports CSL's `WebSessionConfiguration` (self-gated on
  `session.persistent.enabled`, set by the bridge). ES-only; OpenSearch mirror is a follow-up.
- `OptimizeSecurityConfigCompatibilityPostProcessor` — `EnvironmentPostProcessor` (registered in
  `META-INF/spring.factories`) bridging legacy Optimize auth config to `camunda.security.*` at low
  precedence. Full mapping in `CONFIG-COMPAT.md`.

## How to run / manually test

1. `mvn install` the CSL spike branch (`camunda-security-library`, `spike/csl-optimize-webapp-support`)
   to publish local `0.1.0-SNAPSHOT` (deny-chain toggle + API/webapp order split). `parent/pom.xml`
   already pins `version.camunda-security-library` to it.
2. Set `optimize.security.csl.enabled=true`. The compat bridge sets the `camunda.security.*` defaults
   (`method=oidc`, `unhandled-paths-chain.enabled=false`, `oidc.redirect-uri`) and maps the existing
   `CAMUNDA_OPTIMIZE_IDENTITY_*` env vars, so no extra `camunda.security.*` config is needed.
3. Build the SPA: `cd optimize/client && yarn build`, then rebuild `optimize-backend` so
   `../client/dist` lands on the `webapp` classpath. Without it, `GET /` -> `NoResourceFoundException`
   after login (unrelated to CSL).
4. Start against an OIDC provider (Identity/Keycloak for CCSM, Auth0 for CCSaaS) and verify: `GET /`
   redirects to the IdP; after login the app loads; `/api/readyz`, `/api/ui-configuration`,
   `/actuator/**`, `/external/**` are reachable without auth; `/api/public/**` needs a bearer token.

The OIDC callback reuses Optimize's `{baseUrl}/api/authentication/callback`, already registered on
the Identity-provisioned Keycloak `optimize` client, so no IdP change is needed; CSL derives its
redirection-endpoint listener path from `oidc.redirect-uri` (ADR-0036) to keep them aligned.
`URLRedirectFilter` (SPA routing, runs before security) is patched to pass `/oauth2/**`,
`/sso-callback`, `/login`, `/logout` through; without that, OIDC login initiation loops to `/#`.
Single-node in-memory sessions are used unless a `SessionStorePort` bean is present and
`session.persistent.enabled=true` (the bridge sets it true).

## Status

- Compiles against the released CSL API; correct runtime needs the CSL spike build above (the toggle
  + order split are behavioral, not new API).
- `SessionStorePort` (Elasticsearch) done; `MembershipPort` is a stub.
- Legacy security code is switched off by the flag, not deleted, so the two can be compared.

## Testing findings (running log)

Found while manually testing against a local CCSM/Keycloak setup
(`optimize/docker-compose.ccsm-without-optimize.yml`), and how each was addressed:

1. **Config bridge not loading.** The `EnvironmentPostProcessor` was registered via the
   auto-configuration `.imports` file (which Boot does not read for EPPs), so it never ran and two
   `/**` chains collided at startup. Fixed by registering it in `META-INF/spring.factories`.
2. **Missing OIDC redirect-uri** (`redirectUri cannot be empty`). The bridge now sets it.
3. **Redirect loop via `URLRedirectFilter`.** It bounced `/oauth2/authorization/**` to `/#` before
   security ran. Fixed by passing the CSL login-initiation endpoints through.
4. **`redirect_uri` mismatch.** Identity registers the `optimize` client with
   `/api/authentication/callback`, but CSL used `/sso-callback`. CSL now derives its
   redirection-endpoint path from the configured `redirect-uri`; the bridge points Optimize at
   `{baseUrl}/api/authentication/callback` (no IdP change).
5. **Session API calls 401'd** (SPA loop). `SessionService` resolved the user only from a bearer JWT
   or the legacy cookie; added a branch resolving the user id from the OIDC session's
   `OAuth2AuthenticationToken`.
6. **Logout did nothing, then re-logged the user in.** The legacy `GET /api/authentication/logout`
   only cleaned cookies; a local `SecurityContextLogoutHandler` still left the Keycloak SSO session
   alive (legacy killed it by revoking the refresh token, which does not exist under CSL). Fixed by
   aligning with OC: `Logout.js` POSTs CSL's `/logout` (CSRF header from the request wrapper); for
   fetch/XHR CSL returns `200 {"url": <IdP end-session URL>}` (or `204`), the frontend navigates
   there, ending the SSO session and returning to the `post_logout_redirect_uri`
   (`postLogoutRedirectPath()="/"` -> login). Server session + IdP session both terminated.
7. **`CCSMUserCache` had no user token.** `getCurrentUserAuthToken()` read the legacy cookie; under
   CSL the token lives in the OIDC session's `OAuth2AuthorizedClient`. Fixed to prefer that (loaded
   from an optional `OAuth2AuthorizedClientRepository` keyed by the `OAuth2AuthenticationToken`),
   falling back to the cookie for legacy.
8. **SPA hung on a spinner after logout.** Unauthenticated `/api/**` XHR got a `302 -> IdP` the
   cross-origin `fetch` could not follow (CORS), so `PrivateRoute`'s 401 handler never fired. CSL's
   default entry point returns 401 only for `Authorization`-header (bearer) requests. Fixed with
   `OptimizeOidcAuthenticationEntryPoint`: 401 for `/api/**`, 302 for navigations. (Feedback for CSL:
   its default could content-negotiate `Sec-Fetch-Dest: empty` -> 401 so cookie-session SPAs work
   without a host override.)
9. **User search 403 from Identity — a Keycloak provisioning gap, not a CSL regression.** Under CSL,
   the cache uses the user's OIDC token (finding 7), as legacy used the cookie token. Identity's user
   API needs the `read:users` client role on `camunda-identity-resource-server`, granted by the
   `Optimize` composite realm role. In the local Keycloak the seed never assigned it — `demo` had only
   `Default user role` (and the compose's `KEYCLOAK_USERS_0_ROLES_0=Identity` names a non-existent
   role; it is `ManagementIdentity`) — so the token carried no permissions/audience and Identity 403'd
   (a client-credentials `optimize` token failed the same way). Fixed by granting `demo` the
   `Optimize` realm role and re-logging in; the `camunda-identity` default scope's audience-resolve +
   `permissions.${client_id}` mappers (`fullScopeAllowed=true`) then populate the token. No code change.

## Follow-ups (tracked in ADR-0036)

1. `SessionStorePort`: ES done. Remaining: OpenSearch mirror (`WebSessionIndexOS` +
   `OpenSearchSchemaManager` registration + OS adapter), and removing the old terminated-session index
   on upgrade.
2. Real `MembershipPort` (OIDC groups-claim extraction).
3. Config back-compat: prototype present (`OptimizeSecurityConfigCompatibilityPostProcessor`). Full
   mapping in `CONFIG-COMPAT.md`; representative OIDC/API/HSTS keys implemented, remaining rows TODO
   (plus the `environment-config.yaml`-only path, vs env vars).
4. CSRF: enabled for OC alignment. Frontend done — `request.ts` reads `X-CSRF-TOKEN` into
   `sessionStorage` and echoes it on state-changing requests. Public `/external` share GETs are exempt.
5. Chain-level boot test (below).

## CCSaaS (cloud) parity — what the `cloud` profile did that CSL must still cover

The spike is validated for CCSM. Comparing the legacy `CCSaaSSecurityConfigurerAdapter`, these
SaaS-only concerns still need bringing across (mapping detail in `CONFIG-COMPAT.md`):

- **Cluster-ID sub-path.** Serve Optimize under `server.servlet.context-path=/<clusterId>`
  (Optimize's `container.contextPath`); Spring strips it before matching and `{baseUrl}` includes it,
  so CSL's login/callback/post-logout URLs carry the prefix natively. Replaces the legacy
  `CCSaasRequestAdjustmentFilter` clusterId stripping + `AddClusterIdSubPathToRedirect...`. The
  `/external/api` -> `/api/external` rewrite + static-share serving stay as a small host filter.
- **Org membership + role gate and cluster-ID claim validation.** CSL owns the config
  (`camunda.security.saas.*` needs org-id + cluster-id, `...oidc.organization-id`) but ships no
  org/cluster checks. The host supplies them as `OAuth2TokenValidator<Jwt>` by overriding CSL's
  `TokenValidatorFactory` bean (this is how OC does it) — port Optimize's `RoleValidator`,
  `CustomClaimValidator` (clusterId), and the `hasAccess` org-membership gate there.
- **User-id migration on login.** Legacy migrated stored ownership when the token's
  `https://camunda.com/originalUserId` differs from the subject. CSL has no login-success hook; a CSL
  SPI is planned (see ADR-0036) that Optimize implements to call `UserIdMigrationService`.
- **Auth0 config bridging.** `CONFIG-COMPAT.md` now maps the full `auth.cloud.*` set. The values come
  from Optimize's `environment-config.yaml` (not env vars), so the `EnvironmentPostProcessor` must
  load that source before it can bridge them (follow-up); until then set `camunda.security.*` directly.
- **Obsolete under CSL (do not port):** the cookie-based auth-request repo, split access-token cookie,
  `AuthenticationCookieFilter`, and self-signed session token — all replaced by CSL server sessions.
- **Independent (keep):** the cloud-console M2M clients (`CCSaaSM2MTokenProvider`, `CCSaaS*Client`,
  `CCSaaSUserCache`) use M2M tokens, not the login chain. Verify `CCSaaSUserCache` resolves users via
  the M2M token (not the session token) so it does not regress like `CCSMUserCache` finding 9.

## Test blind spot to close (important)

Optimize's auth tests are almost all unit tests of helpers/filters; none boots the real
`SecurityFilterChain` beans and drives HTTP through the assembled chain. That is why the Spring Boot
4.0 / Spring Security 7 wiring break passed CI.

Add a chain-level boot test (`@SpringBootTest(webEnvironment = RANDOM_PORT)` or MockMvc with the real
security beans) asserting, against the assembled chain:

1. Unauthenticated `GET /` -> 302 to the IdP authorize URL.
2. `GET /api/public/**` with no/invalid/wrong-audience bearer -> 401; valid bearer -> 2xx.
3. Unauthenticated `GET /api/...` (non-public, served by the catch-all) -> 302; valid session ->
   reaches the handler.
4. Each permitAll path (`/api/readyz`, `/api/ui-configuration`, `/api/localization`, `/actuator/**`,
   `/external/**`, static) reachable without auth -> 2xx.
5. A valid session reaches a protected endpoint (proves the SecurityContext is populated in-chain).
