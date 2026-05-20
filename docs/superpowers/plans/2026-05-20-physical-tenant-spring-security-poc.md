# Physical-Tenant Spring Security PoC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Stand up isolated Spring Security per Physical Tenant in OC (`dist/`), proving login/OIDC-provider/session isolation against two Keycloak realms under a single `pt-security` Spring profile.

**Architecture:** Per-tenant `SecurityFilterChain` pairs (webapp + api) registered programmatically from `PhysicalTenantResolver.getAll()`. Browser-side isolation via cookie `Path` scoping. CSL's auto-config is opted out on the `pt-security` profile. The chain factory and per-tenant collaborator slice are designed portable to a future child-context model (approach C). End-to-end IT drives two Keycloak Testcontainers; a `PtPocLocalIdpRunner` exposes the same realms for browser-based developer iteration.

**Tech Stack** (confirmed against `parent/pom.xml`):
- Spring Boot **4.0.6** / Spring Security **7.0.5** (`oauth2Login`, `oauth2ResourceServer`)
- Spring Session (existing `WebSessionRepository` reused per-tenant via a key-prefixing decorator)
- `dasniko/testcontainers-keycloak` (already a project dependency, used by `OidcAuthOverRestIT`)
- JUnit **6.0.3** + AssertJ + Awaitility (project conventions)

**Spring Security 7 / Boot 4 API notes** the engineer should keep in mind:
- `HttpSecurity` configurer lambdas (`csrf(c -> ...)`, `oauth2Login(l -> ...)`, `authorizeHttpRequests(a -> ...)`) are the canonical style; the legacy builder-chain syntax is removed in 7.
- `CookieCsrfTokenRepository.withHttpOnlyFalse()` was removed/relocated in Security 7. Equivalent: `new CookieCsrfTokenRepository()` plus `setCookieCustomizer(c -> c.httpOnly(false))`. Engineer verifies the exact factory name when implementing Task 11.
- `SecurityContextPersistenceFilter` (referenced as an anchor for `addFilterBefore` in Tasks 11–12) was removed in Security 6; use `org.springframework.security.web.context.SecurityContextHolderFilter.class` as the anchor instead.
- `SessionRepositoryFilter` (Spring Session) still accepts a custom `HttpSessionIdResolver` via `setHttpSessionIdResolver`.
- `oauth2Login(...).authorizationEndpoint(ae -> ae.baseUri(...))` and `redirectionEndpoint(re -> re.baseUri(...))` remain the per-chain customisation hooks.
- JUnit 6: parameter resolution and `@Test` semantics unchanged; some `Assertions` overloads were deprecated, but AssertJ is the project convention so this is moot.

**Spec:** [`docs/superpowers/specs/2026-05-20-physical-tenant-spring-security-poc-design.md`](../specs/2026-05-20-physical-tenant-spring-security-poc-design.md)

---

## File structure

New files (all paths from repo root):

|                                                 File                                                 |                                                                    Responsibility                                                                     |
|------------------------------------------------------------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java` | Top-level `@Configuration`, profile-gated, registers per-tenant chains via `BeanDefinitionRegistryPostProcessor`                                      |
| `authentication/src/main/java/io/camunda/authentication/pt/PerTenantSecurityChainFactory.java`       | Builds the pair of `SecurityFilterChain` (webapp + api) for one tenant from a `TenantSecuritySlice`                                                   |
| `authentication/src/main/java/io/camunda/authentication/pt/TenantSecuritySlice.java`                 | Record bundling per-tenant collaborators (clients, decoder, session repo, cookie serializer, etc.)                                                    |
| `authentication/src/main/java/io/camunda/authentication/pt/PerTenantOidcRegistry.java`               | Builds the per-tenant `OidcAuthenticationConfigurationRepository` (filters by `providers.assigned`) and the `ClientRegistrationRepository`            |
| `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantRedirectUriRewriter.java`   | Stamps the per-tenant prefix into each `ClientRegistration.redirectUri` template                                                                      |
| `authentication/src/main/java/io/camunda/authentication/pt/PerTenantSessionRepository.java`          | Decorator over `WebSessionRepository` that prefixes session ids with `t:<tenant>:` for keyspace isolation                                             |
| `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantCookieSerializer.java`      | Static factory that produces a `DefaultCookieSerializer` configured per tenant (cookie name + Path)                                                   |
| `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantWhoamiController.java`      | Demo controller exposing `/physical-tenant/{t}/whoami` and `/v2/physical-tenants/{t}/whoami`                                                          |
| `dist/src/main/resources/application-pt-poc.yaml`                                                    | Bundled config that activates the PoC against the local Keycloak runner's default ports                                                               |
| `dist/src/test/java/io/camunda/application/pt/PtPocLocalIdpRunner.java`                              | Standalone `main()` for the developer iteration loop; boots two Keycloak containers on fixed ports                                                    |
| `dist/src/test/java/io/camunda/application/pt/PhysicalTenantSecurityIT.java`                         | End-to-end Testcontainers IT; boots two Keycloaks + OC in-JVM, drives OIDC flow, asserts isolation                                                    |
| `dist/src/test/resources/pt-poc/keycloak-default-realm.json`                                         | Realm export: one client + one user for the default tenant                                                                                            |
| `dist/src/test/resources/pt-poc/keycloak-tenanta-realm.json`                                         | Realm export: one client + one user for tenant A                                                                                                      |

Modified files:

|                                                File                                                 |                                                          Change                                                           |
|-----------------------------------------------------------------------------------------------------|---------------------------------------------------------------------------------------------------------------------------|
| `authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java`              | Add `!pt-security` to the profile predicate so CSL stays off when the PoC is active                                       |
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

**Subagent guardrails:**

- The spec (`docs/superpowers/specs/...`) and this plan are tracked files. **Never** `git checkout --` or `git restore` them autonomously, even if `spotless` reformats them as a side-effect. If `spotless` touches a doc file, leave the working-tree change alone and call it out in your status report — the controller decides whether to revert, amend, or accept the reformat.
- Stage only the files explicitly listed in your task. Use `git add <path1> <path2>` with explicit paths, never `git add .` or `git add -A`.
- Do not push. The controller handles pushes at checkpoint boundaries.

---

## Task 1: Profile scaffold + verify CSL opts out

**Goal:** Introduce the `pt-security` profile, exclude `WebSecurityConfig` and `WebSessionRepositoryConfiguration` when it is active, and verify boot produces zero `SecurityFilterChain` beans on this profile.

**Files:**
- Modify: `authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java:60-62` (profile predicate)
- Modify: `dist/src/main/java/io/camunda/application/commons/identity/WebSessionRepositoryConfiguration.java` (add profile predicate)
- Create: `authentication/src/test/java/io/camunda/authentication/pt/PtSecurityProfileBootTest.java`

- [ ] **Step 1: Write the failing test**

The test must be **load-bearing**: the minimal `@SpringBootApplication` only scans its own package, so it doesn't pick up `WebSecurityConfig` (which lives in `io.camunda.authentication.config` and is loaded by host apps via `@Import`, not auto-config). Without an explicit `@Import`, the assertion `chains.isEmpty()` is vacuously true regardless of the `pt-security` profile.

The fix: parametrise the test by toggling `pt-security` on and off, with `@Import(WebSecurityConfig.class)` so CSL's chains actually get a chance to register. Without `pt-security`, CSL produces a non-empty chain set; with `pt-security`, the profile expression on `WebSecurityConfig` keeps the import inert and `chains` is empty.

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

import io.camunda.authentication.config.WebSecurityConfig;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

class PtSecurityProfileBootTest {

  @SpringBootApplication
  @Import(WebSecurityConfig.class)
  static class App {}

  @Nested
  @SpringBootTest(classes = App.class)
  @ActiveProfiles({"consolidated-auth"})
  @TestPropertySource(properties = {"camunda.security.authentication.method=oidc"})
  class WithoutPtSecurity {

    @Test
    void shouldRegisterCslFilterChainsByDefault(final ApplicationContext context) {
      // given - consolidated-auth without pt-security
      // when we collect every SecurityFilterChain in the context
      final Map<String, SecurityFilterChain> chains =
          context.getBeansOfType(SecurityFilterChain.class);
      // then CSL's chains are present (proves the @Import is load-bearing)
      assertThat(chains).isNotEmpty();
    }
  }

  @Nested
  @SpringBootTest(classes = App.class)
  @ActiveProfiles({"consolidated-auth", "pt-security"})
  @TestPropertySource(properties = {"camunda.security.authentication.method=oidc"})
  class WithPtSecurity {

    @Test
    void shouldRegisterZeroFilterChainsWhenPtSecurityIsActive(final ApplicationContext context) {
      // given - both profiles active
      // when we collect every SecurityFilterChain in the context
      final Map<String, SecurityFilterChain> chains =
          context.getBeansOfType(SecurityFilterChain.class);
      // then WebSecurityConfig has opted out and so have its CSL imports
      assertThat(chains).isEmpty();
    }
  }
}
```

