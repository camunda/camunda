/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static io.camunda.zeebe.db.cache.TestColumnFamily.FIRST;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.BACKPRESSURE_WAIT;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.CACHE_EVICTIONS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.CACHE_GETS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.CACHE_LOADS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.CACHE_SIZE;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.COMMITTED_KEYS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.COMMIT_LOCK_WAIT;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.FAILURES;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.LOCKED_READS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.PENDING_BYTES;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.PENDING_TRANSACTIONS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.READ_RETRIES;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.SCAN_HOLD;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.SNAPSHOT_CATCH_UP;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.SNAPSHOT_CHECKPOINT;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.UNFLUSHED_KEYS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.UNFLUSHED_READS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.WRITE_KEYS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.WRITE_LATENCY;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.WRITE_RETRIES;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.WRITE_TRANSACTIONS;
import static io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.WRITTEN_KEYS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.cache.WriteBehindMetricsDoc.WriteBehindKeyNames;
import io.camunda.zeebe.util.exception.RecoverableException;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.io.File;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class WriteBehindMetricsTest {

  private static final Duration STALLED = Duration.ofHours(1);
  private static final Duration QUICK = Duration.ofMillis(10);

  private final InMemoryStorage storage = new InMemoryStorage();
  private final MeterRegistry registry = storage.meterRegistry();
  private final StorageColumnFamilies<TestColumnFamily> reader =
      new StorageColumnFamilies<>(storage.db(), storage.db().createContext());

  @Test
  void shouldCountReadsByWhereTheyAreAnswered() {
    try (final var store = store(new TestConfiguration().maxWriteDelay(STALLED))) {
      // given
      store.commit(Map.of(key(1), new Op(bytes(10))));

      // when
      store.get(key(1), reader);
      store.catchUp();
      store.get(key(1), reader);
      store.get(key(2), reader);
      store.get(key(2), reader);

      // then - the commit put key 1 into the cache, and the first read of key 2 loaded it
      assertThat(counter(UNFLUSHED_READS)).isOne();
      assertThat(counter(CACHE_GETS)).isEqualTo(3);
      assertThat(counter(CACHE_LOADS)).isOne();
      assertThat(counter(LOCKED_READS)).isZero();
    }
  }

  @Test
  void shouldCountReadsThatTakeTheReadLock() {
    // given - a store that reads under the lock right away
    try (final var store =
        new WriteBehindStore<>(storage.db(), new TestConfiguration().build(), 0)) {
      // when
      store.get(key(1), reader);

      // then
      assertThat(counter(LOCKED_READS)).isOne();
      assertThat(counter(READ_RETRIES)).isZero();
      assertThat(counter(CACHE_GETS)).isZero();
    }
  }

  @Test
  void shouldMeasureTheReadCache() {
    // given
    final long cacheMaxBytes = 16 * 1024;
    try (final var store =
        store(new TestConfiguration().maxWriteDelay(QUICK).cacheMaxBytes(cacheMaxBytes))) {

      // when
      for (int key = 0; key < 1_000; key++) {
        store.commit(Map.of(wideKey(key), new Op(new byte[64])));
      }

      // then
      assertThat(registry.get(CACHE_EVICTIONS.getName()).functionCounter().count()).isPositive();
      assertThat(gauge(CACHE_SIZE)).isPositive().isLessThanOrEqualTo(cacheMaxBytes);
    }
  }

  @Test
  void shouldShowWhatTheStorageDoesNotHaveYet() {
    try (final var store = store(new TestConfiguration().maxWriteDelay(STALLED))) {
      // when
      store.commit(Map.of(key(1), new Op(bytes(10)), key(2), new Op(bytes(20))));
      store.commit(Map.of(key(1), new Op(bytes(11))));

      // then
      assertThat(gauge(UNFLUSHED_KEYS)).isEqualTo(2);
      assertThat(gauge(PENDING_TRANSACTIONS)).isEqualTo(2);
      assertThat(gauge(PENDING_BYTES)).isEqualTo(store.pendingBytes()).isPositive();

      // when
      store.catchUp();

      // then
      assertThat(gauge(UNFLUSHED_KEYS)).isZero();
      assertThat(gauge(PENDING_TRANSACTIONS)).isZero();
      assertThat(gauge(PENDING_BYTES)).isZero();
    }
  }

  @Test
  void shouldShowHowManyWritesTheWriterCombined() {
    // given
    storage.put(FIRST, bytes(2), bytes(20));
    try (final var store = store(new TestConfiguration().maxWriteDelay(STALLED))) {
      store.commit(Map.of(key(1), new Op(bytes(10))));
      store.commit(Map.of(key(1), new Op(bytes(11))));
      store.commit(Map.of(key(1), new Op(bytes(12)), key(2), new Op(null)));

      // when
      store.catchUp();

      // then - four committed keys became one storage transaction with one upsert and one delete
      assertThat(counter(COMMITTED_KEYS)).isEqualTo(4);
      assertThat(writtenKeys("upsert")).isOne();
      assertThat(writtenKeys("delete")).isOne();
      assertThat(summary(WRITE_TRANSACTIONS).count()).isOne();
      assertThat(summary(WRITE_TRANSACTIONS).totalAmount()).isEqualTo(3);
      assertThat(summary(WRITE_KEYS).totalAmount()).isEqualTo(2);
      assertThat(timer(WRITE_LATENCY).count()).isOne();
    }
  }

  @Test
  void shouldTimeCommitsThatWaitForTheWriter() {
    // given - one commit is enough to go above the pending bytes limit
    try (final var store =
        store(new TestConfiguration().maxWriteDelay(Duration.ofMillis(50)).maxPendingBytes(1))) {
      store.commit(Map.of(key(1), new Op(bytes(10))));

      // when
      store.commit(Map.of(key(2), new Op(bytes(20))));

      // then
      assertThat(timer(BACKPRESSURE_WAIT).count()).isOne();
      assertThat(timer(COMMIT_LOCK_WAIT).count()).isEqualTo(2);
    }
  }

  @Test
  void shouldTimeHowLongAScanHoldsPublicationsBack() {
    try (final var store = store(new TestConfiguration())) {
      // given
      final var overlay = store.beginScan(FIRST, null, true, null, true);

      // when
      overlay.release();
      overlay.release();

      // then
      assertThat(timer(SCAN_HOLD).count()).isOne();
    }
  }

  @Test
  void shouldCountRetriedWrites() {
    try (final var store = store(new TestConfiguration().maxWriteDelay(QUICK))) {
      // given
      storage.failTransactions(1, () -> new RecoverableException("expected"));

      // when
      store.commit(Map.of(key(1), new Op(bytes(10))));
      store.catchUp();

      // then
      assertThat(counter(WRITE_RETRIES)).isOne();
      assertThat(counter(FAILURES)).isZero();
    }
  }

  @Test
  void shouldCountFailures() {
    try (final var store = store(new TestConfiguration().maxWriteDelay(QUICK))) {
      // given
      storage.failTransactions(1, () -> new IllegalStateException("expected"));

      // when
      store.commit(Map.of(key(1), new Op(bytes(10))));

      // then
      await().untilAsserted(() -> assertThat(counter(FAILURES)).isOne());
    }
  }

  @Test
  void shouldTimeSnapshots() throws Exception {
    try (final var db =
        new WriteBehindZeebeDb<>(
            storage.db(),
            new TestConfiguration().maxWriteDelay(STALLED).build(),
            new ConsistencyChecksSettings(false, false))) {
      // when
      db.createSnapshot(new File("snapshot"));

      // then
      assertThat(timer(SNAPSHOT_CATCH_UP).count()).isOne();
      assertThat(timer(SNAPSHOT_CHECKPOINT).count()).isOne();
    }
  }

  private WriteBehindStore<TestColumnFamily> store(final TestConfiguration configuration) {
    return new WriteBehindStore<>(storage.db(), configuration.build());
  }

  private double counter(final WriteBehindMetricsDoc documentation) {
    return registry.get(documentation.getName()).counter().count();
  }

  private double writtenKeys(final String operation) {
    return registry
        .get(WRITTEN_KEYS.getName())
        .tag(WriteBehindKeyNames.OPERATION.asString(), operation)
        .counter()
        .count();
  }

  private double gauge(final WriteBehindMetricsDoc documentation) {
    return registry.get(documentation.getName()).gauge().value();
  }

  private Timer timer(final WriteBehindMetricsDoc documentation) {
    return registry.get(documentation.getName()).timer();
  }

  private DistributionSummary summary(final WriteBehindMetricsDoc documentation) {
    return registry.get(documentation.getName()).summary();
  }

  private static CacheKey key(final int key) {
    return new CacheKey(FIRST, bytes(key));
  }

  private static CacheKey wideKey(final int key) {
    return new CacheKey(FIRST, ByteBuffer.allocate(Integer.BYTES).putInt(key).array());
  }

  private static byte[] bytes(final int... values) {
    final var bytes = new byte[values.length];
    for (int i = 0; i < values.length; i++) {
      bytes[i] = (byte) values[i];
    }
    return bytes;
  }
}
