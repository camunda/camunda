/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.security.oidc;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    userInfoClient = mock(OidcUserInfoClient.class);
    meterRegistry = new SimpleMeterRegistry();
  }

  private CachingOidcClaimsProvider newProvider() {
    return new CachingOidcClaimsProvider(
        oidcConfig, URI.create("https://idp.example/userinfo"), userInfoClient, meterRegistry);
  }

  @Test
  void returnsJwtClaimsUnchangedWhenAugmentationDisabled() {
    oidcConfig.getUserInfoAugmentation().setEnabled(false);
    final var provider = newProvider();

    final Map<String, Object> jwtClaims = Map.of("sub", "alice");
    assertThat(provider.claimsFor(jwtClaims, "token-abc")).isSameAs(jwtClaims);
    verify(userInfoClient, times(0)).fetch(any(), any());
  }

  @Test
  void mergeIsAdditiveOnlyJwtWinsOnConflict() {
    // UserInfo tries to override claims that should be JWT-authoritative.
    when(userInfoClient.fetch(any(), eq("token-abc")))
        .thenReturn(
            Map.of(
                "sub",
                "alice", // matches JWT — legitimate
                "groups",
                List.of("eng"), // absent from JWT — should be added
                "exp",
                Instant.now().plusSeconds(99_999).getEpochSecond(), // UserInfo MUST NOT override
                "email",
                "alice@example.com")); // absent from JWT — added

    final var provider = newProvider();
    final long exp = Instant.now().getEpochSecond() + 3600;
    final Map<String, Object> jwtClaims =
        Map.of("sub", "alice", "iss", "https://idp.example", "jti", "jti-1", "exp", exp);

    final Map<String, Object> result = provider.claimsFor(jwtClaims, "token-abc");

    assertThat(result)
        .containsEntry("sub", "alice")
        .containsEntry("iss", "https://idp.example")
        .containsEntry("jti", "jti-1")
        .containsEntry("exp", exp) // JWT's exp preserved; UserInfo's ignored
        .containsEntry("groups", List.of("eng")) // additive
        .containsEntry("email", "alice@example.com"); // additive
  }

  @Test
  void fallsBackToJwtClaimsWhenUserInfoSubMismatchesJwtSub() {
    // Spec §5.3.2 MUST — UserInfo sub must equal JWT sub. Mismatch means the IdP
    // returned the wrong user's claims; treat as a degradation event, don't merge.
    when(userInfoClient.fetch(any(), eq("token-abc")))
        .thenReturn(Map.of("sub", "bob", "groups", List.of("admin")));

    final var provider = newProvider();
    final long exp = Instant.now().getEpochSecond() + 3600;
    final Map<String, Object> jwtClaims =
        Map.of("sub", "alice", "iss", "https://idp.example", "jti", "jti-1", "exp", exp);

    final Map<String, Object> result = provider.claimsFor(jwtClaims, "token-abc");

    // groups from bob's response must NOT reach alice's authorization
    assertThat(result).containsEntry("sub", "alice").doesNotContainKey("groups");
    assertThat(
            meterRegistry
                .get("camunda.oidc.userinfo.fetch")
                .tag("outcome", "failure")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void callsUserInfoOnceAndAddsMissingClaimsFromUserInfo() {
    when(userInfoClient.fetch(any(), eq("token-abc"))).thenReturn(Map.of("groups", List.of("eng")));

    final var provider = newProvider();
    final Map<String, Object> jwtClaims =
        Map.of(
            "sub",
            "alice",
            "iss",
            "https://idp.example",
            "jti",
            "jti-1",
            "exp",
            Instant.now().getEpochSecond() + 3600);

    final Map<String, Object> result = provider.claimsFor(jwtClaims, "token-abc");

    assertThat(result).containsEntry("groups", List.of("eng"));
    verify(userInfoClient, times(1)).fetch(any(), any());
  }

  @Test
  void cachesByJtiSoSecondCallDoesNotHitIdp() {
    when(userInfoClient.fetch(any(), any())).thenReturn(Map.of("groups", List.of("eng")));

    final var provider = newProvider();
    final Map<String, Object> jwtClaims =
        Map.of(
            "sub",
            "alice",
            "iss",
            "https://idp.example",
            "jti",
            "jti-1",
            "exp",
            Instant.now().getEpochSecond() + 3600);

    provider.claimsFor(jwtClaims, "token-abc");
    provider.claimsFor(jwtClaims, "token-abc");

    verify(userInfoClient, times(1)).fetch(any(), any());
  }

  @Test
  void distinctJtisResultInDistinctCacheEntries() {
    when(userInfoClient.fetch(any(), any())).thenReturn(Map.of("groups", List.of("eng")));

    final var provider = newProvider();
    final long exp = Instant.now().getEpochSecond() + 3600;
    provider.claimsFor(
        Map.of("sub", "alice", "iss", "https://idp.example", "jti", "jti-1", "exp", exp),
        "token-a");
    provider.claimsFor(
        Map.of("sub", "alice", "iss", "https://idp.example", "jti", "jti-2", "exp", exp),
        "token-b");

    verify(userInfoClient, times(2)).fetch(any(), any());
  }

  @Test
  void cacheKeyIncludesIssSoSameJtiFromDifferentIssuersDoNotCollide() {
    // Key invariant: jti is only unique per-issuer (RFC 7519); without the iss prefix,
    // two providers could legitimately issue tokens with the same jti and collide in the
    // cache, leaking one user's merged claims to the other.
    when(userInfoClient.fetch(any(), any())).thenReturn(Map.of("groups", List.of("eng")));

    final var provider = newProvider();
    final long exp = Instant.now().getEpochSecond() + 3600;

    final Map<String, Object> issA =
        Map.of("sub", "alice", "iss", "https://idp-a.example", "jti", "shared-jti", "exp", exp);
    final Map<String, Object> issB =
        Map.of("sub", "alice", "iss", "https://idp-b.example", "jti", "shared-jti", "exp", exp);

    provider.claimsFor(issA, "token-a");
    provider.claimsFor(issB, "token-b");

    verify(userInfoClient, times(2)).fetch(any(), any());
  }

  @Test
  void fallsBackToSubIatExpKeyWhenJtiAbsent() {
    when(userInfoClient.fetch(any(), any())).thenReturn(Map.of("groups", List.of("eng")));

    final var provider = newProvider();
    final long exp = Instant.now().getEpochSecond() + 3600;
    final long iat = Instant.now().getEpochSecond();
    final Map<String, Object> claimsNoJti =
        Map.of("sub", "alice", "iss", "https://idp.example", "iat", iat, "exp", exp);

    provider.claimsFor(claimsNoJti, "token-abc");
    provider.claimsFor(claimsNoJti, "token-abc"); // same sub+iat+exp+iss -> cache hit

    verify(userInfoClient, times(1)).fetch(any(), any());
  }

  @Test
  void bypassesCacheWhenNeitherJtiNorSubIatExpAvailable() {
    // Malformed/unusual JWT with no jti, no iat, no exp. Can't key safely — skip cache
    // entirely so we never store the bearer-token material and every request re-fetches.
    when(userInfoClient.fetch(any(), any())).thenReturn(Map.of("groups", List.of("eng")));

    final var provider = newProvider();
    final Map<String, Object> sparseClaims = Map.of("sub", "alice", "iss", "https://idp.example");

    provider.claimsFor(sparseClaims, "token-abc");
    provider.claimsFor(sparseClaims, "token-abc");

    verify(userInfoClient, times(2)).fetch(any(), any());
  }

  @Test
  void fallsBackToJwtClaimsWhenUserInfoFetchThrows() {
    when(userInfoClient.fetch(any(), any())).thenThrow(new OidcUserInfoException("IdP down"));

    final var provider = newProvider();
    final Map<String, Object> jwtClaims =
        Map.of(
            "sub",
            "alice",
            "iss",
            "https://idp.example",
            "jti",
            "jti-1",
            "exp",
            Instant.now().getEpochSecond() + 3600);

    // Fail-open: returns JWT claims unchanged, increments failure counter, does not throw.
    final Map<String, Object> result = provider.claimsFor(jwtClaims, "token-abc");
    assertThat(result).containsEntry("sub", "alice").doesNotContainKey("groups");
    assertThat(
            meterRegistry
                .get("camunda.oidc.userinfo.fetch")
                .tag("outcome", "failure")
                .counter()
                .count())
        .isEqualTo(1.0);
  }

  @Test
  void degradedEntryPreventsRetrySpamDuringOutage() {
    when(userInfoClient.fetch(any(), any())).thenThrow(new OidcUserInfoException("IdP down"));

    final var provider = newProvider();
    final Map<String, Object> jwtClaims =
        Map.of(
            "sub",
            "alice",
            "iss",
            "https://idp.example",
            "jti",
            "jti-1",
            "exp",
            Instant.now().getEpochSecond() + 3600);

    for (int i = 0; i < 20; i++) {
      provider.claimsFor(jwtClaims, "token-abc");
    }

    verify(userInfoClient, times(1)).fetch(any(), any());
  }

  @Test
  void ttlCapRespectsExpWhenExpIsInstant() {
    // Spring's NimbusJwtDecoder + MappedJwtClaimSetConverter surfaces 'exp' as a
    // java.time.Instant, not a Number. Regression guard against the earlier
    // `instanceof Number` check that silently fell through to the 5-minute fallback
    // in production.
    when(userInfoClient.fetch(any(), any())).thenReturn(Map.of("groups", List.of("eng")));

    oidcConfig.getUserInfoAugmentation().setCacheTtl(Duration.ofHours(1));
    final var provider = newProvider();

    final Instant expPast = Instant.now().minusSeconds(120);
    final Map<String, Object> expired =
        Map.of("sub", "alice", "iss", "https://idp.example", "jti", "jti-i", "exp", expPast);

    provider.claimsFor(expired, "token-abc");
    provider.claimsFor(expired, "token-abc");

    verify(userInfoClient, times(2)).fetch(any(), any());
  }

  @Test
  void ttlIsCappedByTokenExpiry() {
    when(userInfoClient.fetch(any(), any())).thenReturn(Map.of("groups", List.of("eng")));

    oidcConfig.getUserInfoAugmentation().setCacheTtl(Duration.ofHours(1));
    final var provider = newProvider();

    // exp is far enough in the past that after clockSkew the effective TTL is <= 0
    final long expPast = Instant.now().minusSeconds(120).getEpochSecond();
    final Map<String, Object> expired =
        Map.of("sub", "alice", "iss", "https://idp.example", "jti", "jti-1", "exp", expPast);

    provider.claimsFor(expired, "token-abc");
    provider.claimsFor(expired, "token-abc");

    // Entry expired immediately, both calls should hit the IdP
    verify(userInfoClient, times(2)).fetch(any(), any());
  }

  @Test
  void exposesHitAndMissCounters() {
    when(userInfoClient.fetch(any(), any())).thenReturn(Map.of("groups", List.of("eng")));

    final var provider = newProvider();
    final Map<String, Object> jwtClaims =
        Map.of(
            "sub",
            "alice",
            "iss",
            "https://idp.example",
            "jti",
            "jti-1",
            "exp",
            Instant.now().getEpochSecond() + 3600);

    provider.claimsFor(jwtClaims, "token-abc"); // miss
    provider.claimsFor(jwtClaims, "token-abc"); // hit

    assertThat(
            meterRegistry
                .get("camunda.oidc.userinfo.cache")
                .tag("result", "miss")
                .counter()
                .count())
        .isEqualTo(1.0);
    assertThat(
            meterRegistry.get("camunda.oidc.userinfo.cache").tag("result", "hit").counter().count())
        .isEqualTo(1.0);
  }

  @Test
  void concurrentRequestsForSameJtiTriggerSingleFetch() throws Exception {
    final CountDownLatch startGate = new CountDownLatch(1);
    final CountDownLatch finishGate = new CountDownLatch(10);
    when(userInfoClient.fetch(any(), any()))
        .thenAnswer(
            invocation -> {
              // slow fetch — simulates IdP latency so all threads race on the miss
              Thread.sleep(50);
              return Map.of("groups", List.of("eng"));
            });

    final var provider = newProvider();
    final Map<String, Object> jwtClaims =
        Map.of(
            "sub",
            "alice",
            "iss",
            "https://idp.example",
            "jti",
            "jti-concurrent",
            "exp",
            Instant.now().getEpochSecond() + 3600);

    final ExecutorService pool = Executors.newFixedThreadPool(10);
    try {
      for (int i = 0; i < 10; i++) {
        pool.submit(
            () -> {
              try {
                startGate.await();
                provider.claimsFor(jwtClaims, "token-concurrent");
              } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
              } finally {
                finishGate.countDown();
              }
            });
      }
      startGate.countDown(); // release all threads
      assertThat(finishGate.await(5, TimeUnit.SECONDS)).isTrue();
    } finally {
      pool.shutdownNow();
    }

    // Caffeine's atomic cache.get(key, loader) coalesces concurrent misses on the same key.
    verify(userInfoClient, times(1)).fetch(any(), any());
  }

  @Test
  void restoresInterruptFlagWhenFetchIsInterrupted() throws Exception {
    when(userInfoClient.fetch(any(), any()))
        .thenAnswer(
            invocation -> {
              throw new InterruptedException("simulated interrupt during fetch");
            });

    final var provider = newProvider();
    final Map<String, Object> jwtClaims =
        Map.of(
            "sub",
            "alice",
            "iss",
            "https://idp.example",
            "jti",
            "jti-int",
            "exp",
            Instant.now().getEpochSecond() + 3600);

    // Run in a dedicated thread so we can inspect its interrupt state.
    final boolean[] interruptedAfter = {false};
    final Map<String, Object>[] result = new Map[1];
    final Thread t =
        new Thread(
            () -> {
              result[0] = provider.claimsFor(jwtClaims, "token-int");
              interruptedAfter[0] = Thread.interrupted();
            });
    t.start();
    t.join(2_000);

    assertThat(interruptedAfter[0])
        .as("interrupt flag must be restored after swallowed InterruptedException")
        .isTrue();
    assertThat(result[0]).as("fail-open returns JWT claims").containsEntry("sub", "alice");
  }
}
