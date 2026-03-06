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
public final class LogDeduplicationCache {

  private final Cache<String, Boolean> cache;

  private LogDeduplicationCache(final Cache<String, Boolean> cache) {
    this.cache = cache;
  }

  /**
   * Creates a new deduplication cache with the specified maximum size and expiration duration.
   *
   * @param maxSize the maximum number of entries before LRU eviction occurs
   * @param expireAfter the duration after which entries are automatically evicted
   * @return a new deduplication cache
   */
  public static LogDeduplicationCache create(final int maxSize, final Duration expireAfter) {
    final Cache<String, Boolean> caffeine =
        Caffeine.newBuilder()
            .maximumSize(maxSize)
            .expireAfterWrite(expireAfter)
            .<String, Boolean>build();
    return new LogDeduplicationCache(caffeine);
  }

  /**
   * Checks if the given key has been seen before. If not, it marks the key as seen and returns
   * true. If the key has been seen before, it returns false.
   *
   * @param key the key to check
   * @return true if this is the first time the key is seen, false otherwise
   */
  public boolean markIfFirstSeen(final String key) {
    if (cache.getIfPresent(key) == null) {
      cache.put(key, Boolean.TRUE);
      return true;
    }
    return false;
  }
}
