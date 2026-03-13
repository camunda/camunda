# OIDC Integration Tests Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add OIDC integration tests using Testcontainers + Keycloak, covering both Bearer JWT API and OAuth2 webapp login flows.

**Architecture:** Two test classes — `OidcApiIntegrationTest` (MockMvc + Bearer JWT) and `OidcWebappLoginIntegrationTest` (embedded server + HttpClient redirect dance). Both share a Keycloak container via a singleton pattern. Existing test app and stub adapters are reused with minimal changes to support webapp OIDC paths.

**Tech Stack:** JUnit 5, Testcontainers, dasniko/testcontainers-keycloak, MockMvc, Java HttpClient

**Spec:** `docs/superpowers/specs/2026-03-13-oidc-integration-tests-design.md`

---

## Chunk 1: Infrastructure

### Task 1: Add test dependencies

**Files:**
- Modify: `gatekeeper-spring-boot-starter/pom.xml` (add test dependencies)

- [ ] **Step 1: Add test dependencies to starter POM**

In `gatekeeper-spring-boot-starter/pom.xml`, add after the existing test dependencies. `org.testcontainers` artifacts are version-managed by the Spring Boot BOM. `testcontainers-keycloak` needs an explicit version since it is not in the BOM.

```xml
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>testcontainers</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>junit-jupiter</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>com.github.dasniko</groupId>
  <artifactId>testcontainers-keycloak</artifactId>
  <version>3.5.1</version>
  <scope>test</scope>
</dependency>
```

- [ ] **Step 2: Verify compilation**

Run: `cd /Users/ben.sheppard/code/camunda/gatekeeper && ./mvnw compile -T1C -q -pl gatekeeper-spring-boot-starter`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add gatekeeper-spring-boot-starter/pom.xml
git commit -m "build: add Testcontainers and Keycloak test dependencies"
```

---

### Task 2: Create Keycloak realm import file

**Files:**
- Create: `gatekeeper-spring-boot-starter/src/test/resources/keycloak/gatekeeper-test-realm.json`

- [ ] **Step 1: Create the realm JSON**

```json
{
  "realm": "gatekeeper-test",
  "enabled": true,
  "sslRequired": "none",
  "registrationAllowed": false,
  "loginWithEmailAllowed": true,
  "users": [
    {
      "username": "demo",
      "enabled": true,
      "email": "demo@example.com",
      "firstName": "Demo",
      "lastName": "User",
      "credentials": [
        {
          "type": "password",
          "value": "demo",
          "temporary": false
        }
      ]
    },
    {
      "username": "operator",
      "enabled": true,
      "email": "operator@example.com",
      "firstName": "Operator",
      "lastName": "User",
      "credentials": [
        {
          "type": "password",
          "value": "operator",
          "temporary": false
        }
      ]
    }
  ],
  "clients": [
    {
      "clientId": "gatekeeper-test-client",
      "enabled": true,
      "clientAuthenticatorType": "client-secret",
      "secret": "test-secret",
      "redirectUris": ["*"],
      "webOrigins": ["*"],
      "directAccessGrantsEnabled": true,
      "publicClient": false,
      "protocol": "openid-connect",
      "standardFlowEnabled": true,
      "protocolMappers": [
        {
          "name": "preferred_username",
          "protocol": "openid-connect",
          "protocolMapper": "oidc-usermodel-attribute-mapper",
          "config": {
            "user.attribute": "username",
            "claim.name": "preferred_username",
            "id.token.claim": "true",
            "access.token.claim": "true",
            "jsonType.label": "String"
          }
        }
      ]
    }
  ]
}

**Note:** The `protocolMappers` entry ensures `preferred_username` is included in the **access token**, not just the ID token. Without this, some Keycloak versions only put `preferred_username` in the ID token, and the Bearer JWT API tests would receive a UUID `sub` claim instead, causing `StubMembershipResolver` to fall through to the default case.
```

- [ ] **Step 2: Commit**

```bash
git add gatekeeper-spring-boot-starter/src/test/resources/keycloak/
git commit -m "test: add Keycloak realm import for OIDC integration tests"
```

---

### Task 3: Create application-oidc.yml

**Files:**
- Create: `gatekeeper-spring-boot-starter/src/test/resources/application-oidc.yml`

- [ ] **Step 1: Create the OIDC profile config**

```yaml
camunda:
  security:
    authentication:
      method: OIDC
      oidc:
        username-claim: preferred_username
        client-id-claim: azp
