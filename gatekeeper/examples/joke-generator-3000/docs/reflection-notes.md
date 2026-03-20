# Joke Generator 3000 — Validation Test Reflection Notes

## Prompt History

### Prompt 1 (User)

> I would like for you now Claude to perform a test, in this test we will be creating a custom
> application that uses the gatekeeper module for the authentication layer in a new application
> that has a UI and an unprotected page and protected page (accessed via login). You should create
> a team for this task, I would suggest a FE dev, BE dev, UI designer, Architect, and a QA. In
> your application you should use the Gatekeeper Integration Guide you created in the gatekeeper
> directory because our aim here is to act like we are an implementing team, if the docs are
> missing something we need to capture that and update them in the end on reflection.
>
> The app will be on the subject of a joke generator and it will be called "Joke Generator 3000",
> it will have a landing page (accessible to all) and then a joke generation area that will be
> protected.
>
> We should offer two modes for our test app, basic auth (backed by H2 in memory DB) and OIDC
> (backed by a postgres container that we can start). You do not need to make the app or API
> complex, we are after a good test validation of our module and its integration. You should also
> keep notes on the steps your team is taking so I can review them with my own manager and share
> this attempt with the wider Engineering audience.
>
> You do not need to worry too much about unit/integration testing here as the test is something
> you will cover with test cases and the QA engineer.
>
> I would suggest that in your agent team you have a: Frontend engineer, Backend engineer,
> Software architect, UI designer, QA engineer.
>
> Think about this and tell me your plan and any areas you need more input on, and yes, you should
> store this prompt in the reflection notes along with every prompt I give you.

### Prompt 2 (User — answers to questions + refinements)

> I would like us to use a docker container for Keycloak, so not testcontainers, remember, we're
> mimicking a real app here. Your location for the app is acceptable to me, but remember, this app
> should be considered entirely unrelated, so no parent configuration to gatekeeper or camunda. I
> would actually like us to implement both, we should have a page that is login to view, but then
> another functionality that is role based.
>
> One thing that I forgot to mention earlier on, the app should just use a Postgres database for
> ease, this way the app can use this for storage of role mappings to users, and Keycloak can use
> this same postgres database for its needs.

Key decisions from this prompt:
- Docker Compose for Keycloak (not testcontainers) — mimics production
- App is entirely independent — no parent POM inheritance from gatekeeper or camunda
- Postgres shared between app and Keycloak (separate schemas/databases within same instance)
- Two access levels: login-to-view (joke browsing) AND role-based (e.g., joke admin/generation)
- H2 is dropped in favour of Postgres for both modes

### Prompt 3 (User — login page and Spring fallback clarification)

> We should offer a login page that is styled to our app because this is how our known real world
> usage (i.e. Camunda) operates, in this instance falling back to Springs login page would be
> considered a negative, to clarify, falling back to Spring autoconfiguration/properties for the
> OIDC is considered positive.

Key decisions from this prompt:
- Custom-styled login page for basic auth (not Spring's default white-label login)
- Falling back to Spring's default login page = negative outcome (library gap if gatekeeper doesn't support custom login pages cleanly)
- Falling back to Spring's standard OIDC properties = positive outcome (validates the planned Spring-native config support)
- This distinction is important: for OIDC, Spring conventions are good. For login UI, the app must own the experience.

### Prompt 5 (User — retrospective on UI approach)

> Something to maybe think about next time then Claude, we should have considered a SPA

Lesson learned: gatekeeper is fundamentally SPA-oriented (204 responses, JSON redirects, CSRF
token headers). A React/vanilla JS SPA would have worked naturally. Choosing Thymeleaf
(server-side rendering) meant every auth flow required JS workarounds. The friction exposed
real library gaps for non-SPA consumers, which is valuable feedback, but a SPA would have been
the right choice for validating the happy path first, then testing SSR separately.

### Prompt 4 (User — start execution, track progress)

> You should, remember to keep track of progress so I can share this with my own manager/team.
> What you produce here will allow me to share experiences and knowledge of working with AI whilst
> evaluating your value to my workflows, no pressure!

## Team Execution Log

