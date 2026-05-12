# Physical-tenant OIDC and session isolation

**Status:** design — not yet planned for implementation
**Date:** 2026-05-12
**Branch:** `evaluate-physical-tenant-identity`
**Scope:** authentication and session isolation only. Out of scope: data-layer
PT routing (already in place), token-claims and membership PT-scoping (sibling
design), per-PT webapp resource delivery (infra concern).

## Motivation

A physical tenant (PT) is a unit of data isolation that lets a single Camunda
orchestration cluster serve multiple customers. PT-prefixed URLs
(`/physical-tenant/{ptId}/...`) carry the PT context end-to-end. The data
layer already resolves a per-PT `Camunda` config via
`PhysicalTenantResolver`. This spec extends the same PT-awareness into the
authentication layer so that:

- Each PT configures its own one or more OIDC IdPs (Keycloak, Entra,
  PingFederate, Okta, etc.).
- Login and logout flows redirect within the PT — a user logging into PT
  `foo` returns to `/physical-tenant/foo/...`, never to another PT.
- Browser sessions are PT-isolated: a session for PT `foo` grants no access
  to PT `bar`, and a single human can be authenticated to multiple PTs in
  separate browser tabs simultaneously.
- Bearer tokens issued by PT `foo`'s IdP are valid only at
  `/physical-tenant/foo/...`. Cross-PT token replay is structurally
  prevented at the JWT-decoder layer.
- Existing single-tenant deployments behave identically to today. No URL
  change, no IdP-side redirect-URI re-registration, no YAML migration
  required.

Basic Authentication is out of scope. Only OIDC is supported for PT-aware
deployments. The default PT continues to support Basic Auth exactly as today
through unchanged code paths.

## Constraints

- **No global runtime persistence.** Every stateful read/write must hit a
  PT-scoped bean. In-memory PT-scoped maps are acceptable; a single shared
  in-memory store across PTs is not.
- **Static per-PT configuration is global.** The PT list, each PT's IdP set,
  registrationIds, issuer URIs, and client credentials are read from YAML at
  startup and held in memory.
- **Static PT lifecycle.** PTs are defined in YAML and frozen at JVM
  startup. Adding or removing a PT requires a redeploy.
- **IdP portability.** The design works on Keycloak, Entra, PingFederate,
  PingOne, Okta, Auth0, and Google without IdP-side wildcards. Per-client
  exact-match redirect URIs only.
- **Backward compatibility.** A deployment with no
  `camunda.physical-tenants.*` configuration behaves byte-for-byte as today.

## Decisions

| # | Decision |
|---|---|
| 1 | PT path segment appears at the **front** of every URL: `/physical-tenant/{ptId}/v2/...`, `/physical-tenant/{ptId}/operate/...`, `/physical-tenant/{ptId}/sso-callback/...`. The default PT keeps bare URLs unchanged. |
| 2 | Sessions are **hard-isolated per PT**. A user authenticated in PT `foo` has no session in PT `bar`. Simultaneous logins in separate tabs are supported. |
| 3 | OIDC callback URL encodes `registrationId` in the path: `/physical-tenant/{ptId}/sso-callback/{registrationId}`. Per-client redirect URIs are registered exact-match at each IdP. (Default PT keeps its current `/sso-callback` shape with `state`-based client identification.) |
| 4 | The entire OIDC surface (login start, authorize, callback, logout, post-logout) lives **under the PT prefix** so the PT-scoped session cookie travels on every step of the flow. |
| 5 | PT lifecycle is **static**. PTs are read from YAML at startup and frozen. |
| 6 | Filter-chain architecture is **N chains per PT** (Path 1). Each PT contributes one API chain and one webapp chain. The default PT's chains are a degenerate case with bare-path matchers. |
| 7 | Cross-cutting security configuration (HTTP headers, CSRF policy, observation settings, SaaS flag) is **global**. Only the OIDC/IdP subtree is PT-scoped. Per-PT cross-cutting keys are **rejected at startup** with a clear error. |
| 8 | Login UI uses Spring's **default login page generator** per chain. The page lists only the current PT's IdPs because the chain's `ClientRegistrationRepository` contains only those. |
| 9 | Session isolation uses **Spring Session** with a private `MapSessionRepository` per PT chain and a per-PT `CookieSerializer` (cookie name `camunda-session-{ptId}`, `Path=/physical-tenant/{ptId}`). |
| 10 | System endpoints (`/actuator/**`, `/health`, `/ready`, `/startup`, `/error`, `/swagger/**`, `/v3/api-docs/**`) stay **global** and unprefixed. They are platform concerns, not tenant concerns. |
| 11 | Static webapp resources are served per PT (`/physical-tenant/{ptId}/operate/assets/**`, etc.). The security layer just enumerates the corresponding `permitAll` paths in each per-PT chain. The delivery mechanism for the bundles is an infra-layer concern, out of scope. |

