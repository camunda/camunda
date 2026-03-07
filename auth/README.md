# Camunda Auth SDK

Centralized authentication and authorization SDK with OIDC support and all OAuth2 grant types.

A standalone, publishable Spring Security enhancement library — zero Camunda runtime dependencies, Spring Security native, hexagonal architecture, all OAuth2 grant types supported. Every auto-configured bean uses `@ConditionalOnMissingBean`, so consumers can override any component.

## Module Map

|             Artifact ID              |            Module            |                                Purpose                                 |
|--------------------------------------|------------------------------|------------------------------------------------------------------------|
| `camunda-auth-bom`                   | `auth-bom`                   | BOM for consistent version management                                  |
| `camunda-auth-domain`                | `auth-domain`                | Pure domain core — models, ports, SPIs, services (zero framework deps) |
| `camunda-auth-spring`                | `auth-spring`                | Spring Security integration layer (converters, filters, adapters)      |
| `camunda-auth-spring-boot-starter`   | `auth-spring-boot-starter`   | Auto-configuration for Spring Boot apps                                |
| `camunda-auth-persist-rdbms`         | `auth-persist-rdbms`         | RDBMS audit persistence adapter (MyBatis + Liquibase)                  |
| `camunda-auth-persist-elasticsearch` | `auth-persist-elasticsearch` | Elasticsearch audit persistence adapter                                |

All modules share group ID `io.camunda.auth`.

## Quick Start

### 1. Import the BOM

```xml
<dependencyManagement>
  <dependencies>
    <dependency>
      <groupId>io.camunda.auth</groupId>
      <artifactId>camunda-auth-bom</artifactId>
      <version>${camunda-auth.version}</version>
      <type>pom</type>
      <scope>import</scope>
    </dependency>
  </dependencies>
</dependencyManagement>
```

### 2. Add the starter

```xml
<dependency>
  <groupId>io.camunda.auth</groupId>
  <artifactId>camunda-auth-spring-boot-starter</artifactId>
</dependency>
```

### 3. Configure your application

```yaml
# Spring Security OAuth2 client (native Spring config)
spring:
  security:
    oauth2:
      client:
        registration:
          camunda:
            client-id: my-app
            client-secret: ${CLIENT_SECRET}
            scope: openid, profile
        provider:
          camunda:
            issuer-uri: https://idp.example.com/realms/my-realm

# Camunda-specific claim mapping
camunda:
  auth:
    method: oidc                          # "oidc" (default) or "basic"
    oidc:
      username-claim: preferred_username  # JWT claim for username
      client-id-claim: azp               # JWT claim for client ID
```

### 4. Define your SecurityFilterChain

The starter provides building blocks (converters, filters, handlers) but **does not** create a `SecurityFilterChain`. You own the filter chain:

```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

  @Bean
  public SecurityFilterChain filterChain(
      HttpSecurity http,
      AuthFailureHandler authFailureHandler,
      OAuth2RefreshTokenFilter refreshFilter) throws Exception {

    return http
        .authorizeHttpRequests(auth -> auth
            .requestMatchers("/public/**").permitAll()
            .anyRequest().authenticated())
        .oauth2ResourceServer(oauth2 -> oauth2
            .jwt(Customizer.withDefaults())
            .authenticationEntryPoint(authFailureHandler)
            .accessDeniedHandler(authFailureHandler))
        .addFilterAfter(refreshFilter, SecurityContextHolderFilter.class)
        .build();
  }
}
```

## Configuration Reference

### `camunda.auth.*`

| Property | Default  |                 Description                  |
|----------|----------|----------------------------------------------|
| `method` | `"oidc"` | Authentication method: `"oidc"` or `"basic"` |

### `camunda.auth.oidc.*`

|        Property         |        Default         |                            Description                            |
|-------------------------|------------------------|-------------------------------------------------------------------|
| `username-claim`        | `"preferred_username"` | JWT claim used to extract the username                            |
| `client-id-claim`       | `"azp"`                | JWT claim used to extract the client ID (M2M)                     |
| `groups-claim`          | `""`                   | JWT claim containing group memberships (empty = disabled)         |
| `prefer-username-claim` | `true`                 | When both username and client ID are present, prefer the username |

### `camunda.auth.token-exchange.*`

