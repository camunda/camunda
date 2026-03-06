# Custom Spring Security Implementation Audit

This document audits each custom Spring Security implementation in the `auth/` library,
documenting **why** each exists, whether it could be replaced by Spring Boot/Security defaults,
and what the migration path would look like.

---

## 1. ClientRegistrationFactory

**Location:** `auth-spring/.../oidc/ClientRegistrationFactory.java`

**What it does:** Factory for constructing `ClientRegistration` objects from
`OidcAuthenticationConfiguration`. Supports two modes: manual construction from explicit URI
properties, or auto-discovery via OIDC issuer endpoint. Maps custom domain config to Spring's
`ClientRegistration` model and injects provider metadata (end-session endpoint for logout).

**Why custom:** Spring Boot's `ClientRegistrations.fromIssuerLocation()` covers basic auto-discovery,
but Camunda needs:
- Selective URI overrides (partially manual OIDC configuration)
- Custom claim mapping (`usernameClaim` -> `userNameAttributeName`)
- Custom grant types beyond standard Spring enums
- Provider metadata injection (`end_session_endpoint`) for OIDC logout
- Fallback between auto-discovery and explicit configuration

**Replaceability:** Partially. Could simplify by removing the manual branch and relying entirely on
issuer auto-discovery, but that requires all OIDC providers to expose complete metadata.

**Non-Spring JDK clients affected:** No (Spring-only concern).

---

## 2. OidcAccessTokenDecoderFactory

**Location:** `auth-spring/.../oidc/OidcAccessTokenDecoderFactory.java`

**What it does:** Creates `JwtDecoder` instances for validating access tokens. Supports single-issuer
and multi-issuer scenarios. Validates issuer presence, configures JWT type verification (JWT +
`at+jwt` per RFC 9068), and delegates signing key selection to `IssuerAwareJWSKeySelector`.

**Why custom:** Spring's `NimbusJwtDecoder` is inherently single-issuer. Camunda requires:
- Multi-issuer support with dynamic key selector lookup
- `at+jwt` JWT type validation (RFC 9068)
- Custom `JWTClaimsSetAwareJWSKeySelector` for issuer-based routing
- Per-provider token validators

**Replaceability:** No. Spring provides no multi-issuer JWT decoder. Alternatives:
- Create a decoder per issuer and route manually (adds complexity)
- Build a custom processor with issuer-aware key selection (current approach)

**Non-Spring JDK clients affected:** No.

---

## 3. IssuerAwareJWSKeySelector

**Location:** `auth-spring/.../oidc/IssuerAwareJWSKeySelector.java`

**What it does:** Implements `JWTClaimsSetAwareJWSKeySelector` to dynamically select the correct
JWK source based on the JWT's `iss` claim. Caches selectors per issuer and supports additional
JWK source URIs.

**Why custom:** Spring has no built-in multi-issuer key selector. Standard flows assume a single
issuer per decoder. This bridges multiple OIDC providers in one application with dynamic routing
based on JWT claims.

**Replaceability:** No. Inherently custom. The only alternative is maintaining separate decoders per
issuer, which loses the single-decoder abstraction.

**Non-Spring JDK clients affected:** No.

---

## 4. CompositeJWKSource

**Location:** `auth-spring/.../oidc/CompositeJWKSource.java`

**What it does:** Composite pattern for JWK sources: tries each source sequentially, returning keys
from the first successful one. Logs failures and supports graceful degradation.

**Why custom:** Neither Spring Security nor Nimbus Jose4J provides JWKSource composition. Needed for
backup JWK endpoints (failover) and multiple key distributions in one OIDC provider.

**Replaceability:** No. Neither framework provides JWKSource composition.

**Non-Spring JDK clients affected:** No.

---

## 5. WebSessionRepository

**Location:** `auth-spring/.../session/WebSessionRepository.java`

**What it does:** Implements Spring's `SessionRepository<WebSession>` interface, adapting the
domain `SessionPersistencePort` SPI to Spring Session. Handles session lifecycle with change
tracking, expiration checks, and polling request detection (`x-is-polling` header).

**Why custom:** Spring Session provides default repositories (InMemory, JDBC, Redis), but Camunda:
- Already has domain-layer session persistence (hexagonal port pattern)
- Needs the adapter to bridge domain SPI to Spring Session
- Requires polling detection for optimized handling
- Delegates actual storage to domain layer (separation of concerns)

**Replaceability:** Partially. Could use a Spring-provided repository if the domain persistence
layer is removed, but the polling optimization and SPI bridge are application-specific.

**Migration path:** If Spring Session JDBC or Redis is acceptable, configure
`spring.session.store-type=jdbc|redis` and remove custom repository. This would require the
persistence adapters (`auth-persist-*`) to be replaced by Spring Session auto-configuration.

**Non-Spring JDK clients affected:** No.

---

## 6. CsrfProtectionRequestMatcher

**Location:** `auth-spring/.../filter/CsrfProtectionRequestMatcher.java`

**What it does:** Custom `RequestMatcher` for CSRF protection. Exempts safe HTTP methods (GET, HEAD,
TRACE, OPTIONS), configured paths, Swagger UI requests, and non-browser-session requests.