```

The `issuer-uri`, `client-id`, and `client-secret` are injected dynamically via `@DynamicPropertySource` from the Keycloak container at test startup.

**Critical:** `username-claim` must be `preferred_username` — Keycloak's `sub` claim is a UUID, not the username string. `StubMembershipResolver` matches on `"demo"` and `"operator"` strings.

- [ ] **Step 2: Commit**

```bash
git add gatekeeper-spring-boot-starter/src/test/resources/application-oidc.yml
git commit -m "test: add OIDC profile configuration for integration tests"
```

---

### Task 4: Update test infrastructure for webapp OIDC paths

**Files:**
- Modify: `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/integration/app/adapter/StubSecurityPathProvider.java`
- Modify: `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/integration/app/controller/TestApiController.java`

The OIDC webapp security filter chain's `securityMatcher` uses `webappPaths()`. For the OAuth2 redirect flow to work, the following paths must match:
- `/oauth2/**` — Spring Security's authorization request initiation
- `/sso-callback` — OAuth2 redirect callback (configured in `GatekeeperSecurityFilterChainAutoConfiguration.REDIRECT_URI`)
- `/app/**` — a testable webapp endpoint

- [ ] **Step 1: Update StubSecurityPathProvider.webappPaths()**

Change `webappPaths()` in `StubSecurityPathProvider.java` from:

```java
@Override
public Set<String> webappPaths() {
  return Set.of("/login/**", "/logout");
}
```

to:

```java
@Override
public Set<String> webappPaths() {
  return Set.of("/login/**", "/logout", "/oauth2/**", "/sso-callback", "/app/**");
}
```

- [ ] **Step 2: Add webapp test endpoint to TestApiController**

Add this method to `TestApiController.java`:

```java
/** Protected webapp endpoint — requires session-based authentication (OIDC login flow). */
@GetMapping("/app/test/identity")
public Map<String, Object> webappIdentity() {
  final var auth = authProvider.getCamundaAuthentication();
  return Map.of(
      "username", auth.authenticatedUsername() != null ? auth.authenticatedUsername() : "",
      "groups", auth.authenticatedGroupIds(),
      "tenants", auth.authenticatedTenantIds(),
      "roles", auth.authenticatedRoleIds(),
      "anonymous", auth.isAnonymous());
}
```

- [ ] **Step 3: Verify existing BasicAuth tests still pass**

Run: `cd /Users/ben.sheppard/code/camunda/gatekeeper && ./mvnw test -pl gatekeeper-spring-boot-starter -Dtest="BasicAuthIntegrationTest" -q`
Expected: All tests pass. The new webapp paths and endpoint don't affect basic auth behavior.

- [ ] **Step 4: Commit**

```bash
git add gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/integration/app/adapter/StubSecurityPathProvider.java
git add gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/integration/app/controller/TestApiController.java
git commit -m "test: add OIDC webapp paths and endpoint to test infrastructure"
```

---

## Chunk 2: Test Classes

### Task 5: Create OidcApiIntegrationTest

**Files:**
- Create: `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/integration/KeycloakTestSupport.java`
- Create: `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/integration/OidcApiIntegrationTest.java`

This test class uses MockMvc with Bearer JWT tokens obtained from the Keycloak container. It mirrors the structure of `BasicAuthIntegrationTest` with `@Nested` groups and `@DisplayName` annotations.

Both OIDC test classes share a single Keycloak container via the `KeycloakTestSupport` helper to avoid starting two containers during the test run.

- [ ] **Step 1: Create the shared Keycloak container helper**

Create `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/integration/KeycloakTestSupport.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dasniko.testcontainers.keycloak.KeycloakContainer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Shared Keycloak Testcontainer for OIDC integration tests. Uses the singleton container pattern to
 * avoid starting multiple Keycloak instances across test classes.
 */
final class KeycloakTestSupport {

  static final String CLIENT_ID = "gatekeeper-test-client";
  static final String CLIENT_SECRET = "test-secret";

