/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static io.camunda.zeebe.util.buffer.BufferUtil.startsWith;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ContainsForeignKeys;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksIterator;

/**
 * Some code conventions that we should follow here:
 *
 * <ul>
 *   <li>Public methods ensure that a transaction is open by using {@link
 *       TransactionalColumnFamily#ensureInOpenTransaction}, private methods can assume that a
 *       transaction is already open and don't need to call ensureInOpenTransaction.
 *   <li>Iteration is implemented in terms of {@link TransactionalColumnFamily#forEachInPrefix} to
 *       depend difficult to follow call chains between the different public methods such as {@link
 *       TransactionalColumnFamily#forEach(Consumer)} and {@link
 *       TransactionalColumnFamily#whileEqualPrefix(DbKey, BiConsumer)}
 * </ul>
 */
class TransactionalColumnFamily<
        ColumnFamilyNames extends Enum<ColumnFamilyNames>,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private final ZeebeTransactionDb<ColumnFamilyNames> transactionDb;
  private final ConsistencyChecksSettings consistencyChecksSettings;
  private final ColumnFamilyNames columnFamily;
  private final TransactionContext context;

  private final ValueType valueInstance;
  private final KeyType keyInstance;
  private final ColumnFamilyContext columnFamilyContext;

  private final ForeignKeyChecker foreignKeyChecker;

  TransactionalColumnFamily(
      final ZeebeTransactionDb<ColumnFamilyNames> transactionDb,
      final ConsistencyChecksSettings consistencyChecksSettings,
      final ColumnFamilyNames columnFamily,
      final TransactionContext context,
      final KeyType keyInstance,
      final ValueType valueInstance) {
    this.transactionDb = transactionDb;
    this.consistencyChecksSettings = consistencyChecksSettings;
    this.columnFamily = columnFamily;
    this.context = context;
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;
    columnFamilyContext = new ColumnFamilyContext(columnFamily.ordinal());
    foreignKeyChecker = new ForeignKeyChecker(transactionDb, consistencyChecksSettings);
  }

  @Override
  public void insert(final KeyType key, final ValueType value) {
    ensureInOpenTransaction(
        transaction -> {
          columnFamilyContext.writeKey(key);
          columnFamilyContext.writeValue(value);

          assertKeyDoesNotExist(transaction);
          assertForeignKeysExist(transaction, key, value);
          transaction.put(
              transactionDb.getDefaultNativeHandle(),
              columnFamilyContext.getKeyBufferArray(),
              columnFamilyContext.getKeyLength(),
              columnFamilyContext.getValueBufferArray(),
              value.getLength());
        });
  }

  @Override
  public void update(final KeyType key, final ValueType value) {
    ensureInOpenTransaction(
        transaction -> {
          columnFamilyContext.writeKey(key);
          columnFamilyContext.writeValue(value);
          assertKeyExists(transaction);
          assertForeignKeysExist(transaction, key, value);
          transaction.put(
              transactionDb.getDefaultNativeHandle(),
              columnFamilyContext.getKeyBufferArray(),
              columnFamilyContext.getKeyLength(),
              columnFamilyContext.getValueBufferArray(),
              value.getLength());
        });
  }

  @Override
  public void upsert(final KeyType key, final ValueType value) {
    ensureInOpenTransaction(
        transaction -> {
          columnFamilyContext.writeKey(key);
          columnFamilyContext.writeValue(value);
          assertForeignKeysExist(transaction, key, value);
          transaction.put(
              transactionDb.getDefaultNativeHandle(),
              columnFamilyContext.getKeyBufferArray(),
              columnFamilyContext.getKeyLength(),
              columnFamilyContext.getValueBufferArray(),
              value.getLength());
        });
  }

  @Override
  public ValueType get(final KeyType key) {
    ensureInOpenTransaction(
        transaction -> {
          columnFamilyContext.writeKey(key);
          final byte[] value =
              transaction.get(
                  transactionDb.getDefaultNativeHandle(),
                  transactionDb.getReadOptionsNativeHandle(),
                  columnFamilyContext.getKeyBufferArray(),
                  columnFamilyContext.getKeyLength());
          columnFamilyContext.wrapValueView(value);
        });
    final var valueBuffer = columnFamilyContext.getValueView();
    if (valueBuffer != null) {
      valueInstance.wrap(valueBuffer, 0, valueBuffer.capacity());
      return valueInstance;
    }
    return null;
  }

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    ensureInOpenTransaction(
        transaction ->
            forEachInPrefix(
                new DbNullKey(),
                (k, v) -> {
                  consumer.accept(v);
                  return true;
                }));
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    ensureInOpenTransaction(
        transaction ->
            forEachInPrefix(
                new DbNullKey(),
                (k, v) -> {
                  consumer.accept(k, v);
                  return true;
                }));
  }

  @Override
  public void whileTrue(
      final KeyType startAtKey, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    ensureInOpenTransaction(transaction -> forEachInPrefix(startAtKey, new DbNullKey(), visitor));
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    ensureInOpenTransaction(transaction -> forEachInPrefix(new DbNullKey(), visitor));
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    ensureInOpenTransaction(
        transaction ->
            forEachInPrefix(
                keyPrefix,
                (k, v) -> {
                  visitor.accept(k, v);
                  return true;
                }));
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    ensureInOpenTransaction(transaction -> forEachInPrefix(keyPrefix, visitor));
  }

  @Override
  public void deleteExisting(final KeyType key) {
    ensureInOpenTransaction(
        transaction -> {
          columnFamilyContext.writeKey(key);
          assertKeyExists(transaction);
          transaction.delete(
              transactionDb.getDefaultNativeHandle(),
              columnFamilyContext.getKeyBufferArray(),
              columnFamilyContext.getKeyLength());
        });
  }

  @Override
  public void deleteIfExists(final KeyType key) {
    ensureInOpenTransaction(
        transaction -> {
          columnFamilyContext.writeKey(key);
          transaction.delete(
              transactionDb.getDefaultNativeHandle(),
              columnFamilyContext.getKeyBufferArray(),
              columnFamilyContext.getKeyLength());
        });
  }

  @Override
  public boolean exists(final KeyType key) {
    ensureInOpenTransaction(
        transaction -> {
          columnFamilyContext.writeKey(key);
          final byte[] value =
              transaction.get(
                  transactionDb.getDefaultNativeHandle(),
                  transactionDb.getReadOptionsNativeHandle(),
                  columnFamilyContext.getKeyBufferArray(),
                  columnFamilyContext.getKeyLength());
          columnFamilyContext.wrapValueView(value);
        });
    return !columnFamilyContext.isValueViewEmpty();
  }

  @Override
  public boolean isEmpty() {
    final AtomicBoolean isEmpty = new AtomicBoolean(true);
    ensureInOpenTransaction(
        transaction ->
            forEachInPrefix(
                new DbNullKey(),
                (key, value) -> {
                  isEmpty.set(false);
                  return false;
                }));

    return isEmpty.get();
  }

  private void assertForeignKeysExist(final ZeebeTransaction transaction, final Object... keys)
      throws Exception {
    if (!consistencyChecksSettings.enableForeignKeyChecks()) {
      return;
    }
    for (final var key : keys) {
      if (key instanceof ContainsForeignKeys containsForeignKey) {
        foreignKeyChecker.assertExists(transaction, containsForeignKey);
      }
    }
  }

  private void assertKeyDoesNotExist(final ZeebeTransaction transaction) throws Exception {
    if (!consistencyChecksSettings.enablePreconditions()) {
      return;
    }
    final var value =
        transaction.get(
            transactionDb.getDefaultNativeHandle(),
            transactionDb.getReadOptionsNativeHandle(),
            columnFamilyContext.getKeyBufferArray(),
            columnFamilyContext.getKeyLength());
    if (value != null) {
      throw new ZeebeDbInconsistentException(
          "Key " + keyInstance + " in ColumnFamily " + columnFamily + " already exists");
    }
  }

  private void assertKeyExists(final ZeebeTransaction transaction) throws Exception {
    if (!consistencyChecksSettings.enablePreconditions()) {
      return;
    }
    final var value =
        transaction.get(
            transactionDb.getDefaultNativeHandle(),
            transactionDb.getReadOptionsNativeHandle(),
            columnFamilyContext.getKeyBufferArray(),
            columnFamilyContext.getKeyLength());
    if (value == null) {
      throw new ZeebeDbInconsistentException(
          "Key " + keyInstance + " in ColumnFamily " + columnFamily + " does not exist");
    }
  }

  /**
   * Make sure to use this method in all public methods of this class to ensure that all operations
   * on the column family occur inside a transaction. Within private methods we can assume that a
   * transaction was already opened.
   */
  private void ensureInOpenTransaction(final TransactionConsumer operation) {
    context.runInTransaction(
        () -> operation.run((ZeebeTransaction) context.getCurrentTransaction()));
  }

  RocksIterator newIterator(final TransactionContext context, final ReadOptions options) {
    final var currentTransaction = (ZeebeTransaction) context.getCurrentTransaction();
    return currentTransaction.newIterator(options, transactionDb.getDefaultHandle());
  }

  /**
   * This is the preferred method to implement methods that iterate over a column family.
   *
   * @param prefix of all keys that are iterated over.
   * @param visitor called for all kv pairs where the key matches the given prefix. The visitor can
   *     indicate whether iteration should continue or not, see {@link KeyValuePairVisitor}.
   */
  private void forEachInPrefix(
      final DbKey prefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    forEachInPrefix(prefix, prefix, visitor);
  }
  /**
   * This is the preferred method to implement methods that iterate over a column family.
   *
   * @param startAt seek to this key before starting iteration. If null, seek to {@code prefix}
   *     instead.
   * @param prefix of all keys that are iterated over.
   * @param visitor called for all kv pairs where the key matches the given prefix. The visitor can
   *     indicate whether iteration should continue or not, see {@link KeyValuePairVisitor}.
   */
  private void forEachInPrefix(
      final DbKey startAt,
      final DbKey prefix,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    final var seekTarget = Objects.requireNonNullElse(startAt, prefix);
    Objects.requireNonNull(prefix);
    Objects.requireNonNull(visitor);

    /*
     * NOTE: it doesn't seem possible in Java RocksDB to set a flexible prefix extractor on
     * iterators at the moment, so using prefixes seem to be mostly related to skipping files that
     * do not contain keys with the given prefix (which is useful anyway), but it will still iterate
     * over all keys contained in those files, so we still need to make sure the key actually
     * matches the prefix.
     *
     * <p>While iterating over subsequent keys we have to validate it.
     */
    columnFamilyContext.withPrefixKey(
        prefix,
        (prefixKey, prefixLength) -> {
          try (final RocksIterator iterator =
              newIterator(context, transactionDb.getPrefixReadOptions())) {

            boolean shouldVisitNext = true;

            for (iterator.seek(columnFamilyContext.keyWithColumnFamily(seekTarget));
                iterator.isValid() && shouldVisitNext;
                iterator.next()) {
              final byte[] keyBytes = iterator.key();
              if (!startsWith(prefixKey, 0, prefixLength, keyBytes, 0, keyBytes.length)) {
                break;
              }

              shouldVisitNext = visit(keyInstance, valueInstance, visitor, iterator);
            }
          }
        });
  }

  private boolean visit(
      final KeyType keyInstance,
      final ValueType valueInstance,
      final KeyValuePairVisitor<KeyType, ValueType> iteratorConsumer,
      final RocksIterator iterator) {
    final var keyBytes = iterator.key();

    columnFamilyContext.wrapKeyView(keyBytes);
    columnFamilyContext.wrapValueView(iterator.value());

    final DirectBuffer keyViewBuffer = columnFamilyContext.getKeyView();
    keyInstance.wrap(keyViewBuffer, 0, keyViewBuffer.capacity());
    final DirectBuffer valueViewBuffer = columnFamilyContext.getValueView();
    valueInstance.wrap(valueViewBuffer, 0, valueViewBuffer.capacity());

    return iteratorConsumer.visit(keyInstance, valueInstance);
  }
}
