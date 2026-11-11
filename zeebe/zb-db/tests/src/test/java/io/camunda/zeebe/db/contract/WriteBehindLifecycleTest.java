/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.zeebe.db.cache.WriteBehindConfiguration;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import java.nio.file.Path;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Opening and closing the write-behind cache around RocksDB. */
final class WriteBehindLifecycleTest {

  private static final WriteBehindConfiguration STALLED_WRITER =
      new WriteBehindConfiguration(
          WriteBehindConfiguration.DEFAULT.cacheMaxBytes(),
          WriteBehindConfiguration.DEFAULT.maxPendingBytes(),
          WriteBehindConfiguration.DEFAULT.batchSize(),
          Duration.ofHours(1),
          WriteBehindConfiguration.DEFAULT.snapshotCatchUpTimeout());

  @TempDir private Path directory;

  @Test
  void shouldCloseWithPendingWrites() throws Exception {
    // given
    final var db =
        WriteBehindTestFactory.<DefaultColumnFamily>create(STALLED_WRITER)
            .createDb(directory.toFile());
    final var columnFamily =
        db.createColumnFamily(
            DefaultColumnFamily.DEFAULT, db.createContext(), new DbLong(), new DbLong());
    for (long key = 0; key < 100; key++) {
      columnFamily.insert(dbLong(key), dbLong(key));
    }

    // when - then: the writer stops before RocksDB closes, so it never uses a freed handle
    assertThatCode(db::close).doesNotThrowAnyException();
  }

  @Test
  void shouldNotCacheTheBulkCopyDatabase() throws Exception {
    // given
    final var factory = WriteBehindTestFactory.<DefaultColumnFamily>create(STALLED_WRITER);

    // when
    try (final var db = factory.createDb(directory.toFile(), false)) {
      db.createColumnFamily(
              DefaultColumnFamily.DEFAULT, db.createContext(), new DbLong(), new DbLong())
          .insert(dbLong(1), dbLong(10));
    }

    // then - written straight to RocksDB, although a cached writer would have waited an hour
    try (final var reopened =
        RocksDbTestFactory.<DefaultColumnFamily>create().createDb(directory.toFile())) {
      assertThat(
              reopened
                  .createColumnFamily(
                      DefaultColumnFamily.DEFAULT,
                      reopened.createContext(),
                      new DbLong(),
                      new DbLong())
                  .get(dbLong(1)))
          .extracting(DbLong::getValue)
          .isEqualTo(10L);
    }
  }

  private static DbLong dbLong(final long value) {
    final var dbLong = new DbLong();
    dbLong.wrapLong(value);
    return dbLong;
  }
}
