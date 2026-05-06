/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.authentication.jwt;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import io.camunda.zeebe.util.VisibleForTesting;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * Wraps a delegate {@link JwtDecoder} with a per-token cache so that repeated decodes of the same
 * bearer token short-circuit JWS signature verification, claim extraction, and validator
 * invocation.
 *
 * <p>The auth chain on the API path runs {@code JwtDecoder.decode(...)} once per request and the
 * decode is dominated by RSA signature verification + Base64 decoding + claim parsing — visible at
 * 25–30% of work-CPU on Tomcat workers in a wall-clock profile of the {@code nodb} configuration.
 * Bearer-token clients in production reuse the same access token across many requests (typically
 * for the lifetime of the token), so the hit rate on a per-token cache approaches 100 % once warm.
 *
 * <p>Cache semantics:
 *
 * <ul>
 *   <li><b>Key:</b> SHA-256 hex digest of the raw token string. The token itself is not stored.
 *       Bounds memory (32 bytes × maxSize) regardless of token size and avoids retaining the
 *       bearer-token bytes on the heap longer than necessary.
 *   <li><b>Value:</b> the {@link Jwt} returned by the delegate decode (immutable).
 *   <li><b>TTL:</b> per entry, clamped to {@code min(configured maxTtl, jwt.exp − now)}. Entries
 *       are evicted at or before the token's natural expiry, so a cache hit is never staler than a
 *       fresh decode would have been (both observe the same {@code exp}).
 *   <li><b>Negative caching:</b> none. {@link JwtException} is propagated and not cached, so a
 *       briefly-flapping JWKS or an in-flight key rollover is re-attempted per request rather than
 *       cementing a "rejected" decision.
 *   <li><b>Concurrency:</b> Caffeine's atomic {@code get(key, loader)} coalesces concurrent misses
 *       on the same token onto a single delegate decode.
 * </ul>
 *
 * <p>This wrapper does not change the validation surface — every cache miss runs the delegate
 * decode (signature verify + claim validators) fully. Only successful decodes are cached, and only
 * for as long as the token would remain valid anyway.
 */
public class CachingJwtDecoder implements JwtDecoder {

  private static final Logger LOG = LoggerFactory.getLogger(CachingJwtDecoder.class);

  private final JwtDecoder delegate;
  private final Cache<String, Jwt> cache;

  public CachingJwtDecoder(final JwtDecoder delegate, final long maxSize, final Duration maxTtl) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    cache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfter(new TokenExpiry(Objects.requireNonNull(maxTtl, "maxTtl")))
            .build();
    LOG.info(
        "Caching JWT decoder enabled: maxSize={} maxTtl={} (delegate {})",
        maxSize,
        maxTtl,
        delegate.getClass().getSimpleName());
  }

  @Override
  public Jwt decode(final String token) throws JwtException {
    if (token == null || token.isEmpty()) {
      // Let the delegate produce its standard error so behaviour matches an uncached decode.
      return delegate.decode(token);
    }

    final String key = sha256Hex(token);
    final Jwt cached = cache.getIfPresent(key);
    if (cached != null) {
      return cached;
    }

    // get(key, loader) is atomic per key — concurrent misses on the same token coalesce onto one
    // delegate decode. The loader may throw JwtException, which Caffeine surfaces to all waiters
    // without caching anything.
    return cache.get(key, k -> delegate.decode(token));
  }

  /** Drop every cached entry — useful when a JWKS rollover invalidates previously-valid tokens. */
  public void invalidateAll() {
    cache.invalidateAll();
  }

  @VisibleForTesting
  long estimatedSize() {
    return cache.estimatedSize();
  }

  /**
   * Hex SHA-256 digest of the token. Used as the cache key so the bearer-token string is not
   * retained on the heap and the per-entry key size is bounded.
   */
  private static String sha256Hex(final String token) {
    try {
      final MessageDigest md = MessageDigest.getInstance("SHA-256");
      final byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (final NoSuchAlgorithmException e) {
      // SHA-256 is a JCA-required algorithm, present on every JDK we run on. If it's not, the
      // safest behaviour is to disable caching for this request rather than cementing a fallback
      // that would conflate distinct tokens.
      throw new IllegalStateException("SHA-256 unavailable; cannot key JWT cache", e);
    }
  }

  /**
   * Caffeine {@link Expiry} that expires each entry at the earlier of {@code maxTtl} or the token's
   * natural {@code exp}. After-read expiration is unchanged so a fresh hit doesn't extend an entry
   * past its token expiry.
   */
  private static final class TokenExpiry implements Expiry<String, Jwt> {

    private final Duration maxTtl;

    TokenExpiry(final Duration maxTtl) {
      this.maxTtl = maxTtl;
    }

    @Override
    public long expireAfterCreate(final String key, final Jwt jwt, final long currentTime) {
      return ttlNanos(jwt);
    }

    @Override
    public long expireAfterUpdate(
        final String key, final Jwt jwt, final long currentTime, final long currentDuration) {
      return ttlNanos(jwt);
    }

    @Override
    public long expireAfterRead(
        final String key, final Jwt jwt, final long currentTime, final long currentDuration) {
      return currentDuration;
    }

    private long ttlNanos(final Jwt jwt) {
      final Instant exp = jwt.getExpiresAt();
      if (exp == null) {
        // Tokens without exp are exotic; treat as if they expire after the configured cap rather
        // than caching them indefinitely.
        return maxTtl.toNanos();
      }
      final Duration untilExp = Duration.between(Instant.now(), exp);
      if (untilExp.isNegative() || untilExp.isZero()) {
        return 0L;
      }
      final Duration effective = untilExp.compareTo(maxTtl) < 0 ? untilExp : maxTtl;
      return effective.toNanos();
    }
  }
}
