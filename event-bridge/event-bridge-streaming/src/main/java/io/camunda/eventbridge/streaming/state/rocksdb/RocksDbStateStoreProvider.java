/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.state.rocksdb;

import io.camunda.eventbridge.streaming.state.api.KeyValueStore;
import io.camunda.eventbridge.streaming.state.api.StateStoreProvider;
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
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A RocksDB-backed {@link StateStoreProvider} (via ZeebeDb): one RocksDB instance under a
 * directory, one column family per store. Stores are created on first request and cached, so
 * repeated requests for the same column family return the same store.
 *
 * <p>The provider exports RocksDB's property metrics (tombstone counts in the memtables, live-data
 * vs. SST-file sizes, memtable sizes, pending compaction, ...) to the given {@link MeterRegistry},
 * tagged with {@code store=<directory name>} and refreshed on a modest schedule. These are the
 * numbers that prove or disprove tombstone accumulation in a store.
 *
 * @param <CF> the caller's column-family enum
 */
public final class RocksDbStateStoreProvider<
        CF extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily>
    implements StateStoreProvider<CF> {

  private static final Logger LOG = LoggerFactory.getLogger(RocksDbStateStoreProvider.class);

  /** How often the RocksDB property gauges are refreshed. */
  private static final Duration METRICS_POLL_INTERVAL = Duration.ofSeconds(10);

  private final ZeebeDb<CF> zeebeDb;
  private final TransactionContext context;
  private final ScheduledExecutorService metricsPoller;
  private final Map<CF, KeyValueStore<?, ?>> stores = new HashMap<>();

  private RocksDbStateStoreProvider(final ZeebeDb<CF> zeebeDb, final String storeName) {
    this.zeebeDb = zeebeDb;
    context = zeebeDb.createContext();
    // Take an eager first snapshot so the gauges exist as soon as the provider is open, then
    // refresh them periodically; each snapshot is a handful of cheap property reads.
    zeebeDb.exportMetrics();
    metricsPoller =
        Executors.newSingleThreadScheduledExecutor(
            runnable -> {
              final Thread thread = new Thread(runnable, "state-store-metrics-" + storeName);
              thread.setDaemon(true);
              return thread;
            });
    metricsPoller.scheduleAtFixedRate(
        this::refreshMetrics,
        METRICS_POLL_INTERVAL.toMillis(),
        METRICS_POLL_INTERVAL.toMillis(),
        TimeUnit.MILLISECONDS);
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
            // ZeebeDb owns — and closes — the registry its factory supplies, so hand it a
            // composite wrapping the caller's registry: the DB's gauges land in the caller's
            // registry tagged by store, and closing the DB removes them again without closing
            // the caller's registry.
            () -> storeRegistry(meterRegistry, directory.getName()));
    // avoidFlush=false: this library takes no snapshots, so flush on close for durability.
    return new RocksDbStateStoreProvider<>(factory.createDb(directory, false), directory.getName());
  }

  private static MeterRegistry storeRegistry(final MeterRegistry parent, final String storeName) {
    final var registry = new CompositeMeterRegistry(parent.config().clock());
    registry.config().commonTags(Tags.of("store", storeName));
    registry.add(parent);
    return registry;
  }

  private void refreshMetrics() {
    try {
      zeebeDb.exportMetrics();
    } catch (final Exception e) {
      // never let a failed snapshot kill the poller; the next tick simply tries again
      LOG.debug("Failed to refresh RocksDB state-store metrics", e);
    }
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
    // stop the poller before closing the DB: a property read on a closed native handle is unsafe
    metricsPoller.shutdownNow();
    metricsPoller.awaitTermination(5, TimeUnit.SECONDS);
    zeebeDb.close();
  }
}