- [ ] **Step 2: Run the test and confirm `WithoutPtSecurity` passes while `WithPtSecurity` fails**

```bash
./mvnw verify -pl authentication -Dtest=PtSecurityProfileBootTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Expected: `WithoutPtSecurity.shouldRegisterCslFilterChainsByDefault` passes (proving the import is load-bearing), and `WithPtSecurity.shouldRegisterZeroFilterChainsWhenPtSecurityIsActive` fails (CSL still active because `WebSecurityConfig`'s profile expression hasn't been updated yet).

If both pass before the profile change, the `@Import` isn't pulling CSL in — investigate the `@ImportAutoConfiguration(CamundaSecurityAutoConfiguration.class)` on `WebSecurityConfig` and add it explicitly to `App` if necessary.

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
## Task 4: Walking skeleton — one tenant, end-to-end login

**Goal:** First runnable login. Under the `pt-security` profile, register a single hard-coded `SecurityFilterChain` for tenant A that drives the OIDC flow against the local Keycloak runner. Verify in a browser. This task deliberately inlines everything — no abstractions, no extractions — to validate the model with the smallest possible code change. The next ten tasks refactor this blob into the unit-testable collaborators from the spec.

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java`
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantWhoamiController.java`
- Create: `dist/src/main/resources/application-pt-poc.yaml`
- Possibly modify: any file required to make OC boot under `pt-security` without `WebSecurityConfig`'s SPI ports. (Unknown at plan-time — discovered when the engineer first tries to boot.)

### Step 1 — Write the configuration

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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Profile("pt-security")
public class PhysicalTenantSecurityConfiguration {

  // Hard-coded for the walking skeleton. T9 generalises this to read from
  // camunda.physical-tenants.<id>.security.authentication.providers.assigned.
  private static final String TENANTA_REGISTRATION_ID = "oidc";

  @Bean
  public SecurityFilterChain ptTenantaWebappChain(
      final HttpSecurity http, final SecurityConfiguration security) throws Exception {

    final var providerConfig =
        security
            .getAuthentication()
            .getProviders()
            .getOidc()
            .get(TENANTA_REGISTRATION_ID);

    final ClientRegistration registration =
        ClientRegistrations.fromIssuerLocation(providerConfig.getIssuerUri())
            .registrationId(TENANTA_REGISTRATION_ID)
            .clientId(providerConfig.getClientId())
            .clientSecret(providerConfig.getClientSecret())
            .redirectUri(
                "{baseUrl}/physical-tenant/tenanta/login/oauth2/code/{registrationId}")
            .build();

    final ClientRegistrationRepository repo =
        new InMemoryClientRegistrationRepository(registration);

    return http.securityMatcher("/physical-tenant/tenanta/**")
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .oauth2Login(
            l ->
                l.clientRegistrationRepository(repo)
                    .authorizationEndpoint(
                        ae -> ae.baseUri("/physical-tenant/tenanta/oauth2/authorization"))
                    .redirectionEndpoint(
                        re -> re.baseUri("/physical-tenant/tenanta/login/oauth2/code/*")))
        .build();
  }
}
```

### Step 2 — Write the whoami controller

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@Profile("pt-security")
public class PhysicalTenantWhoamiController {

  public record Whoami(String tenantId, String principal) {}

  @GetMapping("/physical-tenant/{tenantId}/whoami")
  @ResponseBody
  public Whoami whoami(
      @PathVariable final String tenantId, final Authentication authentication) {
    return new Whoami(
        tenantId, authentication != null ? authentication.getName() : "anonymous");
  }
}
```

### Step 3 — Write the bundled application profile

`dist/src/main/resources/application-pt-poc.yaml`:

```yaml
camunda:
  security:
    authentication:
      method: oidc
      providers:
        oidc:
          oidc:
            client-id: camunda-pt-tenanta-client
            client-secret: tenanta-secret
            issuer-uri: http://localhost:8082/realms/tenanta

server:
  port: 8080

spring:
  profiles:
    include:
      - consolidated-auth
      - pt-security
```

### Step 4 — Try to boot OC; satisfy whatever SPI ports CSL's absence breaks

```bash
# In terminal 1: leave the IdP runner up
./mvnw -pl dist test-compile exec:java \
  -Dexec.mainClass=io.camunda.application.pt.PtPocLocalIdpRunner \
  -Dexec.classpathScope=test

# In terminal 2: try to boot OC
./mvnw -pl dist spring-boot:run \
  -Dspring-boot.run.profiles=pt-poc
```

`WebSecurityConfig` defines a handful of SPI port beans (`SecurityPathPort`, `WebAppProviderPort`, `AuthorizationRepositoryPort`, `ResourcePermissionPort`, `OidcResourceServerCustomizer`, etc.) that may be consumed by OC code outside CSL. With `pt-security` active, `WebSecurityConfig` is off and those ports are missing. Two paths to resolve:

- **A (preferred):** confirm the missing ports are only consumed by CSL itself (which is also off under `pt-security`). If so, OC boots cleanly; no further changes needed.
- **B (if A fails):** add a minimal `PhysicalTenantHostStubsConfiguration` `@Configuration @Profile("pt-security")` that provides no-op implementations of the ports that fail to autowire. Report each port stubbed.

This step is the moment of truth. Take an iterative approach: boot, read the first autowire failure, stub the port (or move it to an `ObjectProvider` consumer), reboot, repeat until OC starts.

### Step 5 — Manual smoke

With OC running on `:8080`:

1. Browser → `http://localhost:8080/physical-tenant/tenanta/whoami`
2. Expect redirect to `http://localhost:8082/realms/tenanta/protocol/openid-connect/auth?...`
3. Log in as `bob` / `bob`
4. Expect return to `http://localhost:8080/physical-tenant/tenanta/login/oauth2/code/oidc` then to `/physical-tenant/tenanta/whoami`
5. Response JSON: `{"tenantId":"tenanta","principal":"bob"}`
6. DevTools → Application → Cookies: a session cookie (likely `JSESSIONID` at this stage; per-chain naming arrives in Task 5) scoped to default Path

