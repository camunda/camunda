/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb;

import static io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.DEFAULT_CACHE_SIZE;
import static io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.DEFAULT_WRITE_BUFFER_SIZE;
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
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration.MemoryAllocationStrategy;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.SharedRocksDbResources;
import io.camunda.zeebe.util.ByteValue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.Properties;
import java.util.stream.Stream;
import org.assertj.core.api.ThrowingConsumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.rocksdb.ColumnFamilyOptions;
import org.rocksdb.CompactionPriority;
import org.rocksdb.LRUCache;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteBufferManager;

final class ZeebeRocksDbFactoryTest {

  static {
    RocksDB.loadLibrary();
  }

  private SharedRocksDbResources sharedRocksDbResources;
  private DefaultZeebeDbFactory.ZeebeDbFactoryResources<DefaultColumnFamily> dbFactoryResources;

  @BeforeEach
  void setUp() {
    dbFactoryResources = DefaultZeebeDbFactory.getDefaultFactoryResources();
    final LRUCache lruCache = new LRUCache(DEFAULT_CACHE_SIZE);
    sharedRocksDbResources =
        new SharedRocksDbResources(
            lruCache,
            new WriteBufferManager(DEFAULT_WRITE_BUFFER_SIZE, lruCache),
            DEFAULT_CACHE_SIZE);
  }

  @AfterEach
  void tearDown() {
    if (sharedRocksDbResources != null) {
      sharedRocksDbResources.close();
    }
    if (dbFactoryResources != null) {
      dbFactoryResources.close();
    }
  }

  @Test
  void shouldCreateNewDb(final @TempDir File pathName) throws Exception {
    // given
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory = dbFactoryResources.factory;

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
    final ZeebeDbFactory<DefaultColumnFamily> dbFactory = dbFactoryResources.factory;

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
  void shouldMergeUserOptionsWithDefaultsInsteadOfOverwriting() {
    // given
    final var customProperties = new Properties();
    customProperties.put("write_buffer_size", String.valueOf(ByteValue.ofMegabytes(16)));
    customProperties.put("compaction_pri", "kByCompensatedSize");

    final var factoryWithDefaults =
        (ZeebeRocksDbFactory<DefaultColumnFamily>) dbFactoryResources.factory;

    final int defaultPartitionCount = 3;
    final var factoryWithCustomOptions =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration().setColumnFamilyOptions(customProperties),
            new ConsistencyChecksSettings(),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new,
            sharedRocksDbResources,
            defaultPartitionCount);

    // when
    final var defaults = factoryWithDefaults.createColumnFamilyOptions(new ArrayList<>());
    final var customOptions = factoryWithCustomOptions.createColumnFamilyOptions(new ArrayList<>());

    // then - defaults should be preserved
    assertThat(defaults)
        .extracting(
            ColumnFamilyOptions::writeBufferSize,
            ColumnFamilyOptions::compactionPriority,
            ColumnFamilyOptions::numLevels)
        .containsExactly(50_704_475L, CompactionPriority.OldestSmallestSeqFirst, 4);

    // then - user options should override defaults
    assertThat(customOptions)
        .extracting(
            ColumnFamilyOptions::writeBufferSize,
            ColumnFamilyOptions::compactionPriority,
            ColumnFamilyOptions::numLevels)
        // numLevels is not overridden, so the default of 4 should remain
        .containsExactly(ByteValue.ofMegabytes(16), CompactionPriority.ByCompensatedSize, 4);
  }

  @Test
  void shouldCreateDbWithExpectedOptions() {
    // given
    final var factoryWithDefaults =
        (ZeebeRocksDbFactory<DefaultColumnFamily>) dbFactoryResources.factory;

    // then - options should match our defaults
    validateDefaultExpectedOptions(
        factoryWithDefaults.createColumnFamilyOptions(new ArrayList<>()));
  }

  @Test
  void shouldHaveDefaultsIfPerBrokerMemoryAllocationStrategy() {
    // when configuring with per-broker memory allocation strategy
    final RocksDbConfiguration rocksDbConfiguration = new RocksDbConfiguration();
    rocksDbConfiguration.setMemoryAllocationStrategy(MemoryAllocationStrategy.BROKER);

    // adjust memory limit to be per broker
    final int defaultPartitionCount = 3;
    rocksDbConfiguration.setMemoryLimit(
        rocksDbConfiguration.getMemoryLimit() * defaultPartitionCount);

    final var factoryWithCustomOptions =
        new ZeebeRocksDbFactory<>(
            rocksDbConfiguration,
            new ConsistencyChecksSettings(),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new,
            sharedRocksDbResources,
            defaultPartitionCount);

    // then - options should match our defaults
    // we expect the same options regardless of the memory allocation strategy
    // as long as the memory limit per partition is the same.
    validateDefaultExpectedOptions(
        factoryWithCustomOptions.createColumnFamilyOptions(new ArrayList<>()));
  }

