# ADR-0004: Support Multiple JWKS Endpoints per OIDC Issuer

## Status

Proposed

## Context

Camunda's OIDC authentication currently supports only a single JWK Set URI per issuer. In some
enterprise deployments, a single OIDC issuer may serve tokens signed by keys hosted at different
JWKS endpoints. For example, an identity provider may use separate JWKS URIs for different signing
key sets while sharing the same issuer claim (`iss`). Tokens signed with key IDs (KIDs) not present
at the configured primary JWKS endpoint fail JWT verification, even though they are legitimately
issued by the same trusted issuer.

This limitation prevents Camunda from operating correctly in environments where key material is
distributed across multiple JWKS endpoints for the same issuer.

See: https://github.com/camunda/product-hub/issues/3472

## Decision

We will extend the OIDC configuration to accept an optional list of additional JWK Set URIs
(`additionalJwkSetUris`) alongside the existing `jwkSetUri` property.

At the Nimbus JOSE+JWT library level, we will introduce a `CompositeJWKSource` that aggregates
multiple `JWKSource` instances. During key selection, the composite source queries each delegate
source in order and returns the first successful match. This approach keeps the change localized to
the JWK source layer, avoiding modifications to the higher-level Spring Security decoder pipeline.

The integration path is:

1. **Configuration**: Add `additionalJwkSetUris` (type `List<String>`) to
   `OidcAuthenticationConfiguration`. The property is optional and remains `null` (unset) unless
   explicitly configured, preserving full backward compatibility.

2. **CompositeJWKSource**: A new `JWKSource<SecurityContext>` implementation that wraps multiple
   `RemoteJWKSet` instances. Key selection iterates through delegates in order, returning keys from
   the first source that provides a match.

3. **Decoder pipeline wiring**: `JWSKeySelectorFactory`, `IssuerAwareJWSKeySelector`, and
   `OidcAccessTokenDecoderFactory` are extended with overloaded methods that accept the additional
   URIs. `WebSecurityConfig` builds and passes the additional URIs map when constructing the decoder.

## Consequences

### Positive

- Operators can configure multiple JWKS endpoints per issuer without deploying custom infrastructure
  (e.g., a JWKS proxy/aggregator).
- The change is fully backward compatible: when `additionalJwkSetUris` is not configured, behavior
  is identical to the current implementation.
- The `CompositeJWKSource` is a self-contained component that can be tested independently.

### Negative

- Key resolution latency may increase in the worst case when a KID is not found in the primary
  source and subsequent sources must be queried.
- Operators must ensure that the additional JWKS endpoints are reachable and trusted, as there is no
  automatic discovery mechanism for additional endpoints.

