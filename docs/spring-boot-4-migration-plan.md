# Spring Boot 4.0 Migration Plan

This document outlines an iterative approach to upgrade the Camunda Orchestration Cluster from
Spring Boot 3.5.x to Spring Boot 4.0.x, including downstream dependencies such as Spring Framework
and Spring Security.

**Note:** This plan excludes modules under `./clients/*` and `./testing/*` as per the migration
scope. However, the `library-parent` pom already references Spring Boot 4.0.0, indicating that those
modules may be migrated separately or have already been adapted.

## Current State

|    Dependency    | Current Version | Target Version |
|------------------|-----------------|----------------|
| Spring Boot      | 3.5.9           | 4.0.x          |
| Spring Framework | 6.2.15          | 7.0.x          |
| Spring Security  | 6.5.7           | 7.0.x          |
| JUnit            | 5.14.2          | 6.x            |

## Prerequisites

Before starting the migration, ensure the following:

1. **Java Version:** Spring Boot 4.0 requires Java 21 at minimum. Current configuration already
   uses `version.java=21`.
2. **Build Tooling:** Ensure Maven and all plugins are compatible with the new versions.
3. **Baseline Tests:** Ensure all existing tests pass on the current Spring Boot 3.5.x version.
4. **Branch Strategy:** Create a dedicated feature branch for the migration work.

---

## Phase 1: Preparation and Dependency Analysis

### 1.1 Audit Third-Party Dependencies

