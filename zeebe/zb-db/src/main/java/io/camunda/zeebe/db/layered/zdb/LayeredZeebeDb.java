/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.layered.LayeredKeyValueStore;
import io.camunda.zeebe.db.layered.LayeredStoreCoordinator;
import io.camunda.zeebe.db.layered.ReadOnlyView;
import io.camunda.zeebe.db.layered.SnapshotSource;
import io.camunda.zeebe.db.layered.typed.LayeredColumnFamily;
import io.camunda.zeebe.protocol.EnumValue;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * A decorator making the layered state store usable through the existing {@link ZeebeDb} surface,
 * wrapping a real database (the {@code inner} durable store).
 *
 * <p><b>Pass-through by default:</b> every context created via {@link #createContext()} — and every
 * column family created on such a context — behaves exactly like the wrapped database: secondary
 * consumers (exporters writing through their own context, the query service, migrations, backup)
 * are untouched by the layering. Their committed writes are visible to the layered path through its
 * delegate reads.
 *
 * <p><b>The layered path is explicit:</b> {@link #layeredContext()} returns the single owner-thread
 * context whose column families buffer writes in per-column-family {@link LayeredKeyValueStore}s
 * instead of RocksDB. Transaction commit on that context promotes the staged batch in memory —
 * <em>no RocksDB write ever happens on commit</em> — and rollback discards exactly the staged
 * batch. Buffered state reaches the wrapped database only in atomic persist rounds driven through
 * {@link #coordinator()}; {@link #overCapacity()} signals when a round is due.
 *
 * <p><b>Lifecycle:</b> create all layered column families before the first {@link #coordinator()}
 * call — the coordinator captures the store set, so later layered creations throw. {@link #close()}
 * closes the wrapped database.
 *
 * <p><b>Snapshot source:</b> by default views published by the coordinator read the wrapped
 * database <em>without</em> a pinned snapshot (see {@link UnpinnedSnapshotSource} for the exact
 * limitations); wirings with asynchronous readers must inject a pinning {@link SnapshotSource} via
 * the dedicated constructor.
 */
public final class LayeredZeebeDb<ColumnFamilyType extends Enum<ColumnFamilyType> & EnumValue>
    implements ZeebeDb<ColumnFamilyType> {

  private final ZeebeDb<ColumnFamilyType> inner;
  private final LayeredZeebeDbConfig config;
  private final SnapshotSource snapshotSource;
  private final boolean fallbackSnapshots;

  private final LayeredTransactionContext layeredContext;
  private final TransactionContext delegateReadContext;
  private final TransactionContext persistContext;
  private final TransactionContext snapshotReadContext;
  private final InnerPersistSink sink;

  private final Map<ColumnFamilyType, LayeredKeyValueStore> storesByColumnFamily =
      new LinkedHashMap<>();
  private final Map<String, ColumnFamily<DbBytes, DbBytes>> persistColumnFamilies = new HashMap<>();
  private final Map<String, ColumnFamily<DbBytes, DbBytes>> snapshotColumnFamilies =
      new HashMap<>();

  private LayeredStoreCoordinator coordinator;

  /** Creates a facade whose coordinator views read through the unpinned fallback source. */
  public LayeredZeebeDb(final ZeebeDb<ColumnFamilyType> inner, final LayeredZeebeDbConfig config) {
    this(inner, config, null);
  }

  /**
   * Creates a facade whose coordinator views read through the given pinning snapshot source; the
   * caller owns the source's lifecycle.
   */
  public LayeredZeebeDb(
      final ZeebeDb<ColumnFamilyType> inner,
      final LayeredZeebeDbConfig config,
      final SnapshotSource snapshotSource) {
    this.inner = Objects.requireNonNull(inner, "inner");
    this.config = Objects.requireNonNull(config, "config");
    delegateReadContext = inner.createContext();
    persistContext = inner.createContext();
    fallbackSnapshots = snapshotSource == null;
    if (fallbackSnapshots) {
      snapshotReadContext = inner.createContext();
      this.snapshotSource = new UnpinnedSnapshotSource(snapshotColumnFamilies);
    } else {
      snapshotReadContext = null;
      this.snapshotSource = snapshotSource;
    }
    layeredContext = new LayeredTransactionContext(storesByColumnFamily.values());
    sink = new InnerPersistSink(persistContext, persistColumnFamilies);
  }

  // ------------------------------------------------------------------
  // ZeebeDb surface
  // ------------------------------------------------------------------

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyType columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    if (context != layeredContext) {
      return inner.createColumnFamily(columnFamily, context, keyInstance, valueInstance);
    }
    if (coordinator != null) {
      throw new IllegalStateException(
          "expected all layered column families to be created before the coordinator, but '"
              + columnFamily.name()
              + "' was requested after coordinator() was called");
    }
    final LayeredKeyValueStore store =
        storesByColumnFamily.computeIfAbsent(columnFamily, this::createStore);
    return new LayeredTransactionalColumnFamily<>(
        layeredContext, new LayeredColumnFamily<>(store, keyInstance, valueInstance));
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    inner.createSnapshot(snapshotDir);
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    return inner.getProperty(propertyName);
  }

  @Override
  public TransactionContext createContext() {
    return inner.createContext();
  }

  @Override
  public boolean isEmpty(final ColumnFamilyType column, final TransactionContext context) {
    if (context != layeredContext) {
      return inner.isEmpty(column, context);
    }
    final LayeredKeyValueStore store = storesByColumnFamily.get(column);
    if (store == null) {
      // no layered store means no buffered writes; the wrapped database alone answers
      return inner.isEmpty(column, delegateReadContext);
    }
    return new LayeredColumnFamily<>(store, new DbBytes(), new DbBytes()).isEmpty();
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return inner.getMeterRegistry();
  }

  @Override
  public void exportMetrics() {
    inner.exportMetrics();
  }

  @Override
  public void close() throws Exception {
    inner.close();
  }

  // ------------------------------------------------------------------
  // Layered surface (not part of the ZeebeDb interface)
  // ------------------------------------------------------------------

  /**
   * The single owner-thread context of the layered path; every call returns the same instance.
   * Column families created on it buffer writes in memory until a persist round drains them.
   */
  public TransactionContext layeredContext() {
    return layeredContext;
  }

  /**
   * The coordinator driving freezes and persist rounds over all layered stores. Built lazily on the
   * first call, capturing every layered column family created so far — create them all first.
   */
  public LayeredStoreCoordinator coordinator() {
    if (coordinator == null) {
      coordinator =
          new LayeredStoreCoordinator(
              storesByColumnFamily.values(), sink, snapshotSource, view -> {});
    }
    return coordinator;
  }

  /**
   * The coordinator driving freezes and persist rounds, with the given listener receiving every
   * published {@link ReadOnlyView}. Must be the first {@code coordinator} call.
   */
  public LayeredStoreCoordinator coordinator(final Consumer<ReadOnlyView> viewListener) {
    if (coordinator != null) {
      throw new IllegalStateException(
          "expected the view listener to be registered before the coordinator is built,"
              + " but one already exists");
    }
    coordinator =
        new LayeredStoreCoordinator(
            storesByColumnFamily.values(), sink, snapshotSource, viewListener);
    return coordinator;
  }

  /**
   * Whether any layered store's pinned (un-evictable) entries exceed its byte budget — the signal
   * to schedule a persist round now.
   */
  public boolean overCapacity() {
    return storesByColumnFamily.values().stream().anyMatch(LayeredKeyValueStore::overCapacity);
  }

  // ------------------------------------------------------------------
  // Wiring
  // ------------------------------------------------------------------

  private LayeredKeyValueStore createStore(final ColumnFamilyType columnFamily) {
    final String name = columnFamily.name();
    final ColumnFamily<DbBytes, DbBytes> readColumnFamily =
        inner.createColumnFamily(columnFamily, delegateReadContext, new DbBytes(), new DbBytes());
    final ColumnFamily<DbBytes, DbBytes> persistColumnFamily =
        inner.createColumnFamily(columnFamily, persistContext, new DbBytes(), new DbBytes());
    persistColumnFamilies.put(name, persistColumnFamily);
    if (fallbackSnapshots) {
      snapshotColumnFamilies.put(
          name,
          inner.createColumnFamily(
              columnFamily, snapshotReadContext, new DbBytes(), new DbBytes()));
    }
    return new LayeredKeyValueStore(
        name,
        new InnerBytesStore(readColumnFamily, persistColumnFamily, persistContext),
        config.maxBytesPerStore(),
        config.absorbDeletes(),
        config.pipelineSegmentLimit());
  }
}
