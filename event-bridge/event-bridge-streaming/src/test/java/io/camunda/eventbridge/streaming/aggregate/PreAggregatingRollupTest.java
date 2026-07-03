/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

final class PreAggregatingRollupTest {

  private record Sale(String region, long amount) {}

  private static final AggregateFunction<Sale, Long, Long> SUM =
      new AggregateFunction<>() {
        @Override
        public Long createAccumulator() {
          return 0L;
        }

        @Override
        public Long add(final Sale sale, final Long acc) {
          return acc + sale.amount();
        }

        @Override
        public Long merge(final Long a, final Long b) {
          return a + b;
        }

        @Override
        public Long getResult(final Long acc) {
          return acc;
        }
      };

  @Test
  void shouldCoalesceFactsForSameKeyAndFlushOnDemand() {
    // given
    final InMemoryRollupStore<String, Long> store = new InMemoryRollupStore<>(SUM);
    final var rollup = new PreAggregatingRollup<>(SUM, Sale::region, store);

    // when — two EU sales buffered (no per-fact store write), then an explicit flush
    rollup.accept(new Sale("EU", 100));
    rollup.accept(new Sale("EU", 50));
    assertThat(store.get("EU")).isEmpty(); // still buffered
    rollup.flush();

    // then — coalesced into one merged partial
    assertThat(store.get("EU")).contains(150L);
  }

  @Test
  void shouldBufferAllKeysUntilFlush() {
    // given — no per-key-count flush: facts stay buffered until the commit-tick flush
    final InMemoryRollupStore<String, Long> store = new InMemoryRollupStore<>(SUM);
    final var rollup = new PreAggregatingRollup<>(SUM, Sale::region, store);

    // when — two distinct keys buffered, no explicit flush yet
    rollup.accept(new Sale("EU", 100));
    rollup.accept(new Sale("US", 70));

    // then — nothing written until flush
    assertThat(store.get("EU")).isEmpty();
    assertThat(store.get("US")).isEmpty();

    // when — the commit tick flushes
    rollup.flush();

    // then — both drained together
    assertThat(store.get("EU")).contains(100L);
    assertThat(store.get("US")).contains(70L);
  }

  @Test
  void shouldFlushRemainderOnClose() {
    // given
    final InMemoryRollupStore<String, Long> store = new InMemoryRollupStore<>(SUM);
    final var rollup = new PreAggregatingRollup<>(SUM, Sale::region, store);
    rollup.accept(new Sale("EU", 100));

    // when
    rollup.close();

    // then
    assertThat(store.get("EU")).contains(100L);
  }
}
