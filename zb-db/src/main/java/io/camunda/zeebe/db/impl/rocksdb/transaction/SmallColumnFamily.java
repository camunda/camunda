/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static io.camunda.zeebe.db.impl.rocksdb.transaction.RocksDbInternal.isRocksDbExceptionRecoverable;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.KeyValuePairVisitor;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.TransactionContext.TransactionListener;
import io.camunda.zeebe.db.ZeebeDbException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Function;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.RocksDBException;

/**
 * Column family where everything is cached. Great for small column families with a few well-known
 * keys such as those for the {@code NextValueManager}.
 */
public final class SmallColumnFamily<
        ColumnFamilyNames extends Enum<ColumnFamilyNames>,
        KeyType extends DbKey,
        ValueType extends DbValue>
    implements TransactionListener {
  private final ZeebeTransactionDb<ColumnFamilyNames> transactionDb;
  private final ColumnFamilyContext columnFamilyContext;
  private final TransactionContext transactionContext;
  private final Function<byte[], ValueType> valueBuilder;
  private final Function<byte[], KeyType> keyBuilder;
  private CfCache cache = new CfCache();

  SmallColumnFamily(
      final ZeebeTransactionDb<ColumnFamilyNames> transactionDb,
      final ColumnFamilyContext columnFamilyContext,
      final TransactionContext transactionContext,
      final Function<byte[], ValueType> valueBuilder,
      final Function<byte[], KeyType> keyBuilder) {
    this.transactionDb = transactionDb;
    this.columnFamilyContext = columnFamilyContext;
    this.transactionContext = transactionContext;
    this.valueBuilder = valueBuilder;
    this.keyBuilder = keyBuilder;
  }

  public void insert(final KeyType key, final ValueType value) {
    final var keyBytes = columnFamilyContext.keyWithColumnFamily(key).array();
    final var valueBytes = valueAsBytes(value).array();
    cache.add(keyBytes, valueBytes);
    inTx(
        (TransactionalVoidOperation)
            tx ->
                tx.put(
                    transactionDb.getDefaultNativeHandle(),
                    keyBytes,
                    keyBytes.length,
                    valueBytes,
                    valueBytes.length));
  }

  public void update(final KeyType key, final ValueType value) {
    final var keyBytes = columnFamilyContext.keyWithColumnFamily(key).array();
    final var valueBytes = valueAsBytes(value).array();
    cache.add(keyBytes, valueBytes);
    inTx(
        (TransactionalVoidOperation)
            tx ->
                tx.put(
                    transactionDb.getDefaultNativeHandle(),
                    keyBytes,
                    keyBytes.length,
                    valueBytes,
                    valueBytes.length));
  }

  public void upsert(final KeyType key, final ValueType value) {
    final var keyBytes = columnFamilyContext.keyWithColumnFamily(key).array();
    final var valueBytes = valueAsBytes(value).array();
    cache.add(keyBytes, valueBytes);
    inTx(
        (TransactionalVoidOperation)
            tx ->
                tx.put(
                    transactionDb.getDefaultNativeHandle(),
                    keyBytes,
                    keyBytes.length,
                    valueBytes,
                    valueBytes.length));
  }

  public ValueType get(final KeyType key) {
    final var keyBytes = columnFamilyContext.keyWithColumnFamily(key).array();

    return inTx(
        tx -> {
          final var value =
              switch (cache.test(keyBytes)) {
                case Cached -> cache.get(keyBytes);
                case Unknown -> tx.get(
                    transactionDb.getDefaultNativeHandle(),
                    transactionDb.getReadOptionsNativeHandle(),
                    keyBytes,
                    keyBytes.length);
                case DoesNotExist -> null;
              };
          return valueBuilder.apply(value);
        });
  }

  public void deleteExisting(final KeyType key) {
    final var keyBytes = columnFamilyContext.keyWithColumnFamily(key).array();
    cache.add(keyBytes, null);
    inTx(
        (TransactionalVoidOperation)
            transaction ->
                transaction.delete(
                    transactionDb.getDefaultNativeHandle(), keyBytes, keyBytes.length));
  }

  public void deleteIfExists(final KeyType key) {
    final var keyBytes = columnFamilyContext.keyWithColumnFamily(key).array();
    cache.add(keyBytes, null);
    inTx(
        (TransactionalVoidOperation)
            transaction ->
                transaction.delete(
                    transactionDb.getDefaultNativeHandle(), keyBytes, keyBytes.length));
  }

  public boolean exists(final KeyType key) {
    final var keyBytes = columnFamilyContext.keyWithColumnFamily(key).array();
    return switch (cache.test(keyBytes)) {
      case Unknown -> inTx(
          transaction ->
              transaction.get(
                      transactionDb.getDefaultNativeHandle(),
                      transactionDb.getReadOptionsNativeHandle(),
                      keyBytes,
                      keyBytes.length)
                  != null);
      case Cached -> true;
      case DoesNotExist -> false;
    };
  }

  @Override
  public void commit() {}

  @Override
  public void rollback() {
    cache = new CfCache();
  }

  private void forEachInPrefix(
      final DbKey startAt,
      final DbKey prefix,
      final KeyValuePairVisitor<KeyType, ValueType> visitor) {
    final var seekTarget = Objects.requireNonNullElse(startAt, prefix);
    Objects.requireNonNull(prefix);
    Objects.requireNonNull(visitor);

    final var prefixKey = columnFamilyContext.keyWithColumnFamily(prefix);
    final var prefixLength = prefix.getLength();

    final var tx = (ZeebeTransaction) transactionContext.getCurrentTransaction();
    final var iterator =
        tx.newIterator(transactionDb.getPrefixReadOptions(), transactionDb.getDefaultHandle());

    boolean shouldVisitNext = true;

    for (iterator.seek(columnFamilyContext.keyWithColumnFamily(seekTarget));
        iterator.isValid() && shouldVisitNext;
        iterator.next()) {
      final var keyBytes = iterator.key();
      final var valueBytes = iterator.value();
      if (!Arrays.equals(prefixKey.array(), 0, prefixLength, keyBytes, 0, keyBytes.length)) {
        break;
      }

      final var key = keyBuilder.apply(Arrays.copyOf(keyBytes, keyBytes.length));
      final var value = valueBuilder.apply(Arrays.copyOf(valueBytes, valueBytes.length));

      shouldVisitNext = visitor.visit(key, value);
    }
  }

  private void inTx(final TransactionalVoidOperation operation) {
    inTx(
        (tx) -> {
          operation.run(tx);
          return null;
        });
  }

  private <T> T inTx(final TransactionalOperation<T> operation) {
    final var tx = (ZeebeTransaction) transactionContext.getCurrentTransaction();

    try {
      if (!tx.isInCurrentTransaction()) {
        throw new IllegalStateException();
      }
      return operation.run(tx);
    } catch (final RuntimeException e) {
      throw e;
    } catch (final RocksDBException e) {
      final String errorMessage = "Unexpected error occurred during RocksDB transaction.";
      if (isRocksDbExceptionRecoverable(e)) {
        throw new ZeebeDbException(errorMessage, e);
      } else {
        throw new RuntimeException(errorMessage, e);
      }
    } catch (final Exception ex) {
      throw new RuntimeException(
          "Unexpected error occurred during zeebe db transaction operation.", ex);
    }
  }

  @FunctionalInterface
  interface TransactionalOperation<T> {
    T run(ZeebeTransaction tx) throws Exception;
  }

  interface TransactionalVoidOperation {
    void run(ZeebeTransaction tx) throws Exception;
  }

  private ByteBuffer valueAsBytes(DbValue value) {
    final var length = value.getLength();
    final var bytes = ByteBuffer.allocate(length);
    value.write(new UnsafeBuffer(bytes), 0);
    return bytes;
  }
}
