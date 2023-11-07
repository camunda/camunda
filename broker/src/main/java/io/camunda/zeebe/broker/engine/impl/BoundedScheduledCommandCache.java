/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.engine.impl;

import io.camunda.zeebe.protocol.record.intent.Intent;
import io.camunda.zeebe.stream.api.scheduling.ScheduledCommandCache.StageableScheduledCommandCache;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.agrona.collections.LongHashSet;

public final class BoundedScheduledCommandCache implements StageableScheduledCommandCache {
  private final Map<Intent, BoundedCommandCache> caches;

  private BoundedScheduledCommandCache(final Map<Intent, BoundedCommandCache> caches) {
    this.caches = caches;
  }

  public static BoundedScheduledCommandCache ofIntent(final Intent... intents) {
    final Map<Intent, BoundedCommandCache> caches =
        Arrays.stream(intents)
            .collect(Collectors.toMap(Function.identity(), ignored -> new BoundedCommandCache()));
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
  public boolean isCached(final Intent intent, final long key) {
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
    public boolean isCached(final Intent intent, final long key) {
      return stagedKeys(intent).contains(key)
          || (caches.containsKey(intent) && caches.get(intent).contains(key));
    }

    @Override
    public void remove(final Intent intent, final long key) {
      if (!stagedKeys(intent).remove(key)) {
        final var cache = caches.get(intent);
        if (cache != null) {
          cache.remove(key);
        }
      }
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

    private LongHashSet stagedKeys(final Intent intent) {
      return stagedKeys.computeIfAbsent(intent, ignored -> new LongHashSet());
    }
  }
}
