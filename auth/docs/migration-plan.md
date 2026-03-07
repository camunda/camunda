# Migration Plan: Consolidate `security/` into the `auth/` Library

**Date:** 2025-03
**Status:** All 6 phases complete (see status per phase below)

## Current State

The auth library (`auth/`) is self-contained with:
- `auth-domain` — SPIs, domain models, port interfaces (12 read/write ports for 6 entity types)
- `auth-spring` — Spring Security integration (OIDC, Basic Auth, filters, converters)
- `auth-spring-boot-starter` — Auto-configuration (12 `@AutoConfiguration` classes)
- `auth-persist-elasticsearch` — ES adapters for all entities + session + token
- `auth-persist-rdbms` — RDBMS adapters with Liquibase migrations

The monorepo already partially consumes the auth library via bridge classes in `dist/`:
- `DefaultAuthenticationInitializer` — bridges properties (`camunda.security.authentication.method` → `camunda.auth.method`)
- `CamundaAuthSdkConfiguration` — registers `MembershipResolver`, `UserProfileProvider`, `TenantInfoProvider`
- `CamundaAuthenticationConfiguration` — registers converters, holders, authentication provider
- `CamundaSecurityConfiguration` — wraps `SecurityConfiguration` properties bean

**The problem**: 195 files across the monorepo still import from `io.camunda.security.configuration`. The `dist/` bridge classes manually wire beans that the auth library's auto-configuration should handle. The `security-core` module still contains authentication configuration classes that are duplicated in `auth-domain`.

---

## Scope Clarification

**In scope** — eliminate authentication configuration duplication, wire auth library auto-config into monorepo, remove bridge code that duplicates auto-config:
- Config classes that are pure authentication concerns (claim mapping, OIDC, auth method, CSRF, multi-tenancy)
- Bridge wiring in `dist/` that duplicates `auth-spring-boot-starter` auto-config
- Property bridging in `DefaultAuthenticationInitializer`

**Out of scope** — authorization concerns remain in `security/`:
- `AuthorizationsConfiguration`, `InitializationConfiguration` — Zeebe identity initialization
- `ConfiguredUser`, `ConfiguredRole`, `ConfiguredTenant`, `ConfiguredGroup`, `ConfiguredAuthorization`, `ConfiguredMappingRule` — Zeebe init config models
- `SaasConfiguration` — SaaS-specific authorization
- `SecurityConfiguration` as a property binding class — stays in `security-core` but delegates auth sub-configs to `auth-domain`
- `ResourceAccessProvider`, `ResourceAccessChecks` — authorization
- Header configs (`CrossOriginEmbedderPolicyConfig`, etc.) — HTTP security headers

---

## Phase 1: Bridge `DefaultAuthenticationInitializer` to set persistence properties ✅ DONE

The initializer already bridges `camunda.security.authentication.method` → `camunda.auth.method`. It must also bridge persistence properties so the auth library's persist modules activate.

### Steps

1. In `DefaultAuthenticationInitializer`, add:
   - Bridge `camunda.data.secondary-storage.type` → `camunda.auth.persistence.type` (mapping `elasticsearch`/`rdbms`)
   - Set `camunda.auth.persistence.mode=external` (OC always uses external mode — Zeebe owns writes)
2. Verify auth library auto-configs activate with the bridged properties.

### Files

- `dist/src/main/java/io/camunda/application/initializers/DefaultAuthenticationInitializer.java`

---

## Phase 2: Eliminate `dist/` bridge beans that duplicate auto-config ✅ DONE

The `CamundaAuthenticationConfiguration` in `dist/` manually registers beans that `auth-spring-boot-starter` already auto-configures. The auto-config should handle these; `dist/` should only register consumer-specific SPI implementations.

### Analysis

| Bean in `dist/CamundaAuthenticationConfiguration` |          Auto-configured in starter?          |      Action      |
|---------------------------------------------------|-----------------------------------------------|------------------|
| `UnprotectedCamundaAuthenticationConverter`       | Yes — `CamundaAuthAutoConfiguration`          | Delete from dist |
| `RequestContextBasedAuthenticationHolder`         | Yes — `CamundaSessionHolderAutoConfiguration` | Delete from dist |
| `HttpSessionBasedAuthenticationHolder`            | Yes — `CamundaSessionHolderAutoConfiguration` | Delete from dist |
| `DefaultCamundaAuthenticationProvider`            | Yes — `CamundaAuthAutoConfiguration`          | Delete from dist |

