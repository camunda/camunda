/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbString;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.agrona.DirectBuffer;

public final class ProcessVersionManager {

  private final long initialValue;

  private final ColumnFamily<DbString, NextValue> nextValueColumnFamily;
  private final DbString processIdKey;
  private final NextValue nextVersion = new NextValue();

  public ProcessVersionManager(
      final long initialValue,
      final ZeebeDb<ZbColumnFamilies> zeebeDb,
      final TransactionContext transactionContext) {
    this.initialValue = initialValue;

    processIdKey = new DbString();
    nextValueColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PROCESS_VERSION, transactionContext, processIdKey, nextVersion);
  }

  public void setProcessVersion(final String processId, final long value) {
    processIdKey.wrapString(processId);
    nextVersion.set(value);
    nextValueColumnFamily.upsert(processIdKey, nextVersion);
  }

  public long getCurrentProcessVersion(final String processId) {
    processIdKey.wrapString(processId);
    return getCurrentProcessVersion();
  }

  public long getCurrentProcessVersion(final DirectBuffer processId) {
    processIdKey.wrapBuffer(processId);
    return getCurrentProcessVersion();
  }

  private long getCurrentProcessVersion() {
    final NextValue readValue = nextValueColumnFamily.get(processIdKey);

    long currentValue = initialValue;
    if (readValue != null) {
      currentValue = readValue.get();
    }
    return currentValue;
  }
}