Compare current dependency versions against Spring Boot 4.0 managed versions from
[Spring Boot 4.0 Dependency Versions](https://docs.spring.io/spring-boot/4.0-SNAPSHOT/appendix/dependency-versions/coordinates.html).

**Strategy:** Follow Spring Boot 4.0's recommended versions. If we already use a newer version
than Spring Boot defines, keep our newer version.

#### Core Spring Dependencies (Must Update)

|     Library      | Current Version | Spring Boot 4.0 Version |         Action         |
|------------------|-----------------|-------------------------|------------------------|
| Spring Framework | 6.2.15          | 7.0.x                   | ⬆️ **Update required** |
| Spring Security  | 6.5.7           | 7.0.x                   | ⬆️ **Update required** |
| Spring Boot      | 3.5.9           | 4.0.x                   | ⬆️ **Update required** |

#### Jakarta EE Dependencies (Must Update for Jakarta EE 11)

|      Library       | Current Version | Spring Boot 4.0 Version |                 Action                 |
|--------------------|-----------------|-------------------------|----------------------------------------|
| Tomcat             | 10.1.50         | 11.0.x                  | ⬆️ **Update required** (Jakarta EE 11) |
| Jakarta Activation | 2.1.4           | 2.1.x                   | ✅ Keep current                         |
| Jakarta Annotation | 3.0.0           | 3.0.x                   | ✅ Keep current                         |
| Jakarta JSON API   | 2.1.3           | 2.1.x                   | ✅ Keep current                         |
| Thymeleaf          | 3.1.3.RELEASE   | 3.1.x                   | ✅ Keep current                         |

#### Observability & Metrics

|      Library      | Current Version | Spring Boot 4.0 Version |              Action              |
|-------------------|-----------------|-------------------------|----------------------------------|
| Micrometer        | 1.15.3          | 1.15.x                  | ✅ Keep current (already aligned) |
| Prometheus Client | 0.16.0          | 1.x.x                   | ⬆️ **Update required**           |

#### Testing Libraries

|    Library    | Current Version | Spring Boot 4.0 Version |         Action         |
|---------------|-----------------|-------------------------|------------------------|
| JUnit Jupiter | 5.14.2          | 5.12.x                  | ✅ Keep current (newer) |
| JUnit 4       | 4.13.2          | 4.13.2                  | ✅ Keep current         |
| Mockito       | 5.21.0          | 5.15.x                  | ✅ Keep current (newer) |
| AssertJ       | 3.27.6          | 3.27.x                  | ✅ Keep current         |
| Awaitility    | 4.3.0           | 4.3.x                   | ✅ Keep current         |
| Hamcrest      | 3.0             | 3.0                     | ✅ Keep current         |
| REST Assured  | 5.5.7           | 5.5.x                   | ✅ Keep current         |
| WireMock      | 3.13.2          | 3.10.x                  | ✅ Keep current (newer) |
| ArchUnit      | 1.4.1           | 1.3.x                   | ✅ Keep current (newer) |

#### JSON & Serialization

|  Library   | Current Version | Spring Boot 4.0 Version |         Action         |
|------------|-----------------|-------------------------|------------------------|
| Jackson    | 2.21.0          | 2.18.x                  | ✅ Keep current (newer) |
| Gson       | 2.13.2          | 2.11.x                  | ✅ Keep current (newer) |
| SnakeYAML  | 2.5             | 2.3                     | ✅ Keep current (newer) |
| JSON Path  | 2.10.0          | 2.9.x                   | ✅ Keep current (newer) |
| JSON Smart | 2.6.0           | 2.5.x                   | ✅ Keep current (newer) |

#### Networking & HTTP

|   Library    | Current Version | Spring Boot 4.0 Version |         Action         |
|--------------|-----------------|-------------------------|------------------------|
| Netty        | 4.2.9.Final     | 4.2.x                   | ✅ Keep current         |
| HttpClient 5 | 5.5.1           | 5.4.x                   | ✅ Keep current (newer) |
| HttpCore 5   | 5.3.4           | 5.3.x                   | ✅ Keep current         |
| OkHttp/Okio  | 3.16.4          | 3.9.x                   | ✅ Keep current (newer) |

#### Database & Persistence

|     Library     | Current Version | Spring Boot 4.0 Version |          Action          |
|-----------------|-----------------|-------------------------|--------------------------|
| PostgreSQL JDBC | 42.7.9          | 42.7.x                  | ✅ Keep current           |
| H2 Database     | 2.4.240         | 2.3.x                   | ✅ Keep current (newer)   |
| Liquibase       | 4.33.0          | 4.30.x                  | ✅ Keep current (newer)   |
| HikariCP        | (managed)       | 6.2.x                   | ✅ Managed by Spring Boot |
| MariaDB Client  | 3.4.1           | 3.5.x                   | ⬆️ Consider update       |

#### Logging

| Library | Current Version | Spring Boot 4.0 Version |         Action         |
|---------|-----------------|-------------------------|------------------------|
| SLF4J   | 2.0.17          | 2.0.x                   | ✅ Keep current         |
| Log4j2  | 2.25.3          | 2.24.x                  | ✅ Keep current (newer) |

#### Security & Authentication

|      Library       | Current Version | Spring Boot 4.0 Version |         Action         |
|--------------------|-----------------|-------------------------|------------------------|
| Bouncy Castle      | 1.83            | 1.79                    | ✅ Keep current (newer) |
| Nimbus JOSE JWT    | 10.7            | 10.0.x                  | ✅ Keep current (newer) |
| UnboundID LDAP SDK | 7.0.4           | 7.0.x                   | ✅ Keep current         |

#### Caching & Data Structures

| Library  | Current Version | Spring Boot 4.0 Version |         Action         |
|----------|-----------------|-------------------------|------------------------|
| Caffeine | 3.2.3           | 3.2.x                   | ✅ Keep current         |
| Guava    | 33.5.0-jre      | 33.4.x                  | ✅ Keep current (newer) |

#### Other Libraries

|         Library          | Current Version | Spring Boot 4.0 Version |         Action         |
|--------------------------|-----------------|-------------------------|------------------------|
| Byte Buddy               | 1.18.4          | 1.15.x                  | ✅ Keep current (newer) |
| Kotlin                   | 2.3.0           | 2.1.x                   | ✅ Keep current (newer) |
| Reactor/Reactive Streams | 1.0.4           | 1.0.4                   | ✅ Keep current         |
| Commons Codec            | 1.20.0          | 1.17.x                  | ✅ Keep current (newer) |
| Commons Compress         | 1.27.1          | 1.27.x                  | ✅ Keep current         |
| Commons IO               | 2.21.0          | 2.18.x                  | ✅ Keep current (newer) |
| Commons Lang3            | 3.20.0          | 3.17.x                  | ✅ Keep current (newer) |

#### Libraries Not Managed by Spring Boot (Require Manual Verification)

|       Library        | Current Version |                                                             Notes                                                              |
|----------------------|-----------------|--------------------------------------------------------------------------------------------------------------------------------|
| Springdoc OpenAPI    | 2.8.15          | ⬆️ **Update to 3.0.1** ([Spring Boot 4.0 compatible](https://github.com/springdoc/springdoc-openapi/releases/tag/v3.0.1))      |
| Spring AI            | 1.1.2           | ⬆️ **Update to 2.0.0-M1** ([Spring Boot 4.0 pre-release](https://github.com/spring-projects/spring-ai/releases/tag/v2.0.0-M1)) |
| MyBatis Spring Boot  | 3.0.5           | ⬆️ **Update to 4.0.0** (Spring Boot 4.0 compatible)                                                                            |
| OpenFeign            | 13.6            | ✅ Keep current (no newer release available)                                                                                    |
| Resilience4j         | 2.3.0           | ✅ Keep current (pure Java library, no Spring Boot version coupling)                                                            |
| Keycloak             | 26.5.1          | ✅ Keep current (uses standard Spring Security APIs, no adapter-specific coupling)                                              |
| Auth0 SDK            | 1.45.1          | ✅ Keep current (HTTP client library, no Spring dependency)                                                                     |
| gRPC                 | 1.78.0          | ✅ Keep current (no Spring dependency, protocol-level library)                                                                  |
| Protobuf             | 4.31.1          | ✅ Keep current (no Spring dependency, serialization library)                                                                   |
| Elasticsearch Client | 8.16.6          | ✅ Keep current (standalone HTTP client, no Spring coupling)                                                                    |
| OpenSearch Client    | 2.17.0          | ✅ Keep current (standalone HTTP client, no Spring coupling)                                                                    |

**Dependency Compatibility Analysis:**

1. **Resilience4j 2.3.0**: The core Resilience4j library is Spring-agnostic. The `resilience4j-spring-boot3`
   module provides Spring Boot integration but typically maintains backward compatibility. The current
   version should work with Spring Boot 4.0 as it relies on standard Spring abstractions. Monitor for
   a potential `resilience4j-spring-boot4` module, but no immediate update required.

2. **Keycloak 26.5.1**: Keycloak's Java adapter was deprecated in favor of standard Spring Security
   OAuth2/OIDC support. Since the codebase uses Spring Security's built-in OAuth2 client (based on
   the `WebSecurityConfig.java` patterns), Keycloak version is only relevant for the Keycloak server
   compatibility, not Spring Boot version. No update required.

3. **Auth0 SDK 1.45.1**: The Auth0 Java SDK is a standalone HTTP client library with no Spring
   dependencies. It communicates with Auth0 APIs over HTTP and is not coupled to Spring versions.
   No update required.

4. **gRPC 1.78.0**: gRPC-Java is a standalone RPC framework. While there are Spring Boot starters
   for gRPC, the core library has no Spring dependency. The current version is compatible.
   No update required.

5. **Protobuf 4.31.1**: Protocol Buffers is a pure serialization library with no Spring coupling.
   No update required.

6. **Elasticsearch Client 8.16.6**: The Elasticsearch Java client is a standalone HTTP client
   that uses Apache HttpClient or low-level REST client. It has no Spring Boot version dependency.
   Spring Boot's auto-configuration exclusion (`ElasticsearchClientAutoConfiguration`) means we
   manage the client ourselves. No update required.

7. **OpenSearch Client 2.17.0**: Similar to Elasticsearch, the OpenSearch Java client is a
   standalone HTTP client with no Spring Boot coupling. No update required.

**Action Items:**
- [ ] Update `version.tomcat` to 11.0.x (required for Jakarta EE 11)
- [ ] Update `version.prometheus` to 1.x.x (breaking change from 0.x)
- [ ] Update `version.springdoc` to 3.0.1 (required for Spring Boot 4.0)
- [ ] Update `version.spring-ai` to 2.0.0-M1 (pre-release with Spring Boot 4.0 support, monitor for GA release)
- [ ] Update `version.mybatis-spring-boot-starter` to 4.0.0 (required for Spring Boot 4.0)
- [ ] Run `./mvnw versions:display-dependency-updates` to identify any missed updates

### 1.2 Review Breaking Changes

Review the official migration guide:
https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide

#### Breaking Change Assessment for This Codebase

|             Breaking Change              |   Relevant?    | Impact |                                                                              Details                                                                               |
|------------------------------------------|----------------|--------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Jakarta EE 11                            | ✅ Yes          | Low    | Codebase already uses `jakarta.*` namespace (e.g., `jakarta.servlet.*`). Tomcat 11.x upgrade required.                                                             |
| Configuration Property Binding           | ✅ Yes          | Medium | 20+ `@ConfigurationProperties` classes found. All use JavaBean-style setters (mutable), no `@ConstructorBinding`. Should be compatible.                            |
| Actuator Endpoints                       | ✅ Yes          | Low    | Already uses new `management.endpoint.*.access` pattern from Spring Boot 3.4. Should be forward-compatible.                                                        |
| Security Configuration                   | ✅ Yes          | Medium | Heavy usage of `HttpSecurity`, `authorizeHttpRequests()`, and `requestMatchers()` (modern patterns). No deprecated `authorizeRequests()` or `antMatchers()` found. |
| HTTP Interface Clients                   | ❌ No           | None   | No `@HttpExchange` annotations found. Not using declarative HTTP interfaces.                                                                                       |
| Testing                                  | ✅ Yes          | Medium | Extensive use of `@SpringBootTest` (20+ test classes). Verify compatibility with Spring Boot 4.0.                                                                  |
| `spring.mvc.pathmatch.matching-strategy` | ⚠️ **Removed** | High   | Property value `ant_path_matcher` is **no longer supported** in Spring Boot 4.0. Must migrate to `path_pattern_parser`.                                            |
| RestClient                               | ✅ Yes          | Low    | Uses `RestClient` for OAuth2 token handling in authentication module. Should be compatible.                                                                        |

#### Detailed Analysis

**1. Jakarta EE 11 (Low Impact)**

The codebase is already migrated to Jakarta namespace:
- `jakarta.servlet.http.HttpServletRequest`
- `jakarta.servlet.http.HttpServletResponse`
- `jakarta.servlet.FilterChain`

Files affected:
- `authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java`
- `authentication/src/main/java/io/camunda/authentication/filters/OAuth2RefreshTokenFilter.java`
- Multiple handler and service classes

**Action:** Update Tomcat from 10.1.x to 11.0.x. No code changes expected.

**2. Configuration Property Binding (Medium Impact)**

Found 20+ `@ConfigurationProperties` classes using JavaBean-style binding:
- `io.camunda.configuration.Camunda` - Uses setters and `@NestedConfigurationProperty`
- `io.camunda.zeebe.util.health.MemoryHealthIndicatorProperties` - Uses setters
- Various other configuration classes in `dist`, `authentication`, `zeebe` modules

Pattern used: Mutable JavaBeans with getters/setters (compatible with Spring Boot 4.0)
Pattern NOT used: `@ConstructorBinding` (would require changes)

**Action:** Test property binding during migration. No immediate code changes expected.

**3. Actuator Endpoints (Low Impact)**

Current configuration in `application.properties`:

```properties
management.endpoints.access.default=none
management.endpoint.health.access=unrestricted
management.endpoint.prometheus.access=unrestricted
# ... and 10+ custom endpoints
```

This uses the new access control model introduced in Spring Boot 3.4, which is the
forward-compatible approach for Spring Boot 4.0.

**Action:** Verify syntax is maintained. No changes expected.

**4. Security Configuration (Medium Impact)**

The codebase uses modern Spring Security patterns:
- ✅ `authorizeHttpRequests()` (not deprecated `authorizeRequests()`)
- ✅ `requestMatchers()` (not deprecated `antMatchers()`)
- ✅ Lambda DSL style configuration
- ✅ `SecurityFilterChain` beans (not deprecated `WebSecurityConfigurerAdapter`)

Files with security configuration:
- `authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java` (1058 lines)
- `optimize/backend/src/main/java/io/camunda/optimize/rest/security/*.java`

**Action:** Review Spring Security 7.0 migration guide for any API changes to `HttpSecurity`
configurers. Monitor for changes in OAuth2/OIDC client configuration.

**5. HTTP Interface Clients (Not Applicable)**

No `@HttpExchange` annotations found in the codebase. The project uses:
- Feign clients (OpenFeign)
- Direct HTTP clients (Elasticsearch, OpenSearch)
- `RestClient` for OAuth2 token exchange

**Action:** None required.

**6. Testing (Medium Impact)**

Extensive use of Spring Boot test annotations:
- 20+ classes using `@SpringBootTest`
- Uses `@ExtendWith(SpringExtension.class)` in some tests
- Uses `TestRestTemplate` for integration tests

**Action:** Verify test annotations are compatible. Watch for:
- Changes to `@SpringBootTest` behavior
- Changes to test slice annotations (`@WebMvcTest`, etc.)
- MockMvc/TestRestTemplate API changes

**7. `spring.mvc.pathmatch.matching-strategy` (High Impact - Removed)**

The property `spring.mvc.pathmatch.matching-strategy=ant_path_matcher` is set in `application.properties`
to support legacy Ant-style path patterns in request mappings.

**Spring Boot 4.0 / Spring Framework 7.0 Change:**
- The `AntPathMatcher` has been **removed** in Spring Framework 7.0
- Only `PathPatternParser` is supported (the default since Spring Framework 6.0)
- The property value `ant_path_matcher` is no longer valid

**Migration Required:**
1. Remove the property `spring.mvc.pathmatch.matching-strategy=ant_path_matcher` from `application.properties`
2. Audit all `@RequestMapping`, `@GetMapping`, `@PostMapping`, etc. annotations for Ant-style patterns
3. Convert any incompatible patterns to PathPattern syntax

**Key Differences:**
| Ant-style Pattern | PathPattern Equivalent | Notes |
|-------------------|------------------------|-------|
| `/**` | `/**` | ✅ Same |
| `/api/*` | `/api/*` | ✅ Same |
| `/api/**` | `/api/**` | ✅ Same |
| `/{id}` | `/{id}` | ✅ Same |
| `/{id:[0-9]+}` | `/{id:[0-9]+}` | ✅ Regex supported |
| `/api/{*path}` | `/api/{*path}` | ✅ Capture rest of path |

Most common patterns are compatible. The main differences are:
- PathPattern is more strict about pattern syntax
- Some complex nested patterns may need adjustment
- Suffix pattern matching (e.g., `*.html`) works differently

**⚠️ `AntPathRequestMatcher` Usage Found:**

The `optimize` module uses `AntPathRequestMatcher` from Spring Security in security configuration:
- `optimize/backend/src/main/java/io/camunda/optimize/rest/security/cloud/CCSaaSSecurityConfigurerAdapter.java`
- `optimize/backend/src/main/java/io/camunda/optimize/rest/security/ccsm/CCSMSecurityConfigurerAdapter.java`

**Note:** `AntPathRequestMatcher` in Spring Security is **NOT deprecated** and remains supported in
Spring Security 7.0. It is separate from Spring MVC's `AntPathMatcher`. No changes required for
Spring Security's `AntPathRequestMatcher`.

**Action Items:**
- [ ] Remove `spring.mvc.pathmatch.matching-strategy=ant_path_matcher` from `application.properties`
- [ ] Audit all request mapping annotations in the codebase
- [ ] Test all REST endpoints after migration
- [ ] Verify `AntPathRequestMatcher` usage in Spring Security configs (should work as-is)

---

## Phase 2: Version Upgrade (Iterative)

Based on the Phase 1 findings, execute the following steps in order. Each step should be
completed and validated before proceeding to the next.

### Step 2.1: Remove Deprecated Path Matching Strategy (Pre-requisite) ✅ COMPLETED

**Why first:** This property will cause startup failure with Spring Boot 4.0. Remove it before
upgrading to avoid blocking issues.

**File:** `dist/src/main/resources/application.properties`

```diff
- # enable ant_path_matcher to support legacy regex in request mappings
- spring.mvc.pathmatch.matching-strategy=ant_path_matcher
```

**Status:** ✅ Completed on 2026-01-22

**Validation:**
- [x] Remove the property
- [x] Run application with Spring Boot 3.5.x to verify no regression
- [x] Run REST API integration tests to confirm all endpoints still work
- [x] Commit this change separately (can be merged to main before Spring Boot 4.0 upgrade)

### Step 2.2: Update Parent POM - Core Spring Versions

**File:** `parent/pom.xml`

Update the core Spring ecosystem versions:

```xml
<!-- Core Spring Updates -->
<version.spring>7.0.3</version.spring>
<version.spring-security>7.0.2</version.spring-security>
<version.spring-boot>4.0.2</version.spring-boot>

<!-- Jakarta EE 11 - Required for Spring Boot 4.0 -->
<version.tomcat>11.0.15</version.tomcat>
```

**Validation:**
- [ ] Run `./mvnw clean compile -T1C -Dquickly` - expect compilation errors
- [ ] Document all compilation errors for next steps

### Step 2.2.1: Migrate from Spring Retry to Spring Framework 7 Native Resilience ✅ COMPLETED

**Issue:** Spring Retry (`org.springframework.retry:spring-retry`) is no longer supported with
Spring Framework 7.0. The dependency version is not managed by Spring Boot 4.0, causing:

```
[ERROR] 'dependencies.dependency.version' for org.springframework.retry:spring-retry:jar is missing
```

**Solution:** Migrate to Spring Framework 7's native resilience support:
- [Spring Framework Resilience Documentation](https://docs.spring.io/spring-framework/reference/core/resilience.html)
- [`@Retryable` Javadoc](https://docs.spring.io/spring-framework/docs/7.0.3/javadoc-api/org/springframework/resilience/annotation/Retryable.html)

**Migration Pattern:**

```java
// BEFORE: Spring Retry (org.springframework.retry.annotation)
@Retryable(
    retryFor = TasklistRuntimeException.class,
    maxAttempts = 5,
    backoff = @Backoff(delay = 3000))
public void someMethod() { ... }

// AFTER: Spring Framework 7 Native (org.springframework.resilience.annotation)
@Retryable(maxAttempts = 5, delay = 3000, includes = TasklistRuntimeException.class)
public void someMethod() { ... }
```

**Configuration Required:**

Spring Framework 7's `@Retryable` requires `@EnableResilientMethods` on a `@Configuration` class.
Add it to existing configuration classes where `@EnableRetry` was previously used:

```java
// BEFORE: Spring Retry
@Configuration
@EnableRetry
public class SomeConfiguration { ... }

// AFTER: Spring Framework 7 Native
@Configuration
@EnableResilientMethods
public class SomeConfiguration { ... }
```

See [Spring Framework Resilience Configuration](https://docs.spring.io/spring-framework/reference/core/resilience.html#resilience-annotations-configuration)

**Key Differences:**
| Spring Retry | Spring Framework 7 |
|--------------|-------------------|
| `retryFor = ...` | `includes = ...` |
| `@Backoff(delay = 3000)` | `delay = 3000` |
| `@EnableRetry` required | Not required |
| `org.springframework.retry.annotation` | `org.springframework.resilience.annotation` |

**Files Updated:**

| File | Changes |
|------|---------|
| `tasklist/qa/util/pom.xml` | Removed `spring-retry` dependency |
| `tasklist/qa/backup-restore-tests/pom.xml` | Removed `spring-retry` dependency |
| `tasklist/qa/util/.../TestContainerUtil.java` | Replaced `@EnableRetry` → `@EnableResilientMethods`, updated `@Retryable` syntax |
| `tasklist/qa/backup-restore-tests/.../TasklistAPICaller.java` | Replaced `@EnableRetry` → `@EnableResilientMethods`, updated `@Retryable` syntax |
| `tasklist/qa/backup-restore-tests/.../BackupRestoreDataGenerator.java` | Updated imports and `@Retryable` syntax |

**Status:** ✅ Completed on 2026-01-23

### Step 2.2.2: Migrate Actuator Health Classes

**Issue:** In Spring Boot 4.0, the actuator health classes have been restructured. The package
`org.springframework.boot.actuate.health` has been reorganized and some classes have moved.

**Compilation Errors:**
```
package org.springframework.boot.actuate.health does not exist
package org.springframework.boot.actuate.health.Health does not exist
package org.springframework.boot.actuate.autoconfigure.health does not exist
```

**Affected Files in This Codebase:**

| File | Usage |
|------|-------|
| `zeebe/util/.../DelayedHealthIndicator.java` | `Health`, `Health.Builder`, `HealthIndicator`, `Status` |
| `zeebe/util/.../MemoryHealthIndicator.java` | `Health`, `HealthIndicator` |
| `zeebe/util/.../MemoryHealthIndicatorAutoConfiguration.java` | `ConditionalOnEnabledHealthIndicator` |
| `zeebe/util/.../LivenessMemoryHealthIndicatorAutoConfiguration.java` | `ConditionalOnEnabledHealthIndicator` |
| `zeebe/gateway-grpc/.../ClusterHealthIndicator.java` | `Health`, `HealthIndicator` |
| `zeebe/gateway-grpc/.../PartitionLeaderAwarenessHealthIndicator.java` | `Health`, `HealthIndicator` |
| `zeebe/gateway-grpc/.../StartedHealthIndicator.java` | `Health`, `HealthIndicator` |
| `zeebe/gateway-grpc/.../ClusterAwarenessHealthIndicator.java` | `Health`, `HealthIndicator` |
| `tasklist/els-schema/.../SearchEngineHealthIndicator.java` | `Health`, `HealthIndicator` |
| `operate/schema/.../IndicesHealthIndicator.java` | `Health`, `HealthIndicator` |
| `dist/.../BrokerStartupHealthIndicator.java` | `Health`, `HealthIndicator` |
| `dist/.../BrokerStatusHealthIndicator.java` | `Health`, `HealthIndicator` |

**Spring Boot 4.0 Health API Changes:**

In Spring Boot 4.0, the health autoconfigure classes have moved to a new package and require
a new dependency:

1. **New Dependency Required:**

   Add `spring-boot-health` dependency to modules using health autoconfigure classes:

   ```xml
   <dependency>
     <groupId>org.springframework.boot</groupId>
     <artifactId>spring-boot-health</artifactId>
   </dependency>
   ```

2. **Package Change for autoconfigure classes:**

   ```java
   // BEFORE: Spring Boot 3.x
   import org.springframework.boot.actuate.autoconfigure.health.ConditionalOnEnabledHealthIndicator;
   import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;

   // AFTER: Spring Boot 4.0
   import org.springframework.boot.health.autoconfigure.contributor.ConditionalOnEnabledHealthIndicator;
   import org.springframework.boot.health.autoconfigure.contributor.HealthContributorAutoConfiguration;
   ```

3. **Package Change for core Health classes:**

   ```java
   // BEFORE: Spring Boot 3.x
   import org.springframework.boot.actuate.health.Health;
   import org.springframework.boot.actuate.health.HealthIndicator;
   import org.springframework.boot.actuate.health.Status;

   // AFTER: Spring Boot 4.0
   import org.springframework.boot.health.contributor.Health;
   import org.springframework.boot.health.contributor.HealthIndicator;
   import org.springframework.boot.health.contributor.Status;
   ```

**Files Updated:**

1. `zeebe/util/pom.xml` - Added `spring-boot-health` dependency
2. `zeebe/util/.../MemoryHealthIndicatorAutoConfiguration.java` - Updated autoconfigure imports
3. `zeebe/util/.../LivenessMemoryHealthIndicatorAutoConfiguration.java` - Updated autoconfigure imports
4. `zeebe/util/.../DelayedHealthIndicator.java` - Updated health imports
5. `zeebe/util/.../MemoryHealthIndicator.java` - Updated health imports
6. `zeebe/util/.../DelayedHealthIndicatorTest.java` - Updated health imports
7. `zeebe/util/.../MemoryHealthIndicatorTest.java` - Updated health imports

**Files Requiring Updates (other modules):**

| Module | Files |
|--------|-------|
| `zeebe/gateway-grpc` | `ClusterHealthIndicator.java`, `StartedHealthIndicator.java`, `PartitionLeaderAwarenessHealthIndicator.java`, `ClusterAwarenessHealthIndicator.java`, test files |
| `tasklist/els-schema` | `SearchEngineHealthIndicator.java` |
| `operate/schema` | `IndicesHealthIndicator.java` |
| `dist` | `BrokerStartupHealthIndicator.java`, `BrokerStatusHealthIndicator.java`, `NodeIdProviderHealthIndicator.java` |
| Test files | Various integration tests using Health classes |

**Action Items:**
- [x] Add `spring-boot-health` dependency to `zeebe/util/pom.xml`
- [x] Update `zeebe/util` health classes with new imports
- [x] Add `spring-boot-health` dependency to other modules as needed
- [x] Update `zeebe/gateway-grpc` health classes
- [x] Update `tasklist/els-schema` health classes
- [x] Update `operate/schema` health classes
- [x] Update `dist` health classes
- [x] Update test files
- [ ] Verify health indicator beans are still registered correctly
- [ ] Test health endpoints after migration

### Step 2.2.3: Spring Boot 4.0 Module Splits - Additional Dependencies

**Issue:** Spring Boot 4.0 has split several classes into dedicated modules. The following new
dependencies may need to be added to modules that use these classes:

| New Dependency | Classes Moved | Old Package | New Package |
|----------------|---------------|-------------|-------------|
| `spring-boot-health` | `Health`, `HealthIndicator`, `Status`, `ConditionalOnEnabledHealthIndicator`, `ReadinessStateHealthIndicator` | `org.springframework.boot.actuate.health`, `org.springframework.boot.actuate.availability` | `org.springframework.boot.health.contributor`, `org.springframework.boot.health.application` |
| `spring-boot-webmvc` | `ErrorAttributes`, `ErrorController` | `org.springframework.boot.web.servlet.error` | `org.springframework.boot.webmvc.error` |
| `spring-boot-webmvc-test` | `AutoConfigureMockMvc`, `AutoConfigureWebMvc`, `WebMvcTest` | `org.springframework.boot.test.autoconfigure.web.servlet` | `org.springframework.boot.webmvc.test.autoconfigure` |
| `spring-boot-tomcat` | `TomcatContextCustomizer` | `org.springframework.boot.web.embedded.tomcat` | Same package, new module |
| `spring-boot-security` | `EndpointRequest`, `SecurityAutoConfiguration` | `org.springframework.boot.actuate.autoconfigure.security.servlet` | `org.springframework.boot.security.autoconfigure.actuate.web.servlet` |
| `spring-boot-jdbc` | `DataSourceAutoConfiguration`, `DataSourceHealthIndicator` | `org.springframework.boot.autoconfigure.jdbc`, `org.springframework.boot.actuate.jdbc` | `org.springframework.boot.jdbc.autoconfigure`, `org.springframework.boot.jdbc.health` |
| `spring-boot-data-jpa` | `HibernateJpaAutoConfiguration` | `org.springframework.boot.autoconfigure.orm.jpa` | `org.springframework.boot.hibernate.autoconfigure` |
| `spring-boot-micrometer-metrics` | `ConditionalOnEnabledMetricsExport` | `org.springframework.boot.actuate.autoconfigure.metrics.export` | `org.springframework.boot.micrometer.metrics.autoconfigure.export` |
| `spring-boot-jackson2` | `Jackson2ObjectMapperBuilderCustomizer` | `org.springframework.boot.autoconfigure.jackson` | `org.springframework.boot.jackson2.autoconfigure` |
| (core spring-boot) | `DefaultPropertiesPropertySource` | `org.springframework.boot` | `org.springframework.boot.env` |
| `spring-boot-starter-freemarker` | `FreeMarkerAutoConfiguration` | `org.springframework.boot.autoconfigure.freemarker` | `org.springframework.boot.freemarker.autoconfigure` |
| `spring-boot-tomcat` | `TomcatServletWebServerFactory` | `org.springframework.boot.web.embedded.tomcat` | `org.springframework.boot.tomcat.servlet` |
| (Spring Security 7) | `AntPathRequestMatcher` | `org.springframework.security.web.util.matcher` | Removed - use `PathPatternRequestMatcher.withDefaults().matcher()` from `org.springframework.security.web.servlet.util.matcher` |
| `spring-boot-resttestclient` | `TestRestTemplate`, `AutoConfigureTestRestTemplate` | `org.springframework.boot.test.web.client` | `org.springframework.boot.resttestclient`, `org.springframework.boot.resttestclient.autoconfigure` (add `@AutoConfigureTestRestTemplate` annotation to tests using `@Autowired TestRestTemplate`) |
| `spring-boot-restclient` | `RestTemplateBuilder` | `org.springframework.boot.web.client` | `org.springframework.boot.restclient` |
| `spring-boot-http-client` | `ClientHttpRequestFactorySettings`, `HttpRedirects` | `org.springframework.boot.http.client.ClientHttpRequestFactorySettings` | `org.springframework.boot.http.client` (use `HttpRedirects.DONT_FOLLOW` instead of `ClientHttpRequestFactorySettings.Redirects.DONT_FOLLOW`) |
| `spring-boot-micrometer-metrics-test` | `AutoConfigureObservability` → `AutoConfigureMetrics` | `org.springframework.boot.test.autoconfigure.actuate.observability` | `org.springframework.boot.micrometer.metrics.test.autoconfigure` (remove `tracing` parameter) |
| `spring-boot-data-jdbc-test` | `DataJdbcTest` | `org.springframework.boot.test.autoconfigure.data.jdbc` | `org.springframework.boot.data.jdbc.test.autoconfigure` |
| (Spring Framework 7) | `ResponseEntity.getStatusCodeValue()` | N/A | Method removed - use `getStatusCode().value()` instead |

**Example Migration - Error Handling Classes:**

```java
// BEFORE: Spring Boot 3.x
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.boot.web.servlet.error.ErrorController;

// AFTER: Spring Boot 4.0
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.error.ErrorAttributeOptions.Include;
import org.springframework.boot.webmvc.error.ErrorAttributes;
import org.springframework.boot.webmvc.error.ErrorController;
```

**Note:** `ErrorAttributeOptions` stays in `org.springframework.boot.web.error`, while `ErrorAttributes`
and `ErrorController` moved to `org.springframework.boot.webmvc.error`.

**Files Updated:**

| File | Dependency Added | Import Changes |
|------|------------------|----------------|
| `zeebe/gateway-rest/pom.xml` | `spring-boot-webmvc`, `spring-boot-webmvc-test`, `spring-boot-jdbc`, `spring-boot-data-jpa` | Multiple package changes |
| `zeebe/gateway-rest/src/test/**` | - | `@WebMvcTest` → `org.springframework.boot.webmvc.test.autoconfigure` |
| `zeebe/gateway-rest/.../TestApplication.java` | - | `DataSourceAutoConfiguration`, `HibernateJpaAutoConfiguration`, `SecurityAutoConfiguration` → new packages |
| `operate/webapp/pom.xml` | `spring-boot-tomcat` | None (same package, new module) |
| `authentication/pom.xml` | `spring-boot-security`, `spring-boot-webmvc-test` | `EndpointRequest` → new package, test annotations → new package |
| `authentication/src/test/**` | - | `AutoConfigureMockMvc`, `AutoConfigureWebMvc` → `org.springframework.boot.webmvc.test.autoconfigure` |

### Step 2.3: Update Parent POM - Observability Dependencies

**File:** `parent/pom.xml`

```xml
<!-- Prometheus Client - Breaking change from 0.x to 1.x -->
<version.prometheus>1.3.5</version.prometheus>
```

**Note:** Prometheus client 1.x has breaking API changes. Review usages in:
- Micrometer Prometheus registry configuration
- Any direct Prometheus client usage

**Validation:**
- [ ] Search for `io.prometheus` imports and update API usage if needed
- [ ] Verify metrics endpoints still work after migration

### Step 2.4: Update Parent POM - Spring Ecosystem Libraries

**File:** `parent/pom.xml`

```xml
<!-- Springdoc OpenAPI - Required for Spring Boot 4.0 -->
<version.springdoc>3.0.1</version.springdoc>

<!-- Spring AI - Pre-release with Spring Boot 4.0 support -->
<version.spring-ai>2.0.0-M1</version.spring-ai>

<!-- MyBatis Spring Boot - Required for Spring Boot 4.0 -->
<version.mybatis-spring-boot-starter>4.0.0</version.mybatis-spring-boot-starter>
```

**Springdoc 3.0 Breaking Changes:**
- Package renamed from `org.springdoc` to `io.swagger.v3`
- Some annotation changes may be required
- Review OpenAPI configuration classes

**Validation:**
- [ ] Update any Springdoc-specific imports if needed
- [ ] Verify OpenAPI/Swagger UI still works
- [ ] Check generated API documentation

### Step 2.5: Verify Auto-Configuration Exclusions

**File:** `dist/src/main/resources/application.properties`

Verify that excluded auto-configuration classes still exist in Spring Boot 4.0:

```properties
spring.autoconfigure.exclude=\
  org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration, \
  org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration, \
  org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchRestClientAutoConfiguration, \
  org.springframework.boot.autoconfigure.elasticsearch.ElasticsearchClientAutoConfiguration, \
  org.springframework.boot.autoconfigure.ldap.LdapAutoConfiguration, \
  org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration, \
  org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration, \
  org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration, \
  org.springframework.boot.autoconfigure.quartz.QuartzAutoConfiguration
```

**Validation:**
- [ ] Check if any class names have changed in Spring Boot 4.0
- [ ] Update class names if renamed
- [ ] Remove any classes that no longer exist

### Step 2.6: Update JUnit Version (Optional - Recommended)

**File:** `parent/pom.xml`

The `library-parent` already uses JUnit 6.0.1. Consider aligning:

```xml
<!-- JUnit 6 - Recommended for Spring Boot 4.0 -->
<version.junit>6.0.1</version.junit>
```

**Note:** JUnit 6 is backward compatible with JUnit 5 tests. This can be done as a
separate iteration if it causes too many test changes.

**Validation:**
- [ ] Run unit tests: `./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C`
- [ ] Fix any JUnit API compatibility issues

### Step 2.7: Complete Version Update Summary

After completing Steps 2.1-2.6, `parent/pom.xml` should have these updated properties:

```xml
<!-- BEFORE -->
<version.spring>6.2.15</version.spring>
<version.spring-security>6.5.7</version.spring-security>
<version.spring-boot>3.5.9</version.spring-boot>
<version.spring-ai>1.1.2</version.spring-ai>
<version.tomcat>10.1.50</version.tomcat>
<version.prometheus>0.16.0</version.prometheus>
<version.springdoc>2.8.15</version.springdoc>
<version.mybatis-spring-boot-starter>3.0.5</version.mybatis-spring-boot-starter>

<!-- AFTER -->
<version.spring>7.0.1</version.spring>
<version.spring-security>7.0.1</version.spring-security>
<version.spring-boot>4.0.0</version.spring-boot>
<version.spring-ai>2.0.0-M1</version.spring-ai>
<version.tomcat>11.0.2</version.tomcat>
<version.prometheus>1.3.5</version.prometheus>
<version.springdoc>3.0.1</version.springdoc>
<version.mybatis-spring-boot-starter>4.0.0</version.mybatis-spring-boot-starter>
```

### Step 2.8: Initial Compilation Validation

Run compilation to identify all breaking changes:

```bash
./mvnw clean compile -T1C -Dquickly 2>&1 | tee compile-errors.log
```

**Expected Issues:**
1. Springdoc API changes (package renames)
2. Prometheus client API changes
3. Possible Spring Security API changes
4. Possible Spring Framework API changes

**Create tracking issues for each category of compilation error.**

---

## Phase 3: Module-by-Module Migration

Migrate modules in dependency order, starting with foundational modules and working up to
application modules.

### 3.1 Core Utility Modules (Low Risk)

Start with modules that have minimal Spring dependencies:

1. **zeebe/util** - Contains health indicators with `@ConfigurationProperties`
2. **configuration** - Core configuration beans

**Tasks:**
- [ ] Compile and verify no breaking API changes
- [ ] Run unit tests
- [ ] Fix any `@ConfigurationProperties` binding issues

### 3.2 Security and Authentication Module (Medium Risk)

The `authentication` module is critical and heavily uses Spring Security:

**Key Files:**
- `authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java`
- Various handlers and converters using `jakarta.servlet.*`

**Migration Tasks:**
- [ ] Review Spring Security 7.0 migration guide
- [ ] Update `HttpSecurity` configuration patterns if changed
- [ ] Verify CSRF, OAuth2, and OIDC configurations
- [ ] Update cookie/session handling if APIs changed
- [ ] Run all authentication integration tests

### 3.3 Gateway and REST Modules (Medium Risk)

**Modules:**
- `zeebe/gateway-rest`
- `zeebe/gateway-grpc`
- `gateways/gateway-mcp`

**Tasks:**
- [ ] Verify `WebMvcConfigurer` implementations (`SecondaryStorageConfig.java`)
- [ ] Check OpenAPI/Springdoc compatibility
- [ ] Verify actuator endpoint configurations

### 3.4 Application Modules (High Risk)

These are the main application entry points:

**Modules:**
- `dist` - Main distribution with `@SpringBootApplication` classes
- `operate` - Operate application
- `tasklist` - Tasklist application
- `optimize` - Optimize application

**Tasks for `dist`:**
- [ ] Update `application.properties` for any renamed properties
- [ ] Verify `spring.autoconfigure.exclude` classes still exist
- [ ] Update actuator endpoint access configuration (already using `management.endpoint.*.access`)
- [ ] Check `StandaloneOperate.java`, `StandaloneTasklist.java` for compatibility

**Tasks for `operate/tasklist/optimize`:**
- [ ] Migrate `spring.factories` to `AutoConfiguration.imports` format (optimize module)
- [ ] Update `@SpringBootApplication` configurations
- [ ] Verify all `@Configuration` classes

---

## Phase 4: Property and Configuration Migration

> **Note:** The path matching strategy removal and auto-configuration exclusion verification
> are addressed in Phase 2 (Steps 2.1 and 2.5). This phase covers remaining configuration tasks.

### 4.1 Actuator Endpoint Configuration

Current configuration uses the new `management.endpoint.*.access` pattern introduced in Spring Boot
3.4, which should be compatible with 4.0.

**Verify:**
- [ ] `management.endpoints.access.default=none` still works
- [ ] `management.endpoint.*.access=unrestricted` syntax is maintained
- [ ] Custom actuator endpoints (`backupHistory`, `cluster`, `partitions`, etc.) are accessible

### 4.2 Spring Security OAuth2/OIDC Configuration

Review OAuth2 and OIDC configuration for Spring Security 7.0 compatibility:

**Files to review:**
- `authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java`
- `authentication/src/main/java/io/camunda/authentication/config/OidcTokenEndpointCustomizer.java`

**Check for:**
- [ ] `RestClientAuthorizationCodeTokenResponseClient` API changes
- [ ] `RestClientRefreshTokenTokenResponseClient` API changes
- [ ] `OAuth2AuthorizedClientManager` configuration changes
- [ ] `JwtDecoder` and `JwtDecoderFactory` API compatibility

### 4.3 Logging and Observability Configuration

Verify logging configuration compatibility:

- [ ] `logging.register-shutdown-hook=true` still works
- [ ] Micrometer observation configuration is compatible
- [ ] OpenTelemetry integration (if enabled) works with new versions

---

## Phase 5: Spring Factories Migration

### 5.1 Migrate `spring.factories` to `AutoConfiguration.imports`

The `spring.factories` file in `optimize/backend` needs migration:

**Current:** `optimize/backend/src/main/resources/META-INF/spring.factories`

```properties
org.springframework.boot.env.EnvironmentPostProcessor=\
io.camunda.optimize.SpringPropertiesPostProcessor
```

**Migration:**
- For `EnvironmentPostProcessor`, keep in `spring.factories` (still supported)
- For any `EnableAutoConfiguration` entries, migrate to
`META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

---

## Phase 6: Test Migration

### 6.1 Update Test Configurations

**Review test classes using:**
- `@ExtendWith(SpringExtension.class)` - verify compatibility
- `@SpringBootTest` - check for any API changes
- `TestRestTemplate` - verify still available

**Files to review:**
- All `*IT.java` files in `operate/qa/integration-tests`
- All `*IT.java` files in `tasklist/qa/integration-tests`
- All `*IT.java` files in `qa/acceptance-tests`

### 6.2 JUnit 6 Consideration

Spring Boot 4.0 may recommend or require JUnit 6. The `library-parent` already sets
`version.junit=6.0.1`.

**Decision Point:**
- Option A: Upgrade to JUnit 6 alongside Spring Boot 4.0
- Option B: Keep JUnit 5 if still supported and migrate JUnit later

---

## Phase 7: Build and Validation

### 7.1 Compilation Check

```bash
./mvnw clean compile -T1C -Dquickly
```

### 7.2 Run Static Checks

```bash
./mvnw verify -Dquickly -DskipChecks=false -P'!autoFormat,checkFormat,spotbugs' -T1C
```

### 7.3 Run Unit Tests

```bash
./mvnw verify -Dquickly -DskipTests=false -DskipITs -T1C
```

### 7.4 Run Integration Tests

```bash
./mvnw verify -Dquickly -DskipTests=false -DskipUTs -T1C
```

---

## Phase 8: Documentation and Cleanup

### 8.1 Update Documentation

- [ ] Update any Spring Boot version references in documentation
- [ ] Update `README.md` files if they reference Spring Boot version
- [ ] Update any configuration examples

### 8.2 Clean Up Deprecated Code

- [ ] Remove any deprecated API usage flagged during migration
- [ ] Remove compatibility shims if no longer needed

---

## Iteration Strategy

Given the complexity and size of the codebase, use the following iterative approach:

### Iteration 0: Pre-Migration Preparation (1-2 days)

1. Execute Step 2.1: Remove `spring.mvc.pathmatch.matching-strategy=ant_path_matcher`
2. Validate all REST endpoints work with `path_pattern_parser` (the default)
3. Merge this change to main branch (safe to do before Spring Boot 4.0 upgrade)

### Iteration 1: Version Updates & Compilation (1-2 weeks)

1. Execute Steps 2.2-2.4: Update all version properties in `parent/pom.xml`
2. Execute Step 2.5: Verify auto-configuration exclusions
3. Run compilation (Step 2.8) and document all errors
4. Fix compilation errors in core utility modules first (`zeebe/util`, `configuration`)
5. Run unit tests for fixed modules only

### Iteration 2: Security & Authentication (1 week)

1. Focus on `authentication` module compilation errors
2. Review Spring Security 7.0 migration guide for specific API changes
3. Update `WebSecurityConfig.java` and related classes
4. Fix OAuth2/OIDC configuration if needed
5. Run authentication-related unit and integration tests

### Iteration 3: Springdoc & OpenAPI (2-3 days)

1. Update Springdoc imports if package names changed
2. Verify OpenAPI configuration classes
3. Test Swagger UI and API documentation endpoints
4. Fix any annotation changes required

### Iteration 4: Observability & Metrics (2-3 days)

1. Address Prometheus client 1.x breaking changes
2. Update any direct Prometheus client API usage
3. Verify Micrometer integration
4. Test metrics endpoints (`/actuator/prometheus`)

### Iteration 5: Gateway & REST Modules (1 week)

1. Fix `zeebe/gateway-rest` and `zeebe/gateway-grpc` modules
2. Verify `WebMvcConfigurer` implementations
3. Test REST API endpoints
4. Run gateway integration tests

### Iteration 6: Application Modules (2 weeks)

1. Fix `dist`, `operate`, `tasklist`, `optimize` modules
2. Migrate `spring.factories` in optimize module
3. Verify all `@SpringBootApplication` classes start correctly
4. Run full integration test suite

### Iteration 7: Full Validation & Cleanup (1 week)

1. Run complete test suite
2. Manual testing of key user flows
3. Performance benchmarking
4. Documentation updates
5. Code cleanup (remove deprecated API usage)

---

## Risk Mitigation

|                Risk                 |                      Mitigation                      |
|-------------------------------------|------------------------------------------------------|
| Third-party library incompatibility | Identify alternatives or pin to compatible versions  |
| Breaking API changes                | Review migration guide thoroughly before starting    |
| Test failures                       | Prioritize fixing critical path tests first          |
| Runtime issues                      | Extensive integration testing in staging environment |
| Performance regression              | Benchmark before and after migration                 |

---

## Rollback Plan

1. Maintain the migration in a feature branch
2. Keep the main branch on Spring Boot 3.5.x until migration is validated
3. If critical issues found, revert to Spring Boot 3.5.x branch
4. Document any partial changes that need to be preserved

---

## References

- [Spring Boot 4.0 Migration Guide](https://github.com/spring-projects/spring-boot/wiki/Spring-Boot-4.0-Migration-Guide)
- [Spring Framework 7.0 Release Notes](https://github.com/spring-projects/spring-framework/wiki)
- [Spring Security 7.0 Migration Guide](https://docs.spring.io/spring-security/reference/migration-7/index.html)
- [Jakarta EE 11 Changes](https://jakarta.ee/release/11/)

---

## Checklist Summary

- [ ] Phase 1: Preparation and dependency analysis complete
- [ ] Phase 2: Version properties updated
- [ ] Phase 3: All modules compile successfully
- [ ] Phase 4: Properties and configuration migrated
- [ ] Phase 5: Spring factories migrated
- [ ] Phase 6: All tests passing
- [ ] Phase 7: Build validation complete
- [ ] Phase 8: Documentation updated

---

*Document created: January 2026*
*Last updated: January 2026*
