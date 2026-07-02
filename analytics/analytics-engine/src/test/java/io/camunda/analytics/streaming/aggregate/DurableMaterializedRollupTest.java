/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.streaming.aggregate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import io.camunda.analytics.streaming.window.TumblingWindows;
import io.camunda.analytics.streaming.window.Windowed;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbLong;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class DurableMaterializedRollupTest {

  private static final long HOUR = 3_600_000L;
  private static final long LATENESS = 60_000L;

  private record Sale(String region, long amount, long timestamp, int partition, long position) {}

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

  private static final SourceCoordinate<Sale> COORD =
      new SourceCoordinate<>() {
        @Override
        public int partition(final Sale sale) {
          return sale.partition();
        }

        @Override
        public long position(final Sale sale) {
          return sale.position();
        }
      };

  private static final Codec<String> KEY_CODEC =
      new Codec<>() {
        @Override
        public byte[] encode(final String value) {
          return value.getBytes(StandardCharsets.UTF_8);
        }

        @Override
        public String decode(final byte[] bytes) {
          return new String(bytes, StandardCharsets.UTF_8);
        }
      };

  private static final Codec<Long> ACC_CODEC =
      new Codec<>() {
        @Override
        public byte[] encode(final Long value) {
          return ByteBuffer.allocate(Long.BYTES).putLong(value).array();
        }

        @Override
        public Long decode(final byte[] bytes) {
          return ByteBuffer.wrap(bytes).getLong();
        }
      };

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

  private DurableMaterializedRollup<Sale, String, Long> newRollup() {
    return newRollup(1, sink);
  }

  private DurableMaterializedRollup<Sale, String, Long> newRollup(
      final int rollupId, final InMemoryResultSink<Windowed<String>, Long> resultSink) {
    final KeyValueStore<DbBytes, DbBytes> cells =
        provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
    final KeyValueStore<DbBytes, DbLong> offsets =
        provider.keyValueStore(TestColumnFamilies.OFFSETS, new DbBytes(), new DbLong());
    return new DurableMaterializedRollup<>(
        rollupId,
        SUM,
        Sale::region,
        Sale::timestamp,
        COORD,
        TumblingWindows.of(HOUR),
        LATENESS,
        resultSink,
        cells,
        offsets,
        KEY_CODEC,
        ACC_CODEC,
        provider::runInTransaction);
  }

  @Test
  void shouldUpsertFullValueAndDedup() {
    // given
    final var rollup = newRollup();

    // when — two sales flushed, then a duplicate plus a new one flushed again
    rollup.accept(new Sale("EU", 100, 1_000L, 1, 1L));
    rollup.accept(new Sale("EU", 50, 2_000L, 1, 2L));
    rollup.flush();
    rollup.accept(new Sale("EU", 50, 2_000L, 1, 2L)); // duplicate position — dropped
    rollup.accept(new Sale("EU", 30, 3_000L, 1, 3L));
    rollup.flush();

    // then — the durable aggregate folded each source position once and the sink holds the full
    // running value (150 + 30), overwritten rather than appended
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(180L);
  }

  @Test
  void shouldFinalizeAndEvictClosedWindows() {
    // given — a sale in hour 0, made durable
    final var rollup = newRollup();
    rollup.accept(new Sale("EU", 100, 1_000L, 1, 1L));
    rollup.flush();
    rollup.checkpoint();

    // when — event time advances past hour-0 end + lateness, then a checkpoint applies the eviction
    rollup.advanceStreamTime(HOUR + LATENESS + 1);
    rollup.checkpoint();

    // then — hour 0 emitted its final value and was evicted from durable state
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(100L);
    final KeyValueStore<DbBytes, DbBytes> cells =
        provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
    final int[] remaining = {0};
    cells.forEach((k, v) -> remaining[0]++);
    assertThat(remaining[0]).isZero();
  }

  @Test
  void shouldNotPersistUntilCheckpoint() throws Exception {
    // given — a sale folded and flushed to the serving view, but never checkpointed
    final var rollup = newRollup();
    rollup.accept(new Sale("EU", 100, 1_000L, 1, 1L));
    rollup.flush(); // converges the sink but makes nothing durable
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(100L);
    provider.close();

    // when — the store is reopened and a fresh rollup recovers from it
    open();
    final var recovered = newRollup();

    // then — flush persisted no state or offset (the source log is the recovery mechanism)
    assertThat(recovered.consumedPositions()).isEmpty();
    final KeyValueStore<DbBytes, DbBytes> cells =
        provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
    final int[] remaining = {0};
    cells.forEach((k, v) -> remaining[0]++);
    assertThat(remaining[0]).isZero();
  }

  @Test
  void shouldCoalesceManyFlushesIntoOneCheckpoint() throws Exception {
    // given — two batches, each flushed to keep the serving view fresh, one checkpoint at the end
    final var rollup = newRollup();
    rollup.accept(new Sale("EU", 100, 1_000L, 1, 1L));
    rollup.flush();
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(100L); // fresh after the first flush
    rollup.accept(new Sale("EU", 50, 2_000L, 1, 2L));
    rollup.flush();
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(150L); // fresh after the second flush
    rollup.checkpoint();
    provider.close();

    // when — the store is reopened and a fresh rollup recovers
    open();
    final var recovered = newRollup();

    // then — the coalesced full value and the offset survived the restart
    assertThat(recovered.consumedPositions()).containsExactly(entry(1, 3L));
    recovered.accept(new Sale("EU", 10, 3_000L, 1, 3L));
    recovered.flush();
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(160L);
  }

  @Test
  void shouldIsolateRollupsSharingTheSameStoresByRollupId() throws Exception {
    // given — two rollups with distinct ids sharing the same cell and offset column families, each
    // with its own sink so we can observe their recovered state independently
    final InMemoryResultSink<Windowed<String>, Long> sinkOne = new InMemoryResultSink<>();
    final InMemoryResultSink<Windowed<String>, Long> sinkTwo = new InMemoryResultSink<>();
    final var first = newRollup(1, sinkOne);
    final var second = newRollup(2, sinkTwo);

    // when — each folds its own sale (same window/key, different amounts) and checkpoints
    first.accept(new Sale("EU", 100, 1_000L, 1, 1L));
    second.accept(new Sale("EU", 7, 1_000L, 1, 1L));
    first.checkpoint();
    second.checkpoint();
    provider.close();

    // then — after a restart each recovers only its own cells/offset from the shared stores
    open();
    final var recoveredFirst = newRollup(1, sinkOne);
    final var recoveredSecond = newRollup(2, sinkTwo);
    recoveredFirst.accept(new Sale("EU", 5, 2_000L, 1, 2L));
    recoveredSecond.accept(new Sale("EU", 3, 2_000L, 1, 2L));
    recoveredFirst.flush();
    recoveredSecond.flush();
    assertThat(sinkOne.get(new Windowed<>("EU", 0L))).contains(105L); // 100 + 5, id 2 not mixed in
    assertThat(sinkTwo.get(new Windowed<>("EU", 0L))).contains(10L); // 7 + 3, id 1 not mixed in
  }

  @Test
  void shouldRecoverStateAndOffsetsAcrossRestart() throws Exception {
    // given — a checkpointed sale, then the store is closed (simulating a crash/restart)
    final var before = newRollup();
    before.accept(new Sale("EU", 100, 1_000L, 1, 5L));
    before.flush();
    before.checkpoint();
    provider.close();

    // when — the store is reopened and a fresh rollup recovers from it
    open();
    final var recovered = newRollup();

    // then — it resumes the source just past the last applied position (start-from-offset)
    assertThat(recovered.consumedPositions()).containsExactly(entry(1, 6L));

    // and — the durable aggregate survived: a new sale folds onto the recovered base (100 + 50),
    // and a replayed old position is still deduped
    recovered.accept(new Sale("EU", 100, 1_000L, 1, 5L)); // replay below watermark — dropped
    recovered.accept(new Sale("EU", 50, 2_000L, 1, 6L));
    recovered.flush();
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(150L);
  }
}
