/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.analytics.streaming.state.TestColumnFamilies;
import io.camunda.analytics.streaming.state.api.KeyValueStore;
import io.camunda.analytics.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.analytics.streaming.window.TumblingWindows;
import io.camunda.analytics.streaming.window.Windowed;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.apache.datasketches.kll.KllDoublesSketch;
import org.apache.datasketches.quantilescommon.QuantileSearchCriteria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises a sketch-backed metric ({@link QuantileAggregateFunction}) through the RocksDB {@link
 * DurableMaterializedRollup}, so the sketch {@link Codec} is validated on the real durable path:
 * decode the stored cell, merge the batch partial, re-encode — across flushes and across a restart.
 */
final class DurableSketchRollupTest {

  private static final long HOUR = 3_600_000L;
  private static final long LATENESS = 60_000L;

  private record Duration(String region, long ms, long timestamp, int partition, long position) {}

  private static final QuantileAggregateFunction<Duration> QUANTILE =
      new QuantileAggregateFunction<>(d -> (double) d.ms());

  private static final SourceCoordinate<Duration> COORD =
      new SourceCoordinate<>() {
        @Override
        public int partition(final Duration d) {
          return d.partition();
        }

        @Override
        public long position(final Duration d) {
          return d.position();
        }
      };

  @TempDir private Path dataDir;
  private RocksDbStateStoreProvider<TestColumnFamilies> provider;
  private InMemoryResultSink<Windowed<String>, KllDoublesSketch> sink;

  @BeforeEach
  void setUp() {
    open();
  }

  @AfterEach
  void tearDown() throws Exception {
    provider.close();
  }

  private void open() {
    provider = RocksDbStateStoreProvider.open(dataDir.toFile(), new SimpleMeterRegistry());
    sink = new InMemoryResultSink<>();
  }

  private DurableMaterializedRollup<Duration, String, KllDoublesSketch> newRollup() {
    final KeyValueStore<DbBytes, DbBytes> cells =
        provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(TestColumnFamilies.OFFSETS, new DbInt(), new DbLong());
    return new DurableMaterializedRollup<>(
        QUANTILE,
        Duration::region,
        Duration::timestamp,
        COORD,
        TumblingWindows.of(HOUR),
        LATENESS,
        sink,
        cells,
        offsets,
        new StringCodec(),
        new KllDoublesSketchCodec(),
        provider::runInTransaction);
  }

  private static double median(final KllDoublesSketch sketch) {
    return sketch.getQuantile(0.5, QuantileSearchCriteria.INCLUSIVE);
  }

  @Test
  void shouldMergeSketchAcrossFlushes() {
    // given
    final var rollup = newRollup();

    // when — the low half is folded and flushed, then the high half folded and flushed into the
    // same window/region cell (forcing decode -> merge -> encode of the durable sketch)
    for (int i = 1; i <= 50; i++) {
      rollup.accept(new Duration("EU", i, 1_000L, 1, i));
    }
    rollup.flush();
    for (int i = 51; i <= 100; i++) {
      rollup.accept(new Duration("EU", i, 2_000L, 1, i));
    }
    rollup.flush();

    // then — the durable sketch spans all 100 observations, not just the last batch
    final KllDoublesSketch stored = sink.get(new Windowed<>("EU", 0L)).orElseThrow();
    assertThat(stored.getN()).isEqualTo(100L);
    assertThat(stored.getMinItem()).isEqualTo(1.0);
    assertThat(stored.getMaxItem()).isEqualTo(100.0);
    assertThat(median(stored)).isCloseTo(50.0, within(3.0));
  }

  @Test
  void shouldRecoverSketchAcrossRestart() throws Exception {
    // given — 50 observations flushed, then the store is closed (crash/restart)
    final var before = newRollup();
    for (int i = 1; i <= 50; i++) {
      before.accept(new Duration("EU", i, 1_000L, 1, i));
    }
    before.flush();
    provider.close();

    // when — the store is reopened and a fresh rollup recovers, then folds 50 more
    open();
    final var recovered = newRollup();
    for (int i = 51; i <= 100; i++) {
      recovered.accept(new Duration("EU", i, 2_000L, 1, i));
    }
    recovered.flush();

    // then — the recovered sketch merged onto the persisted base (all 100 observations)
    final KllDoublesSketch stored = sink.get(new Windowed<>("EU", 0L)).orElseThrow();
    assertThat(stored.getN()).isEqualTo(100L);
    assertThat(median(stored)).isCloseTo(50.0, within(3.0));
  }
}
