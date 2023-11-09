/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.engine.impl;

import io.camunda.zeebe.util.LockUtil;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.agrona.collections.LongHashSet;

/**
 * A thread-safe, bounded command cache with light memory footprint, by storing keys in an
 * underlying {@link LongHashSet}. You can roughly estimate the memory usage of the set as the
 * capacity times 8 bytes (i.e. size of long).
 *
 * <p>Thread-safety is guaranteed via naive locking, to be optimized if need be.
 *
 * <p>The set is bounded by performing random eviction to avoid going over capacity. Whenever new
 * keys are added, we calculate how many should be evicted beforehand, then randomly remove this
 * amount (since a set has no deterministic ordering).
 */
public final class BoundedCommandCache {
  private static final int DEFAULT_CAPACITY = 100_000;

  private final Lock lock = new ReentrantLock();

  private final int capacity;
  private final LongHashSet cache;

  public BoundedCommandCache() {
    this(DEFAULT_CAPACITY);
  }

  /**
   * You can estimate the size based on the capacity as followed. Since we use a {@link LongHashSet}
   * primitives, each element takes about 8 bytes. There is some minimal overhead for state
   * management and the likes, which means in the end, amortized, each entry takes about 8.4 bytes.
   *
   * <p>So the default capacity, 100,000 entries, will use about 840KB of memory, even when full.
   *
   * @param capacity the maximum capacity of the command cache
   */
  public BoundedCommandCache(final int capacity) {
    this.capacity = capacity;

    // to avoid resizing, we set a load factor of 0.9, and increase the internal capacity
    // preemptively
    final var resizeThreshold = (int) Math.ceil(capacity * 0.9f);
    final var capacityToPreventResize = 2 * capacity - resizeThreshold;
    cache = new LongHashSet(capacityToPreventResize, 0.9f, true);
  }

  public void add(final LongHashSet keys) {
    LockUtil.withLock(lock, () -> lockedAdd(keys));
  }

  public boolean contains(final long key) {
    return LockUtil.withLock(lock, () -> cache.contains(key));
  }

  public void remove(final long key) {
    LockUtil.withLock(lock, (Runnable) () -> cache.remove(key));
  }

  public int size() {
    return LockUtil.withLock(lock, cache::size);
  }

  private void lockedAdd(final LongHashSet keys) {
    final int evictionCount = cache.size() + keys.size() - capacity;
    if (evictionCount > 0) {
      evict(evictionCount);
    }

    cache.addAll(keys);
  }

  private void evict(final int count) {
    final var evictionStartIndex = ThreadLocalRandom.current().nextInt(0, capacity - count + 1);
    final int evictionEndIndex = evictionStartIndex + count;
    final var iterator = cache.iterator();

    for (int i = 0; i < evictionEndIndex && iterator.hasNext(); i++) {
      iterator.next();

      if (i >= evictionStartIndex) {
        iterator.remove();
      }
    }
  }
}
