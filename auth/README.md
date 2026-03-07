# Camunda Auth SDK

Centralized authentication and authorization SDK with OIDC support.

A standalone, publishable Spring Security enhancement library — zero Camunda runtime dependencies, Spring Security native, hexagonal architecture. Every auto-configured bean uses `@ConditionalOnMissingBean`, so consumers can override any component.

## Module Map

|             Artifact ID              |            Module            |                                Purpose                                 |
|--------------------------------------|------------------------------|------------------------------------------------------------------------|
| `camunda-auth-bom`                   | `auth-bom`                   | BOM for consistent version management                                  |
| `camunda-auth-domain`                | `auth-domain`                | Pure domain core — models, ports, SPIs, services (zero framework deps) |
| `camunda-auth-spring`                | `auth-spring`                | Spring Security integration layer (converters, filters, adapters)      |
| `camunda-auth-spring-boot-starter`   | `auth-spring-boot-starter`   | Auto-configuration for Spring Boot apps                                |
| `camunda-auth-persist-rdbms`         | `auth-persist-rdbms`         | RDBMS persistence adapter (MyBatis + Liquibase)                        |
| `camunda-auth-persist-elasticsearch` | `auth-persist-elasticsearch` | Elasticsearch persistence adapter                                      |

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

Alternatively, the starter's auto-configuration classes (`CamundaSecurityFilterChainAutoConfiguration`, `CamundaWebappSecurityAutoConfiguration`, `CamundaBasicAuthAutoConfiguration`) **do** create filter chains when the appropriate properties are set. This is useful for applications that want the full managed experience rather than building their own chains.

---

## Configuration Reference

### `camunda.auth.*`

|     Property      | Default  |                    Description                     |
|-------------------|----------|----------------------------------------------------|
| `method`          | `"oidc"` | Authentication method: `"oidc"` or `"basic"`       |
| `unprotected-api` | `false`  | When `true`, API paths are open (development only) |

### `camunda.auth.oidc.*`

|        Property         |        Default         |                            Description                            |
|-------------------------|------------------------|-------------------------------------------------------------------|
| `username-claim`        | `"preferred_username"` | JWT claim used to extract the username                            |
| `client-id-claim`       | `"azp"`                | JWT claim used to extract the client ID (M2M)                     |
| `groups-claim`          | `""`                   | JWT claim containing group memberships (empty = disabled)         |
| `prefer-username-claim` | `true`                 | When both username and client ID are present, prefer the username |

### `camunda.auth.basic.*`

|           Property            | Default |                              Description                              |
|-------------------------------|---------|-----------------------------------------------------------------------|
| `secondary-storage-available` | `false` | Whether a database is available. Required for basic auth to function. |

### `camunda.auth.token-exchange.*`

|         Property         |      Default       |                           Description                            |
|--------------------------|--------------------|------------------------------------------------------------------|
| `enabled`                | `false`            | Enable RFC 8693 token exchange support                           |
| `client-registration-id` | `"token-exchange"` | Spring Security OAuth2 client registration ID for token exchange |

### `camunda.auth.obo.*`

| Property  | Default |              Description               |
|-----------|---------|----------------------------------------|
| `enabled` | `false` | Enable On-Behalf-Of token relay filter |

### `camunda.auth.session.*`

|      Property      |  Default  |                             Description                              |
|--------------------|-----------|----------------------------------------------------------------------|
| `enabled`          | `false`   | Enable HTTP session-based authentication holding                     |
| `refresh-interval` | `"PT30S"` | How often to refresh authentication from session (ISO-8601 duration) |

### `camunda.auth.persistence.*`

| Property |    Default     |                              Description                               |
|----------|----------------|------------------------------------------------------------------------|
| `type`   | —              | Storage backend: `"elasticsearch"` or `"rdbms"`                        |
| `mode`   | `"standalone"` | `"standalone"` (library owns reads+writes) or `"external"` (read-only) |

### `camunda.auth.persistence.rdbms.*`

|    Property    | Default |                  Description                  |
|----------------|---------|-----------------------------------------------|
| `enabled`      | `true`  | Enable RDBMS persistence adapter              |
| `auto-migrate` | `true`  | Run Liquibase schema migrations automatically |

### `camunda.auth.persistence.elasticsearch.*`

