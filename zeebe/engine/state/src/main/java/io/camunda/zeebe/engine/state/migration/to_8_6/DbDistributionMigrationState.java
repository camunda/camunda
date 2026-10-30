/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_6;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.protocol.ZbColumnFamilies;

public class DbDistributionMigrationState {

  /** [distribution key | partition id] => [DbNil] */
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbInt>, DbNil>
      pendingDistributionColumnFamily;

  /** [distribution key | partition id] => [DbNil] */
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbInt>, DbNil>
      retriableDistributionColumnFamily;

  private final DbLong distributionKey;
  private final DbInt partitionKey;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbInt> distributionPartitionKey;

  public DbDistributionMigrationState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    distributionKey = new DbLong();
    final var fkDistribution =
        new DbForeignKey<>(distributionKey, ZbColumnFamilies.COMMAND_DISTRIBUTION_RECORD);

    partitionKey = new DbInt();
    distributionPartitionKey = new DbCompositeKey<>(fkDistribution, partitionKey);
    pendingDistributionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PENDING_DISTRIBUTION,
            transactionContext,
            distributionPartitionKey,
            DbNil.INSTANCE);

    retriableDistributionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.RETRIABLE_DISTRIBUTION,
            transactionContext,
            distributionPartitionKey,
            DbNil.INSTANCE);
  }

  public void migratePendingDistributionsToRetriableDistributions() {
    pendingDistributionColumnFamily.forEach(retriableDistributionColumnFamily::insert);
  }

  public boolean existsPendingDistribution(final long distributionKey, final int partitionId) {
    this.distributionKey.wrapLong(distributionKey);
    partitionKey.wrapInt(partitionId);

    return pendingDistributionColumnFamily.exists(distributionPartitionKey);
  }

  public boolean existsRetriableDistribution(final long distributionKey, final int partitionId) {
    this.distributionKey.wrapLong(distributionKey);
    partitionKey.wrapInt(partitionId);

    return retriableDistributionColumnFamily.exists(distributionPartitionKey);
  }
}
