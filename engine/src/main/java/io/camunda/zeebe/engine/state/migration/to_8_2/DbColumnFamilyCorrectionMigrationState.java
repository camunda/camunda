/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.migration.to_8_2;

import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.engine.state.migration.to_8_2.corrections.ColumnFamily48Corrector;
import io.camunda.zeebe.engine.state.migration.to_8_2.corrections.ColumnFamily49Corrector;
import io.camunda.zeebe.engine.state.migration.to_8_2.corrections.ColumnFamily50Corrector;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

public final class DbColumnFamilyCorrectionMigrationState {

  private final ColumnFamily48Corrector columnFamily48Corrector;
  private final ColumnFamily49Corrector columnFamily49Corrector;
  private final ColumnFamily50Corrector columnFamily50Corrector;

  public DbColumnFamilyCorrectionMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    columnFamily48Corrector = new ColumnFamily48Corrector(zeebeDb, transactionContext);
    columnFamily49Corrector = new ColumnFamily49Corrector(zeebeDb, transactionContext);
    columnFamily50Corrector = new ColumnFamily50Corrector(zeebeDb, transactionContext);
  }

  public void correctColumnFamilyPrefix() {
    columnFamily48Corrector.correctColumnFamilyPrefix();
    columnFamily49Corrector.correctColumnFamilyPrefix();
    columnFamily50Corrector.correctColumnFamilyPrefix();
  }
}