### Phase 1: Architecture (Software Architect)
**Status**: COMPLETE
**Duration**: ~3 minutes
**Output**: `ARCHITECTURE.md` — 13-section design document

Key findings by the architect:
- Gatekeeper hardcodes `LOGIN_URL = "/login"` — not configurable, so the app must serve its login page at that exact path
- Basic auth webapp chain uses `authorizeHttpRequests(auth -> auth.anyRequest().permitAll())` — role checks must be in controllers, not filter chains
- `CamundaUserInfo` has 10 constructor parameters including unused fields (`salesPlanType`, `c8Links`) — awkward for implementers
- OIDC redirect URI is `/sso-callback` (gatekeeper constant) — must be in `webappPaths()`
- Spring Boot version must match gatekeeper (4.0.3) — binary compatibility requirement

### Phase 2: UI Design (UI Designer)
**Status**: COMPLETE
**Duration**: ~2 minutes
**Output**: `src/main/resources/static/css/style.css` (15KB)

Design choices:
- Warm orange primary (`#e06d10`), slate nav bar (`#3a4a5c`), neutral backgrounds
- CSS custom properties for easy theming
- Responsive breakpoints at 768px and 480px
- 13 component groups: nav, hero, login, joke cards, admin, buttons, alerts, forbidden, footer
- No external dependencies — pure CSS with system font stack

### Phase 3: Backend Implementation (Backend Engineer)
**Status**: COMPLETE
**Duration**: ~4 minutes
**Output**: 17 Java files, 3 SQL migrations, 3 YAML configs, Docker Compose, Keycloak realm JSON

Files created:
- 3 SPI implementations (`JokeGenSecurityPathProvider`, `BasicMembershipResolver`, `OidcMembershipResolver`, `JokeGenUserProvider`)
- 4 controllers (`HomeController`, `JokeBrowserController`, `JokeAdminController`, `JokeApiController`)
- 3 JPA entities, 3 repositories, 2 services
- Docker Compose (Postgres + Keycloak)
- Keycloak realm with 2 users, 1 role, 1 client

### Phase 4: Frontend Implementation (Frontend Engineer)
**Status**: COMPLETE
**Duration**: ~1.5 minutes
**Output**: 6 Thymeleaf templates

Templates:
- `layout.html` — shared fragments (nav, footer)
- `home.html` — public landing page with hero section
- `login.html` — custom styled login form (basic auth)
- `jokes.html` — joke browser with random joke fetch
- `admin.html` — joke creation form (role-gated)
- `forbidden.html` — 403 access denied page

### Phase 5: Compilation Verification
**Status**: COMPLETE
**Result**: App compiles cleanly against gatekeeper 0.1.0-SNAPSHOT

### Phase 6: QA Test Plan (QA Engineer)
**Status**: COMPLETE
**Duration**: ~2.5 minutes
**Output**: `TEST-PLAN.md` — 41 test cases across 4 suites

### Phase 7: Automated Test Execution (QA Engineers)
**Status**: COMPLETE
**Output**: Two shell scripts in `tests/`

Results:
- `test-basic-auth.sh` — **18/18 passed** (public access, login/logout, role enforcement, API auth, security boundaries, headers)
- `test-oidc.sh` — **15/15 passed** (public access, Keycloak token grant, Bearer token API, role enforcement, invalid token rejection, headers)
- **Total: 33/33 passed**

Key fix required during OIDC testing: Keycloak realm JSON needed a `protocolMapper` (`oidc-usermodel-realm-role-mapper`) to include `realm_access.roles` in the access token. Keycloak 26.x doesn't include realm roles in tokens by default — another undocumented integration step.

