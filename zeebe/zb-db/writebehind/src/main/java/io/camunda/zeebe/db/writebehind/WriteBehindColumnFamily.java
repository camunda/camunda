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
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Column family over a write-behind transaction. A write holds a private copy of the value, see
 * {@link Values#hold}, and a read copies into the caller's instance, so the store never shares an
 * object with a caller.
 *
 * <p>Keys are encoded like RocksDB's column families do (the column family as a big-endian long,
 * then the key), so that both order keys in the same way and share the storage format.
 *
 * <p>An instance belongs to one transaction context, which one thread uses at a time, so it reuses
 * its scratch buffers.
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
  private final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();
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
    final boolean begun = context.begin();
    try {
      final var transaction = context.currentTransaction();
      final var rawKey = rawKey(key);
      if (consistencyChecks.enablePreconditions() && transaction.exists(rawKey)) {
        throw new ZeebeDbInconsistentException(
            "Key " + keyInstance + " in ColumnFamily " + columnFamily + " already exists");
      }
      assertForeignKeysExist(transaction, key, value);
      transaction.put(rawKey, Values.hold(value, valueBuffer));
      context.commitIfBegun(begun);
    } finally {
      context.endIfBegun(begun);
    }
  }

  @Override
  public void update(final KeyType key, final ValueType value) {
    final boolean begun = context.begin();
    try {
      final var transaction = context.currentTransaction();
      final var rawKey = rawKey(key);
      assertKeyExists(transaction, rawKey);
      assertForeignKeysExist(transaction, key, value);
      transaction.put(rawKey, Values.hold(value, valueBuffer));
      context.commitIfBegun(begun);
    } finally {
      context.endIfBegun(begun);
    }
  }

  @Override
  public void upsert(final KeyType key, final ValueType value) {
    final boolean begun = context.begin();
    try {
      final var transaction = context.currentTransaction();
      final var rawKey = rawKey(key);
      assertForeignKeysExist(transaction, key, value);
      transaction.put(rawKey, Values.hold(value, valueBuffer));
      context.commitIfBegun(begun);
    } finally {
      context.endIfBegun(begun);
    }
  }

  @Override
  public @Nullable ValueType get(final KeyType key) {
    return read(key, null);
  }

  @Override
  public @Nullable ValueType get(final KeyType key, final Supplier<ValueType> valueSupplier) {
    return read(key, valueSupplier);
  }

  /** Copies the value into a new instance from the supplier, or into the value instance. */
  private @Nullable ValueType read(
      final KeyType key, final @Nullable Supplier<ValueType> valueSupplier) {
    final @Nullable HeldValue held;
    final boolean begun = context.begin();
    try {
      held = context.currentTransaction().get(rawKey(key));
      context.commitIfBegun(begun);
    } finally {
      context.endIfBegun(begun);
    }
    if (held == null) {
      return null;
    }
    final var target = valueSupplier == null ? valueInstance : valueSupplier.get();
    Values.copyInto(held, target);
    return target;
  }

  @Override
  public boolean exists(final KeyType key) {
    final boolean exists;
    final boolean begun = context.begin();
    try {
      exists = context.currentTransaction().exists(rawKey(key));
      context.commitIfBegun(begun);
    } finally {
      context.endIfBegun(begun);
    }
    return exists;
  }

  @Override
  public void deleteExisting(final KeyType key) {
    final boolean begun = context.begin();
    try {
      final var transaction = context.currentTransaction();
      final var rawKey = rawKey(key);
      assertKeyExists(transaction, rawKey);
      transaction.delete(rawKey);
      context.commitIfBegun(begun);
    } finally {
      context.endIfBegun(begun);
    }
  }

  @Override
  public void deleteIfExists(final KeyType key) {
    final boolean begun = context.begin();
    try {
      context.currentTransaction().delete(rawKey(key));
      context.commitIfBegun(begun);
    } finally {
      context.endIfBegun(begun);
    }
  }

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    scan(
        null,
        DbNullKey.INSTANCE,
        false,
        (k, v) -> {
          consumer.accept(v);
          return true;
        },
        null,
        Long.MAX_VALUE);
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    scan(
        null,
        DbNullKey.INSTANCE,
        false,
        (k, v) -> {
          consumer.accept(k, v);
          return true;
        },
        null,
        Long.MAX_VALUE);
  }

  @Override
  public void whileTrue(
      final @Nullable KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(startAtKey, DbNullKey.INSTANCE, false, visitor, null, Long.MAX_VALUE);
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(null, DbNullKey.INSTANCE, false, visitor, null, Long.MAX_VALUE);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    scan(
        null,
        keyPrefix,
        false,
        (k, v) -> {
          visitor.accept(k, v);
          return true;
        },
        null,
        Long.MAX_VALUE);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(null, keyPrefix, false, visitor, null, Long.MAX_VALUE);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix,
      final @Nullable KeyType startAtKey,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(startAtKey, keyPrefix, false, visitor, null, Long.MAX_VALUE);
  }

  @Override
  public void whileTrueReverse(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(startAtKey, DbNullKey.INSTANCE, true, visitor, null, Long.MAX_VALUE);
  }

  @Override
  public void forEachKey(final Consumer<KeyType> consumer) {
    scan(
        null,
        DbNullKey.INSTANCE,
        false,
        null,
        k -> {
          consumer.accept(k);
          return true;
        },
        Long.MAX_VALUE);
  }

  @Override
  public void whileTrue(final KeyVisitor<KeyType> visitor) {
    scan(null, DbNullKey.INSTANCE, false, null, visitor, Long.MAX_VALUE);
  }

  @Override
  public void whileTrue(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    scan(startAtKey, DbNullKey.INSTANCE, false, null, visitor, Long.MAX_VALUE);
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final Consumer<KeyType> visitor) {
    scan(
        null,
        keyPrefix,
        false,
        null,
        k -> {
          visitor.accept(k);
          return true;
        },
        Long.MAX_VALUE);
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final KeyVisitor<KeyType> visitor) {
    scan(null, keyPrefix, false, null, visitor, Long.MAX_VALUE);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    scan(startAtKey, keyPrefix, false, null, visitor, Long.MAX_VALUE);
  }

  @Override
  public void whileTrueReverse(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    scan(startAtKey, DbNullKey.INSTANCE, true, null, visitor, Long.MAX_VALUE);
  }

  @Override
  public boolean isEmpty() {
    return scan(null, DbNullKey.INSTANCE, false, null, null, 1) == 0;
  }

  @Override
  public long count() {
    return countEqualPrefix(DbNullKey.INSTANCE);
  }

  /** Counts raw entries without decoding their keys, like RocksDB's column family does. */
  @Override
  public long countEqualPrefix(final DbKey prefix) {
    return scan(null, prefix, false, null, null, Long.MAX_VALUE);
  }

  @Override
  public ColumnFamilyScope partitionScope() {
    return columnFamily.partitionScope();
  }

  /**
   * Visits the entries whose key starts with {@code prefix}, from {@code startAt} (or the prefix)
   * on, until the visitor returns false or {@code limit} entries were visited. Decodes the key only
   * for a visitor, and the value only for an entry visitor.
   *
   * @return the number of entries visited
   */
  private long scan(
      final @Nullable DbKey startAt,
      final DbKey prefix,
      final boolean reverse,
      final @Nullable KeyValuePairVisitor<KeyType, ValueType> entryVisitor,
      final @Nullable KeyVisitor<KeyType> keyVisitor,
      final long limit) {
    // RocksDB's column family supports two nested iterations only; failing the same way keeps
    // engine code from relying on deeper nesting that works only here
    if (openIterations >= MAX_NESTED_ITERATIONS) {
      throw new IllegalStateException(
          "Currently nested prefix iterations are not supported! This will cause unexpected behavior.");
    }
    openIterations++;
    final boolean begun = context.begin();
    try {
      final long visited =
          scanInTransaction(startAt, prefix, reverse, entryVisitor, keyVisitor, limit);
      context.commitIfBegun(begun);
      return visited;
    } finally {
      context.endIfBegun(begun);
      openIterations--;
    }
  }

  private long scanInTransaction(
      final @Nullable DbKey startAt,
      final DbKey prefix,
      final boolean reverse,
      final @Nullable KeyValuePairVisitor<KeyType, ValueType> entryVisitor,
      final @Nullable KeyVisitor<KeyType> keyVisitor,
      final long limit) {
    final var prefixKey = encode(columnFamily.getValue(), prefix, keyBuffer);
    final var seekTarget =
        startAt == null ? prefixKey : encode(columnFamily.getValue(), startAt, keyBuffer);
    long visited = 0;
    try (final var cursor = context.currentTransaction().newCursor(prefixKey)) {
      if (reverse) {
        cursor.seekForPrev(RawKey.of(seekTarget));
      } else {
        cursor.seek(RawKey.of(seekTarget));
      }
      while (visited < limit && cursor.isValid()) {
        final var key = cursor.key().bytes();
        if (!startsWith(prefixKey, 0, prefixKey.length, key, 0, key.length)) {
          break;
        }
        visited++;
        if (entryVisitor != null) {
          wrapKey(key);
          cursor.readValue(valueInstance);
          if (!entryVisitor.visit(keyInstance, valueInstance)) {
            break;
          }
        } else if (keyVisitor != null) {
          wrapKey(key);
          if (!keyVisitor.visit(keyInstance)) {
            break;
          }
        }
        cursor.advance();
      }
    }
    return visited;
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
      final WriteBehindTransaction transaction, final DbKey key, final DbValue value) {
    if (!consistencyChecks.enableForeignKeyChecks()) {
      return;
    }
    assertForeignKeysExist(transaction, key);
    assertForeignKeysExist(transaction, value);
  }

  private void assertForeignKeysExist(
      final WriteBehindTransaction transaction, final Object keyOrValue) {
    if (keyOrValue instanceof final ContainsForeignKeys containsForeignKeys) {
      for (final var foreignKey : containsForeignKeys.containedForeignKeys()) {
        assertForeignKeyExists(transaction, foreignKey);
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
      cursor.seek(RawKey.copyOf(prefix));
      if (!cursor.isValid()) {
        return false;
      }
      final var key = cursor.key().bytes();
      return startsWith(prefix, 0, prefix.length, key, 0, key.length);
    }
  }

  private RawKey rawKey(final DbKey key) {
    return RawKey.of(encode(columnFamily.getValue(), key, keyBuffer));
  }

  /** The raw key: the column family as a long, then the key. A new array, sized exactly. */
  private static byte[] encode(
      final long columnFamilyPrefix, final DbKey key, final ExpandableArrayBuffer buffer) {
    buffer.putLong(0, columnFamilyPrefix, ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    final var bytes = new byte[Long.BYTES + key.write(buffer, Long.BYTES)];
    buffer.getBytes(0, bytes);
    return bytes;
  }
}
