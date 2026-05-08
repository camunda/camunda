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
import io.camunda.security.auth.CamundaAuthenticationProvider;
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
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Wraps a delegate {@link CamundaAuthenticationProvider} with a per-token cache so that repeated
 * calls to {@code getCamundaAuthentication()} for the same bearer token short-circuit the full
 * conversion path — the {@code OidcTokenAuthenticationConverter} → {@code TokenClaimsConverter} →
 * {@code DefaultMembershipService} chain that issues 3–4 secondary-storage queries (mappingRules,
 * groups, roles, tenants) per request.
 *
 * <p>The existing per-request {@code RequestContextBasedAuthenticationHolder} caches within a
 * single request, which deduplicates repeated calls inside one controller method but does
 * <em>not</em> help across requests. Bearer clients in production reuse the same access token
 * across many requests, so a cross-request token-keyed cache eliminates the membership-DB roundtrip
 * on cache hits.
 *
 * <p>Cache semantics:
 *
 * <ul>
 *   <li><b>Key:</b> SHA-256 hex digest of the raw bearer token (read from the current {@link
 *       JwtAuthenticationToken}). Bounds memory and avoids retaining the bearer-token bytes on the
 *       heap longer than necessary.
 *   <li><b>Scope:</b> only requests authenticated by a {@link JwtAuthenticationToken} are cached.
 *       Session-based webapp users (handled by {@code HttpSessionBasedAuthenticationHolder}) and
 *       basic-auth users bypass the cache and use the delegate directly.
 *   <li><b>TTL:</b> per entry, clamped to {@code min(configured maxTtl, jwt.exp − now)}. Hits are
 *       never staler than a fresh build would have been at the same instant.
 *   <li><b>Anonymous results:</b> not cached. Authentication failures or anonymous fallbacks should
 *       be retried per request rather than cemented.
 *   <li><b>Concurrency:</b> Caffeine's atomic {@code get(key, loader)} coalesces concurrent misses
 *       on the same token onto a single delegate build.
 * </ul>
 *
 * <p>This wrapper does not change the result of any single call relative to the underlying provider
 * — every cache miss runs the delegate fully (membership lookups, claim conversion, holder set).
 * Only successful, non-anonymous results are cached, and only for as long as the underlying token
 * would remain valid anyway.
 */
public class CachingCamundaAuthenticationProvider implements CamundaAuthenticationProvider {

  private static final Logger LOG =
      LoggerFactory.getLogger(CachingCamundaAuthenticationProvider.class);

  private final CamundaAuthenticationProvider delegate;
  private final Cache<String, CachedEntry> cache;

  public CachingCamundaAuthenticationProvider(
      final CamundaAuthenticationProvider delegate, final long maxSize, final Duration maxTtl) {
    this.delegate = Objects.requireNonNull(delegate, "delegate");
    cache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfter(new TokenExpiry(Objects.requireNonNull(maxTtl, "maxTtl")))
            .build();
    LOG.info(
        "Caching CamundaAuthentication provider enabled: maxSize={} maxTtl={} (delegate {})",
        maxSize,
        maxTtl,
        delegate.getClass().getSimpleName());
  }

  @Override
  public CamundaAuthentication getCamundaAuthentication() {
    final TokenInfo info = currentTokenInfo();
    if (info == null) {
      // Not a bearer-authenticated request — fall through. Session-based users and basic-auth
      // users have their own holder strategies and shouldn't share a token-keyed cache.
      return delegate.getCamundaAuthentication();
    }
    final CachedEntry cached = cache.getIfPresent(info.cacheKey());
    if (cached != null) {
      return cached.authentication();
    }
    final CamundaAuthentication result = delegate.getCamundaAuthentication();
    // Skip caching anonymous / null outcomes — these typically indicate a transient resolution
    // problem and should be retried per request rather than cemented.
    if (result != null && !result.isAnonymous()) {
      cache.put(info.cacheKey(), new CachedEntry(result, info.exp()));
    }
    return result;
  }

  /**
   * Drop every cached entry — useful when group/role/tenant memberships change for tokens that are
   * still cryptographically valid.
   */
  public void invalidateAll() {
    cache.invalidateAll();
  }

  @VisibleForTesting
  long estimatedSize() {
    return cache.estimatedSize();
  }

  private static TokenInfo currentTokenInfo() {
    final Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    if (auth instanceof final JwtAuthenticationToken jwtAuth) {
      final Jwt jwt = jwtAuth.getToken();
      return new TokenInfo(sha256Hex(jwt.getTokenValue()), jwt.getExpiresAt());
    }
    return null;
  }

  private static String sha256Hex(final String token) {
    try {
      final MessageDigest md = MessageDigest.getInstance("SHA-256");
      final byte[] digest = md.digest(token.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(digest);
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 unavailable; cannot key auth cache", e);
    }
  }

  private record TokenInfo(String cacheKey, Instant exp) {}

  private record CachedEntry(CamundaAuthentication authentication, Instant exp) {}

  /**
   * Caffeine {@link Expiry} that expires each entry at the earlier of {@code maxTtl} or the
   * underlying token's {@code exp}. After-read expiration is unchanged so a hit doesn't extend an
   * entry past its token expiry.
   */
  private static final class TokenExpiry implements Expiry<String, CachedEntry> {

    private final Duration maxTtl;

    TokenExpiry(final Duration maxTtl) {
      this.maxTtl = maxTtl;
    }

    @Override
    public long expireAfterCreate(
        final String key, final CachedEntry value, final long currentTime) {
      return ttlNanos(value);
    }

    @Override
    public long expireAfterUpdate(
        final String key,
        final CachedEntry value,
        final long currentTime,
        final long currentDuration) {
      return ttlNanos(value);
    }

    @Override
    public long expireAfterRead(
        final String key,
        final CachedEntry value,
        final long currentTime,
        final long currentDuration) {
      return currentDuration;
    }

    private long ttlNanos(final CachedEntry value) {
      final Instant exp = value.exp();
      if (exp == null) {
        return maxTtl.toNanos();
      }
      final Duration untilExp = Duration.between(Instant.now(), exp);
      if (untilExp.isNegative() || untilExp.isZero()) {
        return 0L;
      }
      return untilExp.compareTo(maxTtl) < 0 ? untilExp.toNanos() : maxTtl.toNanos();
    }
  }
}
