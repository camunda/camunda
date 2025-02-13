/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionOperation;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.db.impl.DbByte;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.DefaultColumnFamily;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import io.camunda.zeebe.util.ByteValue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.Properties;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompactionPriority;

final class ZeebeRocksDbFactoryTest {

  @Test
  void shouldCreateNewDb(final @TempDir File pathName) throws Exception {
    // given
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory = DefaultZeebeDbFactory.getDefaultFactory();

    // when
    final ZeebeDb<DefaultColumnFamily> db = dbFactory.createDb(pathName);

    // then
    assertThat(pathName).isNotEmptyDirectory();
    db.close();
  }

  @Test
  void shouldCreateTwoNewDbs(final @TempDir File firstPath, final @TempDir File secondPath)
      throws Exception {
    // given
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory = DefaultZeebeDbFactory.getDefaultFactory();

    // when
    final ZeebeDb<DefaultColumnFamily> firstDb = dbFactory.createDb(firstPath);
    final ZeebeDb<DefaultColumnFamily> secondDb = dbFactory.createDb(secondPath);

    // then
    assertThat(firstDb).isNotEqualTo(secondDb);

    assertThat(firstPath).isNotEmptyDirectory();
    assertThat(secondPath).isNotEmptyDirectory();

    firstDb.close();
    secondDb.close();
  }

  @Test
  void shouldOverwriteDefaultColumnFamilyOptions() {
    // given
    final var customProperties = new Properties();
    customProperties.put("write_buffer_size", String.valueOf(ByteValue.ofMegabytes(16)));
    customProperties.put("compaction_pri", "kByCompensatedSize");

    //noinspection unchecked
    final var factoryWithDefaults =
        (ZeebeRocksDbFactory<DefaultColumnFamily>)
            DefaultZeebeDbFactory.<DefaultColumnFamily>getDefaultFactory();
    final var factoryWithCustomOptions =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration().setColumnFamilyOptions(customProperties),
            new ConsistencyChecksSettings(),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            new SimpleMeterRegistry());

    // when
    final var defaults = factoryWithDefaults.createColumnFamilyOptions(new ArrayList<>());
    final var customOptions = factoryWithCustomOptions.createColumnFamilyOptions(new ArrayList<>());

    // then
    assertThat(defaults)
        .extracting(
            ColumnFamilyOptions::writeBufferSize,
            ColumnFamilyOptions::compactionPriority,
            ColumnFamilyOptions::numLevels)
        .containsExactly(50704475L, CompactionPriority.OldestSmallestSeqFirst, 4);

    // user cfg will only be set and all other is rocksdb default
    assertThat(customOptions)
        .extracting(
            ColumnFamilyOptions::writeBufferSize,
            ColumnFamilyOptions::compactionPriority,
            ColumnFamilyOptions::numLevels)
        .containsExactly(ByteValue.ofMegabytes(16), CompactionPriority.ByCompensatedSize, 7);
  }

  @Test
  void shouldFailIfPropertiesDoesNotExist(final @TempDir File pathName) {
    // given
    final var customProperties = new Properties();
    customProperties.put("notExistingProperty", String.valueOf(ByteValue.ofMegabytes(16)));

    final var factoryWithCustomOptions =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration().setColumnFamilyOptions(customProperties),
            new ConsistencyChecksSettings(),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            new SimpleMeterRegistry());

    // expect
    //noinspection resource
    assertThatThrownBy(() -> factoryWithCustomOptions.createDb(pathName))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining(
            "Expected to create column family options for RocksDB, but one or many values are undefined in the context of RocksDB");
  }

  @Test
  void shouldOpenSnapshotOnlyDb(final @TempDir File path, final @TempDir File tempDir)
      throws Exception {
    // given
    final var factory = DefaultZeebeDbFactory.<DefaultColumnFamily>getDefaultFactory();
    final var key = new DbString();
    final var value = new DbString();
    key.wrapString("foo");
    value.wrapString("bar");

    try (final var db = factory.createDb(path)) {
      final var column =
          db.createColumnFamily(
              DefaultColumnFamily.DEFAULT, db.createContext(), new DbString(), new DbString());
      column.insert(key, value);
    }

    // when
    final var snapshotPath = new File(tempDir, "snapshot");
    try (final var db = factory.openSnapshotOnlyDb(path)) {
      db.createSnapshot(snapshotPath);
    }

    // then
    final String snapshotValue;
    try (final var db = factory.createDb(snapshotPath)) {
      final var column =
          db.createColumnFamily(
              DefaultColumnFamily.DEFAULT, db.createContext(), new DbString(), new DbString());
      snapshotValue = column.get(key).toString();
    }

    assertThat(snapshotValue).isEqualTo("bar");
  }

  @Test
  void shouldFailToOpenNonExistentSnapshotOnlyDb(final @TempDir File path) {
    // given
    final var factory = DefaultZeebeDbFactory.getDefaultFactory();
    assertThat(path.delete()).isTrue();

    // when - then
    //noinspection resource
    assertThatThrownBy(() -> factory.openSnapshotOnlyDb(path))
        .isInstanceOf(IllegalStateException.class);
  }

  @ParameterizedTest
  @MethodSource("provideSnapshotOnlyOperation")
  void shouldFailToWriteOnSnapshotOnlyDb(
      final ThrowingConsumer<ZeebeDb<DefaultColumnFamily>> assertions, final @TempDir File dbPath)
      throws Exception {
    // given
    final var factory = DefaultZeebeDbFactory.<DefaultColumnFamily>getDefaultFactory();
    final var key = new DbString();
    final var value = new DbString();
    key.wrapString("foo");
    value.wrapString("bar");

    try (final var db = factory.createDb(dbPath)) {
      final var column =
          db.createColumnFamily(
              DefaultColumnFamily.DEFAULT, db.createContext(), new DbString(), new DbString());
      column.insert(key, value);
    }

    // when - then
    try (final var db = factory.openSnapshotOnlyDb(dbPath)) {
      assertThatCode(() -> assertions.accept(db)).isInstanceOf(UnsupportedOperationException.class);
    }
  }

  private static Stream<Named<ThrowingConsumer<ZeebeDb<DefaultColumnFamily>>>>
      provideSnapshotOnlyOperation() {
    return Stream.of(
        Named.of("createContext", ZeebeDb::createContext),
        Named.of(
            "createColumnFamily",
            db ->
                db.createColumnFamily(
                    DefaultColumnFamily.DEFAULT,
                    new NoOpTransactionContext(),
                    new DbByte(),
                    new DbByte())),
        Named.of(
            "isEmpty", db -> db.isEmpty(DefaultColumnFamily.DEFAULT, new NoOpTransactionContext())),
        Named.of("getProperty", db -> db.getProperty("foo")));
  }

  private static final class NoOpTransactionContext implements TransactionContext {

    @Override
    public void runInTransaction(final TransactionOperation operations) {}

    @Override
    public ZeebeDbTransaction getCurrentTransaction() {
      return null;
    }
  }
}
