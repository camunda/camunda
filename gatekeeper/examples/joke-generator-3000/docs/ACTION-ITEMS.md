# Gatekeeper — Action Items from Validation Test

## Must Fix (blocks adoption by non-Camunda consumers)

| # | Action | Why |
|---|--------|-----|
| 1 | `getCamundaAuthentication()` must return null (not throw) for unauthenticated requests | Every controller needs a `SecurityContextHolder` pre-check, defeating the provider abstraction |
| 2 | Add content-negotiation to `WebappRedirectStrategy` and form login handlers — 302 for `Accept: text/html`, 204/JSON for APIs | Login, logout, and error flows only work for SPAs; any SSR app needs JS workarounds |
| 3 | Add `@AutoConfiguration(after = JacksonAutoConfiguration.class)` to `GatekeeperAuthAutoConfiguration` | `ObjectMapper` not available at bean creation time without manual workaround |
| 4 | Document the required "magic paths" in the integration guide: `/login`, `/sso-callback`, `/oauth2/**`, `/post-logout` | Implementers discover these by debugging 404s |
| 5 | Document that the app must serve `GET /login` for basic auth | 404 on login redirect with no explanation |

## Should Fix (significant developer experience improvements)

| # | Action | Why |
|---|--------|-----|
| 6 | Make `LOGIN_URL` configurable via `camunda.security.authentication.login-url` | Hardcoded `/login` can't be changed without overriding the filter chain |
| 7 | Refactor `CamundaUserInfo` — builder pattern, remove SaaS-specific fields (`salesPlanType`, `c8Links`) | 10-param constructor with unused fields is awkward for external consumers |
| 8 | Provide a `SimpleSecurityPathProvider` base class with sensible defaults | 5 methods returning empty sets is boilerplate for simple apps |
| 9 | Implement Spring-native OIDC property support (`spring.security.oauth2.client.*`) | Blocks adoption by teams using standard Spring conventions |
| 10 | Document OIDC logout configuration (`idp-logout-enabled`, required paths, Keycloak realm role mapper) | Full OIDC flow undocumented |
| 11 | Document Spring Boot version compatibility requirement | Binary incompatibility with no error message |

## Nice to Have (polish)

| # | Action | Why |
|---|--------|-----|
| 12 | Add optional declarative role-based path protection (`@RequiresRole` or path config) | Currently every role check is manual in controllers |
| 13 | Document CSP `script-src 'self'` impact on inline JS | Silent failure with no visible error |
| 14 | Document that role-based access is the app's responsibility, not gatekeeper's | Integration guide implies gatekeeper handles it |
| 15 | Log a warning when gatekeeper detects SSR responses with restrictive CSP | Helps developers diagnose silent JS failures |