## Architecture

### One consolidated `WebSecurityConfig` that iterates PTs

The existing `WebSecurityConfig` is refactored to iterate
`PhysicalTenantResolver.getAll()` (a `Map<String, Camunda>` that always
contains at least the `default` entry). For each entry it emits two
`SecurityFilterChain` beans (API and webapp).

The differences between the default PT and explicit PTs reduce to values of
a `PtUrlTemplate` (paths, callback shape, cookie name and path):

| Path                          | Default PT                            | Explicit PT `foo`                                       |
|-------------------------------|---------------------------------------|---------------------------------------------------------|
| API security matcher          | `/api/**`, `/v1/**`, `/v2/**`, `/mcp/**`, `/.well-known/oauth-protected-resource/**` | `/physical-tenant/foo/api/**`, …, `/physical-tenant/foo/.well-known/oauth-protected-resource/**` |
| Webapp security matcher       | today's `WEBAPP_PATHS`                | every entry in `WEBAPP_PATHS` prefixed with `/physical-tenant/foo` |
| Login URL                     | `/login`                              | `/physical-tenant/foo/login`                            |
| Logout URL                    | `/logout`                             | `/physical-tenant/foo/logout`                           |
| OAuth2 authorize base URI     | `/oauth2/authorization`               | `/physical-tenant/foo/oauth2/authorization`             |
| OAuth2 callback base URI      | `/sso-callback` (no registrationId)   | `/physical-tenant/foo/sso-callback` (registrationId in path) |
| OIDC client redirect URI      | `{baseUrl}/sso-callback`              | `{baseUrl}/physical-tenant/foo/sso-callback/{registrationId}` |
| Post-logout redirect          | `{baseUrl}/post-logout`               | `{baseUrl}/physical-tenant/foo/post-logout`             |
| Session cookie name           | `camunda-session`                     | `camunda-session-foo`                                   |
| Session cookie path           | `/`                                   | `/physical-tenant/foo`                                  |
| CSRF cookie name              | `X-CSRF-TOKEN`                        | `X-CSRF-TOKEN-foo`                                      |
| CSRF cookie path              | `/`                                   | `/physical-tenant/foo`                                  |

The two `SecurityFilterChain` beans per PT are registered with
`@Order(ORDER_WEBAPP_API)` (1). Dispatch is unambiguous because the
`securityMatcher` patterns are path-based and non-overlapping across PTs and
the default PT.

`ORDER_UNPROTECTED` (0) and `ORDER_UNHANDLED` (2) chains continue to exist
unchanged. The unhandled-paths chain catches malformed PT prefixes
(`/physical-tenant/nonexistent/...`) and returns 404 exactly as today.

### Beans built inline, not as application-context singletons

For each PT chain, the OIDC-related beans
(`ClientRegistrationRepository`, `JwtDecoder`,
`OAuth2AuthorizedClientRepository`, `OAuth2AuthorizedClientManager`,
`OidcUserService`, `OidcTokenEndpointCustomizer`, `LogoutSuccessHandler`,
`OidcAuthenticationConfigurationRepository`, `CookieCsrfTokenRepository`,
`SessionRepository`, `HttpSessionIdResolver`, `CookieSerializer`,
`SessionRepositoryFilter`) are constructed inline inside the `@Bean` method
that returns the chain. They are **not** exposed to the Spring application
context as named beans. This avoids per-PT qualifier explosion (`N` × ~15
beans) and keeps the per-PT wiring local to the chain.

Beans that are legitimately stateless / threadsafe singletons remain
application-context beans wired into every chain by ordinary autowiring:
`HeaderConfiguration`, observation settings, `AuthFailureHandler`,
`MembershipService`, `TokenClaimsConverter`, `WebappRedirectStrategy`,
`oidcUserInfoHttpClient`, `JWSKeySelectorFactory`,
`OidcAccessTokenDecoderFactory`, `AssertionJwkProvider`,
`TokenValidatorFactory`.