  static final KeycloakContainer KEYCLOAK =
      new KeycloakContainer().withRealmImportFile("keycloak/gatekeeper-test-realm.json");

  private static final ObjectMapper MAPPER = new ObjectMapper();

  static {
    KEYCLOAK.start();
  }

  private KeycloakTestSupport() {}

  static void configureOidc(final DynamicPropertyRegistry registry) {
    final var issuerUri = KEYCLOAK.getAuthServerUrl() + "/realms/gatekeeper-test";
    registry.add("camunda.security.authentication.oidc.issuer-uri", () -> issuerUri);
    registry.add("camunda.security.authentication.oidc.client-id", () -> CLIENT_ID);
    registry.add("camunda.security.authentication.oidc.client-secret", () -> CLIENT_SECRET);
  }

  static String obtainAccessToken(final String username, final String password) throws Exception {
    final var tokenUri =
        KEYCLOAK.getAuthServerUrl() + "/realms/gatekeeper-test/protocol/openid-connect/token";
    final var body =
        "grant_type=password&client_id=%s&client_secret=%s&username=%s&password=%s"
            .formatted(CLIENT_ID, CLIENT_SECRET, username, password);

    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(tokenUri))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

    try (var client = HttpClient.newHttpClient()) {
      final var response = client.send(request, HttpResponse.BodyHandlers.ofString());
      final JsonNode json = MAPPER.readTree(response.body());
      return json.get("access_token").asText();
    }
  }
}
```

- [ ] **Step 2: Write the test class**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.integration;

import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.camunda.gatekeeper.spring.integration.app.TestComponentApplication;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * OIDC API integration test that boots a Spring Boot application with gatekeeper configured for OIDC
 * authentication. Uses a Keycloak Testcontainer as the identity provider. Tests verify the Bearer
 * JWT authentication pipeline for API endpoints.
 *
 * <p>This test mirrors {@link BasicAuthIntegrationTest} but uses OIDC/JWT instead of HTTP Basic.
 */
@SpringBootTest(classes = TestComponentApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("oidc")
final class OidcApiIntegrationTest {

  @Autowired private MockMvc mockMvc;

  private static String demoToken;
  private static String operatorToken;

  @DynamicPropertySource
  static void configureOidc(final DynamicPropertyRegistry registry) {
    KeycloakTestSupport.configureOidc(registry);
  }

  @BeforeAll
  static void obtainTokens() throws Exception {
    demoToken = KeycloakTestSupport.obtainAccessToken("demo", "demo");
    operatorToken = KeycloakTestSupport.obtainAccessToken("operator", "operator");
  }

  @Nested
  @DisplayName("Protected API endpoints")
  class ProtectedApiTests {

    @Test
    @DisplayName("unauthenticated request returns 401")
    void unauthenticatedRequestShouldReturn401() throws Exception {
      mockMvc.perform(get("/v2/test/identity")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("invalid token returns 401")
    void invalidTokenShouldReturn401() throws Exception {
      mockMvc
          .perform(
              get("/v2/test/identity")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("valid token returns 200 with resolved identity")
    void validTokenShouldReturnResolvedIdentity() throws Exception {
      mockMvc
          .perform(
              get("/v2/test/identity")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + demoToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.username").value("demo"))
          .andExpect(jsonPath("$.anonymous").value(false));
    }
  }

  @Nested
  @DisplayName("Membership resolution")
  class MembershipResolutionTests {

    @Test
    @DisplayName("demo user gets correct groups, roles, and tenants from MembershipResolver")
    void demoUserShouldHaveExpectedMemberships() throws Exception {
      mockMvc
          .perform(
              get("/v2/test/identity")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + demoToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.groups", contains("engineering", "admins")))
          .andExpect(jsonPath("$.roles", contains("operator")))
          .andExpect(jsonPath("$.tenants", contains("tenant-alpha", "tenant-beta")));
    }

    @Test
    @DisplayName("different user gets different memberships")
    void operatorUserShouldHaveDifferentMemberships() throws Exception {
      mockMvc
          .perform(
              get("/v2/test/identity")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + operatorToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.username").value("operator"))
          .andExpect(jsonPath("$.groups", contains("ops")))
          .andExpect(jsonPath("$.roles", contains("viewer")))
          .andExpect(jsonPath("$.tenants", contains("tenant-alpha")));
    }
  }

  @Nested
  @DisplayName("User info endpoint (CamundaUserProvider pipeline)")
  class UserInfoTests {

    @Test
    @DisplayName("returns assembled user info with profile, components, and memberships")
    void shouldReturnFullUserInfo() throws Exception {
      mockMvc
          .perform(
              get("/v2/test/user")
                  .header(HttpHeaders.AUTHORIZATION, "Bearer " + demoToken))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.displayName").value("Demo User"))
          .andExpect(jsonPath("$.username").value("demo"))
          .andExpect(jsonPath("$.email").value("demo@example.com"))
          .andExpect(jsonPath("$.canLogout").value(true))
          .andExpect(jsonPath("$.authorizedComponents").isArray())
          .andExpect(jsonPath("$.tenants").isArray())
          .andExpect(jsonPath("$.groups").isArray())
          .andExpect(jsonPath("$.roles").isArray());
    }
  }

  @Nested
  @DisplayName("Unprotected paths")
  class UnprotectedPathTests {

    @Test
    @DisplayName("actuator health is accessible without authentication")
    void healthShouldBeAccessibleWithoutAuth() throws Exception {
      mockMvc
          .perform(get("/actuator/health"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("unprotected API paths are accessible without authentication")
    void licenseShouldBeAccessibleWithoutAuth() throws Exception {
      mockMvc
          .perform(get("/v2/license"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.type").value("test-license"));
    }
  }
}
```

