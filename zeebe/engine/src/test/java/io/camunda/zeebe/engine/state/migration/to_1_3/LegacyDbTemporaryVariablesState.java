/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_1_3;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.engine.common.state.migration.TemporaryVariables;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import org.agrona.DirectBuffer;

public class LegacyDbTemporaryVariablesState {

  private final DbLong temporaryVariablesKeyInstance;
  private final TemporaryVariables temporaryVariables;
  private final ColumnFamily<DbLong, TemporaryVariables> temporaryVariableColumnFamily;

  public LegacyDbTemporaryVariablesState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    temporaryVariablesKeyInstance = new DbLong();
    temporaryVariables = new TemporaryVariables();
    temporaryVariableColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.TEMPORARY_VARIABLE_STORE,
            transactionContext,
            temporaryVariablesKeyInstance,
            temporaryVariables);
  }

  public void put(final long key, final DirectBuffer variables) {
    temporaryVariables.reset();
    temporaryVariables.set(variables);
    temporaryVariablesKeyInstance.wrapLong(key);

    temporaryVariableColumnFamily.upsert(temporaryVariablesKeyInstance, temporaryVariables);
  }

  public boolean isEmpty() {
    return temporaryVariableColumnFamily.isEmpty();
  }
}
