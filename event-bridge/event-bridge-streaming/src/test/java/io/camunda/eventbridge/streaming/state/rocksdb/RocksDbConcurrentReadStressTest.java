/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.eventbridge.streaming.state.TestColumnFamilies;
import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbString;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Stress-tests the async-checkpointing read path: one thread committing write transactions on the
 * provider's write context while another thread reads and scans the same column families through
 * the provider's dedicated read context — the exact shape of a background persist committing a
 * checkpoint while the partition's processing thread serves cache misses from the store.
 *
 * <p>Every write transaction rewrites a fixed key set to one new version as a single atomic batch,
 * so the reader can validate three properties: values are never torn (each value carries a checksum
 * of its version), point reads are monotonic per key (committed versions only, never rolled back),
 * and a single scan never observes a mix of two commits (iterators are point-in-time).
 */
final class RocksDbConcurrentReadStressTest {

  /** Keys rewritten by every commit; all of them move to the new version atomically. */
  private static final int KEY_COUNT = 32;

  /** Write transactions the writer commits while the reader hammers the read path. */
  private static final int COMMITS = 500;

  /** Row pair toggled per commit: present in even versions, deleted in odd ones — never split. */
  private static final long PAIR_PREFIX = 7_000_000L;

  @TempDir private Path dataDir;
  private RocksDbStateStoreProvider<TestColumnFamilies> provider;
  private KeyValueStore<DbLong, DbString> kvStore;
  private KeyValueStore<DbCompositeKey<DbLong, DbString>, DbString> pairStore;

  @BeforeEach
  void setUp() {
    provider = RocksDbStateStoreProvider.open(dataDir.toFile(), new SimpleMeterRegistry());
    kvStore = provider.keyValueStore(TestColumnFamilies.KV, new DbLong(), new DbString());
    pairStore =
        provider.keyValueStore(
            TestColumnFamilies.COMPOSITE,
            new DbCompositeKey<>(new DbLong(), new DbString()),
            new DbString());
  }

  @AfterEach
  void tearDown() throws Exception {
    provider.close();
  }

  @Test
  void shouldServeOnlyCommittedConsistentStateToConcurrentReader() {
    // given -- a writer committing versioned batches and a reader validating concurrently
    final var writerDone = new AtomicBoolean(false);
    final var readerDone = new AtomicBoolean(false);
    final var writerFailure = new AtomicReference<Throwable>();
    final var readerFailure = new AtomicReference<Throwable>();
    final var scansObserved = new AtomicLong();
    final var maxVersionObserved = new AtomicLong();
    // The writer holds its first commit until the reader is demonstrably in its validation loop,
    // so every commit races an active reader instead of finishing before the reader warms up.
    final var readerLooping = new CountDownLatch(1);

    final Thread writer =
        new Thread(
            () -> {
              try {
                readerLooping.await();
                runWriter();
              } catch (final Throwable t) {
                writerFailure.set(t);
              } finally {
                writerDone.set(true);
              }
            },
            "stress-writer");
    final Thread reader =
        new Thread(
            () -> {
              try {
                runReader(writerDone, readerLooping, scansObserved, maxVersionObserved);
              } catch (final Throwable t) {
                readerFailure.set(t);
              } finally {
                readerDone.set(true);
              }
            },
            "stress-reader");

    // when -- both threads run against the same stores until the writer finishes
    writer.start();
    reader.start();
    await()
        .atMost(Duration.ofMinutes(2))
        .until(
            () -> writerDone.get() && readerDone.get() && !writer.isAlive() && !reader.isAlive());

    // then -- neither thread failed, the reader made progress, and the final commit is visible
    assertThat(writerFailure.get()).as("writer thread failure").isNull();
    assertThat(readerFailure.get()).as("reader thread failure").isNull();
    assertThat(scansObserved.get()).as("reader scan iterations").isPositive();
    // The reader's final pass runs after the writer's last commit, so it must have seen it.
    assertThat(maxVersionObserved.get())
        .as("reader observed the final committed version")
        .isEqualTo(COMMITS);
    // Both threads have terminated, so reading from the test thread no longer races the reader.
    final var key = new DbLong();
    key.wrapLong(0);
    assertThat(kvStore.get(key))
        .as("final committed version visible after the writer finished")
        .hasValueSatisfying(value -> assertThat(parseVersion(value.toString())).isEqualTo(COMMITS));
  }

