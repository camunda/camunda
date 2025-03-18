/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.engine.impl;

import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.StageableScheduledCommandCache;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.agrona.collections.LongHashSet;

/**
 * A thread safe bounded command cache. It will only cache intent and key pairs for the intents it
 * was originally built with. The underlying cache map is pre-populated with a {@link
 * BoundedCommandCache} per intent. Thus, the bound of the complete cache is sum of the bounds of
 * the mapped caches, i.e. the number of intents times the cache capacity (fixed at 100,000).
 *
 * <p>NOTE: the staged cache return via {@link #stage()} is not thread-safe!
 */
public final class BoundedScheduledCommandCache implements StageableScheduledCommandCache {

  private final Map<Intent, BoundedCommandCache> caches;

  /**
   * Initializes the command cache with a pre-populated map of intent -> bounded cache. You can use
   * this constructor to customize the size of the cache per intent.
   *
   * @param caches immutable cache map
   */
  public BoundedScheduledCommandCache(final Map<Intent, BoundedCommandCache> caches) {
    this.caches = caches;
  }

  /**
   * Returns a bounded cache which will only cache commands for the given intents.
   *
   * @param intents the intents to cache
   * @return a thread-safe command cache
   */
  public static BoundedScheduledCommandCache ofIntent(
      final ScheduledCommandCacheMetrics metrics, final Intent... intents) {
    final Map<Intent, BoundedCommandCache> caches =
        Arrays.stream(intents)
            .collect(
                Collectors.toMap(
                    Function.identity(),
                    intent -> new BoundedCommandCache(metrics.forIntent(intent))));
    return new BoundedScheduledCommandCache(caches);
  }

  @Override
  public void add(final Intent intent, final long key) {
    final var cache = caches.get(intent);
    if (cache != null) {
      final var singleton = new LongHashSet();
      singleton.add(key);
      cache.add(singleton);
    }
  }

  @Override
  public boolean contains(final Intent intent, final long key) {
    final var cache = caches.get(intent);
    return cache != null && cache.contains(key);
  }

  @Override
  public void remove(final Intent intent, final long key) {
    final var cache = caches.get(intent);
    if (cache != null) {
      cache.remove(key);
    }
  }

  @Override
  public void clear() {
    caches.values().forEach(BoundedCommandCache::clear);
  }

  @Override
  public StagedScheduledCommandCache stage() {
    return new StagedCache();
  }

  private final class StagedCache implements StagedScheduledCommandCache {
    private final Map<Intent, LongHashSet> stagedKeys = new HashMap<>();

    @Override
    public void add(final Intent intent, final long key) {
      stagedKeys(intent).add(key);
    }

    @Override
    public boolean contains(final Intent intent, final long key) {
      return stagedKeys(intent).contains(key)
          || (caches.containsKey(intent) && caches.get(intent).contains(key));
    }

    @Override
    public void remove(final Intent intent, final long key) {
      stagedKeys(intent).remove(key);
    }

    @Override
    public void clear() {
      stagedKeys.values().forEach(Set::clear);
    }

    @Override
    public void persist() {
      for (final var entry : stagedKeys.entrySet()) {
        final var cache = caches.get(entry.getKey());
        if (cache != null) {
          cache.add(entry.getValue());
        }
      }
    }

    @Override
    public void rollback() {
      for (final var entry : stagedKeys.entrySet()) {
        final var cache = caches.get(entry.getKey());
        if (cache != null) {
          // TODO: use a batch remove operation to avoid multiple locks
          entry.getValue().forEachLong(cache::remove);
        }
      }
    }

    private LongHashSet stagedKeys(final Intent intent) {
      return stagedKeys.computeIfAbsent(intent, ignored -> new LongHashSet());
    }
  }
}
