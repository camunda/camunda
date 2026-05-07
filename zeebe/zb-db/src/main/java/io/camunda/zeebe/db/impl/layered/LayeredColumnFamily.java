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
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.RawEntryIteratorProvider;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import java.util.NavigableSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

final class LayeredColumnFamily<KeyType extends DbKey, ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private final LayeredZeebeDb<?> db;
  private final LayeredTransactionContext context;
  private final Enum<?> columnFamily;
  private final long columnFamilyPrefix;
  private final ColumnFamily<KeyType, ValueType> activeColumnFamily;
  private final ColumnFamily<KeyType, ValueType> persistentColumnFamily;
  private final RawEntryIteratorProvider activeIteratorProvider;
  private final RawEntryIteratorProvider persistentIteratorProvider;
  private final KeyType keyInstance;
  private final ValueType valueInstance;
  private final ConsistencyChecksSettings consistencyChecksSettings;

  LayeredColumnFamily(
      final LayeredZeebeDb<?> db,
      final LayeredTransactionContext context,
      final Enum<?> columnFamily,
      final ColumnFamily<KeyType, ValueType> activeColumnFamily,
      final ColumnFamily<KeyType, ValueType> persistentColumnFamily,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final ConsistencyChecksSettings consistencyChecksSettings) {
    this.db = db;
    this.context = context;
    this.columnFamily = columnFamily;
    columnFamilyPrefix = ((EnumValue) columnFamily).getValue();
    this.activeColumnFamily = activeColumnFamily;
    this.persistentColumnFamily = persistentColumnFamily;
    activeIteratorProvider = asRawIteratorProvider(activeColumnFamily);
    persistentIteratorProvider = asRawIteratorProvider(persistentColumnFamily);
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;
    this.consistencyChecksSettings = consistencyChecksSettings;
  }

  @Override
  public void insert(final KeyType key, final ValueType value) {
    context.runInTransaction(
        () -> {
          // Only check the active (in-memory) layer for the precondition. After a snapshot
          // recovery the in-memory layer is empty while RocksDB still holds flushed entries.
          // During replay the engine legitimately re-inserts keys that are already in
          // RocksDB, so checking the persistent layer would cause a false-positive.
          if (consistencyChecksSettings.enablePreconditions() && activeColumnFamily.exists(key)) {
            throw new ZeebeDbInconsistentException(
                "Key " + key + " in ColumnFamily " + columnFamily + " already exists");
          }

          context.clearTombstone(serializeKey(key));
          activeColumnFamily.upsert(key, value);
        });
  }

  @Override
  public void update(final KeyType key, final ValueType value) {
    context.runInTransaction(
        () -> {
          if (consistencyChecksSettings.enablePreconditions() && !existsInternal(key)) {
            throw new ZeebeDbInconsistentException(
                "Key " + key + " in ColumnFamily " + columnFamily + " does not exist");
          }

          context.clearTombstone(serializeKey(key));
          activeColumnFamily.upsert(key, value);
        });
  }

  /**
   * In-place mutation for the layered DB. If the entry is already in the active (in-memory) layer,
   * it is mutated directly without any copies. If it is only in the persistent layer, it is
   * read-through into the active layer first, then mutated in-place. If a supplier is provided and
   * the entry does not exist in either layer, a fresh value from the supplier is inserted into the
   * active layer and then mutated.
   */
  @Override
  public <A> @Nullable A updateAndGet(
      final KeyType key,
      final @Nullable Supplier<ValueType> valueSupplier,
      final java.util.function.Function<ValueType, A> mutator) {
    final var result = new Holder<A>();
    context.runInTransaction(
        () -> {
          final var rawKey = serializeKey(key);

          if (context.isTombstoned(rawKey)) {
            if (valueSupplier == null) {
              throw new ZeebeDbInconsistentException(
                  "Key " + key + " in ColumnFamily " + columnFamily + " does not exist");
            }
            context.clearTombstone(rawKey);
            result.value = activeColumnFamily.updateAndGet(key, valueSupplier, mutator);
            return;
          }

          if (!activeColumnFamily.exists(key)) {
            final var persistentValue = persistentColumnFamily.get(key, this::newValueInstance);
            if (persistentValue != null) {
              activeColumnFamily.upsert(key, persistentValue);
            }
          }

          result.value = activeColumnFamily.updateAndGet(key, valueSupplier, mutator);
        });
    return result.value;
  }

  @Override
  public void upsert(final KeyType key, final ValueType value) {
    context.runInTransaction(
        () -> {
          context.clearTombstone(serializeKey(key));
          activeColumnFamily.upsert(key, value);
        });
  }

  @Override
  public @Nullable ValueType get(final KeyType key) {
    final var result = new Holder<ValueType>();
    context.runInTransaction(
        () -> {
          if (context.isTombstoned(serializeKey(key))) {
            return;
          }

          final var activeValue = activeColumnFamily.get(key);
          if (activeValue != null) {
            result.value = activeValue;
            return;
          }

          result.value = persistentColumnFamily.get(key, this::newValueInstance);
        });
    return result.value;
  }

  @Override
  public @Nullable ValueType get(final KeyType key, final Supplier<ValueType> valueSupplier) {
    final var result = new Holder<ValueType>();
    context.runInTransaction(
        () -> {
          final var rawKey = serializeKey(key);
          if (context.isTombstoned(rawKey)) {
            return;
          }

          final var activeValue = activeColumnFamily.get(key, valueSupplier);
          if (activeValue != null) {
            result.value = activeValue;
            return;
          }

          result.value = persistentColumnFamily.get(key, valueSupplier);
        });
    return result.value;
  }

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    whileTrue(
        (key, value) -> {
          consumer.accept(value);
          return true;
        });
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    whileTrue(
        (key, value) -> {
          consumer.accept(key, value);
          return true;
        });
  }

  @Override
  public void whileTrue(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    visitEntries(startAtKey, new io.camunda.zeebe.db.impl.rocksdb.DbNullKey(), false, visitor);
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    visitEntries(null, new io.camunda.zeebe.db.impl.rocksdb.DbNullKey(), false, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(
        keyPrefix,
        (key, value) -> {
          visitor.accept(key, value);
          return true;
        });
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    visitEntries(null, keyPrefix, false, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix,
      final KeyType startAtKey,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    visitEntries(startAtKey, keyPrefix, false, visitor);
  }

  @Override
  public void whileTrueReverse(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    visitEntries(startAtKey, new io.camunda.zeebe.db.impl.rocksdb.DbNullKey(), true, visitor);
  }

  @Override
  public void forEachKey(final Consumer<KeyType> consumer) {
    whileTrue(
        key -> {
          consumer.accept(key);
          return true;
        });
  }

  @Override
  public void whileTrue(final KeyVisitor<KeyType> visitor) {
    visitKeys(null, new io.camunda.zeebe.db.impl.rocksdb.DbNullKey(), false, visitor);
  }

  @Override
  public void whileTrue(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    visitKeys(startAtKey, new io.camunda.zeebe.db.impl.rocksdb.DbNullKey(), false, visitor);
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final Consumer<KeyType> visitor) {
    whileEqualPrefix(
        keyPrefix,
        key -> {
          visitor.accept(key);
          return true;
        });
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final KeyVisitor<KeyType> visitor) {
    visitKeys(null, keyPrefix, false, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    visitKeys(startAtKey, keyPrefix, false, visitor);
  }

  @Override
  public void whileTrueReverse(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    visitKeys(startAtKey, new io.camunda.zeebe.db.impl.rocksdb.DbNullKey(), true, visitor);
  }

  @Override
  public void deleteExisting(final KeyType key) {
    context.runInTransaction(
        () -> {
          if (consistencyChecksSettings.enablePreconditions() && !existsInternal(key)) {
            throw new ZeebeDbInconsistentException(
                "Key " + key + " in ColumnFamily " + columnFamily + " does not exist");
          }

          deleteInternal(key);
        });
  }

  @Override
  public void deleteIfExists(final KeyType key) {
    context.runInTransaction(() -> deleteInternal(key));
  }

  /**
   * Optimized bulk prefix delete that iterates each layer independently instead of doing the
   * expensive merged iteration + per-entry {@link #deleteInternal}. For scopes created after the
   * last snapshot (nothing in RocksDB), the persistent scan returns nothing instantly.
   */
  @Override
  public void deleteByPrefix(final DbKey prefix) {
    context.runInTransaction(
        () -> {
          // 1. Delete all matching entries from the active (in-memory) layer — fast TreeMap
          //    range operation, no RocksDB involved.
          activeColumnFamily.deleteByPrefix(prefix);

          // 2. Single RocksDB prefix scan: add tombstones for any persistent entries.
          //    For newly-created scopes this scan returns nothing instantly.
          persistentColumnFamily.deleteByPrefix(prefix);
        });
  }

  @Override
  public boolean exists(final KeyType key) {
    final boolean[] result = {false};
    context.runInTransaction(() -> result[0] = existsInternal(key));
    return result[0];
  }

  @Override
  public boolean isEmpty() {
    final boolean[] empty = {true};
    context.runInTransaction(
        () -> {
          try (final var iterator =
              newMergedIterator(null, new io.camunda.zeebe.db.impl.rocksdb.DbNullKey(), false)) {
            empty[0] = !iterator.isValid();
          }
        });
    return empty[0];
  }

  @Override
  public long count() {
    return countEntries(new io.camunda.zeebe.db.impl.rocksdb.DbNullKey());
  }

  @Override
  public long countEqualPrefix(final DbKey prefix) {
    return countEntries(prefix);
  }

  @Override
  public ColumnFamilyScope partitionScope() {
    return persistentColumnFamily.partitionScope();
  }

  boolean matchesColumnFamily(final byte[] rawKey) {
    if (rawKey.length < Long.BYTES) {
      return false;
    }

    return new UnsafeBuffer(rawKey).getLong(0, ZeebeDbConstants.ZB_DB_BYTE_ORDER)
        == columnFamilyPrefix;
  }

  void flushActiveToPersistent() {
    activeColumnFamily.forEach((key, value) -> persistentColumnFamily.upsert(key, value));
  }

  void flushTombstonesToPersistent(final NavigableSet<byte[]> tombstones) {
    for (final byte[] tombstone : tombstones) {
      if (!matchesColumnFamily(tombstone)) {
        continue;
      }

      keyInstance.wrap(new UnsafeBuffer(tombstone), Long.BYTES, tombstone.length - Long.BYTES);
      persistentColumnFamily.deleteIfExists(keyInstance);
    }
  }

  private void deleteInternal(final KeyType key) {
    final var rawKey = serializeKey(key);
    final var persistentExists = persistentColumnFamily.exists(key);

    activeColumnFamily.deleteIfExists(key);
    if (persistentExists) {
      context.addTombstone(rawKey);
    } else {
      context.clearTombstone(rawKey);
    }
  }

  private boolean existsInternal(final KeyType key) {
    final var rawKey = serializeKey(key);
    if (context.isTombstoned(rawKey)) {
      return false;
    }

    return activeColumnFamily.exists(key) || persistentColumnFamily.exists(key);
  }

  private void visitEntries(
      final @Nullable DbKey startAt,
      final DbKey prefix,
      final boolean reverse,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    context.runInTransaction(
        () -> {
          try (final var iterator = newMergedIterator(startAt, prefix, reverse)) {
            while (iterator.isValid()) {
              wrapKey(iterator.key());
              iterator.writeValueInto(valueInstance);
              if (!visitor.visit(keyInstance, valueInstance)) {
                break;
              }
              iterator.next();
            }
          }
        });
  }

  private void visitKeys(
      final @Nullable DbKey startAt,
      final DbKey prefix,
      final boolean reverse,
      final KeyVisitor<KeyType> visitor) {
    context.runInTransaction(
        () -> {
          try (final var iterator = newMergedIterator(startAt, prefix, reverse)) {
            while (iterator.isValid()) {
              wrapKey(iterator.key());
              if (!visitor.visit(keyInstance)) {
                break;
              }
              iterator.next();
            }
          }
        });
  }

  private long countEntries(final DbKey prefix) {
    final long[] count = {0};
    context.runInTransaction(
        () -> {
          try (final var iterator = newMergedIterator(null, prefix, false)) {
            while (iterator.isValid()) {
              count[0]++;
              iterator.next();
            }
          }
        });
    return count[0];
  }

  private MergedRawEntryIterator newMergedIterator(
      final @Nullable DbKey startAt, final DbKey prefix, final boolean reverse) {
    return new MergedRawEntryIterator(
        activeIteratorProvider.newRawIterator(startAt, prefix, reverse),
        persistentIteratorProvider.newRawIterator(startAt, prefix, reverse),
        reverse);
  }

  @SuppressWarnings("unchecked")
  private ValueType newValueInstance() {
    return (ValueType) valueInstance.newInstance();
  }

  private void wrapKey(final byte[] rawKey) {
    keyInstance.wrap(new UnsafeBuffer(rawKey), Long.BYTES, rawKey.length - Long.BYTES);
  }

  private byte[] serializeKey(final DbKey key) {
    final int length = Long.BYTES + key.getLength();
    final byte[] result = new byte[length];
    final MutableDirectBuffer buffer = new UnsafeBuffer(result);
    buffer.putLong(0, columnFamilyPrefix, ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    key.write(buffer, Long.BYTES);
    return result;
  }

  private RawEntryIteratorProvider asRawIteratorProvider(
      final ColumnFamily<KeyType, ValueType> columnFamily) {
    if (columnFamily instanceof final RawEntryIteratorProvider provider) {
      return provider;
    }

    throw new IllegalStateException(
        "ColumnFamily " + columnFamily.getClass().getName() + " does not support raw iteration");
  }

  private static final class Holder<T> {
    private @Nullable T value;
  }

  private final class MergedRawEntryIterator implements AutoCloseable {
    private static final int NONE = 0;
    private static final int ACTIVE = 1;
    private static final int PERSISTENT = 2;
    private static final byte[] NO_KEY = new byte[0];

    private final RawEntryIteratorProvider.RawEntryIterator activeIterator;
    private final RawEntryIteratorProvider.RawEntryIterator persistentIterator;
    private final boolean reverse;
    private int currentSource = NONE;
    private byte[] currentKey = NO_KEY;

    private MergedRawEntryIterator(
        final RawEntryIteratorProvider.RawEntryIterator activeIterator,
        final RawEntryIteratorProvider.RawEntryIterator persistentIterator,
        final boolean reverse) {
      this.activeIterator = activeIterator;
      this.persistentIterator = persistentIterator;
      this.reverse = reverse;
      moveToNext();
    }

    private boolean isValid() {
      return currentSource != NONE;
    }

    private byte[] key() {
      return currentKey;
    }

    private void writeValueInto(final DbValue target) {
      switch (currentSource) {
        case ACTIVE -> activeIterator.writeValueInto(target);
        case PERSISTENT -> persistentIterator.writeValueInto(target);
        case NONE -> throw new IllegalStateException("Iterator is exhausted");
      }
    }

    private void next() {
      switch (currentSource) {
        case ACTIVE -> activeIterator.next();
        case PERSISTENT -> persistentIterator.next();
        case NONE -> {
          return;
        }
      }
      moveToNext();
    }

    @Override
    public void close() throws Exception {
      Exception failure = null;
      try {
        activeIterator.close();
      } catch (final Exception e) {
        failure = e;
      }

      try {
        persistentIterator.close();
      } catch (final Exception e) {
        if (failure == null) {
          failure = e;
        } else {
          failure.addSuppressed(e);
        }
      }

      if (failure != null) {
        throw failure;
      }
    }

    private void moveToNext() {
      currentSource = NONE;
      currentKey = NO_KEY;

      while (true) {
        final boolean activeValid = activeIterator.isValid();
        final boolean persistentValid = persistentIterator.isValid();

        if (!activeValid && !persistentValid) {
          return;
        }

        if (!activeValid) {
          final byte[] persistentKey = persistentIterator.key();
          if (context.isTombstoned(persistentKey)) {
            persistentIterator.next();
            continue;
          }
          currentSource = PERSISTENT;
          currentKey = persistentKey;
          return;
        }

        if (!persistentValid) {
          final byte[] activeKey = activeIterator.key();
          if (context.isTombstoned(activeKey)) {
            activeIterator.next();
            continue;
          }
          currentSource = ACTIVE;
          currentKey = activeKey;
          return;
        }

        final byte[] activeKey = activeIterator.key();
        final byte[] persistentKey = persistentIterator.key();
        final int cmp = java.util.Arrays.compareUnsigned(activeKey, persistentKey);
        final int effectiveCmp = reverse ? -cmp : cmp;

        if (effectiveCmp < 0) {
          if (context.isTombstoned(activeKey)) {
            activeIterator.next();
            continue;
          }
          currentSource = ACTIVE;
          currentKey = activeKey;
          return;
        }

        if (effectiveCmp > 0) {
          if (context.isTombstoned(persistentKey)) {
            persistentIterator.next();
            continue;
          }
          currentSource = PERSISTENT;
          currentKey = persistentKey;
          return;
        }

        persistentIterator.next();
        if (context.isTombstoned(activeKey)) {
          activeIterator.next();
          continue;
        }
        currentSource = ACTIVE;
        currentKey = activeKey;
        return;
      }
    }
  }
}
