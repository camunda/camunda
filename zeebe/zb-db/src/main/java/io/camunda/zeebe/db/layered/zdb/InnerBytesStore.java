/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.layered.zdb;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.impl.DbBytes;
import io.camunda.zeebe.db.layered.BytesStore;
import java.util.Objects;
import java.util.function.BiConsumer;

/**
 * The durable delegate of one layered store, backed by a raw-bytes column family of the wrapped
 * database. Reads run through the dedicated owner-thread read context (whose transaction is never
 * left open, so they only ever see committed data); direct writes — unused by the layered wiring,
 * where all durable writes flow through the persist batch — run self-committing through the persist
 * context.
 *
 * <p>Returned and visited arrays are safe to retain: the raw-bytes flyweight copies into a fresh
 * array on every wrap.
 */
final class InnerBytesStore implements BytesStore {

  private final ColumnFamily<DbBytes, DbBytes> readColumnFamily;
  private final ColumnFamily<DbBytes, DbBytes> writeColumnFamily;
  private final TransactionContext writeContext;

  // owner-thread read scratch; the write pair is touched only by the persist IO thread
  private final DbBytes readKey = new DbBytes();
  private final DbBytes readPrefix = new DbBytes();
  private final DbBytes writeKey = new DbBytes();
  private final DbBytes writeValue = new DbBytes();

  InnerBytesStore(
      final ColumnFamily<DbBytes, DbBytes> readColumnFamily,
      final ColumnFamily<DbBytes, DbBytes> writeColumnFamily,
      final TransactionContext writeContext) {
    this.readColumnFamily = Objects.requireNonNull(readColumnFamily, "readColumnFamily");
    this.writeColumnFamily = Objects.requireNonNull(writeColumnFamily, "writeColumnFamily");
    this.writeContext = Objects.requireNonNull(writeContext, "writeContext");
  }

  @Override
  public byte[] get(final byte[] key) {
    readKey.wrapBytes(key);
    final DbBytes value = readColumnFamily.get(readKey);
    return value == null ? null : value.getBytes();
  }

  @Override
  public void put(final byte[] key, final byte[] value) {
    writeContext.runInTransaction(
        () -> {
          writeKey.wrapBytes(key);
          writeValue.wrapBytes(value);
          writeColumnFamily.upsert(writeKey, writeValue);
        });
  }

  @Override
  public void delete(final byte[] key) {
    writeContext.runInTransaction(
        () -> {
          writeKey.wrapBytes(key);
          writeColumnFamily.deleteIfExists(writeKey);
        });
  }

  @Override
  public void prefixScan(final byte[] prefix, final BiConsumer<byte[], byte[]> visitor) {
    readPrefix.wrapBytes(prefix);
    final BiConsumer<DbBytes, DbBytes> rawVisitor =
        (key, value) -> visitor.accept(key.getBytes(), value.getBytes());
    readColumnFamily.whileEqualPrefix(readPrefix, rawVisitor);
  }
}
