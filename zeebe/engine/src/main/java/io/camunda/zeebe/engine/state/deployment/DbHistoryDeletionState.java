/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.mutable.MutableHistoryDeletionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import java.util.function.Function;

public class DbHistoryDeletionState implements MutableHistoryDeletionState {

  private final DbLong processInstanceKey;
  private final ColumnFamily<DbLong, DbNil> processInstancesToDeleteColumnFamily;

  public DbHistoryDeletionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    processInstanceKey = new DbLong();
    processInstancesToDeleteColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.HISTORY_DELETION,
            transactionContext,
            processInstanceKey,
            DbNil.INSTANCE);
  }

  @Override
  public void insertProcessInstanceToDelete(final long processInstanceKey) {
    this.processInstanceKey.wrapLong(processInstanceKey);
    processInstancesToDeleteColumnFamily.insert(this.processInstanceKey, DbNil.INSTANCE);
  }

  @Override
  public void forEachProcessInstanceToDelete(final Function<DbLong, Boolean> visitor) {
    processInstancesToDeleteColumnFamily.whileTrue(
        (processInstanceKey, nil) -> visitor.apply(processInstanceKey));
  }
}
