# OIDC UserInfo Claim Augmentation for Bearer Tokens Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add opt-in UserInfo claim augmentation for OIDC bearer-token authentication so authorizations built from claims that only appear in the `/userinfo` response (not the JWT access token) work for both REST (`/v1`/`/v2`) and gRPC (Zeebe gateway) requests. Configurable per-provider TTL cache keeps IdP round-trips bounded.

**Architecture:** Introduce a shared `OidcClaimsProvider` bean in `security-core`. It takes a validated JWT's claims + raw token string, and returns the final claims map used by `TokenClaimsConverter` (REST) and `OidcPrincipalLoader`/`OidcGroupsLoader` (gRPC). When augmentation is enabled for the provider, the implementation calls the IdP's `/userinfo` endpoint with the bearer token, merges the response over the JWT claims, and caches the result by `jti` (fallback SHA-256 of token) with TTL = `min(configured, exp - now - skew)`. Caffeine provides the cache; Micrometer provides metrics; fail-closed on userinfo errors with a short negative cache to prevent storm-retries.

**Tech Stack:** Java 21, Spring Security 6, Caffeine (already in parent POM), Jackson (for JSON parsing), JDK `java.net.http.HttpClient` (no new module deps), Micrometer, JUnit 5 + AssertJ + WireMock for tests.

---

## File Structure

### New files

- `security/security-core/src/main/java/io/camunda/security/configuration/OidcUserInfoAugmentationConfiguration.java` — nested config class for the three knobs.
- `security/security-core/src/main/java/io/camunda/security/oidc/OidcClaimsProvider.java` — interface `Map<String, Object> claimsFor(Map<String, Object> jwtClaims, String tokenValue)`.
- `security/security-core/src/main/java/io/camunda/security/oidc/OidcUserInfoClient.java` — thin JDK `HttpClient` wrapper for the userinfo endpoint.
- `security/security-core/src/main/java/io/camunda/security/oidc/CachingOidcClaimsProvider.java` — production impl: config-aware, Caffeine-cached, metric-instrumented, fail-closed.
- `security/security-core/src/main/java/io/camunda/security/oidc/NoopOidcClaimsProvider.java` — pass-through; used when no OIDC provider has augmentation enabled (avoids cache/HTTP client overhead).
- `security/security-core/src/test/java/io/camunda/security/oidc/CachingOidcClaimsProviderTest.java` — unit tests (WireMock-backed).
- `security/security-core/src/test/java/io/camunda/security/oidc/OidcUserInfoClientTest.java` — unit tests for the HTTP client.
- `authentication/src/test/java/io/camunda/authentication/oidc/OidcBearerUserInfoAugmentationIT.java` — integration test: full REST path through a Spring Boot slice with WireMock IdP.
- `zeebe/gateway-grpc/src/test/java/io/camunda/zeebe/gateway/interceptors/impl/AuthenticationHandlerOidcUserInfoTest.java` — gRPC-side integration test for the handler with a mocked provider.

### Modified files

- `security/security-core/pom.xml` — add `jackson-databind` and `caffeine` deps (both already managed in parent).
- `security/security-core/src/main/java/io/camunda/security/configuration/OidcAuthenticationConfiguration.java:56–58` — add `userInfoAugmentation` field, getter/setter, builder wiring.
- `authentication/src/main/java/io/camunda/authentication/converter/OidcTokenAuthenticationConverter.java:33–42` — call `OidcClaimsProvider` instead of passing `token.getTokenAttributes()` straight to `TokenClaimsConverter`.
- `authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java` — add `@Bean OidcClaimsProvider` inside `OidcConfiguration` (around line 632); inject into `oidcTokenAuthenticationConverter` bean (line 633).
- `zeebe/gateway-grpc/src/main/java/io/camunda/zeebe/gateway/interceptors/impl/AuthenticationHandler.java:58–69, 79–104` — accept `OidcClaimsProvider` in the `Oidc` constructor; call it after `jwtDecoder.decode(...)` and use the returned map in place of `token.getClaims()` at lines 97 and 108.
- `zeebe/gateway-grpc/src/main/java/io/camunda/zeebe/gateway/Gateway.java:100, 112, 122, 135, 145, 418–419` — thread `OidcClaimsProvider` through Gateway constructors to the `AuthenticationHandler.Oidc` instantiation.
- `dist/src/main/java/io/camunda/zeebe/gateway/GatewayModuleConfiguration.java:112–120` — resolve the `OidcClaimsProvider` bean and pass it to `new Gateway(...)`.
- `zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/EmbeddedGatewayService.java:47–48` — same constructor update.
- `zeebe/qa/integration-tests/src/test/java/io/camunda/zeebe/it/cluster/clustering/ClusteringRule.java:523–528` and `zeebe/gateway-grpc/src/test/java/io/camunda/zeebe/gateway/api/util/StubbedGateway.java:107–111` — pass `NoopOidcClaimsProvider` in tests that don't care.

---

## Task 1: Config surface — `OidcUserInfoAugmentationConfiguration`

**Files:**
- Create: `security/security-core/src/main/java/io/camunda/security/configuration/OidcUserInfoAugmentationConfiguration.java`
- Modify: `security/security-core/src/main/java/io/camunda/security/configuration/OidcAuthenticationConfiguration.java`
- Modify: `security/security-core/pom.xml`
- Test: `security/security-core/src/test/java/io/camunda/security/configuration/OidcUserInfoAugmentationConfigurationTest.java`

- [ ] **Step 1: Write failing test for the nested config defaults**

Create `security/security-core/src/test/java/io/camunda/security/configuration/OidcUserInfoAugmentationConfigurationTest.java`:

```java
package io.camunda.security.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class OidcUserInfoAugmentationConfigurationTest {

  @Test
  void defaultsAreDisabledWithFiveMinuteTtlAndTenThousandEntries() {
    final var config = new OidcUserInfoAugmentationConfiguration();

    assertThat(config.isEnabled()).isFalse();
    assertThat(config.getCacheTtl()).isEqualTo(Duration.ofMinutes(5));
    assertThat(config.getCacheMaxSize()).isEqualTo(10_000);
  }

  @Test
  void oidcAuthenticationConfigurationExposesNonNullAugmentationByDefault() {
    final var oidc = new OidcAuthenticationConfiguration();

    assertThat(oidc.getUserInfoAugmentation()).isNotNull();
    assertThat(oidc.getUserInfoAugmentation().isEnabled()).isFalse();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl security/security-core -am test -Dtest=OidcUserInfoAugmentationConfigurationTest`

Expected: compile failure — `OidcUserInfoAugmentationConfiguration` does not exist.

- [ ] **Step 3: Create `OidcUserInfoAugmentationConfiguration`**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.configuration;

import java.time.Duration;

public class OidcUserInfoAugmentationConfiguration {

  public static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);
  public static final int DEFAULT_CACHE_MAX_SIZE = 10_000;
  public static final Duration NEGATIVE_CACHE_TTL = Duration.ofSeconds(5);

  private boolean enabled = false;
  private Duration cacheTtl = DEFAULT_CACHE_TTL;
  private int cacheMaxSize = DEFAULT_CACHE_MAX_SIZE;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public Duration getCacheTtl() {
    return cacheTtl;
  }

  public void setCacheTtl(final Duration cacheTtl) {
    this.cacheTtl = cacheTtl;
  }

  public int getCacheMaxSize() {
    return cacheMaxSize;
  }

  public void setCacheMaxSize(final int cacheMaxSize) {
    this.cacheMaxSize = cacheMaxSize;
  }
}
```

- [ ] **Step 4: Wire it into `OidcAuthenticationConfiguration`**

In `OidcAuthenticationConfiguration.java`, after the existing `userInfoEnabled` field (line 58):

```java
private OidcUserInfoAugmentationConfiguration userInfoAugmentation =
    new OidcUserInfoAugmentationConfiguration();
```

Add getter/setter near the other accessors (mirror the pattern around line 263):

```java
public OidcUserInfoAugmentationConfiguration getUserInfoAugmentation() {
  return userInfoAugmentation;
}

public void setUserInfoAugmentation(
    final OidcUserInfoAugmentationConfiguration userInfoAugmentation) {
  this.userInfoAugmentation = userInfoAugmentation;
}
```

Then update the `Builder` (around line 334) to carry the field:

```java
private OidcUserInfoAugmentationConfiguration userInfoAugmentation =
    new OidcUserInfoAugmentationConfiguration();

