/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.shuffle;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.eventbridge.streaming.aggregate.AggregateFunction;
import io.camunda.eventbridge.streaming.aggregate.InMemoryResultSink;
import io.camunda.eventbridge.streaming.aggregate.LongCodec;
import io.camunda.eventbridge.streaming.aggregate.StringCodec;
import io.camunda.eventbridge.streaming.aggregate.SumAggregateFunction;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.eventbridge.streaming.window.TumblingWindows;
import io.camunda.eventbridge.streaming.window.Windowed;
import io.camunda.zeebe.db.impl.DbBytes;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MergingRollupTest {

  private static final long HOUR = 3_600_000L;
  private static final long LATENESS = 60_000L;

  private static final AggregateFunction<Object, Long, Long> SUM =
      new SumAggregateFunction<>(o -> 0L);
  private static final StringCodec KEY_CODEC = new StringCodec();
  private static final LongCodec ACC_CODEC = new LongCodec();

  @TempDir private Path dataDir;
  private RocksDbStateStoreProvider<TestColumnFamilies> provider;
  private InMemoryResultSink<Windowed<String>, Long> sink;

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

  private MergingRollup<String, Long> newMerger(
      final int aggId, final InMemoryResultSink<Windowed<String>, Long> resultSink) {
    final KeyValueStore<DbBytes, DbBytes> slots =
        provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
    return new MergingRollup<>(
        aggId,
        SUM,
        TumblingWindows.ofSizeAndGrace(HOUR, LATENESS),
        resultSink,
        slots,
        KEY_CODEC,
        ACC_CODEC,
        provider::runInTransaction);
  }

  private static Partial partial(
      final int aggId, final String key, final long window, final int writer, final long value) {
    return new Partial(aggId, KEY_CODEC.encode(key), window, writer, ACC_CODEC.encode(value));
  }

  @Test
  void shouldMergeAcrossWriters() {
    // given — two source partitions contribute partials for the same cell
    final var merger = newMerger(1, sink);

    // when
    merger.accept(partial(1, "EU", 0L, 1, 100L));
    merger.accept(partial(1, "EU", 0L, 2, 50L));
    merger.flush();

    // then — the served value is the merge across writers
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(150L);
  }

  @Test
  void shouldOverwriteWriterSlotIdempotently() {
    // given
    final var merger = newMerger(1, sink);
    merger.accept(partial(1, "EU", 0L, 1, 100L));
    merger.flush();
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(100L);

    // when — the same writer re-sends the same partial (a duplicate) and later a newer one
    merger.accept(partial(1, "EU", 0L, 1, 100L)); // duplicate — overwrite, not add
    merger.flush();
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(100L); // still 100, not 200

    merger.accept(partial(1, "EU", 0L, 1, 120L)); // newer cumulative for the same writer
    merger.flush();

    // then — overwrite wins, not accumulate
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(120L);
  }

  @Test
  void shouldRecoverSlotsAcrossReopen() throws Exception {
    // given — two writers checkpointed, then the store closed
    final var merger = newMerger(1, sink);
    merger.accept(partial(1, "EU", 0L, 1, 100L));
    merger.accept(partial(1, "EU", 0L, 2, 50L));
    merger.checkpoint();
    provider.close();

    // when — reopened and a fresh merger recovers the slots
    open();
    final var recovered = newMerger(1, sink);
    recovered.accept(partial(1, "EU", 0L, 3, 10L)); // a third writer joins
    recovered.flush();

    // then — recovered 100 + 50 plus the new 10
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(160L);
  }

  @Test
  void shouldIsolateRollupsByAggIdInOneStore() throws Exception {
    // given — two mergers with distinct aggIds share the slot store, each with its own sink
    final InMemoryResultSink<Windowed<String>, Long> sinkOne = new InMemoryResultSink<>();
    final InMemoryResultSink<Windowed<String>, Long> sinkTwo = new InMemoryResultSink<>();
    final var first = newMerger(1, sinkOne);
    final var second = newMerger(2, sinkTwo);

    // when — same cell, different values, then checkpoint + restart
    first.accept(partial(1, "EU", 0L, 1, 100L));
    second.accept(partial(2, "EU", 0L, 1, 7L));
    first.checkpoint();
    second.checkpoint();
    provider.close();

    // a fresh merger per aggId recovers its own slots, then a new writer triggers a re-emit
    open();
    final var recoveredFirst = newMerger(1, sinkOne);
    recoveredFirst.accept(partial(1, "EU", 0L, 2, 5L));
    recoveredFirst.flush();
    final var recoveredSecond = newMerger(2, sinkTwo);
    recoveredSecond.accept(partial(2, "EU", 0L, 2, 3L));
    recoveredSecond.flush();

    // then — each recovered only its own slots from the shared store (100+5, 7+3), not mixed
    assertThat(sinkOne.get(new Windowed<>("EU", 0L))).contains(105L);
    assertThat(sinkTwo.get(new Windowed<>("EU", 0L))).contains(10L);
  }

  @Test
  void shouldFinalizeAndEvictClosedWindows() {
    // given — a cell in hour 0
    final var merger = newMerger(1, sink);
    merger.accept(partial(1, "EU", 0L, 1, 100L));

    // when — a partial for a much later window advances the watermark past hour-0 + lateness
    merger.accept(partial(1, "EU", 5 * HOUR, 1, 5L));
    merger.checkpoint();

    // then — hour 0 emitted its final value and its slots were evicted from the durable store
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(100L);
    final KeyValueStore<DbBytes, DbBytes> slots =
        provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
    final int[] remaining = {0};
    slots.forEach((k, v) -> remaining[0]++);
    assertThat(remaining[0]).isEqualTo(1); // only the still-open 5*HOUR cell remains
  }
}