- [ ] **Step 2: Run the test**

Run: `cd /Users/ben.sheppard/code/camunda/gatekeeper && ./mvnw test -pl gatekeeper-spring-boot-starter -Dtest="OidcApiIntegrationTest" -q`
Expected: All tests pass. Keycloak container starts, tokens are obtained, Bearer JWT authentication works.

- [ ] **Step 3: Commit**

```bash
git add gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/integration/KeycloakTestSupport.java
git add gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/integration/OidcApiIntegrationTest.java
git commit -m "test: add OIDC API integration tests with Keycloak Testcontainer"
```

---

### Task 6: Create OidcWebappLoginIntegrationTest

**Files:**
- Create: `gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/integration/OidcWebappLoginIntegrationTest.java`

This test uses `RANDOM_PORT` with a real HTTP client to perform the full OAuth2 redirect dance against Keycloak. It follows redirects manually, submits the login form to Keycloak, and verifies session-based authentication is established.

- [ ] **Step 1: Write the test class**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.gatekeeper.spring.integration;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.gatekeeper.spring.integration.app.TestComponentApplication;
import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * OIDC webapp login integration test that performs the full OAuth2 authorization code redirect flow
 * against a Keycloak Testcontainer. Uses Java {@link HttpClient} with manual redirect following and
 * cookie-based session tracking.
 */
