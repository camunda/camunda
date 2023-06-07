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
import io.camunda.zeebe.db.impl.DbForeignKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.state.mutable.MutableDistributionState;
import io.camunda.zeebe.protocol.ZbColumnFamilies;
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import org.agrona.collections.MutableBoolean;

public class DbDistributionState implements MutableDistributionState {

  private final DbLong distributionKey;
  private final DbForeignKey<DbLong> fkDistribution;
  private final DbInt partitionKey;
  private final DbCompositeKey<DbForeignKey<DbLong>, DbInt> distributionPartitionKey;

  /** [distribution key | partition id] => [DbNil] */
  private final ColumnFamily<DbCompositeKey<DbForeignKey<DbLong>, DbInt>, DbNil>
      pendingDistributionColumnFamily;

  private final PersistedCommandDistribution persistedCommandDistribution;

  /** [distribution key] => [persisted command distribution] */
  private final ColumnFamily<DbLong, PersistedCommandDistribution>
      commandDistributionRecordColumnFamily;

  public DbDistributionState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {
    distributionKey = new DbLong();
    fkDistribution =
        new DbForeignKey<>(distributionKey, ZbColumnFamilies.COMMAND_DISTRIBUTION_RECORD);
    persistedCommandDistribution = new PersistedCommandDistribution();
    commandDistributionRecordColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.COMMAND_DISTRIBUTION_RECORD,
            transactionContext,
            distributionKey,
            persistedCommandDistribution);

    partitionKey = new DbInt();
    distributionPartitionKey = new DbCompositeKey<>(fkDistribution, partitionKey);
    pendingDistributionColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PENDING_DISTRIBUTION,
            transactionContext,
            distributionPartitionKey,
            DbNil.INSTANCE);
  }

  @Override
  public void addCommandDistribution(
      final long distributionKey, final CommandDistributionRecord commandDistributionRecord) {
    this.distributionKey.wrapLong(distributionKey);
    commandDistributionRecordColumnFamily.insert(
        this.distributionKey, new PersistedCommandDistribution().wrap(commandDistributionRecord));
  }

  @Override
  public void removeCommandDistribution(final long distributionKey) {
    this.distributionKey.wrapLong(distributionKey);
    commandDistributionRecordColumnFamily.deleteIfExists(this.distributionKey);
  }

  @Override
  public void addPendingDistribution(final long distributionKey, final int partition) {
    this.distributionKey.wrapLong(distributionKey);
    partitionKey.wrapInt(partition);
    pendingDistributionColumnFamily.insert(distributionPartitionKey, DbNil.INSTANCE);
  }

  @Override
  public void removePendingDistribution(final long distributionKey, final int partition) {
    this.distributionKey.wrapLong(distributionKey);
    partitionKey.wrapInt(partition);
    pendingDistributionColumnFamily.deleteExisting(distributionPartitionKey);
  }

  @Override
  public boolean hasPendingDistribution(final long distributionKey) {
    this.distributionKey.wrapLong(distributionKey);

    final var hasPending = new MutableBoolean();
    pendingDistributionColumnFamily.whileEqualPrefix(
        this.distributionKey,
        (compositeKey, dbNil) -> {
          hasPending.set(true);
          return false;
        });

    return hasPending.get();
  }

  @Override
  public boolean hasPendingDistribution(final long distributionKey, final int partition) {
    this.distributionKey.wrapLong(distributionKey);
    partitionKey.wrapInt(partition);
    return pendingDistributionColumnFamily.exists(distributionPartitionKey);
  }

  @Override
  public CommandDistributionRecord getCommandDistributionRecord(
      final long distributionKey, final int partition) {
    this.distributionKey.wrapLong(distributionKey);

    final var persistedDistribution =
        commandDistributionRecordColumnFamily.get(this.distributionKey);

    if (persistedDistribution == null) {
      return null;
    }

    return new CommandDistributionRecord()
        .setPartitionId(partition)
        .setValueType(persistedDistribution.getValueType())
        .setRecordValue(persistedDistribution.getCommandValue());
  }
}
