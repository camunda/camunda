/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationHolder;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Unit tests for the cross-request caching behaviour in {@link
 * DefaultCamundaAuthenticationProvider}.
 */
class CachingCamundaAuthenticationProviderTest {

  private CamundaAuthenticationHolder holder;
  private CamundaAuthenticationConverter<Authentication> converter;
  private Cache<String, CamundaAuthentication> cache;
  private DefaultCamundaAuthenticationProvider provider;

  @BeforeEach
  @SuppressWarnings("unchecked")
  void setUp() {
    holder = mock(CamundaAuthenticationHolder.class);
    converter = mock(CamundaAuthenticationConverter.class);
    // Use a real Caffeine cache with no automatic expiry — tests control invalidation
    cache = Caffeine.newBuilder().build();
    provider = new DefaultCamundaAuthenticationProvider(holder, converter, cache);
    SecurityContextHolder.clearContext();
  }

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  // ── helpers ────────────────────────────────────────────────────────────────

  private static JwtAuthenticationToken jwtAuth(final String tokenValue) {
    final Jwt jwt =
        Jwt.withTokenValue(tokenValue)
            .header("alg", "RS256")
            .claim("sub", "client-id")
            .expiresAt(Instant.now().plusSeconds(300))
            .issuedAt(Instant.now().minusSeconds(60))
            .build();
    return new JwtAuthenticationToken(jwt);
  }

  private static Authentication nonOidcAuth() {
    final var auth = mock(Authentication.class);
    when(auth.getPrincipal()).thenReturn("basic-user");
    return auth;
  }

  // ── tests ──────────────────────────────────────────────────────────────────

  @Test
  void cacheMissOnFirstCall_delegateConverterIsCalled() {
    // given
    final var oidcAuth = jwtAuth("token-aaa");
    SecurityContextHolder.getContext().setAuthentication(oidcAuth);
    final var expected = CamundaAuthentication.of(b -> b.clientId("client-id"));
    when(converter.convert(oidcAuth)).thenReturn(expected);

    // when
    final var result = provider.getCamundaAuthentication();

    // then
    assertThat(result).isEqualTo(expected);
    verify(converter, times(1)).convert(oidcAuth);
  }

  @Test
  void cacheHitOnSecondCall_converterCalledOnlyOnce() {
    // given
    final var oidcAuth = jwtAuth("token-bbb");
    SecurityContextHolder.getContext().setAuthentication(oidcAuth);
    final var expected = CamundaAuthentication.of(b -> b.clientId("client-id"));
    when(converter.convert(oidcAuth)).thenReturn(expected);

    // when — first call populates the cache
    when(holder.get()).thenReturn(null);
    final var first = provider.getCamundaAuthentication();

    // Simulate second request: reset per-request holder, same security context and cache
    when(holder.get()).thenReturn(null);
    final var second = provider.getCamundaAuthentication();

    // then — converter called only once; both calls return the same resolved authentication
    verify(converter, times(1)).convert(any());
    assertThat(first).isEqualTo(expected);
    assertThat(second).isEqualTo(expected);
  }

  @Test
  void differentTokens_eachResolvedIndependently() {
    // given
    final var authA = jwtAuth("token-ccc");
    final var authD = jwtAuth("token-ddd");
    final var expectedA = CamundaAuthentication.of(b -> b.clientId("client-a"));
    final var expectedD = CamundaAuthentication.of(b -> b.clientId("client-d"));
    when(converter.convert(authA)).thenReturn(expectedA);
    when(converter.convert(authD)).thenReturn(expectedD);

    // when
    SecurityContextHolder.getContext().setAuthentication(authA);
    when(holder.get()).thenReturn(null);
    final var resultA = provider.getCamundaAuthentication();

    SecurityContextHolder.getContext().setAuthentication(authD);
    when(holder.get()).thenReturn(null);
    final var resultD = provider.getCamundaAuthentication();

    // then — each token resolved exactly once
    assertThat(resultA).isEqualTo(expectedA);
    assertThat(resultD).isEqualTo(expectedD);
    verify(converter, times(1)).convert(authA);
    verify(converter, times(1)).convert(authD);
  }

  @Test
  void nonOidcAuth_cacheIsBypassed_converterCalledEachTime() {
    // given
    final var basicAuth = nonOidcAuth();
    SecurityContextHolder.getContext().setAuthentication(basicAuth);
    final var expected = CamundaAuthentication.of(b -> b.user("basic-user"));
    when(converter.convert(basicAuth)).thenReturn(expected);

    // when — two calls with non-OIDC auth (holder returns null each time)
    when(holder.get()).thenReturn(null);
    provider.getCamundaAuthentication();
    when(holder.get()).thenReturn(null);
    provider.getCamundaAuthentication();

    // then — converter is called each time; nothing was put in the cross-request cache
    verify(converter, times(2)).convert(basicAuth);
    assertThat(cache.getIfPresent("any-key")).isNull();
  }

  @Test
  void perRequestHolderPopulatedAfterFirstCall() {
    // given
    final var oidcAuth = jwtAuth("token-eee");
    SecurityContextHolder.getContext().setAuthentication(oidcAuth);
    final var expected = CamundaAuthentication.of(b -> b.clientId("client-id"));
    when(converter.convert(oidcAuth)).thenReturn(expected);
    when(holder.get()).thenReturn(null);

    // when
    provider.getCamundaAuthentication();

    // then — holder.set() was called so same-request subsequent calls go through the fast path
    verify(holder).set(expected);
  }

  @Test
  void cacheHit_holderIsStillPopulated() {
    // given — prime the cache directly (simulates a previous request having resolved the token)
    final var oidcAuth = jwtAuth("token-fff");
    SecurityContextHolder.getContext().setAuthentication(oidcAuth);
    final var expected = CamundaAuthentication.of(b -> b.clientId("client-id"));
    cache.put("token-fff", expected);

    // holder returns null — start of a new request
    when(holder.get()).thenReturn(null);

    // when
    final var result = provider.getCamundaAuthentication();

    // then — result served from cache, and holder is populated for intra-request subsequent calls
    assertThat(result).isEqualTo(expected);
    verify(converter, times(0)).convert(any());
    verify(holder).set(expected);
  }
}
