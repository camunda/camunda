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
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LayeredZeebeDbIterationTest {

  private final ZeebeDbFactory<DefaultColumnFamily> persistentFactory =
      DefaultZeebeDbFactory.getDefaultFactory();

  @Test
  void shouldMergeOverlayWritesDeletesAndCounts(final @TempDir File path) throws Exception {
    // given
    writePersistentLongValue(path, 1L, 10L);
    writePersistentLongValue(path, 2L, 20L);

    try (final var layeredDb = openLayeredDb(path)) {
      final var columnFamily = longColumnFamily(layeredDb);
      columnFamily.upsert(dbLong(2L), dbLong(200L));
      columnFamily.upsert(dbLong(3L), dbLong(300L));
      columnFamily.deleteExisting(dbLong(1L));

      // when
      final List<Long> forwardKeys = new ArrayList<>();
      final List<Long> reverseKeys = new ArrayList<>();
      columnFamily.forEach((key, value) -> forwardKeys.add(key.getValue()));
      columnFamily.whileTrueReverse(
          dbLong(999L),
          (key, value) -> {
            reverseKeys.add(key.getValue());
            return true;
          });

      // then
      assertThat(forwardKeys).containsExactly(2L, 3L);
      assertThat(reverseKeys).containsExactly(3L, 2L);
      assertThat(columnFamily.count()).isEqualTo(2L);
      assertThat(columnFamily.isEmpty()).isFalse();
      assertThat(columnFamily.exists(dbLong(1L))).isFalse();
      assertThat(columnFamily.exists(dbLong(2L))).isTrue();
      assertThat(columnFamily.get(dbLong(2L))).isNotNull();
      assertThat(columnFamily.get(dbLong(2L)).getValue()).isEqualTo(200L);
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
