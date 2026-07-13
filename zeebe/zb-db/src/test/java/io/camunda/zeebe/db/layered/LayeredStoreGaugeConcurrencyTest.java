/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.layered.LayeredStoreCoordinator.PersistRound;
import io.camunda.zeebe.db.layered.util.InMemoryDurableState;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * The metrics contract of the store and coordinator gauges: a scrape thread may poll every gauge at
 * any time while the owner thread mutates the store, and a poll never throws — the gauges read only
 * volatile stat mirrors, never the owner-mutable structures (pipeline list, clean cache, overlays).
 */
final class LayeredStoreGaugeConcurrencyTest {

  private static final String STORE = "store-a";
  private static final Duration JOIN_TIMEOUT = Duration.ofSeconds(30);

  @Test
  void shouldNeverThrowWhenGaugesArePolledWhileOwnerMutates() throws Exception {
    // given a fully instrumented store and coordinator (all gauges registered)
    final var registry = new SimpleMeterRegistry();
    final LayeredStoreMetrics metrics = LayeredStoreMetrics.of(registry, "hammer");
    final InMemoryDurableState state = new InMemoryDurableState();
    // a segment limit of 1 forces a pipeline merge on nearly every freeze, a small byte budget
    // keeps the clean cache evicting — both hammer the exact structures the gauges must not walk
    final LayeredKeyValueStore store =
        new LayeredKeyValueStore(STORE, state.store(STORE), 32 * 1024, true, 1, metrics);
    // the stores list must stay strongly reachable for the whole test: micrometer gauges hold it
    // only weakly (it is referenced again after the hammer loop below)
    final List<LayeredKeyValueStore> stores = List.of(store);
    final LayeredStoreCoordinator coordinator =
        new LayeredStoreCoordinator(
            stores, state.sink(), state.snapshotSource(), view -> {}, metrics);
    metrics.registerStoreGauges(stores);
    final List<Gauge> gauges =
        registry.getMeters().stream()
            .filter(Gauge.class::isInstance)
            .map(Gauge.class::cast)
            .toList();
    assertThat(gauges).isNotEmpty();

    // when one thread polls every gauge in a tight loop while the owner thread freezes, merges,
    // persists, drops drained segments and evicts (bounded iterations, no sleeps)
    final AtomicBoolean ownerDone = new AtomicBoolean();
    final AtomicReference<Throwable> scrapeFailure = new AtomicReference<>();
    final Thread scraper =
        new Thread(
            () -> {
              try {
                while (!ownerDone.get()) {
                  pollAll(gauges);
                }
                pollAll(gauges); // one final pass over the settled state
              } catch (final Throwable t) {
                scrapeFailure.set(t);
              }
            },
            "gauge-scraper");
    scraper.start();
    try {
      final Random random = new Random(0xC0FFEE);
      for (int i = 1; i <= 5_000 && scrapeFailure.get() == null; i++) {
        // a small key space so puts, deletes, read-through caching and tombstones all collide
        for (int write = 0; write < 4; write++) {
          final byte[] key = key(random.nextInt(64));
          if (random.nextBoolean()) {
            store.put(key, value(random.nextInt(1024)));
          } else {
            store.delete(key);
          }
        }
        store.get(key(random.nextInt(64)));
        store.promote();
        coordinator.freezeAll(i);
        if (i % 3 == 0) {
          final PersistRound round = coordinator.prepareRound(i);
          boolean success = true;
          try {
            round.persist();
          } catch (final Exception e) {
            success = false;
          }
          coordinator.completeRound(round, success);
        }
      }
    } finally {
      ownerDone.set(true);
      scraper.join(JOIN_TIMEOUT.toMillis());
    }

    // then no poll ever threw
    assertThat(scraper.isAlive()).describedAs("the scraper thread must terminate").isFalse();
    assertThat(scrapeFailure.get()).describedAs("polling a gauge must never throw").isNull();
    assertThat(stores).hasSize(1); // keeps the weakly-referenced gauge targets reachable
    coordinator.close();
  }

  /** Polls every gauge; values may be momentarily stale mid-mutation, but a poll never throws. */
  private static void pollAll(final List<Gauge> gauges) {
    for (final Gauge gauge : gauges) {
      gauge.value();
    }
  }

  private static byte[] key(final int index) {
    return new byte[] {(byte) (index >> 8), (byte) index};
  }

  private static byte[] value(final int size) {
    return new byte[size];
  }
}
