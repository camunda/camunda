/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.distribution;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.mutable.MutableDistributionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

public class DbDistributionState implements MutableDistributionState {

  private final DbLong distributionKey;
  private final DbInt partitionKey;
  private final DbCompositeKey<DbLong, DbInt> distributionPartitionKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbInt>, DbNil> pendingDistributionColumnFamily;

  private final CommandValueAndValueTypeWrapper commandValueAndValueTypeWrapper;
  private final ColumnFamily<DbLong, CommandValueAndValueTypeWrapper>
      commandDistributionRecordColumnFamily;

  public DbDistributionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    distributionKey = new DbLong();
    partitionKey = new DbInt();
    distributionPartitionKey = new DbCompositeKey<>(distributionKey, partitionKey);
    pendingDistributionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PENDING_DISTRIBUTION,
            transactionContext,
            distributionPartitionKey,
            DbNil.INSTANCE);

    commandValueAndValueTypeWrapper = new CommandValueAndValueTypeWrapper();
    commandDistributionRecordColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.COMMAND_DISTRIBUTION_RECORD,
            transactionContext,
            distributionKey,
            commandValueAndValueTypeWrapper);
  }
}
