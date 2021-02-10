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
import io.zeebe.engine.processing.deployment.distribute.PendingDeploymentDistribution;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.mutable.MutableDeploymentState;
import java.util.function.ObjLongConsumer;
import org.agrona.concurrent.UnsafeBuffer;

public final class DbDeploymentState implements MutableDeploymentState {

  private final PendingDeploymentDistribution pendingDeploymentDistribution;

  private final DbLong deploymentKey;
  private final DbInt partitionKey;
  private final DbCompositeKey<DbLong, DbInt> deploymentPartitionKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbInt>, DbNil> newPendingDeploymentColumnFamily;

  private final ColumnFamily<DbLong, PendingDeploymentDistribution> pendingDeploymentColumnFamily;

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

    pendingDeploymentDistribution =
        new PendingDeploymentDistribution(new UnsafeBuffer(0, 0), -1, 0);
    pendingDeploymentColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PENDING_DEPLOYMENT,
            transactionContext,
            deploymentKey,
            pendingDeploymentDistribution);
  }

  @Override
  public void putPendingDeployment(
      final long key, final PendingDeploymentDistribution pendingDeploymentDistribution) {

    deploymentKey.wrapLong(key);
    pendingDeploymentColumnFamily.put(deploymentKey, pendingDeploymentDistribution);
  }

  private PendingDeploymentDistribution getPending(final long key) {
    deploymentKey.wrapLong(key);
    return pendingDeploymentColumnFamily.get(deploymentKey);
  }

  @Override
  public PendingDeploymentDistribution getPendingDeployment(final long key) {
    return getPending(key);
  }

  @Override
  public PendingDeploymentDistribution removePendingDeployment(final long key) {
    final PendingDeploymentDistribution pending = getPending(key);
    if (pending != null) {
      pendingDeploymentColumnFamily.delete(deploymentKey);
    }
    return pending;
  }

  @Override
  public void foreachPending(final ObjLongConsumer<PendingDeploymentDistribution> consumer) {

    pendingDeploymentColumnFamily.forEach(
        (deploymentKey, pendingDeployment) ->
            consumer.accept(pendingDeployment, deploymentKey.getValue()));
  }

  @Override
  public void addPendingDeploymentDistribution(final long deploymentKey, final int partition) {
    this.deploymentKey.wrapLong(deploymentKey);
    partitionKey.wrapInt(partition);
    newPendingDeploymentColumnFamily.put(deploymentPartitionKey, DbNil.INSTANCE);
  }
}