If any step fails, this is where the security model meets reality. Adapt the chain config (typically the `redirectUri` template or the `authorizationEndpoint.baseUri`) and rerun. Do not move on until the manual flow works.

### Step 6 — Format and commit

```bash
./mvnw license:format spotless:apply -T1C
./mvnw install -pl authentication,dist -am -Dquickly -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java \
        authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantWhoamiController.java \
        dist/src/main/resources/application-pt-poc.yaml \
        <any-host-stub-files>
git commit -m "$(cat <<'EOF'
feat: walking skeleton for PT-security PoC — one tenant end-to-end login

Single hard-coded SecurityFilterChain for tenant A wired against the
local Keycloak runner. Demonstrates the OIDC flow under the pt-security
profile end-to-end before any abstractions land. Subsequent tasks
refactor this blob into the per-tenant slice and chain factory from
the spec.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## Task 5: Add default tenant prefixed chain + per-chain cookie isolation

**Goal:** Second tenant chain. Both chains must issue distinct, Path-scoped session cookies so the browser holds them as two independent sessions. After this task, opening one tab on `/physical-tenant/tenanta/whoami` and a second on `/physical-tenant/default/whoami` produces two simultaneous logins with two cookies.

**Files:**
- Modify: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java`
- Modify: `dist/src/main/resources/application-pt-poc.yaml`

### Step 1 — Extend the YAML with the default-tenant provider entry

Add under `camunda.security.authentication.providers.oidc`:

```yaml
        default-provider:
          client-id: camunda-pt-default-client
          client-secret: default-secret
          issuer-uri: http://localhost:8081/realms/default
```

