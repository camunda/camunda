/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletionException;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * A {@link JwtDecoder} that caches successfully validated JWTs by their raw token string.
 *
 * <p>JWT signature verification (RSA/ECDSA) is expensive. Under load, clients reuse the same token
 * for its full lifetime. This decorator eliminates repeated cryptographic work by caching the
 * parsed {@link Jwt} until the token's own {@code exp} claim expires.
 *
 * <p>Security properties:
 *
 * <ul>
 *   <li>Entries are evicted at the token's {@code exp} time — expired tokens are never served from
 *       cache.
 *   <li>Tokens that fail validation are not cached; every invalid token is re-checked on the next
 *       request.
 *   <li>Mid-lifetime revocation (e.g. Keycloak logout, JWKS rotation) is not detected until the
 *       cached entry expires. This is an accepted trade-off for stateless bearer tokens.
 * </ul>
 */
public class CachingJwtDecoder implements JwtDecoder {

  static final int DEFAULT_MAX_CACHE_SIZE = 10_000;
  static final Duration DEFAULT_MAX_TOKEN_LIFETIME = Duration.ofHours(24);

  private final JwtDecoder delegate;
  private final Cache<String, Jwt> cache;

  public CachingJwtDecoder(final JwtDecoder delegate) {
    this(delegate, DEFAULT_MAX_CACHE_SIZE, DEFAULT_MAX_TOKEN_LIFETIME);
  }

  CachingJwtDecoder(
      final JwtDecoder delegate, final int maxCacheSize, final Duration maxTokenLifetime) {
    this.delegate = delegate;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(maxCacheSize)
            .expireAfter(expiryPolicy(maxTokenLifetime))
            .build();
  }

  @Override
  public Jwt decode(final String token) throws JwtException {
    try {
      return cache.get(token, delegate::decode);
    } catch (final CompletionException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof final JwtException jwtException) {
        throw jwtException;
      }
      throw new BadJwtException("JWT decoding failed: " + cause.getMessage(), cause);
    }
  }

  private static Expiry<String, Jwt> expiryPolicy(final Duration maxTokenLifetime) {
    return new Expiry<>() {
      @Override
      public long expireAfterCreate(final String key, final Jwt value, final long currentTime) {
        final Instant expiresAt = value.getExpiresAt();
        if (expiresAt == null) {
          return maxTokenLifetime.toNanos();
        }
        final long remainingNanos = ChronoUnit.NANOS.between(Instant.now(), expiresAt);
        return Math.min(Math.max(0L, remainingNanos), maxTokenLifetime.toNanos());
      }

      @Override
      public long expireAfterUpdate(
          final String key, final Jwt value, final long currentTime, final long currentDuration) {
        return currentDuration;
      }

      @Override
      public long expireAfterRead(
          final String key, final Jwt value, final long currentTime, final long currentDuration) {
        return currentDuration;
      }
    };
  }
}
