# Migrate Auth Config Consumers to Gatekeeper Types

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove `AuthenticationConfiguration`, `OidcAuthenticationConfiguration`, and `CsrfConfiguration` from security-core by migrating all consumers to inject gatekeeper types directly (`AuthenticationConfig`, `OidcConfig`, `AuthenticationMethod`).

**Architecture:** All production consumers are read-only — they can switch to gatekeeper's immutable records. Test utilities that mutate `SecurityConfiguration.getAuthentication()` will switch to Spring property overrides since gatekeeper binds to the same `camunda.security.*` namespace. `SecurityConfiguration` keeps its authorization fields (`authorizations`, `initialization`, `multiTenancy`, `saas`) but loses `authentication` and `csrf`.

**Tech Stack:** Java 21, Spring Boot 4.0.3, Maven multi-module

---

## Important Notes

- All consumers are READ-ONLY on auth config. No production code mutates it.
- `GatekeeperProperties` binds to the same `camunda.security.*` namespace as `SecurityConfiguration`, so property values are already available through both.
- `AuthenticationConfig` and `OidcConfig` are already registered as Spring beans by `GatekeeperOidcAutoConfiguration` (the `AuthenticationConfig` bean comes from `GatekeeperProperties.toAuthenticationConfig()`).
- The `AuthenticationConfig` bean must exist for non-OIDC modes too. Check that `GatekeeperAuthAutoConfiguration` creates it.
- Always pass `-Dcheckstyle.skip` to mvn (pre-existing issue).

---

## Chunk 1: Ensure AuthenticationConfig bean is always available

### Task 1: Verify AuthenticationConfig bean creation

**Files:**
- Read: `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/autoconfigure/GatekeeperAuthAutoConfiguration.java`
- Read: `gatekeeper-spring-boot-starter/src/main/java/io/camunda/gatekeeper/spring/autoconfigure/GatekeeperOidcAutoConfiguration.java`

- [ ] **Step 1: Check where AuthenticationConfig bean is created**

Search for `AuthenticationConfig` bean definition. It should be in `GatekeeperAuthAutoConfiguration` (which runs for ALL auth methods, not just OIDC).

Run: `grep -rn "AuthenticationConfig" gatekeeper/gatekeeper-spring-boot-starter/src/main/java/ --include="*.java"`

- [ ] **Step 2: Add AuthenticationConfig bean if missing**

If no `@Bean` method returns `AuthenticationConfig`, add one to `GatekeeperAuthAutoConfiguration`:

```java
@Bean
@ConditionalOnMissingBean
public AuthenticationConfig authenticationConfig(final GatekeeperProperties properties) {
  return properties.toAuthenticationConfig();
}
```

This ensures all consumers can inject `AuthenticationConfig` regardless of auth method.

- [ ] **Step 3: Compile and test**

Run: `cd gatekeeper && ../mvnw compile -T1C -q && ../mvnw test -q`

---

## Chunk 2: Swap production consumers — zeebe gateway

### Task 2: Migrate AuthenticationHandler to use OidcConfig

**Files:**
- Modify: `zeebe/gateway-grpc/src/main/java/io/camunda/zeebe/gateway/interceptors/impl/AuthenticationHandler.java`

The `Oidc` inner class constructor currently takes `OidcAuthenticationConfiguration`. Change it to take `OidcConfig` from gatekeeper.

Fields accessed (all have exact equivalents on `OidcConfig`):
- `getUsernameClaim()` → `usernameClaim()`
- `getClientIdClaim()` → `clientIdClaim()`
- `getGroupsClaim()` → `groupsClaim()`
- `isGroupsClaimConfigured()` → `isGroupsClaimConfigured()`
- `isPreferUsernameClaim()` → `preferUsernameClaim()`

- [ ] **Step 1: Change import and constructor parameter**

