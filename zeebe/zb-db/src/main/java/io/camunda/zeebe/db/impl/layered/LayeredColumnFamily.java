/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.layered;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.db.UntypedDbValueTarget;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import java.util.Arrays;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.Optional;
import java.util.TreeMap;
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
  private final KeyType keyInstance;
  private final ValueType valueInstance;

  LayeredColumnFamily(
      final LayeredZeebeDb<?> db,
      final LayeredTransactionContext context,
      final Enum<?> columnFamily,
      final ColumnFamily<KeyType, ValueType> activeColumnFamily,
      final ColumnFamily<KeyType, ValueType> persistentColumnFamily,
      final KeyType keyInstance,
      final ValueType valueInstance) {
    this.db = db;
    this.context = context;
    this.columnFamily = columnFamily;
    columnFamilyPrefix = ((EnumValue) columnFamily).getValue();
    this.activeColumnFamily = activeColumnFamily;
    this.persistentColumnFamily = persistentColumnFamily;
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;
  }

  @Override
  public void insert(final KeyType key, final ValueType value) {
    context.runInTransaction(
        () -> {
          if (existsInternal(key)) {
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
          if (!existsInternal(key)) {
            throw new ZeebeDbInconsistentException(
                "Key " + key + " in ColumnFamily " + columnFamily + " does not exist");
          }

          context.clearTombstone(serializeKey(key));
          activeColumnFamily.upsert(key, value);
        });
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
    context.runInTransaction(() -> result.value = getOrLoad(key));
    return result.value;
  }

  @Override
  public @Nullable ValueType get(final KeyType key, final Supplier<ValueType> valueSupplier) {
    final var result = new Holder<ValueType>();
    context.runInTransaction(
        () -> {
          final var rawKey = serializeKey(key);
          if (context.isTombstoned(rawKey)) {
            result.value = null;
            return;
          }

          final var activeValue = activeColumnFamily.get(key, valueSupplier);
          if (activeValue != null) {
            result.value = activeValue;
            return;
          }

          final var persistentValue = persistentColumnFamily.get(key, this::newValueInstance);
          if (persistentValue == null) {
            result.value = null;
            return;
          }

          activeColumnFamily.upsert(key, persistentValue);
          result.value = activeColumnFamily.get(key, valueSupplier);
        });
    return result.value;
  }

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    context.runInTransaction(
        () ->
            mergedEntriesInOrder(
                    new PrefixRange(serializeKey(new io.camunda.zeebe.db.impl.rocksdb.DbNullKey())))
                .forEach(
                    (ignored, value) -> {
                      readValueInto(value, valueInstance);
                      consumer.accept(valueInstance);
                    }));
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
    final var prefixRange =
        new PrefixRange(serializeKey(new io.camunda.zeebe.db.impl.rocksdb.DbNullKey()));
    if (startAtKey == null) {
      visitEntries(mergedEntriesInOrder(prefixRange), visitor);
    } else {
      visitEntries(mergedEntriesInOrder(serializeKey(startAtKey), prefixRange), visitor);
    }
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    final var prefixRange =
        new PrefixRange(serializeKey(new io.camunda.zeebe.db.impl.rocksdb.DbNullKey()));
    visitEntries(mergedEntriesInOrder(prefixRange), visitor);
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
    visitEntries(mergedEntriesInOrder(new PrefixRange(serializeKey(keyPrefix))), visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix,
      final KeyType startAtKey,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    final var prefixRange = new PrefixRange(serializeKey(keyPrefix));
    if (startAtKey == null) {
      visitEntries(mergedEntriesInOrder(prefixRange), visitor);
    } else {
      visitEntries(mergedEntriesInOrder(serializeKey(startAtKey), prefixRange), visitor);
    }
  }

  @Override
  public void whileTrueReverse(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    final var prefixRange =
        new PrefixRange(serializeKey(new io.camunda.zeebe.db.impl.rocksdb.DbNullKey()));
    if (startAtKey == null) {
      visitEntriesReverse(mergedEntriesInOrder(prefixRange), prefixRange, visitor);
    } else {
      visitEntriesReverse(
          mergedEntriesInOrder(prefixRange), serializeKey(startAtKey), prefixRange, visitor);
    }
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
    final var prefixRange =
        new PrefixRange(serializeKey(new io.camunda.zeebe.db.impl.rocksdb.DbNullKey()));
    visitKeys(mergedEntriesInOrder(prefixRange), visitor);
  }

  @Override
  public void whileTrue(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    final var prefixRange =
        new PrefixRange(serializeKey(new io.camunda.zeebe.db.impl.rocksdb.DbNullKey()));
    if (startAtKey == null) {
      visitKeys(mergedEntriesInOrder(prefixRange), visitor);
    } else {
      visitKeys(mergedEntriesInOrder(serializeKey(startAtKey), prefixRange), visitor);
    }
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
    visitKeys(mergedEntriesInOrder(new PrefixRange(serializeKey(keyPrefix))), visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    final var prefixRange = new PrefixRange(serializeKey(keyPrefix));
    if (startAtKey == null) {
      visitKeys(mergedEntriesInOrder(prefixRange), visitor);
    } else {
      visitKeys(mergedEntriesInOrder(serializeKey(startAtKey), prefixRange), visitor);
    }
  }

  @Override
  public void whileTrueReverse(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    final var prefixRange =
        new PrefixRange(serializeKey(new io.camunda.zeebe.db.impl.rocksdb.DbNullKey()));
    if (startAtKey == null) {
      visitKeysReverse(mergedEntriesInOrder(prefixRange), prefixRange, visitor);
    } else {
      visitKeysReverse(
          mergedEntriesInOrder(prefixRange), serializeKey(startAtKey), prefixRange, visitor);
    }
  }

  @Override
  public void deleteExisting(final KeyType key) {
    context.runInTransaction(
        () -> {
          if (!existsInternal(key)) {
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

  @Override
  public boolean exists(final KeyType key) {
    final boolean[] result = {false};
    context.runInTransaction(() -> result[0] = existsInternal(key));
    return result[0];
  }

  @Override
  public boolean isEmpty() {
    return count() == 0;
  }

  @Override
  public long count() {
    final long[] count = {0};
    context.runInTransaction(
        () ->
            count[0] =
                mergedEntriesInOrder(
                        new PrefixRange(
                            serializeKey(new io.camunda.zeebe.db.impl.rocksdb.DbNullKey())))
                    .size());
    return count[0];
  }

  @Override
  public long countEqualPrefix(final DbKey prefix) {
    final long[] count = {0};
    context.runInTransaction(
        () -> count[0] = mergedEntriesInOrder(new PrefixRange(serializeKey(prefix))).size());
    return count[0];
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

  private @Nullable ValueType getOrLoad(final KeyType key) {
    final var rawKey = serializeKey(key);
    if (context.isTombstoned(rawKey)) {
      return null;
    }

    final var activeValue = activeColumnFamily.get(key);
    if (activeValue != null) {
      return activeValue;
    }

    final var persistentValue = persistentColumnFamily.get(key, this::newValueInstance);
    if (persistentValue == null) {
      return null;
    }

    activeColumnFamily.upsert(key, persistentValue);
    return activeColumnFamily.get(key);
  }

  private NavigableMap<byte[], DbValue> mergedEntriesInOrder(final PrefixRange prefixRange) {
    return materializeMergedEntries(prefixRange);
  }

  private NavigableMap<byte[], DbValue> mergedEntriesInOrder(
      final byte[] startAt, final PrefixRange prefixRange) {
    return materializeMergedEntries(prefixRange).tailMap(startAt, true);
  }

  private NavigableMap<byte[], DbValue> materializeMergedEntries(final PrefixRange prefixRange) {
    final NavigableMap<byte[], DbValue> merged = new TreeMap<>(Arrays::compareUnsigned);
    final NavigableSet<byte[]> tombstones = context.visibleTombstones();

    persistentColumnFamily.forEach(
        (key, value) -> {
          final byte[] rawKey = serializeKey(key);
          if (prefixRange.contains(rawKey) && !tombstones.contains(rawKey)) {
            merged.putIfAbsent(rawKey, cloneValue(value));
          }
        });

    activeColumnFamily.forEach(
        (key, value) -> {
          final byte[] rawKey = serializeKey(key);
          if (prefixRange.contains(rawKey) && !tombstones.contains(rawKey)) {
            merged.put(rawKey, cloneValue(value));
          }
        });

    tombstones.forEach(
        rawKey -> {
          if (prefixRange.contains(rawKey)) {
            merged.remove(rawKey);
          }
        });

    return merged;
  }

  private void visitEntries(
      final NavigableMap<byte[], DbValue> entries,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    for (final var entry : entries.entrySet()) {
      wrapKey(entry.getKey());
      readValueInto(entry.getValue(), valueInstance);
      if (!visitor.visit(keyInstance, valueInstance)) {
        break;
      }
    }
  }

  private void visitEntriesReverse(
      final NavigableMap<byte[], DbValue> entries,
      final PrefixRange prefixRange,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    final NavigableMap<byte[], DbValue> ranged =
        prefixRange
            .upperBound()
            .map(upperBound -> entries.subMap(prefixRange.lowerBound(), true, upperBound, false))
            .orElse(entries.tailMap(prefixRange.lowerBound(), true));

    for (final var entry : ranged.descendingMap().entrySet()) {
      wrapKey(entry.getKey());
      readValueInto(entry.getValue(), valueInstance);
      if (!visitor.visit(keyInstance, valueInstance)) {
        break;
      }
    }
  }

  private void visitEntriesReverse(
      final NavigableMap<byte[], DbValue> entries,
      final byte[] startAt,
      final PrefixRange prefixRange,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    final NavigableMap<byte[], DbValue> ranged =
        prefixRange
            .upperBound()
            .map(upperBound -> entries.subMap(prefixRange.lowerBound(), true, upperBound, false))
            .orElse(entries.tailMap(prefixRange.lowerBound(), true));

    for (final var entry : ranged.headMap(startAt, true).descendingMap().entrySet()) {
      wrapKey(entry.getKey());
      readValueInto(entry.getValue(), valueInstance);
      if (!visitor.visit(keyInstance, valueInstance)) {
        break;
      }
    }
  }

  private void visitKeys(
      final NavigableMap<byte[], DbValue> entries, final KeyVisitor<KeyType> visitor) {
    for (final byte[] rawKey : entries.keySet()) {
      wrapKey(rawKey);
      if (!visitor.visit(keyInstance)) {
        break;
      }
    }
  }

  private void visitKeysReverse(
      final NavigableMap<byte[], DbValue> entries,
      final PrefixRange prefixRange,
      final KeyVisitor<KeyType> visitor) {
    final NavigableMap<byte[], DbValue> ranged =
        prefixRange
            .upperBound()
            .map(upperBound -> entries.subMap(prefixRange.lowerBound(), true, upperBound, false))
            .orElse(entries.tailMap(prefixRange.lowerBound(), true));

    for (final byte[] rawKey : ranged.descendingKeySet()) {
      wrapKey(rawKey);
      if (!visitor.visit(keyInstance)) {
        break;
      }
    }
  }

  private void visitKeysReverse(
      final NavigableMap<byte[], DbValue> entries,
      final byte[] startAt,
      final PrefixRange prefixRange,
      final KeyVisitor<KeyType> visitor) {
    final NavigableMap<byte[], DbValue> ranged =
        prefixRange
            .upperBound()
            .map(upperBound -> entries.subMap(prefixRange.lowerBound(), true, upperBound, false))
            .orElse(entries.tailMap(prefixRange.lowerBound(), true));

    for (final byte[] rawKey : ranged.headMap(startAt, true).descendingKeySet()) {
      wrapKey(rawKey);
      if (!visitor.visit(keyInstance)) {
        break;
      }
    }
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

  private void readValueInto(final DbValue stored, final DbValue target) {
    if (target instanceof UntypedDbValueTarget) {
      final int length = stored.getLength();
      final byte[] bytes = new byte[length];
      final MutableDirectBuffer buffer = new UnsafeBuffer(bytes);
      stored.write(buffer, 0);
      target.wrap(buffer, 0, length);
      return;
    }

    stored.copyTo(target);
  }

  private DbValue cloneValue(final ValueType value) {
    final DbValue clone = value.newInstance();
    value.copyTo(clone);
    return clone;
  }

  @SuppressWarnings("unchecked")
  private ValueType newValueInstance() {
    return (ValueType) valueInstance.newInstance();
  }

  private static final class Holder<T> {
    private @Nullable T value;
  }

  private record PrefixRange(byte[] lowerBound) {
    Optional<byte[]> upperBound() {
      final byte[] upper = Arrays.copyOf(lowerBound, lowerBound.length);
      for (int i = upper.length - 1; i >= 0; i--) {
        if ((upper[i] & 0xFF) < 0xFF) {
          upper[i]++;
          return Optional.of(Arrays.copyOf(upper, i + 1));
        }
      }
      return Optional.empty();
    }

    boolean contains(final byte[] rawKey) {
      if (Arrays.compareUnsigned(rawKey, lowerBound) < 0) {
        return false;
      }

      return upperBound().map(upper -> Arrays.compareUnsigned(rawKey, upper) < 0).orElse(true);
    }
  }
}
