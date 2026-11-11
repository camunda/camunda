/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.cache.WriteBehindConfiguration;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import java.io.File;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Snapshots of the write-behind cache around RocksDB, opened with plain RocksDB as recovery would
 * open them.
 */
final class WriteBehindSnapshotTest {

  private static final long KEYS = 10;
  private static final long MARKER = 1_000;

  @TempDir private Path directory;

  @Test
  void shouldSnapshotEveryTransactionCommittedBeforeTheSnapshot() throws Exception {
    // given - a writer that would otherwise wait an hour before writing anything
    final var factory =
        WriteBehindTestFactory.<DefaultColumnFamily>create(
            configuration(Duration.ofHours(1), 1_000));
    try (final var db = factory.createDb(directory.resolve("runtime").toFile())) {
      final var columnFamily =
          db.createColumnFamily(
              DefaultColumnFamily.DEFAULT, db.createContext(), new DbLong(), new DbLong());
      for (long key = 0; key < 100; key++) {
        columnFamily.insert(dbLong(key), dbLong(key * 10));
      }

      // when
      final var snapshot = directory.resolve("snapshot").toFile();
      db.createSnapshot(snapshot);

      // then
      assertThat(entries(snapshot)).hasSize(100).containsEntry(99L, 990L);
    }
  }

  @Test
  void shouldSnapshotWholeTransactionsWhileTheWriterKeepsWriting() throws Exception {
    // given - each transaction writes the same value to every key and to a marker; the writer
    // groups several of them and writes every millisecond
    final var factory =
        WriteBehindTestFactory.<DefaultColumnFamily>create(configuration(Duration.ofMillis(1), 50));
    try (final var db = factory.createDb(directory.resolve("runtime").toFile())) {
      final var committing = new AtomicBoolean(true);
      final var committer =
          CompletableFuture.runAsync(
              () -> {
                final var context = db.createContext();
                final var columnFamily =
                    db.createColumnFamily(
                        DefaultColumnFamily.DEFAULT, context, new DbLong(), new DbLong());
                for (long n = 1; committing.get(); n++) {
                  final long value = n;
                  context.runInTransaction(
                      () -> {
                        for (long key = 0; key < KEYS; key++) {
                          columnFamily.upsert(dbLong(key), dbLong(value));
                        }
                        columnFamily.upsert(dbLong(MARKER), dbLong(value));
                      });
                }
              });
      final var reader =
          db.createColumnFamily(
              DefaultColumnFamily.DEFAULT, db.createContext(), new DbLong(), new DbLong());

      for (int i = 0; i < 20; i++) {
        // when - like a snapshot's positions, the marker is read before the snapshot is taken
        final var committedMarker = reader.get(dbLong(MARKER));
        final long position = committedMarker == null ? 0 : committedMarker.getValue();
        final var snapshot = directory.resolve("snapshot-" + i).toFile();
        db.createSnapshot(snapshot);

        // then
        final var entries = entries(snapshot);
        final long marker = entries.getOrDefault(MARKER, 0L);
        assertThat(marker).isGreaterThanOrEqualTo(position);
        if (marker > 0) {
          assertThat(entries).hasSize((int) KEYS + 1);
          assertThat(entries.values()).containsOnly(marker);
        }
      }

      committing.set(false);
      assertThat(committer).succeedsWithin(Duration.ofSeconds(10));
    }
  }

  private static WriteBehindConfiguration configuration(
      final Duration maxWriteDelay, final int batchSize) {
    final var defaults = WriteBehindConfiguration.DEFAULT;
    return new WriteBehindConfiguration(
        defaults.cacheMaxBytes(),
        defaults.maxPendingBytes(),
        batchSize,
        maxWriteDelay,
        defaults.snapshotCatchUpTimeout());
  }

  private static Map<Long, Long> entries(final File snapshot) throws Exception {
    try (final var db = RocksDbTestFactory.<DefaultColumnFamily>create().createDb(snapshot)) {
      final var columnFamily =
          db.createColumnFamily(
              DefaultColumnFamily.DEFAULT, db.createContext(), new DbLong(), new DbLong());
      final Map<Long, Long> entries = new TreeMap<>();
      columnFamily.forEach((key, value) -> entries.put(key.getValue(), value.getValue()));
      return entries;
    }
  }

  private static DbLong dbLong(final long value) {
    final var dbLong = new DbLong();
    dbLong.wrapLong(value);
    return dbLong;
  }
}
