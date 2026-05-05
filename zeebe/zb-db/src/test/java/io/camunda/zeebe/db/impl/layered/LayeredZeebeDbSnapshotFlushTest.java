/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.layered;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LayeredZeebeDbSnapshotFlushTest {

  private final ZeebeDbFactory<DefaultColumnFamily> persistentFactory =
      DefaultZeebeDbFactory.getDefaultFactory();

  @Test
  void shouldFlushOverlayWritesAndDeletesBeforeSnapshot(
      final @TempDir File path, final @TempDir File snapshotDir) throws Exception {
    // given
    writePersistentLongValue(path, 1L, 100L);
    writePersistentLongValue(path, 3L, 300L);

    try (final var layeredDb = openLayeredDb(path)) {
      final var columnFamily = longColumnFamily(layeredDb);
      columnFamily.upsert(dbLong(1L), dbLong(111L));
      columnFamily.upsert(dbLong(2L), dbLong(222L));
      columnFamily.deleteExisting(dbLong(3L));

      // when
      final var snapshotPath = new File(snapshotDir, "snapshot");
      layeredDb.createSnapshot(snapshotPath);
    }

    // then
    try (final var persistentSnapshotDb =
        persistentFactory.createDb(new File(snapshotDir, "snapshot"))) {
      final var columnFamily = longColumnFamily(persistentSnapshotDb);
      assertThat(columnFamily.get(dbLong(1L))).isNotNull();
      assertThat(columnFamily.get(dbLong(1L)).getValue()).isEqualTo(111L);
      assertThat(columnFamily.get(dbLong(2L))).isNotNull();
      assertThat(columnFamily.get(dbLong(2L)).getValue()).isEqualTo(222L);
      assertThat(columnFamily.get(dbLong(3L))).isNull();
    }
  }

  @SuppressWarnings("unchecked")
  private LayeredZeebeDb<DefaultColumnFamily> openLayeredDb(final File path) {
    return (LayeredZeebeDb<DefaultColumnFamily>)
        new LayeredZeebeDbFactory<DefaultColumnFamily>(
                new RocksDbConfiguration(),
                new ConsistencyChecksSettings(true, true),
                new AccessMetricsConfiguration(Kind.NONE, 1),
                SimpleMeterRegistry::new)
            .createDb(path);
  }

  private void writePersistentLongValue(final File path, final long keyValue, final long value)
      throws Exception {
    try (final var persistentDb = persistentFactory.createDb(path, false)) {
      final var columnFamily = longColumnFamily(persistentDb);
      columnFamily.upsert(dbLong(keyValue), dbLong(value));
    }
  }

  private ColumnFamily<DbLong, DbLong> longColumnFamily(final ZeebeDb<DefaultColumnFamily> db) {
    return db.createColumnFamily(
        DefaultColumnFamily.DEFAULT, db.createContext(), new DbLong(), new DbLong());
  }

  private DbLong dbLong(final long value) {
    final var dbLong = new DbLong();
    dbLong.wrapLong(value);
    return dbLong;
  }
}
