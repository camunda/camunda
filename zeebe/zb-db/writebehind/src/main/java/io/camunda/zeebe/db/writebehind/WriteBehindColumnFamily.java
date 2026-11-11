/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import static io.camunda.zeebe.util.buffer.BufferUtil.startsWith;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ContainsForeignKeys;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbForeignKey.MatchType;
import io.camunda.zeebe.db.impl.DbNullKey;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Column family over a write-behind transaction. Values stay objects: a write stores a private copy
 * and a read copies into the caller's instance, so a value is encoded only by the writer thread and
 * decoded only when it is read from the storage.
 *
 * <p>Keys are encoded like RocksDB's column families do (the column family as a big-endian long,
 * then the key), so that both order keys in the same way and share the storage format.
 */
final class WriteBehindColumnFamily<
        ColumnFamilyType extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private static final int MAX_NESTED_ITERATIONS = 2;

  private final ColumnFamilyType columnFamily;
  private final WriteBehindTransactionContext context;
  private final ConsistencyChecksSettings consistencyChecks;
  private final KeyType keyInstance;
  private final ValueType valueInstance;
  private final ExpandableArrayBuffer keyBuffer = new ExpandableArrayBuffer();
  private final UnsafeBuffer keyView = new UnsafeBuffer(0, 0);
  private int openIterations;

  WriteBehindColumnFamily(
      final ColumnFamilyType columnFamily,
      final WriteBehindTransactionContext context,
      final ConsistencyChecksSettings consistencyChecks,
      final KeyType keyInstance,
      final ValueType valueInstance) {
    this.columnFamily = columnFamily;
    this.context = context;
    this.consistencyChecks = consistencyChecks;
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;
  }

  @Override
  public void insert(final KeyType key, final ValueType value) {
    inTransaction(
        transaction -> {
          final var rawKey = rawKey(key);
          if (consistencyChecks.enablePreconditions() && transaction.exists(rawKey)) {
            throw new ZeebeDbInconsistentException(
                "Key " + keyInstance + " in ColumnFamily " + columnFamily + " already exists");
          }
          assertForeignKeysExist(transaction, key, value);
          transaction.put(rawKey, Values.copyOf(value));
        });
  }

  @Override
  public void update(final KeyType key, final ValueType value) {
    inTransaction(
        transaction -> {
          final var rawKey = rawKey(key);
          assertKeyExists(transaction, rawKey);
          assertForeignKeysExist(transaction, key, value);
          transaction.put(rawKey, Values.copyOf(value));
        });
  }

  @Override
  public void upsert(final KeyType key, final ValueType value) {
    inTransaction(
        transaction -> {
          final var rawKey = rawKey(key);
          assertForeignKeysExist(transaction, key, value);
          transaction.put(rawKey, Values.copyOf(value));
        });
  }

  @Override
  public @Nullable ValueType get(final KeyType key) {
    return get(key, () -> valueInstance);
  }

  @Override
  public @Nullable ValueType get(final KeyType key, final Supplier<ValueType> valueSupplier) {
    final @Nullable DbValue[] stored = new DbValue[1];
    inTransaction(transaction -> stored[0] = transaction.get(rawKey(key), valueInstance));
    if (stored[0] == null) {
      return null;
    }
    final var target = valueSupplier.get();
    Values.copy(stored[0], target);
    return target;
  }

  @Override
  public boolean exists(final KeyType key) {
    return readInTransaction(transaction -> transaction.exists(rawKey(key)));
  }

  @Override
  public void deleteExisting(final KeyType key) {
    inTransaction(
        transaction -> {
          final var rawKey = rawKey(key);
          assertKeyExists(transaction, rawKey);
          transaction.delete(rawKey);
        });
  }

  @Override
  public void deleteIfExists(final KeyType key) {
    inTransaction(transaction -> transaction.delete(rawKey(key)));
  }

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    visitEntries(
        null,
        DbNullKey.INSTANCE,
        false,
        (k, v) -> {
          consumer.accept(v);
          return true;
        });
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    visitEntries(
        null,
        DbNullKey.INSTANCE,
        false,
        (k, v) -> {
          consumer.accept(k, v);
          return true;
        });
  }

  @Override
  public void whileTrue(
      final @Nullable KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    visitEntries(startAtKey, DbNullKey.INSTANCE, false, visitor);
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    visitEntries(null, DbNullKey.INSTANCE, false, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    visitEntries(
        null,
        keyPrefix,
        false,
        (k, v) -> {
          visitor.accept(k, v);
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
      final @Nullable KeyType startAtKey,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    visitEntries(startAtKey, keyPrefix, false, visitor);
  }

  @Override
  public void whileTrueReverse(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    visitEntries(startAtKey, DbNullKey.INSTANCE, true, visitor);
  }

  @Override
  public void forEachKey(final Consumer<KeyType> consumer) {
    visitKeys(
        null,
        DbNullKey.INSTANCE,
        false,
        k -> {
          consumer.accept(k);
          return true;
        });
  }

  @Override
  public void whileTrue(final KeyVisitor<KeyType> visitor) {
    visitKeys(null, DbNullKey.INSTANCE, false, visitor);
  }

  @Override
  public void whileTrue(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    visitKeys(startAtKey, DbNullKey.INSTANCE, false, visitor);
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final Consumer<KeyType> visitor) {
    visitKeys(
        null,
        keyPrefix,
        false,
        k -> {
          visitor.accept(k);
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
    visitKeys(startAtKey, DbNullKey.INSTANCE, true, visitor);
  }

  @Override
  public boolean isEmpty() {
    final boolean[] empty = {true};
    iterate(
        null,
        DbNullKey.INSTANCE,
        false,
        cursor -> {
          empty[0] = false;
          return false;
        });
    return empty[0];
  }

  @Override
  public long count() {
    return countEqualPrefix(DbNullKey.INSTANCE);
  }

  /** Counts raw entries without decoding their keys, like RocksDB's column family does. */
  @Override
  public long countEqualPrefix(final DbKey prefix) {
    final long[] count = {0};
    iterate(
        null,
        prefix,
        false,
        cursor -> {
          count[0]++;
          return true;
        });
    return count[0];
  }

  @Override
  public ColumnFamilyScope partitionScope() {
    return columnFamily.partitionScope();
  }

  private void visitEntries(
      final @Nullable DbKey startAt,
      final DbKey prefix,
      final boolean reverse,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    iterate(
        startAt,
        prefix,
        reverse,
        cursor -> {
          wrapKey(cursor.key());
          // false if a concurrent commit deleted the entry; it is no longer there to visit
          return !cursor.readValue(valueInstance) || visitor.visit(keyInstance, valueInstance);
        });
  }

  private void visitKeys(
      final @Nullable DbKey startAt,
      final DbKey prefix,
      final boolean reverse,
      final KeyVisitor<KeyType> visitor) {
    iterate(
        startAt,
        prefix,
        reverse,
        cursor -> {
          wrapKey(cursor.key());
          return visitor.visit(keyInstance);
        });
  }

  /**
   * Visits the entries whose key starts with {@code prefix}, from {@code startAt} (or the prefix)
   * on, until the visitor returns false.
   */
  private void iterate(
      final @Nullable DbKey startAt,
      final DbKey prefix,
      final boolean reverse,
      final Predicate<MergingCursor> visitor) {
    // RocksDB's column family supports two nested iterations only; failing the same way keeps
    // engine code from relying on deeper nesting that works only here
    if (openIterations >= MAX_NESTED_ITERATIONS) {
      throw new IllegalStateException(
          "Currently nested prefix iterations are not supported! This will cause unexpected behavior.");
    }
    openIterations++;
    try {
      iterateInTransaction(startAt, prefix, reverse, visitor);
    } finally {
      openIterations--;
    }
  }

  private void iterateInTransaction(
      final @Nullable DbKey startAt,
      final DbKey prefix,
      final boolean reverse,
      final Predicate<MergingCursor> visitor) {
    inTransaction(
        transaction -> {
          transaction.prepareScan(columnFamily.getValue());
          final var prefixKey = encode(prefix);
          final var seekTarget = encode(Objects.requireNonNullElse(startAt, prefix));
          try (final var cursor = transaction.newCursor(prefixKey)) {
            if (reverse) {
              cursor.seekForPrev(seekTarget);
            } else {
              cursor.seek(seekTarget);
            }
            while (cursor.isValid()) {
              final var key = cursor.key();
              if (!startsWith(prefixKey, 0, prefixKey.length, key, 0, key.length)
                  || !visitor.test(cursor)) {
                return;
              }
              cursor.advance();
            }
          }
        });
  }

  private void wrapKey(final byte[] rawKey) {
    // a copy, as the key instance may keep a view of it
    final var bytes = rawKey.clone();
    keyView.wrap(bytes, Long.BYTES, bytes.length - Long.BYTES);
    keyInstance.wrap(keyView, 0, keyView.capacity());
  }

  private void assertKeyExists(final WriteBehindTransaction transaction, final RawKey rawKey) {
    if (consistencyChecks.enablePreconditions() && !transaction.exists(rawKey)) {
      throw new ZeebeDbInconsistentException(
          "Key " + keyInstance + " in ColumnFamily " + columnFamily + " does not exist");
    }
  }

  private void assertForeignKeysExist(
      final WriteBehindTransaction transaction, final Object... keys) {
    if (!consistencyChecks.enableForeignKeyChecks()) {
      return;
    }
    for (final var key : keys) {
      if (key instanceof final ContainsForeignKeys containsForeignKeys) {
        for (final var foreignKey : containsForeignKeys.containedForeignKeys()) {
          assertForeignKeyExists(transaction, foreignKey);
        }
      }
    }
  }

  private void assertForeignKeyExists(
      final WriteBehindTransaction transaction, final DbForeignKey<DbKey> foreignKey) {
    if (foreignKey.shouldSkipCheck()) {
      return;
    }
    final var rawKey =
        encode(((EnumValue) foreignKey.columnFamily()).getValue(), foreignKey, keyBuffer);
    final boolean exists =
        foreignKey.match() == MatchType.Full
            ? transaction.exists(RawKey.of(rawKey))
            : prefixExists(transaction, rawKey);
    if (!exists) {
      throw new ZeebeDbInconsistentException(
          "Foreign key "
              + foreignKey.inner()
              + " does not exist"
              + (foreignKey.match() == MatchType.Prefix ? " as prefix" : "")
              + " in "
              + foreignKey.columnFamily());
    }
  }

  private static boolean prefixExists(
      final WriteBehindTransaction transaction, final byte[] prefix) {
    try (final var cursor = transaction.newCursor(prefix)) {
      cursor.seek(prefix);
      if (!cursor.isValid()) {
        return false;
      }
      final var key = cursor.key();
      return startsWith(prefix, 0, prefix.length, key, 0, key.length);
    }
  }

  private RawKey rawKey(final DbKey key) {
    return RawKey.of(encode(columnFamily.getValue(), key, keyBuffer));
  }

  private byte[] encode(final DbKey key) {
    return encode(columnFamily.getValue(), key, new ExpandableArrayBuffer());
  }

  private static byte[] encode(
      final long columnFamilyPrefix, final DbKey key, final ExpandableArrayBuffer buffer) {
    buffer.putLong(0, columnFamilyPrefix, ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    final var bytes = new byte[Long.BYTES + key.write(buffer, Long.BYTES)];
    buffer.getBytes(0, bytes);
    return bytes;
  }

  private void inTransaction(final Consumer<WriteBehindTransaction> operation) {
    context.runInTransaction(() -> operation.accept(context.currentTransaction()));
  }

  private <T> T readInTransaction(final Function<WriteBehindTransaction, T> operation) {
    final var result = new Object[1];
    context.runInTransaction(() -> result[0] = operation.apply(context.currentTransaction()));
    @SuppressWarnings("unchecked")
    final T value = (T) result[0];
    return value;
  }
}
