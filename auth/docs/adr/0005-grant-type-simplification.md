# ADR-0005: Grant Type Model Removal

**Date:** 2025-03 (Original), 2026-03 (Revised)
**Status:** Accepted (Revised)

## Context

The auth library was initially designed with a custom grant type domain model:

- `AuthorizationGrantRequest` (sealed interface) with four record subtypes:
  `TokenExchangeGrantRequest`, `ClientCredentialsGrantRequest`,
  `JwtBearerGrantRequest`, `AuthorizationCodeGrantRequest`
- Corresponding `AuthorizationGrantResponse` sealed hierarchy
- `GrantType` enum mapping grant type URIs
- `TokenType` enum mapping token type URIs
- `DelegatedIdentity` model for delegation chain tracking
- `AuthorizationGrantException` sealed exception hierarchy
- `SpringOidcTokenExchangeConverter` bridging Spring Security to the domain model

A prior revision (2025-03) removed the abstract service/port/client layer
(`AuthorizationGrantService`, `AuthorizationGrantPort`, `AuthorizationGrantClient`,
`auth-sdk` module) but preserved the domain model types, anticipating future consumers.

### The problem

1. **Zero consumers.** No code in the Camunda codebase imports or uses any of the grant type
   request/response records, the `GrantType` enum, `TokenType` enum, `DelegatedIdentity` model,
   or `AuthorizationGrantException` hierarchy.

2. **Spring Security already provides native equivalents.** `AuthorizationGrantType` covers all
   standard grants (authorization_code, client_credentials, jwt_bearer, token_exchange,
   refresh_token, device_code). Spring's `OAuth2AuthorizedClientManager` and
   `OAuth2AuthorizedClientProvider` handle grant execution. The OBO flow
   (`OnBehalfOfTokenRelayFilter`) uses Spring's `OAuth2AuthorizedClientManager` directly.

3. **`SpringOidcTokenExchangeConverter`** was registered as a bean but never injected or called
   by any component.

4. **`DelegatedIdentity`** and `DelegationChainTooDeep`** had no consumers — the
   `DelegationChainValidator` was deleted in the prior revision, and
   `OnBehalfOfTokenRelayFilter` does not track delegation chains.

5. **`OboProperties.maxDelegationChainDepth` and `targetAudiences`** had zero consumers —
   no code reads these configuration values.

## Decision

Remove the entire custom grant type domain model and related infrastructure. Spring Security's
native OAuth2 types are sufficient for all current grant type needs.

### Deleted

**Domain model** (`auth-domain`):
- `AuthorizationGrantRequest` (sealed interface) and all four record subtypes
- `AuthorizationGrantResponse` (sealed interface) and all four record subtypes
- `GrantType` enum
- `TokenType` enum
- `DelegatedIdentity` model
- `AuthorizationGrantException` sealed exception hierarchy

**Spring adapter** (`auth-spring`):
- `SpringOidcTokenExchangeConverter`

**Auto-configuration** (`auth-spring-boot-starter`):
- `springOidcTokenExchangeConverter()` bean in `CamundaAuthAutoConfiguration`
- `maxDelegationChainDepth` and `targetAudiences` properties from `OboProperties`

### Preserved

- All persistence modules (`auth-persist-rdbms`, `auth-persist-elasticsearch`)
- `OnBehalfOfTokenRelayFilter` — uses Spring's `OAuth2AuthorizedClientManager` directly
- `CamundaOboAutoConfiguration` — wires the OBO filter using Spring Security's native types
- Token exchange via Spring Security's native `OAuth2AuthorizedClientManager`

## Consequences

- **Positive:** Eliminated 15 unused source files and reduced domain surface area.
- **Positive:** No behavioral change — all deleted types had zero consumers.
- **Positive:** Future grant type needs use Spring Security's native types directly, avoiding
  a parallel type system.
- **Negative:** If a future need arises for custom grant type modeling beyond Spring Security's
  capabilities, the types would need to be re-created. This is considered unlikely given Spring
  Security's comprehensive OAuth2 support.