The `HttpSessionBasedAuthenticationHolder` in `dist/` reads `authenticationRefreshInterval` from `SecurityConfiguration` (legacy). The auto-config reads from `camunda.auth.session.refresh-interval`. The initializer must bridge this property.

### Steps

1. Bridge `camunda.security.authentication.authenticationRefreshInterval` → `camunda.auth.session.refresh-interval` in `DefaultAuthenticationInitializer`.
2. Bridge `camunda.security.authentication.unprotected-api` → `camunda.auth.unprotected-api` in `DefaultAuthenticationInitializer`.
3. Delete `CamundaAuthenticationConfiguration` from `dist/` — all its beans are auto-configured.
4. Verify no broken bean references.

### Files

- `dist/src/main/java/io/camunda/application/initializers/DefaultAuthenticationInitializer.java`
- `dist/src/main/java/io/camunda/application/commons/authentication/CamundaAuthenticationConfiguration.java` — DELETE

---

## Phase 3: Move `SecurityConfiguration` authentication fields to `auth-domain` ✅ ALREADY DONE

Investigation revealed this was already completed in earlier commits. `SecurityConfiguration` already imports `AuthenticationConfiguration` and `MultiTenancyConfiguration` from `auth-domain`.

`SecurityConfiguration` in `security-core` is a 126-line aggregate that contains BOTH authentication and authorization configs:

```java
public class SecurityConfiguration {
    private AuthenticationConfiguration authentication;   // ← auth concern
    private MultiTenancyConfiguration multiTenancy;       // ← auth concern
    private AuthorizationsConfiguration authorizations;   // ← authZ concern (stay)
    private InitializationConfiguration initialization;   // ← authZ concern (stay)
    private SaasConfiguration saas;                       // ← authZ concern (stay)
    private HeaderConfiguration headers;                  // ← authZ concern (stay)
}
```

`AuthenticationConfiguration` and `MultiTenancyConfiguration` are already in `auth-domain/config/`. The `SecurityConfiguration` getter delegates to them. The 195 consumer files import `SecurityConfiguration` — most use it to access `.getAuthentication()` or `.getMultiTenancy()` (auth concerns).

### Steps

1. Make `SecurityConfiguration` import `AuthenticationConfiguration` and `MultiTenancyConfiguration` FROM `auth-domain` instead of defining its own.
2. Update `security-core/pom.xml` to depend on `auth-domain` (it may already).
3. Verify all 195 consumers still compile — the API surface doesn't change, only the import source for the nested config classes.

### Files

- `security/security-core/pom.xml` — verify auth-domain dependency
- `security/security-core/src/main/java/io/camunda/security/configuration/SecurityConfiguration.java`
- `security/security-core/src/main/java/io/camunda/security/configuration/AuthenticationConfiguration.java` — DELETE (moved to auth-domain)
- `security/security-core/src/main/java/io/camunda/security/configuration/OidcAuthenticationConfiguration.java` — DELETE (moved to auth-domain)

---

## Phase 4: Migrate `CamundaSecurityConfiguration` bean creation ✅ NOT NEEDED

Investigation revealed `MultiTenancyConfiguration` is extracted from `SecurityConfiguration` (which uses Spring property binding `camunda.security.*`). This serves both auth and authZ consumers. Moving it to auth auto-config would break the property binding chain. The current setup already works correctly — `SecurityConfiguration` delegates to `auth-domain` config classes.

`dist/CamundaSecurityConfiguration` creates `InitializationConfiguration` and `MultiTenancyConfiguration` beans from `SecurityConfiguration`. These should be auto-configured.

### Steps

1. Add `MultiTenancyConfiguration` bean to auth-spring-boot-starter auto-config (reads from `camunda.auth.multi-tenancy.*`).
2. Bridge `camunda.security.multiTenancy.checksEnabled` → `camunda.auth.multi-tenancy.checks-enabled` in initializer.
3. Keep `InitializationConfiguration` in `dist/CamundaSecurityConfiguration` — it's an authorization concern.
4. Remove `MultiTenancyConfiguration` bean from `dist/CamundaSecurityConfiguration`.

### Files

- `auth/auth-spring-boot-starter/` — add multi-tenancy auto-config bean
- `dist/src/main/java/io/camunda/application/initializers/DefaultAuthenticationInitializer.java`
- `dist/src/main/java/io/camunda/application/commons/security/CamundaSecurityConfiguration.java`

