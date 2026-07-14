/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DefaultZeebeDbFactory;
import io.camunda.zeebe.db.layered.ReadSnapshot;
import io.camunda.zeebe.db.layered.SnapshotSource;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.io.File;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import org.agrona.CloseHelper;
import org.agrona.concurrent.UnsafeBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The pinned snapshot source must expose exactly the committed state at pin time — routed per
 * logical column family with the transactional database's own key encoding — no matter what commits
 * after the pin.
 */
final class RocksDbPinnedSnapshotSourceTest {

  private final ZeebeDbFactory<ColumnFamilies> dbFactory =
      DefaultZeebeDbFactory.getDefaultFactory();

  @TempDir private File dbDirectory;

  private ZeebeTransactionDb<ColumnFamilies> db;
  private TransactionContext context;
  private ColumnFamily<DbLong, DbLong> oneColumnFamily;
  private ColumnFamily<DbLong, DbLong> twoColumnFamily;
  private DbLong key;
  private DbLong value;
  private SnapshotSource source;

  @BeforeEach
  void setUp() {
    db = (ZeebeTransactionDb<ColumnFamilies>) dbFactory.createDb(dbDirectory);
    context = db.createContext();
    key = new DbLong();
    value = new DbLong();
    oneColumnFamily = db.createColumnFamily(ColumnFamilies.ONE, context, key, value);
    twoColumnFamily = db.createColumnFamily(ColumnFamilies.TWO, context, key, value);
    source = db.pinnedSnapshotSource(ColumnFamilies.class);
  }

  @AfterEach
  void tearDown() {
    CloseHelper.quietClose(db);
  }

  @Test
  void shouldReadCommittedStateThroughSnapshot() {
    // given
    upsert(oneColumnFamily, 1, 100);

    // when
    try (final ReadSnapshot snapshot = source.takeSnapshot()) {
      // then
      assertThat(snapshot.get(ColumnFamilies.ONE.name(), keyBytes(1))).isEqualTo(valueBytes(100));
      assertThat(snapshot.get(ColumnFamilies.ONE.name(), keyBytes(2))).isNull();
    }
  }

  @Test
  void shouldNotSeeWritesCommittedAfterPin() {
    // given
    upsert(oneColumnFamily, 1, 100);

    try (final ReadSnapshot snapshot = source.takeSnapshot()) {
      // when overwriting, adding and deleting after the pin
      upsert(oneColumnFamily, 1, 200);
      upsert(oneColumnFamily, 2, 300);

      // then the snapshot still serves the pinned cut
      assertThat(snapshot.get(ColumnFamilies.ONE.name(), keyBytes(1))).isEqualTo(valueBytes(100));
      assertThat(snapshot.get(ColumnFamilies.ONE.name(), keyBytes(2))).isNull();
    }
  }

  @Test
  void shouldKeepServingKeysDeletedAfterPin() {
    // given
    upsert(oneColumnFamily, 1, 100);

    try (final ReadSnapshot snapshot = source.takeSnapshot()) {
      // when
      context.runInTransaction(
          () -> {
            key.wrapLong(1);
            oneColumnFamily.deleteExisting(key);
          });

      // then no ghost: the pinned cut still contains the key
      assertThat(snapshot.get(ColumnFamilies.ONE.name(), keyBytes(1))).isEqualTo(valueBytes(100));
    }
  }

  @Test
  void shouldScanOnlyTheRequestedStore() {
    // given interleaving keys in two column families
    upsert(oneColumnFamily, 1, 100);
    upsert(oneColumnFamily, 2, 200);
    upsert(twoColumnFamily, 1, 999);

    // when
    final List<Long> keys = new ArrayList<>();
    final List<Long> values = new ArrayList<>();
    try (final ReadSnapshot snapshot = source.takeSnapshot()) {
      snapshot.prefixScan(
          ColumnFamilies.ONE.name(),
          new byte[0],
          (keyBytes, valueBytes) -> {
            keys.add(new UnsafeBuffer(keyBytes).getLong(0, ByteOrder.BIG_ENDIAN));
            values.add(new UnsafeBuffer(valueBytes).getLong(0, ByteOrder.BIG_ENDIAN));
          });
    }

    // then only the requested store's entries appear, keys stripped of the routing prefix
    assertThat(keys).containsExactly(1L, 2L);
    assertThat(values).containsExactly(100L, 200L);
  }

  @Test
  void shouldScanWithinKeyPrefix() {
    // given
    upsert(oneColumnFamily, 1, 100);
    upsert(oneColumnFamily, 2, 200);

    // when scanning with the full key of one entry as the prefix
    final List<Long> values = new ArrayList<>();
    try (final ReadSnapshot snapshot = source.takeSnapshot()) {
      snapshot.prefixScan(
          ColumnFamilies.ONE.name(),
          keyBytes(2),
          (keyBytes, valueBytes) ->
              values.add(new UnsafeBuffer(valueBytes).getLong(0, ByteOrder.BIG_ENDIAN)));
    }

    // then
    assertThat(values).containsExactly(200L);
  }

  @Test
  void shouldRejectUnknownStore() {
    try (final ReadSnapshot snapshot = source.takeSnapshot()) {
      assertThatThrownBy(() -> snapshot.get("unknown", keyBytes(1)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("unknown");
    }
  }

  @Test
  void shouldTolerateDoubleClose() {
    // given
    final ReadSnapshot snapshot = source.takeSnapshot();

    // when + then
    snapshot.close();
    snapshot.close();
  }

  private void upsert(
      final ColumnFamily<DbLong, DbLong> columnFamily, final long keyValue, final long valueValue) {
    context.runInTransaction(
        () -> {
          key.wrapLong(keyValue);
          value.wrapLong(valueValue);
          columnFamily.upsert(key, value);
        });
  }

  private static byte[] keyBytes(final long keyValue) {
    return longBytes(keyValue);
  }

  private static byte[] valueBytes(final long valueValue) {
    return longBytes(valueValue);
  }

  private static byte[] longBytes(final long value) {
    final byte[] bytes = new byte[Long.BYTES];
    new UnsafeBuffer(bytes).putLong(0, value, ByteOrder.BIG_ENDIAN);
    return bytes;
  }

  private enum ColumnFamilies implements EnumValue, ScopedColumnFamily {
    DEFAULT,
    ONE,
    TWO;

    @Override
    public int getValue() {
      return ordinal();
    }

    @Override
    public ColumnFamilyScope partitionScope() {
      return ColumnFamilyScope.PARTITION_LOCAL;
    }
  }
}
