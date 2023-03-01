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
import io.camunda.zeebe.protocol.impl.record.value.distribution.CommandDistributionRecord;
import org.agrona.collections.MutableBoolean;

public class DbDistributionState implements MutableDistributionState {

  private final DbLong distributionKey;
  private final DbInt partitionKey;
  private final DbCompositeKey<DbLong, DbInt> distributionPartitionKey;
  /** [distribution key | partition id] => [pending distributions] */
  private final ColumnFamily<DbCompositeKey<DbLong, DbInt>, DbNil> pendingDistributionColumnFamily;

  private final CommandValueAndValueTypeWrapper commandValueAndValueTypeWrapper;
  /** [distribution key] => [ValueType and RecordValue of distributed command] */
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

  @Override
  public void addCommandDistribution(
      final long distributionKey, final CommandDistributionRecord commandDistributionRecord) {
    this.distributionKey.wrapLong(distributionKey);
    commandDistributionRecordColumnFamily.insert(
        this.distributionKey,
        new CommandValueAndValueTypeWrapper().wrap(commandDistributionRecord));
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
  public CommandDistributionRecord getCommandDistributionRecord(
      final long distributionKey, final int partition) {
    this.distributionKey.wrapLong(distributionKey);
    partitionKey.wrapInt(partition);

    final var storedDistribution = commandDistributionRecordColumnFamily.get(this.distributionKey);

    if (storedDistribution == null) {
      return null;
    }

    return new CommandDistributionRecord()
        .setPartitionId(partition)
        .setValueType(storedDistribution.getValueType())
        .setRecordValue(storedDistribution.getCommandValue());
  }
}
