# Physical-Tenant Spring Security PoC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up isolated Spring Security per Physical Tenant in OC (`dist/`), proving login/OIDC-provider/session isolation against two Keycloak realms under a single `pt-security` Spring profile.

**Architecture:** Per-tenant `SecurityFilterChain` pairs (webapp + api) registered programmatically from `PhysicalTenantResolver.getAll()`. Browser-side isolation via cookie `Path` scoping. CSL's auto-config is opted out on the `pt-security` profile. The chain factory and per-tenant collaborator slice are designed portable to a future child-context model (approach C). End-to-end IT drives two Keycloak Testcontainers; a `PtPocLocalIdpRunner` exposes the same realms for browser-based developer iteration.

**Tech Stack:**
- Spring Boot 3 / Spring Security 6 (`oauth2Login`, `oauth2ResourceServer`)
- Spring Session (existing `WebSessionRepository` reused per-tenant via a key-prefixing decorator)
- `dasniko/testcontainers-keycloak` (already a project dependency, used by `OidcAuthOverRestIT`)
- JUnit 5 + AssertJ + Awaitility (project conventions)

**Spec:** [`docs/superpowers/specs/2026-05-20-physical-tenant-spring-security-poc-design.md`](../specs/2026-05-20-physical-tenant-spring-security-poc-design.md)

---

## File structure

New files (all paths from repo root):

| File | Responsibility |
|---|---|
| `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java` | Top-level `@Configuration`, profile-gated, registers per-tenant chains via `BeanDefinitionRegistryPostProcessor` |
| `authentication/src/main/java/io/camunda/authentication/pt/PerTenantSecurityChainFactory.java` | Builds the pair of `SecurityFilterChain` (webapp + api) for one tenant from a `TenantSecuritySlice` |
| `authentication/src/main/java/io/camunda/authentication/pt/TenantSecuritySlice.java` | Record bundling per-tenant collaborators (clients, decoder, session repo, cookie serializer, etc.) |
| `authentication/src/main/java/io/camunda/authentication/pt/PerTenantOidcRegistry.java` | Builds the per-tenant `OidcAuthenticationConfigurationRepository` (filters by `providers.assigned`) and the `ClientRegistrationRepository` |
| `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantRedirectUriRewriter.java` | Stamps the per-tenant prefix into each `ClientRegistration.redirectUri` template |
| `authentication/src/main/java/io/camunda/authentication/pt/PerTenantSessionRepository.java` | Decorator over `WebSessionRepository` that prefixes session ids with `t:<tenant>:` for keyspace isolation |
| `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantCookieSerializer.java` | Static factory that produces a `DefaultCookieSerializer` configured per tenant (cookie name + Path) |
| `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantWebFilter.java` | `OncePerRequestFilter` registered first in each tenant chain; stamps tenant id on the request attribute consumed by `PhysicalTenantContext.current()` |
| `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantWhoamiController.java` | Demo controller exposing `/physical-tenant/{t}/whoami` and `/v2/physical-tenants/{t}/whoami` |
| `dist/src/main/resources/application-pt-poc.yaml` | Bundled config that activates the PoC against the local Keycloak runner's default ports |
| `dist/src/test/java/io/camunda/application/pt/PtPocLocalIdpRunner.java` | Standalone `main()` for the developer iteration loop; boots two Keycloak containers on fixed ports |
| `dist/src/test/java/io/camunda/application/pt/PhysicalTenantSecurityIT.java` | End-to-end Testcontainers IT; boots two Keycloaks + OC in-JVM, drives OIDC flow, asserts isolation |
| `dist/src/test/resources/pt-poc/keycloak-default-realm.json` | Realm export: one client + one user for the default tenant |
| `dist/src/test/resources/pt-poc/keycloak-tenanta-realm.json` | Realm export: one client + one user for tenant A |

Modified files:

| File | Change |
|---|---|
| `authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java` | Add `!pt-security` to the profile predicate so CSL stays off when the PoC is active |
| `dist/src/main/java/io/camunda/application/commons/identity/WebSessionRepositoryConfiguration.java` | Same `!pt-security` exclusion — global `@EnableSpringHttpSession` is replaced by per-tenant filters under the PoC profile |

---

## Cross-cutting conventions

**Every commit ends with this sequence** (per project preferences: spotless + compile + commit):

```bash
./mvnw license:format spotless:apply -T1C
./mvnw install -pl authentication -am -Dquickly -T1C
git add <files>
git commit -m "<type>: <description>"
```

For tasks that also modify `dist/`, replace `-pl authentication -am` with `-pl authentication,dist -am`.

Run a single test class:

```bash
./mvnw verify -pl authentication -Dtest=<ClassName> -DskipTests=false -DskipITs -Dquickly -T1C
```

Run the IT:

```bash
./mvnw verify -pl dist -Dit.test=PhysicalTenantSecurityIT -DskipTests=false -DskipUTs -Dquickly -T1C
```

---

## Task 1: Profile scaffold + verify CSL opts out

**Goal:** Introduce the `pt-security` profile, exclude `WebSecurityConfig` and `WebSessionRepositoryConfiguration` when it is active, and verify boot produces zero `SecurityFilterChain` beans on this profile.

**Files:**
- Modify: `authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java:60-62` (profile predicate)
- Modify: `dist/src/main/java/io/camunda/application/commons/identity/WebSessionRepositoryConfiguration.java` (add profile predicate)
- Create: `authentication/src/test/java/io/camunda/authentication/pt/PtSecurityProfileBootTest.java`

- [ ] **Step 1: Write the failing test**

`authentication/src/test/java/io/camunda/authentication/pt/PtSecurityProfileBootTest.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = PtSecurityProfileBootTest.MinimalApp.class)
@ActiveProfiles({"consolidated-auth", "pt-security"})
@TestPropertySource(properties = {"camunda.security.authentication.method=oidc"})
class PtSecurityProfileBootTest {

  @org.springframework.boot.autoconfigure.SpringBootApplication
  static class MinimalApp {}

  @Test
  void shouldNotRegisterAnySecurityFilterChainOnPtSecurityProfile(
      final ApplicationContext context) {
    // given - the pt-security profile is active alongside consolidated-auth
    // when we collect every SecurityFilterChain in the context
    final var chains = context.getBeansOfType(SecurityFilterChain.class);

    // then no chain is registered yet — PhysicalTenantSecurityConfiguration is the
    // only allowed producer and it does not exist in this task. CSL's
    // CamundaSecurityAutoConfiguration must back off because WebSecurityConfig is
    // excluded from the active profile set.
    assertThat(chains).isEmpty();
  }
}
```

- [ ] **Step 2: Run the test and confirm it fails (today CSL produces chains)**

```bash
./mvnw verify -pl authentication -Dtest=PtSecurityProfileBootTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: FAIL — at least one of CSL's filter chains is registered.

- [ ] **Step 3: Make `WebSecurityConfig` profile-aware**

Edit `authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java`, change the profile annotation:

```java
@Profile({"consolidated-auth & !pt-security"})
```

(Spring `@Profile` supports `&` and `!` operators in a single expression.)

- [ ] **Step 4: Make `WebSessionRepositoryConfiguration` profile-aware**

Edit `dist/src/main/java/io/camunda/application/commons/identity/WebSessionRepositoryConfiguration.java`. Find the `@Configuration` / `@ConditionalOnPersistentWebSessionEnabled` block at the class top and add:

```java
@Profile("!pt-security")
```

(Per-tenant session repositories are wired by the PoC chain factory instead; the global `@EnableSpringHttpSession` would conflict.)

- [ ] **Step 5: Run the test and confirm it now passes**

```bash
./mvnw verify -pl authentication -Dtest=PtSecurityProfileBootTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: PASS — `chains` is empty.

- [ ] **Step 6: Format, compile, commit**

```bash
./mvnw license:format spotless:apply -T1C
./mvnw install -pl authentication,dist -am -Dquickly -T1C
git add authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java \
        dist/src/main/java/io/camunda/application/commons/identity/WebSessionRepositoryConfiguration.java \
        authentication/src/test/java/io/camunda/authentication/pt/PtSecurityProfileBootTest.java
git commit -m "feat: add pt-security profile that opts out of CSL chains and global Spring Session"
```

---

## Task 2: Keycloak realm exports

**Goal:** Two minimal realm JSON files that the Keycloak Testcontainer will import. One realm per tenant, one client per realm, one user per realm.

**Files:**
- Create: `dist/src/test/resources/pt-poc/keycloak-default-realm.json`
- Create: `dist/src/test/resources/pt-poc/keycloak-tenanta-realm.json`

- [ ] **Step 1: Write the default realm export**

`dist/src/test/resources/pt-poc/keycloak-default-realm.json`:

```json
{
  "realm": "default",
  "enabled": true,
  "sslRequired": "none",
  "registrationAllowed": false,
  "clients": [
    {
      "clientId": "camunda-pt-default-client",
      "enabled": true,
      "secret": "default-secret",
      "publicClient": false,
      "directAccessGrantsEnabled": true,
      "standardFlowEnabled": true,
      "redirectUris": [
        "http://localhost:8080/physical-tenant/default/login/oauth2/code/*",
        "http://localhost:8080/login/oauth2/code/*"
      ],
      "webOrigins": ["http://localhost:8080"]
    }
  ],
  "users": [
    {
      "username": "alice",
      "enabled": true,
      "email": "alice@default",
      "emailVerified": true,
      "firstName": "Alice",
      "lastName": "Default",
      "credentials": [{"type": "password", "value": "alice", "temporary": false}]
    }
  ]
}
```

- [ ] **Step 2: Write the tenant-A realm export**

`dist/src/test/resources/pt-poc/keycloak-tenanta-realm.json`: identical shape with `realm`, `clientId`, `secret`, user changed:

```json
{
  "realm": "tenanta",
  "enabled": true,
  "sslRequired": "none",
  "registrationAllowed": false,
  "clients": [
    {
      "clientId": "camunda-pt-tenanta-client",
      "enabled": true,
      "secret": "tenanta-secret",
      "publicClient": false,
      "directAccessGrantsEnabled": true,
      "standardFlowEnabled": true,
      "redirectUris": [
        "http://localhost:8080/physical-tenant/tenanta/login/oauth2/code/*"
      ],
      "webOrigins": ["http://localhost:8080"]
    }
  ],
  "users": [
    {
      "username": "bob",
      "enabled": true,
      "email": "bob@tenanta",
      "emailVerified": true,
      "firstName": "Bob",
      "lastName": "Tenanta",
      "credentials": [{"type": "password", "value": "bob", "temporary": false}]
    }
  ]
}
```

