/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
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
import java.util.List;
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
        .thenReturn(Map.of("groups", List.of("eng"), "sub", "alice-from-userinfo"));

    final var provider =
        new CachingOidcClaimsProvider(
            oidcConfig, URI.create("https://idp.example/userinfo"), userInfoClient, meterRegistry);

    final Map<String, Object> jwtClaims =
        Map.of("sub", "alice", "jti", "jti-1", "exp", Instant.now().getEpochSecond() + 3600);

    final var result = provider.claimsFor(jwtClaims, "token-abc");

    assertThat(result).containsEntry("groups", List.of("eng"));
    assertThat(result).containsEntry("sub", "alice-from-userinfo"); // userinfo overrides
    assertThat(result).containsEntry("jti", "jti-1"); // jwt-only claim preserved
    verify(userInfoClient, times(1)).fetch(any(), any());
  }

  @Test
  void cachesByJtiSoSecondCallDoesNotHitIdp() {
    when(userInfoClient.fetch(any(), any())).thenReturn(Map.of("groups", List.of("eng")));

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
    when(userInfoClient.fetch(any(), any())).thenReturn(Map.of("groups", List.of("eng")));

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
    when(userInfoClient.fetch(any(), any())).thenReturn(Map.of("groups", List.of("eng")));

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
    when(userInfoClient.fetch(any(), any())).thenThrow(new OidcUserInfoException("boom"));

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
    when(userInfoClient.fetch(any(), any())).thenThrow(new OidcUserInfoException("IdP down"));

    final var provider =
        new CachingOidcClaimsProvider(
            oidcConfig, URI.create("https://idp.example/userinfo"), userInfoClient, meterRegistry);

    final Map<String, Object> jwtClaims =
        Map.of("sub", "alice", "jti", "jti-1", "exp", Instant.now().getEpochSecond() + 3600);

    for (int i = 0; i < 20; i++) {
      try {
        provider.claimsFor(jwtClaims, "token-abc");
      } catch (final OidcUserInfoException ignored) {
        // expected
      }
    }

    verify(userInfoClient, times(1)).fetch(any(), any());
  }

  @Test
  void ttlIsCappedByTokenExpiry() {
    when(userInfoClient.fetch(any(), any())).thenReturn(Map.of("groups", List.of("eng")));

    oidcConfig.getUserInfoAugmentation().setCacheTtl(Duration.ofHours(1));
    final var provider =
        new CachingOidcClaimsProvider(
            oidcConfig, URI.create("https://idp.example/userinfo"), userInfoClient, meterRegistry);

    // exp is far enough in the past that after clockSkew the effective TTL is <= 0
    final long expPast = Instant.now().minusSeconds(120).getEpochSecond();
    final Map<String, Object> expired = Map.of("sub", "alice", "jti", "jti-1", "exp", expPast);

    provider.claimsFor(expired, "token-abc");
    provider.claimsFor(expired, "token-abc");

    // Entry expired immediately, both calls should hit the IdP
    verify(userInfoClient, times(2)).fetch(any(), any());
  }

  @Test
  void exposesHitAndMissCounters() {
    when(userInfoClient.fetch(any(), any())).thenReturn(Map.of("groups", List.of("eng")));

    final var provider =
        new CachingOidcClaimsProvider(
            oidcConfig, URI.create("https://idp.example/userinfo"), userInfoClient, meterRegistry);

    final Map<String, Object> jwtClaims =
        Map.of("sub", "alice", "jti", "jti-1", "exp", Instant.now().getEpochSecond() + 3600);

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
}
