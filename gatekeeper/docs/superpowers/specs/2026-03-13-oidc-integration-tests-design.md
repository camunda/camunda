# OIDC Integration Tests Design

**Date:** 2026-03-13
**Status:** Proposed

## Summary

Add OIDC integration tests to the gatekeeper module using Testcontainers with Keycloak, testing both the Bearer JWT API flow and the full OAuth2 webapp login flow against a real identity provider — independent of other Camunda applications.

## Context

The gatekeeper module has integration tests for basic authentication (`BasicAuthIntegrationTest`) using stub SPI implementations and a test Spring Boot application. There are no integration tests for the OIDC authentication path, which is the primary production auth method. The OIDC auto-configuration (`GatekeeperOidcAutoConfiguration`) and its associated security filter chains need end-to-end validation against a real OIDC provider.

## Design

### Test Infrastructure

**Keycloak Testcontainer:**
- Uses `com.github.dasniko:testcontainers-keycloak` to run a Keycloak container
- Realm `gatekeeper-test` configured via JSON realm import file at `src/test/resources/keycloak/gatekeeper-test-realm.json`
- Realm contains:
  - Two users: `demo` (password: `demo`) and `operator` (password: `operator`) — consistent with existing stub test data
  - Confidential client `gatekeeper-test-client` with `client_secret_basic` authentication
  - Direct access grants enabled (for obtaining tokens via password grant in API tests)
  - Redirect URI pointing to the test app's `/sso-callback` callback (using wildcard `*` for port flexibility)
  - Standard OIDC scopes: `openid`, `profile`, `email`

**Dependencies (test scope in `gatekeeper-spring-boot-starter/pom.xml`):**
- `org.testcontainers:testcontainers`
- `org.testcontainers:junit-jupiter`
- `com.github.dasniko:testcontainers-keycloak`

Version properties to add to the parent POM (`gatekeeper/pom.xml`):
- `version.testcontainers` — managed by Spring Boot BOM (no explicit version needed for `org.testcontainers` artifacts)
- `version.testcontainers-keycloak` — e.g., `3.5.1` (not managed by Spring Boot BOM)

**Application configuration (`application-oidc.yml`):**

```yaml
camunda:
  security:
    authentication:
      method: OIDC
      oidc:
        username-claim: preferred_username
        client-id-claim: azp
```

Properties injected dynamically via `@DynamicPropertySource`:
- `camunda.security.authentication.oidc.issuer-uri` — from `keycloak.getAuthServerUrl() + "/realms/gatekeeper-test"`
- `camunda.security.authentication.oidc.client-id` — `gatekeeper-test-client`
- `camunda.security.authentication.oidc.client-secret` — the client's secret from the realm config

**Critical:** `username-claim` must be `preferred_username` (not the default `sub`), because Keycloak's `sub` claim is a UUID. The `StubMembershipResolver` and `StubCamundaUserProvider` key on username strings like `"demo"` and `"operator"`, so the resolved username must match.

**Note on `TestSecurityConfiguration`:** The existing `TestSecurityConfiguration` defines a `UserDetailsService` and `PasswordEncoder` for basic auth. These beans are unused in OIDC mode and harmless — no profile-scoping is needed.

### Test Classes

Two test classes to maintain consistency with the existing `BasicAuthIntegrationTest` style:

**1. `OidcApiIntegrationTest.java`** — API-level tests using `MockMvc`

```
@SpringBootTest(classes = TestComponentApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("oidc")
@Testcontainers
```

Uses `MockMvc` with Bearer token headers, consistent with how `BasicAuthIntegrationTest` uses `MockMvc` with `httpBasic()`. Tokens are obtained from Keycloak's token endpoint via a helper method (password grant).

**2. `OidcWebappLoginIntegrationTest.java`** — Webapp login flow using embedded server

```
@SpringBootTest(classes = TestComponentApplication.class, webEnvironment = RANDOM_PORT)
@ActiveProfiles("oidc")
@Testcontainers
```

Uses Java `HttpClient` with `CookieManager` and `Redirect.NEVER` for the full redirect dance. Requires an embedded server because the flow involves real HTTP redirects between the app and Keycloak.

Both classes share a static `KeycloakContainer` via `@Container`. If additional OIDC test classes are added in the future, a shared base class or `@SharedContainerLifecycle` should be used.

### Test Groups

#### OidcApiIntegrationTest

**1. ProtectedApiTests (Bearer JWT flow)** — `@Nested @DisplayName`
- Unauthenticated request to `/v2/test/identity` → 401
- Valid Bearer token → 200 with resolved identity (username, groups, roles, tenants)
- Invalid/expired token → 401

**2. MembershipResolutionTests (Token claims → membership mapping)** — `@Nested @DisplayName`
- `demo` user token resolves to expected groups/roles/tenants via `StubMembershipResolver`
- `operator` user token resolves to different memberships

**3. UserInfoTests (CamundaUserProvider pipeline)** — `@Nested @DisplayName`
- Valid Bearer token to `/v2/test/user` → 200 with assembled user info (displayName, username, email, components, memberships)

**4. UnprotectedPathTests** — `@Nested @DisplayName`
- `/actuator/health` accessible without auth → 200
- `/v2/license` accessible without auth → 200

#### OidcWebappLoginIntegrationTest

**1. WebappLoginTests (Full OAuth2 redirect flow)** — `@Nested @DisplayName`

Uses Java `HttpClient` with `CookieManager` (session tracking) and `Redirect.NEVER` (manual redirect control).

The redirect dance:
1. GET protected webapp path → 302 to `/oauth2/authorization/{registrationId}`
2. GET authorization redirect → 302 to Keycloak's authorization endpoint
3. GET Keycloak auth endpoint → 200 with HTML login form
4. POST credentials to Keycloak login form action URL (extracted via regex from HTML — Keycloak's form action URL contains a session code) → 302 back to app's `/sso-callback?code=...&state=...`
5. GET callback → Spring exchanges code for tokens, creates session, 302 to original URL
6. GET final redirect with session cookie → 200 with authenticated user identity

Assertions: status 200, response body contains user profile and memberships.

**Note on HTML parsing:** The Keycloak login form action URL is extracted from the HTML response using a simple regex pattern (matching `action="..."` in the form). This is a known coupling to Keycloak's login page structure. If this proves fragile across Keycloak versions, it can be replaced with a more robust approach (e.g., jsoup parsing or Keycloak Admin API).

### Reuse of Existing Test Infrastructure

The existing test application and stub adapters are reused without modification:
- `TestComponentApplication` — minimal Spring Boot app
- `TestApiController` — protected and unprotected endpoints
- `StubMembershipResolver` — hardcoded group/role/tenant mappings
- `StubCamundaUserProvider` — user profile assembly
- `StubSecurityPathProvider` — path declarations (API, webapp, unprotected)
- `StubWebComponentAccessProvider` — component access
- `StubAdminUserCheckProvider` — admin check

The only difference is the active profile (`oidc` vs `basicauth`), which switches the authentication method and activates the OIDC auto-configuration instead of basic auth.

### Out of Scope

- Browser JavaScript / SPA interactions
- CSRF token handling
- Logout flow
- Multi-issuer OIDC configuration
- Token refresh / session persistence
