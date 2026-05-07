/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.layered;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbTransaction;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.db.impl.inmemory.InMemoryZeebeDb;
import io.camunda.zeebe.db.impl.rocksdb.DbNullKey;
import io.camunda.zeebe.protocol.EnumValue;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Layered ZeebeDb with an active in-memory overlay in front of the persistent RocksDB-backed
 * state.
 *
 * <p>The in-memory layer stores unflushed writes and tombstones only; plain reads do not cache
 * RocksDB entries into heap.
 *
 * <p>Thread-safety between the engine thread and the snapshot-director thread is provided by a
 * {@link ReadWriteLock} that guards only the snapshot/close coordination path:
 *
 * <ul>
 *   <li><b>Write lock</b> — held by {@link #commitActiveAndTombstones} and {@link #close()}.
 *   <li><b>Read lock</b> — held by {@link #createSnapshot} for the entire flush + checkpoint.
 * </ul>
 *
 * <p>Hot-path operations ({@link #isCommittedTombstoned}, column-family get/put) do <b>not</b>
 * acquire any lock. Tombstone reads are safe because {@code committedTombstones} is a {@link
 * java.util.concurrent.ConcurrentSkipListMap}.
 */
public final class LayeredZeebeDb<ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue>
    implements ZeebeDb<ColumnFamilyType> {

  private final ZeebeDb<ColumnFamilyType> activeDb;
  private final ZeebeDb<ColumnFamilyType> persistentDb;
  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final NavigableSet<byte[]> committedTombstones =
      new java.util.concurrent.ConcurrentSkipListSet<>(Arrays::compareUnsigned);
  private final Map<RegisteredColumnFamilyKey, RegisteredColumnFamily> registeredColumnFamilies =
      new ConcurrentHashMap<>();

  /**
   * Guards committed entries (via InMemoryZeebeDb) and committed tombstones. The lock ordering is
   * always {@code stateLock → InMemoryZeebeDb monitor} to avoid deadlocks.
   */
  private final ReadWriteLock stateLock = new ReentrantReadWriteLock();

  private volatile boolean closed;

  public LayeredZeebeDb(
      final ZeebeDb<ColumnFamilyType> activeDb,
      final ZeebeDb<ColumnFamilyType> persistentDb,
      final ConsistencyChecksSettings consistencyChecksSettings) {
    this.activeDb = activeDb;
    this.persistentDb = persistentDb;
    this.consistencyChecksSettings = consistencyChecksSettings;
  }

  ZeebeDb<ColumnFamilyType> activeDb() {
    return activeDb;
  }

  ZeebeDb<ColumnFamilyType> persistentDb() {
    return persistentDb;
  }

  boolean isCommittedTombstoned(final byte[] rawKey) {
    return committedTombstones.contains(rawKey);
  }

  NavigableSet<byte[]> snapshotCommittedTombstones() {
    return snapshotCommittedTombstonesUnlocked();
  }

  /**
   * Atomically commits the active (in-memory) transaction and applies tombstone changes under the
   * write lock. This ensures that the flush cannot observe a state where entries have been
   * committed but tombstones have not yet been updated (or vice versa).
   */
  void commitActiveAndTombstones(
      final ZeebeDbTransaction activeTransaction,
      final NavigableSet<byte[]> pendingTombstones,
      final NavigableSet<byte[]> pendingTombstoneRemovals)
      throws Exception {
    stateLock.writeLock().lock();
    try {
      activeTransaction.commit();
      applyCommittedTombstoneChangesUnlocked(pendingTombstones, pendingTombstoneRemovals);
    } finally {
      stateLock.writeLock().unlock();
    }
  }

  /**
   * Atomically captures both committed entries and committed tombstones under the read lock. Since
   * commits acquire the write lock, this guarantees the flush sees a consistent pair of entries and
   * tombstones — it can never observe entries that should have had their tombstones cleared, or
   * tombstones for entries that have already been re-created.
   */
  FlushSnapshot captureFlushSnapshot() {
    stateLock.readLock().lock();
    try {
      final var inMemoryDb = (InMemoryZeebeDb<?>) activeDb;
      final var entries = inMemoryDb.snapshotCommittedEntries();
      final var tombstones = snapshotCommittedTombstonesUnlocked();
      return new FlushSnapshot(entries, tombstones);
    } finally {
      stateLock.readLock().unlock();
    }
  }

  @Override
  public <KeyType extends DbKey, ValueType extends DbValue>
      ColumnFamily<KeyType, ValueType> createColumnFamily(
          final ColumnFamilyType columnFamily,
          final TransactionContext context,
          final KeyType keyInstance,
          final ValueType valueInstance) {
    final var layeredContext =
        context instanceof final LayeredTransactionContext existingContext
            ? existingContext
            : new LayeredTransactionContext(this, activeDb.createContext(), context);

    final ColumnFamily<KeyType, ValueType> columnFamilyInstance =
        layeredContext.getOrCreateColumnFamily(
            columnFamily,
            keyInstance,
            valueInstance,
            () -> {
              final var activeColumnFamily =
                  activeDb.createColumnFamily(
                      columnFamily, layeredContext.activeContext(), keyInstance, valueInstance);
              final var persistentColumnFamily =
                  persistentDb.createColumnFamily(
                      columnFamily, layeredContext.persistentContext(), keyInstance, valueInstance);

              return new LayeredColumnFamily<>(
                  this,
                  layeredContext,
                  columnFamily,
                  activeColumnFamily,
                  persistentColumnFamily,
                  keyInstance,
                  valueInstance,
                  consistencyChecksSettings);
            });

    registerColumnFamily(columnFamily, keyInstance, valueInstance, columnFamilyInstance);
    return columnFamilyInstance;
  }

  @Override
  public void createSnapshot(final File snapshotDir) {
    // Hold the read lock for the entire snapshot operation — capture, flush to RocksDB,
    // and checkpoint creation. This prevents close() (which acquires the write lock)
    // from freeing native RocksDB resources while the flush is still writing.
    stateLock.readLock().lock();
    try {
      if (closed) {
        return; // DB was closed before we acquired the lock — nothing to snapshot
      }
      final var flushed = LayeredSnapshotFlusher.flush(this);
      persistentDb.createSnapshot(snapshotDir);
      // Tombstone removal only needs to remove the specific set we flushed.
      // Safe under the read lock because committedTombstones is a ConcurrentSkipListMap.
      flushed.forEach(committedTombstones::remove);
    } finally {
      stateLock.readLock().unlock();
    }
  }

  @Override
  public Optional<String> getProperty(final String propertyName) {
    return persistentDb.getProperty(propertyName);
  }

  @Override
  public TransactionContext createContext() {
    return new LayeredTransactionContext(
        this, activeDb.createContext(), persistentDb.createContext());
  }

  @Override
  public boolean isEmpty(final ColumnFamilyType column, final TransactionContext context) {
    return createColumnFamily(column, context, DbNullKey.INSTANCE, DbNil.INSTANCE).isEmpty();
  }

  @Override
  public MeterRegistry getMeterRegistry() {
    return persistentDb.getMeterRegistry();
  }

  @Override
  public void exportMetrics() {
    persistentDb.exportMetrics();
  }

  @Override
  public void close() throws Exception {
    // Acquire the write lock to ensure no concurrent flush capture is in progress.
    // Without this, close() can clear committedData (via activeDb.close()) while
    // captureFlushSnapshot() is iterating it, producing a partial snapshot that
    // loses entries but keeps the processedPosition — causing NPEs during replay.
    stateLock.writeLock().lock();
    try {
      closed = true;
      activeDb.close();
    } finally {
      try {
        persistentDb.close();
      } finally {
        stateLock.writeLock().unlock();
      }
    }
  }

  private NavigableSet<byte[]> snapshotCommittedTombstonesUnlocked() {
    final NavigableSet<byte[]> snapshot = new TreeSet<>(Arrays::compareUnsigned);
    committedTombstones.forEach(key -> snapshot.add(Arrays.copyOf(key, key.length)));
    return snapshot;
  }

  // ---- Internal helpers ----

  private void applyCommittedTombstoneChangesUnlocked(
      final NavigableSet<byte[]> additions, final NavigableSet<byte[]> removals) {
    removals.forEach(committedTombstones::remove);
    additions.forEach(key -> committedTombstones.add(Arrays.copyOf(key, key.length)));
  }

  @SuppressWarnings("unchecked")
  private <ValueType extends DbValue> ValueType newValueInstance(final ValueType valueInstance) {
    return (ValueType) valueInstance.newInstance();
  }

  private <KeyType extends DbKey, ValueType extends DbValue> void registerColumnFamily(
      final ColumnFamilyType columnFamily,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final ColumnFamily<KeyType, ValueType> columnFamilyInstance) {
    synchronized (registeredColumnFamilies) {
      registeredColumnFamilies.putIfAbsent(
          new RegisteredColumnFamilyKey(
              columnFamily.getDeclaringClass(),
              columnFamily.name(),
              keyInstance.getClass(),
              valueInstance.getClass()),
          new RegisteredColumnFamily(
              columnFamily,
              keyInstance.getClass(),
              valueInstance.getClass(),
              (LayeredColumnFamily<?, ?>) columnFamilyInstance));
    }
  }

  record FlushSnapshot(
      ArrayList<Map.Entry<byte[], DbValue>> entries, NavigableSet<byte[]> tombstones) {}

  record RegisteredColumnFamilyKey(
      Class<?> columnFamilyEnumType,
      String columnFamilyName,
      Class<?> keyType,
      Class<?> valueType) {}

  record RegisteredColumnFamily(
      Enum<?> columnFamily, Class<?> keyType, Class<?> valueType, LayeredColumnFamily<?, ?> layer) {
    @SuppressWarnings("unchecked")
    <KeyType extends DbKey, ValueType extends DbValue>
        LayeredColumnFamily<KeyType, ValueType> typed() {
      return (LayeredColumnFamily<KeyType, ValueType>) layer;
    }

    boolean matches(final byte[] rawKey) {
      return typed().matchesColumnFamily(rawKey);
    }
  }
}
