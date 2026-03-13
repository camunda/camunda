# Plan: Support Standard Spring Security Configuration Properties

## Problem

Gatekeeper currently requires the custom `camunda.security.authentication.method` property to
activate its authentication pipeline. Users who are familiar with Spring Security's standard
configuration properties cannot use them:

- **OIDC**: `spring.security.oauth2.client.registration.*` / `spring.security.oauth2.client.provider.*`
- **Basic auth**: `spring.security.user.name` / `spring.security.user.password`

This creates a barrier for Spring Boot developers who expect standard conventions to work, and
prevents gatekeeper from being a drop-in replacement in existing Spring Security applications.

## Goal

A user should be able to configure authentication using **either** Camunda's custom properties
or Spring's standard properties, without needing to set `camunda.security.authentication.method`
explicitly. When standard Spring properties are detected, gatekeeper should activate the
appropriate authentication mode automatically.

## Current Architecture

```
camunda.security.authentication.method=OIDC
        │
        ▼
@ConditionalOnAuthenticationMethod(OIDC)
        │
        ├── GatekeeperOidcAutoConfiguration
        │     ├── OidcAuthenticationConfigurationRepository (from camunda.security.authentication.oidc.*)
        │     ├── ClientRegistrationRepository (@ConditionalOnMissingBean)
        │     ├── JwtDecoder (@ConditionalOnMissingBean)
        │     └── TokenClaimsConverter, validators, etc.
        │
        └── GatekeeperSecurityFilterChainAutoConfiguration
              ├── oidcApiSecurityFilterChain
              └── oidcWebappSecurityFilterChain
```

The `@ConditionalOnAuthenticationMethod` annotation checks a single property to decide
which beans to activate. Spring Boot's own auto-configuration (`OAuth2ClientAutoConfiguration`,
`SecurityAutoConfiguration`) is effectively ignored.

## Proposed Architecture

```
Detection layer (new):
  1. camunda.security.authentication.method explicitly set? → use it
  2. spring.security.oauth2.client.registration.* present?  → infer OIDC
  3. spring.security.user.name present?                     → infer BASIC
  4. none of the above?                                     → default BASIC

                    │
                    ▼
        @ConditionalOnAuthenticationMethod (enhanced)
                    │
           ┌────────┴────────┐
           ▼                 ▼
     OIDC pathway       BASIC pathway
     (unchanged)        (unchanged)
```

### Key principle

Gatekeeper's `@ConditionalOnMissingBean` pattern already handles the bean-level integration.
Spring Boot auto-configures `ClientRegistrationRepository`, `JwtDecoder`, `UserDetailsService`
etc. from standard properties. When those beans exist, gatekeeper backs off for those specific
beans but still provides its filter chains, converters, holders, and authentication pipeline
around them.

The only change needed is **how gatekeeper decides which mode to activate**.

## Implementation Plan

### Step 1: Enhance `AuthenticationMethodCondition`

**File**: `ConditionalOnAuthenticationMethod.java`

Modify the condition to auto-detect the authentication method when
`camunda.security.authentication.method` is not explicitly set:

```java
final class AuthenticationMethodCondition implements Condition {
  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    var required = (AuthenticationMethod) metadata
        .getAnnotationAttributes(ConditionalOnAuthenticationMethod.class.getName())
        .get("value");

    var env = context.getEnvironment();
    var explicit = env.getProperty(METHOD_PROPERTY);

    if (explicit != null) {
      // Explicit property always wins
      return AuthenticationMethod.parse(explicit).orElse(DEFAULT_METHOD) == required;
    }

    // Auto-detect from standard Spring properties
    var detected = detectFromEnvironment(env);
    return detected == required;
  }

  private AuthenticationMethod detectFromEnvironment(Environment env) {
    // Check for standard Spring OIDC properties
    if (env.containsProperty("spring.security.oauth2.client.registration")) {
      return AuthenticationMethod.OIDC;
    }
    // More reliable: check for any property matching the registration pattern
    if (env instanceof ConfigurableEnvironment ce) {
      for (var source : ce.getPropertySources()) {
        if (source instanceof EnumerablePropertySource eps) {
          for (var name : eps.getPropertyNames()) {
            if (name.startsWith("spring.security.oauth2.client.registration.")) {
              return AuthenticationMethod.OIDC;
            }
          }
        }
      }
    }
    return DEFAULT_METHOD; // BASIC
  }
}
```

**Effort**: Small. Single file change. Well-contained.

**Risk**: Low. The explicit property check comes first, so existing users are unaffected.
Only activates auto-detection when no explicit method is set.

### Step 2: Handle missing `OidcConfig` in Spring-native OIDC mode

When a user configures OIDC via standard Spring properties, `AuthenticationConfig.oidc()` will
be null because there are no `camunda.security.authentication.oidc.*` properties.

Several beans in `GatekeeperOidcAutoConfiguration` directly access `authenticationConfig.oidc()`
and would NPE:

| Bean | Uses `OidcConfig` for | Fallback needed |
|------|-----------------------|-----------------|
| `tokenClaimsConverter` | `usernameClaim`, `clientIdClaim` | Default to `"sub"` / `"azp"` |
| `tokenValidatorFactory` | `audience`, validators | Skip audience validation |
| `oidcAuthenticationConfigurationRepository` | Primary provider config | Empty — Spring's `ClientRegistrationRepository` provides registrations |
| `assertionJwkProvider` | Keystore config | No-op provider (no private_key_jwt) |
| `oidcTokenEndpointCustomizer` | Token endpoint customisation | No-op customiser |

