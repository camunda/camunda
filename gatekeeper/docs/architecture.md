# Gatekeeper Architecture Guide

This document explains the structure, architecture patterns, and core principles of the gatekeeper
modules. It is intended for developers who want to understand how gatekeeper works before using or
contributing to it.

## Purpose

Gatekeeper is a centralized authentication SDK for Camunda. It provides:

- A canonical set of identity models (records) shared across all Camunda components
- A set of SPIs (Service Provider Interfaces) that components implement to connect gatekeeper to
  their own user stores, session backends, and authorization engines
- A Spring Boot starter that auto-configures OIDC/Basic auth, session management, CSRF protection,
  and security filter chains

## Module Structure

```
gatekeeper/
├── gatekeeper-domain/          # Pure domain layer (zero framework dependencies)
└── gatekeeper-spring-boot-starter/  # Spring Boot auto-configuration
```

### gatekeeper-domain

The domain module is the heart of the library. It contains models, configuration records, SPIs, and
lightweight auth utilities. It has **no dependency on Spring, Jakarta Servlet, or Jackson runtime**
(jackson-annotations are permitted for serialization hints). This constraint is enforced by ArchUnit
tests in `DomainArchTest`.

```
io.camunda.gatekeeper/
├── auth/           # JWT claim extraction utilities (OidcPrincipalLoader, OidcGroupsLoader)
├── config/         # Immutable configuration records
├── model/
│   ├── identity/   # CamundaAuthentication, CamundaUserInfo, AuthenticationMethod, SecurityContext
│   ├── session/    # SessionData, SessionToken, SessionTokenValidation
│   └── cookie/     # CookieDescriptor, SameSitePolicy
└── spi/            # Service Provider Interfaces (11 interfaces)
```

### gatekeeper-spring-boot-starter

The starter module provides Spring Boot auto-configuration that wires gatekeeper's SPIs into a
working security stack. It reads `camunda.security.*` properties, creates the appropriate beans,
and activates features conditionally based on the authentication method and feature flags.

```
io.camunda.gatekeeper.spring/
├── autoconfigure/  # 6 @AutoConfiguration classes
├── condition/      # Custom @Conditional annotations
├── config/         # GatekeeperProperties (@ConfigurationProperties binding)
├── converter/      # Spring Authentication → CamundaAuthentication converters
├── filter/         # Servlet filters, WebappFilterChainCustomizer extension point
├── handler/        # Auth failure/success/logout handlers
├── holder/         # Authentication context holders (request, session)
├── oidc/           # OIDC plumbing (JWT decoders, client registration, multi-issuer)
└── session/        # Session management (repository, mapper, attribute converter)
```

## Core Principles

### 1. Hexagonal Architecture

The domain module defines **ports** (SPIs) that the Spring starter and consuming components
implement as **adapters**. The domain never reaches out to infrastructure — infrastructure reaches
in through the SPIs.

```
                  ┌─────────────────────────┐
                  │    gatekeeper-domain     │
                  │                         │
Consumers ──────▶│  SPIs ◄── Models        │
implement         │  (ports)    (records)    │
these             │                         │
                  └─────────────────────────┘
                            ▲
                            │ implements
                  ┌─────────────────────────┐
                  │  gatekeeper-spring-boot  │
                  │       -starter           │
                  │                         │
                  │  Auto-config, filters,   │
                  │  converters, holders     │
                  └─────────────────────────┘
```

### 2. Models Are Records

All model classes are Java records, enums, or sealed interfaces. Never mutable classes. This is
enforced by ArchUnit. Records provide:

- Immutability (thread-safe by default)
- Structural equality
- Compact constructors for defensive copying (e.g., `List.copyOf()`)

Configuration records (`config/`) follow the same rule. Spring property binding uses the mutable
`GatekeeperProperties` class in the starter, which converts to immutable domain records via
`toXxxConfig()` methods.

### 3. SPIs Are Interfaces

All extension points in `spi/` are plain Java interfaces. They define what the library needs from
its host environment without prescribing how it is provided. The Spring starter supplies default
implementations for some SPIs (authentication provider, converter chain, holders). Others must be
implemented by the consuming component (membership resolution, user profile, resource access).

### 4. Every Bean Backs Off

Every `@Bean` in the auto-configuration classes is annotated with `@ConditionalOnMissingBean`.
This means a consuming component can always override any default by declaring its own bean of the
same type. The library never forces its defaults if you provide your own.

### 5. Domain Has Zero Framework Dependencies