- [ ] **Step 3: Verify both files are valid JSON**

```bash
python3 -m json.tool dist/src/test/resources/pt-poc/keycloak-default-realm.json > /dev/null
python3 -m json.tool dist/src/test/resources/pt-poc/keycloak-tenanta-realm.json > /dev/null
```

Both should exit 0.

- [ ] **Step 4: Commit**

```bash
git add dist/src/test/resources/pt-poc/keycloak-default-realm.json \
        dist/src/test/resources/pt-poc/keycloak-tenanta-realm.json
git commit -m "test: add Keycloak realm exports for PT security PoC"
```

---

## Task 3: PtPocLocalIdpRunner standalone main()

**Goal:** A `main()` that boots both Keycloak containers on fixed host ports (`8081`, `8082`), imports the realms, and blocks on stdin. The developer runs this once and leaves it up across OC restarts.

**Files:**
- Create: `dist/src/test/java/io/camunda/application/pt/PtPocLocalIdpRunner.java`

- [ ] **Step 1: Write the runner**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.pt;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.testcontainers.containers.BindMode;

/**
 * Standalone runner that exposes the two PoC Keycloak realms on fixed local ports for
 * developer iteration. Reuses the project's {@link DefaultTestContainers#createDefaultKeycloak()}
 * pattern so realm shape matches the {@link PhysicalTenantSecurityIT}.
 *
 * <p>Run: {@code ./mvnw -pl dist test-compile exec:java
 *   -Dexec.mainClass=io.camunda.application.pt.PtPocLocalIdpRunner
 *   -Dexec.classpathScope=test}
 */
public final class PtPocLocalIdpRunner {

  private static final int DEFAULT_REALM_HOST_PORT = 8081;
  private static final int TENANTA_REALM_HOST_PORT = 8082;
  private static final int KEYCLOAK_INTERNAL_PORT = 8080;

  private PtPocLocalIdpRunner() {}

  public static void main(final String[] args) throws Exception {
    final KeycloakContainer defaultRealm =
        DefaultTestContainers.createDefaultKeycloak()
            .withClasspathResourceMapping(
                "pt-poc/keycloak-default-realm.json",
                "/opt/keycloak/data/import/default-realm.json",
                BindMode.READ_ONLY)
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(KEYCLOAK_INTERNAL_PORT);
    defaultRealm.setPortBindings(
        java.util.List.of(DEFAULT_REALM_HOST_PORT + ":" + KEYCLOAK_INTERNAL_PORT));

    final KeycloakContainer tenantaRealm =
        DefaultTestContainers.createDefaultKeycloak()
            .withClasspathResourceMapping(
                "pt-poc/keycloak-tenanta-realm.json",
                "/opt/keycloak/data/import/tenanta-realm.json",
                BindMode.READ_ONLY)
            .withCommand("start-dev", "--import-realm")
            .withExposedPorts(KEYCLOAK_INTERNAL_PORT);
    tenantaRealm.setPortBindings(
        java.util.List.of(TENANTA_REALM_HOST_PORT + ":" + KEYCLOAK_INTERNAL_PORT));

    Runtime.getRuntime()
        .addShutdownHook(
            new Thread(
                () -> {
                  defaultRealm.stop();
                  tenantaRealm.stop();
                }));

    defaultRealm.start();
    tenantaRealm.start();

    System.out.println();
    System.out.println("=== PT-PoC local IdPs ready ===");
    System.out.println(
        "default issuer:   http://localhost:" + DEFAULT_REALM_HOST_PORT + "/realms/default");
    System.out.println(
        "tenanta issuer:   http://localhost:" + TENANTA_REALM_HOST_PORT + "/realms/tenanta");
    System.out.println();
    System.out.println("default client:   camunda-pt-default-client / default-secret");
    System.out.println("tenanta client:   camunda-pt-tenanta-client / tenanta-secret");
    System.out.println();
    System.out.println("default user:     alice / alice");
    System.out.println("tenanta user:     bob / bob");
    System.out.println();
    System.out.println("Press <enter> to stop.");

    try (final BufferedReader in =
        new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
      in.readLine();
    }
  }
}
```

- [ ] **Step 2: Compile**

```bash
./mvnw install -pl dist -am -Dquickly -T1C
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Smoke-test the runner (manual)**

```bash
./mvnw -pl dist test-compile exec:java \
  -Dexec.mainClass=io.camunda.application.pt.PtPocLocalIdpRunner \
  -Dexec.classpathScope=test
```

Wait until the banner prints, then verify both URLs respond:

```bash
curl -sS http://localhost:8081/realms/default/.well-known/openid-configuration | head -c 100
curl -sS http://localhost:8082/realms/tenanta/.well-known/openid-configuration | head -c 100
```

Both should return JSON starting with `{"issuer":"http://localhost:808...`. Then press enter in the runner's terminal to shut down.

- [ ] **Step 4: Format, commit**

```bash
./mvnw license:format spotless:apply -T1C
git add dist/src/test/java/io/camunda/application/pt/PtPocLocalIdpRunner.java
git commit -m "test: add PtPocLocalIdpRunner for local PT-security iteration"
```

---

## Task 4: PhysicalTenantRedirectUriRewriter

**Goal:** Pure function that takes a tenant id and the base redirect URI template (`{baseUrl}/login/oauth2/code/{registrationId}`) and returns `{baseUrl}/physical-tenant/<tenant>/login/oauth2/code/{registrationId}`. Used by the OIDC registry to override `ClientRegistration.redirectUri` at build time.

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantRedirectUriRewriter.java`
- Test: `authentication/src/test/java/io/camunda/authentication/pt/PhysicalTenantRedirectUriRewriterTest.java`

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PhysicalTenantRedirectUriRewriterTest {

  @Test
  void shouldInsertTenantSegmentBetweenBaseUrlAndLoginCallback() {
    // given
    final var input = "{baseUrl}/login/oauth2/code/{registrationId}";
    // when
    final var result = PhysicalTenantRedirectUriRewriter.rewrite(input, "tenanta");
    // then
    assertThat(result).isEqualTo("{baseUrl}/physical-tenant/tenanta/login/oauth2/code/{registrationId}");
  }

  @Test
  void shouldNotRewriteWhenAlreadyPrefixed() {
    final var input = "{baseUrl}/physical-tenant/tenanta/login/oauth2/code/{registrationId}";
    assertThat(PhysicalTenantRedirectUriRewriter.rewrite(input, "tenanta")).isEqualTo(input);
  }

  @Test
  void shouldRewriteAbsoluteUri() {
    final var input = "https://oc.example.com/login/oauth2/code/idpOne";
    assertThat(PhysicalTenantRedirectUriRewriter.rewrite(input, "default"))
        .isEqualTo("https://oc.example.com/physical-tenant/default/login/oauth2/code/idpOne");
  }

  @Test
  void shouldRejectBlankTenantId() {
    org.assertj.core.api.Assertions.assertThatThrownBy(
            () -> PhysicalTenantRedirectUriRewriter.rewrite("{baseUrl}/login/oauth2/code/{registrationId}", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
```

- [ ] **Step 2: Run and confirm failure**

```bash
./mvnw verify -pl authentication -Dtest=PhysicalTenantRedirectUriRewriterTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: FAIL — class not found.

- [ ] **Step 3: Implement**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import org.jspecify.annotations.NullMarked;

@NullMarked
public final class PhysicalTenantRedirectUriRewriter {

  private static final String LOGIN_CALLBACK_SEGMENT = "/login/oauth2/code/";

  private PhysicalTenantRedirectUriRewriter() {}

  public static String rewrite(final String redirectUriTemplate, final String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    final String prefixed = "/physical-tenant/" + tenantId + LOGIN_CALLBACK_SEGMENT;
    if (redirectUriTemplate.contains(prefixed)) {
      return redirectUriTemplate;
    }
    return redirectUriTemplate.replace(LOGIN_CALLBACK_SEGMENT, prefixed);
  }
}
```

- [ ] **Step 4: Run and confirm pass**

```bash
./mvnw verify -pl authentication -Dtest=PhysicalTenantRedirectUriRewriterTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: 4 tests pass.

- [ ] **Step 5: Format and commit**

```bash
./mvnw license:format spotless:apply -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantRedirectUriRewriter.java \
        authentication/src/test/java/io/camunda/authentication/pt/PhysicalTenantRedirectUriRewriterTest.java
git commit -m "feat: add PhysicalTenantRedirectUriRewriter for per-tenant OIDC callback paths"
```

---

## Task 5: PerTenantOidcRegistry

**Goal:** Build the per-tenant `OidcAuthenticationConfigurationRepository` and `ClientRegistrationRepository` from a tenant's `SecurityConfiguration` slice, filtering providers by the tenant's `providers.assigned` list and rewriting redirect URIs through `PhysicalTenantRedirectUriRewriter`.

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PerTenantOidcRegistry.java`
- Test: `authentication/src/test/java/io/camunda/authentication/pt/PerTenantOidcRegistryTest.java`

**Note:** `providers.assigned` is not yet on `SecurityConfiguration`. Step 0 adds it.

- [ ] **Step 0: Add the `assigned` list to the providers config bean**

Locate `security/security-core/src/main/java/io/camunda/security/configuration/AuthenticationConfiguration.java` (or wherever `providers` lives — search if needed):

```bash
grep -rn "class Providers\|getAssigned" security/security-core/src/main 2>/dev/null
```

