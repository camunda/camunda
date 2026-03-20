# Gatekeeper Validation Test — Summary Report

## Overview

We built "Joke Generator 3000", a standalone Spring Boot application that uses gatekeeper
(`0.1.0-SNAPSHOT`) as its authentication library. The goal was to simulate a real implementing
team discovering how well gatekeeper works as a standalone library, and to capture documentation
gaps, library gaps, and developer experience issues.

**Team**: 5 AI agents (Software Architect, UI Designer, Backend Engineer, Frontend Engineer,
QA Engineer) working from gatekeeper's integration guide.

**App**: Server-side rendered (Thymeleaf) with two auth modes — basic auth (Postgres user store)
and OIDC (Keycloak). Landing page (public), joke browser (authenticated), joke admin (role-based).

**Infrastructure**: Docker Compose with Postgres (shared between app and Keycloak) and Keycloak
for OIDC.

## Results

### Test Execution Results

| Suite | Tests | Passed | Failed |
|-------|-------|--------|--------|
| Basic Auth (18 cases) | Public access, login/logout, role enforcement, API auth, security headers | 18 | 0 |
| OIDC (15 cases) | Public access, Keycloak token grant, Bearer token API, role enforcement, security headers | 15 | 0 |
| **Total** | **33** | **33** | **0** |

Tests are automated as shell scripts using `curl`:
- `tests/test-basic-auth.sh` — requires Postgres + app with `basic` profile
- `tests/test-oidc.sh` — requires Postgres + Keycloak + app with `oidc` profile

### What Worked

- Gatekeeper compiles and integrates as a standalone Maven dependency
- SPI pattern (SecurityPathProvider, MembershipResolver, CamundaUserProvider) is clean and
  extensible
- OIDC login flow works end-to-end with Keycloak
- Basic auth credential validation works via Spring Security's UserDetailsService
- Role-based access via `CamundaAuthentication.authenticatedRoleIds()` works
- OIDC IdP-initiated logout works (with JS workaround for JSON redirect)
- `@ConditionalOnMissingBean` back-off works — app can override any bean
- Both Spring profiles (basic/oidc) work with a single SecurityPathProvider

### What Didn't Work Out of the Box

The app required **7 workarounds** before it was functional:

| Issue | Workaround |
|-------|-----------|
| `getCamundaAuthentication()` throws on null auth | Check `SecurityContextHolder` before every gatekeeper API call |
| Login success returns 204 (not redirect) | JavaScript fetch + manual redirect |
| Login failure returns JSON 401 (not redirect) | JavaScript error display |
| Logout returns JSON with redirect URL (not 302) | JavaScript fetch + parse JSON + navigate |
| CSP blocks inline scripts | Move all JS to external files |
| `ObjectMapper` bean not available | Declare explicit `ObjectMapper` bean |
| Flyway auto-config missing in Spring Boot 4.x | Switch to Hibernate `ddl-auto: update` + `data.sql` |

## Documentation Gaps (8)

Issues where the integration guide was missing information an implementer needs:

1. **Hardcoded `/login` path** — not mentioned anywhere
2. **`/sso-callback` must be in `webappPaths()`** — not mentioned
3. **`/oauth2/**` must be in `webappPaths()`** — not mentioned
4. **`/post-logout` must be in `webappPaths()`** — not mentioned
5. **App must serve `GET /login` controller** — gatekeeper only handles POST
6. **`CamundaUserInfo` 10-param constructor** — no docs on which fields are optional
7. **Role-based access is app's responsibility** — guide implies gatekeeper handles it
8. **Spring Boot version compatibility** — major version must match gatekeeper's
9. **OIDC logout configuration** — `idp-logout-enabled` property not documented

## Library Gaps (11)

Issues where the library itself needs improvement:

