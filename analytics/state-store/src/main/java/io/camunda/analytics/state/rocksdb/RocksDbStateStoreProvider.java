/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.state.rocksdb;

import io.camunda.analytics.state.api.KeyValueStore;
import io.camunda.analytics.state.api.StateStoreProvider;
import io.camunda.zeebe.db.AccessMetricsConfiguration;
import io.camunda.zeebe.db.AccessMetricsConfiguration.Kind;
import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.db.impl.rocksdb.RocksDbConfiguration;
import io.camunda.zeebe.db.impl.rocksdb.ZeebeRocksDbFactory;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

/**
 * A RocksDB-backed {@link StateStoreProvider} (via ZeebeDb): one RocksDB instance under a
 * directory, one column family per store. Stores are created on first request and cached, so
 * repeated requests for the same column family return the same store.
 *
 * @param <CF> the caller's column-family enum
 */
public final class RocksDbStateStoreProvider<
        CF extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements StateStoreProvider<CF> {

  private final ZeebeDb<CF> zeebeDb;
  private final TransactionContext context;
  private final Map<CF, KeyValueStore<?, ?>> stores = new HashMap<>();

  private RocksDbStateStoreProvider(final ZeebeDb<CF> zeebeDb) {
    this.zeebeDb = zeebeDb;
    context = zeebeDb.createContext();
  }

  /** Opens (creating it and any parents) a RocksDB instance under {@code directory}. */
  public static <CF extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
      RocksDbStateStoreProvider<CF> open(final File directory, final MeterRegistry meterRegistry) {
    try {
      Files.createDirectories(directory.toPath());
    } catch (final IOException e) {
      throw new UncheckedIOException("Failed to create state-store directory " + directory, e);
    }
    final ZeebeDbFactory<CF> factory =
        new ZeebeRocksDbFactory<>(
            new RocksDbConfiguration(),
            new ConsistencyChecksSettings(true, true),
            new AccessMetricsConfiguration(Kind.NONE, 1),
            () -> meterRegistry);
    // avoidFlush=false: this library takes no snapshots, so flush on close for durability.
    return new RocksDbStateStoreProvider<>(factory.createDb(directory, false));
  }

  @Override
  @SuppressWarnings("unchecked")
  public <K extends DbKey, V extends DbValue> KeyValueStore<K, V> keyValueStore(
      final CF columnFamily, final K keyFlyweight, final V valueFlyweight) {
    return (KeyValueStore<K, V>)
        stores.computeIfAbsent(
            columnFamily,
            cf -> {
              final ColumnFamily<K, V> handle =
                  zeebeDb.createColumnFamily(cf, context, keyFlyweight, valueFlyweight);
              return new RocksDbKeyValueStore<>(handle, context);
            });
  }

  @Override
  public void runInTransaction(final Runnable operations) {
    context.runInTransaction(operations::run);
  }

  @Override
  public void close() throws Exception {
    zeebeDb.close();
  }
}
