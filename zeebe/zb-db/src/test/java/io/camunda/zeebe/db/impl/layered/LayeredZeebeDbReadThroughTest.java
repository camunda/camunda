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
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LayeredZeebeDbReadThroughTest {

  private final ZeebeDbFactory<DefaultColumnFamily> persistentFactory =
      DefaultZeebeDbFactory.getDefaultFactory();

  @Test
  void shouldReuseSameColumnFamilyInstancePerContextAndTypes(final @TempDir File path)
      throws Exception {
    try (final var layeredDb = openLayeredDb(path)) {
      final var context = layeredDb.createContext();

      final var first =
          layeredDb.createColumnFamily(
              DefaultColumnFamily.DEFAULT, context, new DbLong(), new DbLong());
      final var second =
          layeredDb.createColumnFamily(
              DefaultColumnFamily.DEFAULT, context, new DbLong(), new DbLong());

      assertThat(second).isSameAs(first);
    }
  }

  @Test
  void shouldAllowDifferentTypedViewsForSameColumnFamily(final @TempDir File path)
      throws Exception {
    try (final var layeredDb = openLayeredDb(path)) {
      final var context = layeredDb.createContext();

      final var first =
          layeredDb.createColumnFamily(
              DefaultColumnFamily.DEFAULT, context, new DbLong(), new DbLong());
      final var second =
          layeredDb.createColumnFamily(
              DefaultColumnFamily.DEFAULT, context, new DbLong(), new DbString());

      assertThat(second).isNotSameAs(first);
    }
  }

  @Test
  void shouldServeSubsequentPlainGetsFromActiveLayerAfterReadThrough(final @TempDir File path)
      throws Exception {
    // given
    writePersistentLongValue(path, 1L, 100L);

    try (final var layeredDb = openLayeredDb(path)) {
      final var columnFamily = longColumnFamily(layeredDb);
      final var key = new DbLong();
      key.wrapLong(1L);

      // when
      final var firstResult = columnFamily.get(key);
      deletePersistentLongValue(layeredDb, 1L);
      final var secondResult = columnFamily.get(key);

      // then
      assertThat(firstResult).isNotNull();
      assertThat(firstResult.getValue()).isEqualTo(100L);
      assertThat(secondResult).isNotNull();
      assertThat(secondResult.getValue()).isEqualTo(100L);
    }
  }

  @Test
  void shouldServeSubsequentSupplierGetsFromActiveLayerAfterReadThrough(final @TempDir File path)
      throws Exception {
    // given
    writePersistentLongValue(path, 1L, 100L);

    try (final var layeredDb = openLayeredDb(path)) {
      final var columnFamily = longColumnFamily(layeredDb);
      final var key = new DbLong();
      key.wrapLong(1L);

      // when
      final var firstResult = columnFamily.get(key, DbLong::new);
      deletePersistentLongValue(layeredDb, 1L);
      final var secondResult = columnFamily.get(key, DbLong::new);

      // then
      assertThat(firstResult).isNotNull();
      assertThat(firstResult.getValue()).isEqualTo(100L);
      assertThat(secondResult).isNotNull();
      assertThat(secondResult.getValue()).isEqualTo(100L);
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
      final var key = new DbLong();
      final var dbValue = new DbLong();
      key.wrapLong(keyValue);
      dbValue.wrapLong(value);
      columnFamily.insert(key, dbValue);
    }
  }

  private void deletePersistentLongValue(
      final LayeredZeebeDb<DefaultColumnFamily> layeredDb, final long keyValue) {
    final var columnFamily = longColumnFamily(layeredDb.persistentDb());
    final var key = new DbLong();
    key.wrapLong(keyValue);
    columnFamily.deleteExisting(key);
  }

  private ColumnFamily<DbLong, DbLong> longColumnFamily(final ZeebeDb<DefaultColumnFamily> db) {
    return db.createColumnFamily(
        DefaultColumnFamily.DEFAULT, db.createContext(), new DbLong(), new DbLong());
  }
}