### Phase 8: Smoke Test (Manual)
**Status**: COMPLETE
**Issues found during first run**:
1. Flyway/Hibernate ordering — `ddl-auto: validate` caused Hibernate to validate before Flyway ran. Fixed by setting `ddl-auto: none`.
2. Missing `ObjectMapper` bean — gatekeeper's `AuthFailureHandler` requires one but Spring's Jackson auto-config hasn't run yet. Workaround: explicit `ObjectMapper` bean in app. **Library gap**: `GatekeeperAuthAutoConfiguration` should declare `@AutoConfiguration(after = JacksonAutoConfiguration.class)`.
3. `final` service classes — `JokeService` and `AppUserDetailsService` were `final` but Spring needs CGLIB proxies for `@Transactional`/`@Service`. Removed `final`.
4. **Critical: `CamundaAuthenticationDelegatingConverter` throws on null auth** — Accessing `/jokes` without auth causes `GatekeeperAuthenticationException` because the webapp chain permits all requests (`anyRequest().permitAll()`) but the converter can't handle null Spring Authentication. Controllers must check `SecurityContextHolder` before calling `getCamundaAuthentication()`. **This is a significant library gap — `getCamundaAuthentication()` should return null for unauthenticated requests, not throw.**

## Documentation Gaps Found

1. **Login page path not documented** — The integration guide doesn't mention that gatekeeper
   hardcodes `LOGIN_URL = "/login"`. An implementer using basic auth wouldn't know they must
   serve their custom login page at exactly `/login` until they debug why Spring's default
   page appears or why their custom page at a different path is ignored.

2. **OIDC callback path not documented** — The guide doesn't mention that `/sso-callback` must
   be included in `webappPaths()`. An implementer would discover this only after OIDC redirect
   fails.

3. **`CamundaUserInfo` constructor not documented** — The 10-parameter constructor with several
   unused fields (`salesPlanType`, `c8Links`) isn't explained. An implementer has to read the
   source to figure out which parameters are required vs optional.

4. **Role-based access pattern not documented** — The guide shows how to get
   `CamundaAuthentication` but doesn't explain that role-based authorization is the app's
   responsibility (checked in controllers), not gatekeeper's. A new implementer might expect
   `@PreAuthorize`-style annotations or path-based role restrictions.

5. **Spring Boot version compatibility not documented** — The guide doesn't mention that the
   consuming app must use the same Spring Boot major version as gatekeeper was compiled against.

6. **OIDC authorization path not documented** — The guide doesn't mention that `/oauth2/**`
   must be in `webappPaths()` for OIDC login redirects to work. Without it, the
   `/oauth2/authorization/oidc` endpoint returns 404. Combined with the undocumented
   `/sso-callback` and `/login` paths, an implementer must discover three magic paths by
   debugging.

7. **OIDC logout not documented** — The integration guide doesn't cover how to configure
   IdP-initiated logout (back-channel or front-channel). Gatekeeper's OIDC chain sets up
   `.oidcLogout()` but the app needs to know how to configure the end-session endpoint
   and what paths/properties are involved.

8. **Login page controller requirement not documented** — The guide doesn't mention that when
   using basic auth, the app must serve a page at `GET /login` via its own controller.
   Gatekeeper configures `formLogin.loginPage("/login")` but only handles the POST (credential
   validation). The GET (rendering the login form) is the app's responsibility. Without this,
   the user gets a 404 when redirected to login.

## Library Gaps Found

1. **Hardcoded login page URL** — `LOGIN_URL = "/login"` is a public static final constant, not
   configurable via properties. An app that wants its login page at `/auth/login` or
   `/signin` cannot achieve this without overriding the entire filter chain.
   **Recommendation**: Make this configurable via `camunda.security.authentication.login-url`.

2. **`CamundaUserInfo` is over-coupled to Camunda SaaS** — Fields like `salesPlanType`,
   `c8Links`, and `authorizedComponents` are Camunda-specific and meaningless for external
   consumers. A standalone library should have a cleaner user info model.
   **Recommendation**: Use a builder pattern or reduce to universal fields, with extension
   points for product-specific data.

3. **No built-in role-based path protection** — Gatekeeper provides authentication (who you
   are) but no declarative authorization (what you can access). Every role check must be
   manual in controllers. For a library aiming to simplify security, this is a gap.
   **Recommendation**: Consider supporting `camunda.security.authorization.paths` config
   or a `@RequiresRole` annotation, even if optional.