|    Property    |     Default      |               Description                |
|----------------|------------------|------------------------------------------|
| `enabled`      | `false`          | Enable Elasticsearch persistence adapter |
| `index-prefix` | `"camunda-auth"` | Index name prefix for documents          |

### `camunda.auth.security.*`

|        Property         |                 Default                  |                           Description                            |
|-------------------------|------------------------------------------|------------------------------------------------------------------|
| `unprotected-paths`     | `/error`, `/actuator/**`, `/ready`, etc. | Paths that bypass authentication entirely                        |
| `api-paths`             | `/api/**`, `/v1/**`, `/v2/**`, `/mcp/**` | API paths (JWT resource server)                                  |
| `unprotected-api-paths` | `/v2/license`, `/v2/setup/user`, etc.    | API paths exempt from authentication                             |
| `webapp-paths`          | `/login/**`, `/logout`, `/`, etc.        | Paths handled by the webapp security filter chain                |
| `webapp-enabled`        | `false`                                  | Enable the webapp security filter chain (form login, CSRF, etc.) |
| `csrf-enabled`          | `true`                                   | Enable CSRF protection on webapp filter chain                    |
| `csrf-token-name`       | `"X-CSRF-TOKEN"`                         | Cookie and header name for the CSRF token                        |
| `session-cookie`        | `"camunda-session"`                      | Name of the session cookie (deleted on logout)                   |
| `idp-logout-enabled`    | `true`                                   | Redirect to IdP logout endpoint on application logout            |

OIDC provider config uses Spring Security's native `spring.security.oauth2.client.registration.*` and `spring.security.oauth2.client.provider.*` properties — the starter does not duplicate them.

---

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
│  Models:  CamundaAuthentication, TokenMetadata, SessionData     │
│  Ports:   AuthenticationPort (in), TokenStorePort (out)         │
│           UserRead/WritePort, RoleRead/WritePort, ...           │
│  SPIs:    MembershipResolver, CamundaAuthenticationConverter    │
│           SessionPersistencePort, CamundaUserProvider           │
└────────────────────────┬────────────────────────────────────────┘
                         │
            ┌────────────┴────────────┐
            ▼                         ▼
