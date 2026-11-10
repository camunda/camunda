/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ContainsForeignKeys;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.cache.PassThroughColumnFamilies.ScanVisitor;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Arrays;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.collections.MutableBoolean;
import org.agrona.collections.MutableLong;
import org.agrona.collections.MutableReference;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * A column family of the write-behind cache. Writes go to the context's transaction; reads see that
 * transaction's own writes first, then the store.
 *
 * <p>Scans merge the backend's scan with the same view; see {@link #scan}.
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

  private static final byte[] NO_PREFIX = new byte[0];

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
    scanEntries(
        NO_PREFIX,
        null,
        false,
        (key, value) -> {
          consumer.accept(value);
          return true;
        });
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    scanEntries(
        NO_PREFIX,
        null,
        false,
        (key, value) -> {
          consumer.accept(key, value);
          return true;
        });
  }

  @Override
  public void whileTrue(
      final @Nullable KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scanEntries(NO_PREFIX, startAtKey, false, visitor);
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scanEntries(NO_PREFIX, null, false, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    scanEntries(
        serialize(keyPrefix),
        null,
        false,
        (key, value) -> {
          visitor.accept(key, value);
          return true;
        });
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scanEntries(serialize(keyPrefix), null, false, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix,
      final @Nullable KeyType startAtKey,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scanEntries(serialize(keyPrefix), startAtKey, false, visitor);
  }

  @Override
  public void whileTrueReverse(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scanEntries(NO_PREFIX, startAtKey, true, visitor);
  }

  @Override
  public void forEachKey(final Consumer<KeyType> consumer) {
    scanKeys(
        NO_PREFIX,
        null,
        false,
        key -> {
          consumer.accept(key);
          return true;
        });
  }

  @Override
  public void whileTrue(final KeyVisitor<KeyType> visitor) {
    scanKeys(NO_PREFIX, null, false, visitor);
  }

  @Override
  public void whileTrue(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    scanKeys(NO_PREFIX, startAtKey, false, visitor);
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final Consumer<KeyType> visitor) {
    scanKeys(
        serialize(keyPrefix),
        null,
        false,
        key -> {
          visitor.accept(key);
          return true;
        });
  }

  @Override
  public void whileEqualPrefix(final DbKey keyPrefix, final KeyVisitor<KeyType> visitor) {
    scanKeys(serialize(keyPrefix), null, false, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    scanKeys(serialize(keyPrefix), startAtKey, false, visitor);
  }

  @Override
  public void whileTrueReverse(final KeyType startAtKey, final KeyVisitor<KeyType> visitor) {
    scanKeys(NO_PREFIX, startAtKey, true, visitor);
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
    final var empty = new MutableBoolean(true);
    scan(
        columnFamily,
        NO_PREFIX,
        null,
        false,
        true,
        (key, value) -> {
          empty.set(false);
          return false;
        });
    return empty.get();
  }

  @Override
  public long count() {
    return countKeys(NO_PREFIX);
  }

  @Override
  public long countEqualPrefix(final DbKey prefix) {
    return countKeys(serialize(prefix));
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
      case Prefix -> {
        final var found = new MutableBoolean();
        scan(
            (EnumValue) foreignKey.columnFamily(),
            serialize(foreignKey),
            null,
            false,
            true,
            (key, value) -> {
              found.set(true);
              return false;
            });
        if (!found.get()) {
          throw new ZeebeDbInconsistentException(
              "Foreign key "
                  + foreignKey.inner()
                  + " does not exist as prefix in "
                  + foreignKey.columnFamily());
        }
      }
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

  private void scanEntries(
      final byte[] prefix,
      final @Nullable DbKey startAt,
      final boolean reverse,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(
        columnFamily,
        prefix,
        startAt == null ? null : serialize(startAt),
        reverse,
        false,
        (key, value) -> {
          final var valueBytes = requireNonNull(value, "a scan with values passes them");
          keyInstance.wrap(new UnsafeBuffer(key), 0, key.length);
          valueInstance.wrap(new UnsafeBuffer(valueBytes), 0, valueBytes.length);
          return visitor.visit(keyInstance, valueInstance);
        });
  }

  private void scanKeys(
      final byte[] prefix,
      final @Nullable DbKey startAt,
      final boolean reverse,
      final KeyVisitor<KeyType> visitor) {
    scan(
        columnFamily,
        prefix,
        startAt == null ? null : serialize(startAt),
        reverse,
        true,
        (key, value) -> {
          keyInstance.wrap(new UnsafeBuffer(key), 0, key.length);
          return visitor.visit(keyInstance);
        });
  }

  private long countKeys(final byte[] prefix) {
    final var count = new MutableLong();
    scan(
        columnFamily,
        prefix,
        null,
        false,
        true,
        (key, value) -> {
          count.increment();
          return true;
        });
    return count.get();
  }

  /**
   * Scans {@code scanned} forward within {@code prefix}, or in reverse over the whole column
   * family, from {@code startAt} or else from the prefix. It merges the backend's scan with this
   * transaction's writes and with the unflushed versions, copied at the instant the backend fixes
   * its view: the copy holds off publication and cleanup until the backend's first callback.
   */
  private void scan(
      final EnumValue scanned,
      final byte[] prefix,
      final byte @Nullable [] startAt,
      final boolean reverse,
      final boolean keysOnly,
      final ScanVisitor visitor) {
    inOpenTransaction(
        transaction -> {
          final var start = startAt != null ? startAt : prefix;
          // RocksDB stops at the first key after the start that isn't in the prefix. If the start
          // lies outside the prefix, only a scan of the whole column family shows that key.
          final var backendPrefix = OverlayMerge.startsWith(start, prefix) ? prefix : NO_PREFIX;
          final var overlay =
              reverse
                  ? store.beginScan(scanned, null, false, new CacheKey(scanned, start), true)
                  : store.beginScan(
                      scanned,
                      new CacheKey(scanned, start),
                      true,
                      prefixEnd(scanned, backendPrefix),
                      false);
          try {
            final var merge =
                new OverlayMerge(
                    scanned,
                    prefix,
                    start,
                    reverse,
                    keysOnly,
                    transaction,
                    overlay.unflushed(),
                    visitor);
            context
                .reader()
                .scan(
                    scanned,
                    backendPrefix,
                    start,
                    reverse,
                    keysOnly,
                    (key, value) -> {
                      overlay.release();
                      return merge.onBackendEntry(key, value);
                    });
            overlay.release();
            merge.finish();
          } finally {
            overlay.release();
          }
        });
  }

  /**
   * Returns the first key after all keys that start with {@code prefix}, or {@code null} if no key
   * of the column family comes after them.
   */
  private static @Nullable CacheKey prefixEnd(final EnumValue columnFamily, final byte[] prefix) {
    for (int i = prefix.length - 1; i >= 0; i--) {
      if (prefix[i] != (byte) 0xFF) {
        final var end = Arrays.copyOf(prefix, i + 1);
        end[i]++;
        return new CacheKey(columnFamily, end);
      }
    }
    return null;
  }
}
