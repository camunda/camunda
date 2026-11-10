/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ContainsForeignKeys;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.MutableBoolean;
import org.agrona.collections.MutableReference;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * A column family of the write-behind cache. Writes go to the context's transaction; reads see that
 * transaction's own writes first, then the store.
 *
 * <p>The consistency checks run here, against that same view: the backend lags behind the cache, so
 * its own checks would give wrong answers. A failed check throws the same {@link
 * ZeebeDbInconsistentException} as RocksDB.
 */
final class CachedColumnFamily<
        ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private final WriteBehindStore<ColumnFamilyNames> store;
  private final CachedTransactionContext<ColumnFamilyNames> context;
  private final ColumnFamilyNames columnFamily;
  private final KeyType keyInstance;
  private final ValueType valueInstance;
  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final ExpandableArrayBuffer serializationBuffer = new ExpandableArrayBuffer();

  CachedColumnFamily(
      final WriteBehindStore<ColumnFamilyNames> store,
      final CachedTransactionContext<ColumnFamilyNames> context,
      final ColumnFamilyNames columnFamily,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final ConsistencyChecksSettings consistencyChecksSettings) {
    this.store = store;
    this.context = context;
    this.columnFamily = columnFamily;
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;
    this.consistencyChecksSettings = consistencyChecksSettings;
  }

  @Override
  public void insert(final KeyType key, final ValueType value) {
    inOpenTransaction(
        transaction -> {
          final var cacheKey = cacheKey(key);
          final var bytes = serialize(value);
          assertKeyDoesNotExist(transaction, cacheKey);
          assertForeignKeysExist(transaction, key, value);
          transaction.put(cacheKey, new Op(bytes));
        });
  }

  @Override
  public void update(final KeyType key, final ValueType value) {
    inOpenTransaction(
        transaction -> {
          final var cacheKey = cacheKey(key);
          final var bytes = serialize(value);
          assertKeyExists(transaction, cacheKey);
          assertForeignKeysExist(transaction, key, value);
          transaction.put(cacheKey, new Op(bytes));
        });
  }

  @Override
  public void upsert(final KeyType key, final ValueType value) {
    inOpenTransaction(
        transaction -> {
          final var cacheKey = cacheKey(key);
          final var bytes = serialize(value);
          assertForeignKeysExist(transaction, key, value);
          transaction.put(cacheKey, new Op(bytes));
        });
  }

  @Override
  public @Nullable ValueType get(final KeyType key) {
    final var bytes = read(key);
    if (bytes == null) {
      return null;
    }
    valueInstance.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    return valueInstance;
  }

  @Override
  public @Nullable ValueType get(final KeyType key, final Supplier<ValueType> valueSupplier) {
    final var bytes = read(key);
    if (bytes == null) {
      return null;
    }
    final var value = valueSupplier.get();
    value.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    return value;
  }

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    throw iterationNotSupported();
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    throw iterationNotSupported();
  }

  @Override
  public void whileTrue(
      final @Nullable KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    throw iterationNotSupported();
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    throw iterationNotSupported();
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    throw iterationNotSupported();
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    throw iterationNotSupported();
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix,
      final @Nullable KeyType startAtKey,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    throw iterationNotSupported();
  }

  @Override
  public void whileTrueReverse(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    throw iterationNotSupported();
  }

  @Override
  public void forEachKey(final Consumer<KeyType> consumer) {
    throw iterationNotSupported();
  }

  @Override
  public void whileTrue(final KeyVisitor<KeyType> visitor) {
    throw iterationNotSupported();
  }

  @Override
  public void whileTrue(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    throw iterationNotSupported();
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final Consumer<KeyType> visitor) {
    throw iterationNotSupported();
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final KeyVisitor<KeyType> visitor) {
    throw iterationNotSupported();
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    throw iterationNotSupported();
  }

  @Override
  public void whileTrueReverse(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    throw iterationNotSupported();
  }

  @Override
  public void deleteExisting(final KeyType key) {
    inOpenTransaction(
        transaction -> {
          final var cacheKey = cacheKey(key);
          assertKeyExists(transaction, cacheKey);
          transaction.put(cacheKey, new Op(null));
        });
  }

  @Override
  public void deleteIfExists(final KeyType key) {
    inOpenTransaction(
        transaction -> {
          transaction.put(cacheKey(key), new Op(null));
        });
  }

  @Override
  public boolean exists(final KeyType key) {
    final var exists = new MutableBoolean();
    inOpenTransaction(transaction -> exists.set(exists(transaction, cacheKey(key))));
    return exists.get();
  }

  @Override
  public boolean isEmpty() {
    throw iterationNotSupported();
  }

  @Override
  public long count() {
    throw iterationNotSupported();
  }

  @Override
  public long countEqualPrefix(final DbKey prefix) {
    throw iterationNotSupported();
  }

  @Override
  public ColumnFamilyScope partitionScope() {
    return columnFamily.partitionScope();
  }

  private void inOpenTransaction(final Consumer<CachedTransaction> operation) {
    context.runInTransaction(() -> operation.accept(context.currentTransaction()));
  }

  private byte @Nullable [] read(final KeyType key) {
    final var value = new MutableReference<byte[]>();
    inOpenTransaction(transaction -> value.set(read(transaction, cacheKey(key))));
    return value.get();
  }

  private byte @Nullable [] read(final CachedTransaction transaction, final CacheKey key) {
    final var own = transaction.get(key);
    if (own != null) {
      final var bytes = own.bytes();
      return bytes == null ? null : bytes.clone();
    }
    return store.get(key, context.reader());
  }

  private boolean exists(final CachedTransaction transaction, final CacheKey key) {
    final var own = transaction.get(key);
    return own != null ? own.bytes() != null : store.exists(key, context.reader());
  }

  private void assertKeyDoesNotExist(final CachedTransaction transaction, final CacheKey key) {
    if (consistencyChecksSettings.enablePreconditions() && exists(transaction, key)) {
      throw new ZeebeDbInconsistentException(
          "Key " + keyInstance + " in ColumnFamily " + columnFamily + " already exists");
    }
  }

  private void assertKeyExists(final CachedTransaction transaction, final CacheKey key) {
    if (consistencyChecksSettings.enablePreconditions() && !exists(transaction, key)) {
      throw new ZeebeDbInconsistentException(
          "Key " + keyInstance + " in ColumnFamily " + columnFamily + " does not exist");
    }
  }

  private void assertForeignKeysExist(final CachedTransaction transaction, final Object... keys) {
    if (!consistencyChecksSettings.enableForeignKeyChecks()) {
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
      final CachedTransaction transaction, final DbForeignKey<DbKey> foreignKey) {
    if (foreignKey.shouldSkipCheck()) {
      return;
    }
    switch (foreignKey.match()) {
      case Full -> {
        final var key = new CacheKey((EnumValue) foreignKey.columnFamily(), serialize(foreignKey));
        if (!exists(transaction, key)) {
          throw new ZeebeDbInconsistentException(
              "Foreign key "
                  + foreignKey.inner()
                  + " does not exist in "
                  + foreignKey.columnFamily());
        }
      }
      case Prefix ->
          throw new UnsupportedOperationException(
              "Expected to scan %s for the prefix foreign key %s, but the write-behind cache doesn't support scans yet"
                  .formatted(foreignKey.columnFamily(), foreignKey.inner()));
      default ->
          throw new IllegalStateException("Unknown foreign key match type: " + foreignKey.match());
    }
  }

  private CacheKey cacheKey(final DbKey key) {
    return new CacheKey(columnFamily, serialize(key));
  }

  private byte[] serialize(final BufferWriter writer) {
    final int length = writer.getLength();
    writer.write(serializationBuffer, 0);
    final var bytes = new byte[length];
    serializationBuffer.getBytes(0, bytes);
    return bytes;
  }

  private UnsupportedOperationException iterationNotSupported() {
    return new UnsupportedOperationException(
        "Expected to iterate over "
            + columnFamily
            + ", but the write-behind cache doesn't support scans yet");
  }
}
