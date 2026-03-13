# ADR-0003: Consumer-Defined Security Paths via SecurityPathProvider SPI

**Date:** 2026-03-12
**Status:** Accepted

## Context

Gatekeeper's `GatekeeperSecurityFilterChainAutoConfiguration` had hardcoded security path constants
(`API_PATHS`, `UNPROTECTED_API_PATHS`, `UNPROTECTED_PATHS`, `WEBAPP_PATHS`) specific to the
orchestration cluster. The `WebComponentAuthorizationCheckFilter` also hardcoded component names
(`identity`, `admin`, `operate`, `tasklist`).

This prevented other consuming applications (e.g., a standalone Hub deployment) from defining their
own URL patterns, which is a fundamental requirement for a reusable auth library.

## Decision

Add a `SecurityPathProvider` SPI to `gatekeeper-domain` that consuming applications implement to
declare their HTTP path patterns and web component names. The auto-configuration injects this
provider instead of using static constants.

### Design choices:

- **Consumers define all paths from scratch.** No inheritance or extension of defaults. Each app
  declares its complete set of paths.
- **SPI interface, not configuration properties.** Path configuration is a developer integration
  concern, not an end-user runtime concern.
- **Single interface for all 5 path categories.** The categories (API, unprotected API, unprotected,
  webapp, web components) do not vary independently.
- **No default implementation.** There is no sensible default across different applications. Missing
  implementation produces a clear Spring startup error.

### Interface:

```java
public interface SecurityPathProvider {
  Set<String> apiPaths();
  Set<String> unprotectedApiPaths();
  Set<String> unprotectedPaths();
  Set<String> webappPaths();
  Set<String> webComponentNames();
}
```

### Changes:

- `GatekeeperSecurityFilterChainAutoConfiguration`: removed 4 hardcoded path constants, added
  `SecurityPathProvider` parameter to all 6 filter chain bean methods
- `WebComponentAuthorizationCheckFilter`: made `final`, accepts component names via constructor
  instead of hardcoding them
- Protocol constants (`LOGIN_URL`, `LOGOUT_URL`, `REDIRECT_URI`, `SESSION_COOKIE`,
  `X_CSRF_TOKEN`) remain as framework-level constants since they are not consumer-specific

## Consequences

- Every consuming application must implement `SecurityPathProvider` (unconditionally required)
- Applications like Hub can define entirely different path sets without forking gatekeeper
- The auto-configuration remains the same structure — only the source of path data changed
- `webComponentNames()` returns bare identifiers (e.g., `"hub"`), not ant-style patterns

