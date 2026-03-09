/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;

/**
 * A simple bounded cache for deduplication purposes. This cache is designed to track whether a
 * specific key has been seen before, using a combination of size-based and time-based eviction.
 *
 * <p>The cache uses:
 *
 * <ul>
 *   <li>LRU eviction when the maximum size is reached
 *   <li>Time-based eviction after the specified duration
 * </ul>
 */
public final class DeduplicationCache {

  private static final int DEFAULT_MAP_ENTRIES = 10_000;
  private static final Duration DEFAULT_EVICTION_DURATION = Duration.ofMinutes(30);
  private static final Object PRESENT = new Object();

  private final Cache<String, Object> cache;

  private DeduplicationCache(final Cache<String, Object> cache) {
    this.cache = cache;
  }

  public static DeduplicationCache create(final int maxSize, final Duration expireAfter) {
    final Cache<String, Object> caffeine =
        Caffeine.newBuilder().maximumSize(maxSize).expireAfterWrite(expireAfter).build();
    return new DeduplicationCache(caffeine);
  }

  /**
   * Creates a new deduplication cache with the default maximum size and expiration duration.
   *
   * @return a new deduplication cache
   */
  public static DeduplicationCache createDefault() {
    return create(DEFAULT_MAP_ENTRIES, DEFAULT_EVICTION_DURATION);
  }

  /**
   * Checks whether the given key is seen for the first time in the cache.
   *
   * <p>Uses a single atomic {@code putIfAbsent} on the underlying {@code asMap()} view, which makes
   * the operation race-free under concurrent access.
   *
   * @param key the deduplication key
   * @return {@code true} if the key was not present and is now recorded as seen, {@code false} if
   *     the key was already present
   */
  public boolean isFirstOccurrence(final String key) {
    return cache.asMap().putIfAbsent(key, PRESENT) == null;
  }

  /**
   * Performs any pending maintenance operations needed by the cache, including eviction of expired
   * or excess entries. This method is primarily useful in tests to force synchronous eviction,
   * since Caffeine performs eviction asynchronously by default.
   */
  public void cleanUp() {
    cache.cleanUp();
  }
}
