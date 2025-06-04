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
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.agrona.concurrent.UnsafeBuffer;
import org.rocksdb.ReadOptions;
import org.rocksdb.RocksIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RawTransactionalColumnFamily {
  private static final Logger LOG = LoggerFactory.getLogger(RawTransactionalColumnFamily.class);

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

  /**
   * Run the given visitor for each key/value pair in the column family.
   *
   * @param visitor to run for each key/value pair: the key bytearray is "raw", i.e. contains also
   *     the column family prefix. In order to get access to the key without the prefix, you need to
   *     use {@link ColumnFamilyContext#wrapKeyView(byte[])}
   */
  public void forEach(final Visitor visitor) {
    context.runInTransaction(
        () -> {
          columnFamilyContext.withPrefixKey(
              new DbNullKey(),
              (prefixKey, prefixLength) -> {
                try (final RocksIterator iterator =
                    newIterator(context, transactionDb.getPrefixReadOptions())) {
                  final var seekTarget = new DbNullKey();
                  boolean shouldVisitNext = true;

                  for (iterator.seek(columnFamilyContext.keyWithColumnFamily(seekTarget));
                      iterator.isValid() && shouldVisitNext;
                      iterator.next()) {
                    final byte[] keyBytes = iterator.key();
                    final byte[] valueBytes = iterator.value();

                    if (!startsWith(prefixKey, 0, prefixLength, keyBytes, 0, keyBytes.length)) {
                      break;
                    }
                    try {
                      shouldVisitNext =
                          visitor.visit(
                              keyBytes, 0, keyBytes.length, valueBytes, 0, valueBytes.length);
                    } catch (final Exception e) {
                      LOG.error(
                          "Error visiting key {} in column family {}",
                          new String(keyBytes),
                          columnFamily,
                          e);
                      shouldVisitNext = false;
                    }
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
    final var dbBytes = new DbBytes();
    final var buffer = new UnsafeBuffer(key, keyOffset, keyLen);
    dbBytes.wrap(buffer, 0, buffer.capacity());
    columnFamilyContext.withPrefixKey(
        dbBytes,
        (wrappedKey, wrappedLength) -> {
          try {
            rawPut(transaction, wrappedKey, 0, wrappedLength, value, valueOffset, valueLen);
          } catch (final Exception e) {
            throw new RuntimeException(e);
          }
        });
  }

  /** Raw put into the DB ignoring the prefix key and "virtual" column family. */
  public void rawPut(
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
