/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.cache;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

final class DeduplicationCacheTest {

  private DeduplicationCache<String> cache;

  @BeforeEach
  void setUp() {
    cache = DeduplicationCache.createDefault();
  }

  @Test
  void shouldReturnTrueForFirstOccurrence() {
    // when
    final var result = cache.isFirstOccurrence("key");

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnFalseForSubsequentOccurrences() {
    // given
    cache.isFirstOccurrence("key");

    // when
    final var secondResult = cache.isFirstOccurrence("key");
    final var thirdResult = cache.isFirstOccurrence("key");

    // then
    assertThat(secondResult).isFalse();
    assertThat(thirdResult).isFalse();
  }

  @Test
  void shouldTrackDifferentKeysIndependently() {
    // when
    final var firstKey = cache.isFirstOccurrence("key-1");
    final var secondKey = cache.isFirstOccurrence("key-2");

    // then
    assertThat(firstKey).isTrue();
    assertThat(secondKey).isTrue();
  }

  @Test
  void shouldReturnTrueAgainAfterEntryExpires() throws InterruptedException {
    // given
    final var shortLivedCache = DeduplicationCache.create(100, Duration.ofMillis(100));
    shortLivedCache.isFirstOccurrence("key");

    // when
    Thread.sleep(200);
    final var result = shortLivedCache.isFirstOccurrence("key");

    // then
    assertThat(result).isTrue();
  }

  @Test
  void shouldEvictEntryWhenMaxSizeReached() {
    // given - a cache with size 1
    final var smallCache = DeduplicationCache.create(1, Duration.ofMinutes(30));
    smallCache.isFirstOccurrence("key-1");

    // when - inserting a second entry exceeds the max size; cleanUp() forces synchronous eviction
    smallCache.isFirstOccurrence("key-2");
    smallCache.cleanUp();

    // then - key-1 was evicted and is treated as a first occurrence again
    assertThat(smallCache.isFirstOccurrence("key-1")).isTrue();
  }

  @Test
  void shouldBeThreadSafeAndCountExactlyOneFirstOccurrence() throws Exception {
    // given
    final int threadCount = 50;
    final var latch = new CountDownLatch(1);
    final var trueCount = new AtomicInteger(0);
    final var futures = new ArrayList<Future<?>>();

    try (final var executor = Executors.newFixedThreadPool(threadCount)) {
      for (int i = 0; i < threadCount; i++) {
        futures.add(
            executor.submit(
                () -> {
                  try {
                    latch.await();
                  } catch (final InterruptedException e) {
                    Thread.currentThread().interrupt();
                  }
                  if (cache.isFirstOccurrence("shared-key")) {
                    trueCount.incrementAndGet();
                  }
                }));
      }

      // when - release all threads at once
      latch.countDown();
      for (final var future : futures) {
        future.get(); // propagates exceptions; ensures all threads finish before asserting
      }
    }

    // then - exactly one thread should have seen the first occurrence
    assertThat(trueCount.get()).isEqualTo(1);
  }
}