### Why per-client redirect URIs

A registered redirect URI of the form
`{baseUrl}/physical-tenant/foo/sso-callback/foo-keycloak` is exact-match. No
wildcards. Every major IdP supports per-client exact-match redirect URIs
(Keycloak, Entra, PingFederate, PingOne, Okta, Auth0, Google). Putting
`registrationId` in the callback URL path lets the app derive the PT from
the request path alone — no `state` parsing, no signed tokens, no global
state→PT lookup table. The PT-scoped session cookie travels with the
callback request because the path matches `Path=/physical-tenant/foo`, so
the saved `OAuth2AuthorizationRequest` is read from the PT-scoped
`SessionRepository` and the auth code exchange completes inside the correct
PT.

The default PT keeps its current callback shape (`/sso-callback` without
`registrationId`, client identified by `state`) to preserve backward
compatibility with existing IdP-side redirect URI registrations.

### Session isolation via Spring Session

A standard servlet context has one `SessionCookieConfig`, so the container
can write only one session cookie name and one cookie path globally. That
makes per-PT path-scoped session cookies impossible to deliver via the
container's native session machinery.

Spring Session replaces the container's session management. Each per-PT
chain owns:

- A private in-memory `MapSessionRepository`.
- A `CookieHttpSessionIdResolver` configured with a `DefaultCookieSerializer`
  that writes `camunda-session-{ptId}` at `Path=/physical-tenant/{ptId}`.
- A `SessionRepositoryFilter` wired into the chain ahead of
  `SecurityContextHolderFilter`.

Each PT's `SessionRepository` is fully private. There is no cross-PT
shared map of sessions, satisfying the "no global persistence" constraint.

Browsers track cookies by `(Name, Domain, Path)`. With distinct cookie names
per PT, there is no collision between PT cookies regardless of path, and
the same browser can hold simultaneous independent sessions for any number
of PTs.

The default PT either keeps the container's native `HttpSession`
(zero-risk, no migration) or moves to a Spring Session
`MapSessionRepository` at `Path=/` with cookie name `camunda-session`
(uniform code path). The implementation can decide; behavior is identical
either way.

### CSRF cookies

`CookieCsrfTokenRepository` is constructed inline per chain with cookie
name `X-CSRF-TOKEN-{ptId}` and `cookiePath` set to
`/physical-tenant/{ptId}`. Default PT keeps `X-CSRF-TOKEN` at `Path=/`.

### JWT decoders enforce PT isolation

Each per-PT chain's `JwtDecoder` is an issuer-aware decoder constructed
from only that PT's IdPs. A bearer token issued by `foo-keycloak` validates
at `/physical-tenant/foo/v2/...` because `foo-keycloak`'s issuer is in
foo's decoder's trust set. The same token presented at
`/physical-tenant/bar/v2/...` is rejected because `bar`'s decoder does not
trust `foo-keycloak`'s issuer. Cross-PT token replay is structurally
prevented at the decoder layer with no extra application-level check.

### Per-PT `.well-known/oauth-protected-resource`

Each per-PT API chain advertises only its own PT's authorization servers
under `/physical-tenant/{ptId}/.well-known/oauth-protected-resource`. Each
PT's clients discover only their own IdPs.

### Per-chain `permitAll` for static webapp assets

Inside each per-PT webapp chain, the `authorizeHttpRequests` block
`permitAll`s the PT-prefixed equivalent of today's static-asset list in
`oidcWebappSecurity`:

- `/physical-tenant/{ptId}/operate/assets/**`,
  `/physical-tenant/{ptId}/operate/client-config.js`,
  `/physical-tenant/{ptId}/operate/custom.css`,
  `/physical-tenant/{ptId}/operate/favicon.ico`
- `/physical-tenant/{ptId}/tasklist/assets/**`,
  `/physical-tenant/{ptId}/tasklist/client-config.js`,
  `/physical-tenant/{ptId}/tasklist/custom.css`,
  `/physical-tenant/{ptId}/tasklist/favicon.ico`
- `/physical-tenant/{ptId}/webapp/assets/**`,
  `/physical-tenant/{ptId}/webapp/custom.css`,
  `/physical-tenant/{ptId}/webapp/favicon.ico`