---

## Phase 5: Wire persistence property bridge and validate ✅ DONE

Ensure the auth library's persistence modules activate correctly within the monorepo.

### Steps

1. Verify `auth-persist-elasticsearch` and `auth-persist-rdbms` are on the monorepo's classpath via `auth-spring-boot-starter` dependency (or directly).
2. Verify auto-config registers `SessionPersistencePort` and `TokenStorePort` beans.
3. Test that existing `SessionPersistenceAdapter` in `dist/` overrides the auto-configured default via `@ConditionalOnMissingBean` (if the monorepo needs to keep its own session adapter during transition).

### Files

- `dist/pom.xml` — verify auth-persist module on classpath
- Integration tests to validate bean wiring

---

## Phase 6: Clean up `CamundaAuthSdkConfiguration` ✅ DONE

The SPI implementations registered in `CamundaAuthSdkConfiguration` (`MembershipResolver`, `UserProfileProvider`, `TenantInfoProvider`) are consumer-specific — they delegate to monorepo services (`MappingRuleServices`, `TenantServices`, etc.). These MUST remain in `dist/` because the auth library should not know about these services.

However, the `camunda.auth.sdk.enabled` gating mechanism can be removed — these beans should activate when `camunda.auth.method` is set (which is already bridged by the initializer).

### Steps

1. Replace `@ConditionalOnProperty(name = "camunda.auth.sdk.enabled")` with `@ConditionalOnProperty(name = "camunda.auth.method")` on `CamundaAuthSdkConfiguration`.
2. Remove `enableAuthSdk()` method from `DefaultAuthenticationInitializer`.
3. Simplify: the auto-config's `NoOpMembershipResolver` is already `@ConditionalOnMissingBean`, so the `dist/` beans will naturally override it.

### Files

- `dist/src/main/java/io/camunda/application/commons/authentication/CamundaAuthSdkConfiguration.java`
- `dist/src/main/java/io/camunda/application/initializers/DefaultAuthenticationInitializer.java`

---

## Verification

After each phase:

```bash
# Auth library standalone build
cd auth && ./mvnw clean verify -T1C

# Monorepo build (quick)
cd .. && ./mvnw compile -T1C -Dquickly
```

After all phases:

```bash
# Full monorepo test
./mvnw verify -Dquickly -T1C

# Verify no new io.camunda.authentication imports
grep -r "io.camunda.authentication" --include="*.java" -l | wc -l
# Expected: 0

# Verify SecurityConfiguration consumers still compile
./mvnw compile -T1C
```

---

## Summary

| Phase |                        Focus                        |     Status     |                           Impact                           |
|-------|-----------------------------------------------------|----------------|------------------------------------------------------------|
| 1     | Bridge persistence properties in initializer        | ✅ Done         | Activates auth library persist modules in monorepo         |
| 2     | Delete duplicate bean wiring in dist                | ✅ Done         | Removes `CamundaAuthenticationConfiguration` (63 LOC)      |
| 3     | Move auth configs from security-core to auth-domain | ✅ Already done | `SecurityConfiguration` already imports from `auth-domain` |
| 4     | Auto-configure multi-tenancy bean                   | ✅ Not needed   | Property binding chain works correctly as-is               |
| 5     | Wire and validate persistence modules               | ✅ Done         | `camunda-auth-persist-*` on dist classpath                 |
| 6     | Clean up SDK configuration gating                   | ✅ Done         | Removes artificial `camunda.auth.sdk.enabled` flag         |

**End state**: Auth library auto-configuration handles all authentication concerns. `dist/` only provides consumer-specific SPI implementations (membership resolver, user profile provider, admin user check, web component access). `security-core` retains authorization concerns only.

---

## Remaining Uncommitted Work

The following changes are in the working tree but not yet committed:

1. **Grant-type hierarchy simplification** — `auth-sdk` module removed, `AuthorizationGrant*` classes deleted, `DelegationChainValidator` removed (~2,549 lines deleted)
2. **Persistence adapter additions** — 78 new files for ES and RDBMS adapters covering all identity entities (User, Role, Tenant, Group, MappingRule, Authorization)
3. **Monorepo integration** — All Phase 1-6 changes in `dist/`

All modules compile (clean build) and all 81 tests pass.
