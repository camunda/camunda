/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ContainsForeignKeys;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.KeyVisitor;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.rdbms.RdbmsTransaction.RowVisitor;
import io.camunda.zeebe.protocol.ColumnFamilyScope;
import io.camunda.zeebe.protocol.EnumValue;
import io.camunda.zeebe.protocol.ScopedColumnFamily;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.Objects;
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
 * A column family in the state table. It behaves like RocksDB's: the same results, the same scan
 * order, and the same consistency checks with the same exceptions.
 *
 * <p>Writes go to the context's transaction; reads see that transaction's own writes.
 */
final class RdbmsColumnFamily<
        ColumnFamilyNames extends Enum<? extends EnumValue> & EnumValue & ScopedColumnFamily,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private static final byte[] NO_PREFIX = new byte[0];

  private final RdbmsTransactionContext context;
  private final ColumnFamilyNames columnFamily;
  private final KeyType keyInstance;
  private final ValueType valueInstance;
  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final ExpandableArrayBuffer serializationBuffer = new ExpandableArrayBuffer();

  RdbmsColumnFamily(
      final RdbmsTransactionContext context,
      final ColumnFamilyNames columnFamily,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final ConsistencyChecksSettings consistencyChecksSettings) {
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
          final var keyBytes = serialize(key);
          final var valueBytes = serialize(value);
          assertKeyDoesNotExist(transaction, keyBytes);
          assertForeignKeysExist(transaction, key, value);
          transaction.put(columnFamily.getValue(), keyBytes, valueBytes);
        });
  }

  @Override
  public void update(final KeyType key, final ValueType value) {
    inOpenTransaction(
        transaction -> {
          final var keyBytes = serialize(key);
          final var valueBytes = serialize(value);
          assertKeyExists(transaction, keyBytes);
          assertForeignKeysExist(transaction, key, value);
          transaction.put(columnFamily.getValue(), keyBytes, valueBytes);
        });
  }

  @Override
  public void upsert(final KeyType key, final ValueType value) {
    inOpenTransaction(
        transaction -> {
          final var keyBytes = serialize(key);
          final var valueBytes = serialize(value);
          assertForeignKeysExist(transaction, key, value);
          transaction.put(columnFamily.getValue(), keyBytes, valueBytes);
        });
  }

  @Override
  public @Nullable ValueType get(final KeyType key) {
    final var bytes = read(key);
    // like RocksDB's, which reads an empty value as missing here but not in get with a supplier
    if (bytes == null || bytes.length == 0) {
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
          final var keyBytes = serialize(key);
          assertKeyExists(transaction, keyBytes);
          transaction.delete(columnFamily.getValue(), keyBytes);
        });
  }

  @Override
  public void deleteIfExists(final KeyType key) {
    inOpenTransaction(transaction -> transaction.delete(columnFamily.getValue(), serialize(key)));
  }

  @Override
  public boolean exists(final KeyType key) {
    final var bytes = read(key);
    // like RocksDB's, which reads an empty value as missing here but not in its consistency checks
    return bytes != null && bytes.length > 0;
  }

  @Override
  public boolean isEmpty() {
    final var empty = new MutableBoolean();
    inOpenTransaction(
        transaction -> empty.set(!transaction.exists(withPrefix(transaction, NO_PREFIX))));
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

  private void inOpenTransaction(final Consumer<RdbmsTransaction> operation) {
    context.runInTransaction(() -> operation.accept(context.currentTransaction()));
  }

  private byte @Nullable [] read(final KeyType key) {
    final var value = new MutableReference<byte[]>();
    inOpenTransaction(
        transaction -> value.set(transaction.get(columnFamily.getValue(), serialize(key))));
    return value.get();
  }

  private void assertKeyDoesNotExist(final RdbmsTransaction transaction, final byte[] key) {
    if (consistencyChecksSettings.enablePreconditions()
        && transaction.get(columnFamily.getValue(), key) != null) {
      throw new ZeebeDbInconsistentException(
          "Key " + keyInstance + " in ColumnFamily " + columnFamily + " already exists");
    }
  }

  private void assertKeyExists(final RdbmsTransaction transaction, final byte[] key) {
    if (consistencyChecksSettings.enablePreconditions()
        && transaction.get(columnFamily.getValue(), key) == null) {
      throw new ZeebeDbInconsistentException(
          "Key " + keyInstance + " in ColumnFamily " + columnFamily + " does not exist");
    }
  }

  private void assertForeignKeysExist(final RdbmsTransaction transaction, final Object... keys) {
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
      final RdbmsTransaction transaction, final DbForeignKey<DbKey> foreignKey) {
    if (foreignKey.shouldSkipCheck()) {
      return;
    }
    final var foreignColumnFamily = ((EnumValue) foreignKey.columnFamily()).getValue();
    switch (foreignKey.match()) {
      case Full -> {
        if (transaction.get(foreignColumnFamily, serialize(foreignKey)) == null) {
          throw new ZeebeDbInconsistentException(
              "Foreign key "
                  + foreignKey.inner()
                  + " does not exist in "
                  + foreignKey.columnFamily());
        }
      }
      case Prefix -> {
        final var range =
            KeyRanges.withPrefix(transaction.stateId(), foreignColumnFamily, serialize(foreignKey));
        if (!transaction.exists(range)) {
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

  private byte[] serialize(final BufferWriter writer) {
    final int length = writer.getLength();
    writer.write(serializationBuffer, 0);
    final var bytes = new byte[length];
    serializationBuffer.getBytes(0, bytes);
    return bytes;
  }

  private StateRange withPrefix(final RdbmsTransaction transaction, final byte[] prefix) {
    return KeyRanges.withPrefix(transaction.stateId(), columnFamily.getValue(), prefix);
  }

  private void scanEntries(
      final byte[] prefix,
      final @Nullable DbKey startAt,
      final boolean reverse,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    scan(
        prefix,
        startAt,
        reverse,
        false,
        (key, value) -> {
          final var valueBytes = Objects.requireNonNull(value, "a scan with values passes them");
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
        prefix,
        startAt,
        reverse,
        true,
        (key, value) -> {
          keyInstance.wrap(new UnsafeBuffer(key), 0, key.length);
          return visitor.visit(keyInstance);
        });
  }

  private long countKeys(final byte[] prefix) {
    final var count = new MutableLong();
    inOpenTransaction(transaction -> count.set(transaction.count(withPrefix(transaction, prefix))));
    return count.get();
  }

  /**
   * Scans forward within {@code prefix}, or in reverse over the whole column family, from {@code
   * startAt} or else from the prefix. Like RocksDB, it stops at the first key without the prefix.
   */
  private void scan(
      final byte[] prefix,
      final @Nullable DbKey startAt,
      final boolean reverse,
      final boolean keysOnly,
      final RowVisitor visitor) {
    inOpenTransaction(
        transaction -> {
          final var start = startAt != null ? serialize(startAt) : prefix;
          final var range =
              reverse
                  ? KeyRanges.reverse(transaction.stateId(), columnFamily.getValue(), prefix, start)
                  : KeyRanges.forward(
                      transaction.stateId(), columnFamily.getValue(), prefix, start);
          transaction.scan(
              range,
              keysOnly,
              (key, value) -> KeyRanges.startsWith(key, prefix) && visitor.visit(key, value));
        });
  }
}
