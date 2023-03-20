/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.stream.impl.state;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.db.impl.rocksdb.transaction.SmallColumnFamily;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.agrona.DirectBuffer;

public final class NextValueManager {

  private final long initialValue;

  private final SmallColumnFamily<ZbColumnFamilies, DbString, NextValue> nextValueColumnFamily;
  private final DbString nextValueKey;
  private final NextValue nextValue = new NextValue();

  public NextValueManager(
      final long initialValue,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final ZbColumnFamilies columnFamily) {
    this.initialValue = initialValue;

    nextValueKey = new DbString();
    nextValueColumnFamily =
        zeebeDb.createCachedColumnFamily(columnFamily, transactionContext, nextValueKey, nextValue);
  }

  public long getNextValue(final String key) {
    final long previousKey = getCurrentValue(key);
    final long nextKey = previousKey + 1;
    nextValue.set(nextKey);
    nextValueColumnFamily.upsert(nextValueKey, nextValue);

    return nextKey;
  }

  public void setValue(final String key, final long value) {
    nextValueKey.wrapString(key);
    nextValue.set(value);
    nextValueColumnFamily.upsert(nextValueKey, nextValue);
  }

  public long getCurrentValue(final String key) {
    nextValueKey.wrapString(key);
    return getCurrentValue();
  }

  public long getCurrentValue(final DirectBuffer key) {
    nextValueKey.wrapBuffer(key);
    return getCurrentValue();
  }

  private long getCurrentValue() {
    final NextValue readValue = nextValueColumnFamily.get(nextValueKey);

    long currentValue = initialValue;
    if (readValue != null) {
      currentValue = readValue.get();
    }
    return currentValue;
  }
}