┌──────────────────────┐  ┌───────────────────────────┐
│  auth-persist-rdbms  │  │  auth-persist-elasticsearch│
│  MyBatis + Liquibase │  │  Elasticsearch Java Client │
└──────────────────────┘  └───────────────────────────┘
```

### Dependency Rules (ArchUnit-Enforced)

1. **`auth-domain` must not depend on Spring, Jakarta Servlet, or Jackson runtime** — only `jackson-annotations` (provided scope) and `json-path` are allowed
2. **Models must be records, enums, or sealed interfaces** — no mutable classes
3. **Ports and SPIs must be interfaces**
4. **Models must not depend on ports or SPIs**
5. **Domain exceptions must extend `RuntimeException`**
6. **Dependency direction**: `auth-domain` ← `auth-spring` ← `auth-spring-boot-starter`; persist modules depend only on `auth-domain`

---

## Deliberate Design Decisions

This section explains Camunda-specific customizations and architectural decisions — things that go beyond standard Spring Security defaults and why.

### Why a Custom `ClientRegistrationFactory`?

Spring Boot's `spring.security.oauth2.client.registration.*` properties work well for single-provider setups. Camunda uses a custom `ClientRegistrationFactory` because:

- **Selective URI overrides**: Some IdPs don't expose complete OIDC discovery metadata. The factory supports a hybrid mode — auto-discovery via `issuerUri` with selective overrides for `tokenUri`, `authorizationUri`, or `jwkSetUri`.
- **Custom grant types**: Spring's enum covers standard types (`authorization_code`, `client_credentials`), but Camunda supports `urn:ietf:params:oauth:grant-type:token-exchange` (RFC 8693) and `urn:ietf:params:oauth:grant-type:jwt-bearer` (RFC 7523) which require string-based `AuthorizationGrantType`.
- **Provider metadata injection**: The factory injects `end_session_endpoint` metadata for OIDC RP-Initiated Logout (required for IdPs that don't include it in their discovery document).
- **Claim mapping**: Maps domain-level `usernameClaim` to Spring's `userNameAttributeName` for consistent user identification.

All registrations are still stored in Spring's `InMemoryClientRegistrationRepository` — the factory only handles construction.

### Why Multi-Issuer JWT Validation?

Standard Spring Security assumes a single JWT issuer per `JwtDecoder`. Camunda supports multiple OIDC providers simultaneously (e.g., Keycloak for human users + Auth0 for M2M clients). This requires three custom components:

1. **`IssuerAwareJWSKeySelector`** — Routes JWK resolution based on the token's `iss` claim. Each issuer has its own JWK endpoint(s). Selectors are lazily cached per issuer.

2. **`CompositeJWKSource`** — Failover pattern: tries multiple JWK endpoints sequentially, returning keys from the first successful one. Needed for providers with backup JWK distributions.

3. **`OidcAccessTokenDecoderFactory`** — Builds the multi-issuer decoder, configures `at+jwt` type validation (RFC 9068), and wires the issuer-aware key selector with per-provider token validators.

Spring provides no built-in alternative for multi-issuer JWT validation.

### Why a Custom Token Refresh Filter?

Spring Security does not proactively refresh expiring tokens. The default behavior uses a token until it expires, then forces re-authentication. `OAuth2RefreshTokenFilter`:

- Detects tokens nearing expiration (configurable clock skew tolerance)
- Proactively refreshes via the refresh token grant before the access token expires
- Automatically logs out the user if refresh fails (e.g., refresh token revoked)
- Skips refresh for polling requests (`x-is-polling` header) to reduce IdP load

### Why Custom CSRF Protection?

`CsrfProtectionRequestMatcher` centralizes CSRF exemptions that would otherwise be scattered across filter chain configurations:

- Exempts configured unprotected paths and API paths
- Exempts non-browser requests (no session = no CSRF risk)
- Exempts Swagger UI requests for development ergonomics
- Could be replaced by Spring Security's `.ignoringRequestMatchers()` DSL, but centralizing provides consistency across multiple filter chains

### Persistence Modes: Standalone vs. External

The library supports two persistence modes via `camunda.auth.persistence.mode`:

- **`standalone`** — The library owns all reads AND writes. The persistence store is the source of truth. Use this when there is no external system populating the storage (e.g., Camunda Hub).
- **`external`** — An external system writes data (e.g., Zeebe exporters). The library only reads. Write port beans (`UserWritePort`, `RoleWritePort`, etc.) are NOT created.

This is configured via `@ConditionalOnProperty(name = "camunda.auth.persistence.mode", havingValue = "standalone")` on write port bean definitions. Read ports are always available.

### Read/Write Port Separation

Each entity type has separate read and write ports:

|        Read Port        |        Write Port        |     Domain Model      |
|-------------------------|--------------------------|-----------------------|
| `UserReadPort`          | `UserWritePort`          | `AuthUser`            |
| `RoleReadPort`          | `RoleWritePort`          | `AuthRole`            |
| `TenantReadPort`        | `TenantWritePort`        | `AuthTenant`          |
| `GroupReadPort`         | `GroupWritePort`         | `AuthGroup`           |
| `MappingRuleReadPort`   | `MappingRuleWritePort`   | `AuthMappingRule`     |
| `AuthorizationReadPort` | `AuthorizationWritePort` | `AuthorizationRecord` |

Combined ports (read + write together):

|           Port           |  Domain Model   |
|--------------------------|-----------------|
| `SessionPersistencePort` | `SessionData`   |
| `TokenStorePort`         | `TokenMetadata` |

### `@ConditionalOnMissingBean` Everywhere

Every auto-configured bean uses `@ConditionalOnMissingBean`. This means consumers can override **any** component by defining their own bean of the same type. The library's defaults are always overridable.

### Composite Token Store

When multiple persistence adapters (`auth-persist-rdbms` + `auth-persist-elasticsearch`) are both on the classpath and enabled, they are composed into a `CompositeTokenStore`:

- **Writes** fan out to all stores — individual failures are logged but do not block other stores
- **Reads** return the first successful result from the delegate list
- If only one store is present, the composite is a pass-through

---

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

### BasicAuthMembershipResolver

Resolves memberships for locally-authenticated (basic auth) users. Used by `UsernamePasswordAuthenticationTokenConverter`.

```java
@Bean
public BasicAuthMembershipResolver basicAuthResolver(MyUserService userService) {
  return username -> {
    var memberships = userService.lookupMemberships(username);
    return CamundaAuthentication.of(b -> b
        .user(username)
        .groupIds(memberships.groups())
        .roleIds(memberships.roles()));
  };
}
```

**Interface:** `io.camunda.auth.domain.spi.BasicAuthMembershipResolver`

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

Provides the current `CamundaAuthentication` for the request context. The default `DefaultCamundaAuthenticationProvider` composites all registered holders and converters.

**Interface:** `io.camunda.auth.domain.spi.CamundaAuthenticationProvider`

### CamundaUserProvider

Provides the current authenticated user's info for web UIs. Two built-in implementations: `BasicCamundaUserProvider` and `OidcCamundaUserProvider`.

**Interface:** `io.camunda.auth.domain.spi.CamundaUserProvider`

### SessionPersistencePort

Provides persistent web session storage. Built-in adapters exist for Elasticsearch and RDBMS. The `deleteExpired()` method allows backends to handle expiry natively (e.g., Elasticsearch `deleteByQuery`, SQL `DELETE WHERE`) rather than loading all sessions into memory.

```java
@Bean
public SessionPersistencePort sessionPersistence(MySessionStore store) {
  return new SessionPersistencePort() {
    @Override public SessionData findById(String id) { return store.get(id); }
    @Override public void save(SessionData data) { store.put(data); }
    @Override public void deleteById(String id) { store.remove(id); }
    @Override public List<SessionData> findAll() { return store.getAll(); }
    @Override public void deleteExpired() { store.removeExpired(); }
  };
}
```

**Interface:** `io.camunda.auth.domain.spi.SessionPersistencePort`

### SecurityFilterChainCustomizer

Extension point for adding custom filters to auto-configured filter chains (webapp and basic auth).

```java
@Bean
public SecurityFilterChainCustomizer myCustomizer() {
  return http -> http.addFilterBefore(new MyFilter(), AuthorizationFilter.class);
}
```

**Interface:** `io.camunda.auth.spring.SecurityFilterChainCustomizer`

### Other SPIs

|             SPI              |                              Purpose                               |
|------------------------------|--------------------------------------------------------------------|
| `AdminUserCheckProvider`     | Checks whether an admin user exists (for first-run setup redirect) |
| `WebComponentAccessProvider` | Checks authorization for web UI component access                   |
| `UserProfileProvider`        | Resolves display name and email from username (basic auth UI)      |
| `TenantInfoProvider`         | Resolves tenant details (id + name) from tenant IDs                |

---

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
```

