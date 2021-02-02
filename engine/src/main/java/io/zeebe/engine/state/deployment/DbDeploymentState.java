/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.deployment;

import io.zeebe.db.ColumnFamily;
import io.zeebe.db.DbContext;
import io.zeebe.db.ZeebeDb;
import io.zeebe.db.impl.DbLong;
import io.zeebe.engine.processing.deployment.distribute.PendingDeploymentDistribution;
import io.zeebe.engine.state.ZbColumnFamilies;
import io.zeebe.engine.state.mutable.MutableDeploymentState;
import java.util.function.ObjLongConsumer;
import org.agrona.concurrent.UnsafeBuffer;

public final class DbDeploymentState implements MutableDeploymentState {
  private final PendingDeploymentDistribution pendingDeploymentDistribution;

  private final DbLong deploymentKey;
  private final ColumnFamily<DbLong, PendingDeploymentDistribution> pendingDeploymentColumnFamily;

  public DbDeploymentState(final ZeebeDb<ZbColumnFamilies> zeebeDb, final DbContext dbContext) {

    deploymentKey = new DbLong();
    pendingDeploymentDistribution =
        new PendingDeploymentDistribution(new UnsafeBuffer(0, 0), -1, 0);
    pendingDeploymentColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PENDING_DEPLOYMENT,
            dbContext,
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
}
