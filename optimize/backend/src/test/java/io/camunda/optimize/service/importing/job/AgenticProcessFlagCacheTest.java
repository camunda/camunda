/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.importing.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AgenticProcessFlagCacheTest {

  private AgenticProcessFlagCache cache;

  @BeforeEach
  void setUp() {
    cache = new AgenticProcessFlagCache();
  }

  @Test
  void shouldReturnAllInputWhenCacheIsEmpty() {
    // when
    final Set<String> unflipped = cache.filterUnflipped(List.of("a", "b", "c"));

    // then
    assertThat(unflipped).containsExactlyInAnyOrder("a", "b", "c");
  }

  @Test
  void shouldFilterOutAlreadyFlippedIds() {
    // given
    cache.markFlipped(List.of("a", "b"));

    // when
    final Set<String> unflipped = cache.filterUnflipped(List.of("a", "b", "c", "d"));

    // then — only c and d remain
    assertThat(unflipped).containsExactlyInAnyOrder("c", "d");
  }

  @Test
  void shouldReturnEmptyWhenAllInputIdsAreFlipped() {
    // given
    cache.markFlipped(List.of("a", "b"));

    // when
    final Set<String> unflipped = cache.filterUnflipped(List.of("a", "b"));

    // then
    assertThat(unflipped).isEmpty();
  }

  @Test
  void shouldReturnEmptyForEmptyInput() {
    // when
    final Set<String> unflipped = cache.filterUnflipped(List.of());

    // then
    assertThat(unflipped).isEmpty();
  }

  @Test
  void shouldReturnEmptyForNullInputToFilterUnflipped() {
    // when
    final Set<String> unflipped = cache.filterUnflipped(null);

    // then — no NPE; safe-empty contract
    assertThat(unflipped).isEmpty();
  }

  @Test
  void shouldBeNoOpForNullInputToMarkFlipped() {
    // when — no NPE
    cache.markFlipped(null);

    // then — cache stays empty
    assertThat(cache.filterUnflipped(List.of("a"))).containsExactly("a");
  }

  @Test
  void shouldBeNoOpForEmptyInputToMarkFlipped() {
    // when
    cache.markFlipped(List.of());

    // then
    assertThat(cache.filterUnflipped(List.of("a"))).containsExactly("a");
  }

  @Test
  void shouldStripNullElementsFromMarkFlipped() {
    // when — null element in the collection must not NPE the underlying ConcurrentHashMap
    cache.markFlipped(Arrays.asList("a", null, "b"));

    // then — non-null ids recorded, nulls silently dropped
    assertThat(cache.filterUnflipped(List.of("a", "b", "c"))).containsExactly("c");
  }

  @Test
  void shouldStripNullIdsFromInput() {
    // when
    final Set<String> unflipped = cache.filterUnflipped(Arrays.asList("a", null, "b", null));

    // then — nulls dropped, real ids kept
    assertThat(unflipped).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  void shouldDeduplicateInputViaReturnedSet() {
    // when — same id appears twice in input
    final Set<String> unflipped = cache.filterUnflipped(List.of("a", "a", "b", "b"));

    // then — Set return type collapses duplicates
    assertThat(unflipped).containsExactlyInAnyOrder("a", "b");
  }

  @Test
  void shouldBeIdempotentWhenMarkingSameIdTwice() {
    // given
    cache.markFlipped(List.of("a"));
    cache.markFlipped(List.of("a"));

    // when
    final Set<String> unflipped = cache.filterUnflipped(List.of("a"));

    // then
    assertThat(unflipped).isEmpty();
  }

  @Test
  void shouldAcceptOverlappingMarkFlippedCalls() {
    // given
    cache.markFlipped(List.of("a", "b"));
    cache.markFlipped(List.of("b", "c"));

    // when
    final Set<String> unflipped = cache.filterUnflipped(List.of("a", "b", "c", "d"));

    // then
    assertThat(unflipped).containsExactly("d");
  }

  @Test
  void shouldBeThreadSafeUnderConcurrentMarkFlipped() throws Exception {
    // given — many concurrent writers marking overlapping id ranges
    final int threads = 16;
    final int idsPerThread = 250;
    final ExecutorService pool = Executors.newFixedThreadPool(threads);

    // when
    try {
      IntStream.range(0, threads)
          .forEach(
              t ->
                  pool.submit(
                      () -> {
                        final int base = (t * idsPerThread) / 2; // overlap between threads
                        final List<String> ids =
                            IntStream.range(base, base + idsPerThread)
                                .mapToObj(i -> "id-" + i)
                                .toList();
                        cache.markFlipped(ids);
                      }));
    } finally {
      pool.shutdown();
      assertThat(pool.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
    }

    // then — all unique ids present, no exception thrown
    final int expectedUniqueCount = (threads * idsPerThread) / 2 + idsPerThread / 2;
    final Set<String> probe =
        cache.filterUnflipped(
            IntStream.range(0, expectedUniqueCount).mapToObj(i -> "id-" + i).toList());
    assertThat(probe).as("All marked ids should have been recorded").isEmpty();
  }
}
