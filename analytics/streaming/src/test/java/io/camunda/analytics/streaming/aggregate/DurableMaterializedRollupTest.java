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

import io.camunda.analytics.streaming.state.TestColumnFamilies;
import io.camunda.analytics.streaming.state.api.KeyValueStore;
import io.camunda.analytics.streaming.state.rocksdb.RocksDbStateStoreProvider;
import io.camunda.analytics.streaming.window.TumblingWindows;
import io.camunda.analytics.streaming.window.Windowed;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.impl.DbInt;
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
    final KeyValueStore<DbBytes, DbBytes> cells =
        provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
    final KeyValueStore<DbInt, DbLong> offsets =
        provider.keyValueStore(TestColumnFamilies.OFFSETS, new DbInt(), new DbLong());
    return new DurableMaterializedRollup<>(
        SUM,
        Sale::region,
        Sale::timestamp,
        COORD,
        TumblingWindows.of(HOUR),
        LATENESS,
        sink,
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
    // given — a sale in hour 0
    final var rollup = newRollup();
    rollup.accept(new Sale("EU", 100, 1_000L, 1, 1L));
    rollup.flush();

    // when — event time advances past hour-0 end + lateness
    rollup.advanceStreamTime(HOUR + LATENESS + 1);

    // then — hour 0 emitted its final value and was evicted from durable state
    assertThat(sink.get(new Windowed<>("EU", 0L))).contains(100L);
    final KeyValueStore<DbBytes, DbBytes> cells =
        provider.keyValueStore(TestColumnFamilies.CELLS, new DbBytes(), new DbBytes());
    final int[] remaining = {0};
    cells.forEach((k, v) -> remaining[0]++);
    assertThat(remaining[0]).isZero();
  }

  @Test
  void shouldRecoverStateAndOffsetsAcrossRestart() throws Exception {
    // given — a flushed sale, then the store is closed (simulating a crash/restart)
    final var before = newRollup();
    before.accept(new Sale("EU", 100, 1_000L, 1, 5L));
    before.flush();
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
