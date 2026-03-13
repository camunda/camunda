# Gatekeeper Integration Guide

This document is for developers who want to use gatekeeper in their Camunda component. It covers
what you need to depend on, what you need to implement, and how the library wires itself into your
application.

## Prerequisites

Read `docs/architecture.md` first to understand the module structure and SPI pattern.

## Step 1: Add the Dependency

**If you are a Spring Boot application** (most Camunda components), add the starter:

```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>gatekeeper-spring-boot-starter</artifactId>
</dependency>
```

This transitively includes `gatekeeper-domain`, so you get both the models/SPIs and the
auto-configuration.

**If you are a non-Spring module** (e.g., Zeebe broker internals, gRPC interceptors), add only the
domain:

```xml
<dependency>
  <groupId>io.camunda</groupId>
  <artifactId>gatekeeper-domain</artifactId>
</dependency>
```

This gives you the model records and SPI interfaces with no framework dependencies.

## Step 2: Configure Properties

Gatekeeper reads from the `camunda.security.*` property namespace. Minimal configuration:

```yaml
# Basic auth (default)
camunda:
  security:
    authentication:
      method: BASIC

# OIDC
camunda:
  security:
    authentication:
      method: OIDC
      oidc:
        issuer-uri: https://your-idp.example.com/realms/camunda
        client-id: camunda
        client-secret: ${OIDC_CLIENT_SECRET}
```

The starter activates the appropriate auto-configuration based on the method.

## Step 3: Implement Required SPIs

Gatekeeper provides defaults for the authentication pipeline (converter, holder, provider), but
your component must implement several SPIs to connect gatekeeper to your data layer. Each
implementation should be a Spring `@Component` (or `@Bean`) so the auto-configuration can discover
it.

### Required SPIs

#### SecurityPathProvider

Defines the URL patterns that gatekeeper uses to configure its security filter chains. Each
consuming application has different paths for its API endpoints, unprotected paths, web application
UI, and web components. Gatekeeper has no built-in defaults — your component must declare all paths.

```java
@Component
public final class MySecurityPathProvider implements SecurityPathProvider {

  @Override
  public Set<String> apiPaths() {
    return Set.of("/api/**", "/v2/**");
  }

  @Override
  public Set<String> unprotectedApiPaths() {
    return Set.of("/v2/license", "/v2/status");
  }

  @Override
  public Set<String> unprotectedPaths() {
    return Set.of("/actuator/**", "/error", "/swagger-ui/**");
  }

  @Override
  public Set<String> webappPaths() {
    return Set.of("/login/**", "/logout", "/my-app/**");
  }

  @Override
  public Set<String> webComponentNames() {
    // Bare path segment identifiers for web component authorization checks.
    // Return empty set if your app has no component-level authorization.
    return Set.of("my-app");
  }
}
```

Path patterns use Spring Security's ant-style syntax (`**` for multi-level, `*` for single-level).
`webComponentNames()` returns bare identifiers (e.g., `"operate"`), not ant-style patterns.

#### MembershipResolver

Resolves groups, roles, tenants, and mapping rules for an authenticated principal. This is the
bridge between "identity provider says this is user X" and "our platform knows user X belongs to
these groups and tenants."

```java
@Component
public final class MyMembershipResolver implements MembershipResolver {

  private final GroupService groupService;
  private final TenantService tenantService;

  // constructor injection...

  @Override
  public CamundaAuthentication resolveMemberships(
      final Map<String, Object> tokenClaims,
      final String principalId,
      final PrincipalType principalType) {

    final var groups = groupService.getGroupIds(principalId);
    final var tenants = tenantService.getTenantIds(principalId);

    return CamundaAuthentication.of(b -> b
        .user(principalId)
        .groupIds(groups)
        .tenants(tenants)
        .claims(tokenClaims));
  }
}
```

#### CamundaUserProvider

Returns the current user's information. Also provides the user's access token for downstream API
calls. How you resolve profile data (display name, email) is up to your implementation — query a
database, read from token claims, or hardcode for testing.

