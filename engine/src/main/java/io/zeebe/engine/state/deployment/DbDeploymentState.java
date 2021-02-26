/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.deployment;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.TransactionContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbCompositeKey;
import io.zeebe.db.impl.DbInt;
import io.zeebe.db.impl.DbLong;
import io.zeebe.db.impl.DbNil;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.mutable.MutableDeploymentState;
import io.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import org.agrona.collections.MutableBoolean;

public final class DbDeploymentState implements MutableDeploymentState {

  private final DbLong deploymentKey;
  private final DbInt partitionKey;
  private final DbCompositeKey<DbLong, DbInt> deploymentPartitionKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbInt>, DbNil> newPendingDeploymentColumnFamily;

  private final DeploymentRaw deploymentRaw;
  private final ColumnFamily<DbLong, DeploymentRaw> deploymentRawColumnFamily;

  public DbDeploymentState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    deploymentKey = new DbLong();
    partitionKey = new DbInt();
    deploymentPartitionKey = new DbCompositeKey<>(deploymentKey, partitionKey);
    newPendingDeploymentColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.NEW_PENDING_DEPLOYMENT,
            transactionContext,
            deploymentPartitionKey,
            DbNil.INSTANCE);

    deploymentRaw = new DeploymentRaw();
    deploymentRawColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPLOYMENT_RAW, transactionContext, deploymentKey, deploymentRaw);
  }

  @Override
  public void addPendingDeploymentDistribution(final long deploymentKey, final int partition) {
    this.deploymentKey.wrapLong(deploymentKey);
    partitionKey.wrapInt(partition);
    newPendingDeploymentColumnFamily.put(deploymentPartitionKey, DbNil.INSTANCE);
  }

  @Override
  public void removePendingDeploymentDistribution(final long deploymentKey, final int partition) {
    this.deploymentKey.wrapLong(deploymentKey);
    partitionKey.wrapInt(partition);
    newPendingDeploymentColumnFamily.delete(deploymentPartitionKey);
  }

  @Override
  public boolean hasPendingDeploymentDistribution(final long deploymentKey) {
    this.deploymentKey.wrapLong(deploymentKey);

    final var hasPending = new MutableBoolean();
    newPendingDeploymentColumnFamily.whileEqualPrefix(
        this.deploymentKey,
        (dbLongDbIntDbCompositeKey, dbNil) -> {
          hasPending.set(true);
          return false;
        });

    return hasPending.get();
  }

  @Override
  public void storeDeploymentRecord(final long key, final DeploymentRecord value) {
    deploymentKey.wrapLong(key);
    deploymentRaw.setDeploymentRecord(value);
    deploymentRawColumnFamily.put(deploymentKey, deploymentRaw);
  }

  @Override
  public void removeDeploymentRecord(final long key) {
    deploymentKey.wrapLong(key);
    deploymentRawColumnFamily.delete(deploymentKey);
  }

  @Override
  public DeploymentRecord getStoredDeploymentRecord(final long key) {
    deploymentKey.wrapLong(key);

    final var storedDeploymentRaw = deploymentRawColumnFamily.get(deploymentKey);

    DeploymentRecord record = null;
    if (storedDeploymentRaw != null) {
      record = storedDeploymentRaw.getDeploymentRecord();
    }

    return record;
  }
}