public Builder userInfoAugmentation(
    final OidcUserInfoAugmentationConfiguration userInfoAugmentation) {
  this.userInfoAugmentation = userInfoAugmentation;
  return this;
}
```

And in the builder's `build()` method (around line 478), add:

```java
config.setUserInfoAugmentation(userInfoAugmentation);
```

- [ ] **Step 5: Run tests — expect pass**

Run: `mvn -pl security/security-core test -Dtest=OidcUserInfoAugmentationConfigurationTest`

Expected: both tests PASS.

- [ ] **Step 6: Commit**

```bash
git add security/security-core/src/main/java/io/camunda/security/configuration/OidcUserInfoAugmentationConfiguration.java \
        security/security-core/src/main/java/io/camunda/security/configuration/OidcAuthenticationConfiguration.java \
        security/security-core/src/test/java/io/camunda/security/configuration/OidcUserInfoAugmentationConfigurationTest.java
git commit -m "feat(security): add OIDC user-info augmentation config"
```

---

## Task 2: `OidcUserInfoClient` — JDK HttpClient wrapper

**Files:**
- Modify: `security/security-core/pom.xml`
- Create: `security/security-core/src/main/java/io/camunda/security/oidc/OidcUserInfoClient.java`
- Create: `security/security-core/src/main/java/io/camunda/security/oidc/OidcUserInfoException.java`
- Test: `security/security-core/src/test/java/io/camunda/security/oidc/OidcUserInfoClientTest.java`

- [ ] **Step 1: Add `jackson-databind` and `wiremock` to `security-core/pom.xml`**

After the existing `jackson-annotations` dependency (around line 45):

```xml
<dependency>
  <groupId>com.fasterxml.jackson.core</groupId>
  <artifactId>jackson-databind</artifactId>
</dependency>
```

After the existing test-scope dependencies (around line 74):

```xml
<dependency>
  <groupId>org.wiremock</groupId>
  <artifactId>wiremock-standalone</artifactId>
  <scope>test</scope>
</dependency>
```

Both are managed in the parent POM — no version needed.

- [ ] **Step 2: Write failing test**

Create `OidcUserInfoClientTest.java`:

```java
package io.camunda.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

class OidcUserInfoClientTest {

  @RegisterExtension
  static WireMockExtension idp =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  private final OidcUserInfoClient client =
      new OidcUserInfoClient(HttpClient.newHttpClient(), Duration.ofSeconds(2));

  @Test
  void returnsClaimsFromSuccessfulResponse() {
    idp.stubFor(
        get("/userinfo")
            .withHeader("Authorization", equalTo("Bearer token-abc"))
            .willReturn(okJson("{\"sub\":\"alice\",\"groups\":[\"engineering\",\"ops\"]}")));

    final var claims =
        client.fetch(URI.create(idp.baseUrl() + "/userinfo"), "token-abc");

    assertThat(claims).containsEntry("sub", "alice");
    assertThat(claims).containsEntry("groups", java.util.List.of("engineering", "ops"));
  }

  @Test
  void throwsOnNon2xx() {
    idp.stubFor(get("/userinfo").willReturn(aResponse().withStatus(401)));

    assertThatThrownBy(
            () -> client.fetch(URI.create(idp.baseUrl() + "/userinfo"), "bad-token"))
        .isInstanceOf(OidcUserInfoException.class)
        .hasMessageContaining("401");
  }

  @Test
  void throwsOnTimeout() {
    idp.stubFor(
        get("/userinfo")
            .willReturn(aResponse().withFixedDelay(3_000).withBody("{}")));

    assertThatThrownBy(
            () -> client.fetch(URI.create(idp.baseUrl() + "/userinfo"), "token-abc"))
        .isInstanceOf(OidcUserInfoException.class);
  }
}
```

- [ ] **Step 3: Run test to verify it fails**

Run: `mvn -pl security/security-core test -Dtest=OidcUserInfoClientTest`

Expected: compile failure — class does not exist.

- [ ] **Step 4: Implement `OidcUserInfoClient`**

Create `security/security-core/src/main/java/io/camunda/security/oidc/OidcUserInfoClient.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.oidc;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;

public class OidcUserInfoClient {

  private static final TypeReference<Map<String, Object>> CLAIMS_TYPE = new TypeReference<>() {};

  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;
  private final Duration requestTimeout;

  public OidcUserInfoClient(final HttpClient httpClient, final Duration requestTimeout) {
    this.httpClient = Objects.requireNonNull(httpClient);
    this.requestTimeout = Objects.requireNonNull(requestTimeout);
    objectMapper = new ObjectMapper();
  }

  public Map<String, Object> fetch(final URI userInfoUri, final String bearerToken) {
    final HttpRequest request =
        HttpRequest.newBuilder(userInfoUri)
            .timeout(requestTimeout)
            .header("Authorization", "Bearer " + bearerToken)
            .header("Accept", "application/json")
            .GET()
            .build();

    final HttpResponse<byte[]> response;
    try {
      response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
    } catch (final Exception e) {
      throw new OidcUserInfoException("UserInfo request failed: " + e.getMessage(), e);
    }

    if (response.statusCode() / 100 != 2) {
      throw new OidcUserInfoException(
          "UserInfo request returned HTTP " + response.statusCode());
    }

    try {
      return objectMapper.readValue(response.body(), CLAIMS_TYPE);
    } catch (final Exception e) {
      throw new OidcUserInfoException("Failed to parse UserInfo JSON: " + e.getMessage(), e);
    }
  }
}
```

Create `security/security-core/src/main/java/io/camunda/security/oidc/OidcUserInfoException.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.oidc;

public class OidcUserInfoException extends RuntimeException {

  public OidcUserInfoException(final String message) {
    super(message);
  }

  public OidcUserInfoException(final String message, final Throwable cause) {
    super(message, cause);
  }
}
```

- [ ] **Step 5: Run tests — expect pass**

Run: `mvn -pl security/security-core test -Dtest=OidcUserInfoClientTest`

Expected: all three tests PASS.

- [ ] **Step 6: Commit**

```bash
git add security/security-core/src/main/java/io/camunda/security/oidc/OidcUserInfoClient.java \
        security/security-core/src/main/java/io/camunda/security/oidc/OidcUserInfoException.java \
        security/security-core/src/test/java/io/camunda/security/oidc/OidcUserInfoClientTest.java \
        security/security-core/pom.xml
