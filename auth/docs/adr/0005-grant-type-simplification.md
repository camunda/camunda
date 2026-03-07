# ADR-0005: Grant Type Simplification — Remove Unused Abstractions

**Date:** 2025-03
**Status:** Accepted

## Context

The auth library was initially designed with a generic authorization grant framework supporting
multiple OAuth 2.0 grant types:

- `AuthorizationGrantRequest` (sealed interface)
  - `TokenExchangeGrantRequest`
  - `ClientCredentialsGrantRequest`
  - `JwtBearerGrantRequest`
  - `AuthorizationCodeGrantRequest`

Each grant type had corresponding request/response record pairs, and a service layer:

- `AuthorizationGrantService` — orchestrator dispatching to the correct grant client
- `DelegationChainValidator` — validated delegation chains for token exchange
- `AuthorizationGrantPort` (inbound port) — use-case interface
- `AuthorizationGrantClient` (outbound port) — infrastructure interface for each grant type
- `SpringSecurityAuthorizationGrantClient` — Spring Security adapter implementing all grants

Additionally, there was an `auth-sdk` module providing a public facade (`CamundaAuthSdk`) that
wrapped all of these in a consumer-friendly API.

### The problem

1. **Only `TokenExchangeGrantRequest` was actually used.** The `ClientCredentials`, `JwtBearer`,
   and `AuthorizationCode` grant types had implementations but zero consumers in the monorepo
   or any known external project.

2. **The `auth-sdk` module had zero consumers.** No code anywhere imported from
   `io.camunda.auth.sdk`.

3. **The generic grant service added indirection without value.** A sealed interface with four
   permits, a dispatcher service, and a delegation chain validator — all for a single active
   grant type.

4. **`LoggingAuthenticationFailureHandler`** duplicated Spring Security's built-in failure
   handling without adding meaningful functionality.

## Decision

Remove the unused abstractions:

### Deleted

- **`auth-sdk` module** — entire module (5 Java files, 1 POM). Zero consumers.
- **`AuthorizationGrantService`** — generic dispatcher. Only token exchange remains, handled
  directly.
- **`DelegationChainValidator`** — only used by the grant service.
- **`AuthorizationGrantPort`** (inbound port) — the generic use-case interface.
- **`AuthorizationGrantClient`** (outbound port) — the generic infrastructure interface.
- **`ClientCredentialsGrantRequest` / `ClientCredentialsGrantResponse`** — unused grant type.
- **`JwtBearerGrantRequest` / `JwtBearerGrantResponse`** — unused grant type.
- **`AuthorizationCodeGrantRequest` / `AuthorizationCodeGrantResponse`** — unused grant type.
- **`SpringSecurityAuthorizationGrantClient`** — Spring adapter for the generic grant framework.
- **`CamundaAuthorizationGrantAutoConfiguration`** — auto-config for the grant service.
- **`LoggingAuthenticationFailureHandler`** — replaced by `AuthFailureHandler`.
- **6 test files** covering the above.

### Preserved

- **`AuthorizationGrantRequest`** (sealed interface) — now permits only `TokenExchangeGrantRequest`.
- **`AuthorizationGrantResponse`** (sealed interface) — now permits only `TokenExchangeGrantResponse`.
- **`TokenExchangeGrantRequest` / `TokenExchangeGrantResponse`** — the one grant type that is
  actually used.
- **Token exchange service and OBO auto-configuration** — `CamundaOboAutoConfiguration` handles
  on-behalf-of token exchange directly.
- **Persistence modules** (`auth-persist-elasticsearch`, `auth-persist-rdbms`) — preserved as
  designed for storage backend selection. `TokenStorePort` and `TokenMetadata` remain.

### Net result

- **2,549 lines deleted**, 29 lines added.
- The sealed hierarchy is simplified but remains extensible — new grant types can be added
  to the `permits` clause when actually needed.

## Consequences

- **Positive:** Significantly less code to understand and maintain.
- **Positive:** No speculative abstractions — code exists only when there's a consumer.
- **Positive:** The sealed interface pattern is preserved, so adding a new grant type later
  is a backward-compatible addition.
- **Negative:** If a consumer needs `ClientCredentials` or `JwtBearer` grants in the future,
  the implementation must be re-created. However, the patterns are well-established and the
  deleted code is preserved in git history.