|         Property         |      Default       |                           Description                            |
|--------------------------|--------------------|------------------------------------------------------------------|
| `enabled`                | `false`            | Enable RFC 8693 token exchange support                           |
| `client-registration-id` | `"token-exchange"` | Spring Security OAuth2 client registration ID for token exchange |

### `camunda.auth.obo.*`

|           Property           | Default |                             Description                             |
|------------------------------|---------|---------------------------------------------------------------------|
| `enabled`                    | `false` | Enable On-Behalf-Of token relay filter                              |
| `max-delegation-chain-depth` | `2`     | Maximum allowed depth of nested `act` claims (RFC 8693 Section 4.1) |
| `target-audiences`           | `{}`    | Map of service name to audience URI for OBO exchanges               |

### `camunda.auth.persistence.rdbms.*`

|    Property    | Default |                               Description                               |
|----------------|---------|-------------------------------------------------------------------------|
| `enabled`      | `true`  | Enable RDBMS audit persistence (requires `token-exchange.enabled=true`) |
| `auto-migrate` | `true`  | Run Liquibase schema migrations automatically                           |

### `camunda.auth.persistence.elasticsearch.*`

|    Property    |     Default      |                                   Description                                   |
|----------------|------------------|---------------------------------------------------------------------------------|
| `enabled`      | `false`          | Enable Elasticsearch audit persistence (requires `token-exchange.enabled=true`) |
| `index-prefix` | `"camunda-auth"` | Index name prefix for audit documents                                           |

OIDC provider config uses Spring Security's native `spring.security.oauth2.client.registration.*` and `spring.security.oauth2.client.provider.*` properties — the starter does not duplicate them.

## Architecture

The library follows **hexagonal architecture** (ports & adapters). The domain core has zero framework dependencies, enforced by ArchUnit tests.

```
┌─────────────────────────────────────────────────────────────────┐
│  Consumers (your application)                                   │
│  SecurityFilterChain, Controllers, Services                     │
└────────────────────────────────┬────────────────────────────────┘
                                 │
                                 ▼
                   ┌──────────────────────────────┐
                   │  auth-spring-boot-starter    │
                   │  Auto-configuration          │
                   │  CamundaAuthProperties       │
                   └──────────────┬───────────────┘
                                  │
                                  ▼
                   ┌────────────────────────────────┐
                   │  auth-spring                   │
                   │  SpringAuthenticationAdapter   │
                   │  TokenClaimsConverter          │
                   │  OnBehalfOfTokenRelayFilter    │
                   │  OAuth2RefreshTokenFilter      │
                   │  AuthFailureHandler            │
                   └──────────────┬─────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────┐
│  auth-domain  (pure Java, no framework dependencies)            │
│                                                                 │
│  Models:  CamundaAuthentication, TokenExchangeRequest/Response  │
│  Ports:   AuthenticationPort (in), TokenExchangePort (in)       │
│           TokenExchangeClient (out), TokenStorePort (out)       │
│  SPIs:    MembershipResolver, CamundaAuthenticationConverter    │
│  Services: TokenExchangeService, DelegationChainValidator       │
└────────────────────────┬────────────────────────────────────────┘
                         │
            ┌────────────┴────────────┐
            ▼                         ▼
┌──────────────────────┐  ┌───────────────────────────┐
│  auth-persist-rdbms  │  │  auth-persist-elasticsearch│
│  MyBatis + Liquibase │  │  Elasticsearch Java Client │
└──────────────────────┘  └───────────────────────────┘
```

### Key Design Decisions

- **No `SecurityFilterChain` created** — consumers own their filter chain configuration and wire in the provided beans
- **Token caching** via Spring Security's `OAuth2AuthorizedClientService` — no custom cache layer
- **Graceful degradation** — when SPIs are not implemented, no-op defaults are used (e.g., `NoOpMembershipResolver` extracts groups from a configurable claim)
- **ArchUnit-enforced constraints** — domain models must be records/enums, ports and SPIs must be interfaces, domain must not depend on Spring/Jakarta/Jackson

## Extension Points (SPIs)

All auto-configured beans use `@ConditionalOnMissingBean`. Define your own bean to replace any default.

### MembershipResolver

Resolves groups, roles, tenants, and mapping rules from token claims. The default `NoOpMembershipResolver` only extracts groups from the configured `groups-claim`.