| # | Gap | Severity | Recommendation |
|---|-----|----------|----------------|
| 1 | Hardcoded `LOGIN_URL = "/login"` | Medium | Make configurable via property |
| 2 | `CamundaUserInfo` coupled to Camunda SaaS | Medium | Builder pattern, remove SaaS fields |
| 3 | No declarative role-based path protection | Low | Optional `@RequiresRole` or path config |
| 4 | No standard Spring OIDC property support | Medium | Implement Spring-native config (planned) |
| 5 | `SecurityPathProvider` too many methods | Low | Provide base class with defaults |
| 6 | `getCamundaAuthentication()` throws on null | **High** | Return null for unauthenticated requests |
| 7 | `ObjectMapper` bean ordering | Medium | Add `@AutoConfiguration(after = JacksonAutoConfiguration.class)` |
| 8 | Login success returns 204 (SPA-only) | **High** | Content-negotiate: 302 for browsers, 204 for APIs |
| 9 | Login failure returns JSON (SPA-only) | **High** | Use redirect handler for form login |
| 10 | Logout returns JSON redirect (SPA-only) | **High** | Content-negotiate: 302 for browsers, JSON for APIs |
| 11 | CSP silently blocks inline JS | Low | Document prominently |

### Theme: SPA-Only Design

Gaps 6, 8, 9, and 10 share a root cause: **gatekeeper is designed exclusively for SPA
consumers**. Every auth flow (login, logout, error handling) returns API-style responses
(204, JSON) instead of browser redirects. This means:

- Any server-rendered app (Thymeleaf, JSP, server components) requires JS workarounds
- Any app without a JavaScript frontend layer cannot use gatekeeper's auth flows
- This is appropriate for Camunda's own webapps (which are React SPAs) but limits gatekeeper's
  value as a standalone library

**Recommended fix**: Content-negotiate based on `Accept` header. If the request accepts
`text/html`, use standard 302 redirects. If it accepts `application/json`, use the current
204/JSON pattern. This is a single change in `WebappRedirectStrategy` and the form login
handlers.

## Undocumented "Magic Paths"

An implementer must include these paths in `SecurityPathProvider.webappPaths()` but none are
documented:

| Path | Purpose | When Needed |
|------|---------|-------------|
| `/login/**` | Form login page + processing | Basic auth |
| `/sso-callback` | OIDC redirect callback | OIDC |
| `/oauth2/**` | Spring Security OIDC authorization | OIDC |
| `/post-logout` | Post-IdP-logout redirect | OIDC with IdP logout |

**Recommended fix**: Either document these prominently in the integration guide, or have
gatekeeper automatically include them in the filter chain without requiring the app to
declare them.

## Lessons Learned

1. **Use a SPA for gatekeeper test apps** — gatekeeper's responses (204, JSON redirects) are
   designed for JavaScript frontends. A React app would have worked without workarounds.

2. **Test the library before declaring it ready** — the initial app failed to start due to
   multiple issues (ObjectMapper ordering, Flyway compatibility, CGLIB proxy issues). Running
   the app end-to-end before handing it to the user would have caught these.

3. **Integration guides must cover the unhappy path** — the guide covers SPI contracts but not
   what happens when things go wrong (null auth, missing paths, CSP blocking JS).

4. **Agents need verification checkpoints** — the Backend Engineer produced compilable code but
   the app didn't actually work. A "does it start and can you log in?" checkpoint would have
   caught issues earlier.

## Files Produced

```
gatekeeper/examples/joke-generator-3000/
├── ARCHITECTURE.md              (design document)
├── TEST-PLAN.md                 (41 test cases)
├── pom.xml                      (standalone, Spring Boot 4.0.3)
├── docker-compose.yml           (Postgres + Keycloak)
├── init-databases.sql
├── keycloak/joke-generator-realm.json
├── src/main/java/io/camunda/jokegen/
│   ├── JokeGeneratorApplication.java
│   ├── auth/ (3 files)          — SPI implementations
│   ├── config/ (1 file)         — SecurityPathProvider
│   ├── controller/ (4 files)    — page + API controllers
│   ├── model/ (3 files)         — JPA entities
│   ├── repository/ (3 files)    — Spring Data JPA
│   └── service/ (2 files)       — business logic
├── src/main/resources/
│   ├── application.yml, application-basic.yml, application-oidc.yml
│   ├── data.sql                 — seed data
│   ├── templates/ (6 files)     — Thymeleaf
│   └── static/
│       ├── css/style.css
│       └── js/ (3 files)        — login, logout, jokes
└── gatekeeper/docs/validation-test/
    ├── reflection-notes.md      (full prompt history + team log)
    └── SUMMARY.md               (this file)
```
