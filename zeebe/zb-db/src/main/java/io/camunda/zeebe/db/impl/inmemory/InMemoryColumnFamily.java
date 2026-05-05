/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.inmemory;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ColumnFamilyMetrics;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ContainsForeignKeys;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.UntypedDbValueTarget;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import io.camunda.zeebe.db.impl.inmemory.InMemoryTransactionContext.InMemoryTransaction;
import io.camunda.zeebe.db.impl.rocksdb.DbNullKey;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.camunda.zeebe.util.Copyable;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * In-memory implementation of {@link ColumnFamily}.
 *
 * <p><b>Values are stored as Java objects</b> (via {@link Copyable#copyTo(Object)}}) — no
 * serialization on the write path. Keys are serialized to {@code byte[]} for correct lexicographic
 * ordering.
 *
 * <p>On read, the stored copy is serialized into the shared {@code valueInstance} via the existing
 * {@link io.camunda.zeebe.util.buffer.BufferWriter}/{@link
 * io.camunda.zeebe.util.buffer.BufferReader} contract. This is unavoidable given the
 * mutable-instance API, but is a pure in-memory operation.
 */
class InMemoryColumnFamily<
        ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private final InMemoryZeebeDb<ColumnFamilyNames> db;
  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final ColumnFamilyNames columnFamily;
  private final TransactionContext context;
  private final KeyType keyInstance;
  private final ValueType valueInstance;
  private final ColumnFamilyMetrics metrics;
  private final long columnFamilyPrefix;

  InMemoryColumnFamily(
      final InMemoryZeebeDb<ColumnFamilyNames> db,
      final ConsistencyChecksSettings consistencyChecksSettings,
      final ColumnFamilyNames columnFamily,
      final TransactionContext context,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final ColumnFamilyMetrics metrics) {
    this.db = db;
    this.consistencyChecksSettings = consistencyChecksSettings;
    this.columnFamily = columnFamily;
    this.context = context;
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;
    this.metrics = metrics;
    columnFamilyPrefix = columnFamily.getValue();
  }

  // ---- Mutating operations ----

  @Override
  public void insert(final KeyType key, final ValueType value) {
    try (final var timer = metrics.measurePutLatency()) {
      ensureInOpenTransaction(
          transaction -> {
            final byte[] rawKey = serializeKey(key);
            if (consistencyChecksSettings.enablePreconditions()
                && transaction.get(rawKey) != null) {
              throw new ZeebeDbInconsistentException(
                  "Key " + key + " in ColumnFamily " + columnFamily + " already exists");
            }
            assertForeignKeysExist(transaction, key, value);
            transaction.put(rawKey, value);
          });
    }
  }

  @Override
  public void update(final KeyType key, final ValueType value) {
    try (final var timer = metrics.measurePutLatency()) {
      ensureInOpenTransaction(
          transaction -> {
            final byte[] rawKey = serializeKey(key);
            if (consistencyChecksSettings.enablePreconditions()
                && transaction.get(rawKey) == null) {
              throw new ZeebeDbInconsistentException(
                  "Key " + key + " in ColumnFamily " + columnFamily + " does not exist");
            }
            assertForeignKeysExist(transaction, key, value);
            transaction.put(rawKey, value);
          });
    }
  }

  @Override
  public void upsert(final KeyType key, final ValueType value) {
    try (final var timer = metrics.measurePutLatency()) {
      ensureInOpenTransaction(
          transaction -> {
            assertForeignKeysExist(transaction, key, value);
            transaction.put(serializeKey(key), value);
          });
    }
  }

  @Override
  public @Nullable ValueType get(final KeyType key) {
    try (final var timer = metrics.measureGetLatency()) {
      final @Nullable DbValue[] result = {null};
      ensureInOpenTransaction(transaction -> result[0] = transaction.get(serializeKey(key)));
      if (result[0] != null) {
        readValueInto(result[0]);
        return valueInstance;
      }
      return null;
    }
  }

  @Override
  public @Nullable ValueType get(final KeyType key, final Supplier<ValueType> valueSupplier) {
    try (final var timer = metrics.measureGetLatency()) {
      final @Nullable DbValue[] result = {null};
      ensureInOpenTransaction(transaction -> result[0] = transaction.get(serializeKey(key)));
      if (result[0] != null) {
        final var newValue = valueSupplier.get();
        readValueInto(result[0], newValue);
        return newValue;
      }
      return null;
    }
  }

  // ---- Iteration ----

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    forEachInPrefix(
        new DbNullKey(),
        (k, v) -> {
          consumer.accept(v);
          return true;
        });
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    forEachInPrefix(
        new DbNullKey(),
        (k, v) -> {
          consumer.accept(k, v);
          return true;
        });
  }

  @Override
  public void whileTrue(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    forEachInPrefix(startAtKey, new DbNullKey(), visitor);
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    forEachInPrefix(new DbNullKey(), visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    forEachInPrefix(
        keyPrefix,
        (k, v) -> {
          visitor.accept(k, v);
          return true;
        });
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    forEachInPrefix(keyPrefix, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix,
      final KeyType startAtKey,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    forEachInPrefix(startAtKey, keyPrefix, visitor);
  }

  @Override
  public void whileTrueReverse(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    forEachInPrefixReverse(startAtKey, new DbNullKey(), visitor);
  }

  // ---- Key-only iteration ----

  @Override
  public void forEachKey(final Consumer<KeyType> consumer) {
    forEachKeyInPrefix(
        new DbNullKey(),
        k -> {
          consumer.accept(k);
          return true;
        });
  }

  @Override
  public void whileTrue(final KeyVisitor<KeyType> visitor) {
    forEachKeyInPrefix(new DbNullKey(), visitor);
  }

  @Override
  public void whileTrue(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    forEachKeyInPrefix(startAtKey, new DbNullKey(), visitor);
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final Consumer<KeyType> visitor) {
    forEachKeyInPrefix(
        keyPrefix,
        k -> {
          visitor.accept(k);
          return true;
        });
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final KeyVisitor<KeyType> visitor) {
    forEachKeyInPrefix(keyPrefix, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    forEachKeyInPrefix(startAtKey, keyPrefix, visitor);
  }

  @Override
  public void whileTrueReverse(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    forEachKeyInPrefixReverse(startAtKey, new DbNullKey(), visitor);
  }

  // ---- Delete ----

  @Override
  public void deleteExisting(final KeyType key) {
    try (final var timer = metrics.measureDeleteLatency()) {
      ensureInOpenTransaction(
          transaction -> {
            final byte[] rawKey = serializeKey(key);
            if (consistencyChecksSettings.enablePreconditions()
                && transaction.get(rawKey) == null) {
              throw new ZeebeDbInconsistentException(
                  "Key " + key + " in ColumnFamily " + columnFamily + " does not exist");
            }
            transaction.delete(rawKey);
          });
    }
  }

  @Override
  public void deleteIfExists(final KeyType key) {
    try (final var timer = metrics.measureDeleteLatency()) {
      ensureInOpenTransaction(transaction -> transaction.delete(serializeKey(key)));
    }
  }

  @Override
  public boolean exists(final KeyType key) {
    try (final var timer = metrics.measureGetLatency()) {
      final boolean[] result = {false};
      ensureInOpenTransaction(
          transaction -> result[0] = transaction.get(serializeKey(key)) != null);
      return result[0];
    }
  }

  @Override
  public boolean isEmpty() {
    final boolean[] empty = {true};
    forEachKeyInPrefix(
        new DbNullKey(),
        k -> {
          empty[0] = false;
          return false;
        });
    return empty[0];
  }

  @Override
  public long count() {
    final long[] c = {0};
    forEachKeyInPrefix(
        new DbNullKey(),
        k -> {
          c[0]++;
          return true;
        });
    return c[0];
  }

  @Override
  public long countEqualPrefix(final DbKey prefix) {
    final long[] c = {0};
    forEachKeyInPrefix(
        prefix,
        k -> {
          c[0]++;
          return true;
        });
    return c[0];
  }

  @Override
  public ColumnFamilyScope partitionScope() {
    return columnFamily.partitionScope();
  }

  // ---- Internal: key serialization (keys are always byte[]) ----

  byte[] serializeKey(final DbKey key) {
    final int length = Long.BYTES + key.getLength();
    final byte[] result = new byte[length];
    final MutableDirectBuffer buffer = new UnsafeBuffer(result);
    buffer.putLong(0, columnFamilyPrefix, ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    key.write(buffer, Long.BYTES);
    return result;
  }

  /**
   * Populates {@code valueInstance} from a stored {@link DbValue} copy using {@link
   * DbValue#copyTo}. Zero serialization for types that override it; falls back to the default
   * serialize+wrap for types that don't.
   */
  private void readValueInto(final DbValue stored) {
    readValueInto(stored, valueInstance);
  }

  /** Like {@link #readValueInto} but targets an arbitrary instance. */
  private void readValueInto(final DbValue stored, final DbValue target) {
    if (target instanceof UntypedDbValueTarget) {
      // Generic readers such as EngineRule.VersatileBlob are not the same concrete type as the
      // stored value. Fall back to serialization + wrap explicitly for those cases.
      final int length = stored.getLength();
      final byte[] bytes = new byte[length];
      final MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
      stored.write(buffer, 0);
      target.wrap(buffer, 0, length);
      return;
    }

    stored.copyTo(target);
  }

  private byte[] computePrefix(final DbKey prefix) {
    return serializeKey(prefix);
  }

  private Optional<byte[]> computePrefixUpperBound(final byte[] prefix) {
    final byte[] upper = Arrays.copyOf(prefix, prefix.length);
    for (int i = upper.length - 1; i >= 0; i--) {
      if ((upper[i] & 0xFF) < 0xFF) {
        upper[i]++;
        return Optional.of(Arrays.copyOf(upper, i + 1));
      }
    }
    return Optional.empty();
  }

  // ---- Internal: iteration core ----

  private void forEachInPrefix(
      final DbKey prefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    forEachInPrefix(prefix, prefix, visitor);
  }

  private void forEachInPrefix(
      final DbKey startAt,
      final DbKey prefix,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    try (final var timer = metrics.measureIterateLatency()) {
      ensureInOpenTransaction(
          transaction -> {
            final byte[] prefixBytes = computePrefix(prefix);
            final byte[] startBytes = serializeKey(startAt == null ? prefix : startAt);
            final Optional<byte[]> upperBound = computePrefixUpperBound(prefixBytes);
            final var it =
                createMergeIterator(transaction, startBytes, prefixBytes, upperBound, false);
            while (it.hasNext()) {
              final var entry = it.next();
              readValueInto(entry.getValue());
              if (!visitKeyValue(entry.getKey(), visitor)) {
                break;
              }
            }
          });
    }
  }

  private void forEachInPrefixReverse(
      final DbKey startAt,
      final DbKey prefix,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    try (final var timer = metrics.measureIterateLatency()) {
      ensureInOpenTransaction(
          transaction -> {
            final byte[] prefixBytes = computePrefix(prefix);
            final byte[] startBytes = serializeKey(startAt == null ? prefix : startAt);
            final Optional<byte[]> upperBound = computePrefixUpperBound(prefixBytes);
            final var it =
                createMergeIterator(transaction, startBytes, prefixBytes, upperBound, true);
            while (it.hasNext()) {
              final var entry = it.next();
              readValueInto(entry.getValue());
              if (!visitKeyValue(entry.getKey(), visitor)) {
                break;
              }
            }
          });
    }
  }

  private void forEachKeyInPrefix(final DbKey prefix, final KeyVisitor<KeyType> visitor) {
    forEachKeyInPrefix(prefix, prefix, visitor);
  }

  private void forEachKeyInPrefix(
      final DbKey startAt, final DbKey prefix, final KeyVisitor<KeyType> visitor) {
    try (final var timer = metrics.measureIterateLatency()) {
      ensureInOpenTransaction(
          transaction -> {
            final byte[] prefixBytes = computePrefix(prefix);
            final byte[] startBytes = serializeKey(startAt == null ? prefix : startAt);
            final Optional<byte[]> upperBound = computePrefixUpperBound(prefixBytes);
            final var it =
                createMergeIterator(transaction, startBytes, prefixBytes, upperBound, false);
            while (it.hasNext()) {
              final var entry = it.next();
              if (!visitKeyOnly(entry.getKey(), visitor)) {
                break;
              }
            }
          });
    }
  }

  private void forEachKeyInPrefixReverse(
      final @Nullable DbKey startAt, final DbKey prefix, final KeyVisitor<KeyType> visitor) {
    try (final var timer = metrics.measureIterateLatency()) {
      ensureInOpenTransaction(
          transaction -> {
            final byte[] prefixBytes = computePrefix(prefix);
            final byte[] startBytes = serializeKey(startAt == null ? prefix : startAt);
            final Optional<byte[]> upperBound = computePrefixUpperBound(prefixBytes);
            final var it =
                createMergeIterator(transaction, startBytes, prefixBytes, upperBound, true);
            while (it.hasNext()) {
              final var entry = it.next();
              if (!visitKeyOnly(entry.getKey(), visitor)) {
                break;
              }
            }
          });
    }
  }

  // ---- Internal: merge iterator ----

  private Iterator<Map.Entry<byte[], DbValue>> createMergeIterator(
      final InMemoryTransaction transaction,
      final byte[] startBytes,
      final byte[] prefixBytes,
      final Optional<byte[]> upperBound,
      final boolean reverse) {

    final NavigableMap<byte[], DbValue> committedRange;
    if (reverse) {
      committedRange =
          upperBound.isPresent()
              ? new java.util.TreeMap<>(
                  db.committedData.subMap(prefixBytes, true, startBytes, true).descendingMap())
              : new java.util.TreeMap<>(
                  db.committedData
                      .tailMap(prefixBytes, true)
                      .headMap(startBytes, true)
                      .descendingMap());
    } else {
      committedRange =
          upperBound.isPresent()
              ? new java.util.TreeMap<>(
                  db.committedData.subMap(startBytes, true, upperBound.orElseThrow(), false))
              : new java.util.TreeMap<>(db.committedData.tailMap(startBytes, true));
    }

    final NavigableMap<byte[], DbValue> allPending = transaction.getPendingWrites();
    final NavigableMap<byte[], DbValue> pendingRange;
    if (reverse) {
      pendingRange =
          upperBound.isPresent()
              ? new java.util.TreeMap<>(
                  allPending.subMap(prefixBytes, true, startBytes, true).descendingMap())
              : new java.util.TreeMap<>(
                  allPending.tailMap(prefixBytes, true).headMap(startBytes, true).descendingMap());
    } else {
      pendingRange =
          upperBound.isPresent()
              ? new java.util.TreeMap<>(
                  allPending.subMap(startBytes, true, upperBound.orElseThrow(), false))
              : new java.util.TreeMap<>(allPending.tailMap(startBytes, true));
    }

    return new MergeIterator(
        committedRange.entrySet().iterator(),
        pendingRange.entrySet().iterator(),
        transaction.getPendingDeletes(),
        prefixBytes,
        reverse);
  }

  private boolean visitKeyValue(
      final byte[] rawKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    final var keyBuffer = new UnsafeBuffer(rawKey, Long.BYTES, rawKey.length - Long.BYTES);
    keyInstance.wrap(keyBuffer, 0, keyBuffer.capacity());
    return visitor.visit(keyInstance, valueInstance);
  }

  // ---- Internal: visiting ----

  private boolean visitKeyOnly(final byte[] rawKey, final KeyVisitor<KeyType> visitor) {
    final var keyBuffer = new UnsafeBuffer(rawKey, Long.BYTES, rawKey.length - Long.BYTES);
    keyInstance.wrap(keyBuffer, 0, keyBuffer.capacity());
    return visitor.visit(keyInstance);
  }

  private void ensureInOpenTransaction(final TransactionConsumer operation) {
    context.runInTransaction(
        () -> operation.run((InMemoryTransaction) context.getCurrentTransaction()));
  }

  // ---- Internal: transaction management ----

  private void assertForeignKeysExist(final InMemoryTransaction transaction, final Object... keys) {
    if (!consistencyChecksSettings.enableForeignKeyChecks()) {
      return;
    }
    for (final var key : keys) {
      if (key instanceof final ContainsForeignKeys c) {
        for (final var fk : c.containedForeignKeys()) {
          assertForeignKeyExists(transaction, fk);
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  private void assertForeignKeyExists(
      final InMemoryTransaction transaction, final DbForeignKey<DbKey> foreignKey) {
    if (foreignKey.shouldSkipCheck()) {
      return;
    }
    final var cfEnum = (ColumnFamilyNames) foreignKey.columnFamily();
    final long fkCfPrefix = cfEnum.getValue();
    final MutableDirectBuffer buffer = new ExpandableArrayBuffer();
    buffer.putLong(0, fkCfPrefix, ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    foreignKey.inner().write(buffer, Long.BYTES);
    final int length = Long.BYTES + foreignKey.inner().getLength();
    final byte[] rawFkKey = new byte[length];
    buffer.getBytes(0, rawFkKey);

    switch (foreignKey.match()) {
      case Full -> {
        if (transaction.get(rawFkKey) == null) {
          throw new ZeebeDbInconsistentException(
              "Foreign key " + foreignKey.inner() + " in ColumnFamily " + cfEnum + " not found");
        }
      }
      case Prefix -> {
        final Optional<byte[]> upperBound = computePrefixUpperBound(rawFkKey);
        boolean found =
            findAnyWithPrefix(transaction.getPendingWrites(), rawFkKey, upperBound, transaction);
        if (!found) {
          found = findAnyWithPrefix(db.committedData, rawFkKey, upperBound, transaction);
        }
        if (!found) {
          throw new ZeebeDbInconsistentException(
              "Foreign key prefix "
                  + foreignKey.inner()
                  + " in ColumnFamily "
                  + cfEnum
                  + " not found");
        }
      }
    }
  }

  // ---- Internal: foreign key checks ----

  private boolean findAnyWithPrefix(
      final NavigableMap<byte[], ?> map,
      final byte[] prefix,
      final Optional<byte[]> upperBound,
      final InMemoryTransaction transaction) {
    final var range =
        upperBound.isPresent()
            ? map.subMap(prefix, true, upperBound.orElseThrow(), false)
            : map.tailMap(prefix, true);
    for (final var entry : range.entrySet()) {
      if (!startsWith(entry.getKey(), prefix)) {
        break;
      }
      if (!transaction.isDeleted(entry.getKey())) {
        return true;
      }
    }
    return false;
  }

  private static boolean startsWith(final byte[] data, final byte[] prefix) {
    if (data.length < prefix.length) {
      return false;
    }
    for (int i = 0; i < prefix.length; i++) {
      if (data[i] != prefix[i]) {
        return false;
      }
    }
    return true;
  }

  private static final class MergeIterator implements Iterator<Map.Entry<byte[], DbValue>> {
    private final Iterator<Map.Entry<byte[], DbValue>> committedIt;
    private final Iterator<Map.Entry<byte[], DbValue>> pendingIt;
    private final java.util.Set<byte[]> pendingDeletes;
    private final byte[] prefixBytes;
    private final boolean reverse;
    private @Nullable Entry<byte[], DbValue> nextCommitted;
    private @Nullable Entry<byte[], DbValue> nextPending;
    private @Nullable Entry<byte[], DbValue> nextResult;

    MergeIterator(
        final Iterator<Map.Entry<byte[], DbValue>> committedIt,
        final Iterator<Map.Entry<byte[], DbValue>> pendingIt,
        final java.util.Set<byte[]> pendingDeletes,
        final byte[] prefixBytes,
        final boolean reverse) {
      this.committedIt = committedIt;
      this.pendingIt = pendingIt;
      this.pendingDeletes = pendingDeletes;
      this.prefixBytes = prefixBytes;
      this.reverse = reverse;
      nextCommitted = advance(committedIt);
      nextPending = advance(pendingIt);
      nextResult = computeNext();
    }

    @Override
    public boolean hasNext() {
      return nextResult != null;
    }

    @Override
    public Map.Entry<byte[], DbValue> next() {
      final var result = nextResult;
      if (result == null) {
        throw new NoSuchElementException();
      }
      nextResult = computeNext();
      return result;
    }

    private @Nullable Entry<byte[], DbValue> computeNext() {
      while (true) {
        if (nextCommitted == null && nextPending == null) {
          return null;
        }
        final Map.Entry<byte[], DbValue> chosen;
        if (nextCommitted == null) {
          chosen = java.util.Objects.requireNonNull(nextPending);
          nextPending = advance(pendingIt);
        } else if (nextPending == null) {
          chosen = java.util.Objects.requireNonNull(nextCommitted);
          nextCommitted = advance(committedIt);
        } else {
          final int cmp = Arrays.compareUnsigned(nextCommitted.getKey(), nextPending.getKey());
          final int effectiveCmp = reverse ? -cmp : cmp;
          if (effectiveCmp < 0) {
            chosen = java.util.Objects.requireNonNull(nextCommitted);
            nextCommitted = advance(committedIt);
          } else if (effectiveCmp > 0) {
            chosen = java.util.Objects.requireNonNull(nextPending);
            nextPending = advance(pendingIt);
          } else {
            chosen = java.util.Objects.requireNonNull(nextPending);
            nextPending = advance(pendingIt);
            nextCommitted = advance(committedIt);
          }
        }
        if (isDeleted(chosen.getKey())) {
          continue;
        }
        if (!startsWith(chosen.getKey(), prefixBytes)) {
          if (reverse) {
            continue;
          } else {
            return null;
          }
        }
        return chosen;
      }
    }

    private boolean isDeleted(final byte[] key) {
      for (final byte[] d : pendingDeletes) {
        if (Arrays.equals(d, key)) {
          return true;
        }
      }
      return false;
    }

    private static @Nullable Entry<byte[], DbValue> advance(
        final Iterator<Map.Entry<byte[], DbValue>> it) {
      return it.hasNext() ? it.next() : null;
    }

    private static boolean startsWith(final byte[] data, final byte[] prefix) {
      if (data.length < prefix.length) {
        return false;
      }
      for (int i = 0; i < prefix.length; i++) {
        if (data[i] != prefix[i]) {
          return false;
        }
      }
      return true;
    }
  }

  @FunctionalInterface
  private interface TransactionConsumer {
    void run(InMemoryTransaction transaction) throws Exception;
  }
}
