# Adopting the Camunda Security Library in the camunda monorepo

Date: 2026-04-28
Author: Ben Sheppard
Status: Approved (design); plan pending

## Context

The Camunda Security Library (CSL, `camunda-security-library`) ships a unified set of Spring Security filter chains as opt-in `@Configuration` classes that hosts compose via `@Import`. Per [CSL ADR-0006](https://github.com/camunda/camunda-security-library/blob/main/docs/adr/0006-central-security-filter-chains.md), the library is the single home for authentication/authorization filter wiring across all Camunda 8 hosts (Hub, Orchestration Cluster gateways, future hosts).

The camunda monorepo currently owns its own copy of this wiring in `authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java` (~1300 lines, behind `@Profile("consolidated-auth")`), with conditional `@ConditionalOnAuthenticationMethod(BASIC|OIDC)` / `@ConditionalOnProtectedApi` / `@ConditionalOnUnprotectedApi` / `@ConditionalOnSecondaryStorageEnabled|Disabled` branches selecting one of six filter-chain shapes plus Camunda-specific filters and customizers.

The CSL has shipped its first PR (`feat/extract-security-filter-chain`) covering chain extraction. A second PR will add host-driven extensions discovered while adopting it here. The camunda monorepo will adopt CSL `0.1.0-SNAPSHOT` after both library PRs land.

## Goal

End-state migration: replace `WebSecurityConfig`'s six filter-chain `@Bean`s with `@Import`s of the library's `@Configuration` classes, register Camunda-specific extensions through the library's extension hooks, and keep behaviour identical — same response bodies, same cookies, same headers, same 401/403 split.

The migration is intentionally complete in this iteration. Where the library is missing something the camunda host needs, we patch the library first (in CSL PR2) so every host gets the same baseline. The only behaviour change accepted is on `OAuth2RefreshTokenFilter` post-failure cleanup — see section 4.

## Approach

Three PRs in two repositories, in this order:

1. **CSL PR1** (existing, in review on `feat/extract-security-filter-chain`) — extracts the chains. Lands as-is.
2. **CSL PR2** (new branch stacked on PR1) — host-driven extensions discovered while adopting in camunda. See §1.
3. **Camunda PR** (new branch off `main`) — replaces `WebSecurityConfig`'s chain `@Bean`s with library imports plus customizer registrations. See §2–§6.

Six topical sections describe the work; sections 1, 3, 4 are pure refactors (no behaviour change), section 2 is the cutover, sections 5 and 6 cover tests and rollout.

## §1 Library extensions (CSL PR2)

CSL PR1 ships the chains with a simplified `CamundaSecurityProperties` shape (raw `String` enum values, no `cookieHttpOnly`, no CSRF ignored matchers, single CSP default, no PRM hook). The camunda host needs more. PR2 adds:

- **`csrf.cookieHttpOnly: boolean`** (default `false`) on `CsrfConfiguration`. When `false`, the `CookieCsrfTokenRepository` builds its cookie with `httpOnly(false)` so browser-side JS can read the `X-CSRF-TOKEN` cookie. Default matches what every webapp host actually wants today.
- **`csrf.ignoredPathPatterns: Set<String>`** (default empty) on `CsrfConfiguration`. Path patterns CSRF protection ignores in addition to the always-ignored unprotected paths and login/logout endpoints. Camunda uses it for `/actuator/loggers` (currently wired via `EndpointRequest.to(LoggersEndpoint.class)`; the host will pass the resolved path string through the property).
- **`httpHeaders.contentSecurityPolicy.mode: enum {SAAS, SELF_MANAGED, CUSTOM}`** (default `SELF_MANAGED`). Library ships both default policies; selects which when `mode != CUSTOM`. When `mode = CUSTOM`, uses `policyDirectives`. Replaces the camunda host's current `getContentSecurityPolicy(headerConfig, isSaas)` branch.
- **`OidcResourceServerCustomizer` extension hook** (`@FunctionalInterface`) — receives the `OAuth2ResourceServerConfigurer` from inside `oauth2ResourceServer(...)`. The library's `OidcApiSecurityConfig` and `OidcWebappSecurityConfig` apply all `OidcResourceServerCustomizer` beans in `@Order`. Camunda uses it for the RFC 9728 `protectedResourceMetadata(...)` wiring on both OIDC chains.
- **`SecurityPathAdapter.unauthenticatedWebappPaths(): Set<String>`** with a `default` empty implementation (interface keeps backward compatibility). The library's `OidcWebappSecurityConfig` authorizes `requestMatchers(unauthenticatedWebappPaths()).permitAll().anyRequest().authenticated()`. Camunda returns `/default-ui.css`, `/tasklist/assets/**`, `/tasklist/client-config.js`, `/tasklist/custom.css`, `/tasklist/favicon.ico`.
- **Typed config relocation**: replace the library's `CamundaSecurityProperties.CsrfProperties` / `HttpHeadersProperties` (raw `String` values) with the camunda monorepo's typed sub-configs. Move the package `io.camunda.security.configuration.headers.*` (sub-config classes + value enums: `ContentTypeOptionsConfig`, `CacheControlConfig`, `HstsConfig`, `FrameOptionsConfig`, `ContentSecurityPolicyConfig`, `ReferrerPolicyConfig`, `PermissionsPolicyConfig`, `CrossOriginXxxPolicyConfig`, and the value enums `ReferrerPolicy`, `FrameOptionMode`, `CrossOriginXxxPolicy`) into the library at `io.camunda.security.adapters.spring.config.headers.*`. Rename the library's `CsrfProperties` → `CsrfConfiguration` (camunda's existing name), `HttpHeadersProperties` → `HeaderConfiguration` (camunda's existing name). The library's `setupSecureHeaders` consumes the typed enums directly.
- **`AuthFailureHandler` marked `@ConditionalOnMissingBean`** so hosts can register their own implementation. Camunda registers its `CamundaProblemDetail`-emitting variant; library default uses RFC 7807.

PR2 is built and installed locally as `0.1.0-SNAPSHOT` in `~/.m2` for the camunda PR to consume.

## §2 Camunda `WebSecurityConfig` restructure

The file shrinks substantially. All filter-chain `@Bean`s and host-internal helpers (`setupSecureHeaders`, `cookieCsrfTokenRepository`, `applyCsrfConfiguration`, `csrfHeaderFilter`, `NoContentResponseHandler`, `NoContentWithCsrfTokenSuccessHandler`, the path constants) move to the library or to a new `SecurityPathAdapter` bean. Conditional activation, OIDC infrastructure beans, and customizer registrations remain.

### Top-level `WebSecurityConfig`

`@Configuration`, `@EnableWebSecurity`, `@Profile("consolidated-auth")`. Contains:

- `@Import(BaseSecurityConfig.class)` — unprotected + catch-all chains. Unconditional.
- `SecurityObservationSettings defaultSecurityObservations()` bean — kept.
- `SecurityPathAdapter` bean: a new `CamundaSecurityPathAdapter` class (in `io.camunda.authentication.config`) holding the path sets previously inline in `WebSecurityConfig` as constants (`API_PATHS`, `UNPROTECTED_API_PATHS`, `UNPROTECTED_PATHS`, `WEBAPP_PATHS`), plus `unauthenticatedWebappPaths()` returning `/default-ui.css` + `/tasklist/assets/**` + `/tasklist/client-config.js` + `/tasklist/custom.css` + `/tasklist/favicon.ico`, plus `webComponentNames()` (new — bare segment IDs the library's SPI requires; exact list pinned at plan time but covers the components served under `webappPaths()`: `identity`, `operate`, `tasklist`, etc.).
- `WebappFilterChainCustomizer webComponentAuthorizationCheckCustomizer(...)` — applies `WebComponentAuthorizationCheckFilter` after `AuthorizationFilter` on both BASIC and OIDC webapp chains. The library applies all `WebappFilterChainCustomizer` beans in `@Order` to every webapp chain — registering it once at top level covers both auth modes.

### Inner `@Configuration @ConditionalOnUnprotectedApi`

`@Import(UnprotectedApiSecurityConfig.class)`.

### Inner `@Configuration @ConditionalOnAuthenticationMethod(BASIC) @ConditionalOnSecondaryStorageEnabled BasicConfiguration`

- `@Import(BasicAuthWebappSecurityConfig.class)`.
- Nested `@Configuration @ConditionalOnProtectedApi @Import(BasicAuthApiSecurityConfig.class)`.
- `usernamePasswordAuthenticationConverter` bean — kept.
- `@PostConstruct verifyBasicConfiguration` — kept.
- New: `WebappFilterChainCustomizer adminUserCheckCustomizer(...)` — registers `AdminUserCheckFilter` before `AuthorizationFilter`. BASIC-only because the bean is scoped to the BASIC config; the library applies it on the BASIC webapp chain.

### Inner `@Configuration @ConditionalOnAuthenticationMethod(BASIC) @ConditionalOnSecondaryStorageDisabled BasicAuthenticationNoDbConfiguration`

Unchanged. Fail-fast bean throws `BasicAuthenticationNotSupportedException` at construction.

### Inner `@Configuration @ConditionalOnAuthenticationMethod(OIDC) OidcConfiguration`

- Nested `@Configuration @ConditionalOnSecondaryStorageEnabled @Import(OidcWebappSecurityConfig.class)`.
- Nested `@Configuration @ConditionalOnProtectedApi @Import(OidcApiSecurityConfig.class)`.
- All existing OIDC infrastructure beans kept: `tokenClaimsConverter`, `oidcTokenAuthenticationConverter`, `oidcUserAuthenticationConverter`, `oidcFallbackMeterRegistry`, `oidcUserInfoHttpClient`, `oidcClaimsProvider`, `oidcProviderRepository`, `clientRegistrationRepository`, `tokenValidatorFactory`, `idTokenDecoderFactory`, `jwsKeySelectorFactory`, `accessTokenDecoderFactory`, `jwtDecoder`, `oAuth2AuthorizedClientRepository`, `assertionJwkProvider`, `oidcTokenEndpointCustomizer`, `authorizedClientManager`, `oidcUserService`, `webappRedirectStrategy`, `oidcLogoutSuccessHandler`, `verifyOidcConfiguration` `@PostConstruct`.
- New: `WebappFilterChainCustomizer multiIdpAuthorizationRequestResolverCustomizer(...)` — calls `http.oauth2Login(c -> c.authorizationEndpoint(a -> a.authorizationRequestResolver(new ClientAwareOAuth2AuthorizationRequestResolver(...))))`. Spring's `oauth2Login` configurer is additive: a second invocation amends, doesn't replace.
- New: `OidcResourceServerCustomizer protectedResourceMetadataCustomizer(...)` — calls `oauth2.protectedResourceMetadata(prm -> prm.protectedResourceMetadataCustomizer(b -> issuerUris.forEach(b::authorizationServer)))`. Library applies it to both OIDC API and webapp chains.

### Path constants and helpers removed from `WebSecurityConfig`

`SESSION_COOKIE`, `X_CSRF_TOKEN`, `LOGIN_URL`, `LOGOUT_URL`, `REDIRECT_URI` — defined in the library's `CamundaSecurityFilterChainConstants`. Any host references update to import from the library.

`API_PATHS`, `UNPROTECTED_API_PATHS`, `UNPROTECTED_PATHS`, `WEBAPP_PATHS` — move to `CamundaSecurityPathAdapter`.

`ORDER_UNPROTECTED`, `ORDER_WEBAPP_API`, `ORDER_UNHANDLED` — internal to library, deleted from host.

## §3 Single source of truth for security configuration types

The camunda repo's `io.camunda.security.configuration.SecurityConfiguration` holds the host's `@ConfigurationProperties("camunda.security")` data, including `csrf` and `httpHeaders` sub-configs. Today these are camunda-owned types (`CsrfConfiguration`, `HeaderConfiguration` + `headers/*` package). After §1 ships, the same shape lives in the library — owned there as the canonical types.

To avoid a bridge bean and keep `securityConfiguration.getCsrf().isEnabled()` style call sites working unchanged:

- `SecurityConfiguration extends CamundaSecurityProperties`. `csrf` and `httpHeaders` fields live on the parent (library-owned). Camunda-specific fields (`authentication`, `authorizations`, `initialization`, `multiTenancy`, `saas`, `idValidationPattern`) stay on the subclass.
- Camunda's existing `io.camunda.security.configuration.CsrfConfiguration` class is **deleted**.
- Camunda's existing `io.camunda.security.configuration.headers.*` package is **deleted** (all sub-config classes and the `values/*` enums).
- Spring auto-injects `SecurityConfiguration` wherever a library `@Configuration` declares `CamundaSecurityProperties` because of the IS-A relationship. No bridge bean.
- Call sites referencing the moved types update their imports. Identified sites outside `security-core`:
  - `zeebe/qa/integration-tests/src/test/java/io/camunda/zeebe/it/shared/security/headers/SecurityHeadersBasicAuthIT.java`
  - `zeebe/qa/integration-tests/src/test/java/io/camunda/zeebe/it/shared/security/headers/SecurityHeadersOidcIT.java`
  - `qa/acceptance-tests/src/test/java/io/camunda/it/logout/BasicAuthLogoutIT.java`
  - `qa/acceptance-tests/src/test/java/io/camunda/it/csrf/CsrfTokenIT.java`
  - `authentication/src/test/java/io/camunda/authentication/config/AbstractWebSecurityConfigTest.java`
  - `authentication/src/test/java/io/camunda/authentication/config/OidcSaaSWebSecurityConfigTest.java`
  - `authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java` (already being rewritten in §2)

Pure type relocation — no logic moves with it.

## §4 Duplicate utility classes

Five host classes overlap with library equivalents:

| Camunda class | Action | Reason |
|---|---|---|
| `io.camunda.authentication.handler.AuthFailureHandler` | **Keep** | Different problem-detail body schema (`CamundaProblemDetail` from `camunda-gateway-model`). Library marks its own `@ConditionalOnMissingBean` (per §1) so camunda's `@Component`-registered version wins. |
| `io.camunda.authentication.handler.LoggingAuthenticationFailureHandler` | **Delete** | Logically identical to library's. |
| `io.camunda.authentication.handler.OAuth2AuthenticationExceptionHandler` | **Delete** | Logically identical to library's. |
| `io.camunda.authentication.csrf.CsrfProtectionRequestMatcher` | **Delete** | Library version takes `allowedPaths` via constructor (cleaner) and is wired internally by the library's filter chains. |
| `io.camunda.authentication.filters.OAuth2RefreshTokenFilter` | **Delete** | Library version is wired by `OidcWebappSecurityConfig` directly. |

Co-located tests of deleted classes also deleted (their library equivalents have CSL coverage):

- `LoggingAuthenticationFailureHandlerTest`
- `LoggingAuthenticationFailureHandlerWebappTest`
- `OAuth2RefreshTokenFilterTest`
- `CsrfProtectionRequestMatcherTest`

### Accepted behaviour change: post-refresh-failure cleanup

The library wires `OAuth2RefreshTokenFilter` with `CompositeLogoutHandler(new CookieClearingLogoutHandler(SESSION_COOKIE, X_CSRF_TOKEN), new SecurityContextLogoutHandler())`. Camunda's current host wiring (the 2-arg `OAuth2RefreshTokenFilter` ctor) uses only `CookieClearingLogoutHandler(SESSION_COOKIE)` — the `X_CSRF_TOKEN` cookie and `SecurityContext` are not cleared on refresh failure. The library version is strictly more correct and is the only behaviour change accepted in this migration. Tests asserting the narrower cleanup are updated.

## §5 Test migration

The 23 files in `authentication/src/test/java/io/camunda/authentication/config/` split:

### Filter-chain integration tests — kept (9 files)

- `AbstractWebSecurityConfigTest`, `BasicAuthCsrfFilteringDisabledTest`, `BasicAuthModifiedHeadersWebSecurityConfigTest`, `BasicAuthWebSecurityConfigParameterizedTest`, `BasicAuthWebSecurityConfigTest`, `OidcSaaSWebSecurityConfigTest`, `OidcWebSecurityConfigTest`, `ProtectedApiSecurityFilterChainTest`, `UnprotectedWebSecurityConfigTest`.

These load `WebSecurityConfig` (now library-imported) under `consolidated-auth`, drive requests through `MockMvc`, and assert end-to-end behaviour. Edits expected:

- Import updates where assertions reference moved types (`io.camunda.security.configuration.headers.values.*` → `io.camunda.security.adapters.spring.config.headers.values.*`).
- Behavioural-assertion updates for the broader post-refresh-failure cleanup (§4): `{SESSION_COOKIE, X_CSRF_TOKEN}` cleared, `SecurityContext` cleared.

### OIDC infrastructure tests — untouched (14 files)

`JwtDecoderTest`, `IssuerAwareTokenValidatorTest`, `JWSKeySelectorFactoryTest`, `CompositeJWKSourceTest`, `AssertionJwkProviderTest`, `AssertionConfigTest`, `AudienceValidatorTest`, `OrganizationValidatorTest`, `ClusterValidatorTest`, `CamundaOidcLogoutSuccessHandlerTest`, `OidcClientRegistrationTest`, `OidcAuthenticationConfigurationTest`, `WebappRedirectStrategyTest`, `controllers/`. Test host-owned beans that don't move.

### Tests of deleted classes — deleted

Listed in §4.

### IT tests outside `authentication/` — import-rename only

- `zeebe/qa/integration-tests/.../SecurityHeadersBasicAuthIT.java`
- `zeebe/qa/integration-tests/.../SecurityHeadersOidcIT.java`
- `qa/acceptance-tests/.../BasicAuthLogoutIT.java`
- `qa/acceptance-tests/.../CsrfTokenIT.java`

### `dist/src/test/.../CamundaSecurityConfigurationTest.java`

Tests the `@ConfigurationProperties("camunda.security")` binding for `SecurityConfiguration`. Still works because `SecurityConfiguration extends CamundaSecurityProperties` binds the same prefix. New assertions added for the new fields: `csrf.cookieHttpOnly`, `csrf.ignoredPathPatterns`, `httpHeaders.contentSecurityPolicy.mode`.

## §6 Branches and rollout

### Library work

- **CSL PR1** (existing, on `feat/extract-security-filter-chain`): land as-is, covers chain extraction.
- **CSL PR2** (new branch `feat/host-driven-extensions` stacked on PR1): contains §1 + the §3 typed-config relocation. Build + install: `./mvnw clean install -DskipTests`. Refreshes `0.1.0-SNAPSHOT` in `~/.m2`. Don't push (saved feedback: user pushes manually).

### Camunda work

New branch off `main`: suggested `feat/adopt-camunda-security-library`.

Add the dependency to `authentication/pom.xml` directly with explicit `<version>0.1.0-SNAPSHOT</version>` (per saved feedback for the gatekeeper module — not modifying parent POMs from a submodule context). If the user prefers the conventional monorepo approach (pin in parent `dependencyManagement`), this is the alternative; revisit when the camunda PR opens.

Three commits, in this order, to keep refactor and behaviour-change concerns separate (per AGENTS.md):

1. **`refactor: relocate security headers config to camunda-security-library`** (§3). Pure type relocation. `SecurityConfiguration extends CamundaSecurityProperties`; delete camunda's `CsrfConfiguration` and `headers/*` package; update the 6 import sites identified in §3. No behaviour change.

2. **`refactor: consume camunda-security-library handlers and filters`** (§4). Delete `LoggingAuthenticationFailureHandler`, `OAuth2AuthenticationExceptionHandler`, `CsrfProtectionRequestMatcher`, `OAuth2RefreshTokenFilter` and their tests. Update remaining call sites. Includes the §4 broader-cleanup behaviour change.

3. **`feat: adopt camunda-security-library filter chains`** (§2). Rewrite `WebSecurityConfig`: replace 6 chain `@Bean`s with conditional `@Import`s; register the customizers; introduce `CamundaSecurityPathAdapter`. Keep OIDC infrastructure, `SecurityObservationSettings`, `BasicAuthenticationNoDbConfiguration`. Update the 9 filter-chain tests for any drift.

### Verification per commit (per AGENTS.md "Before Submitting")

- `./mvnw license:format spotless:apply -T1C`
- `./mvnw verify -pl authentication -am -DskipTests=false -DskipITs -Dquickly`
- After commit 3: `./mvnw verify -pl qa/acceptance-tests` and `./mvnw verify -pl zeebe/qa/integration-tests -Dit.test=SecurityHeaders*` for the affected IT tests.
- Don't push.

### Order of merge

Camunda PR depends on CSL PR2 landing first; CSL PR2 depends on CSL PR1. Local development of the camunda PR can proceed in parallel because PR2's outputs are built into `~/.m2` 0.1.0-SNAPSHOT.

## What this design deliberately does not do

- Move conditional activation (`@ConditionalOnAuthenticationMethod`, `@ConditionalOnProtectedApi`, `@ConditionalOnUnprotectedApi`) into the library. The CSL ADR-0006 explicitly chose plain `@Configuration` + host-side `@Import` over `@AutoConfiguration` + conditionals. Activation logic stays in the host.
- Refactor `OidcConfiguration`'s OIDC infrastructure beans. They remain host-owned because they are host-specific (multi-IdP routing, `private_key_jwt` assertions, custom claims provider, etc.).
- Touch the parent monorepo POM for the new dependency — added at the submodule level per saved feedback.
- Push any branches. The user pushes manually.

## Risks and mitigations

- **Library API surface evolves between PR1 and PR2.** Mitigated by §1 explicitly listing every new field/extension hook before implementation. Reviewers of PR2 see the full contract surface.
- **Type-shape churn in the library.** PR1 lands raw-String shape; PR2 replaces with typed shape. Bounded blast radius — no external consumers yet (camunda is first). Library's own tests churn twice.
- **Behaviour drift in tests.** The kept filter-chain tests assert end-to-end behaviour through `MockMvc`; if anything drifts, the tests catch it. The single accepted behaviour change (§4) has explicit test updates.
- **Camunda PR cannot merge until CSL PR2 lands.** Acceptable; the dependency on `0.1.0-SNAPSHOT` makes the relationship explicit.