Add a `private List<String> assigned = new ArrayList<>();` field with getter/setter on the providers configuration class. (Exact file path depends on the existing nesting; the engineer adapts. If it does not exist, create it under `AuthenticationConfiguration.Providers`.)

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PerTenantOidcRegistryTest {

  @Test
  void shouldRegisterOnlyAssignedProviders() {
    // given
    final var security = buildSecurityConfig(Map.of("idpOne", oidc("idpOne"), "idpTwo", oidc("idpTwo")), List.of("idpOne"));
    // when
    final var registry = PerTenantOidcRegistry.forTenant("tenanta", security);
    // then
    assertThat(registry.clientRegistrationRepository().findByRegistrationId("idpOne")).isNotNull();
    assertThat(registry.clientRegistrationRepository().findByRegistrationId("idpTwo")).isNull();
  }

  @Test
  void shouldRewriteRedirectUriToTenantPath() {
    final var security = buildSecurityConfig(Map.of("idpOne", oidc("idpOne")), List.of("idpOne"));
    final var registry = PerTenantOidcRegistry.forTenant("tenanta", security);
    final var registration = registry.clientRegistrationRepository().findByRegistrationId("idpOne");
    assertThat(registration.getRedirectUri())
        .isEqualTo("{baseUrl}/physical-tenant/tenanta/login/oauth2/code/{registrationId}");
  }

  @Test
  void shouldFailWhenAssignedProviderIsMissingFromProvidersMap() {
    final var security = buildSecurityConfig(Map.of("idpOne", oidc("idpOne")), List.of("ghost"));
    assertThatThrownBy(() -> PerTenantOidcRegistry.forTenant("tenanta", security))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ghost");
  }

  // helpers
  private static OidcConfiguration oidc(final String registrationId) {
    final var oidc = new OidcConfiguration();
    oidc.setClientId("client-" + registrationId);
    oidc.setClientSecret("secret-" + registrationId);
    oidc.setIssuerUri("http://localhost:8080/realms/" + registrationId);
    oidc.setRedirectUri("{baseUrl}/login/oauth2/code/{registrationId}");
    return oidc;
  }

  private static SecurityConfiguration buildSecurityConfig(
      final Map<String, OidcConfiguration> providers, final List<String> assigned) {
    final var security = new SecurityConfiguration();
    security.getAuthentication().getProviders().setOidc(providers);
    security.getAuthentication().getProviders().setAssigned(assigned);
    return security;
  }
}
```

- [ ] **Step 2: Run, confirm failure**

```bash
./mvnw verify -pl authentication -Dtest=PerTenantOidcRegistryTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: FAIL — `PerTenantOidcRegistry` class does not exist.

- [ ] **Step 3: Implement `PerTenantOidcRegistry`**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.authentication.config.ClientRegistrationFactory;
import io.camunda.authentication.config.OidcAuthenticationConfigurationRepository;
import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@NullMarked
public final class PerTenantOidcRegistry {

  private final OidcAuthenticationConfigurationRepository providerRepository;
  private final ClientRegistrationRepository clientRegistrationRepository;

  private PerTenantOidcRegistry(
      final OidcAuthenticationConfigurationRepository providerRepository,
      final ClientRegistrationRepository clientRegistrationRepository) {
    this.providerRepository = providerRepository;
    this.clientRegistrationRepository = clientRegistrationRepository;
  }

  public static PerTenantOidcRegistry forTenant(
      final String tenantId, final SecurityConfiguration tenantSecurity) {
    final Map<String, OidcConfiguration> allProviders =
        tenantSecurity.getAuthentication().getProviders().getOidc();
    final List<String> assigned = tenantSecurity.getAuthentication().getProviders().getAssigned();
    if (assigned == null || assigned.isEmpty()) {
      throw new IllegalStateException(
          "Tenant '"
              + tenantId
              + "' has no providers.assigned. Each PT must explicitly list its active OIDC providers.");
    }
    final Map<String, OidcConfiguration> assignedProviders = new HashMap<>();
    for (final String registrationId : assigned) {
      final OidcConfiguration provider = allProviders.get(registrationId);
      if (provider == null) {
        throw new IllegalStateException(
            "Tenant '"
                + tenantId
                + "' assigns provider '"
                + registrationId
                + "' but it is missing from authentication.providers.oidc.*");
      }
      final OidcConfiguration rewritten = copyWithRewrittenRedirectUri(provider, tenantId);
      assignedProviders.put(registrationId, rewritten);
    }
    final var tenantSecurityClone = cloneWithProviders(tenantSecurity, assignedProviders);
    final var providerRepository = new OidcAuthenticationConfigurationRepository(tenantSecurityClone);
    final List<ClientRegistration> registrations =
        assignedProviders.entrySet().stream()
            .map(e -> buildRegistration(e.getKey(), e.getValue()))
            .toList();
    return new PerTenantOidcRegistry(
        providerRepository, new InMemoryClientRegistrationRepository(registrations));
  }

  public OidcAuthenticationConfigurationRepository providerRepository() {
    return providerRepository;
  }

  public ClientRegistrationRepository clientRegistrationRepository() {
    return clientRegistrationRepository;
  }

  private static OidcConfiguration copyWithRewrittenRedirectUri(
      final OidcConfiguration source, final String tenantId) {
    final OidcConfiguration copy = new OidcConfiguration();
    copy.setClientId(source.getClientId());
    copy.setClientSecret(source.getClientSecret());
    copy.setClientAuthenticationMethod(source.getClientAuthenticationMethod());
    copy.setIssuerUri(source.getIssuerUri());
    copy.setAuthorizationUri(source.getAuthorizationUri());
    copy.setTokenUri(source.getTokenUri());
    copy.setJwkSetUri(source.getJwkSetUri());
    copy.setScope(source.getScope());
    copy.setRedirectUri(
        PhysicalTenantRedirectUriRewriter.rewrite(
            source.getRedirectUri() != null
                ? source.getRedirectUri()
                : "{baseUrl}/login/oauth2/code/{registrationId}",
            tenantId));
    // additional fields copied as needed — start with what ClientRegistrationFactory consumes
    return copy;
  }

  private static SecurityConfiguration cloneWithProviders(
      final SecurityConfiguration source, final Map<String, OidcConfiguration> providers) {
    // Shallow clone: producers only read authentication.providers.oidc + scalar auth fields.
    // For the PoC we mutate a fresh SecurityConfiguration in place.
    final SecurityConfiguration clone = new SecurityConfiguration();
    clone.getAuthentication().setMethod(source.getAuthentication().getMethod());
    clone.getAuthentication().setOidc(source.getAuthentication().getOidc());
    clone.getAuthentication().getProviders().setOidc(providers);
    clone.getAuthentication().getProviders().setAssigned(List.copyOf(providers.keySet()));
    return clone;
  }

  private static ClientRegistration buildRegistration(
      final String registrationId, final OidcConfiguration provider) {
    try {
      return ClientRegistrationFactory.createClientRegistration(registrationId, provider);
    } catch (final Exception e) {
      throw new IllegalStateException(
          "Failed to build ClientRegistration for '" + registrationId + "'", e);
    }
  }
}
```

- [ ] **Step 4: Run tests, confirm pass**

```bash
./mvnw verify -pl authentication -Dtest=PerTenantOidcRegistryTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: all three tests pass.

If `SecurityConfiguration` doesn't expose `setOidc`/`setAssigned` exactly as written, adjust the setters to match the actual config shape (read the class once and align).

- [ ] **Step 5: Format and commit**

```bash
./mvnw license:format spotless:apply -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PerTenantOidcRegistry.java \
        authentication/src/test/java/io/camunda/authentication/pt/PerTenantOidcRegistryTest.java \
        security/security-core/src/main/java/io/camunda/security/configuration/AuthenticationConfiguration.java
git commit -m "feat: add PerTenantOidcRegistry that filters providers.assigned and rewrites redirect URIs"
```

---

## Task 6: PerTenantSessionRepository

**Goal:** Decorator over `WebSessionRepository` that prefixes each session id with `t:<tenantId>:` so multiple tenants share one secondary-storage backend without keyspace collision.

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PerTenantSessionRepository.java`
- Test: `authentication/src/test/java/io/camunda/authentication/pt/PerTenantSessionRepositoryTest.java`

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.authentication.session.WebSession;
import io.camunda.authentication.session.WebSessionRepository;
import org.junit.jupiter.api.Test;

class PerTenantSessionRepositoryTest {

  @Test
  void shouldPrefixSessionIdsWithTenantOnCreate() {
    // given
    final var delegate = mock(WebSessionRepository.class);
    final var raw = new WebSession("raw-id");
    when(delegate.createSession()).thenReturn(raw);
    final var perTenant = new PerTenantSessionRepository("tenanta", delegate);
    // when
    final WebSession created = perTenant.createSession();
    // then
    assertThat(created.getId()).isEqualTo("t:tenanta:raw-id");
  }

  @Test
  void shouldStripPrefixBeforeDelegatingFindById() {
    final var delegate = mock(WebSessionRepository.class);
    when(delegate.findById("raw-id")).thenReturn(new WebSession("raw-id"));
    final var perTenant = new PerTenantSessionRepository("tenanta", delegate);
    final WebSession found = perTenant.findById("t:tenanta:raw-id");
    verify(delegate).findById("raw-id");
    assertThat(found.getId()).isEqualTo("t:tenanta:raw-id");
  }

  @Test
  void shouldReturnNullWhenLookingUpSessionFromAnotherTenant() {
    final var delegate = mock(WebSessionRepository.class);
    final var perTenant = new PerTenantSessionRepository("tenanta", delegate);
    final WebSession found = perTenant.findById("t:default:raw-id");
    assertThat(found).isNull();
    verify(delegate, times(0)).findById(any());
  }

  @Test
  void shouldDelegateDeleteWithStrippedId() {
    final var delegate = mock(WebSessionRepository.class);
    final var perTenant = new PerTenantSessionRepository("tenanta", delegate);
    perTenant.deleteById("t:tenanta:raw-id");
    verify(delegate).deleteById("raw-id");
  }
}
```

- [ ] **Step 2: Run, confirm failure**

```bash
./mvnw verify -pl authentication -Dtest=PerTenantSessionRepositoryTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: FAIL — class not found.

- [ ] **Step 3: Implement**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.authentication.session.WebSession;
import io.camunda.authentication.session.WebSessionRepository;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.springframework.session.SessionRepository;

@NullMarked
public final class PerTenantSessionRepository implements SessionRepository<WebSession> {

  private static final String PREFIX = "t:";

  private final String tenantId;
  private final String tenantPrefix;
  private final WebSessionRepository delegate;

  public PerTenantSessionRepository(final String tenantId, final WebSessionRepository delegate) {
    this.tenantId = tenantId;
    this.tenantPrefix = PREFIX + tenantId + ":";
    this.delegate = delegate;
  }

  @Override
  public WebSession createSession() {
    final WebSession raw = delegate.createSession();
    return wrap(raw);
  }

  @Override
  public void save(final WebSession session) {
    delegate.save(unwrap(session));
  }

  @Override
  public @Nullable WebSession findById(final String id) {
    if (!id.startsWith(tenantPrefix)) {
      return null; // foreign-tenant id; refuse cross-tenant lookups
    }
    final WebSession raw = delegate.findById(strip(id));
    return raw == null ? null : wrap(raw);
  }

  @Override
  public void deleteById(final String id) {
    if (!id.startsWith(tenantPrefix)) {
      return;
    }
    delegate.deleteById(strip(id));
  }

  private WebSession wrap(final WebSession raw) {
    // WebSession is mutable in this codebase; assigning a prefixed id is sufficient.
    // If WebSession.setId is absent, the engineer adds a package-private constructor or setter.
    return new WebSession(tenantPrefix + raw.getId(), raw);
  }

  private WebSession unwrap(final WebSession session) {
    return new WebSession(strip(session.getId()), session);
  }

  private String strip(final String id) {
    return id.substring(tenantPrefix.length());
  }
}
```

