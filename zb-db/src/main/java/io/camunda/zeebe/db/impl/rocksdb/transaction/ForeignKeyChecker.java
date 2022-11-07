/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import io.camunda.zeebe.db.ConsistencyChecksSettings;
import io.camunda.zeebe.db.ContainsForeignKeys;
import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.ZeebeDbInconsistentException;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.nio.ByteBuffer;
import org.agrona.ExpandableArrayBuffer;

/**
 * Similar to {@link TransactionalColumnFamily} but supports lookups on arbitrary column families.
 * Can be used to check that a foreign key is valid.
 */
public final class ForeignKeyChecker {
  private final ZeebeTransactionDb<?> transactionDb;
  private final ExpandableArrayBuffer keyBuffer = new ExpandableArrayBuffer();
  private final boolean enabled;

  public ForeignKeyChecker(
      final ZeebeTransactionDb<?> transactionDb, final ConsistencyChecksSettings settings) {
    this.transactionDb = transactionDb;
    enabled = settings.enableForeignKeyChecks();
  }

  public void assertExists(
      final ZeebeTransaction transaction, final ContainsForeignKeys containsForeignKey)
      throws Exception {
    if (!enabled) {
      return;
    }
    for (final var fk : containsForeignKey.containedForeignKeys()) {
      assertForeignKeyExists(transaction, fk);
    }
  }

  private void assertForeignKeyExists(
      final ZeebeTransaction transaction, final DbForeignKey<DbKey> foreignKey) throws Exception {
    if (foreignKey.shouldSkipCheck()) {
      return;
    }

    keyBuffer.putLong(0, foreignKey.columnFamily().ordinal(), ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    foreignKey.write(keyBuffer, Long.BYTES);
    final var keyBufferLength = Long.BYTES + foreignKey.getLength();

    switch (foreignKey.match()) {
      case Full -> assertKeyExists(transaction, foreignKey, keyBuffer.byteArray(), keyBufferLength);
      case Prefix -> assertPrefixExists(
          transaction, foreignKey, keyBuffer.byteArray(), keyBufferLength);
      default -> throw new IllegalStateException(
          "Unknown foreign key match type: " + foreignKey.match());
    }
  }

  private void assertKeyExists(
      final ZeebeTransaction transaction,
      final DbForeignKey<? extends DbKey> foreignKey,
      final byte[] key,
      final int keyLength)
      throws Exception {
    final var exists =
        transaction.get(
                transactionDb.getDefaultNativeHandle(),
                transactionDb.getReadOptionsNativeHandle(),
                key,
                keyLength)
            != null;
    if (!exists) {
      throw new ZeebeDbInconsistentException(
          "Foreign key " + foreignKey.inner() + " does not exist in " + foreignKey.columnFamily());
    }
  }

  private void assertPrefixExists(
      final ZeebeTransaction transaction,
      final DbForeignKey<? extends DbKey> foreignKey,
      final byte[] prefix,
      final int prefixLength) {
    try (final var iterator =
        transaction.newIterator(
            transactionDb.getPrefixReadOptions(), transactionDb.getDefaultHandle())) {

      final ByteBuffer bufferView = ByteBuffer.wrap(prefix, 0, prefixLength);
      iterator.seek(bufferView);
      boolean exists = false;
      if (iterator.isValid()) {
        final byte[] keyBytes = iterator.key();
        exists = BufferUtil.startsWith(prefix, 0, prefixLength, keyBytes, 0, keyBytes.length);
      }
      if (!exists) {
        throw new ZeebeDbInconsistentException(
            "Foreign key "
                + foreignKey.inner()
                + " does not exist as prefix in "
                + foreignKey.columnFamily());
      }
    }
  }
}