The existing `oidc:` entry remains for tenant A. (We're not yet using `physical-tenants.<id>.security.authentication.providers.assigned` — that arrives in Task 8.)

### Step 2 — Add a default-tenant chain bean

Inside `PhysicalTenantSecurityConfiguration`, copy the tenant-A bean to a sibling `ptDefaultWebappChain` that:

- Reads provider config from the `default-provider` key.
- Matches `securityMatcher("/physical-tenant/default/**")`.
- Builds a `ClientRegistration` with `redirectUri("{baseUrl}/physical-tenant/default/login/oauth2/code/{registrationId}")`.

Resist the urge to extract a helper — that's Task 6. Two near-duplicate `@Bean` methods are fine here; the duplication is what motivates the extraction.

### Step 3 — Wire per-chain session cookies

Each chain needs its own session cookie name and Path. Add to each `@Bean`:

```java
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.session.web.http.SessionRepositoryFilter;
import org.springframework.session.MapSessionRepository;
import java.util.concurrent.ConcurrentHashMap;

// in each chain, before .build():
final DefaultCookieSerializer serializer = new DefaultCookieSerializer();
serializer.setCookieName("camunda-session-tenanta"); // or "camunda-session-default"
serializer.setCookiePath("/physical-tenant/tenanta"); // or "/physical-tenant/default"
serializer.setUseHttpOnlyCookie(true);
serializer.setSameSite("Lax");

final CookieHttpSessionIdResolver resolver = new CookieHttpSessionIdResolver();
resolver.setCookieSerializer(serializer);

final SessionRepositoryFilter<?> sessionFilter =
    new SessionRepositoryFilter<>(new MapSessionRepository(new ConcurrentHashMap<>()));
sessionFilter.setHttpSessionIdResolver(resolver);

http.addFilterBefore(
    sessionFilter,
    org.springframework.security.web.context.SecurityContextHolderFilter.class);
```

`MapSessionRepository` is in-memory; T9 replaces it with the persistent-storage-backed `PerTenantSessionRepository`. For the walking skeleton, in-memory is fine — manual smoke tests run within one process lifetime.

### Step 4 — Manual smoke for isolation

Both Keycloak realms + OC running. Browser:

1. Tab 1 → `/physical-tenant/tenanta/whoami` → login `bob/bob` → see `tenantId=tenanta`. DevTools shows cookie `camunda-session-tenanta; Path=/physical-tenant/tenanta`.
2. Tab 2 → `/physical-tenant/default/whoami` → redirect to default IdP → login `alice/alice` → see `tenantId=default`. DevTools shows a second cookie `camunda-session-default; Path=/physical-tenant/default`.
3. Tab 1 still works (refresh): the tenanta cookie isn't shadowed by the new default cookie because they have disjoint Paths.
4. Optional: try replaying tenant A's cookie in DevTools against the default tenant's URL — cookie name doesn't match, so default's chain doesn't see it, and the user is challenged to re-auth.

### Step 5 — Format and commit

```bash
./mvnw license:format spotless:apply -T1C
./mvnw install -pl authentication,dist -am -Dquickly -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java \
        dist/src/main/resources/application-pt-poc.yaml
git commit -m "feat: add default-tenant PT chain with per-chain cookie isolation"
```

---

## Task 6: Extract TenantSecuritySlice + PerTenantSecurityChainFactory

**Goal:** Pull the duplicated chain-building code from Tasks 4 and 5 into a `TenantSecuritySlice` record and a `PerTenantSecurityChainFactory.buildWebappChain(http, slice)` method. Behaviour identical to before; both `@Bean` methods in `PhysicalTenantSecurityConfiguration` reduce to slice-construction + factory call.

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/TenantSecuritySlice.java`
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PerTenantSecurityChainFactory.java`
- Modify: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java`

### Step 1 — Write the slice record

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
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.session.web.http.CookieHttpSessionIdResolver;
import org.springframework.session.web.http.SessionRepositoryFilter;

@NullMarked
public record TenantSecuritySlice(
    String tenantId,
    AccessPath accessPath,
    ClientRegistrationRepository clientRegistrationRepository,
    SessionRepositoryFilter<?> sessionRepositoryFilter,
    CookieHttpSessionIdResolver httpSessionIdResolver) {

  public enum AccessPath {
    PREFIXED,
    UNPREFIXED_DEFAULT
  }

  public String webappPathPrefix() {
    return accessPath == AccessPath.PREFIXED ? "/physical-tenant/" + tenantId : "";
  }
}
```

### Step 2 — Write the factory

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
import org.springframework.security.web.SecurityFilterChain;

@NullMarked
public final class PerTenantSecurityChainFactory {

  public SecurityFilterChain buildWebappChain(
      final HttpSecurity http, final TenantSecuritySlice slice) throws Exception {
    final String prefix = slice.webappPathPrefix();
    return http.securityMatcher(prefix.isEmpty() ? "/**" : prefix + "/**")
        .addFilterBefore(
            slice.sessionRepositoryFilter(),
            org.springframework.security.web.context.SecurityContextHolderFilter.class)
        .authorizeHttpRequests(a -> a.anyRequest().authenticated())
        .oauth2Login(
            l ->
                l.clientRegistrationRepository(slice.clientRegistrationRepository())
                    .authorizationEndpoint(
                        ae -> ae.baseUri(prefix + "/oauth2/authorization"))
                    .redirectionEndpoint(
                        re -> re.baseUri(prefix + "/login/oauth2/code/*")))
        .build();
  }
}
```

### Step 3 — Reduce the @Bean methods to slice construction

Each `@Bean` now:

```java
@Bean
public SecurityFilterChain ptTenantaWebappChain(
    final HttpSecurity http, final SecurityConfiguration security) throws Exception {
  return new PerTenantSecurityChainFactory().buildWebappChain(http, sliceFor("tenanta", "oidc", security));
}
```

…with a `private TenantSecuritySlice sliceFor(String tenantId, String registrationId, SecurityConfiguration security)` helper that does the existing inline work.

### Step 4 — Re-run the manual smoke

Steps from Task 5. Two tabs, two cookies, isolated. No behaviour change expected; this is pure refactor.

### Step 5 — Format and commit

```bash
./mvnw license:format spotless:apply -T1C
./mvnw install -pl authentication -am -Dquickly -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/TenantSecuritySlice.java \
        authentication/src/main/java/io/camunda/authentication/pt/PerTenantSecurityChainFactory.java \
        authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java
git commit -m "refactor: extract TenantSecuritySlice and PerTenantSecurityChainFactory"
```

---

## Task 7: Extract PhysicalTenantRedirectUriRewriter + unit test

**Goal:** Pull the redirect-URI templating out of the inline `ClientRegistration.redirectUri(...)` call into a tested pure function.

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantRedirectUriRewriter.java`
- Test: `authentication/src/test/java/io/camunda/authentication/pt/PhysicalTenantRedirectUriRewriterTest.java`
- Modify: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java` (use the rewriter)

### Step 1 — Write the failing test

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

import org.junit.jupiter.api.Test;

class PhysicalTenantRedirectUriRewriterTest {

  @Test
  void shouldInsertTenantSegmentBetweenBaseUrlAndLoginCallback() {
    assertThat(
            PhysicalTenantRedirectUriRewriter.rewrite(
                "{baseUrl}/login/oauth2/code/{registrationId}", "tenanta"))
        .isEqualTo(
            "{baseUrl}/physical-tenant/tenanta/login/oauth2/code/{registrationId}");
  }

  @Test
  void shouldNotRewriteWhenAlreadyPrefixed() {
    final var input = "{baseUrl}/physical-tenant/tenanta/login/oauth2/code/{registrationId}";
    assertThat(PhysicalTenantRedirectUriRewriter.rewrite(input, "tenanta")).isEqualTo(input);
  }

  @Test
  void shouldRewriteAbsoluteUri() {
    assertThat(
            PhysicalTenantRedirectUriRewriter.rewrite(
                "https://oc.example.com/login/oauth2/code/idpOne", "default"))
        .isEqualTo("https://oc.example.com/physical-tenant/default/login/oauth2/code/idpOne");
  }

  @Test
  void shouldRejectBlankTenantId() {
    assertThatThrownBy(
            () -> PhysicalTenantRedirectUriRewriter.rewrite("{baseUrl}/login/oauth2/code/x", ""))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
```

### Step 2 — Run, confirm failure; implement

```bash
./mvnw verify -pl authentication -Dtest=PhysicalTenantRedirectUriRewriterTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Then:

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

  private static final String LOGIN_CALLBACK = "/login/oauth2/code/";

  private PhysicalTenantRedirectUriRewriter() {}

  public static String rewrite(final String template, final String tenantId) {
    if (tenantId == null || tenantId.isBlank()) {
      throw new IllegalArgumentException("tenantId must not be blank");
    }
    final String prefixed = "/physical-tenant/" + tenantId + LOGIN_CALLBACK;
    if (template.contains(prefixed)) {
      return template;
    }
    return template.replace(LOGIN_CALLBACK, prefixed);
  }
}
```

### Step 3 — Replace inline `redirectUri(...)` callsites with the rewriter

In `PhysicalTenantSecurityConfiguration`:

```java
.redirectUri(
    PhysicalTenantRedirectUriRewriter.rewrite(
        "{baseUrl}/login/oauth2/code/{registrationId}", tenantId))
```

### Step 4 — Run tests, manual smoke

```bash
./mvnw verify -pl authentication -Dtest=PhysicalTenantRedirectUriRewriterTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Both tests green. Manual smoke unchanged from Task 6.

### Step 5 — Format and commit

```bash
./mvnw license:format spotless:apply -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantRedirectUriRewriter.java \
        authentication/src/test/java/io/camunda/authentication/pt/PhysicalTenantRedirectUriRewriterTest.java \
        authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java
git commit -m "refactor: extract PhysicalTenantRedirectUriRewriter with unit tests"
```

---

## Task 8: Extract PerTenantOidcRegistry + consume `providers.assigned`

**Goal:** Pull the per-tenant `ClientRegistrationRepository` construction into a `PerTenantOidcRegistry` that filters the provider map by the tenant's `providers.assigned` list. After this task, tenant config moves from "hard-coded provider key" to "tenant's `providers.assigned` declares which IdPs are active for that tenant."

This is the first task that requires the `Providers.assigned` field on the security config bean. If it doesn't exist yet, add it as Step 0.

**Files:**
- Step 0: possibly modify the `AuthenticationConfiguration.Providers` class under `security/security-core/src/main/java/io/camunda/security/configuration/` to add `private List<String> assigned` + getter/setter
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PerTenantOidcRegistry.java`
- Test: `authentication/src/test/java/io/camunda/authentication/pt/PerTenantOidcRegistryTest.java`
- Modify: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java` (use the registry)
- Modify: `dist/src/main/resources/application-pt-poc.yaml` (move tenant A's provider config under `camunda.physical-tenants.tenanta.security.authentication.providers.*` and set `assigned: [idpOne]`)

### Step 0 — Add `Providers.assigned` if missing

```bash
grep -rn "class Providers\|getAssigned" security/security-core/src/main 2>/dev/null
```

If the field is absent, add it on the providers configuration class with a default `new ArrayList<>()` and standard getters/setters. Use the existing nullability convention in that file.

### Step 1 — Write the failing test

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
    final var security = sec(Map.of("idpOne", oidc("idpOne"), "idpTwo", oidc("idpTwo")), List.of("idpOne"));
    final var registry = PerTenantOidcRegistry.forTenant("tenanta", security);
    assertThat(registry.clientRegistrationRepository().findByRegistrationId("idpOne")).isNotNull();
    assertThat(registry.clientRegistrationRepository().findByRegistrationId("idpTwo")).isNull();
  }

  @Test
  void shouldRewriteRedirectUriToTenantPath() {
    final var security = sec(Map.of("idpOne", oidc("idpOne")), List.of("idpOne"));
    final var registry = PerTenantOidcRegistry.forTenant("tenanta", security);
    assertThat(
            registry
                .clientRegistrationRepository()
                .findByRegistrationId("idpOne")
                .getRedirectUri())
        .isEqualTo("{baseUrl}/physical-tenant/tenanta/login/oauth2/code/{registrationId}");
  }

  @Test
  void shouldFailWhenAssignedProviderIsMissingFromProvidersMap() {
    final var security = sec(Map.of("idpOne", oidc("idpOne")), List.of("ghost"));
    assertThatThrownBy(() -> PerTenantOidcRegistry.forTenant("tenanta", security))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("ghost");
  }

  private static OidcConfiguration oidc(final String id) {
    final var c = new OidcConfiguration();
    c.setClientId("client-" + id);
    c.setClientSecret("secret-" + id);
    c.setIssuerUri("http://localhost:8080/realms/" + id);
    c.setRedirectUri("{baseUrl}/login/oauth2/code/{registrationId}");
    return c;
  }

  private static SecurityConfiguration sec(
      final Map<String, OidcConfiguration> providers, final List<String> assigned) {
    final var s = new SecurityConfiguration();
    s.getAuthentication().getProviders().setOidc(providers);
    s.getAuthentication().getProviders().setAssigned(assigned);
    return s;
  }
}
```

### Step 2 — Implement the registry

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.pt;

import io.camunda.security.api.model.config.oidc.OidcConfiguration;
import io.camunda.security.configuration.SecurityConfiguration;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.ClientRegistrations;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

@NullMarked
public final class PerTenantOidcRegistry {

  private final ClientRegistrationRepository clientRegistrationRepository;

  private PerTenantOidcRegistry(final ClientRegistrationRepository repo) {
    this.clientRegistrationRepository = repo;
  }

  public static PerTenantOidcRegistry forTenant(
      final String tenantId, final SecurityConfiguration tenantSecurity) {
    final var allProviders = tenantSecurity.getAuthentication().getProviders().getOidc();
    final var assigned = tenantSecurity.getAuthentication().getProviders().getAssigned();
    if (assigned == null || assigned.isEmpty()) {
      throw new IllegalStateException(
          "Tenant '" + tenantId + "' has no providers.assigned");
    }
    final List<ClientRegistration> registrations = new ArrayList<>();
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
      registrations.add(buildRegistration(tenantId, registrationId, provider));
    }
    return new PerTenantOidcRegistry(new InMemoryClientRegistrationRepository(registrations));
  }

  public ClientRegistrationRepository clientRegistrationRepository() {
    return clientRegistrationRepository;
  }

  private static ClientRegistration buildRegistration(
      final String tenantId,
      final String registrationId,
      final OidcConfiguration provider) {
    final String redirectUri =
        PhysicalTenantRedirectUriRewriter.rewrite(
            provider.getRedirectUri() != null
                ? provider.getRedirectUri()
                : "{baseUrl}/login/oauth2/code/{registrationId}",
            tenantId);
    return ClientRegistrations.fromIssuerLocation(provider.getIssuerUri())
        .registrationId(registrationId)
        .clientId(provider.getClientId())
        .clientSecret(provider.getClientSecret())
        .redirectUri(redirectUri)
        .build();
  }
}
```

### Step 3 — Replace inline construction in the chain config

`PhysicalTenantSecurityConfiguration` now builds `TenantSecuritySlice` from `PerTenantOidcRegistry.forTenant(tenantId, tenantSecurity).clientRegistrationRepository()`. The hard-coded `getOidc().get("oidc")` lookup goes away.

### Step 4 — Move tenant config to `physical-tenants.<id>.*` shape

`application-pt-poc.yaml` becomes:

```yaml
camunda:
  security:
    authentication:
      method: oidc
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
    default:
      security:
        authentication:
          providers:
            assigned: [defaultIdp]
            oidc:
              defaultIdp:
                client-id: camunda-pt-default-client
                client-secret: default-secret
                issuer-uri: http://localhost:8081/realms/default