git commit -m "feat(security): add OidcUserInfoClient for bearer-auth userinfo calls"
```

---

## Task 3: `OidcClaimsProvider` interface and `NoopOidcClaimsProvider`

**Files:**
- Create: `security/security-core/src/main/java/io/camunda/security/oidc/OidcClaimsProvider.java`
- Create: `security/security-core/src/main/java/io/camunda/security/oidc/NoopOidcClaimsProvider.java`
- Test: `security/security-core/src/test/java/io/camunda/security/oidc/NoopOidcClaimsProviderTest.java`

- [ ] **Step 1: Write failing test**

```java
package io.camunda.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class NoopOidcClaimsProviderTest {

  @Test
  void returnsJwtClaimsUnchanged() {
    final var provider = new NoopOidcClaimsProvider();
    final var input = Map.<String, Object>of("sub", "alice", "iss", "https://idp.example");

    final var output = provider.claimsFor(input, "token-abc");

    assertThat(output).isSameAs(input);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl security/security-core test -Dtest=NoopOidcClaimsProviderTest`

Expected: compile failure — types don't exist.

- [ ] **Step 3: Create interface and Noop impl**

`OidcClaimsProvider.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.oidc;

import java.util.Map;

/**
 * Resolves the final claims map used for authorization from a validated JWT's claims and the raw
 * token value. Implementations may augment the JWT claims with the OIDC UserInfo response,
 * subject to provider-specific configuration and caching.
 */
public interface OidcClaimsProvider {

  /**
   * @param jwtClaims the claims as extracted from the validated JWT access token
   * @param tokenValue the raw bearer token string (needed if UserInfo must be called)
   * @return the claims map to be used for principal and authorization resolution
   * @throws OidcUserInfoException if UserInfo augmentation is required but the IdP call fails
   */
  Map<String, Object> claimsFor(Map<String, Object> jwtClaims, String tokenValue);
}
```

`NoopOidcClaimsProvider.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.oidc;

import java.util.Map;

public class NoopOidcClaimsProvider implements OidcClaimsProvider {

  @Override
  public Map<String, Object> claimsFor(
      final Map<String, Object> jwtClaims, final String tokenValue) {
    return jwtClaims;
  }
}
```

- [ ] **Step 4: Run test — expect pass**

Run: `mvn -pl security/security-core test -Dtest=NoopOidcClaimsProviderTest`

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add security/security-core/src/main/java/io/camunda/security/oidc/OidcClaimsProvider.java \
        security/security-core/src/main/java/io/camunda/security/oidc/NoopOidcClaimsProvider.java \
        security/security-core/src/test/java/io/camunda/security/oidc/NoopOidcClaimsProviderTest.java
git commit -m "feat(security): add OidcClaimsProvider interface with no-op default"
```

---

## Task 4: `CachingOidcClaimsProvider` — caching impl with metrics and fail-closed behaviour

**Files:**
- Modify: `security/security-core/pom.xml`
- Create: `security/security-core/src/main/java/io/camunda/security/oidc/CachingOidcClaimsProvider.java`
- Test: `security/security-core/src/test/java/io/camunda/security/oidc/CachingOidcClaimsProviderTest.java`

- [ ] **Step 1: Add `caffeine`, `micrometer-core`, and `mockito-core` to `security-core/pom.xml`**

In the main `<dependencies>` section:

```xml
<dependency>
  <groupId>com.github.ben-manes.caffeine</groupId>
  <artifactId>caffeine</artifactId>
</dependency>
<dependency>
  <groupId>io.micrometer</groupId>
  <artifactId>micrometer-core</artifactId>
</dependency>
```

In the test-scope section:

```xml
<dependency>
  <groupId>org.mockito</groupId>
  <artifactId>mockito-core</artifactId>
  <scope>test</scope>
</dependency>
```

All managed in the parent POM.

- [ ] **Step 2: Write failing tests**

Create `CachingOidcClaimsProviderTest.java`:

```java
package io.camunda.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.OidcUserInfoAugmentationConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CachingOidcClaimsProviderTest {

  private OidcAuthenticationConfiguration oidcConfig;
  private OidcUserInfoClient userInfoClient;
  private SimpleMeterRegistry meterRegistry;

  @BeforeEach
  void setUp() {
    oidcConfig = new OidcAuthenticationConfiguration();
    oidcConfig.setIssuerUri("https://idp.example");
    final var aug = new OidcUserInfoAugmentationConfiguration();
    aug.setEnabled(true);
    aug.setCacheTtl(Duration.ofMinutes(5));
    aug.setCacheMaxSize(100);
    oidcConfig.setUserInfoAugmentation(aug);
    oidcConfig.setIssuerUri("https://idp.example");
    userInfoClient = mock(OidcUserInfoClient.class);
    meterRegistry = new SimpleMeterRegistry();
  }

  @Test
  void returnsJwtClaimsUnchangedWhenAugmentationDisabled() {
    oidcConfig.getUserInfoAugmentation().setEnabled(false);
    final var provider =
        new CachingOidcClaimsProvider(
            oidcConfig, URI.create("https://idp.example/userinfo"), userInfoClient, meterRegistry);

    final Map<String, Object> jwtClaims = Map.of("sub", "alice");
    final var result = provider.claimsFor(jwtClaims, "token-abc");

    assertThat(result).isSameAs(jwtClaims);
    verify(userInfoClient, times(0)).fetch(any(), any());
  }

  @Test
  void callsUserInfoOnceAndMergesClaimsOverJwt() {
    when(userInfoClient.fetch(any(), eq("token-abc")))
        .thenReturn(Map.of("groups", java.util.List.of("eng"), "sub", "alice-from-userinfo"));

    final var provider =
        new CachingOidcClaimsProvider(
            oidcConfig, URI.create("https://idp.example/userinfo"), userInfoClient, meterRegistry);

    final Map<String, Object> jwtClaims =
        Map.of("sub", "alice", "jti", "jti-1", "exp", Instant.now().getEpochSecond() + 3600);

    final var result = provider.claimsFor(jwtClaims, "token-abc");

    assertThat(result).containsEntry("groups", java.util.List.of("eng"));
    assertThat(result).containsEntry("sub", "alice-from-userinfo"); // userinfo overrides
    assertThat(result).containsEntry("jti", "jti-1"); // jwt-only claim preserved
    verify(userInfoClient, times(1)).fetch(any(), any());
  }

  @Test
  void cachesByJtiSoSecondCallDoesNotHitIdp() {
    when(userInfoClient.fetch(any(), any()))
        .thenReturn(Map.of("groups", java.util.List.of("eng")));

    final var provider =
        new CachingOidcClaimsProvider(
            oidcConfig, URI.create("https://idp.example/userinfo"), userInfoClient, meterRegistry);

    final Map<String, Object> jwtClaims =
        Map.of("sub", "alice", "jti", "jti-1", "exp", Instant.now().getEpochSecond() + 3600);

    provider.claimsFor(jwtClaims, "token-abc");
    provider.claimsFor(jwtClaims, "token-abc");

    verify(userInfoClient, times(1)).fetch(any(), any());
  }

  @Test
  void distinctJtisResultInDistinctCacheEntries() {
    when(userInfoClient.fetch(any(), any()))
        .thenReturn(Map.of("groups", java.util.List.of("eng")));

    final var provider =
        new CachingOidcClaimsProvider(
            oidcConfig, URI.create("https://idp.example/userinfo"), userInfoClient, meterRegistry);

    final long exp = Instant.now().getEpochSecond() + 3600;
    provider.claimsFor(Map.of("sub", "alice", "jti", "jti-1", "exp", exp), "token-a");
    provider.claimsFor(Map.of("sub", "alice", "jti", "jti-2", "exp", exp), "token-b");

    verify(userInfoClient, times(2)).fetch(any(), any());
  }

  @Test
  void fallsBackToTokenHashWhenJtiAbsent() {
    when(userInfoClient.fetch(any(), any()))
        .thenReturn(Map.of("groups", java.util.List.of("eng")));

    final var provider =
        new CachingOidcClaimsProvider(
            oidcConfig, URI.create("https://idp.example/userinfo"), userInfoClient, meterRegistry);

    final long exp = Instant.now().getEpochSecond() + 3600;
    final Map<String, Object> claimsNoJti = Map.of("sub", "alice", "exp", exp);

    provider.claimsFor(claimsNoJti, "token-abc");
    provider.claimsFor(claimsNoJti, "token-abc"); // same token -> same cache entry
    provider.claimsFor(claimsNoJti, "token-xyz"); // different token -> new entry

    verify(userInfoClient, times(2)).fetch(any(), any());
  }

  @Test
  void failsClosedWhenUserInfoThrows() {
    when(userInfoClient.fetch(any(), any()))
        .thenThrow(new OidcUserInfoException("boom"));

    final var provider =
        new CachingOidcClaimsProvider(
            oidcConfig, URI.create("https://idp.example/userinfo"), userInfoClient, meterRegistry);

    final Map<String, Object> jwtClaims =
        Map.of("sub", "alice", "jti", "jti-1", "exp", Instant.now().getEpochSecond() + 3600);

    assertThatThrownBy(() -> provider.claimsFor(jwtClaims, "token-abc"))
        .isInstanceOf(OidcUserInfoException.class);
  }

  @Test
  void negativeCachePreventsHammeringDuringOutage() {
    when(userInfoClient.fetch(any(), any()))
        .thenThrow(new OidcUserInfoException("IdP down"));

    final var provider =
        new CachingOidcClaimsProvider(
            oidcConfig, URI.create("https://idp.example/userinfo"), userInfoClient, meterRegistry);

    final Map<String, Object> jwtClaims =
        Map.of("sub", "alice", "jti", "jti-1", "exp", Instant.now().getEpochSecond() + 3600);

    for (int i = 0; i < 20; i++) {
      try {
        provider.claimsFor(jwtClaims, "token-abc");
      } catch (final OidcUserInfoException ignored) {
      }
    }

    verify(userInfoClient, times(1)).fetch(any(), any());
  }

  @Test
  void ttlIsCappedByTokenExpiry() {
    when(userInfoClient.fetch(any(), any()))
        .thenReturn(Map.of("groups", java.util.List.of("eng")));

    oidcConfig.getUserInfoAugmentation().setCacheTtl(Duration.ofHours(1));
    final var provider =
        new CachingOidcClaimsProvider(
            oidcConfig, URI.create("https://idp.example/userinfo"), userInfoClient, meterRegistry);

    // exp is 1 second in the past (after skew) -> entry should not be cached
    final long expPast = Instant.now().minusSeconds(120).getEpochSecond();
    final Map<String, Object> expired =
        Map.of("sub", "alice", "jti", "jti-1", "exp", expPast);

    provider.claimsFor(expired, "token-abc");
    provider.claimsFor(expired, "token-abc");

    // Should hit IdP both times because the entry expired immediately
    verify(userInfoClient, times(2)).fetch(any(), any());
  }

  @Test
  void exposesHitAndMissCounters() {
    when(userInfoClient.fetch(any(), any()))
        .thenReturn(Map.of("groups", java.util.List.of("eng")));

    final var provider =
        new CachingOidcClaimsProvider(
            oidcConfig, URI.create("https://idp.example/userinfo"), userInfoClient, meterRegistry);

    final Map<String, Object> jwtClaims =
        Map.of("sub", "alice", "jti", "jti-1", "exp", Instant.now().getEpochSecond() + 3600);

    provider.claimsFor(jwtClaims, "token-abc"); // miss
    provider.claimsFor(jwtClaims, "token-abc"); // hit

    assertThat(meterRegistry.get("camunda.oidc.userinfo.cache").tag("result", "miss").counter().count())
        .isEqualTo(1.0);
    assertThat(meterRegistry.get("camunda.oidc.userinfo.cache").tag("result", "hit").counter().count())
        .isEqualTo(1.0);
  }
}
```

- [ ] **Step 3: Run tests to verify they fail**

Run: `mvn -pl security/security-core test -Dtest=CachingOidcClaimsProviderTest`

Expected: compile failure — `CachingOidcClaimsProvider` does not exist.

- [ ] **Step 4: Implement `CachingOidcClaimsProvider`**

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.oidc;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.configuration.OidcUserInfoAugmentationConfiguration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachingOidcClaimsProvider implements OidcClaimsProvider {

  private static final Logger LOG = LoggerFactory.getLogger(CachingOidcClaimsProvider.class);
  private static final String CACHE_METRIC = "camunda.oidc.userinfo.cache";
  private static final String FETCH_METRIC = "camunda.oidc.userinfo.fetch";

  private final OidcAuthenticationConfiguration oidcConfig;
  private final URI userInfoUri;
  private final OidcUserInfoClient userInfoClient;
  private final Cache<String, CacheEntry> cache;
  private final Counter hitCounter;
  private final Counter missCounter;
  private final Timer fetchTimer;
  private final Counter fetchFailureCounter;
  private final Duration clockSkew;

  public CachingOidcClaimsProvider(
      final OidcAuthenticationConfiguration oidcConfig,
      final URI userInfoUri,
      final OidcUserInfoClient userInfoClient,
      final MeterRegistry meterRegistry) {
    this.oidcConfig = Objects.requireNonNull(oidcConfig);
    this.userInfoUri = userInfoUri; // may be null if augmentation disabled
    this.userInfoClient = Objects.requireNonNull(userInfoClient);
    clockSkew = oidcConfig.getClockSkew();
    final OidcUserInfoAugmentationConfiguration aug = oidcConfig.getUserInfoAugmentation();
    cache =
        Caffeine.newBuilder()
            .maximumSize(aug.getCacheMaxSize())
            .expireAfter(new EntryExpiry(aug.getCacheTtl()))
            .build();
    hitCounter =
        Counter.builder(CACHE_METRIC).tag("result", "hit").register(meterRegistry);
    missCounter =
        Counter.builder(CACHE_METRIC).tag("result", "miss").register(meterRegistry);
    fetchTimer = Timer.builder(FETCH_METRIC).register(meterRegistry);
    fetchFailureCounter =
        Counter.builder(FETCH_METRIC).tag("outcome", "failure").register(meterRegistry);
  }

  @Override
  public Map<String, Object> claimsFor(
      final Map<String, Object> jwtClaims, final String tokenValue) {
    if (!oidcConfig.getUserInfoAugmentation().isEnabled()) {
      return jwtClaims;
    }
    if (userInfoUri == null) {
      throw new OidcUserInfoException(
          "UserInfo augmentation is enabled but no userinfo URI is available for this provider");
    }

    final String key = cacheKey(jwtClaims, tokenValue);
    final CacheEntry cached = cache.getIfPresent(key);
    if (cached != null) {
      hitCounter.increment();
      if (cached.failure() != null) {
        throw cached.failure();
      }
      return cached.claims();
    }
    missCounter.increment();

    final Map<String, Object> merged;
    try {
      final Map<String, Object> userInfoClaims =
          fetchTimer.recordCallable(() -> userInfoClient.fetch(userInfoUri, tokenValue));
      merged = merge(jwtClaims, userInfoClaims);
    } catch (final OidcUserInfoException e) {
      fetchFailureCounter.increment();
      cache.put(key, CacheEntry.failure(e));
      throw e;
    } catch (final Exception e) {
      fetchFailureCounter.increment();
      final var wrapped = new OidcUserInfoException("UserInfo fetch failed: " + e.getMessage(), e);
      cache.put(key, CacheEntry.failure(wrapped));
      throw wrapped;
    }

    cache.put(key, CacheEntry.success(merged, tokenExpiry(jwtClaims)));
    return merged;
  }

  private static Map<String, Object> merge(
      final Map<String, Object> jwtClaims, final Map<String, Object> userInfoClaims) {
    final Map<String, Object> merged = new HashMap<>(jwtClaims);
    merged.putAll(userInfoClaims); // userinfo wins on conflict
    return Map.copyOf(merged);
  }

  private static String cacheKey(final Map<String, Object> jwtClaims, final String tokenValue) {
    final Object jti = jwtClaims.get("jti");
    if (jti instanceof final String s && !s.isBlank()) {
      return "jti:" + s;
    }
    return "tok:" + sha256(tokenValue);
  }

  private static String sha256(final String input) {
    try {
      final MessageDigest md = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    } catch (final Exception e) {
      throw new IllegalStateException("SHA-256 unavailable", e);
    }
  }

  private static Instant tokenExpiry(final Map<String, Object> jwtClaims) {
    final Object exp = jwtClaims.get("exp");
    if (exp instanceof Number n) {
      return Instant.ofEpochSecond(n.longValue());
    }
    return Instant.now().plus(Duration.ofMinutes(5));
  }

  private record CacheEntry(
      Map<String, Object> claims, OidcUserInfoException failure, Instant tokenExp) {
    static CacheEntry success(final Map<String, Object> claims, final Instant tokenExp) {
      return new CacheEntry(claims, null, tokenExp);
    }

    static CacheEntry failure(final OidcUserInfoException e) {
      return new CacheEntry(null, e, null);
    }
  }

  /**
   * Caffeine Expiry that:
   *  - for successful entries, expires at min(configured TTL, tokenExp - skew - now)
   *  - for failure entries, uses a short hard-coded negative TTL
   */
  private final class EntryExpiry implements Expiry<String, CacheEntry> {
    private final Duration configuredTtl;

    EntryExpiry(final Duration configuredTtl) {
      this.configuredTtl = configuredTtl;
    }

    @Override
    public long expireAfterCreate(
        final String key, final CacheEntry value, final long currentTime) {
      return ttlNanos(value);
    }

    @Override
    public long expireAfterUpdate(
        final String key, final CacheEntry value, final long currentTime, final long currentDuration) {
      return ttlNanos(value);
    }

    @Override
    public long expireAfterRead(
        final String key, final CacheEntry value, final long currentTime, final long currentDuration) {
      return currentDuration;
    }

    private long ttlNanos(final CacheEntry value) {
      if (value.failure() != null) {
        return TimeUnit.NANOSECONDS.convert(
            OidcUserInfoAugmentationConfiguration.NEGATIVE_CACHE_TTL);
      }
      final Duration untilExp =
          Duration.between(Instant.now(), value.tokenExp()).minus(clockSkew);
      final Duration effective =
          untilExp.isNegative() || untilExp.isZero() || untilExp.compareTo(configuredTtl) < 0
              ? untilExp
              : configuredTtl;
      if (effective.isNegative() || effective.isZero()) {
        return 0L;
      }
      return effective.toNanos();
    }
  }
}
```

- [ ] **Step 5: Run tests — expect pass**

Run: `mvn -pl security/security-core test -Dtest=CachingOidcClaimsProviderTest`

Expected: all tests PASS.

- [ ] **Step 6: Commit**

```bash
git add security/security-core/src/main/java/io/camunda/security/oidc/CachingOidcClaimsProvider.java \
        security/security-core/src/test/java/io/camunda/security/oidc/CachingOidcClaimsProviderTest.java \
        security/security-core/pom.xml
git commit -m "feat(security): add CachingOidcClaimsProvider with Caffeine + metrics"
```

---

## Task 5: Wire the provider into the REST `OidcTokenAuthenticationConverter`

**Files:**
- Modify: `authentication/src/main/java/io/camunda/authentication/converter/OidcTokenAuthenticationConverter.java`
- Modify: `authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java`
- Test: `authentication/src/test/java/io/camunda/authentication/converter/OidcTokenAuthenticationConverterTest.java`

- [ ] **Step 1: Write failing test**

Create `OidcTokenAuthenticationConverterTest.java` (or extend if it exists — check first with `ls authentication/src/test/java/io/camunda/authentication/converter/`):

```java
package io.camunda.authentication.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.oidc.OidcClaimsProvider;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

class OidcTokenAuthenticationConverterTest {

  @Test
  void convertUsesClaimsReturnedByProvider() {
    final TokenClaimsConverter tokenClaimsConverter = mock(TokenClaimsConverter.class);
    final OidcClaimsProvider claimsProvider = mock(OidcClaimsProvider.class);
    final var jwt =
        Jwt.withTokenValue("token-abc")
            .header("alg", "RS256")
            .claim("sub", "alice")
            .claim("iss", "https://idp.example")
            .build();
    final var authentication = new JwtAuthenticationToken(jwt);

    final Map<String, Object> augmentedClaims =
        Map.of("sub", "alice", "groups", List.of("eng"));
    when(claimsProvider.claimsFor(any(), eq("token-abc"))).thenReturn(augmentedClaims);
    final var expected = mock(CamundaAuthentication.class);
    when(tokenClaimsConverter.convert(augmentedClaims)).thenReturn(expected);

    final var converter =
        new OidcTokenAuthenticationConverter(tokenClaimsConverter, claimsProvider);

    assertThat(converter.convert(authentication)).isSameAs(expected);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl authentication test -Dtest=OidcTokenAuthenticationConverterTest`

Expected: compile failure — two-arg constructor does not exist.

- [ ] **Step 3: Modify `OidcTokenAuthenticationConverter` to accept and use the provider**

Replace the file contents with:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.converter;

import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.oidc.OidcClaimsProvider;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

public class OidcTokenAuthenticationConverter
    implements CamundaAuthenticationConverter<Authentication> {

  private final TokenClaimsConverter tokenClaimsConverter;
  private final OidcClaimsProvider claimsProvider;

  public OidcTokenAuthenticationConverter(
      final TokenClaimsConverter tokenClaimsConverter, final OidcClaimsProvider claimsProvider) {
    this.tokenClaimsConverter = tokenClaimsConverter;
    this.claimsProvider = claimsProvider;
  }

  @Override
  public boolean supports(final Authentication authentication) {
    return authentication instanceof JwtAuthenticationToken;
  }

  @Override
  public CamundaAuthentication convert(final Authentication authentication) {
    return Optional.of(authentication)
        .map(JwtAuthenticationToken.class::cast)
        .map(
            token -> {
              final Jwt jwt = token.getToken();
              return claimsProvider.claimsFor(jwt.getClaims(), jwt.getTokenValue());
            })
        .map(tokenClaimsConverter::convert)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "Failed to convert 'JwtAuthenticationToken' to 'CamundaAuthentication'"));
  }
}
```

Note: `supports()` was loosened from `AbstractOAuth2TokenAuthenticationToken` to `JwtAuthenticationToken` because we now need `Jwt.getTokenValue()` — bearer-token flow always produces `JwtAuthenticationToken` per Spring's `BearerTokenAuthenticationFilter`. Any existing callers with introspection-based `BearerTokenAuthenticationToken` would be out of scope (none in this codebase — confirmed by the earlier grep for `opaqueToken`/`introspection`).

- [ ] **Step 4: Add `@Bean OidcClaimsProvider` and update the converter bean in `WebSecurityConfig.OidcConfiguration`**

Find the bean definition at `WebSecurityConfig.java:632`:

```java
@Bean
public CamundaAuthenticationConverter<Authentication> oidcTokenAuthenticationConverter(
    final TokenClaimsConverter tokenClaimsConverter) {
  return new OidcTokenAuthenticationConverter(tokenClaimsConverter);
}
```

Replace with:

```java
@Bean
public CamundaAuthenticationConverter<Authentication> oidcTokenAuthenticationConverter(
    final TokenClaimsConverter tokenClaimsConverter, final OidcClaimsProvider oidcClaimsProvider) {
  return new OidcTokenAuthenticationConverter(tokenClaimsConverter, oidcClaimsProvider);
}
```

Add a new bean near the other OIDC beans (after `oidcProviderRepository` at WebSecurityConfig.java:654):

```java
@Bean
public OidcClaimsProvider oidcClaimsProvider(
    final SecurityConfiguration securityConfiguration,
    final OidcAuthenticationConfigurationRepository oidcProviderRepository,
    final MeterRegistry meterRegistry) {
  final var oidc = securityConfiguration.getAuthentication().getOidc();
  if (oidc == null || !oidc.getUserInfoAugmentation().isEnabled()) {
    return new NoopOidcClaimsProvider();
  }
  // Resolve userinfo URI from the first matching provider's discovery doc.
  // In multi-provider setups, a future iteration can key by 'iss' claim.
  final URI userInfoUri =
      oidcProviderRepository.getOidcAuthenticationConfigurations().values().stream()
          .map(OidcAuthenticationConfiguration::getIssuerUri)
          .filter(Objects::nonNull)
          .findFirst()
          .map(issuer -> URI.create(issuer.replaceAll("/$", "") + "/userinfo"))
          .orElseThrow(
              () ->
                  new IllegalStateException(
                      "UserInfo augmentation enabled but no issuerUri configured"));
  final var httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  return new CachingOidcClaimsProvider(
      oidc, userInfoUri, new OidcUserInfoClient(httpClient, Duration.ofSeconds(5)), meterRegistry);
}
```

Add imports at the top of `WebSecurityConfig.java`:

```java
import io.camunda.security.oidc.CachingOidcClaimsProvider;
import io.camunda.security.oidc.NoopOidcClaimsProvider;
import io.camunda.security.oidc.OidcClaimsProvider;
import io.camunda.security.oidc.OidcUserInfoClient;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
```

**Note for reviewers:** the userinfo URI discovery above is the simplest thing that works for the customer's single-provider setup. Multi-provider-per-chain support (selecting userinfo URI by `iss`) is out of scope for this plan — flag as follow-up if review surfaces it.

- [ ] **Step 5: Also update the `BasicConfiguration` Oidc converter? No — `BasicConfiguration` has no OIDC converter. Skip.**

- [ ] **Step 6: Run converter test — expect pass**

Run: `mvn -pl authentication test -Dtest=OidcTokenAuthenticationConverterTest`

Expected: PASS.

- [ ] **Step 7: Compile & run the authentication module's existing tests to catch regressions**

Run: `mvn -pl authentication test`

Expected: all PASS. If anything breaks, fix before moving on.

- [ ] **Step 8: Commit**

```bash
git add authentication/src/main/java/io/camunda/authentication/converter/OidcTokenAuthenticationConverter.java \
        authentication/src/main/java/io/camunda/authentication/config/WebSecurityConfig.java \
        authentication/src/test/java/io/camunda/authentication/converter/OidcTokenAuthenticationConverterTest.java
git commit -m "feat(authentication): route OIDC bearer claims through OidcClaimsProvider"
```

---

## Task 6: Wire the provider into the gRPC `AuthenticationHandler.Oidc`

**Files:**
- Modify: `zeebe/gateway-grpc/src/main/java/io/camunda/zeebe/gateway/interceptors/impl/AuthenticationHandler.java`
- Modify: `zeebe/gateway-grpc/src/main/java/io/camunda/zeebe/gateway/Gateway.java`
- Modify: `dist/src/main/java/io/camunda/zeebe/gateway/GatewayModuleConfiguration.java`
- Modify: `zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/EmbeddedGatewayService.java`
- Modify test utilities: `ClusteringRule.java`, `StubbedGateway.java`
- Test: `zeebe/gateway-grpc/src/test/java/io/camunda/zeebe/gateway/interceptors/impl/AuthenticationHandlerOidcUserInfoTest.java`

- [ ] **Step 1: Write failing test**

```java
package io.camunda.zeebe.gateway.interceptors.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.security.configuration.OidcAuthenticationConfiguration;
import io.camunda.security.oidc.OidcClaimsProvider;
import io.camunda.zeebe.gateway.interceptors.impl.AuthenticationHandler.Oidc;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

class AuthenticationHandlerOidcUserInfoTest {

  @Test
  void usesClaimsProviderResultForGroupsAndPrincipal() {
    final var jwtDecoder = mock(JwtDecoder.class);
    final var claimsProvider = mock(OidcClaimsProvider.class);
    final var oidc = new OidcAuthenticationConfiguration();
    oidc.setUsernameClaim("sub");
    oidc.setGroupsClaim("groups");

    final var jwt =
        Jwt.withTokenValue("token-abc")
            .header("alg", "RS256")
            .claim("sub", "alice")
            .build();
    when(jwtDecoder.decode("token-abc")).thenReturn(jwt);
    // provider adds groups from userinfo that the JWT does not have
    when(claimsProvider.claimsFor(any(), eq("token-abc")))
        .thenReturn(Map.of("sub", "alice", "groups", List.of("engineering")));

    final var handler = new Oidc(jwtDecoder, oidc, claimsProvider);
    final var result = handler.authenticate("Bearer token-abc");

    assertThat(result.isRight()).isTrue();
    final var ctx = result.get();
    assertThat(ctx.call(() -> AuthenticationHandler.GROUPS_CLAIMS.get()))
        .isEqualTo(List.of("engineering"));
    assertThat(ctx.call(() -> AuthenticationHandler.USERNAME.get())).isEqualTo("alice");
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl zeebe/gateway-grpc test -Dtest=AuthenticationHandlerOidcUserInfoTest`

Expected: compile failure — three-arg `Oidc` constructor does not exist.

- [ ] **Step 3: Modify `AuthenticationHandler.Oidc`**

In `AuthenticationHandler.java`, update the `Oidc` class:

1. Add import: `import io.camunda.security.oidc.OidcClaimsProvider;`
2. Add field and update constructor (replace lines 53–69):

```java
private final JwtDecoder jwtDecoder;
private final OidcAuthenticationConfiguration oidcAuthenticationConfiguration;
private final OidcClaimsProvider claimsProvider;
private final OidcPrincipalLoader oidcPrincipalLoader;
private final OidcGroupsLoader oidcGroupsLoader;

public Oidc(
    final JwtDecoder jwtDecoder,
    final OidcAuthenticationConfiguration oidcAuthenticationConfiguration,
    final OidcClaimsProvider claimsProvider) {
  this.jwtDecoder = Objects.requireNonNull(jwtDecoder);
  this.oidcAuthenticationConfiguration =
      Objects.requireNonNull(oidcAuthenticationConfiguration);
  this.claimsProvider = Objects.requireNonNull(claimsProvider);
  oidcPrincipalLoader =
      new OidcPrincipalLoader(
          oidcAuthenticationConfiguration.getUsernameClaim(),
          oidcAuthenticationConfiguration.getClientIdClaim());
  oidcGroupsLoader = new OidcGroupsLoader(oidcAuthenticationConfiguration.getGroupsClaim());
}
```

3. In `authenticate()` (line 71), after the successful `jwtDecoder.decode(...)` block, resolve augmented claims once:

Replace the body from line 79 onward up to `return Either.right(...)` with:

```java
final Jwt token;
try {
  token = jwtDecoder.decode(authorizationHeader.substring(BEARER_PREFIX.length()));
} catch (final JwtException e) {
  return Either.left(
      Status.UNAUTHENTICATED
          .augmentDescription("Expected a valid token, see cause for details")
          .withCause(e));
}

final Map<String, Object> claims;
try {
  claims =
      claimsProvider.claimsFor(
          token.getClaims(), authorizationHeader.substring(BEARER_PREFIX.length()));
} catch (final Exception e) {
  return Either.left(
      Status.UNAUTHENTICATED
          .augmentDescription("Failed to resolve OIDC claims, see cause for details")
          .withCause(e));
}

var context = Context.current();
context = context.withValue(IS_CAMUNDA_USERS_ENABLED, false);
context =
    context.withValue(
        IS_CAMUNDA_GROUPS_ENABLED,
        !oidcAuthenticationConfiguration.isGroupsClaimConfigured());
if (oidcAuthenticationConfiguration.isGroupsClaimConfigured()) {
  try {
    context = context.withValue(GROUPS_CLAIMS, oidcGroupsLoader.load(claims));
  } catch (final Exception e) {
    return Either.left(
        Status.UNAUTHENTICATED
            .augmentDescription("Failed to load OIDC groups, see cause for details")
            .withCause(e));
  }
}

final OidcPrincipals principals;
try {
  principals = oidcPrincipalLoader.load(claims);
} catch (final Exception e) {
  return Either.left(
      Status.UNAUTHENTICATED
          .augmentDescription("Failed to load OIDC principals, see cause for details")
          .withCause(e));
}

if (principals.username() == null && principals.clientId() == null) {
  return Either.left(
      Status.UNAUTHENTICATED.augmentDescription(
          "Expected either a username (claim: %s) or client ID (claim: %s) on the token, but no matching claim found"
              .formatted(
                  oidcAuthenticationConfiguration.getUsernameClaim(),
                  oidcAuthenticationConfiguration.getClientIdClaim())));
}

final var preferUsernameClaim = oidcAuthenticationConfiguration.isPreferUsernameClaim();
if ((preferUsernameClaim && principals.username() != null) || principals.clientId() == null) {
  return Either.right(
      context.withValue(USERNAME, principals.username()).withValue(USER_CLAIMS, claims));
} else {
  return Either.right(
      context.withValue(CLIENT_ID, principals.clientId()).withValue(USER_CLAIMS, claims));
}
```

- [ ] **Step 4: Thread the provider through `Gateway`**

In `zeebe/gateway-grpc/src/main/java/io/camunda/zeebe/gateway/Gateway.java`:

1. Add import: `import io.camunda.security.oidc.OidcClaimsProvider;`
2. Add field near `private final JwtDecoder jwtDecoder;` (line 100):
   ```java
   private final OidcClaimsProvider oidcClaimsProvider;
   ```
3. Update every constructor to take an `OidcClaimsProvider` parameter and assign it. Mirror the pattern used for `jwtDecoder` at lines 112, 135, 145.
4. In `applyInterceptors()` (line 418), update:
   ```java
   case OIDC ->
       new AuthenticationHandler.Oidc(
           jwtDecoder,
           securityConfiguration.getAuthentication().getOidc(),
           oidcClaimsProvider);
   ```

- [ ] **Step 5: Update `Gateway` callers to pass the provider**

In `dist/src/main/java/io/camunda/zeebe/gateway/GatewayModuleConfiguration.java` around line 113:
1. Inject `OidcClaimsProvider` via the existing Spring constructor injection (add parameter).
2. Pass it into `new Gateway(...)`.

In `zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/EmbeddedGatewayService.java` around line 48:
1. Accept an `OidcClaimsProvider` (via constructor injection from Spring).
2. Pass it into `new Gateway(...)`.

In `zeebe/qa/integration-tests/src/test/java/io/camunda/zeebe/it/cluster/clustering/ClusteringRule.java:524` and `zeebe/gateway-grpc/src/test/java/io/camunda/zeebe/gateway/api/util/StubbedGateway.java:109`:
Pass `new NoopOidcClaimsProvider()` as the new arg. Add import to each.

- [ ] **Step 6: Run the new unit test — expect pass**

Run: `mvn -pl zeebe/gateway-grpc test -Dtest=AuthenticationHandlerOidcUserInfoTest`

Expected: PASS.

- [ ] **Step 7: Run the broader gateway-grpc test suite**

Run: `mvn -pl zeebe/gateway-grpc test`

Expected: all PASS (existing tests should still pass because we added the arg without changing default behaviour — Noop provider is a pass-through).

- [ ] **Step 8: Commit**

```bash
git add zeebe/gateway-grpc/src/main/java/io/camunda/zeebe/gateway/interceptors/impl/AuthenticationHandler.java \
        zeebe/gateway-grpc/src/main/java/io/camunda/zeebe/gateway/Gateway.java \
        dist/src/main/java/io/camunda/zeebe/gateway/GatewayModuleConfiguration.java \
        zeebe/broker/src/main/java/io/camunda/zeebe/broker/system/EmbeddedGatewayService.java \
        zeebe/qa/integration-tests/src/test/java/io/camunda/zeebe/it/cluster/clustering/ClusteringRule.java \
        zeebe/gateway-grpc/src/test/java/io/camunda/zeebe/gateway/api/util/StubbedGateway.java \
        zeebe/gateway-grpc/src/test/java/io/camunda/zeebe/gateway/interceptors/impl/AuthenticationHandlerOidcUserInfoTest.java
git commit -m "feat(gateway): route gRPC OIDC claims through OidcClaimsProvider"
```

---

## Task 7: End-to-end integration tests — REST + mock IdP

These tests encode the **customer's acceptance criterion**: authorizations depend on a `groups` claim the IdP only returns from `/userinfo`, never in the JWT access token.

The paired classes are deliberate: `Disabled` documents the current bug (groups never reach the converter chain → authorization would be empty), `Enabled` documents the fix (groups from userinfo reach `TokenClaimsConverter` and therefore `MembershipService.resolveMemberships(...)`). Both mirror the existing `OidcUserAuthenticationConverterIntegrationTest` context pattern.

**Files:**
- Create: `authentication/src/test/java/io/camunda/authentication/oidc/OidcBearerUserInfoAugmentationDisabledIT.java`
- Create: `authentication/src/test/java/io/camunda/authentication/oidc/OidcBearerUserInfoAugmentationEnabledIT.java`

- [ ] **Step 1: Write the baseline (augmentation disabled) failing test**

Create `OidcBearerUserInfoAugmentationDisabledIT.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oidc;

import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.authentication.config.AbstractWebSecurityConfigTest;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import io.camunda.authentication.converter.OidcTokenAuthenticationConverter;
import io.camunda.authentication.converter.TokenClaimsConverter;
import io.camunda.security.auth.CamundaAuthentication;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Baseline: with UserInfo augmentation DISABLED, a JWT that lacks the configured {@code groups}
 * claim cannot be upgraded with the groups from the /userinfo response. This documents the
 * current behaviour the customer is seeing — authorizations that depend on {@code groups} will
 * see an empty groups list because the claim never reaches {@link TokenClaimsConverter}.
 */
@SuppressWarnings("SpringBootApplicationProperties")
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityOidcTestContext.class,
      WebSecurityConfig.class
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id=example",
      "camunda.security.authentication.oidc.redirect-uri=redirect.example.com",
      "camunda.security.authentication.oidc.authorization-uri=authorization.example.com",
      "camunda.security.authentication.oidc.token-uri=token.example.com",
      "camunda.security.authentication.oidc.groups-claim=groups",
      "camunda.security.authentication.oidc.user-info-augmentation.enabled=false",
    })
public class OidcBearerUserInfoAugmentationDisabledIT extends AbstractWebSecurityConfigTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().configureStaticDsl(true).build();

  @Autowired private OidcTokenAuthenticationConverter converter;

  @MockitoBean private TokenClaimsConverter tokenClaimsConverter;

  @DynamicPropertySource
  static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
    final var issuerUri = "http://localhost:" + wireMock.getPort() + "/issuer";
    registry.add("camunda.security.authentication.oidc.issuer-uri", () -> issuerUri);
    registry.add(
        "camunda.security.authentication.oidc.jwk-set-uri",
        () -> "http://localhost:" + wireMock.getPort() + "/issuer/jwks");

    // Stub the OIDC discovery document so client registration can be built at context startup.
    final var openidConfig =
        "{\"issuer\":\"" + issuerUri + "\","
            + "\"token_endpoint\":\"token.example.com\","
            + "\"jwks_uri\":\"http://localhost:" + wireMock.getPort() + "/issuer/jwks\","
            + "\"userinfo_endpoint\":\"" + issuerUri + "/userinfo\","
            + "\"subject_types_supported\":[\"public\"]}";
    wireMock
        .getRuntimeInfo()
        .getWireMock()
        .register(
            get(urlMatching(".*/issuer/.well-known/openid-configuration"))
                .willReturn(okJson(openidConfig)));
  }

  @Test
  void groupsClaimIsAbsentWhenAugmentationIsDisabledEvenIfUserInfoWouldProvideIt() {
    // Stub userinfo to return the groups claim the customer needs.
    // With augmentation disabled the provider must NOT call this endpoint.
    wireMock.stubFor(
        get(urlMatching(".*/userinfo"))
            .willReturn(okJson("{\"sub\":\"alice\",\"groups\":[\"engineering\"]}")));

    final var jwt =
        Jwt.withTokenValue("token-abc")
            .header("alg", "RS256")
            .claim("sub", "alice")
            .claim("iss", "http://localhost:" + wireMock.getPort() + "/issuer")
            .claim("jti", "jti-baseline")
            .claim("exp", Instant.now().getEpochSecond() + 3600)
            .build();

    Mockito.when(tokenClaimsConverter.convert(Mockito.any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("alice")));

    converter.convert(new JwtAuthenticationToken(jwt));

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(tokenClaimsConverter).convert(claimsCaptor.capture());
    assertThat(claimsCaptor.getValue())
        .containsEntry("sub", "alice")
        .doesNotContainKey("groups"); // the customer's bug: groups never arrive

    // Sanity-check: userinfo was never called when augmentation is disabled.
    wireMock.verify(0, com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor(
        urlMatching(".*/userinfo")));
  }
}
```

- [ ] **Step 2: Write the fix (augmentation enabled) failing test**

Create `OidcBearerUserInfoAugmentationEnabledIT.java`:

```java
/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.oidc;

import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import io.camunda.authentication.config.AbstractWebSecurityConfigTest;
import io.camunda.authentication.config.WebSecurityConfig;
import io.camunda.authentication.config.controllers.WebSecurityConfigTestContext;
import io.camunda.authentication.config.controllers.WebSecurityOidcTestContext;
import io.camunda.authentication.converter.OidcTokenAuthenticationConverter;
import io.camunda.authentication.converter.TokenClaimsConverter;
import io.camunda.security.auth.CamundaAuthentication;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Fix: with UserInfo augmentation ENABLED, the groups claim from the /userinfo response is
 * merged onto the JWT claims and therefore reaches {@link TokenClaimsConverter} (and through it
 * {@link io.camunda.authentication.service.MembershipService#resolveMemberships}).
 *
 * <p>This is the customer's acceptance test: a bearer request with a JWT that does not carry the
 * configured {@code groups} claim still results in group-based authorization, because the claim
 * is sourced from /userinfo and cached for the configured TTL.
 */
@SuppressWarnings("SpringBootApplicationProperties")
@SpringBootTest(
    classes = {
      WebSecurityConfigTestContext.class,
      WebSecurityOidcTestContext.class,
      WebSecurityConfig.class
    },
    properties = {
      "camunda.security.authentication.unprotected-api=false",
      "camunda.security.authentication.method=oidc",
      "camunda.security.authentication.oidc.client-id=example",
      "camunda.security.authentication.oidc.redirect-uri=redirect.example.com",
      "camunda.security.authentication.oidc.authorization-uri=authorization.example.com",
      "camunda.security.authentication.oidc.token-uri=token.example.com",
      "camunda.security.authentication.oidc.groups-claim=groups",
      "camunda.security.authentication.oidc.user-info-augmentation.enabled=true",
      "camunda.security.authentication.oidc.user-info-augmentation.cache-ttl=PT5M",
      "camunda.security.authentication.oidc.user-info-augmentation.cache-max-size=100",
    })
public class OidcBearerUserInfoAugmentationEnabledIT extends AbstractWebSecurityConfigTest {

  @RegisterExtension
  static WireMockExtension wireMock =
      WireMockExtension.newInstance().configureStaticDsl(true).build();

  @Autowired private OidcTokenAuthenticationConverter converter;

  @MockitoBean private TokenClaimsConverter tokenClaimsConverter;

  @DynamicPropertySource
  static void registerWireMockProperties(final DynamicPropertyRegistry registry) {
    final var issuerUri = "http://localhost:" + wireMock.getPort() + "/issuer";
    registry.add("camunda.security.authentication.oidc.issuer-uri", () -> issuerUri);
    registry.add(
        "camunda.security.authentication.oidc.jwk-set-uri",
        () -> "http://localhost:" + wireMock.getPort() + "/issuer/jwks");

    final var openidConfig =
        "{\"issuer\":\"" + issuerUri + "\","
            + "\"token_endpoint\":\"token.example.com\","
            + "\"jwks_uri\":\"http://localhost:" + wireMock.getPort() + "/issuer/jwks\","
            + "\"userinfo_endpoint\":\"" + issuerUri + "/userinfo\","
            + "\"subject_types_supported\":[\"public\"]}";
    wireMock
        .getRuntimeInfo()
        .getWireMock()
        .register(
            get(urlMatching(".*/issuer/.well-known/openid-configuration"))
                .willReturn(okJson(openidConfig)));
  }

  @Test
  void groupsClaimFromUserInfoReachesTokenClaimsConverterWhenAugmentationEnabled() {
    // The customer's scenario: JWT carries no groups claim, userinfo does.
    wireMock.stubFor(
        get(urlMatching(".*/userinfo"))
            .willReturn(okJson("{\"sub\":\"alice\",\"groups\":[\"engineering\"]}")));

    final var jwt =
        Jwt.withTokenValue("token-abc")
            .header("alg", "RS256")
            .claim("sub", "alice")
            .claim("iss", "http://localhost:" + wireMock.getPort() + "/issuer")
            .claim("jti", "jti-1")
            .claim("exp", Instant.now().getEpochSecond() + 3600)
            .build();

    Mockito.when(tokenClaimsConverter.convert(Mockito.any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("alice")));

    converter.convert(new JwtAuthenticationToken(jwt));

    @SuppressWarnings("unchecked")
    final ArgumentCaptor<Map<String, Object>> claimsCaptor = ArgumentCaptor.forClass(Map.class);
    verify(tokenClaimsConverter).convert(claimsCaptor.capture());

    // The critical assertion: groups arrive at TokenClaimsConverter even though the JWT
    // did not carry them. This is what unblocks group-based authorization for bearer calls.
    assertThat(claimsCaptor.getValue())
        .containsEntry("sub", "alice")
        .containsEntry("groups", List.of("engineering"));

    wireMock.verify(exactly(1), getRequestedFor(urlMatching(".*/userinfo")));
  }

  @Test
  void userInfoIsCalledOnlyOncePerTokenWithinTtl() {
    wireMock.stubFor(
        get(urlMatching(".*/userinfo"))
            .willReturn(okJson("{\"sub\":\"alice\",\"groups\":[\"engineering\"]}")));

    final var jwt =
        Jwt.withTokenValue("token-cached")
            .header("alg", "RS256")
            .claim("sub", "alice")
            .claim("iss", "http://localhost:" + wireMock.getPort() + "/issuer")
            .claim("jti", "jti-cached")
            .claim("exp", Instant.now().getEpochSecond() + 3600)
            .build();

    Mockito.when(tokenClaimsConverter.convert(Mockito.any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("alice")));

    for (int i = 0; i < 50; i++) {
      converter.convert(new JwtAuthenticationToken(jwt));
    }

    // This is the performance acceptance criterion: under a burst of bearer requests with the
    // same token, we must not hammer the IdP — Caffeine serves subsequent calls from cache.
    wireMock.verify(exactly(1), getRequestedFor(urlMatching(".*/userinfo")));
  }

  @Test
  void distinctTokensEachTriggerOneUserInfoCall() {
    wireMock.stubFor(
        get(urlMatching(".*/userinfo"))
            .willReturn(okJson("{\"sub\":\"alice\",\"groups\":[\"engineering\"]}")));

    Mockito.when(tokenClaimsConverter.convert(Mockito.any()))
        .thenReturn(CamundaAuthentication.of(b -> b.user("alice")));

    final long exp = Instant.now().getEpochSecond() + 3600;
    for (int i = 0; i < 3; i++) {
      final var jwt =
          Jwt.withTokenValue("token-" + i)
              .header("alg", "RS256")
              .claim("sub", "alice")
              .claim("iss", "http://localhost:" + wireMock.getPort() + "/issuer")
              .claim("jti", "jti-" + i)
              .claim("exp", exp)
              .build();
      converter.convert(new JwtAuthenticationToken(jwt));
    }

    wireMock.verify(exactly(3), getRequestedFor(urlMatching(".*/userinfo")));
  }
}
```

- [ ] **Step 3: Run the tests — expect initial failures**

Run: `mvn -pl authentication test -Dtest='OidcBearerUserInfoAugmentation*IT'`

Expected: the two tests in the `Disabled` class may pass already (the `doesNotContainKey("groups")` assertion holds with `NoopOidcClaimsProvider`). The three tests in the `Enabled` class should FAIL until Spring wiring resolves the userinfo URI and the `CachingOidcClaimsProvider` bean activates.

- [ ] **Step 4: Fix any wiring gaps surfaced by the tests**

Most likely issues:
- Property binding for the nested `user-info-augmentation` group — Spring's relaxed binding should accept both camelCase and kebab-case; check `OidcUserInfoAugmentationConfiguration` isn't missing a getter.
- `AbstractWebSecurityConfigTest` may need `OidcClaimsProvider` registered explicitly — verify by looking at how `TokenClaimsConverter` is wired into the existing test context (`WebSecurityOidcTestContext`).
- If `oidcClaimsProvider` bean throws at startup because there's no `userinfo_endpoint` in the discovery doc, ensure the WireMock stub in `@DynamicPropertySource` registers the discovery doc BEFORE the Spring context boots the bean. The existing test's `wireMock.getRuntimeInfo().getWireMock().register(...)` pattern works because it runs inside `@DynamicPropertySource` which fires before context creation.

Make minimal fixes only.

- [ ] **Step 5: Re-run, expect pass**

Run: `mvn -pl authentication test -Dtest='OidcBearerUserInfoAugmentation*IT'`

Expected: all five tests PASS.

- [ ] **Step 6: Commit**

```bash
git add authentication/src/test/java/io/camunda/authentication/oidc/OidcBearerUserInfoAugmentationDisabledIT.java \
        authentication/src/test/java/io/camunda/authentication/oidc/OidcBearerUserInfoAugmentationEnabledIT.java
git commit -m "test(authentication): integration tests for OIDC userinfo groups-claim augmentation"
```

---

## Task 8: Documentation and configuration reference

**Files:**
- Modify: `dist/src/main/config/application.yaml` (or the closest canonical config reference file — check with `ls dist/src/main/config/`)
- Modify: `docs/` (if Camunda keeps config docs in-repo — check under `docs/self-managed/` or similar)

- [ ] **Step 1: Document the three new properties in the canonical config reference**

Add to the OIDC section:

```yaml
camunda:
  security:
    authentication:
      oidc:
        # ... existing config ...
        userInfoAugmentation:
          # Enable merging of OIDC UserInfo response claims onto the JWT access
          # token claims for bearer-authenticated API requests. Required when
          # authorization claims (groups, roles, tenants) only appear in the
          # /userinfo response and cannot be added to the JWT access token
          # issued by the Identity Provider. When disabled, bearer requests
          # use only the JWT claims.
          enabled: false
          # Maximum age of a cached UserInfo response before the IdP is called
          # again. Effective TTL is min(cacheTtl, tokenExp - clockSkew - now)
          # so the cache never outlives the bearer token.
          cacheTtl: PT5M
          # Hard upper bound on cached entries. Cache is keyed by JWT 'jti'
          # (or SHA-256 of the raw token if 'jti' is absent), so this bounds
          # memory under high bearer-token churn.
          cacheMaxSize: 10000
```

- [ ] **Step 2: Commit**

```bash
git add dist/src/main/config/application.yaml
git commit -m "docs(config): document OIDC userinfo augmentation properties"
```

---

## Out of scope (flag for review)

- **Multi-provider userinfo URI selection:** the bean in Task 5 picks the first configured issuer's userinfo URI. Single-provider setups are fine; multi-provider selection by `iss` claim is a follow-up.
- **Discovery document caching:** we build the userinfo URI by appending `/userinfo` to the issuer URI. Strictly per-OIDC-spec the URI should be read from the discovery document. In practice this works for all major IdPs. Flag if the customer's IdP uses a non-standard userinfo path.
- **`OidcUserAuthenticationConverter` (webapp flow):** intentionally untouched. It already handles userinfo via Spring's OIDC login; this plan is only about bearer flow.
- **Backward compatibility:** `OidcTokenAuthenticationConverter`'s one-arg constructor is removed. If any out-of-tree code or tests instantiate it directly, they'll need updating. Search confirms only the `WebSecurityConfig` bean and tests use it.

---

## Risks and review focus

- **gRPC hot path latency:** the Caffeine `getIfPresent` + `put` is lock-free and sub-microsecond; the network call on cache miss is the real cost. Worker patterns (same token for 30 minutes) mean ~1 userinfo call per worker per 30 min, not per request. Reviewer should sanity-check this against the customer's worker count vs. IdP rate limits.
- **Fail-closed choice:** when userinfo fails, we reject the request. Alternative is fall-back to JWT-only claims — but that silently downgrades authorization and is a bigger security surprise. Recommend keeping fail-closed, but open to discussion for specific environments.
- **`NEGATIVE_CACHE_TTL = 5s`:** hard-coded. Worth making configurable? I'd lean no — it's an implementation defence against IdP outages, not a product lever.
- **Metric naming:** `camunda.oidc.userinfo.cache` and `camunda.oidc.userinfo.fetch`. Check against Camunda's micrometer naming convention — alignment may require adjustment.