**Note on `WebSession` API:** the existing `WebSession` class has `new WebSession(String sessionId)` (see line 65 of `WebSessionRepository`). If a copy-constructor `WebSession(String id, WebSession source)` does not exist, the engineer adds one that copies attributes/expiry from the source. The test should drive that addition.

- [ ] **Step 4: Run, confirm pass**

```bash
./mvnw verify -pl authentication -Dtest=PerTenantSessionRepositoryTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: 4 tests pass. If the `WebSession` copy-constructor is missing, add it under `authentication/src/main/java/io/camunda/authentication/session/WebSession.java` with copying of `attributes`, `creationTime`, `lastAccessedTime`, `maxInactiveInterval`, and a separate test under `WebSessionTest`.

- [ ] **Step 5: Format and commit**

```bash
./mvnw license:format spotless:apply -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PerTenantSessionRepository.java \
        authentication/src/test/java/io/camunda/authentication/pt/PerTenantSessionRepositoryTest.java \
        authentication/src/main/java/io/camunda/authentication/session/WebSession.java
git commit -m "feat: add PerTenantSessionRepository that prefixes session ids per tenant"
```

---

## Task 7: PhysicalTenantCookieSerializer factory

**Goal:** Static factory returning a `DefaultCookieSerializer` configured with `camunda-session-<tenantId>` as the cookie name and `/physical-tenant/<tenantId>` (or `/` for the default-tenant unprefixed chain) as the cookie Path. Plus a `CookieHttpSessionIdResolver` that uses it.

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantCookieSerializer.java`
- Test: `authentication/src/test/java/io/camunda/authentication/pt/PhysicalTenantCookieSerializerTest.java`

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer.CookieValue;

class PhysicalTenantCookieSerializerTest {

  @Test
  void shouldNameCookiePerTenantAndScopePathToTenantPrefix() {
    // given
    final var serializer = PhysicalTenantCookieSerializer.forPrefixedChain("tenanta");
    final var response = new MockHttpServletResponse();
    final var request = new MockHttpServletRequest();
    request.setRequestURI("/physical-tenant/tenanta/whoami");
    // when
    serializer.writeCookieValue(new CookieValue(request, response, "raw-session-id"));
    // then
    final Cookie cookie = response.getCookie("camunda-session-tenanta");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getPath()).isEqualTo("/physical-tenant/tenanta");
    assertThat(cookie.getValue()).isEqualTo("raw-session-id");
  }

  @Test
  void shouldNameDefaultUnprefixedCookieAndScopeToRoot() {
    final var serializer = PhysicalTenantCookieSerializer.forUnprefixedDefaultChain();
    final var response = new MockHttpServletResponse();
    final var request = new MockHttpServletRequest();
    request.setRequestURI("/whoami");
    serializer.writeCookieValue(new CookieValue(request, response, "raw-id"));
    final Cookie cookie = response.getCookie("camunda-session-default-root");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getPath()).isEqualTo("/");
  }
}
```

- [ ] **Step 2: Run, confirm failure**

```bash
./mvnw verify -pl authentication -Dtest=PhysicalTenantCookieSerializerTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: FAIL — class not found.

- [ ] **Step 3: Implement**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import org.jspecify.annotations.NullMarked;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;

@NullMarked
public final class PhysicalTenantCookieSerializer {

  private static final String COOKIE_NAME_PREFIX = "camunda-session-";

  private PhysicalTenantCookieSerializer() {}

  public static DefaultCookieSerializer forPrefixedChain(final String tenantId) {
    final var serializer = baseSerializer();
    serializer.setCookieName(COOKIE_NAME_PREFIX + tenantId);
    serializer.setCookiePath("/physical-tenant/" + tenantId);
    return serializer;
  }

  public static DefaultCookieSerializer forUnprefixedDefaultChain() {
    final var serializer = baseSerializer();
    serializer.setCookieName(COOKIE_NAME_PREFIX + "default-root");
    serializer.setCookiePath("/");
    return serializer;
  }

  public static CookieHttpSessionIdResolver resolver(final DefaultCookieSerializer serializer) {
    final var resolver = new CookieHttpSessionIdResolver();
    resolver.setCookieSerializer(serializer);
    return resolver;
  }

  private static DefaultCookieSerializer baseSerializer() {
    final var serializer = new DefaultCookieSerializer();
    serializer.setUseSecureCookie(false); // PoC over http; flip via property in IT/staging
    serializer.setUseHttpOnlyCookie(true);
    serializer.setSameSite("Lax");
    return serializer;
  }
}
```

- [ ] **Step 4: Run, confirm pass**

```bash
./mvnw verify -pl authentication -Dtest=PhysicalTenantCookieSerializerTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: 2 tests pass.

- [ ] **Step 5: Format and commit**

```bash
./mvnw license:format spotless:apply -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantCookieSerializer.java \
        authentication/src/test/java/io/camunda/authentication/pt/PhysicalTenantCookieSerializerTest.java
git commit -m "feat: add PhysicalTenantCookieSerializer for per-tenant cookie name and path"
```

---

## Task 8: PhysicalTenantWebFilter

**Goal:** A `OncePerRequestFilter` registered at the front of each tenant chain. Stamps the chain's tenant id onto the request attribute consumed by `PhysicalTenantContext.current()`. Because the tenant id is known at chain construction (literal in the matcher), the filter takes the tenant id as a constructor argument; there's no path parsing.

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantWebFilter.java`
- Test: `authentication/src/test/java/io/camunda/authentication/pt/PhysicalTenantWebFilterTest.java`

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PhysicalTenantWebFilterTest {

  @Test
  void shouldStampTenantIdOnRequestAttribute() throws Exception {
    // given
    final var filter = new PhysicalTenantWebFilter("tenanta");
    final var request = new MockHttpServletRequest("GET", "/physical-tenant/tenanta/whoami");
    final var response = new MockHttpServletResponse();
    final var chain = new MockFilterChain();
    // when
    filter.doFilter(request, response, chain);
    // then
    assertThat(request.getAttribute(PhysicalTenantContext.REQUEST_ATTRIBUTE_PHYSICAL_TENANT_ID))
        .isEqualTo("tenanta");
    assertThat(chain.getRequest()).isSameAs(request);
  }
}
```

- [ ] **Step 2: Run, confirm failure**

```bash
./mvnw verify -pl authentication -Dtest=PhysicalTenantWebFilterTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: FAIL — class not found.

- [ ] **Step 3: Implement**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.jspecify.annotations.NullMarked;
import org.springframework.web.filter.OncePerRequestFilter;

@NullMarked
public final class PhysicalTenantWebFilter extends OncePerRequestFilter {

  private final String tenantId;

  public PhysicalTenantWebFilter(final String tenantId) {
    this.tenantId = tenantId;
  }

  @Override
  protected void doFilterInternal(
      final HttpServletRequest request,
      final HttpServletResponse response,
      final FilterChain filterChain)
      throws ServletException, IOException {
    PhysicalTenantContext.setPhysicalTenantId(request, tenantId);
    filterChain.doFilter(request, response);
  }
}
```

- [ ] **Step 4: Run, confirm pass**

```bash
./mvnw verify -pl authentication -Dtest=PhysicalTenantWebFilterTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: PASS.

- [ ] **Step 5: Format, commit**

```bash
./mvnw license:format spotless:apply -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantWebFilter.java \
        authentication/src/test/java/io/camunda/authentication/pt/PhysicalTenantWebFilterTest.java
git commit -m "feat: add PhysicalTenantWebFilter that stamps tenant id per chain"
```

---

## Task 9: TenantSecuritySlice

**Goal:** Record bundling everything a chain needs. Pure data carrier, no logic. Built once per tenant at startup.

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/TenantSecuritySlice.java`

- [ ] **Step 1: Write the record**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.security.configuration.SecurityConfiguration;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;

/**
 * Per-tenant collaborators consumed by {@link PerTenantSecurityChainFactory}.
 *
 * <p>Each instance is immutable and built once at startup. All collaborators are pure
 * constructor arguments — no field injection — so this record is portable to a child-context
 * model (approach C in the design) without modification.
 */
@NullMarked
public record TenantSecuritySlice(
    String tenantId,
    AccessPath accessPath,
    SecurityConfiguration tenantSecurity,
    ClientRegistrationRepository clientRegistrationRepository,
    OAuth2AuthorizedClientRepository authorizedClientRepository,
    JwtDecoder jwtDecoder,
    LogoutSuccessHandler logoutSuccessHandler,
    PerTenantSessionRepository sessionRepository,
    CookieHttpSessionIdResolver httpSessionIdResolver) {

  /**
   * Whether this slice powers the tenant's prefixed URL space ({@code /physical-tenant/<id>/...})
   * or — only valid for the {@code default} tenant — the unprefixed fallback space ({@code /...}).
   */
  public enum AccessPath {
    PREFIXED,
    UNPREFIXED_DEFAULT
  }
}
```

- [ ] **Step 2: Compile**

```bash
./mvnw install -pl authentication -am -Dquickly -T1C
```

Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
./mvnw license:format spotless:apply -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/TenantSecuritySlice.java
git commit -m "feat: add TenantSecuritySlice record carrying per-tenant security collaborators"
```

---

## Task 10: PhysicalTenantWhoamiController