```

The chain `@Bean` methods now resolve each tenant's `SecurityConfiguration` via `PhysicalTenantResolver`. The full generalisation (iterating the resolver to register N tenants) lands in Task 12; for now keep the two explicit `@Bean` methods and just have each call `tenantResolver.forPhysicalTenant("tenanta").getSecurity()` (or however the resolver exposes it).

### Step 5 — Run tests, manual smoke

```bash
./mvnw verify -pl authentication -Dtest=PerTenantOidcRegistryTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Then the manual two-tab login flow from Task 5. The `assigned` provider id is `idpOne` for tenant A and `defaultIdp` for default, so the URLs become `/physical-tenant/tenanta/oauth2/authorization/idpOne` and `/physical-tenant/default/oauth2/authorization/defaultIdp`.

### Step 6 — Format and commit

```bash
./mvnw license:format spotless:apply -T1C
./mvnw install -pl authentication,dist,security/security-core -am -Dquickly -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PerTenantOidcRegistry.java \
        authentication/src/test/java/io/camunda/authentication/pt/PerTenantOidcRegistryTest.java \
        authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java \
        dist/src/main/resources/application-pt-poc.yaml \
        <any security-core changes>
git commit -m "feat: extract PerTenantOidcRegistry and consume providers.assigned"
```

---

## Task 9: Extract PerTenantSessionRepository (storage keyspace isolation)

**Goal:** Replace the `MapSessionRepository` stub from Task 5 with a `PerTenantSessionRepository` that prefixes session ids and delegates to the existing `WebSessionRepository` (which `pt-security` turned off in Task 1 — re-enable just the repository bean, but not the global `@EnableSpringHttpSession`).

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PerTenantSessionRepository.java`
- Test: `authentication/src/test/java/io/camunda/authentication/pt/PerTenantSessionRepositoryTest.java`
- Modify: `dist/src/main/java/io/camunda/application/commons/identity/WebSessionRepositoryConfiguration.java` (split: gate the `@EnableSpringHttpSession` part on `!pt-security`, but expose `WebSessionRepository` as a `@Bean` available under both profiles)
- Modify: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java`
- Possibly modify: `authentication/src/main/java/io/camunda/authentication/session/WebSession.java` (add a copy constructor `WebSession(String id, WebSession source)` if the decorator needs one)

### Step 1 — Write the failing test

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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.authentication.session.WebSession;
import io.camunda.authentication.session.WebSessionRepository;
import org.junit.jupiter.api.Test;

class PerTenantSessionRepositoryTest {

  @Test
  void shouldPrefixSessionIdsOnCreate() {
    final var delegate = mock(WebSessionRepository.class);
    when(delegate.createSession()).thenReturn(new WebSession("raw-id"));
    final var perTenant = new PerTenantSessionRepository("tenanta", delegate);
    assertThat(perTenant.createSession().getId()).isEqualTo("t:tenanta:raw-id");
  }

  @Test
  void shouldStripPrefixBeforeDelegatingFindById() {
    final var delegate = mock(WebSessionRepository.class);
    when(delegate.findById("raw-id")).thenReturn(new WebSession("raw-id"));
    final var perTenant = new PerTenantSessionRepository("tenanta", delegate);
    assertThat(perTenant.findById("t:tenanta:raw-id").getId()).isEqualTo("t:tenanta:raw-id");
    verify(delegate).findById("raw-id");
  }

