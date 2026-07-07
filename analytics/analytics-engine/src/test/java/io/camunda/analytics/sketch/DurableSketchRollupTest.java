/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.sketch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.eventbridge.streaming.aggregate.ResultSink;
import io.camunda.eventbridge.streaming.aggregate.SegmentMergingAggregation;
import io.camunda.eventbridge.streaming.aggregate.StringRecordValue;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.zeebe.db.impl.DbBytes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.datasketches.kll.KllDoublesSketch;
import org.apache.datasketches.quantilescommon.QuantileSearchCriteria;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises a sketch-backed metric ({@link QuantileAggregateFunction}) through the RocksDB-backed
 * {@link SegmentMergingAggregation} — the live Stage-2 reduce operator — so the sketch record
 * flyweight ({@link KllDoublesSketchValue}) is validated on the real durable path: a segment delta
 * is decoded through the merge-only wrap ({@code fromBytesForMerge}), folded into the owned running
 * total, encoded into the durable cell at the checkpoint, and decoded again on recovery after a
 * restart.
 */
final class DurableSketchRollupTest {

  private static final long HOUR = 3_600_000L;
  private static final long LATENESS = 60_000L;

  private static final QuantileAggregateFunction<Double> QUANTILE =
      new QuantileAggregateFunction<>(d -> d);

  /**
   * One codec flyweight for both the delta decode and the merger's durable cells, as the stage
   * shares one per meter group.
   */
  private static final KllDoublesSketchValue CODEC = new KllDoublesSketchValue();

  @TempDir private Path dataDir;
  private RocksDbStateStoreProvider<TestColumnFamilies> provider;

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
  }

  private SegmentMergingAggregation<String, KllDoublesSketch> newMerger(final MapSink sink) {
    final KeyValueStore<DbBytes, DbBytes> cells =
        provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
    return new SegmentMergingAggregation<>(
        1,
        QUANTILE,
        TumblingWindows.ofSizeAndGrace(HOUR, LATENESS),
        sink,
        cells,
        new StringRecordValue(),
        CODEC,
        provider::runInTransaction);
  }

  /** A sealed segment's from-empty delta over the observations {@code from..to}, as bytes. */
  private static byte[] deltaOf(final int from, final int to) {
    KllDoublesSketch acc = QUANTILE.createAccumulator();
    for (int i = from; i <= to; i++) {
      acc = QUANTILE.add((double) i, acc);
    }
    return CODEC.toBytes(acc);
  }

  private static double median(final KllDoublesSketch sketch) {
    return sketch.getQuantile(0.5, QuantileSearchCriteria.INCLUSIVE);
  }

  @Test
  void shouldFoldReadOnlySketchDeltasIntoTheRunningTotal() {
    // given
    final MapSink sink = new MapSink();
    final SegmentMergingAggregation<String, KllDoublesSketch> merger = newMerger(sink);
    final Windowed<String> cell = new Windowed<>("EU", 0L);

    // when — two sealed segments' deltas arrive serialized and are decoded through the merge-only
    // wrap (a read-only sketch aliasing the bytes, the production decode), then folded into the
    // cell's running total the merger owns
    merger.merge(cell, CODEC.fromBytesForMerge(deltaOf(1, 50)));
    merger.flush();
    merger.merge(cell, CODEC.fromBytesForMerge(deltaOf(51, 100)));
    merger.flush();

    // then — the served sketch spans all 100 observations, not just the last delta
    final KllDoublesSketch stored = sink.get(cell).orElseThrow();
    assertThat(stored.getN()).isEqualTo(100L);
    assertThat(stored.getMinItem()).isEqualTo(1.0);
    assertThat(stored.getMaxItem()).isEqualTo(100.0);
    assertThat(median(stored)).isCloseTo(50.0, within(3.0));
  }

  @Test
  void shouldRecoverSketchAcrossRestart() throws Exception {
    // given — the low half folded and checkpointed (the running sketch is encoded into the durable
    // RocksDB cell), then the store is closed (crash/restart)
    final Windowed<String> cell = new Windowed<>("EU", 0L);
    final SegmentMergingAggregation<String, KllDoublesSketch> before = newMerger(new MapSink());
    before.merge(cell, CODEC.fromBytesForMerge(deltaOf(1, 50)));
    before.flush();
    before.checkpoint();
    provider.close();

    // when — the store is reopened and a fresh merger recovers (full decode of the stored cell),
    // then folds the high half onto the recovered sketch
    open();
    final MapSink sink = new MapSink();
    final SegmentMergingAggregation<String, KllDoublesSketch> recovered = newMerger(sink);
    recovered.merge(cell, CODEC.fromBytesForMerge(deltaOf(51, 100)));
    recovered.flush();

    // then — the recovered sketch merged onto the persisted base (all 100 observations)
    final KllDoublesSketch stored = sink.get(cell).orElseThrow();
    assertThat(stored.getN()).isEqualTo(100L);
    assertThat(median(stored)).isCloseTo(50.0, within(3.0));
  }

  @Test
  void shouldReencodeTheRecoveredSketchAcrossASecondRestart() throws Exception {
    // given — the low half checkpointed, a restart, then the high half folded onto the recovered
    // sketch and checkpointed again: decode -> merge -> re-encode of the durable cell
    final Windowed<String> cell = new Windowed<>("EU", 0L);
    final SegmentMergingAggregation<String, KllDoublesSketch> first = newMerger(new MapSink());
    first.merge(cell, CODEC.fromBytesForMerge(deltaOf(1, 50)));
    first.checkpoint();
    provider.close();
    open();
    final SegmentMergingAggregation<String, KllDoublesSketch> second = newMerger(new MapSink());
    second.merge(cell, CODEC.fromBytesForMerge(deltaOf(51, 100)));
    second.checkpoint();
    provider.close();

    // when — a second restart recovers the re-encoded cell and a delta for a much later window
    // advances the watermark past the cell's window end, finalizing it
    open();
    final MapSink sink = new MapSink();
    final SegmentMergingAggregation<String, KllDoublesSketch> third = newMerger(sink);
    third.merge(new Windowed<>("EU", 10 * HOUR), CODEC.fromBytesForMerge(deltaOf(1, 1)));
    third.checkpoint();

    // then — the finalized value is the complete 100-observation sketch, proving the checkpoint
    // re-encoded the recovered-and-merged sketch losslessly
    final KllDoublesSketch stored = sink.get(cell).orElseThrow();
    assertThat(stored.getN()).isEqualTo(100L);
    assertThat(stored.getMinItem()).isEqualTo(1.0);
    assertThat(stored.getMaxItem()).isEqualTo(100.0);
    assertThat(median(stored)).isCloseTo(50.0, within(3.0));
  }

  /** A heap-backed idempotent sink (overwrite by key) capturing the served sketches. */
  private static final class MapSink implements ResultSink<Windowed<String>, KllDoublesSketch> {

    private final Map<Windowed<String>, KllDoublesSketch> values = new HashMap<>();

    @Override
    public void upsert(final Windowed<String> key, final KllDoublesSketch value) {
      values.put(key, value);
    }

    Optional<KllDoublesSketch> get(final Windowed<String> key) {
      return Optional.ofNullable(values.get(key));
    }
  }
}
