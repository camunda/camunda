/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.stream.impl.state;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

public final class NextValueManager {

  private final long initialValue;

  private final ColumnFamily<DbString, NextValue> nextValueColumnFamily;
  private final DbString nextValueKey;
  private final NextValue nextValue = new NextValue();

  public NextValueManager(
      final long initialValue,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext,
      final ZbColumnFamilies columnFamily,
      final String key) {
    this.initialValue = initialValue;

    nextValueKey = new DbString();
    nextValueKey.wrapString(key);
    nextValueColumnFamily =
        zeebeDb.createColumnFamily(columnFamily, transactionContext, nextValueKey, nextValue);
  }

  public long getNextValue() {
    return nextValueColumnFamily.updateAndGet(
        nextValueKey,
        r -> {
          var value = r;
          if (r == null) {
            value = new NextValue(initialValue);
          }
          return value.increment();
        });
  }

  public void setValue(final String key, final long value) {
    nextValueKey.wrapString(key);
    nextValue.set(value);
    nextValueColumnFamily.upsert(nextValueKey, nextValue);
  }

  public long getCurrentValue() {
    final NextValue readValue = nextValueColumnFamily.get(nextValueKey);

    long currentValue = initialValue;
    if (readValue != null) {
      currentValue = readValue.get();
    }
    return currentValue;
  }
}