```java
@Component
public final class MyCamundaUserProvider implements CamundaUserProvider {

  private final CamundaAuthenticationProvider authProvider;
  private final UserRepository userRepository;

  // constructor injection...

  @Override
  public CamundaUserInfo getCurrentUser() {
    final var auth = authProvider.getCamundaAuthentication();
    if (auth == null || auth.isAnonymous()) {
      return null;
    }
    final var username = auth.authenticatedUsername();
    final var user = userRepository.findByUsername(username);

    return new CamundaUserInfo(
        user.getDisplayName(),
        username,
        user.getEmail(),
        List.of(), // authorized components — resolve via your own authorization logic
        auth.authenticatedTenantIds(),
        auth.authenticatedGroupIds(),
        auth.authenticatedRoleIds(),
        true);
  }

  @Override
  public String getUserToken() {
    // Return the OAuth2 access token if available, null for basic auth
    return null;
  }
}
```

### Optional SPIs

These SPIs have defaults or are only needed for specific features:

#### SessionPersistencePort

Only implement if you use persistent sessions (i.e., sessions that survive application restarts).
If not implemented, gatekeeper uses in-memory sessions.

```java
@Component
public final class MySessionPersistence implements SessionPersistencePort {

  @Override
  public Optional<SessionData> findById(final String id) { /* ... */ }

  @Override
  public void save(final SessionData sessionData) { /* ... */ }

  @Override
  public void deleteById(final String id) { /* ... */ }

  @Override
  public void deleteExpired() { /* ... */ }
}
```

#### OidcConfigurationProvider

Override if you need dynamic OIDC configuration (e.g., multi-tenant IdP switching at runtime).
The default reads from `camunda.security.authentication.oidc.*` properties.

## Step 4: Override Defaults (Optional)

Because every auto-configured bean uses `@ConditionalOnMissingBean`, you can replace any default
by declaring your own bean of the same type. For example, to replace the authentication provider:

```java
@Bean
public CamundaAuthenticationProvider camundaAuthenticationProvider() {
  return new MyCustomAuthenticationProvider();
}
```

Gatekeeper's default will back off and yours will be used instead.

## Step 5: Use Gatekeeper Types

### Getting the Current Authentication

Inject `CamundaAuthenticationProvider` wherever you need to know who is making the current request:

```java
@RestController
public final class MyController {

  private final CamundaAuthenticationProvider authProvider;

  @GetMapping("/my-endpoint")
  public ResponseEntity<?> handle() {
    final var auth = authProvider.getCamundaAuthentication();
    final var username = auth.authenticatedUsername();
    final var tenants = auth.authenticatedTenantIds();
    // ...
  }
}
```

### Building a SecurityContext for Queries

When executing authorized queries, combine the authentication with the required authorization:

```java
final var securityContext = SecurityContext.of(authProvider.getCamundaAuthentication());
```

### Creating Authentication in Tests

The builder pattern makes test setup straightforward:

```java
// Anonymous user
final var anon = CamundaAuthentication.anonymous();

// User with groups and tenants
final var user = CamundaAuthentication.of(b -> b
    .user("testuser")
    .groupIds(List.of("engineering"))
    .tenants(List.of("tenant-a", "tenant-b")));

// M2M client
final var client = CamundaAuthentication.of(b -> b
    .clientId("my-worker")
    .claims(Map.of("aud", "camunda-api")));
```

## What Gatekeeper Gives You for Free

When you add the starter and implement the required SPIs, you automatically get:

- **Authentication pipeline**: Converts Spring Security's `Authentication` object into
  `CamundaAuthentication`, stores it in request/session context, makes it available via
  `CamundaAuthenticationProvider`
- **OIDC integration**: JWT validation, multi-issuer support, token refresh, client registration,
  logout handling (when `method=OIDC`)
- **Basic auth integration**: Username/password authentication against your user store (when
  `method=BASIC`)
- **Security filter chain**: CSRF protection, security headers, session management, content-aware
  authentication entry points (OIDC webapp chains return 302 redirect for browser requests and 401
  for Bearer token requests)
- **Filter chain extension**: `WebappFilterChainCustomizer` for injecting custom filters
- **Session management**: Optional persistent sessions with JWT tokens

## Checklist

Before shipping your integration:

- [ ] Added `gatekeeper-spring-boot-starter` (or `gatekeeper-domain`) dependency
- [ ] Configured `camunda.security.authentication.method` property
- [ ] Implemented `SecurityPathProvider`
- [ ] Implemented `MembershipResolver`
- [ ] Implemented `CamundaUserProvider`
- [ ] (If using persistent sessions) Implemented `SessionPersistencePort`
- [ ] Verified with `@ConditionalOnMissingBean` back-off — your beans take priority
