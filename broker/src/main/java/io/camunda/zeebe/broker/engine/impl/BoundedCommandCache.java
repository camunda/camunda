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

final class BoundedCommandCache {
  private static final int DEFAULT_CAPACITY = 100_000;

  private final Lock lock = new ReentrantLock();

  private final int capacity;
  private final LongHashSet cache;

  BoundedCommandCache() {
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
  BoundedCommandCache(final int capacity) {
    this.capacity = capacity;

    // to avoid resizing, we set a load factor of 0.9, and increase the internal capacity
    // preemptively
    final var resizeThreshold = (int) Math.ceil(capacity * 0.9f);
    final var capacityToPreventResize = 2 * capacity - resizeThreshold;
    cache = new LongHashSet(capacityToPreventResize, 0.9f, true);
  }

  void add(final LongHashSet keys) {
    LockUtil.withLock(lock, () -> lockedAdd(keys));
  }

  boolean contains(final long key) {
    return LockUtil.withLock(lock, () -> cache.contains(key));
  }

  void remove(final long key) {
    LockUtil.withLock(lock, (Runnable) () -> cache.remove(key));
  }

  private void lockedAdd(final LongHashSet keys) {
    final int evictionCount = cache.size() + keys.size() - capacity;
    if (evictionCount > 0) {
      evict(evictionCount);
    }

    cache.addAll(keys);
  }

  private void evict(final int count) {
    final var evictionStartIndex = ThreadLocalRandom.current().nextInt(0, capacity - count);
    final int evictionEndIndex = evictionStartIndex + count;
    final var iterator = cache.iterator();

    for (int i = 0; i < evictionEndIndex && iterator.hasNext(); i++, iterator.next()) {
      if (i >= evictionStartIndex) {
        iterator.remove();
      }
    }
  }
}