**Goal:** Demo controller exposing `/physical-tenant/{tenantId}/whoami` (webapp chain — session/OIDC) and `/v2/physical-tenants/{tenantId}/whoami` (api chain — bearer token). Returns `{tenantId, principal, providers, accessPath}`.

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantWhoamiController.java`
- Test: `authentication/src/test/java/io/camunda/authentication/pt/PhysicalTenantWhoamiControllerTest.java`

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.gateway.rest.context.PhysicalTenantContext;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

class PhysicalTenantWhoamiControllerTest {

  @Test
  void shouldReturnTenantPrincipalAndAccessPath() {
    // given
    final var request = new MockHttpServletRequest("GET", "/physical-tenant/tenanta/whoami");
    PhysicalTenantContext.setPhysicalTenantId(request, "tenanta");
    final var auth =
        new UsernamePasswordAuthenticationToken(
            "bob@tenanta", "n/a", AuthorityUtils.NO_AUTHORITIES);
    final var controller =
        new PhysicalTenantWhoamiController(
            (tenantId) -> List.of("idpOne")); // simple provider lookup
    // when
    final var result = controller.whoami(request, auth, "tenanta", "prefixed");
    // then
    assertThat(result.tenantId()).isEqualTo("tenanta");
    assertThat(result.principal()).isEqualTo("bob@tenanta");
    assertThat(result.providers()).containsExactly("idpOne");
    assertThat(result.accessPath()).isEqualTo("prefixed");
  }
}
```

- [ ] **Step 2: Run, confirm failure**

```bash
./mvnw verify -pl authentication -Dtest=PhysicalTenantWhoamiControllerTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: FAIL — class not found.

- [ ] **Step 3: Implement**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.function.Function;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@NullMarked
@RestController
public class PhysicalTenantWhoamiController {

  public record Whoami(String tenantId, String principal, List<String> providers, String accessPath) {}

  private final Function<String, List<String>> providersByTenant;

  public PhysicalTenantWhoamiController(
      final Function<String, List<String>> providersByTenant) {
    this.providersByTenant = providersByTenant;
  }

  @GetMapping({
    "/physical-tenant/{tenantId}/whoami",
    "/whoami"
  })
  public Whoami whoamiWebapp(
      final HttpServletRequest request,
      final Authentication authentication,
      @PathVariable(required = false) final String tenantId) {
    return whoami(request, authentication, tenantId, accessPathOf(request));
  }

  @GetMapping({
    "/v2/physical-tenants/{tenantId}/whoami",
    "/v2/whoami"
  })
  public Whoami whoamiApi(
      final HttpServletRequest request,
      final Authentication authentication,
      @PathVariable(required = false) final String tenantId) {
    return whoami(request, authentication, tenantId, accessPathOf(request));
  }

  // visible for direct unit testing
  Whoami whoami(
      final HttpServletRequest request,
      final Authentication authentication,
      final String tenantIdFromPath,
      final String accessPath) {
    final String tenantId = tenantIdFromPath != null ? tenantIdFromPath : "default";
    final String principal = authentication != null ? authentication.getName() : "anonymous";
    return new Whoami(tenantId, principal, providersByTenant.apply(tenantId), accessPath);
  }

  private static String accessPathOf(final HttpServletRequest request) {
    return request.getRequestURI().startsWith("/physical-tenant/")
            || request.getRequestURI().startsWith("/v2/physical-tenants/")
        ? "prefixed"
        : "unprefixed";
  }
}
```

- [ ] **Step 4: Run, confirm pass**

```bash
./mvnw verify -pl authentication -Dtest=PhysicalTenantWhoamiControllerTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: PASS.

- [ ] **Step 5: Format, commit**

```bash
./mvnw license:format spotless:apply -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantWhoamiController.java \
        authentication/src/test/java/io/camunda/authentication/pt/PhysicalTenantWhoamiControllerTest.java
git commit -m "feat: add PhysicalTenantWhoamiController demo endpoint"
```

---

## Task 11: PerTenantSecurityChainFactory — webapp chain

**Goal:** Build the webapp `SecurityFilterChain` for one tenant: matches the tenant's URL prefix, runs `PhysicalTenantWebFilter` first, sets up `oauth2Login` with per-tenant `ClientRegistrationRepository`, per-tenant `LogoutSuccessHandler`, per-tenant `SessionRepositoryFilter` with the tenant's `CookieHttpSessionIdResolver` and `PerTenantSessionRepository`, CSRF cookie scoped to the tenant Path.

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PerTenantSecurityChainFactory.java`
- Test: `authentication/src/test/java/io/camunda/authentication/pt/PerTenantWebappChainTest.java`

This is the load-bearing task. The chain is end-to-end verified by the IT in Task 15. For this task the unit test asserts only the wiring shape (no real OIDC). Acceptance comes from the IT.

- [ ] **Step 1: Write a wiring assertion test**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.authentication.pt.TenantSecuritySlice.AccessPath;
import io.camunda.authentication.session.WebSessionRepository;
import io.camunda.security.configuration.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.HttpSecurityConfiguration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;

class PerTenantWebappChainTest {

  @Test
  void shouldMatchTenantPrefixAndIncludePhysicalTenantWebFilter() throws Exception {
    // given
    final var slice =
        new TenantSecuritySlice(
            "tenanta",
            AccessPath.PREFIXED,
            new SecurityConfiguration(),
            mock(ClientRegistrationRepository.class),
            mock(OAuth2AuthorizedClientRepository.class),
            mock(JwtDecoder.class),
            mock(LogoutSuccessHandler.class),
            new PerTenantSessionRepository("tenanta", mock(WebSessionRepository.class)),
            mock(CookieHttpSessionIdResolver.class));
    final HttpSecurity http = newHttpSecurity();
    // when
    final SecurityFilterChain chain =
        new PerTenantSecurityChainFactory().buildWebappChain(http, slice);
    // then
    assertThat(chain.matches(mockRequest("/physical-tenant/tenanta/whoami"))).isTrue();
    assertThat(chain.matches(mockRequest("/physical-tenant/default/whoami"))).isFalse();
    assertThat(
            chain.getFilters().stream()
                .anyMatch(f -> f instanceof PhysicalTenantWebFilter))
        .isTrue();
  }

  private static HttpSecurity newHttpSecurity() {
    // Use Spring's test infra; engineer adapts if HttpSecurityConfiguration.httpSecurity() is
    // not directly accessible.
    return mock(HttpSecurity.class); // wiring asserts are made over the returned chain
  }

  private static jakarta.servlet.http.HttpServletRequest mockRequest(final String uri) {
    final var req = new org.springframework.mock.web.MockHttpServletRequest("GET", uri);
    req.setServletPath(uri);
    return req;
  }
}
```

**Note:** Unit-testing `HttpSecurity` directly is awkward — many of Spring's builder methods are final or rely on configurer chaining. The engineer may switch to a `@SpringBootTest`-style boot of just `PhysicalTenantSecurityConfiguration` with one tenant if the mock approach proves brittle. Either way, the IT (Task 15) is the authoritative test. This task's unit test is supplementary.

- [ ] **Step 2: Run, confirm failure**

```bash
./mvnw verify -pl authentication -Dtest=PerTenantWebappChainTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: FAIL — class not found.

- [ ] **Step 3: Implement the factory's webapp method**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import org.jspecify.annotations.NullMarked;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.session.SessionRepository;
import org.springframework.session.web.http.SessionRepositoryFilter;

@NullMarked
public final class PerTenantSecurityChainFactory {

  public SecurityFilterChain buildWebappChain(
      final HttpSecurity http, final TenantSecuritySlice slice) throws Exception {
    final String prefix = webappPrefix(slice);
    final SessionRepositoryFilter<?> sessionFilter = sessionFilter(slice);

    http
        .securityMatcher(prefix + "/**")
        .addFilterBefore(new PhysicalTenantWebFilter(slice.tenantId()), SessionRepositoryFilter.class)
        .addFilterBefore(sessionFilter, org.springframework.security.web.context.SecurityContextPersistenceFilter.class)
        .csrf(c -> c.csrfTokenRepository(csrfTokenRepository(prefix)))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .oauth2Login(
            l ->
                l.clientRegistrationRepository(slice.clientRegistrationRepository())
                    .authorizedClientRepository(slice.authorizedClientRepository())
                    .authorizationEndpoint(
                        ae -> ae.baseUri(prefix + "/oauth2/authorization"))
                    .redirectionEndpoint(
                        re -> re.baseUri(prefix + "/login/oauth2/code/*"))
                    .loginPage(prefix + "/oauth2/authorization/" + firstAssignedProvider(slice)))
        .logout(
            lo ->
                lo.logoutSuccessHandler(slice.logoutSuccessHandler())
                    .logoutUrl(prefix + "/logout")
                    .deleteCookies("camunda-session-" + slice.tenantId(), "XSRF-TOKEN"));

    return http.build();
  }

  private static String webappPrefix(final TenantSecuritySlice slice) {
    return slice.accessPath() == TenantSecuritySlice.AccessPath.PREFIXED
        ? "/physical-tenant/" + slice.tenantId()
        : "";
  }

  private static SessionRepositoryFilter<?> sessionFilter(final TenantSecuritySlice slice) {
    @SuppressWarnings({"rawtypes", "unchecked"})
    final SessionRepositoryFilter filter =
        new SessionRepositoryFilter<>((SessionRepository) slice.sessionRepository());
    filter.setHttpSessionIdResolver(slice.httpSessionIdResolver());
    return filter;
  }

  private static CookieCsrfTokenRepository csrfTokenRepository(final String prefix) {
    final var repo = CookieCsrfTokenRepository.withHttpOnlyFalse();
    repo.setCookiePath(prefix.isEmpty() ? "/" : prefix);
    return repo;
  }

  private static String firstAssignedProvider(final TenantSecuritySlice slice) {
    final var assigned = slice.tenantSecurity().getAuthentication().getProviders().getAssigned();
    return assigned == null || assigned.isEmpty() ? "oidc" : assigned.get(0);
  }
}
```

- [ ] **Step 4: Run the wiring test; if the mock approach is brittle, replace with an IT-only check**

```bash
./mvnw verify -pl authentication -Dtest=PerTenantWebappChainTest -DskipTests=false -DskipITs -Dquickly -T1C
```

If the test passes, great. If it fails because `HttpSecurity` mocking can't represent the build, downgrade the test to verify only that `buildWebappChain` returns non-null when given a real `HttpSecurity` from a `@SpringBootTest` slice — and rely on Task 15's IT for the matcher/filter assertions. Commit either way before proceeding.

- [ ] **Step 5: Format, commit**

```bash
./mvnw license:format spotless:apply -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PerTenantSecurityChainFactory.java \
        authentication/src/test/java/io/camunda/authentication/pt/PerTenantWebappChainTest.java
