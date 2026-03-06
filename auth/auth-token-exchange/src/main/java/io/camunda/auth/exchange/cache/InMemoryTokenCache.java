/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.auth.exchange.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.auth.domain.model.TokenExchangeResponse;
import io.camunda.auth.domain.port.outbound.TokenCachePort;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * In-memory implementation of {@link TokenCachePort} backed by a Caffeine cache. Entries are evicted
 * based on the minimum of the configured TTL and the token's remaining lifetime (minus a safety
 * buffer).
 */
public class InMemoryTokenCache implements TokenCachePort {

  private static final Logger LOG = LoggerFactory.getLogger(InMemoryTokenCache.class);

  private static final int DEFAULT_MAX_SIZE = 1000;
  private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);
  private static final long DEFAULT_EVICTION_BUFFER_SECONDS = 60;

  private final Cache<String, TokenExchangeResponse> cache;
  private final Duration ttl;
  private final long evictionBufferSeconds;

  public InMemoryTokenCache() {
    this(DEFAULT_MAX_SIZE, DEFAULT_TTL, DEFAULT_EVICTION_BUFFER_SECONDS);
  }

  public InMemoryTokenCache(final int maxSize, final Duration ttl) {
    this(maxSize, ttl, DEFAULT_EVICTION_BUFFER_SECONDS);
  }

  public InMemoryTokenCache(
      final int maxSize, final Duration ttl, final long evictionBufferSeconds) {
    this.ttl = ttl;
    this.evictionBufferSeconds = evictionBufferSeconds;
    this.cache =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(ttl)
            .build();
    LOG.debug(
        "Initialized InMemoryTokenCache with maxSize={}, ttl={}, evictionBufferSeconds={}",
        maxSize,
        ttl,
        evictionBufferSeconds);
  }

  @Override
  public Optional<TokenExchangeResponse> get(final String cacheKey) {
    final TokenExchangeResponse response = cache.getIfPresent(cacheKey);
    if (response != null) {
      LOG.debug("Cache hit for key={}", cacheKey);
      return Optional.of(response);
    }
    LOG.debug("Cache miss for key={}", cacheKey);
    return Optional.empty();
  }

  @Override
  public void put(final String cacheKey, final TokenExchangeResponse response) {
    final Duration effectiveTtl = computeEffectiveTtl(response);
    if (effectiveTtl.isNegative() || effectiveTtl.isZero()) {
      LOG.debug(
          "Skipping cache put for key={}: token already expired or within eviction buffer",
          cacheKey);
      return;
    }
    cache.put(cacheKey, response);
    LOG.debug("Cached token for key={} with effectiveTtl={}", cacheKey, effectiveTtl);
  }

  @Override
  public void evictBySubject(final String subjectId) {
    // TODO: Caffeine does not support secondary index lookups efficiently.
    //  As a conservative approach, evict all entries. A future improvement could
    //  maintain a secondary index (subject -> Set<cacheKey>) if selective eviction
    //  becomes a performance concern.
    LOG.debug("Evicting all cache entries for subject={} (full invalidation)", subjectId);
    cache.invalidateAll();
  }

  @Override
  public void evictAll() {
    LOG.debug("Evicting all cache entries");
    cache.invalidateAll();
  }

  /**
   * Computes the effective TTL as the minimum of the configured TTL and the token's remaining
   * lifetime minus the eviction buffer.
   */
  private Duration computeEffectiveTtl(final TokenExchangeResponse response) {
    final Instant tokenExpiry = response.issuedAt().plusSeconds(response.expiresIn());
    final Duration remainingLifetime =
        Duration.between(Instant.now(), tokenExpiry).minusSeconds(evictionBufferSeconds);
    return ttl.compareTo(remainingLifetime) < 0 ? ttl : remainingLifetime;
  }
}
