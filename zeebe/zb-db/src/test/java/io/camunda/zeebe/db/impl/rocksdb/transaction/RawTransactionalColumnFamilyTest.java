/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.DEFAULT_CACHE_SIZE;
import static io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory.DEFAULT_WRITE_BUFFER_SIZE;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.agrona.collections.MutableReference;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.rocksdb.LRUCache;
import org.rocksdb.RocksDB;
import org.rocksdb.WriteBufferManager;

public class RawTransactionalColumnFamilyTest {
  @TempDir static Path path;
  @AutoClose static ZeebeTransactionDb<ZbColumnFamilies> db;
  static Map<ZbColumnFamilies, RawTransactionalColumnFamily> columnFamilies = new HashMap<>();
  private static TransactionContext context;

  static {
    RocksDB.loadLibrary();
  }

  @BeforeAll
  static void setup() {
    final LRUCache lruCache = new LRUCache(DEFAULT_CACHE_SIZE);
    final int defaultPartitionCount = 3;
    final ZeebeRocksDbFactory<ZbColumnFamilies> factory =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            SimpleMeterRegistry::new,
            lruCache,
            new WriteBufferManager(DEFAULT_WRITE_BUFFER_SIZE, lruCache),
            defaultPartitionCount);
    db = factory.createDb(path.toFile());
    context = db.createContext();

    for (final var cf : ZbColumnFamilies.values()) {
      final var rawCF = new RawTransactionalColumnFamily(db, cf);
      columnFamilies.put(cf, rawCF);
    }
  }

  @ParameterizedTest
  @EnumSource(ZbColumnFamilies.class)
  public void shouldIterateOverAllValues(final ZbColumnFamilies cf) {
    // given
    final int numEntries = 100;
    final var rawCF = columnFamilies.get(cf);
    final var expected = new HashMap<Integer, byte[]>();
    context.runInTransaction(
        () -> {
          for (int i = 0; i < numEntries; i++) {
            // Convert the integer to a byte array so that ordering is preserved in rocksDB.
            // if it's converted to a string, the order is broken (e.g. 10 < 2).
            final var bytes =
                ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(i).array();
            expected.put(i, bytes);
            rawCF.put(
                (ZeebeTransaction) context.getCurrentTransaction(),
                bytes,
                0,
                bytes.length,
                bytes,
                0,
                bytes.length);
          }
        });
    // when
    final var i = new MutableReference<>(0);
    rawCF.forEach(
        context,
        ((rawKey, keyOffset, keyLen, value, valueOffset, valueLen) -> {
          final var cfContext = new ColumnFamilyContext(cf.getValue());
          assertThat(keyOffset).isZero();
          assertThat(valueOffset).isZero();

          cfContext.wrapKeyView(rawKey);
          final var key = new byte[cfContext.getKeyView().capacity()];
          cfContext.getKeyView().getBytes(0, key);

          // then
          assertThat(key).isEqualTo(expected.get(i.get()));
          assertThat(value).isEqualTo(expected.get(i.get()));

          i.set(i.get() + 1);
          return true;
        }));

    // then
    assertThat(i.get()).isEqualTo(numEntries);
  }
}