  /**
   * Commits {@link #COMMITS} transactions. Each one atomically rewrites all {@link #KEY_COUNT} keys
   * to the same self-checksummed version value and toggles the row pair (both rows in, or both rows
   * out) — so any state a reader may observe is one of the committed cuts, never a mixture.
   */
  private void runWriter() {
    final var key = new DbLong();
    final var value = new DbString();
    final var pairFirst = new DbLong();
    final var pairSecond = new DbString();
    final var pairKey = new DbCompositeKey<>(pairFirst, pairSecond);
    final var pairValue = new DbString();
    pairFirst.wrapLong(PAIR_PREFIX);
    pairValue.wrapString("pair");

    for (int version = 1; version <= COMMITS; version++) {
      final int v = version;
      provider.runInTransaction(
          () -> {
            for (long k = 0; k < KEY_COUNT; k++) {
              key.wrapLong(k);
              value.wrapString(versionedValue(v));
              kvStore.put(key, value);
            }
            for (final String member : List.of("a", "b")) {
              pairSecond.wrapString(member);
              if (v % 2 == 0) {
                pairStore.put(pairKey, pairValue);
              } else {
                pairStore.delete(pairKey);
              }
            }
          });
    }
  }

  /**
   * Hammers the read path until the writer finishes, then validates one final pass. Uses its own
   * key flyweights; the store-registered flyweights are only ever wrapped by this (reading) thread.
   */
  private void runReader(
      final AtomicBoolean writerDone,
      final CountDownLatch readerLooping,
      final AtomicLong scansObserved,
      final AtomicLong maxVersionObserved) {
    final var key = new DbLong();
    final var pairPrefix = new DbLong();
    pairPrefix.wrapLong(PAIR_PREFIX);
    final long[] lastVersionPerKey = new long[KEY_COUNT];

    boolean finalPass = false;
    while (!finalPass) {
      readerLooping.countDown();
      // Run one last validation pass after the writer is done so the final commit is also checked
      // through the concurrent read path (not only from the test thread afterwards).
      finalPass = writerDone.get();

      // Point reads: each value must be self-consistent (untorn) and per-key monotonic (committed
      // versions only; a rolled-back or in-flight write would break monotonicity or the checksum).
      for (int k = 0; k < KEY_COUNT; k++) {
        key.wrapLong(k);
        final Optional<DbString> read = kvStore.get(key);
        if (read.isEmpty()) {
          assertThat(lastVersionPerKey[k])
              .as("a key can only be absent before the first commit")
              .isZero();
          continue;
        }
        final long version = parseVersion(read.get().toString());
        assertThat(version)
            .as("point read of key %d is monotonic", k)
            .isGreaterThanOrEqualTo(lastVersionPerKey[k])
            .isLessThanOrEqualTo(COMMITS);
        lastVersionPerKey[k] = version;
        maxVersionObserved.accumulateAndGet(version, Math::max);
      }

      // Scan: a single iterator is point-in-time, and every commit rewrites all keys atomically —
      // so one scan must observe exactly one version across all keys (or nothing at all).
      final List<Long> versionsInScan = new ArrayList<>();
      kvStore.forEach((k, v) -> versionsInScan.add(parseVersion(v.toString())));
      if (!versionsInScan.isEmpty()) {
        assertThat(versionsInScan)
            .as("one scan observes exactly one committed cut")
            .hasSize(KEY_COUNT)
            .containsOnly(versionsInScan.getFirst());
      }
      scansObserved.incrementAndGet();

      // Pair invariant: the two rows are always written or deleted in the same transaction, so a
      // prefix scan sees both or neither — one of them alone would be a torn commit.
      final List<String> members = new ArrayList<>();
      pairStore.prefixScan(pairPrefix, (k, v) -> members.add(k.second().toString()));
      assertThat(members.size()).as("row pair is never observed half-committed").isIn(0, 2);
    }
  }

  /** A value that a reader can validate on its own: {@code v<version>:<checksum(version)>}. */
  private static String versionedValue(final long version) {
    return "v" + version + ":" + checksum(version);
  }

  /** Parses a {@link #versionedValue} and asserts it is not torn before returning its version. */
  private static long parseVersion(final String value) {
    assertThat(value).as("value has the versioned format").startsWith("v").contains(":");
    final int separator = value.indexOf(':');
    final long version = Long.parseLong(value, 1, separator, 10);
    final long checksum = Long.parseLong(value, separator + 1, value.length(), 10);
    assertThat(checksum).as("value %s is not torn", value).isEqualTo(checksum(version));
    return version;
  }

  private static long checksum(final long version) {
    return version * 31 + 17;
  }
}