@SpringBootTest(classes = TestComponentApplication.class, webEnvironment = WebEnvironment.RANDOM_PORT)
@ActiveProfiles("oidc")
final class OidcWebappLoginIntegrationTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final Pattern FORM_ACTION_PATTERN =
      Pattern.compile("<form[^>]*action=\"([^\"]+)\"");

  @LocalServerPort private int port;

  private HttpClient httpClient;

  @DynamicPropertySource
  static void configureOidc(final DynamicPropertyRegistry registry) {
    KeycloakTestSupport.configureOidc(registry);
  }

  @BeforeEach
  void setUp() {
    httpClient =
        HttpClient.newBuilder()
            .cookieHandler(new CookieManager())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
  }

  @AfterEach
  void tearDown() throws Exception {
    httpClient.close();
  }

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  @Nested
  @DisplayName("OAuth2 webapp login flow")
  class WebappLoginTests {

    @Test
    @DisplayName("accessing protected webapp path redirects to OAuth2 authorization")
    void protectedWebappPathShouldRedirectToAuthorization() throws Exception {
      final var response = httpGet(baseUrl() + "/app/test/identity");

      assertThat(response.statusCode()).isEqualTo(302);
      assertThat(response.headers().firstValue("Location"))
          .isPresent()
          .get()
          .asString()
          .contains("/oauth2/authorization/");
    }

    @Test
    @DisplayName("full login flow results in authenticated session")
    void fullLoginFlowShouldEstablishAuthenticatedSession() throws Exception {
      // Step 1: Access protected webapp path → redirect to /oauth2/authorization/{registrationId}
      final var step1 = httpGet(baseUrl() + "/app/test/identity");
      assertThat(step1.statusCode()).isEqualTo(302);
      final var authorizationUrl = step1.headers().firstValue("Location").orElseThrow();

      // Step 2: Follow to Spring's authorization endpoint → redirect to Keycloak
      final var step2 = httpGet(resolveUrl(authorizationUrl));
      assertThat(step2.statusCode()).isEqualTo(302);
      final var keycloakAuthUrl = step2.headers().firstValue("Location").orElseThrow();

      // Step 3: GET Keycloak's authorization page → login form HTML
      final var step3 = httpGet(keycloakAuthUrl);
      assertThat(step3.statusCode()).isEqualTo(200);
      final var loginFormAction = extractFormAction(step3.body());

      // Step 4: POST credentials to Keycloak → redirect back to app with auth code
      final var step4 = httpPostForm(loginFormAction, "username=demo&password=demo");
      assertThat(step4.statusCode()).isEqualTo(302);
      final var callbackUrl = step4.headers().firstValue("Location").orElseThrow();
      assertThat(callbackUrl).contains("/sso-callback").contains("code=");

      // Step 5: Follow callback → Spring exchanges code for tokens, redirects
      final var step5 = httpGet(callbackUrl);
      assertThat(step5.statusCode()).isEqualTo(302);
      final var finalRedirect = step5.headers().firstValue("Location").orElseThrow();

      // Step 6: Follow final redirect → authenticated response
      final var step6 = httpGet(resolveUrl(finalRedirect));
      assertThat(step6.statusCode()).isEqualTo(200);

      // Verify the response contains the authenticated user's identity
      @SuppressWarnings("unchecked")
      final Map<String, Object> identity = MAPPER.readValue(step6.body(), Map.class);
      assertThat(identity.get("username")).isEqualTo("demo");
      assertThat(identity.get("anonymous")).isEqualTo(false);
    }
  }

  private HttpResponse<String> httpGet(final String url) throws Exception {
    final var request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> httpPostForm(final String url, final String formBody)
      throws Exception {
    final var request =
        HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(formBody))
            .build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }

  private String resolveUrl(final String url) {
    if (url.startsWith("http")) {
      return url;
    }
    return baseUrl() + url;
  }

  private static String extractFormAction(final String html) {
    final var matcher = FORM_ACTION_PATTERN.matcher(html);
    if (!matcher.find()) {
      throw new IllegalStateException(
          "Could not find form action URL in Keycloak login page HTML");
    }
    return matcher.group(1).replace("&amp;", "&");
  }
}
```

- [ ] **Step 2: Run the test**

Run: `cd /Users/ben.sheppard/code/camunda/gatekeeper && ./mvnw test -pl gatekeeper-spring-boot-starter -Dtest="OidcWebappLoginIntegrationTest" -q`
Expected: All tests pass. The full redirect dance completes and the session is established.

- [ ] **Step 3: Commit**

```bash
git add gatekeeper-spring-boot-starter/src/test/java/io/camunda/gatekeeper/spring/integration/OidcWebappLoginIntegrationTest.java
git commit -m "test: add OIDC webapp login integration tests with Keycloak Testcontainer"
```

---

### Task 7: Run full test suite and format

- [ ] **Step 1: Run spotless**

Run: `cd /Users/ben.sheppard/code/camunda/gatekeeper && ./mvnw spotless:apply -pl gatekeeper-spring-boot-starter`

- [ ] **Step 2: Run all tests**

Run: `cd /Users/ben.sheppard/code/camunda/gatekeeper && ./mvnw test -pl gatekeeper-spring-boot-starter -q`
Expected: All tests pass — existing BasicAuth tests, new OIDC API tests, new OIDC webapp tests, and all unit tests.

- [ ] **Step 3: Commit if spotless made changes**

```bash
git add -u
git commit -m "style: apply spotless formatting"
```