The list is templated programmatically from the existing list — not
enumerated in YAML.

Spring's `/default-ui.css` is **promoted to `UNPROTECTED_PATHS`** (served
by the global `ORDER_UNPROTECTED` chain). It is static, PT-irrelevant, and
shared by every PT's auto-generated login page. The default PT's webapp
chain no longer needs to `permitAll` it; per-PT chains never did.

The default PT's webapp chain continues to `permitAll` today's
unprefixed asset paths unchanged.

## Configuration shape

Per-PT IdPs nest under the existing `camunda.physical-tenants.<ptId>.*`
namespace that `PhysicalTenantResolver` already binds.

```yaml
camunda:
  # ── default PT (root scope) — existing single-tenant deployments
  # configure here, unchanged ──
  security:
    authentication:
      method: oidc
      oidc:
        # legacy single-IdP form, still supported
        issuerUri: https://corp.example.com/idp
        clientId: camunda-default
        clientSecret: ...

  physical-tenants:
    foo:
      security:
        authentication:
          method: oidc
          providers:
            oidc:
              foo-keycloak:
                issuerUri: https://keycloak.foo.example.com/realms/foo
                clientId: camunda-foo-keycloak
                clientSecret: ...
              foo-entra:
                issuerUri: https://login.microsoftonline.com/<foo-tenant>/v2.0
                clientId: camunda-foo-entra
                clientSecret: ...
    bar:
      security:
        authentication:
          method: oidc
          providers:
            oidc:
              bar-keycloak:
                issuerUri: https://keycloak.bar.example.com/realms/bar
                clientId: camunda-bar-keycloak
                clientSecret: ...
```

**Rules:**

- `registrationId` must be **unique within a PT**. Across PTs they may
  collide (`foo` and `bar` may each have an IdP named `keycloak`) because
  each PT has its own `ClientRegistrationRepository`.
- The `redirectUri` field on a PT's OIDC config is inferred from the PT
  prefix and the `registrationId`. Operators do not need to set it
  explicitly; if they do, the explicit value is honored.
- **Per-PT cross-cutting keys are rejected at startup** with a clear error:
  `camunda.physical-tenants.<pt>.security.httpHeaders.*`,
  `camunda.physical-tenants.<pt>.security.csrf.*`,
  `camunda.physical-tenants.<pt>.security.observation.*`,
  `camunda.physical-tenants.<pt>.security.saas.*`. These are global and
  may only be set at the root `camunda.security.*` scope.
- The default PT continues to support both the legacy single-IdP form
  (`camunda.security.authentication.oidc.*`) and the multi-provider map
  (`camunda.security.authentication.providers.oidc.*`).

## Auth flow walkthrough

### Browser login (PT `foo`)

1. `GET /physical-tenant/foo/operate/processes` — no session cookie.
2. The `fooWebappSecurity` chain matches; `ExceptionTranslationFilter`
   sees no authentication; the request is saved in the PT-scoped session;
   `302 → /physical-tenant/foo/login`.
3. `GET /physical-tenant/foo/login` — the chain's
   `DefaultLoginPageGeneratingFilter` renders a page listing only foo's
   IdPs. Each button posts to
   `/physical-tenant/foo/oauth2/authorization/{registrationId}`.
4. `GET /physical-tenant/foo/oauth2/authorization/foo-keycloak` — the
   chain's `OAuth2AuthorizationRequestRedirectFilter` (using
   `ClientAwareOAuth2AuthorizationRequestResolver` parameterized with
   foo's repo) builds the request, stashes the saved
   `OAuth2AuthorizationRequest` in the PT session, and 302s to Keycloak
   with `redirect_uri={baseUrl}/physical-tenant/foo/sso-callback/foo-keycloak`.
5. User authenticates at Keycloak. Keycloak 302s to
   `{baseUrl}/physical-tenant/foo/sso-callback/foo-keycloak?code=...&state=...`.
   The browser sends `camunda-session-foo` because the path matches
   `Path=/physical-tenant/foo`.
6. The chain's `OAuth2LoginAuthenticationFilter`
   (`redirectionEndpoint().baseUri("/physical-tenant/foo/sso-callback")`)
   matches, extracts `foo-keycloak` from the path, retrieves the saved
   request from the PT session, exchanges the code at Keycloak's token
   endpoint, validates the id_token with foo's `JwtDecoder`, persists
   tokens in foo's `OAuth2AuthorizedClientRepository`, establishes the
   `SecurityContext` in the PT session, 302 → originally requested
   `/physical-tenant/foo/operate/processes`.