`gatekeeper-domain` must not import Spring, Jakarta, or Jackson runtime classes. This allows the
domain models and SPIs to be used in non-Spring contexts (e.g., the Zeebe broker, gRPC
interceptors, or standalone tests) without pulling in a web framework.

## Key Types

### CamundaAuthentication

The central identity record. Represents the authentication context for a user, client, or anonymous
principal.

```java
CamundaAuthentication auth = CamundaAuthentication.of(b -> b
    .user("jane")
    .groupIds(List.of("engineering", "admins"))
    .roleIds(List.of("operator"))
    .tenants(List.of("tenant-a"))
    .claims(jwtClaims));
```

Either `authenticatedUsername` or `authenticatedClientId` is set (never both). Anonymous
authentication sets neither and flags `anonymousUser = true`.

This record is the canonical type used across all Camunda components for representing "who is
making this request."

### SecurityContext

Wraps `CamundaAuthentication` into a context object that can be passed through the system.
Consumers that need authorization data should compose this with their own authorization context.

### CamundaUserInfo

The user-facing profile record returned by REST endpoints. Contains display name, email, authorized
web components, tenants, groups, roles, and a `canLogout` flag.

## SPI Overview

SPIs are grouped by concern. The table below indicates whether gatekeeper provides a default
implementation or whether the consuming component must supply one.

|                 SPI                 |                      Purpose                      |                Default provided?                |
|-------------------------------------|---------------------------------------------------|-------------------------------------------------|
| `CamundaAuthenticationProvider`     | Get current authentication                        | Yes (delegates to converter + holder chains)    |
| `CamundaAuthenticationConverter<T>` | Convert framework auth to `CamundaAuthentication` | Yes (OIDC and basic auth converters)            |
| `CamundaAuthenticationHolder`       | Store/retrieve auth in request context            | Yes (request-scoped and session-scoped)         |
| `MembershipResolver`                | Resolve groups, roles, tenants from token claims  | **No** — component must implement               |
| `CamundaUserProvider`               | Get current user info and token                   | **No** — component must implement               |
| `SessionPersistencePort`            | Persist web sessions                              | **No** — implement if using persistent sessions |
| `SessionTokenService`               | JWT session token lifecycle                       | Optional                                        |
| `TerminatedSessionPort`             | Store revoked sessions                            | Optional                                        |
| `OidcConfigurationProvider`         | Provide OIDC configs at runtime                   | Yes (from properties; override for dynamic IdP) |
| `SecurityPathProvider`              | Define URL patterns for security filter chains    | **No** — component must implement               |
| `CookiePathResolver`                | Resolve cookie path                               | Optional (uses config default)                  |

## Auto-Configuration Activation

The starter registers 6 auto-configuration classes, activated conditionally:

|                Auto-configuration                |                 Activates when                 |
|--------------------------------------------------|------------------------------------------------|
| `GatekeeperAuthAutoConfiguration`                | Always (core beans)                            |
| `GatekeeperOidcAutoConfiguration`                | `camunda.security.authentication.method=OIDC`  |
| `GatekeeperBasicAuthAutoConfiguration`           | `camunda.security.authentication.method=BASIC` |
| `GatekeeperSecurityFilterChainAutoConfiguration` | Always (security filter chain)                 |
| `GatekeeperSessionAutoConfiguration`             | Persistent sessions enabled                    |
| `GatekeeperWebappFiltersAutoConfiguration`       | Always (webapp-specific beans)                 |

Custom conditional annotations in `condition/`:
- `@ConditionalOnAuthenticationMethod(OIDC)` / `@ConditionalOnAuthenticationMethod(BASIC)`
- `@ConditionalOnProtectedApi` / `@ConditionalOnUnprotectedApi`
- `@ConditionalOnPersistentWebSessionEnabled`

## Dependency Direction

```
gatekeeper-domain  ◄──  gatekeeper-spring-boot-starter
        ▲                         ▲
        │                         │
        └─── consuming components ─┘
```

- The domain module depends on nothing (except jackson-annotations)
- The starter depends on the domain and on Spring Boot
- Consuming components depend on either or both, depending on need
- The domain module is the **canonical source** for shared types
  (`CamundaAuthentication`, `CamundaAuthenticationProvider`, etc.)

## ArchUnit Rules

`DomainArchTest` enforces these rules on `gatekeeper-domain`:

1. Domain must not depend on Spring Framework
2. Domain must not depend on Jakarta Servlet
3. Domain must not depend on Jackson runtime (jackson-annotations are permitted)
4. Model classes must be records, enums, or sealed interfaces
5. SPI classes must be interfaces
6. Models must not depend on SPIs (layering)
7. Exceptions must extend `RuntimeException`