**Why custom:** Spring's default CSRF matcher only exempts safe methods. Camunda needs configurable
path exclusions, Swagger UI handling, and browser session detection.

**Replaceability:** Yes, with configuration. Spring Security's `.ignoringRequestMatchers()` DSL can
achieve similar results:

```java
http.csrf(csrf -> csrf.ignoringRequestMatchers("/path1", "/path2"))
```

However, this would spread path exclusions across security config instead of centralizing them.

**Migration path:** Use Spring Security's `CsrfConfigurer.ignoringRequestMatchers()` with
`AntPathRequestMatcher` patterns. The browser session detection would need a custom
`RequestMatcher` regardless.

**Non-Spring JDK clients affected:** No.

---

## 7. ClientAwareOAuth2AuthorizationRequestResolver

**Location:** `auth-spring/.../oidc/ClientAwareOAuth2AuthorizationRequestResolver.java`

**What it does:** Implements `OAuth2AuthorizationRequestResolver` for multi-client OIDC. Extracts
registration ID from request URI, creates per-client resolvers, and applies client-specific
customizations (additional parameters, `resource` parameter for multi-tenant OAuth 2.0 Resource
Indicators RFC 8707).

**Why custom:** Spring's `DefaultOAuth2AuthorizationRequestResolver` is single-client by default.
Camunda needs per-client authorization flows, client-specific parameter customization, and
resource parameter injection.

**Replaceability:** Partially. Spring supports client-specific customization via
`resolver.setAuthorizationRequestCustomizer()`, but doesn't route based on registration ID in
the URI.

**Non-Spring JDK clients affected:** No.

---

## 8. OidcTokenEndpointCustomizer

**Location:** `auth-spring/.../oidc/OidcTokenEndpointCustomizer.java`

**What it does:** Customizes the OAuth2 token endpoint with
`RestClientAuthorizationCodeTokenResponseClient` when custom parameters are needed (JWT client
assertion for `private_key_jwt` auth, `resource` parameter for multi-tenant).

**Why custom:** Spring's default token client handles standard OAuth2/OIDC but doesn't support:
- `private_key_jwt` client authentication with keystore-based JWT signing
- `resource` parameter injection (RFC 8707)
- Conditional replacement (only when keystore/resource is configured)

**Replaceability:** Partially. Spring 6.1+ provides more customization hooks, but `private_key_jwt`
support remains limited without custom JWK management. Could simplify if Spring adds resource
parameter support.

**Non-Spring JDK clients affected:** No.

---

## 9. OAuth2RefreshTokenFilter

**Location:** `auth-spring/.../filter/OAuth2RefreshTokenFilter.java`

**What it does:** Servlet filter that proactively refreshes expiring OAuth2 access tokens. Monitors
token expiration with configurable clock skew. Logs out user if refresh fails.

**Why custom:** Spring Security doesn't automatically refresh tokens on every request. Default
behavior uses the expired token until forced re-authentication. Camunda needs transparent
proactive refresh before expiration with automatic logout on failure.

**Replaceability:** No. Spring 6.2+ added `OAuth2AccessTokenResponseClient` refresh capability, but
it's reactive-focused, not automatic (manual call needed), and doesn't provide clock skew
tolerance or auto-logout on failure.

**Non-Spring JDK clients affected:** No.

---

## Summary

| # |                     Class                     | Replaceable |           Key Gap in Spring            |
|---|-----------------------------------------------|-------------|----------------------------------------|
| 1 | ClientRegistrationFactory                     | Partially   | Selective URI override + metadata      |
| 2 | OidcAccessTokenDecoderFactory                 | No          | Multi-issuer support                   |
| 3 | IssuerAwareJWSKeySelector                     | No          | Multi-issuer key routing               |
| 4 | CompositeJWKSource                            | No          | JWKSource composition/failover         |
| 5 | WebSessionRepository                          | Partially   | Domain layer SPI integration           |
| 6 | CsrfProtectionRequestMatcher                  | Yes         | Path-based exclusions (can use config) |
| 7 | ClientAwareOAuth2AuthorizationRequestResolver | Partially   | Multi-client registration ID routing   |
| 8 | OidcTokenEndpointCustomizer                   | Partially   | private_key_jwt + resource parameter   |
| 9 | OAuth2RefreshTokenFilter                      | No          | Proactive token refresh + clock skew   |

### Must Keep (No Spring Alternative)

- **IssuerAwareJWSKeySelector** — multi-issuer is fundamental to the architecture
- **OidcAccessTokenDecoderFactory** — wraps necessary multi-issuer complexity
- **CompositeJWKSource** — failover pattern not available in any framework
- **OAuth2RefreshTokenFilter** — proactive refresh is a hard requirement

### Could Simplify with Future Spring Releases

- **OidcTokenEndpointCustomizer** — if Spring adds resource parameter support
- **ClientAwareOAuth2AuthorizationRequestResolver** — if Spring modernizes multi-client routing

### Could Replace with Configuration

- **CsrfProtectionRequestMatcher** — use `.ignoringRequestMatchers()` with more config
- **WebSessionRepository** — use Spring Session defaults if domain persistence is removed
- **ClientRegistrationFactory** — could partially simplify with issuer auto-discovery only