7. `GET /physical-tenant/foo/operate/processes` (with `camunda-session-foo`)
   — chain validates session, webapp resources served.
8. SPA makes `GET /physical-tenant/foo/v2/process-definitions/search`.
   Same cookie travels (path matches), `fooApiSecurity` chain authenticates
   from session, serves data.

### Browser logout (PT `foo`)

1. `POST /physical-tenant/foo/logout` — chain's `LogoutFilter` invalidates
   the PT session and deletes the path-scoped `camunda-session-foo` and
   `X-CSRF-TOKEN-foo` cookies.
2. If `idpLogoutEnabled` is true on the IdP, foo's `LogoutSuccessHandler`
   (`CamundaOidcLogoutSuccessHandler` parameterized with foo's
   `ClientRegistrationRepository`) builds the IdP end-session URL with
   `post_logout_redirect_uri={baseUrl}/physical-tenant/foo/post-logout`
   and 302s.
3. IdP redirects to `/physical-tenant/foo/post-logout` — that path is
   `permitAll`'d on the chain and returns a 204 or renders a "you are
   logged out" page.

### Bearer-token API call (PT `foo`)

1. `GET /physical-tenant/foo/v2/process-definitions/search` with
   `Authorization: Bearer <jwt>`.
2. `fooApiSecurity` chain runs. The `BearerTokenAuthenticationFilter`
   invokes foo's issuer-aware `JwtDecoder`.
3. If the token's `iss` claim matches one of foo's configured issuers,
   validation succeeds. Otherwise the token is rejected — including any
   token issued by another PT's IdP.

### Default PT flow

Identical to today, URL by URL: login at `/login`, authorize at
`/oauth2/authorization/{registrationId}`, callback at `/sso-callback`
(client identified by `state`), logout at `/logout`. Existing IdP redirect
URI registrations stay valid. No customer migration required.

## Backward compatibility

- A deployment with no `camunda.physical-tenants.*` configuration produces
  exactly the same set of `SecurityFilterChain` beans as today, with
  exactly the same URLs, cookies, and IdP redirect URIs.
- Adding a single explicit PT (`camunda.physical-tenants.foo.*`) does not
  change the default PT's behavior. The default PT's IdP registrations,
  redirect URIs, login URL, and session cookies are untouched. The new PT
  adds its own chains under `/physical-tenant/foo/**`.
- The "PT prefix everything, including default" option (currently
  undecided by the team) is a strictly additive change layered on later:
  introduce a redirect / rewrite layer that maps bare paths to
  `/physical-tenant/default/...`. No per-PT auth code changes required.

## Open questions and follow-ups

- **Token claims and membership PT-scoping** (sibling design).
  `TokenClaimsConverter` and `MembershipService` today resolve roles,
  groups, and tenants in a single global namespace. Once tokens are
  PT-scoped, the converter and membership lookup must also resolve within
  the PT's namespace; otherwise a user authenticated against foo's IdP
  could inherit memberships from bar's identity store. Necessary for
  end-to-end PT isolation but out of scope here.
- **Per-PT webapp resource delivery mechanism** (infra concern). The
  security layer enumerates `permitAll` paths under each PT prefix; how the
  bytes are actually served (per-PT filesystem mount, per-PT classpath JAR,
  edge reverse proxy) is decided separately.
- **HA / multi-replica session replication.** Today's container HttpSession
  is process-local; Spring Session `MapSessionRepository` is also
  process-local. Any future need for cross-replica session replication
  applies equally to today's design and to this one; if and when it lands,
  it lands as a PT-scoped `RedisSessionRepository` (or similar) per chain.
- **"PT prefix everything, including default" routing.** Whether the
  default PT eventually also serves under `/physical-tenant/default/...`
  is a team decision; the answer is additive routing, not a redesign.
- **`camunda.physical-tenants.default.*` overrides.** Whether and how
  operators may override default-PT settings via the explicit `default`
  key needs to align with `PhysicalTenantResolver`'s behavior (the resolver
  honors an explicit `default` declaration). Behavior under the security
  layer should be consistent with the data layer.