The OBO filter uses Spring Security's `OAuth2AuthorizedClientManager` to perform the token exchange via the configured client registration.

---

## Persistence

### Session Persistence

When `SessionPersistencePort` is available and persistent sessions are enabled, the library manages HTTP session storage via `WebSessionRepository`:

```yaml
camunda:
  persistent:
    sessions:
      enabled: true
```

The `WebSessionDeletionTask` runs on a scheduled executor to clean up expired sessions. Backends implement `deleteExpired()` natively:

- **Elasticsearch**: Uses `deleteByQuery` with a Painless script to compute expiry server-side
- **RDBMS**: Uses a SQL `DELETE WHERE` with computed expiry condition
- **Fallback**: Loads all sessions via `findAll()` and filters in memory (deprecated)

### Audit Persistence (Token Exchange)

Token exchange operations can be audited via `TokenStorePort`. Persistence is activated when `camunda.auth.token-exchange.enabled=true`.

#### RDBMS (MyBatis + Liquibase)

```xml
<dependency>
  <groupId>io.camunda.auth</groupId>
  <artifactId>camunda-auth-persist-rdbms</artifactId>
</dependency>
```

Schema is managed by Liquibase and auto-migrated unless `auto-migrate=false`.

#### Elasticsearch

```xml
<dependency>
  <groupId>io.camunda.auth</groupId>
  <artifactId>camunda-auth-persist-elasticsearch</artifactId>
</dependency>
```

```yaml
camunda:
  auth:
    persistence:
      elasticsearch:
        enabled: true
        index-prefix: camunda-auth
```

---

## Auto-Configuration Classes

The starter provides fine-grained auto-configuration. Each concern has its own class:

