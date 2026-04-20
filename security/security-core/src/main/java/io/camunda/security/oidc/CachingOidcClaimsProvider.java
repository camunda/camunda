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
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Caching {@link OidcClaimsProvider} that, when UserInfo augmentation is enabled, calls the OIDC
 * UserInfo endpoint on cache miss, merges its response onto the JWT claims (UserInfo wins on
 * conflict), and caches by {@code jti} (falling back to SHA-256 of the token). Fails closed on
 * UserInfo errors and negatively caches failures to avoid hammering a down IdP.
 */
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
    hitCounter = Counter.builder(CACHE_METRIC).tag("result", "hit").register(meterRegistry);
    missCounter = Counter.builder(CACHE_METRIC).tag("result", "miss").register(meterRegistry);
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
      LOG.debug("UserInfo fetch failed; caching negative entry", e);
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
   * Caffeine {@link Expiry} that expires successful entries at min(configured TTL, token exp − skew
   * − now) and uses a short fixed TTL for failure entries.
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
        final String key,
        final CacheEntry value,
        final long currentTime,
        final long currentDuration) {
      return ttlNanos(value);
    }

    @Override
    public long expireAfterRead(
        final String key,
        final CacheEntry value,
        final long currentTime,
        final long currentDuration) {
      return currentDuration;
    }

    private long ttlNanos(final CacheEntry value) {
      if (value.failure() != null) {
        return OidcUserInfoAugmentationConfiguration.NEGATIVE_CACHE_TTL.toNanos();
      }
      final Duration untilExp = Duration.between(Instant.now(), value.tokenExp()).minus(clockSkew);
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