Replace `import io.camunda.security.configuration.OidcAuthenticationConfiguration` with `import io.camunda.gatekeeper.config.OidcConfig`.

Update the constructor and field type from `OidcAuthenticationConfiguration` to `OidcConfig`.

- [ ] **Step 2: Update accessor calls**

OidcConfig is a record, so getters are record-style:
- `config.getUsernameClaim()` → `config.usernameClaim()`
- `config.getClientIdClaim()` → `config.clientIdClaim()`
- `config.getGroupsClaim()` → `config.groupsClaim()`
- `config.isPreferUsernameClaim()` → `config.preferUsernameClaim()`
- `config.isGroupsClaimConfigured()` stays the same (it's a method on OidcConfig)

- [ ] **Step 3: Update Gateway.java**

`Gateway.java` passes `securityConfiguration.getAuthentication().getOidc()` to the AuthenticationHandler. Change it to inject `AuthenticationConfig` and pass `authenticationConfig.oidc()`:

```java
// Before:
new AuthenticationHandler.Oidc(jwtDecoder, securityConfiguration.getAuthentication().getOidc())
// After:
new AuthenticationHandler.Oidc(jwtDecoder, authenticationConfig.oidc())
```

Also change `securityConfiguration.getAuthentication().getMethod()` to use injected `AuthenticationConfig`:

```java
// Before:
final var authMethod = securityConfiguration.getAuthentication().getMethod();
// After:
final var authMethod = authenticationConfig.method();
```

Gateway.java will need `AuthenticationConfig` as a constructor/field injection parameter.

- [ ] **Step 4: Update AuthenticationInterceptorTest**

The test creates `OidcAuthenticationConfiguration` via its builder. Replace with `OidcConfig` record construction. The test likely constructs a test OIDC config — use the record constructor with the needed field values and `null` for unused fields.

- [ ] **Step 5: Update StubbedGateway**

Similar to Gateway.java — passes OIDC config to AuthenticationHandler. Update to use `OidcConfig`.

- [ ] **Step 6: Compile zeebe gateway-grpc**

Run: `mvn compile -pl zeebe/gateway-grpc -am -q -Dcheckstyle.skip`

---

### Task 3: Migrate SetupController and BrokerRequestAuthorizationConverter

**Files:**
- Modify: `zeebe/gateway-rest/src/main/java/io/camunda/zeebe/gateway/rest/controller/setup/SetupController.java`
- Modify: `zeebe/gateway-rest/src/test/java/io/camunda/zeebe/gateway/rest/controller/setup/SetupControllerTest.java`
- Modify: `security/security-core/src/main/java/io/camunda/security/auth/BrokerRequestAuthorizationConverter.java`

**SetupController:** Reads `securityConfiguration.getAuthentication().getMethod()` to check for BASIC auth. Replace with injecting `AuthenticationConfig` (or just `AuthenticationMethod` from gatekeeper).

**BrokerRequestAuthorizationConverter:** Reads `securityConfiguration.getAuthentication().getMethod()` and `.getOidc().isGroupsClaimConfigured()`. It currently takes `SecurityConfiguration` in its constructor. Change to take `AuthenticationConfig` (for method check and OIDC config access).

- [ ] **Step 1: Update SetupController**

Inject `AuthenticationConfig` instead of `SecurityConfiguration` for the auth check. Keep `SecurityConfiguration` if it's also used for non-auth fields.

- [ ] **Step 2: Update SetupControllerTest**

The test mocks `securityConfiguration.getAuthentication().getMethod()`. Change to mock or inject `AuthenticationConfig` with the desired method.

- [ ] **Step 3: Update BrokerRequestAuthorizationConverter**

Change constructor to take `AuthenticationConfig` instead of (or in addition to) `SecurityConfiguration`. Use `authenticationConfig.method()` and `authenticationConfig.oidc().isGroupsClaimConfigured()`.

Note: This class also uses `securityConfiguration` for the `isApiProtected()` check — verify if that's still needed.

- [ ] **Step 4: Compile**

Run: `mvn compile -pl zeebe/gateway-rest,security/security-core -am -q -Dcheckstyle.skip`

---

## Chunk 3: Swap production consumers — dist and operate

### Task 4: Migrate AdminClientConfigController and CamundaSecurityConfiguration

**Files:**
- Modify: `dist/src/main/java/io/camunda/identity/webapp/controllers/AdminClientConfigController.java`
- Modify: `dist/src/main/java/io/camunda/application/commons/security/CamundaSecurityConfiguration.java`
- Modify: `dist/src/test/java/io/camunda/webapps/controllers/AdminClientConfigControllerTest.java`

**AdminClientConfigController:** Reads `getAuthentication().getMethod()` and `getAuthentication().getOidc().getGroupsClaim()`. Inject `AuthenticationConfig` directly.

**CamundaSecurityConfiguration:** Reads `getAuthentication().getUnprotectedApi()`. Inject `AuthenticationConfig` or `GatekeeperProperties`.

- [ ] **Step 1: Update AdminClientConfigController**

Inject `AuthenticationConfig` alongside `SecurityConfiguration`. Replace:
- `securityConfiguration.getAuthentication().getMethod()` → `authenticationConfig.method()`
- `securityConfiguration.getAuthentication().getOidc()` → `authenticationConfig.oidc()`
- Record accessor style: `.getGroupsClaim()` → `.groupsClaim()`

- [ ] **Step 2: Update AdminClientConfigControllerTest**

Remove `AuthenticationConfiguration` and `OidcAuthenticationConfiguration` imports. Construct test data using gatekeeper types.

- [ ] **Step 3: Update CamundaSecurityConfiguration**

Replace `camundaSecurityProperties.getAuthentication().getUnprotectedApi()` with `authenticationConfig.unprotectedApi()` (inject `AuthenticationConfig`).

- [ ] **Step 4: Compile**

Run: `mvn compile -pl dist -am -q -Dcheckstyle.skip`

### Task 5: Migrate operate ClientConfig

**Files:**
- Modify: `operate/webapp/src/main/java/io/camunda/operate/webapp/rest/ClientConfig.java`
- Modify: `operate/qa/integration-tests/src/test/java/io/camunda/operate/rest/ClientConfigRestServiceIT.java`

**ClientConfig:** Reads `getAuthentication().getOidc().getOrganizationId()`. Inject `AuthenticationConfig` and use `authenticationConfig.oidc()`.

Note: `OidcConfig` doesn't currently have `organizationId`. Check if it's needed. If so, it's a SaaS concern that belongs in `SaasConfiguration`, not auth config. The code checks `organizationId == null` to determine if logout is allowed. This may need to be refactored to read from `SaasConfiguration` directly instead.

- [ ] **Step 1: Check organizationId usage**

If `organizationId` is SaaS-specific, change `ClientConfig.java` to read from `securityConfiguration.getSaas().getOrganizationId()` instead of `getAuthentication().getOidc().getOrganizationId()`.

- [ ] **Step 2: Update ClientConfigRestServiceIT**

Remove `AuthenticationConfiguration` / `OidcAuthenticationConfiguration` imports. Use gatekeeper types or SaaS config.

- [ ] **Step 3: Compile**

Run: `mvn compile -pl operate/webapp -am -q -Dcheckstyle.skip`

---

## Chunk 4: Simplify OidcConfigurationAdapter

### Task 6: Simplify or remove OidcConfigurationAdapter

**Files:**
- Modify: `authentication/src/main/java/io/camunda/authentication/adapter/OidcConfigurationAdapter.java`

This adapter reads `SecurityConfiguration` → `AuthenticationConfiguration` → `OidcAuthenticationConfiguration` and converts to `OidcConfig`. With gatekeeper now providing `OidcConfig` directly from properties, this adapter's conversion logic is redundant.

However, the adapter also reads from `SecurityConfiguration.getAuthentication().getProviders()` — but we already deleted `ProvidersConfiguration`. So the adapter should now only handle the primary OIDC config.

Actually, gatekeeper's `propertiesOidcConfigurationProvider` bean (in `GatekeeperOidcAutoConfiguration`) already reads providers from `GatekeeperProperties`. So `OidcConfigurationAdapter` is now redundant if gatekeeper handles all OIDC config.

- [ ] **Step 1: Check if OidcConfigurationAdapter can be removed**

If gatekeeper's `propertiesOidcConfigurationProvider` provides the same configs that `OidcConfigurationAdapter` did, the adapter can be deleted. The `@ConditionalOnMissingBean` on gatekeeper's provider means it would back off if the adapter exists. Removing the adapter lets gatekeeper's default take over.

- [ ] **Step 2: Remove or simplify the adapter**

If removing: delete `OidcConfigurationAdapter.java` and its `OidcAuthenticationConfiguration` import.

If simplifying (e.g., adapter still reads something gatekeeper doesn't): update to not use `OidcAuthenticationConfiguration`.

- [ ] **Step 3: Compile authentication module**

Run: `mvn compile -pl authentication -am -q -Dcheckstyle.skip`

---

## Chunk 5: Remove auth fields from SecurityConfiguration

### Task 7: Remove authentication and csrf fields from SecurityConfiguration

**Files:**
- Modify: `security/security-core/src/main/java/io/camunda/security/configuration/SecurityConfiguration.java`

- [ ] **Step 1: Remove the authentication field**

Remove:
- `private AuthenticationConfiguration authentication` field
- `getAuthentication()` and `setAuthentication()` methods
- Import for `AuthenticationConfiguration`

- [ ] **Step 2: Remove the csrf field**

Remove:
- `private CsrfConfiguration csrf` field
- `getCsrf()` and `setCsrf()` methods
- Import for `CsrfConfiguration`

- [ ] **Step 3: Update isApiProtected() if it references authentication**

If `SecurityConfiguration.isApiProtected()` checks `authentication.getMethod()` or `authentication.getUnprotectedApi()`, it needs updating. Options:
- Move the logic to a gatekeeper utility
- Have the method take `AuthenticationConfig` as parameter
- Or remove it and let callers check directly

- [ ] **Step 4: Fix compilation errors**

Run: `mvn compile -pl security/security-core -am -q -Dcheckstyle.skip`

Fix any remaining references to `getAuthentication()` or `getCsrf()` on `SecurityConfiguration`.

---

## Chunk 6: Update test utilities and QA tests

### Task 8: Update test utilities

**Files:**
- Modify: `zeebe/qa/util/src/main/java/io/camunda/zeebe/qa/util/cluster/TestStandaloneBroker.java`
- Modify: `zeebe/qa/util/src/main/java/io/camunda/zeebe/qa/util/cluster/TestStandaloneGateway.java`
- Modify: `qa/util/src/main/java/io/camunda/qa/util/cluster/TestCamundaApplication.java`

These utilities mutate `securityConfig.getAuthentication().setXxx()`. Replace with Spring property overrides.

Pattern:

```java
// Before:
securityConfig.getAuthentication().setUnprotectedApi(true);
// After:
withProperty("camunda.security.authentication.unprotected-api", "true");
```

```java
// Before:
securityConfig.getAuthentication().setMethod(AuthenticationMethod.OIDC);
// After:
withProperty("camunda.security.authentication.method", "OIDC");
```

Check each utility for a `withProperty` method or equivalent. These test builders typically support property overrides.

- [ ] **Step 1: Update TestStandaloneBroker**
- [ ] **Step 2: Update TestStandaloneGateway**
- [ ] **Step 3: Update TestCamundaApplication**
- [ ] **Step 4: Compile QA utilities**

### Task 9: Update QA acceptance tests

**Files:**
- Modify: ~15 test files in `qa/acceptance-tests/src/test/java/io/camunda/it/` and `zeebe/qa/integration-tests/src/test/java/`

Tests that call `.withSecurityConfig(c -> c.getAuthentication().getOidc().setXxx())` should switch to `.withProperty("camunda.security.authentication.oidc.xxx", "value")`.

Common patterns:

```java
// Before:
.withSecurityConfig(c -> c.getAuthentication().getOidc().setClientIdClaim("client_id"))
// After:
.withProperty("camunda.security.authentication.oidc.client-id-claim", "client_id")
```

- [ ] **Step 1: Find all withSecurityConfig calls that access authentication**

Run: `grep -rn "getAuthentication()" --include="*.java" qa/ zeebe/qa/`

- [ ] **Step 2: Replace each with property overrides**
- [ ] **Step 3: Compile and verify**

---

## Chunk 7: Delete auth config classes and CsrfConfiguration

### Task 10: Delete AuthenticationConfiguration and OidcAuthenticationConfiguration

**Files:**
- Delete: `security/security-core/src/main/java/io/camunda/security/configuration/AuthenticationConfiguration.java`
- Delete: `security/security-core/src/main/java/io/camunda/security/configuration/OidcAuthenticationConfiguration.java`

- [ ] **Step 1: Verify no remaining references**

Run: `grep -rn "AuthenticationConfiguration\|OidcAuthenticationConfiguration" --include="*.java" . | grep -v "/target/"`

Expected: No hits (or only in deleted/updated files).

- [ ] **Step 2: Delete the files**
- [ ] **Step 3: Compile the full project**

Run: `mvn compile -T1C -q -Dcheckstyle.skip`

### Task 11: Delete CsrfConfiguration and update its 2 test consumers

**Files:**
- Modify: `qa/acceptance-tests/src/test/java/io/camunda/it/csrf/CsrfTokenIT.java`
- Modify: `qa/acceptance-tests/src/test/java/io/camunda/it/logout/BasicAuthLogoutIT.java`
- Delete: `security/security-core/src/main/java/io/camunda/security/configuration/CsrfConfiguration.java`

These tests access `securityConfiguration.getCsrf().isEnabled()`. Replace with reading from `GatekeeperProperties.getCsrf().isEnabled()` or Spring property `camunda.security.csrf.enabled`.

- [ ] **Step 1: Update CsrfTokenIT**
- [ ] **Step 2: Update BasicAuthLogoutIT**
- [ ] **Step 3: Remove csrf field from SecurityConfiguration** (if not already done in Task 7)
- [ ] **Step 4: Delete CsrfConfiguration.java**
- [ ] **Step 5: Verify no remaining references**
- [ ] **Step 6: Full compile**

### Task 12: Update SecurityConfigurations factory

**Files:**
- Modify: `security/security-core/src/main/java/io/camunda/security/configuration/SecurityConfigurations.java`

The `unauthenticatedAndUnauthorized()` factory creates a `SecurityConfiguration` with auth config set. Since `SecurityConfiguration` no longer has authentication fields, update this factory to only set authorization-related defaults.

- [ ] **Step 1: Remove authentication setup from factory**
- [ ] **Step 2: Compile and verify**

### Task 13: Final commit

- [ ] **Step 1: Run spotless**

Run: `mvn spotless:apply -Dcheckstyle.skip`

- [ ] **Step 2: Full compile**

Run: `mvn compile -T1C -q -Dcheckstyle.skip`

- [ ] **Step 3: Run gatekeeper tests**

Run: `cd gatekeeper && ../mvnw test`

- [ ] **Step 4: Commit**

```bash
git add -A
git commit -m "refactor: migrate auth config consumers to gatekeeper types, remove AuthenticationConfiguration and OidcAuthenticationConfiguration from security-core"
```