4. **No standard Spring OIDC property support** — Users familiar with
   `spring.security.oauth2.client.registration.*` cannot use those properties. They must learn
   the Camunda-specific property namespace.
   **Recommendation**: Implement the planned Spring-native configuration support
   (see `docs/plan/spring-native-configuration-support.md`).

5. **`SecurityPathProvider` requires too many methods** — An app with a simple setup (one
   public path, everything else authenticated) still must implement 5 methods, several
   returning empty sets. A sensible-defaults base class or builder would reduce boilerplate.
   **Recommendation**: Provide a `SimpleSecurityPathProvider` base class or builder.

6. **`getCamundaAuthentication()` throws instead of returning null** — When the webapp chain
   permits unauthenticated access (`anyRequest().permitAll()`), calling
   `authProvider.getCamundaAuthentication()` throws `GatekeeperAuthenticationException`
   instead of returning null. This forces every controller to check
   `SecurityContextHolder.getContext().getAuthentication()` before calling gatekeeper's API,
   which defeats the purpose of having a provider abstraction.
   **Recommendation**: `getCamundaAuthentication()` should return null for unauthenticated
   requests. The converter should handle null Spring Authentication gracefully.

8. **Form login success returns 204 instead of redirect** — Gatekeeper's basic auth webapp
   chain success handler returns `204 No Content` with a CSRF token header. This is designed
   for SPA consumers (Camunda's Operate/Tasklist use JavaScript to handle the 204), but
   server-side rendered apps (Thymeleaf, JSP) expect a redirect to the target page after
   login. The workaround is JavaScript-based form submission that redirects on 204.
   **Recommendation**: Make the success handler configurable, or detect `Accept: text/html`
   and redirect browser requests while returning 204 for API/SPA requests.

9. **Form login failure returns JSON instead of redirect** — Gatekeeper's basic auth webapp
   chain uses `AuthFailureHandler` for `formLogin.failureHandler()`, which returns a JSON
   `401 Unauthorized` response. For browser-based form login, the expected behaviour is to
   redirect back to `/login?error` so the login page can display a user-friendly error
   message. The `AuthFailureHandler` is designed for API error responses, not browser forms.
   **Recommendation**: Use a redirect-based failure handler for form login
   (e.g., `SimpleUrlAuthenticationFailureHandler("/login?error")`) and reserve
   `AuthFailureHandler` for API chains.

10. **OIDC logout returns JSON instead of redirect** — Gatekeeper's `WebappRedirectStrategy`
    returns `200 OK` with `{"url": "...keycloak.../logout?id_token_hint=..."}` instead of a
    302 redirect to the IdP. Like login, this is SPA-oriented — a JavaScript frontend is
    expected to read the JSON and navigate. Server-rendered apps must use JS to handle logout
    too. Combined with the login 204 and failure JSON patterns, gatekeeper is fundamentally
    designed for SPA consumers, making traditional webapp integration awkward.
    **Recommendation**: Detect `Accept: text/html` and issue real 302 redirects for browser
    requests. Or provide a `WebappRedirectStrategy` that does standard redirects as an
    alternative to the JSON-based one.

11. **Default CSP silently blocks inline JavaScript** — Gatekeeper's `DEFAULT_CSP_POLICY`
    includes `script-src 'self' https:` without `'unsafe-inline'`, which silently blocks
    all inline `<script>` blocks and `onclick` handlers. There are no visible errors in the
    page — the JS simply doesn't execute. Developers must use external `.js` files or add
    `'unsafe-inline'` to the CSP. This is correct security practice but the silent failure
    mode is a developer experience trap.
    **Recommendation**: Document this prominently in the integration guide. Consider logging
    a warning when CSP is active and the app serves HTML responses.

7. **`ObjectMapper` bean ordering issue** — `GatekeeperAuthAutoConfiguration` requires
   `ObjectMapper` for `AuthFailureHandler` but doesn't declare ordering after
   `JacksonAutoConfiguration`. Consumers must provide their own `ObjectMapper` bean as a
   workaround.
   **Recommendation**: Add `@AutoConfiguration(after = JacksonAutoConfiguration.class)` to
   `GatekeeperAuthAutoConfiguration`.
