/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.db.impl.rocksdb.transaction;

import static io.zeebe.util.buffer.BufferUtil.startsWith;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbKey;
import io.zeebe.db.DbValue;
import io.zeebe.db.KeyValuePairVisitor;
import io.zeebe.db.TransactionContext;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.agrona.DirectBuffer;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksIterator;

class TransactionalColumnFamily<
        ColumnFamilyNames extends Enum<ColumnFamilyNames>,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements ColumnFamily<KeyType, ValueType> {

  private final ZeebeTransactionDb<ColumnFamilyNames> transactionDb;
  private final TransactionContext context;

  private final ValueType valueInstance;
  private final KeyType keyInstance;
  private final ColumnFamilyContext columnFamilyContext;

  TransactionalColumnFamily(
      final ZeebeTransactionDb<ColumnFamilyNames> transactionDb,
      final ColumnFamilyNames columnFamily,
      final TransactionContext context,
      final KeyType keyInstance,
      final ValueType valueInstance) {
    this.transactionDb = transactionDb;
    this.context = context;
    this.keyInstance = keyInstance;
    this.valueInstance = valueInstance;
    columnFamilyContext = new ColumnFamilyContext(columnFamily.ordinal());
  }

  private void ensureInOpenTransaction(
      final TransactionContext context, final TransactionConsumer operation) {
    context.runInTransaction(
        () -> operation.run((ZeebeTransaction) context.getCurrentTransaction()));
  }

  @Override
  public void put(final KeyType key, final ValueType value) {
    ensureInOpenTransaction(
        context,
        transaction -> {
          columnFamilyContext.writeKey(key);
          columnFamilyContext.writeValue(value);

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
    columnFamilyContext.writeKey(key);
    final DirectBuffer valueBuffer = getValue(context, columnFamilyContext);
    if (valueBuffer != null) {
      valueInstance.wrap(valueBuffer, 0, valueBuffer.capacity());
      return valueInstance;
    }
    return null;
  }

  private DirectBuffer getValue(
      final TransactionContext context, final ColumnFamilyContext columnFamilyContext) {
    ensureInOpenTransaction(
        context,
        transaction -> {
          final byte[] value =
              transaction.get(
                  transactionDb.getDefaultNativeHandle(),
                  transactionDb.getReadOptionsNativeHandle(),
                  columnFamilyContext.getKeyBufferArray(),
                  columnFamilyContext.getKeyLength());
          columnFamilyContext.wrapValueView(value);
        });
    return columnFamilyContext.getValueView();
  }

  @Override
  public void forEach(final Consumer<ValueType> consumer) {
    forEach(context, consumer);
  }

  @Override
  public void forEach(final BiConsumer<KeyType, ValueType> consumer) {
    forEach(context, consumer);
  }

  @Override
  public void whileTrue(final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileTrue(context, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyPrefix, visitor);
  }

  @Override
  public void whileEqualPrefix(
      final DbKey keyPrefix, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyPrefix, visitor);
  }

  @Override
  public void delete(final KeyType key) {
    columnFamilyContext.writeKey(key);
    ensureInOpenTransaction(
        context,
        transaction ->
            transaction.delete(
                transactionDb.getDefaultNativeHandle(),
                columnFamilyContext.getKeyBufferArray(),
                columnFamilyContext.getKeyLength()));
  }

  @Override
  public boolean exists(final KeyType key) {
    columnFamilyContext.wrapValueView(new byte[0]);
    ensureInOpenTransaction(
        context,
        transaction -> {
          columnFamilyContext.writeKey(key);
          getValue(context, columnFamilyContext);
        });
    return !columnFamilyContext.isValueViewEmpty();
  }

  @Override
  public boolean isEmpty() {
    final AtomicBoolean isEmpty = new AtomicBoolean(true);
    whileEqualPrefix(
        context,
        keyInstance,
        valueInstance,
        (key, value) -> {
          isEmpty.set(false);
          return false;
        });
    return isEmpty.get();
  }

  public void forEach(final TransactionContext context, final Consumer<ValueType> consumer) {
    whileEqualPrefix(
        context,
        keyInstance,
        valueInstance,
        (BiConsumer<KeyType, ValueType>) (ignore, value) -> consumer.accept(value));
  }

  public void forEach(
      final TransactionContext context, final BiConsumer<KeyType, ValueType> consumer) {
    whileEqualPrefix(context, keyInstance, valueInstance, consumer);
  }

  public void whileTrue(
      final TransactionContext context, final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyInstance, valueInstance, visitor);
  }

  public void whileEqualPrefix(
      final TransactionContext context,
      final DbKey keyPrefix,
      final BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyPrefix, keyInstance, valueInstance, visitor);
  }

  public void whileEqualPrefix(
      final TransactionContext context,
      final DbKey keyPrefix,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, keyPrefix, keyInstance, valueInstance, visitor);
  }

  RocksIterator newIterator(final TransactionContext context, final ReadOptions options) {
    final var currentTransaction = (ZeebeTransaction) context.getCurrentTransaction();
    return currentTransaction.newIterator(options, transactionDb.getDefaultHandle());
  }

  protected <KeyType extends DbKey, ValueType extends DbValue> void whileEqualPrefix(
      final TransactionContext context,
      final DbKey prefix,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(
        context,
        prefix,
        keyInstance,
        valueInstance,
        (k, v) -> {
          visitor.accept(k, v);
          return true;
        });
  }

  /**
   * This method is used mainly from other iterator methods to iterate over column family entries,
   * which are prefixed with column family key.
   */
  protected <KeyType extends DbKey, ValueType extends DbValue> void whileEqualPrefix(
      final TransactionContext context,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final BiConsumer<KeyType, ValueType> visitor) {
    whileEqualPrefix(
        context,
        new DbNullKey(),
        keyInstance,
        valueInstance,
        (k, v) -> {
          visitor.accept(k, v);
          return true;
        });
  }

  /**
   * This method is used mainly from other iterator methods to iterate over column family entries,
   * which are prefixed with column family key.
   */
  protected <KeyType extends DbKey, ValueType extends DbValue> void whileEqualPrefix(
      final TransactionContext context,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    whileEqualPrefix(context, new DbNullKey(), keyInstance, valueInstance, visitor);
  }

  /**
   * NOTE: it doesn't seem possible in Java RocksDB to set a flexible prefix extractor on iterators
   * at the moment, so using prefixes seem to be mostly related to skipping files that do not
   * contain keys with the given prefix (which is useful anyway), but it will still iterate over all
   * keys contained in those files, so we still need to make sure the key actually matches the
   * prefix.
   *
   * <p>While iterating over subsequent keys we have to validate it.
   */
  protected <KeyType extends DbKey, ValueType extends DbValue> void whileEqualPrefix(
      final TransactionContext context,
      final DbKey prefix,
      final KeyType keyInstance,
      final ValueType valueInstance,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    columnFamilyContext.withPrefixKey(
        prefix,
        (prefixKey, prefixLength) ->
            ensureInOpenTransaction(
                context,
                transaction -> {
                  try (final RocksIterator iterator =
                      newIterator(context, transactionDb.getPrefixReadOptions())) {

                    boolean shouldVisitNext = true;

                    for (RocksDbInternal.seek(
                            iterator,
                            ZeebeTransactionDb.getNativeHandle(iterator),
                            prefixKey,
                            prefixLength);
                        iterator.isValid() && shouldVisitNext;
                        iterator.next()) {
                      final byte[] keyBytes = iterator.key();
                      if (!startsWith(prefixKey, 0, prefixLength, keyBytes, 0, keyBytes.length)) {
                        break;
                      }

                      shouldVisitNext = visit(keyInstance, valueInstance, visitor, iterator);
                    }
                  }
                }));
  }

  private <KeyType extends DbKey, ValueType extends DbValue> boolean visit(
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