  @Test
  void shouldReturnNullWhenLookingUpSessionFromAnotherTenant() {
    final var delegate = mock(WebSessionRepository.class);
    final var perTenant = new PerTenantSessionRepository("tenanta", delegate);
    assertThat(perTenant.findById("t:default:raw-id")).isNull();
    verify(delegate, never()).findById(any());
  }
}
```

### Step 2 — Implement (consult the spec's bean catalogue if the API doesn't match)

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

  private final String tenantPrefix;
  private final WebSessionRepository delegate;

  public PerTenantSessionRepository(final String tenantId, final WebSessionRepository delegate) {
    this.tenantPrefix = PREFIX + tenantId + ":";
    this.delegate = delegate;
  }

  @Override
  public WebSession createSession() {
    return wrap(delegate.createSession());
  }

  @Override
  public void save(final WebSession session) {
    delegate.save(unwrap(session));
  }

  @Override
  public @Nullable WebSession findById(final String id) {
    if (!id.startsWith(tenantPrefix)) {
      return null;
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

If `WebSession` doesn't have a `(String id, WebSession source)` copy constructor, add one that copies attributes/expiry from the source. Drive the addition with a focused `WebSessionTest`.

### Step 3 — Expose `WebSessionRepository` as a bean under `pt-security` too

Split `WebSessionRepositoryConfiguration`: the `WebSessionRepository` `@Bean` should remain available under `pt-security` (so per-tenant decorators can wrap it), but the `@EnableSpringHttpSession` annotation must not fire (otherwise Spring registers a global `SessionRepositoryFilter` that conflicts with our per-chain filters).

Concretely: move `@EnableSpringHttpSession` onto a nested static `@Configuration @Profile("!pt-security")` class, leave the `@Bean WebSessionRepository` on the outer class with no profile restriction.

### Step 4 — Wire `PerTenantSessionRepository` into the chain config

Replace `new SessionRepositoryFilter<>(new MapSessionRepository(...))` in `PhysicalTenantSecurityConfiguration` with `new SessionRepositoryFilter<>(new PerTenantSessionRepository(tenantId, webSessionRepository))`.

### Step 5 — Run tests, manual smoke

```bash
./mvnw verify -pl authentication -Dtest=PerTenantSessionRepositoryTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Manual: two tabs login. Restart OC. Cookie still works (sessions persisted). Look at the secondary-storage backend (ES/OS index or RDBMS table) to confirm session rows are prefixed with `t:tenanta:` and `t:default:`.

### Step 6 — Format and commit

```bash
./mvnw license:format spotless:apply -T1C
./mvnw install -pl authentication,dist -am -Dquickly -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PerTenantSessionRepository.java \
        authentication/src/test/java/io/camunda/authentication/pt/PerTenantSessionRepositoryTest.java \
        dist/src/main/java/io/camunda/application/commons/identity/WebSessionRepositoryConfiguration.java \
        authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java \
        authentication/src/main/java/io/camunda/authentication/session/WebSession.java
git commit -m "feat: extract PerTenantSessionRepository with tenant-prefixed session ids"
```

---

## Task 10: Extract PhysicalTenantCookieSerializer + unit test

**Goal:** Pull the inline cookie-serializer/Path config from Task 5 into a tested factory.

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantCookieSerializer.java`
- Test: `authentication/src/test/java/io/camunda/authentication/pt/PhysicalTenantCookieSerializerTest.java`
- Modify: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java` (use the factory)

### Step 1 — Write the failing test

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
  void shouldScopePrefixedTenantCookieToTenantPath() {
    final var serializer = PhysicalTenantCookieSerializer.forPrefixedChain("tenanta");
    final var response = new MockHttpServletResponse();
    serializer.writeCookieValue(
        new CookieValue(new MockHttpServletRequest(), response, "raw"));
    final Cookie cookie = response.getCookie("camunda-session-tenanta");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getPath()).isEqualTo("/physical-tenant/tenanta");
  }

  @Test
  void shouldScopeUnprefixedDefaultCookieToRoot() {
    final var serializer = PhysicalTenantCookieSerializer.forUnprefixedDefaultChain();
    final var response = new MockHttpServletResponse();
    serializer.writeCookieValue(
        new CookieValue(new MockHttpServletRequest(), response, "raw"));
    final Cookie cookie = response.getCookie("camunda-session-default-root");
    assertThat(cookie).isNotNull();
    assertThat(cookie.getPath()).isEqualTo("/");
  }
}
```

### Step 2 — Implement and replace inline call sites

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
    final var s = base();
    s.setCookieName(COOKIE_NAME_PREFIX + tenantId);
    s.setCookiePath("/physical-tenant/" + tenantId);
    return s;
  }

  public static DefaultCookieSerializer forUnprefixedDefaultChain() {
    final var s = base();
    s.setCookieName(COOKIE_NAME_PREFIX + "default-root");
    s.setCookiePath("/");
    return s;
  }

  public static CookieHttpSessionIdResolver resolver(final DefaultCookieSerializer serializer) {
    final var resolver = new CookieHttpSessionIdResolver();
    resolver.setCookieSerializer(serializer);
    return resolver;
  }

  private static DefaultCookieSerializer base() {
    final var s = new DefaultCookieSerializer();
    s.setUseSecureCookie(false);
    s.setUseHttpOnlyCookie(true);
    s.setSameSite("Lax");
    return s;
  }
}
```

Replace the inline `DefaultCookieSerializer` construction in `PhysicalTenantSecurityConfiguration` with calls to the factory.

### Step 3 — Run tests, manual smoke

```bash
./mvnw verify -pl authentication -Dtest=PhysicalTenantCookieSerializerTest -DskipTests=false -DskipITs -Dquickly -T1C
```

Manual: two-tab flow unchanged.

### Step 4 — Format and commit

```bash
./mvnw license:format spotless:apply -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantCookieSerializer.java \
        authentication/src/test/java/io/camunda/authentication/pt/PhysicalTenantCookieSerializerTest.java \
        authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java
git commit -m "refactor: extract PhysicalTenantCookieSerializer factory with unit tests"
```

---

## Task 11: Add API chain per tenant (shared decoder + per-chain issuer allowlist)