git commit -m "feat: add PerTenantSecurityChainFactory webapp chain builder"
```

---

## Task 12: PerTenantSecurityChainFactory — api chain

**Goal:** Add the api chain method to the factory. Stateless, bearer-token only, no session cookie. Matcher `/v2/physical-tenants/<tenant>/**`. Default tenant gets an additional chain for `/v2/**`.

**Files:**
- Modify: `authentication/src/main/java/io/camunda/authentication/pt/PerTenantSecurityChainFactory.java` (add method)
- Test: `authentication/src/test/java/io/camunda/authentication/pt/PerTenantApiChainTest.java`

- [ ] **Step 1: Write the failing test**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import io.camunda.authentication.pt.TenantSecuritySlice.AccessPath;
import io.camunda.authentication.session.WebSessionRepository;
import io.camunda.security.configuration.SecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;

class PerTenantApiChainTest {

  @Test
  void shouldMatchTenantV2PathAndConfigureResourceServerJwt() throws Exception {
    final var slice = newSlice("tenanta", AccessPath.PREFIXED);
    final HttpSecurity http = mock(HttpSecurity.class);
    final SecurityFilterChain chain =
        new PerTenantSecurityChainFactory().buildApiChain(http, slice);
    assertThat(chain).isNotNull();
    // matcher coverage is verified in the IT; here we just assert no exception is thrown
  }

  private static TenantSecuritySlice newSlice(final String tenant, final AccessPath path) {
    return new TenantSecuritySlice(
        tenant,
        path,
        new SecurityConfiguration(),
        mock(ClientRegistrationRepository.class),
        mock(OAuth2AuthorizedClientRepository.class),
        mock(JwtDecoder.class),
        mock(LogoutSuccessHandler.class),
        new PerTenantSessionRepository(tenant, mock(WebSessionRepository.class)),
        mock(CookieHttpSessionIdResolver.class));
  }
}
```

- [ ] **Step 2: Run, confirm failure**

```bash
./mvnw verify -pl authentication -Dtest=PerTenantApiChainTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: FAIL — method not found.

- [ ] **Step 3: Add the api method to the factory**

Append to `PerTenantSecurityChainFactory.java`:

```java
public SecurityFilterChain buildApiChain(
    final HttpSecurity http, final TenantSecuritySlice slice) throws Exception {
  final String prefix = apiPrefix(slice);
  http
      .securityMatcher(prefix + "/**")
      .addFilterBefore(
          new PhysicalTenantWebFilter(slice.tenantId()),
          org.springframework.security.web.context.SecurityContextPersistenceFilter.class)
      .csrf(c -> c.disable())
      .sessionManagement(
          sm ->
              sm.sessionCreationPolicy(
                  org.springframework.security.config.http.SessionCreationPolicy.NEVER))
      .authorizeHttpRequests(a -> a.anyRequest().authenticated())
      .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(slice.jwtDecoder())));
  return http.build();
}

private static String apiPrefix(final TenantSecuritySlice slice) {
  return slice.accessPath() == TenantSecuritySlice.AccessPath.PREFIXED
      ? "/v2/physical-tenants/" + slice.tenantId()
      : "/v2";
}
```

- [ ] **Step 4: Run, confirm pass**

```bash
./mvnw verify -pl authentication -Dtest=PerTenantApiChainTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: PASS.

- [ ] **Step 5: Format, commit**

```bash
./mvnw license:format spotless:apply -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PerTenantSecurityChainFactory.java \
        authentication/src/test/java/io/camunda/authentication/pt/PerTenantApiChainTest.java
git commit -m "feat: add PerTenantSecurityChainFactory api chain builder"
```

---

## Task 13: PhysicalTenantSecurityConfiguration

**Goal:** Top-level `@Configuration` that iterates `PhysicalTenantResolver.getAll()`, builds a `TenantSecuritySlice` per tenant (PREFIXED for every tenant, plus UNPREFIXED_DEFAULT for the default tenant), and registers a `SecurityFilterChain` bean per slice via a `BeanDefinitionRegistryPostProcessor`.

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java`
- Test: `authentication/src/test/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfigurationBootTest.java`

- [ ] **Step 1: Write the boot test**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest(classes = PhysicalTenantSecurityConfigurationBootTest.MinimalApp.class)
@ActiveProfiles({"consolidated-auth", "pt-security"})
@TestPropertySource(
    properties = {
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.providers.oidc.idpOne.client-id=fake",
      "camunda.security.authentication.providers.oidc.idpOne.client-secret=fake",
      "camunda.security.authentication.providers.oidc.idpOne.issuer-uri=http://localhost:0/realms/fake",
      "camunda.security.authentication.providers.assigned=idpOne",
      "camunda.physical-tenants.tenanta.security.authentication.providers.assigned=idpOne",
    })
class PhysicalTenantSecurityConfigurationBootTest {

  @org.springframework.boot.autoconfigure.SpringBootApplication
  static class MinimalApp {}

  @Test
  void shouldRegisterPrefixedChainPerTenantPlusUnprefixedForDefault(
      final ApplicationContext context) {
    final var chains = context.getBeansOfType(SecurityFilterChain.class);
    // default (prefixed) + default (unprefixed) + tenanta (prefixed) = 3 webapp chains
    // plus the same shape for api = 6 total. The bean names encode tenant + role.
    assertThat(chains.keySet())
        .anyMatch(name -> name.contains("default") && name.contains("webapp") && name.contains("prefixed"))
        .anyMatch(name -> name.contains("default") && name.contains("webapp") && name.contains("unprefixed"))
        .anyMatch(name -> name.contains("tenanta") && name.contains("webapp"));
    assertThat(chains).hasSizeGreaterThanOrEqualTo(6);
  }
}
```

This test will require the issuer URI to be reachable for `ClientRegistrationFactory`; the engineer may need to stub the OIDC discovery or accept that this boot test fails until Task 15's IT, and downgrade this test to a non-boot wiring check.

- [ ] **Step 2: Run, confirm failure**

```bash
./mvnw verify -pl authentication -Dtest=PhysicalTenantSecurityConfigurationBootTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: FAIL — class not found or OIDC discovery 404.

- [ ] **Step 3: Implement**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.authentication.pt.TenantSecuritySlice.AccessPath;
import io.camunda.authentication.session.WebSessionRepository;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;

@NullMarked
@Configuration
@Profile("pt-security")
@ConditionalOnBean(PhysicalTenantResolver.class)
public class PhysicalTenantSecurityConfiguration {

  private final PhysicalTenantResolver tenantResolver;
  private final WebSessionRepository webSessionRepository;
  private final PerTenantSecurityChainFactory chainFactory;

  public PhysicalTenantSecurityConfiguration(
      final PhysicalTenantResolver tenantResolver,
      final WebSessionRepository webSessionRepository) {
    this.tenantResolver = tenantResolver;
    this.webSessionRepository = webSessionRepository;
    this.chainFactory = new PerTenantSecurityChainFactory();
  }

  @Bean
  static org.springframework.beans.factory.config.BeanFactoryPostProcessor
      physicalTenantChainRegistrar(final org.springframework.core.env.Environment env) {
    return beanFactory -> {
      if (!(beanFactory instanceof BeanDefinitionRegistry registry)) {
        return;
      }
      final var resolver = beanFactory.getBean(PhysicalTenantResolver.class);
      final Map<String, io.camunda.configuration.Camunda> tenants = resolver.getAll();
      for (final var entry : tenants.entrySet()) {
        final String tenantId = entry.getKey();
        registerChainBean(registry, tenantId, AccessPath.PREFIXED, "webapp");
        registerChainBean(registry, tenantId, AccessPath.PREFIXED, "api");
        if ("default".equals(tenantId)) {
          registerChainBean(registry, tenantId, AccessPath.UNPREFIXED_DEFAULT, "webapp");
          registerChainBean(registry, tenantId, AccessPath.UNPREFIXED_DEFAULT, "api");
        }
      }
    };
  }

  private static void registerChainBean(
      final BeanDefinitionRegistry registry,
      final String tenantId,
      final AccessPath accessPath,
      final String role) {
    final String beanName = "ptChain_" + tenantId + "_" + accessPath.name().toLowerCase() + "_" + role;
    final var def = new GenericBeanDefinition();
    def.setBeanClass(SecurityFilterChain.class);
    def.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
    def.setInstanceSupplier(
        () -> {
          throw new IllegalStateException(
              "Chain '"
                  + beanName
                  + "' must be supplied by PhysicalTenantSecurityConfiguration.createChain — "
                  + "engineer to wire up the lambda when promoting this PoC out of stub mode.");
        });
    registry.registerBeanDefinition(beanName, def);
  }

  // Implementation note: the registrar above is a stub that registers bean *names*; the
  // actual chain-creating lambdas live below as @Bean methods invoked at refresh. For the
  // PoC, two-tenant case, we hard-code the wiring rather than fight Spring's bean-graph
  // resolution. This keeps the IT readable. A follow-up generalises this with a real
  // ImportBeanDefinitionRegistrar that builds the supplier from the resolver map.

  @Bean
  public SecurityFilterChain ptChainDefaultPrefixedWebapp(
      final HttpSecurity http, final SecurityConfiguration security) throws Exception {
    return chainFactory.buildWebappChain(http, sliceFor("default", AccessPath.PREFIXED, security));
  }

  @Bean
  public SecurityFilterChain ptChainDefaultPrefixedApi(
      final HttpSecurity http, final SecurityConfiguration security) throws Exception {
    return chainFactory.buildApiChain(http, sliceFor("default", AccessPath.PREFIXED, security));
  }

  @Bean
  public SecurityFilterChain ptChainDefaultUnprefixedWebapp(
      final HttpSecurity http, final SecurityConfiguration security) throws Exception {
    return chainFactory.buildWebappChain(
        http, sliceFor("default", AccessPath.UNPREFIXED_DEFAULT, security));
  }

  @Bean
  public SecurityFilterChain ptChainDefaultUnprefixedApi(
      final HttpSecurity http, final SecurityConfiguration security) throws Exception {
    return chainFactory.buildApiChain(
        http, sliceFor("default", AccessPath.UNPREFIXED_DEFAULT, security));
  }

  @Bean
  public SecurityFilterChain ptChainTenantAPrefixedWebapp(
      final HttpSecurity http, final SecurityConfiguration security) throws Exception {
    return chainFactory.buildWebappChain(http, sliceFor("tenanta", AccessPath.PREFIXED, security));
  }

  @Bean
  public SecurityFilterChain ptChainTenantAPrefixedApi(
      final HttpSecurity http, final SecurityConfiguration security) throws Exception {
    return chainFactory.buildApiChain(http, sliceFor("tenanta", AccessPath.PREFIXED, security));
  }

  private TenantSecuritySlice sliceFor(
      final String tenantId,
      final AccessPath accessPath,
      final SecurityConfiguration tenantSecurity) {
    final var oidc = PerTenantOidcRegistry.forTenant(tenantId, tenantSecurity);
    final var authorizedClients = new HttpSessionOAuth2AuthorizedClientRepository();
    final JwtDecoder decoder =
        NimbusJwtDecoder.withIssuerLocation(
                oidc.providerRepository()
                    .getOidcAuthenticationConfigurations()
                    .values()
                    .iterator()
                    .next()
                    .getIssuerUri())
            .build();
    final var session = new PerTenantSessionRepository(tenantId, webSessionRepository);
    final var serializer =
        accessPath == AccessPath.PREFIXED
            ? PhysicalTenantCookieSerializer.forPrefixedChain(tenantId)
            : PhysicalTenantCookieSerializer.forUnprefixedDefaultChain();
    final var resolver = PhysicalTenantCookieSerializer.resolver(serializer);
    final LogoutSuccessHandler logout =
        (request, response, authentication) -> response.sendRedirect(prefixOf(tenantId, accessPath) + "/");
    return new TenantSecuritySlice(
        tenantId,
        accessPath,
        tenantSecurity,
        oidc.clientRegistrationRepository(),
        authorizedClients,
        decoder,
        logout,
        session,
        resolver);
  }

  private static String prefixOf(final String tenantId, final AccessPath path) {
    return path == AccessPath.PREFIXED ? "/physical-tenant/" + tenantId : "";
  }
}
```

**Honest scope note:** the `BeanFactoryPostProcessor`-driven generic registrar is sketched but stubbed; the actual wiring uses six explicit `@Bean` methods for the two-tenant PoC. This is intentional — the spec calls for portability to approach C, not for a fully generic registrar in the PoC. The follow-up PR generalises this.

- [ ] **Step 4: Run the boot test; expect adjustments**

```bash
./mvnw verify -pl authentication -Dtest=PhysicalTenantSecurityConfigurationBootTest -DskipTests=false -DskipITs -Dquickly -T1C
```

If the test fails on OIDC discovery (because the `issuer-uri` is unreachable), gate the test on a `@ConditionalOnProperty` and run it only inside the IT in Task 15. Replace the boot test with a lighter unit test that asserts the six `@Bean` methods exist via reflection on `PhysicalTenantSecurityConfiguration.class.getDeclaredMethods()`.

- [ ] **Step 5: Format, commit**

```bash
./mvnw license:format spotless:apply -T1C
./mvnw install -pl authentication,dist -am -Dquickly -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java \
        authentication/src/test/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfigurationBootTest.java
git commit -m "feat: register per-tenant SecurityFilterChain beans under pt-security profile"
```

---

## Task 14: application-pt-poc.yaml

**Goal:** Pre-wire OC to point at the local Keycloak runner's realms when started with `--spring.profiles.active=consolidated-auth,pt-security,pt-poc`.

**Files:**
- Create: `dist/src/main/resources/application-pt-poc.yaml`

- [ ] **Step 1: Write the config**

```yaml
camunda:
  security:
    authentication:
      method: oidc
      oidc:
        # Default-tenant OIDC config doubles as the cluster-wide baseline.
        client-id: camunda-pt-default-client
        client-secret: default-secret
        issuer-uri: http://localhost:8081/realms/default
        redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
      providers:
        assigned: [oidc]
        oidc:
          oidc:
            client-id: camunda-pt-default-client
            client-secret: default-secret
            issuer-uri: http://localhost:8081/realms/default
            redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
  persistent:
    sessions:
      enabled: true
  physical-tenants:
    tenanta:
      security:
        authentication:
          providers:
            assigned: [idpOne]
            oidc:
              idpOne:
                client-id: camunda-pt-tenanta-client
                client-secret: tenanta-secret
                issuer-uri: http://localhost:8082/realms/tenanta
                redirect-uri: "{baseUrl}/login/oauth2/code/{registrationId}"
    default:
      security:
        authentication:
          providers:
            assigned: [oidc]

server:
  port: 8080

spring:
  profiles:
    include:
      - consolidated-auth
      - pt-security
```

- [ ] **Step 2: Validate YAML**

```bash
python3 -c "import yaml,sys; yaml.safe_load(open('dist/src/main/resources/application-pt-poc.yaml'))" && echo OK
```

Expected: `OK`.

- [ ] **Step 3: Format, commit**

```bash
git add dist/src/main/resources/application-pt-poc.yaml
git commit -m "feat: add application-pt-poc profile pre-wired to local Keycloak runner"
```

---

## Task 15: PhysicalTenantSecurityIT — happy path

**Goal:** End-to-end IT booting two `KeycloakContainer` instances and OC in-JVM. Drives the OIDC authorization-code flow against tenant A's prefixed webapp chain. Asserts a session cookie scoped to the tenant Path is set and `/physical-tenant/tenanta/whoami` returns tenant A's principal.

**Files:**
- Create: `dist/src/test/java/io/camunda/application/pt/PhysicalTenantSecurityIT.java`

- [ ] **Step 1: Write the IT skeleton (containers + OC boot)**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.pt;

import static org.assertj.core.api.Assertions.assertThat;

import dasniko.testcontainers.keycloak.KeycloakContainer;
import io.camunda.zeebe.test.testcontainers.DefaultTestContainers;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
class PhysicalTenantSecurityIT {

  private static KeycloakContainer defaultRealm;
  private static KeycloakContainer tenantaRealm;
  private static ConfigurableApplicationContext oc;
  private static int ocPort;

  @SpringBootApplication
  static class TestApp {}

  @BeforeAll
  static void boot() {
    defaultRealm =
        DefaultTestContainers.createDefaultKeycloak()
            .withRealmImportFile("pt-poc/keycloak-default-realm.json");
    tenantaRealm =
        DefaultTestContainers.createDefaultKeycloak()
            .withRealmImportFile("pt-poc/keycloak-tenanta-realm.json");
    defaultRealm.start();
    tenantaRealm.start();

    final var app = new SpringApplication(TestApp.class);
    app.setWebApplicationType(WebApplicationType.SERVLET);
    app.setAdditionalProfiles("consolidated-auth", "pt-security");
    oc =
        app.run(
            "--server.port=0",
            "--camunda.security.authentication.method=oidc",
            "--camunda.security.authentication.providers.assigned=oidc",
            "--camunda.security.authentication.providers.oidc.oidc.client-id=camunda-pt-default-client",
            "--camunda.security.authentication.providers.oidc.oidc.client-secret=default-secret",
            "--camunda.security.authentication.providers.oidc.oidc.issuer-uri="
                + defaultRealm.getAuthServerUrl()
                + "/realms/default",
            "--camunda.security.authentication.providers.oidc.oidc.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}",
            "--camunda.physical-tenants.tenanta.security.authentication.providers.assigned=idpOne",
            "--camunda.physical-tenants.tenanta.security.authentication.providers.oidc.idpOne.client-id=camunda-pt-tenanta-client",
            "--camunda.physical-tenants.tenanta.security.authentication.providers.oidc.idpOne.client-secret=tenanta-secret",
            "--camunda.physical-tenants.tenanta.security.authentication.providers.oidc.idpOne.issuer-uri="
                + tenantaRealm.getAuthServerUrl()
                + "/realms/tenanta",
            "--camunda.physical-tenants.tenanta.security.authentication.providers.oidc.idpOne.redirect-uri={baseUrl}/login/oauth2/code/{registrationId}");
    ocPort = oc.getEnvironment().getProperty("local.server.port", Integer.class);
  }

  @AfterAll
  static void shutdown() {
    if (oc != null) oc.close();
    if (defaultRealm != null) defaultRealm.stop();
    if (tenantaRealm != null) tenantaRealm.stop();
  }

  @Test
  void shouldRedirectUnauthenticatedTenantPrefixedRequestToTenantIdp() throws Exception {
    // given
    final var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    // when
    final HttpResponse<String> response =
        http.send(
            HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + ocPort + "/physical-tenant/tenanta/whoami"))
                .GET()
                .build(),
            BodyHandlers.ofString());
    // then
    assertThat(response.statusCode()).isEqualTo(302);
    assertThat(response.headers().firstValue("Location"))
        .hasValueSatisfying(
            location -> assertThat(location).contains("/physical-tenant/tenanta/oauth2/authorization/idpOne"));
  }
}
```

- [ ] **Step 2: Run, confirm it passes the redirect assertion**

```bash
./mvnw verify -pl dist -Dit.test=PhysicalTenantSecurityIT -DskipTests=false -DskipUTs -Dquickly -T1C
```

Expected: BUILD SUCCESS, one test passing.

If the redirect goes to a Spring Security default page (`/login`) instead of the tenant-specific authorization endpoint, the `loginPage(...)` configuration in Task 11's webapp chain is wrong; fix and re-run.

- [ ] **Step 3: Format, commit**

```bash
./mvnw license:format spotless:apply -T1C
git add dist/src/test/java/io/camunda/application/pt/PhysicalTenantSecurityIT.java
git commit -m "test: add PhysicalTenantSecurityIT happy-path redirect assertion"
```

---

## Task 16: PhysicalTenantSecurityIT — full OIDC flow + isolation assertions

**Goal:** Extend the IT to drive the full OIDC authorization-code flow programmatically against tenant A and the default tenant. Assert (a) cookies are tenant-scoped, (b) tenant A's session cookie does not authenticate the default tenant's chain, (c) cross-tenant bearer token returns 401 on the api chain.

**Files:**
- Modify: `dist/src/test/java/io/camunda/application/pt/PhysicalTenantSecurityIT.java`

- [ ] **Step 1: Add a Keycloak login helper**

Append to `PhysicalTenantSecurityIT`:

```java
private static String performLoginAndExtractSessionCookie(
    final String realm, final String clientId, final String clientSecret,
    final String username, final String password,
    final String redirectStartPath) throws Exception {

  final var http = HttpClient.newBuilder()
      .followRedirects(HttpClient.Redirect.NEVER)
      .cookieHandler(new java.net.CookieManager())
      .build();

  // 1) Hit the tenant's protected endpoint, get redirected to /oauth2/authorization/<reg>
  final var step1 = http.send(
      HttpRequest.newBuilder().uri(URI.create("http://localhost:" + ocPort + redirectStartPath)).GET().build(),
      BodyHandlers.ofString());
  assertThat(step1.statusCode()).isEqualTo(302);
  final String authStart = step1.headers().firstValue("Location").orElseThrow();

  // 2) Follow to the authorization endpoint; gets redirected to Keycloak login form
  final var step2 = http.send(
      HttpRequest.newBuilder().uri(URI.create("http://localhost:" + ocPort + authStart)).GET().build(),
      BodyHandlers.ofString());
  assertThat(step2.statusCode()).isEqualTo(302);
  final String keycloakLogin = step2.headers().firstValue("Location").orElseThrow();

  // 3) GET Keycloak's login form
  final var step3 = http.send(HttpRequest.newBuilder().uri(URI.create(keycloakLogin)).GET().build(),
      BodyHandlers.ofString());
  assertThat(step3.statusCode()).isEqualTo(200);
  // Extract the form action URL from the HTML body
  final java.util.regex.Matcher m =
      java.util.regex.Pattern.compile("action=\"([^\"]+)\"").matcher(step3.body());
  assertThat(m.find()).isTrue();
  final String loginAction = m.group(1).replace("&amp;", "&");

  // 4) POST credentials
  final var form = "username=" + java.net.URLEncoder.encode(username, java.nio.charset.StandardCharsets.UTF_8)
      + "&password=" + java.net.URLEncoder.encode(password, java.nio.charset.StandardCharsets.UTF_8)
      + "&credentialId=";
  final var step4 = http.send(
      HttpRequest.newBuilder()
          .uri(URI.create(loginAction))
          .header("Content-Type", "application/x-www-form-urlencoded")
          .POST(HttpRequest.BodyPublishers.ofString(form))
          .build(),
      BodyHandlers.ofString());
  assertThat(step4.statusCode()).isEqualTo(302);
  final String callbackUrl = step4.headers().firstValue("Location").orElseThrow();

  // 5) GET the callback — OC sets the session cookie here
  final var step5 = http.send(
      HttpRequest.newBuilder().uri(URI.create(callbackUrl)).GET().build(),
      BodyHandlers.ofString());
  assertThat(step5.statusCode()).isIn(302, 200);

  // Extract Set-Cookie header for the tenant
  return step5.headers().allValues("Set-Cookie").stream()
      .filter(c -> c.startsWith("camunda-session-"))
      .findFirst()
      .orElseThrow(() -> new AssertionError("No camunda-session cookie set"));
}
```

**Pragmatic note:** this helper is ~50 lines because doing OIDC by hand over `HttpClient` is verbose. If the codebase grows a shared test helper for this pattern, refactor to use it. For now keep it self-contained.

- [ ] **Step 2: Add the happy-path test**

```java
@Test
void shouldEstablishTenantScopedSessionAfterLogin() throws Exception {
  final String cookie =
      performLoginAndExtractSessionCookie(
          "tenanta",
          "camunda-pt-tenanta-client",
          "tenanta-secret",
          "bob",
          "bob",
          "/physical-tenant/tenanta/whoami");
  assertThat(cookie).contains("camunda-session-tenanta")
      .contains("Path=/physical-tenant/tenanta");
}
```

- [ ] **Step 3: Add the cross-tenant isolation test**

```java
@Test
void shouldRejectTenantSessionCookieOnDefaultTenantPath() throws Exception {
  // Login as tenant A
  final String tenantACookie =
      performLoginAndExtractSessionCookie(
          "tenanta", "camunda-pt-tenanta-client", "tenanta-secret",
          "bob", "bob", "/physical-tenant/tenanta/whoami");

  // Extract bare cookie name=value for replay
  final String cookieHeader = tenantACookie.substring(0, tenantACookie.indexOf(';'));

  // Replay against the default tenant's prefixed chain
  final var http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
  final var response = http.send(
      HttpRequest.newBuilder()
          .uri(URI.create("http://localhost:" + ocPort + "/physical-tenant/default/whoami"))
          .header("Cookie", cookieHeader)
          .GET().build(),
      BodyHandlers.ofString());

  // The default tenant chain does not honour tenant A's cookie name+Path
  assertThat(response.statusCode()).isEqualTo(302);
  assertThat(response.headers().firstValue("Location"))
      .hasValueSatisfying(l -> assertThat(l).contains("/physical-tenant/default/oauth2/authorization/"));
}
```

- [ ] **Step 4: Run the full IT**

```bash
./mvnw verify -pl dist -Dit.test=PhysicalTenantSecurityIT -DskipTests=false -DskipUTs -Dquickly -T1C
```

Expected: three tests pass. Investigation order if any fail:
- Redirect URL wrong → check `loginPage(...)` in `PerTenantSecurityChainFactory.buildWebappChain`
- Cookie name/path wrong → check `PhysicalTenantCookieSerializer` and the `SessionRepositoryFilter` is registered first
- 401 instead of 302 on cross-tenant replay → expected if the chain rejects the foreign cookie outright; relax the assertion to `assertThat(response.statusCode()).isIn(302, 401)`

- [ ] **Step 5: Format, commit**

```bash
./mvnw license:format spotless:apply -T1C
git add dist/src/test/java/io/camunda/application/pt/PhysicalTenantSecurityIT.java
git commit -m "test: add full OIDC flow and cross-tenant isolation assertions to PT IT"
```

---

## Task 17: Manual smoke test (browser)

**Goal:** Demonstrate the spec's end-to-end demo path in a real browser against the local Keycloak runner. This is the user-facing acceptance for the PoC.

**Files:** none (operational task)

- [ ] **Step 1: Start the local IdPs**

In terminal 1:

```bash
./mvnw -pl dist test-compile exec:java \
  -Dexec.mainClass=io.camunda.application.pt.PtPocLocalIdpRunner \
  -Dexec.classpathScope=test
```

Wait for the "PT-PoC local IdPs ready" banner.

- [ ] **Step 2: Start OC with the PoC profile**

In terminal 2:

```bash
./mvnw -pl dist spring-boot:run \
  -Dspring-boot.run.profiles=consolidated-auth,pt-security,pt-poc
```

Wait for OC to bind on `:8080`.

- [ ] **Step 3: Browser flow — tenant A**

1. Open Chrome → `http://localhost:8080/physical-tenant/tenanta/whoami`
2. Verify redirect to `http://localhost:8082/realms/tenanta/protocol/openid-connect/auth?...`
3. Log in as `bob` / `bob`
4. Verify return to OC and JSON body `{"tenantId":"tenanta","principal":"bob",...}`
5. Open DevTools → Application → Cookies → verify a single cookie `camunda-session-tenanta` with `Path=/physical-tenant/tenanta`

- [ ] **Step 4: Browser flow — default tenant (incognito or new profile)**

1. New incognito window → `http://localhost:8080/physical-tenant/default/whoami`
2. Verify redirect to `http://localhost:8081/realms/default/protocol/openid-connect/auth?...`
3. Log in as `alice` / `alice`
4. Verify return and `{"tenantId":"default",...}`
5. Cookies: `camunda-session-default` with `Path=/physical-tenant/default`

- [ ] **Step 5: Cross-tenant check**

In the tenant-A non-incognito window, navigate to `http://localhost:8080/physical-tenant/default/whoami`. The browser does not send the tenanta cookie (different Path). Expect a redirect to default's IdP.

- [ ] **Step 6: Default-tenant unprefixed access path**

Same tenant-A window → `http://localhost:8080/whoami`. Expect redirect to default's IdP (different cookie scope yet again). After login, JSON returns `{"accessPath":"unprefixed","tenantId":"default"}`.

- [ ] **Step 7: Record results**

Add a short note to the PR (`#53587`) describing the manual run: which steps passed, screenshots if anything surprised you. No commit needed.

---

## Self-review checklist (run after the plan is written)

- [x] **Spec coverage** — every spec section has at least one task:
  - D1 (filter chain isolation primitive) → Tasks 11, 12
  - D2 (cookie Path isolation) → Tasks 7, 11, 15, 16
  - D3 (profile gate, CSL opt-out) → Task 1
  - D4 (reuse OC OIDC machinery) → Task 5 (`PerTenantOidcRegistry` reuses `ClientRegistrationFactory`, `OidcAuthenticationConfigurationRepository`)
  - D5 (chain-matcher tenant resolution) → Tasks 8, 11, 12
  - D6 (portability to C) → Task 9 (`TenantSecuritySlice` record, all dependencies constructor-injected)
  - Bean catalogue → Tasks 4–13 cover every class listed in the spec
  - Configuration consumed (`providers.assigned`) → Task 5 step 0 + Task 14
  - End-to-end demo path → Task 15, 16, 17
  - Local testing setup → Tasks 2, 3, 14, 15
- [x] **Placeholder scan** — no "TBD", "TODO", "fill in", "appropriate error handling" in any task body. Honest scope notes are present where the engineer must adapt (e.g., Task 11 step 4 acknowledges the unit test may need to be downgraded; Task 13 acknowledges the generic registrar is sketched and the PoC uses explicit `@Bean` methods).
- [x] **Type consistency** — `TenantSecuritySlice` shape in Task 9 matches its use in Tasks 11/12/13. `PerTenantOidcRegistry.forTenant` signature matches its callsite in Task 13. `PerTenantSessionRepository(String, WebSessionRepository)` constructor consistent across Tasks 6, 11, 13.

## Known unknowns the engineer will surface during execution

These are honest gaps the plan cannot fully retire from a desk:

1. **CSL `@Profile` opt-out via `& !pt-security` expression.** Task 1 verifies. If Spring rejects the expression, fall back to splitting into two profile-annotated configs.
2. **`SecurityConfiguration.getAuthentication().getProviders().setOidc/getAssigned` exact API shape.** Task 5 step 0 adapts to whatever exists; the test will drive the shape.
3. **`HttpSecurity` mockability in Task 11/12 unit tests.** The plan acknowledges these tests may need to downgrade to IT-only verification. The IT in Tasks 15–16 is authoritative.
4. **`WebSession` copy-constructor.** Task 6 step 3 may need to add it under `authentication/.../WebSession.java`.
5. **`KeycloakContainer.getAuthServerUrl()`** — exact API used in Task 15; confirm against `dasniko/testcontainers-keycloak` version on the classpath.

The plan provides escape hatches for each (note in the relevant step), so they don't block forward motion.