```java
@Bean
public MembershipResolver membershipResolver(MyUserService userService) {
  return (claims, principalId, principalType) -> {
    var memberships = userService.lookupMemberships(principalId);
    return CamundaAuthentication.of(b -> b
        .user(principalId)
        .groupIds(memberships.groups())
        .roleIds(memberships.roles())
        .tenants(memberships.tenants()));
  };
}
```

**Interface:** `io.camunda.auth.domain.spi.MembershipResolver`

### CamundaAuthenticationConverter\<T\>

Converts framework-specific authentication objects to `CamundaAuthentication`. The default chain handles `AbstractOAuth2TokenAuthenticationToken` (JWT and OIDC).

```java
@Bean
public CamundaAuthenticationConverter<Authentication> customConverter() {
  return new CamundaAuthenticationConverter<>() {
    @Override
    public boolean supports(Authentication auth) {
      return auth instanceof MyCustomAuth;
    }

    @Override
    public CamundaAuthentication convert(Authentication auth) {
      // Build CamundaAuthentication from your custom auth
    }
  };
}
```

**Interface:** `io.camunda.auth.domain.spi.CamundaAuthenticationConverter<T>`

### CamundaAuthenticationProvider

Provides the current `CamundaAuthentication` for the request context.

**Interface:** `io.camunda.auth.domain.spi.CamundaAuthenticationProvider`

### TokenExchangeClient

Performs the actual token exchange against the IdP. The default `SpringSecurityTokenExchangeClient` delegates to Spring Security's `OAuth2AuthorizedClientManager`.

**Interface:** `io.camunda.auth.domain.port.outbound.TokenExchangeClient`

## Token Exchange

### Enabling RFC 8693 Token Exchange

```yaml
camunda:
  auth:
    token-exchange:
      enabled: true
      client-registration-id: token-exchange  # must match a Spring Security registration

spring:
  security:
    oauth2:
      client:
        registration:
          token-exchange:
            client-id: my-service
            client-secret: ${TOKEN_EXCHANGE_SECRET}
            authorization-grant-type: "urn:ietf:params:oauth:grant-type:token-exchange"
        provider:
          token-exchange:
            token-uri: https://idp.example.com/realms/my-realm/protocol/openid-connect/token
```

### On-Behalf-Of (OBO)

OBO adds a servlet filter (`OnBehalfOfTokenRelayFilter`) that exchanges the inbound JWT for a downstream token and stores it as a request attribute (`camunda.auth.obo.token`).

```yaml
camunda:
  auth:
    token-exchange:
      enabled: true
    obo:
      enabled: true
      max-delegation-chain-depth: 2  # max nested `act` claims
      target-audiences:
        service-a: "https://api.service-a.example.com"
        service-b: "https://api.service-b.example.com"
```

The `DelegationChainValidator` parses JWT `act` claims (per RFC 8693 Section 4.1) and rejects exchanges that exceed the configured max depth, throwing `TokenExchangeException.DelegationChainTooDeep`.

## Audit Persistence

Token exchange operations can be audited via pluggable persistence adapters. Persistence is activated when `camunda.auth.token-exchange.enabled=true`.

### RDBMS (MyBatis + Liquibase)

Add the dependency:

```xml
<dependency>
  <groupId>io.camunda.auth</groupId>
  <artifactId>camunda-auth-persist-rdbms</artifactId>
</dependency>
```

Enabled by default (`camunda.auth.persistence.rdbms.enabled=true`). Schema is managed by Liquibase and auto-migrated unless `auto-migrate` is set to `false`.

### Elasticsearch

Add the dependency:

```xml
<dependency>
  <groupId>io.camunda.auth</groupId>
  <artifactId>camunda-auth-persist-elasticsearch</artifactId>
</dependency>
```

Enable it:

```yaml
camunda:
  auth:
    persistence:
      elasticsearch:
        enabled: true
        index-prefix: camunda-auth  # default
```

### Composite Store

When multiple persistence adapters are present, they are composed into a `CompositeTokenStore`:

- **Writes** are fanned out to all stores — individual failures are logged but do not block other stores
- **Reads** return the first successful result from the delegate list

## Building

```bash
# Build all modules (skip tests)
mvn clean install -DskipTests -T1C

# Run unit tests
mvn verify -T1C

# Format code (Google Java Format)
mvn spotless:apply -T1C

# Check formatting
mvn spotless:check -T1C
```

These commands should be run from the `auth/` directory. The module is self-contained and builds independently from the Camunda monorepo.