  @Test
  void shouldFailIfPropertiesDoesNotExist(final @TempDir File pathName) {
    // given
    final var customProperties = new Properties();
    customProperties.put("notExistingProperty", String.valueOf(ByteValue.ofMegabytes(16)));

    final int defaultPartitionCount = 3;
    final var factoryWithCustomOptions =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration().setColumnFamilyOptions(customProperties),
            new ConsistencyChecksSettings(),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new,
            sharedRocksDbResources,
            defaultPartitionCount);

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
    final var factory = dbFactoryResources.factory;
    final var key = new DbString();
    final var value = new DbString();
    key.wrapString("foo");
    value.wrapString("bar");

    try (final var db = factory.createDb(path, false)) {
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
    final var factory = dbFactoryResources.factory;
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
    final var factory = dbFactoryResources.factory;
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

  private static void validateDefaultExpectedOptions(
      final ColumnFamilyOptions columnFamilyOptions) {

    // column family options match our defaults
    assertThat(columnFamilyOptions.memtablePrefixBloomSizeRatio()).isEqualTo(0.15);
    assertThat(columnFamilyOptions.minWriteBufferNumberToMerge()).isEqualTo(3);
    assertThat(columnFamilyOptions.maxWriteBufferNumber()).isEqualTo(6);
    assertThat(columnFamilyOptions.writeBufferSize()).isEqualTo(50_704_475L);
    assertThat(columnFamilyOptions.compactionPriority())
        .isEqualTo(CompactionPriority.OldestSmallestSeqFirst);
    assertThat(columnFamilyOptions.compactionStyle()).isEqualTo(org.rocksdb.CompactionStyle.LEVEL);
    assertThat(columnFamilyOptions.level0FileNumCompactionTrigger()).isEqualTo(6);
    assertThat(columnFamilyOptions.level0SlowdownWritesTrigger()).isEqualTo(9);
    assertThat(columnFamilyOptions.level0StopWritesTrigger()).isEqualTo(12);
    assertThat(columnFamilyOptions.numLevels()).isEqualTo(4);
    assertThat(columnFamilyOptions.maxBytesForLevelBase()).isEqualTo(33_554_432L);
    assertThat(columnFamilyOptions.maxBytesForLevelMultiplier()).isEqualTo(10.0);
    assertThat(columnFamilyOptions.compressionPerLevel())
        .containsExactly(
            org.rocksdb.CompressionType.NO_COMPRESSION,
            org.rocksdb.CompressionType.NO_COMPRESSION,
            org.rocksdb.CompressionType.LZ4_COMPRESSION,
            org.rocksdb.CompressionType.LZ4_COMPRESSION);
    assertThat(columnFamilyOptions.targetFileSizeBase()).isEqualTo(8 * 1_024 * 1_024L);
    assertThat(columnFamilyOptions.targetFileSizeMultiplier()).isEqualTo(2);

    // table config matches our defaults
    final var tableConfig =
        (org.rocksdb.BlockBasedTableConfig) columnFamilyOptions.tableFormatConfig();
    assertThat(tableConfig.blockSize()).isEqualTo(32 * 1_024L);
    assertThat(tableConfig.formatVersion()).isEqualTo(5);
    assertThat(tableConfig.cacheIndexAndFilterBlocks()).isTrue();
    assertThat(tableConfig.pinL0FilterAndIndexBlocksInCache()).isTrue();
    assertThat(tableConfig.cacheIndexAndFilterBlocksWithHighPriority()).isTrue();
    assertThat(tableConfig.indexType()).isEqualTo(org.rocksdb.IndexType.kHashSearch);
    assertThat(tableConfig.dataBlockIndexType())
        .isEqualTo(org.rocksdb.DataBlockIndexType.kDataBlockBinaryAndHash);
    assertThat(tableConfig.dataBlockHashTableUtilRatio()).isEqualTo(0.75);
    assertThat(tableConfig.wholeKeyFiltering()).isTrue();

    columnFamilyOptions.close();
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
