/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import static io.camunda.zeebe.util.buffer.BufferUtil.startsWith;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RawTransactionalColumnFamily {
  private static final Logger LOG = LoggerFactory.getLogger(RawTransactionalColumnFamily.class);

  private static final int INITIAL_KEY_LENGTH = 256;
  private static final int INITIAL_VALUE_LENGTH = 4 * 1024;

  protected final ZeebeTransactionDb<ZbColumnFamilies> transactionDb;
  protected final ZbColumnFamilies columnFamily;
  protected final ColumnFamilyContext columnFamilyContext;
  protected final TransactionContext context;

  public RawTransactionalColumnFamily(
      final ZeebeTransactionDb<ZbColumnFamilies> transactionDb,
      final ZbColumnFamilies columnFamily,
      final TransactionContext context) {
    this.transactionDb = transactionDb;
    this.columnFamily = columnFamily;
    columnFamilyContext = new ColumnFamilyContext(columnFamily.getValue());
    this.context = context;
  }

  public void forEach(final Visitor visitor) {
    context.runInTransaction(
        () -> {
          columnFamilyContext.withPrefixKey(
              new DbNullKey(),
              (prefixKey, prefixLength) -> {
                try (final RocksIterator iterator =
                    newIterator(context, transactionDb.getPrefixReadOptions())) {

                  byte[] keyBytes = new byte[INITIAL_KEY_LENGTH];
                  byte[] valueBytes = new byte[INITIAL_VALUE_LENGTH];
                  final var seekTarget = new DbNullKey();
                  boolean shouldVisitNext = true;

                  for (iterator.seek(columnFamilyContext.keyWithColumnFamily(seekTarget));
                      iterator.isValid() && shouldVisitNext;
                      iterator.next()) {
                    final int keyLen = iterator.key(keyBytes, 0, keyBytes.length);
                    if (keyLen > keyBytes.length) {
                      final var previousLen = keyBytes.length;
                      keyBytes = iterator.key();
                      LOG.debug(
                          "Reallocating keyBytes from {} to {}", previousLen, keyBytes.length);
                    }
                    final int valueLen = iterator.value(valueBytes, 0, valueBytes.length);
                    if (valueLen > valueBytes.length) {
                      final var previousLen = valueBytes.length;
                      valueBytes = iterator.value();
                      LOG.debug(
                          "Reallocating valueBytes from {} to {}", previousLen, valueBytes.length);
                    }

                    if (!startsWith(prefixKey, 0, prefixLength, keyBytes, 0, keyLen)) {
                      break;
                    }
                    shouldVisitNext = visitor.visit(keyBytes, 0, keyLen, valueBytes, 0, valueLen);
                  }
                }
              });
        });
  }

  public void put(
      final ZeebeTransaction transaction,
      final byte[] key,
      final int keyOffset,
      final int keyLen,
      final byte[] value,
      final int valueOffset,
      final int valueLen)
      throws Exception {
    transaction.put(
        transactionDb.getDefaultNativeHandle(),
        key,
        keyOffset,
        keyLen,
        value,
        valueOffset,
        valueLen);
  }

  public byte[] get(
      final ZeebeTransaction transaction, final byte[] key, final int keyOffset, final int keyLen)
      throws Exception {
    return transaction.get(
        transactionDb.getDefaultNativeHandle(),
        transactionDb.getReadOptionsNativeHandle(),
        key,
        keyOffset,
        keyLen);
  }

  RocksIterator newIterator(final TransactionContext context, final ReadOptions options) {
    final var currentTransaction = (ZeebeTransaction) context.getCurrentTransaction();
    return currentTransaction.newIterator(options, transactionDb.getDefaultHandle());
  }

  public interface Visitor {
    boolean visit(
        byte[] key, int keyOffset, int keyLen, byte[] value, int valueOffset, int valueLen);
  }
}
