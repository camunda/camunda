# ADR-0004: Spring Boot Auto-Configuration Strategy

**Date:** 2025-02
**Status:** Accepted

## Context

The auth library targets two types of consumers:

1. **Spring Boot applications** (the primary case) — both the monorepo and standalone products
   like Hub are Spring Boot applications.
2. **Non-Spring JDK clients** — consumers that need auth domain types and SPIs without a Spring
   runtime (e.g., pure Java services using `auth-domain` directly).

The monorepo's existing `authentication/` module uses a single 1,143-line `WebSecurityConfig`
class that manually constructs all SecurityFilterChain beans. This monolith is difficult to
extend, override, or test in isolation.

## Decision

### 1. Modular auto-configuration in `auth-spring-boot-starter`

Each concern gets its own `@AutoConfiguration` class with precise activation conditions:

|           Auto-configuration class            |                           Activates when                           |
|-----------------------------------------------|--------------------------------------------------------------------|
| `CamundaAuthAutoConfiguration`                | `camunda.auth.method` is set                                       |
| `CamundaAuthSecurityAutoConfiguration`        | Same — sets up security context                                    |
| `CamundaOidcAutoConfiguration`                | `camunda.auth.method=oidc`                                         |
| `CamundaBasicAuthAutoConfiguration`           | `camunda.auth.method=basic`                                        |
| `CamundaBasicAuthNoDbAutoConfiguration`       | `camunda.auth.method=basic-no-db`                                  |
| `CamundaSecurityFilterChainAutoConfiguration` | `camunda.auth.method` is set                                       |
| `CamundaWebappSecurityAutoConfiguration`      | `camunda.auth.method` is set                                       |
| `CamundaWebappFiltersAutoConfiguration`       | `camunda.auth.method` is set                                       |
| `CamundaOboAutoConfiguration`                 | `camunda.auth.method` is set                                       |
| `CamundaAuthPersistenceAutoConfiguration`     | `camunda.auth.token-exchange.enabled=true`                         |
| `CamundaUserProviderAutoConfiguration`        | `camunda.auth.method` is set                                       |
| `CamundaWebSessionAutoConfiguration`          | Persistent sessions enabled + `SessionPersistencePort` bean exists |

All are registered in `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.

### 2. Persistence modules provide their own auto-configurations

Each persistence module (`auth-persist-elasticsearch`, `auth-persist-rdbms`) has its own
`@AutoConfiguration` class registered in its own `META-INF/spring/...AutoConfiguration.imports`.

This means:
- Adding `auth-persist-elasticsearch` to the classpath automatically registers its auto-config.
- Adding `auth-persist-rdbms` to the classpath automatically registers its auto-config.
- The starter does not need compile-time dependencies on the persist modules.

Activation is conditional on `camunda.auth.persistence.type=elasticsearch|rdbms`.

### 3. Every bean uses `@ConditionalOnMissingBean`

All beans declared by auto-configuration use `@ConditionalOnMissingBean`, allowing consumers
to override any default. This is critical for the monorepo, which provides its own
implementations for several SPIs (e.g., `MembershipResolver`, `SessionPersistencePort`).

### 4. Custom condition annotations

The starter provides reusable condition annotations in the `condition/` package:

|                          Annotation                           |                  Checks                  |
|---------------------------------------------------------------|------------------------------------------|
| `@ConditionalOnAuthenticationMethod(BASIC\|OIDC)`             | `camunda.auth.method` property           |
| `@ConditionalOnPersistentWebSessionEnabled`                   | Multiple legacy + current property names |
| `@ConditionalOnProtectedApi` / `@ConditionalOnUnprotectedApi` | `camunda.auth.unprotected-api`           |
| `@ConditionalOnInternalUserManagement`                        | `camunda.auth.internal-user-management`  |
| `@ConditionalOnCamundaGroupsEnabled`                          | `camunda.auth.camunda-groups-enabled`    |

### 5. Property bridge for monorepo compatibility

The monorepo's `DefaultAuthenticationInitializer` (an `ApplicationContextInitializer`) bridges
legacy properties and Spring profiles to the auth library's property namespace. This ensures
existing monorepo configurations work without modification.

## Consequences

- **Positive:** Each auto-configuration class is small, focused, and independently testable
  with `ApplicationContextRunner`.
- **Positive:** Consumers activate features by setting properties — no `@Import` or manual
  bean wiring needed.
- **Positive:** `@ConditionalOnMissingBean` makes every default overridable.
- **Positive:** Persist modules are self-contained — adding the JAR + setting a property is
  sufficient.
- **Negative:** 12+ auto-configuration classes require careful ordering (`@AutoConfiguration(after=...)`)
  to avoid initialization races.
- **Negative:** Custom condition annotations duplicate some Spring Boot standard annotations
  — a future simplification could replace some with `@ConditionalOnProperty`.