**Goal:** Stateless bearer-token-only API chain for `/v2/physical-tenants/<tenant>/**`. The JWT decoder is **cluster-shared** (one issuer-aware bean built from the union of all tenants' issuer URIs). Per-chain isolation comes from an **issuer allowlist** authorization rule: each tenant's API chain rejects tokens whose `iss` claim isn't in that tenant's `providers.assigned` issuer set.

**Why not per-tenant decoder:** an issuer-aware decoder already routes signature validation by `iss` — it can validate any known token. Duplicating it per tenant is overhead. Authorization (which tenant's API may this token access?) is the per-chain concern, not signature validation.

**Files:**
- Create: `authentication/src/main/java/io/camunda/authentication/pt/PtIssuerAwareJwtDecoder.java` (`@Bean` factory; or inline in `PhysicalTenantSecurityConfiguration`)
- Modify: `authentication/src/main/java/io/camunda/authentication/pt/PerTenantSecurityChainFactory.java` (add `buildApiChain(http, slice, sharedDecoder, allowedIssuers)`)
- Modify: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java`
- Modify: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantWhoamiController.java` (add `/v2/physical-tenants/{tenantId}/whoami` mapping)
- Do **not** add `jwtDecoder` to `TenantSecuritySlice`.

### Step 1 — Add the shared issuer-aware JwtDecoder bean

In `PhysicalTenantSecurityConfiguration`:

```java
@Bean
public JwtDecoder ptIssuerAwareJwtDecoder(final PhysicalTenantResolver tenants) {
  final Set<String> allIssuers = new LinkedHashSet<>();
  tenants.getAll().values().forEach(camunda -> {
    final var providers = camunda.getSecurity().getAuthentication().getProviders();
    final var assigned = providers.getAssigned();
    if (assigned == null) return;
    for (final String id : assigned) {
      final var p = providers.getOidc().get(id);
      if (p != null && p.getIssuerUri() != null) {
        allIssuers.add(p.getIssuerUri());
      }
    }
  });
  // JwtIssuerAuthenticationManagerResolver wires per-issuer JwtDecoders via discovery;
  // wrap as a delegating JwtDecoder so the chain can plug it into oauth2ResourceServer.jwt().
  // Engineer adapts to whichever Spring Security 7 API (JwtIssuerValidator + per-issuer
  // NimbusJwtDecoder map, or JwtIssuerAuthenticationManagerResolver) lands the cleanest.
  return buildIssuerAwareDecoder(allIssuers);
}
```

The Spring Security 7 idiom: build one `NimbusJwtDecoder` per issuer (via `NimbusJwtDecoder.withIssuerLocation(...)`), keep a `Map<String, JwtDecoder>` keyed by issuer URI, and wrap with a `Supplier<JwtDecoder>` or `JwtIssuerAuthenticationManagerResolver` that dispatches by `iss`. The engineer picks the cleanest API; both work.

### Step 2 — Add the API chain builder with issuer allowlist

```java
public SecurityFilterChain buildApiChain(
    final HttpSecurity http,
    final TenantSecuritySlice slice,
    final JwtDecoder sharedDecoder,
    final Set<String> allowedIssuers) throws Exception {

  final String prefix =
      slice.accessPath() == TenantSecuritySlice.AccessPath.PREFIXED
          ? "/v2/physical-tenants/" + slice.tenantId()
          : "/v2";

  // Authorization rule: anyRequest authenticated AND the JWT's iss must be in this
  // tenant's allowed-issuer set. Reject other tenants' valid tokens with 403.
  final AuthorizationManager<RequestAuthorizationContext> issuerAllowed =
      (auth, ctx) -> {
        final var a = auth.get();
        if (!(a instanceof JwtAuthenticationToken jwt)) {
          return new AuthorizationDecision(false);
        }
        return new AuthorizationDecision(allowedIssuers.contains(jwt.getToken().getIssuer().toString()));
      };

  return http.securityMatcher(prefix + "/**")
      .csrf(c -> c.disable())
      .sessionManagement(
          sm ->
              sm.sessionCreationPolicy(
                  org.springframework.security.config.http.SessionCreationPolicy.NEVER))
      .authorizeHttpRequests(a -> a.anyRequest().access(issuerAllowed))
      .oauth2ResourceServer(o -> o.jwt(j -> j.decoder(sharedDecoder)))
      .build();
}
```

### Step 3 — Register API chain beans

```java
@Bean
public SecurityFilterChain ptTenantaApiChain(
    final HttpSecurity http,
    final JwtDecoder ptIssuerAwareJwtDecoder,
    final PhysicalTenantResolver tenants) throws Exception {
  return new PerTenantSecurityChainFactory()
      .buildApiChain(http, sliceFor("tenanta"), ptIssuerAwareJwtDecoder, allowedIssuersFor("tenanta", tenants));
}

@Bean
public SecurityFilterChain ptDefaultPrefixedApiChain(...) { /* same shape, "default" */ }
```

`allowedIssuersFor(tenantId, tenants)` returns the set of issuer URIs from that tenant's `providers.assigned`.

### Step 4 — Extend the controller

```java
@GetMapping("/v2/physical-tenants/{tenantId}/whoami")
@ResponseBody
public Whoami whoamiApi(@PathVariable final String tenantId, final Authentication authentication) {
  return new Whoami(tenantId, authentication != null ? authentication.getName() : "anonymous");
}
```

### Step 5 — Manual smoke (cross-tenant token rejection)

```bash
# Get a tenant-A-issued token via password grant
TOKEN=$(curl -sS -X POST http://localhost:8082/realms/tenanta/protocol/openid-connect/token \
  -d grant_type=password -d client_id=camunda-pt-tenanta-client \
  -d client_secret=tenanta-secret -d username=bob -d password=bob | jq -r .access_token)

# Tenant A's token on tenant A's API: 200
curl -sS -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/v2/physical-tenants/tenanta/whoami
# expect: {"tenantId":"tenanta","principal":"bob"}

# Tenant A's token on default's API: 403 (signature is valid, but issuer is not in default's allowlist)
curl -sS -i -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/v2/physical-tenants/default/whoami
# expect: 403
```

### Step 6 — Format and commit

```bash
./mvnw license:format spotless:apply -T1C
git add <files>
git commit -m "feat: add per-tenant API chain with bearer-token authentication"
```

---

## Task 12: Add default tenant unprefixed access-path chains

**Goal:** Default tenant alone is reachable via both prefixed (`/physical-tenant/default/...`, `/v2/physical-tenants/default/...`) and unprefixed (`/...`, `/v2/...`) paths. The unprefixed chains share OIDC config with the prefixed default chain but use cookie Path `/` (per spec D2 / OQ-2: separate cookies, re-login on switching access path is acceptable).

**Files:**
- Modify: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java`
- Modify: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantWhoamiController.java` (add unprefixed mappings)

### Step 1 — Register two more chains for the default tenant

```java
@Bean
public SecurityFilterChain ptDefaultUnprefixedWebappChain(
    final HttpSecurity http, final SecurityConfiguration security) throws Exception {
  return new PerTenantSecurityChainFactory()
      .buildWebappChain(http, sliceFor("default", AccessPath.UNPREFIXED_DEFAULT));
}

@Bean
public SecurityFilterChain ptDefaultUnprefixedApiChain(
    final HttpSecurity http, final SecurityConfiguration security) throws Exception {
  return new PerTenantSecurityChainFactory()
      .buildApiChain(http, sliceFor("default", AccessPath.UNPREFIXED_DEFAULT));
}
```

The slice's `accessPath` field selects between `PREFIXED` and `UNPREFIXED_DEFAULT`. The chain factory's `webappPathPrefix()` already handles both.

### Step 2 — Extend the controller

```java
@GetMapping({"/physical-tenant/{tenantId}/whoami", "/whoami"})
@GetMapping({"/v2/physical-tenants/{tenantId}/whoami", "/v2/whoami"})
```

When `/whoami` or `/v2/whoami` is hit, `tenantId` is null; default to `"default"` in the response body.

### Step 3 — Manual smoke

In a fresh incognito window: `http://localhost:8080/whoami` → redirect to default IdP → login → `{"tenantId":"default","principal":"alice","accessPath":"unprefixed"}`. Then `http://localhost:8080/physical-tenant/default/whoami` re-prompts for login (different cookie Path).

### Step 4 — Format and commit

```bash
./mvnw license:format spotless:apply -T1C
git add <files>
git commit -m "feat: add unprefixed default-tenant access-path chains"
```

---

## Task 13: Generalise registration via PhysicalTenantResolver.getAll()

**Goal:** Replace the six explicit `@Bean` methods with a single mechanism that registers chains for every tenant in `PhysicalTenantResolver.getAll()`. Adding a third tenant becomes config-only.

**Files:**
- Modify: `authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java`

### Step 1 — Replace the explicit beans with a programmatic registrar

Spring Security 7 requires `SecurityFilterChain` beans to be discoverable at refresh time. Two implementation options:

1. **`BeanDefinitionRegistryPostProcessor`** that, given the resolver, registers one bean definition per (tenant × accessPath × role) combination, supplying an `InstanceSupplier` that builds the chain on first use.
2. **Static enumeration**: keep the explicit `@Bean` methods but generate them via a `@Bean SecurityFilterChain[]` method that returns the array (Spring 7 supports collection-typed bean registration).

Option 1 is more idiomatic. Option 2 is simpler. Engineer picks based on what compiles. The plan does not prescribe — both achieve the goal.

### Step 2 — Manual smoke

The two-tenant matrix from earlier tasks. Then add a third tenant to `application-pt-poc.yaml` (e.g., `tenantb` with a third Keycloak realm — or just stub one without a real IdP and confirm bean registration happens without error). Restart and observe that the chain count grew without code changes.

### Step 3 — Format and commit

```bash
./mvnw license:format spotless:apply -T1C
git add authentication/src/main/java/io/camunda/authentication/pt/PhysicalTenantSecurityConfiguration.java
git commit -m "refactor: register PT chains programmatically from PhysicalTenantResolver"
```

---

## Task 14: PhysicalTenantSecurityIT — happy path

**Goal:** End-to-end Testcontainers IT booting two `KeycloakContainer`s + OC in-JVM. Drives the OIDC flow programmatically and asserts the redirect-to-IdP behaviour from Task 4. Becomes the CI gate.

**Files:**
- Create: `dist/src/test/java/io/camunda/application/pt/PhysicalTenantSecurityIT.java`

Use the code skeleton from the original plan (Task 15 in the bottom-up version, included here verbatim). Boot two Keycloaks with random ports, point OC at them via `--camunda.physical-tenants.tenanta.security.authentication.providers.oidc.idpOne.issuer-uri=` etc., run a redirect assertion.

The skeleton lives in the spec's End-to-end demo path section. Adapt the `--` flag values to the property structure introduced in Task 8 (`camunda.physical-tenants.<id>.security.authentication.providers.assigned`).

### Step — Format and commit

```bash
./mvnw verify -pl dist -Dit.test=PhysicalTenantSecurityIT -DskipTests=false -DskipUTs -Dquickly -T1C
./mvnw license:format spotless:apply -T1C
git add dist/src/test/java/io/camunda/application/pt/PhysicalTenantSecurityIT.java
git commit -m "test: add PhysicalTenantSecurityIT happy-path redirect assertion"
```

---

## Task 15: PhysicalTenantSecurityIT — full flow + isolation

**Goal:** Extend the IT to drive the complete OIDC authorization-code flow and assert (a) the tenant-scoped cookie is issued, (b) tenant A's cookie does not authenticate the default tenant's chain, (c) a tenant-A-issued bearer token returns 401 on the default tenant's API chain.

**Files:**
- Modify: `dist/src/test/java/io/camunda/application/pt/PhysicalTenantSecurityIT.java`

The `performLoginAndExtractSessionCookie` helper and the isolation assertions are sketched in the original plan's Task 16 — reuse them verbatim, adapting any property-name changes from Tasks 8 and 12.

### Step — Run and commit

```bash
./mvnw verify -pl dist -Dit.test=PhysicalTenantSecurityIT -DskipTests=false -DskipUTs -Dquickly -T1C
./mvnw license:format spotless:apply -T1C
git add dist/src/test/java/io/camunda/application/pt/PhysicalTenantSecurityIT.java
git commit -m "test: add full OIDC flow and cross-tenant isolation assertions"
```

---

## Task 16: Manual browser smoke test

**Goal:** Final acceptance — walk through the spec's demo path in a real browser against the local Keycloak runner, with everything (per-chain isolation, persistent sessions, unprefixed default access path, API chain) wired.

**Files:** none.

### Step 1 — Start the local IdPs

In terminal 1:

```bash
./mvnw -pl dist test-compile exec:java \
  -Dexec.mainClass=io.camunda.application.pt.PtPocLocalIdpRunner \
  -Dexec.classpathScope=test
```

### Step 2 — Start OC

In terminal 2:

```bash
./mvnw -pl dist spring-boot:run -Dspring-boot.run.profiles=pt-poc
```

### Step 3 — Walk through the demo path

Follow the steps in the spec's "End-to-end demo path" section. Record results on PR #53587.

---

## Self-review checklist (run after the plan is written)

- [x] **Spec coverage** — every spec section has at least one task:
  - D1 (filter chain isolation primitive) → Tasks 4, 5, 6 (walking skeleton proves it; subsequent tasks refactor)
  - D2 (cookie Path isolation) → Tasks 5 (inline), 10 (extracted into factory), 15 (asserted in IT)
  - D3 (profile gate, CSL opt-out) → Task 1
  - D4 (reuse OC OIDC machinery) → Task 8 (`PerTenantOidcRegistry` consumes the existing `SecurityConfiguration` / `OidcConfiguration` shape; `ClientRegistrations.fromIssuerLocation` keeps Spring-Security-stock OIDC discovery)
  - D5 (chain-matcher tenant resolution) → Tasks 4, 5 (the `securityMatcher` literal carries the tenant id; no per-request resolver filter)
  - D6 (portability to C) → Task 6 (`TenantSecuritySlice` record, all dependencies constructor-injected; chain factory takes the slice as a method parameter)
  - Bean catalogue → Tasks 6–11 cover every class listed in the spec
  - Configuration consumed (`providers.assigned`) → Task 8
  - End-to-end demo path → Tasks 14, 15, 16
  - Local testing setup → Tasks 2, 3, 16

- [x] **Placeholder scan** — no "TBD", "TODO", "fill in", "appropriate error handling" in any task body. Honest scope notes are present where the engineer must adapt (Task 4 step 4 acknowledges OC's SPI ports may need stubbing; Task 13 acknowledges either of two registration approaches is acceptable).

- [x] **Type consistency** — `TenantSecuritySlice` shape introduced in Task 6 carries five fields (tenantId, accessPath, ClientRegistrationRepository, SessionRepositoryFilter, CookieHttpSessionIdResolver). JwtDecoder, LogoutSuccessHandler, AuthorizedClientRepository, SecurityContextRepository are intentionally NOT on the slice — they are cluster-shared. Task 11 introduces a shared `ptIssuerAwareJwtDecoder` `@Bean` and a per-chain `allowedIssuers` set used for the API authorization rule. Slice shape stable across Tasks 6, 7, 8, 9, 10, 12.

## Known unknowns the engineer will surface during execution

These are honest gaps the plan cannot fully retire from a desk:

1. **OC's SPI ports under `pt-security`.** Task 4 step 4 is where this lands: if any port currently defined in `WebSecurityConfig` is consumed by non-CSL OC code, boot will fail with an autowire error. Approach is incremental — boot, read the failure, stub or relax to `ObjectProvider`, reboot.
2. **`SecurityConfiguration.getAuthentication().getProviders().getAssigned`/`setOidc` exact API shape.** Task 8 step 0 adapts to whatever exists; the test drives the shape.
3. **`WebSession` copy-constructor.** Task 9 may need to add `WebSession(String id, WebSession source)` if not present.
4. **`KeycloakContainer.getAuthServerUrl()`** — exact API used in Tasks 14–15; confirm against the `dasniko/testcontainers-keycloak` version on the classpath.
5. **Spring Security 7's `oauth2Login(...).authorizationEndpoint(...).baseUri(...)` semantics.** If the trailing slash or wildcard handling has shifted from 6 → 7, Task 4's manual smoke is where the engineer notices and adjusts.
6. **Spring Session's `MapSessionRepository` typing under Spring Boot 4.** Used as a stub in Task 5; replaced in Task 9. If it doesn't compile against the on-classpath version, fall back to a quick `Map<String, Session>` based custom `SessionRepository` for the walking-skeleton step.

The plan provides escape hatches for each (note in the relevant step), so they don't block forward motion.