**Approach**: Create a `OidcConfig.defaults()` factory method that returns sensible defaults:

```java
public record OidcConfig(...) {
  public static OidcConfig defaults() {
    return new OidcConfig(
        null, null, null, null, null, null, null, null, null, null,
        "sub",   // usernameClaim
        null, null, null, null, null, null, null, null, null, null, null);
  }
}
```

Then in `GatekeeperOidcAutoConfiguration`, use the fallback:

```java
final var oidcConfig = authenticationConfig.oidc() != null
    ? authenticationConfig.oidc()
    : OidcConfig.defaults();
```

**Effort**: Medium. Touches `OidcConfig` (domain) and several beans in the OIDC auto-config.

**Risk**: Low. The `@ConditionalOnMissingBean` pattern means Spring's auto-configured beans
take precedence. We only need the `OidcConfig` defaults for beans that Spring doesn't provide
(converters, validators).

### Step 3: Handle `ClientRegistrationRepository` bean ordering

When using standard Spring properties, Spring Boot's `OAuth2ClientAutoConfiguration` creates the
`ClientRegistrationRepository`. Gatekeeper's version has `@ConditionalOnMissingBean` and will
back off.

However, beans that depend on `OidcAuthenticationConfigurationRepository` (which is built from
`OidcConfig`) need to handle the case where the repository is empty because the user configured
providers via Spring properties instead.

**Key beans affected**:
- `jwtDecoder` — needs to work with Spring's `ClientRegistrationRepository` even when
  `OidcAuthenticationConfigurationRepository` has no entries
- `assertionJwkProvider` — should be a no-op when no Camunda-specific keystore config exists
- `oidcTokenEndpointCustomizer` — should be a no-op when no customisation config exists

**Approach**: Add null-safety and empty-collection handling to these beans. The
`OidcAuthenticationConfigurationRepository` already handles an empty state gracefully
(returns empty map). The `jwtDecoder` bean already receives `ClientRegistrationRepository`
as a parameter and extracts registrations from it, so it works regardless of the source.

**Effort**: Small. Defensive null checks in a few bean factory methods.

**Risk**: Low.

### Step 4: Basic auth with standard Spring properties

For basic auth, the situation is simpler. Spring Boot's `SecurityAutoConfiguration` already
creates a `UserDetailsService` with `spring.security.user.name`/`password`. Gatekeeper's
basic auth filter chain uses `httpBasic(Customizer.withDefaults())` which delegates to
Spring Security's `AuthenticationManager`, which uses any available `UserDetailsService`.

The only change needed is in Step 1 — detecting `spring.security.user.name` as an indicator
of BASIC auth mode. The filter chains and converters work as-is because they don't depend
on Camunda-specific basic auth configuration.

**Effort**: None beyond Step 1.

**Risk**: None. Spring's `UserDetailsService` auto-configuration is well-established.

### Step 5: Documentation and tests

- Add integration test: configure OIDC via standard Spring properties, verify login flow works
- Add integration test: configure basic auth via `spring.security.user.*`, verify auth works
- Update integration guide with "Standard Spring configuration" section
- Add ADR documenting the decision to support both property namespaces

**Effort**: Medium.

## User Experience After Implementation

```yaml
# Option A: Camunda-native (existing, unchanged)
camunda:
  security:
    authentication:
      method: OIDC
      oidc:
        issuer-uri: https://idp/realms/camunda
        client-id: my-app
        client-secret: secret

# Option B: Standard Spring OIDC (new)
spring:
  security:
    oauth2:
      client:
        registration:
          my-provider:
            client-id: my-app
            client-secret: secret
        provider:
          my-provider:
            issuer-uri: https://idp/realms/camunda

# Option C: Standard Spring basic auth (new)
spring:
  security:
    user:
      name: admin
      password: secret

# Option D: Mixed — Spring OIDC with Camunda-specific overrides (new)
spring:
  security:
    oauth2:
      client:
        registration:
          my-provider:
            client-id: my-app
            client-secret: secret
        provider:
          my-provider:
            issuer-uri: https://idp/realms/camunda
camunda:
  security:
    authentication:
      oidc:
        username-claim: preferred_username
        groups-claim: groups
```

Option D is powerful — users get Spring's standard provider configuration (with IDE
auto-complete and documentation) while still being able to set Camunda-specific claims
mapping that Spring doesn't know about.

## Effort Summary

| Step | Description | Effort | Risk |
|------|-------------|--------|------|
| 1 | Enhance `AuthenticationMethodCondition` | Small | Low |
| 2 | `OidcConfig.defaults()` fallback | Medium | Low |
| 3 | Null-safety in OIDC bean factories | Small | Low |
| 4 | Basic auth (covered by Step 1) | None | None |
| 5 | Tests and documentation | Medium | None |

**Total estimate**: ~2–3 days of implementation work, primarily in Steps 2 and 5.

## Migration Impact

- **Zero breaking changes** — existing `camunda.security.*` properties continue to work
- **Existing users** are unaffected because explicit `camunda.security.authentication.method`
  takes precedence over auto-detection
- **New users** can choose whichever property style they prefer
