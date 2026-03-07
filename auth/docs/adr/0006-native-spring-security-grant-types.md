# ADR-0006: Native Spring Security Grant Type Support

**Date:** 2026-03
**Status:** Accepted

## Context

The auth library supports five OAuth2/OIDC grant types:

| Grant Type | Spring Security Mechanism | Auth Library Component |
|---|---|---|
| `client_credentials` | `OAuth2AuthorizedClientProvider.clientCredentials()` | `CamundaOidcAutoConfiguration` |
| `authorization_code` + PKCE | `oauth2Login()` + `DefaultOAuth2AuthorizationRequestResolver` | `CamundaWebappSecurityAutoConfiguration` |
| `refresh_token` | `OAuth2AuthorizedClientProvider.refreshToken()` | `OAuth2RefreshTokenFilter` |
| `urn:ietf:params:oauth:grant-type:token-exchange` | `OAuth2AuthorizedClientManager.authorize()` | `OnBehalfOfTokenRelayFilter` |
| `private_key_jwt` (client auth method) | `NimbusJwtClientAuthenticationParametersConverter` | `OidcTokenEndpointCustomizer` |

ADR-0005 removed the custom grant type domain model (`AuthorizationGrantRequest`, `GrantType` enum,
etc.) because it had zero consumers. This ADR documents the strategic decision behind that removal:
the library deliberately relies on Spring Security's native OAuth2 infrastructure for all grant type
operations, rather than maintaining a parallel abstraction layer.

### Why Spring Security's native types are sufficient

1. **`AuthorizationGrantType`** — Spring's enum covers all standard grants (`authorization_code`,
   `client_credentials`, `jwt_bearer`, `token_exchange`, `refresh_token`, `device_code`) plus
   custom grant types via the string constructor.

2. **`OAuth2AuthorizedClientManager`** — Orchestrates grant execution, token caching, and automatic
   refresh. The auth library uses this directly in `OnBehalfOfTokenRelayFilter` for token exchange.

3. **`OAuth2AuthorizedClientProvider`** — Composable providers for each grant type. Spring Boot
   auto-configures providers based on the client registration's `authorization-grant-type` property.

4. **`NimbusJwtClientAuthenticationParametersConverter`** — Handles `private_key_jwt` client
   authentication by generating signed JWT assertions. The auth library configures this via
   `OidcTokenEndpointCustomizer` using keystores loaded through `AssertionJwkProvider`.

5. **`oauth2Login()`** — Spring Security's built-in authorization code flow with PKCE support.
   The auth library configures this via `CamundaWebappSecurityAutoConfiguration` with custom
   token endpoint customization when assertion-based client auth is needed.

## Decision

The auth library relies exclusively on Spring Security's native `AuthorizationGrantType`,
`OAuth2AuthorizedClientManager`, and `OAuth2AuthorizedClientProvider` for all grant type operations.
Custom providers or converters will only be introduced if Spring Security's built-in components
prove insufficient for a specific use case.

### Per-grant-type configuration

- **`client_credentials`**: Auto-configured via `CamundaOidcAutoConfiguration` when
  `camunda.security.authentication.oidc.grantType=client_credentials`. Uses Spring Boot's
  default `OAuth2AuthorizedClientProvider.clientCredentials()`.

- **`authorization_code` + PKCE**: Configured via `CamundaWebappSecurityAutoConfiguration` when
  `camunda.auth.security.webapp-enabled=true`. Spring Security generates PKCE challenges
  automatically when the client registration uses `authorization_code`.

- **`refresh_token`**: Handled by Spring Security's `OAuth2AuthorizedClientProvider.refreshToken()`
  composed into the `OAuth2AuthorizedClientManager`. Tokens are refreshed automatically when expired.

- **Token exchange (RFC 8693)**: `OnBehalfOfTokenRelayFilter` uses
  `OAuth2AuthorizedClientManager.authorize()` with a `TokenExchangeOAuth2AuthorizedClientProvider`
  to exchange incoming JWT tokens for downstream service tokens.

- **`private_key_jwt`**: `OidcTokenEndpointCustomizer` adds
  `NimbusJwtClientAuthenticationParametersConverter` to the token response client. The JWT assertion
  is signed using a private key loaded from a keystore via `AssertionJwkProvider`.

### Integration test mandate

Every supported grant type must have a dedicated integration test validating end-to-end
functionality against a real IdP (Keycloak via Testcontainers). Tests use a shared Keycloak
container (`SharedKeycloakContainer`) with per-test realm isolation to minimize startup overhead.

## Consequences

- **Positive:** No parallel type system to maintain — the library composes with Spring Security
  rather than wrapping it.
- **Positive:** Consumers benefit from Spring Security's built-in token caching, automatic refresh,
  and error handling without additional library code.
- **Positive:** Upgrades to Spring Security automatically bring support for new grant types and
  protocol improvements.
- **Positive:** Integration tests verify that the library's configuration correctly activates
  Spring Security's native grant type handling for each supported flow.
- **Negative:** If a grant type requires behavior that Spring Security does not support, a custom
  `OAuth2AuthorizedClientProvider` would need to be implemented. This is considered unlikely given
  Spring Security's comprehensive OAuth2 support.
