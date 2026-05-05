/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.layered;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

final class LayeredZeebeDbOverlayWriteTest {

  private final ZeebeDbFactory<DefaultColumnFamily> persistentFactory =
      DefaultZeebeDbFactory.getDefaultFactory();

  @Test
  void shouldKeepUpdateInActiveLayerUntilSnapshot(final @TempDir File path) throws Exception {
    // given
    writePersistentLongValue(path, 1L, 100L);

    try (final var layeredDb = openLayeredDb(path)) {
      final var columnFamily = longColumnFamily(layeredDb);
      final var key = dbLong(1L);
      columnFamily.update(key, dbLong(200L));

      // then
      assertThat(columnFamily.get(key)).isNotNull();
      assertThat(columnFamily.get(key).getValue()).isEqualTo(200L);
      assertThat(longColumnFamily(layeredDb.persistentDb()).get(key)).isNotNull();
      assertThat(longColumnFamily(layeredDb.persistentDb()).get(key).getValue()).isEqualTo(100L);
    }
  }

  @Test
  void shouldHidePersistentValueWithCommittedTombstone(final @TempDir File path) throws Exception {
    // given
    writePersistentLongValue(path, 1L, 100L);

    try (final var layeredDb = openLayeredDb(path)) {
      final var columnFamily = longColumnFamily(layeredDb);
      final var key = dbLong(1L);

      // when
      columnFamily.deleteExisting(key);

      // then
      assertThat(columnFamily.get(key)).isNull();
      assertThat(columnFamily.exists(key)).isFalse();
      assertThat(longColumnFamily(layeredDb.persistentDb()).get(key)).isNotNull();
      assertThat(longColumnFamily(layeredDb.persistentDb()).get(key).getValue()).isEqualTo(100L);
    }
  }

  @Test
  void shouldRollbackOverlayWritesAndTombstones(final @TempDir File path) throws Exception {
    // given
    writePersistentLongValue(path, 1L, 100L);

    try (final var layeredDb = openLayeredDb(path)) {
      final var context = layeredDb.createContext();
      final var columnFamily =
          layeredDb.createColumnFamily(
              DefaultColumnFamily.DEFAULT, context, new DbLong(), new DbLong());
      final var key = dbLong(1L);

      // when
      assertThatThrownBy(
              () ->
                  context.runInTransaction(
                      () -> {
                        columnFamily.update(key, dbLong(200L));
                        columnFamily.deleteExisting(key);
                        throw new RuntimeException("rollback");
                      }))
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("rollback");

      // then
      assertThat(columnFamily.get(key)).isNotNull();
      assertThat(columnFamily.get(key).getValue()).isEqualTo(100L);
      assertThat(longColumnFamily(layeredDb.persistentDb()).get(key)).isNotNull();
      assertThat(longColumnFamily(layeredDb.persistentDb()).get(key).getValue()).isEqualTo(100L);
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