|           Auto-Configuration Class            |                           Activates When                           |
|-----------------------------------------------|--------------------------------------------------------------------|
| `CamundaAuthAutoConfiguration`                | `camunda.auth.method` is set (any value)                           |
| `CamundaAuthSecurityAutoConfiguration`        | `camunda.auth.method=oidc`                                         |
| `CamundaOidcAutoConfiguration`                | `camunda.auth.method=oidc`                                         |
| `CamundaSecurityFilterChainAutoConfiguration` | `camunda.auth.method=oidc`                                         |
| `CamundaWebappSecurityAutoConfiguration`      | `camunda.auth.security.webapp-enabled=true`                        |
| `CamundaWebappFiltersAutoConfiguration`       | `camunda.auth.security.webapp-enabled=true`                        |
| `CamundaBasicAuthAutoConfiguration`           | `camunda.auth.method=basic` + `basic.secondary-storage-available`  |
| `CamundaBasicAuthNoDbAutoConfiguration`       | `camunda.auth.method=basic` + no storage (fail-fast)               |
| `CamundaOboAutoConfiguration`                 | `camunda.auth.obo.enabled=true`                                    |
| `CamundaAuthPersistenceAutoConfiguration`     | `camunda.auth.token-exchange.enabled=true`                         |
| `CamundaUserProviderAutoConfiguration`        | `camunda.auth.method` is set                                       |
| `CamundaWebSessionAutoConfiguration`          | Persistent sessions enabled + `SessionPersistencePort` bean exists |

---

## Basic Auth Setup

Basic auth requires a database (`secondary-storage-available=true`) because user credentials and memberships are stored in DB.

```yaml
camunda:
  auth:
    method: basic
    basic:
      secondary-storage-available: true
    security:
      webapp-enabled: true     # enable form login
```

You must provide a `BasicAuthMembershipResolver` bean and, for the webapp, a `UserProfileProvider` bean. The auto-configuration creates:

- **API filter chain**: HTTP Basic authentication, stateless sessions, `AuthFailureHandler` as entry point
- **Webapp filter chain**: Form login at `/login`, logout at `/logout`, CSRF protection, secure headers
- **Catch-all filter chain**: Denies unmatched paths with 404

Without a database (`secondary-storage-available=false` or missing), the `CamundaBasicAuthNoDbAutoConfiguration` fails fast on startup with `BasicAuthenticationNotSupportedException`.

---

## OIDC Setup

OIDC is the default (`camunda.auth.method=oidc`). The auto-configuration creates:

- **API filter chain**: JWT resource server with `OidcTokenAuthenticationConverter`, proactive token refresh via `OAuth2RefreshTokenFilter`
- **Webapp filter chain** (when `webapp-enabled=true`): OIDC login/logout, session management, CSRF, secure headers
- **Catch-all filter chain**: Denies unmatched paths with 404

OIDC provider configuration uses Spring Security's native properties:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          camunda:
            client-id: my-app
            client-secret: ${CLIENT_SECRET}
            scope: openid, profile
            authorization-grant-type: authorization_code
        provider:
          camunda:
            issuer-uri: https://idp.example.com/realms/my-realm
```

For providers that don't expose full OIDC discovery, you can specify URIs explicitly via `camunda.security.oidc.providers.*` (bridged from the legacy `SecurityConfiguration`).

### Multi-Issuer (Multiple OIDC Providers)

Register multiple providers and the library automatically creates a multi-issuer `JwtDecoder`:

```yaml
spring:
  security:
    oauth2:
      client:
        registration:
          keycloak:
            client-id: my-app
            client-secret: ${KC_SECRET}
            scope: openid, profile
          auth0:
            client-id: my-m2m-app
            client-secret: ${AUTH0_SECRET}
            scope: openid
        provider:
          keycloak:
            issuer-uri: https://keycloak.example.com/realms/my-realm
          auth0:
            issuer-uri: https://my-tenant.auth0.com/
```

The `IssuerAwareJWSKeySelector` routes JWT validation to the correct provider based on the token's `iss` claim. Each provider's JWK endpoint is cached and resolved independently.

---

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

---

## Further Reading

- [ADR-0001: Hexagonal Architecture](docs/adr/0001-hexagonal-architecture.md)
- [ADR-0002: Standalone Library Extraction](docs/adr/0002-standalone-library-extraction.md)
- [ADR-0003: Persistence Abstraction](docs/adr/0003-persistence-abstraction.md)
- [ADR-0004: Spring Boot Auto-Configuration Strategy](docs/adr/0004-spring-boot-auto-configuration.md)
- [ADR-0005: Grant Type Simplification](docs/adr/0005-grant-type-simplification.md)
- [Custom Spring Security Audit](docs/custom-spring-security-audit.md)

