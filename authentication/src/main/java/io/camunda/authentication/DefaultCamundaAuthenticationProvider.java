/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationConverter;
import io.camunda.security.auth.CamundaAuthenticationHolder;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.checkerframework.checker.index.qual.NonNegative;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.AbstractOAuth2TokenAuthenticationToken;

public class DefaultCamundaAuthenticationProvider implements CamundaAuthenticationProvider {

  private static final Logger LOG =
      LoggerFactory.getLogger(DefaultCamundaAuthenticationProvider.class);

  /** Maximum TTL for a cache entry even if the token's own expiry is further in the future. */
  static final Duration MAX_CACHE_TTL = Duration.ofHours(24);

  /** Fallback TTL when the token carries no expiry claim. */
  static final Duration DEFAULT_CACHE_TTL = Duration.ofMinutes(5);

  private final CamundaAuthenticationHolder holder;
  private final CamundaAuthenticationConverter<Authentication> converter;

  /**
   * Cross-request cache: raw JWT token string → resolved CamundaAuthentication.
   *
   * <p>Keyed by the raw Bearer token value so that M2M clients reusing the same JWT across many
   * requests skip all 4 membership DB queries after the first resolution. Each entry expires when
   * the token itself expires (capped at {@link #MAX_CACHE_TTL}).
   */
  private final Cache<String, CamundaAuthentication> tokenCache;

  public DefaultCamundaAuthenticationProvider(
      final CamundaAuthenticationHolder holder,
      final CamundaAuthenticationConverter<Authentication> converter) {
    this.holder = holder;
    this.converter = converter;
    this.tokenCache =
        Caffeine.newBuilder()
            .expireAfter(
                new Expiry<String, CamundaAuthentication>() {
                  @Override
                  public long expireAfterCreate(
                      final String key,
                      final CamundaAuthentication value,
                      final long currentTime) {
                    return computeTtlNanos(key);
                  }

                  @Override
                  public long expireAfterUpdate(
                      final String key,
                      final CamundaAuthentication value,
                      final long currentTime,
                      @NonNegative final long currentDuration) {
                    return currentDuration;
                  }

                  @Override
                  public long expireAfterRead(
                      final String key,
                      final CamundaAuthentication value,
                      final long currentTime,
                      @NonNegative final long currentDuration) {
                    return currentDuration;
                  }
                })
            .build();
  }

  /** Package-private constructor for tests that supply a pre-built cache instance. */
  DefaultCamundaAuthenticationProvider(
      final CamundaAuthenticationHolder holder,
      final CamundaAuthenticationConverter<Authentication> converter,
      final Cache<String, CamundaAuthentication> tokenCache) {
    this.holder = holder;
    this.converter = converter;
    this.tokenCache = tokenCache;
  }

  @Override
  public CamundaAuthentication getCamundaAuthentication() {
    final var springBasedAuthentication = SecurityContextHolder.getContext().getAuthentication();

    // 1. Check per-request holder first — cheapest path, no allocations
    final var holderResult = getFromHolderIfPresent(springBasedAuthentication);
    if (holderResult.isPresent()) {
      return holderResult.get();
    }

    // 2. Resolve — use the cross-request cache for OIDC bearer tokens only
    final CamundaAuthentication result;
    if (springBasedAuthentication
        instanceof AbstractOAuth2TokenAuthenticationToken<?> oidcToken) {
      final var tokenValue = oidcToken.getToken().getTokenValue();
      result = tokenCache.get(tokenValue, k -> converter.convert(springBasedAuthentication));
    } else {
      result = converter.convert(springBasedAuthentication);
    }

    LOG.debug("Resolved camunda authentication: {}", result);

    // 3. Populate the per-request holder so intra-request subsequent calls are free
    Optional.ofNullable(result).filter(a -> !a.isAnonymous()).ifPresent(holder::set);

    return result;
  }

  private Optional<CamundaAuthentication> getFromHolderIfPresent(
      final Authentication authentication) {
    return Optional.ofNullable(authentication).map(principal -> holder.get());
  }

  /**
   * Returns the Caffeine TTL in nanoseconds for the given raw token value.
   *
   * <p>Called synchronously from {@code expireAfterCreate} while the loading thread's
   * SecurityContext still holds the JWT being cached, so the expiresAt can be read directly.
   */
  private static long computeTtlNanos(final String tokenValue) {
    final var auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof AbstractOAuth2TokenAuthenticationToken<?> oidcToken
        && oidcToken.getToken().getTokenValue().equals(tokenValue)) {
      final Instant expiresAt = oidcToken.getToken().getExpiresAt();
      if (expiresAt != null) {
        final long remainingNanos = ChronoUnit.NANOS.between(Instant.now(), expiresAt);
        final long maxNanos = MAX_CACHE_TTL.toNanos();
        return Math.max(0L, Math.min(remainingNanos, maxNanos));
      }
    }
    return DEFAULT_CACHE_TTL.toNanos();
  }
}
