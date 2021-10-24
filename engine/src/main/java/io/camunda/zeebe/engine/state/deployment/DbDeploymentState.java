/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.deployment;

import io.camunda.zeebe.db.ColumnFamily;
import io.camunda.zeebe.db.TransactionContext;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.impl.DbCompositeKey;
import io.camunda.zeebe.db.impl.DbInt;
import io.camunda.zeebe.db.impl.DbLong;
import io.camunda.zeebe.db.impl.DbNil;
import io.camunda.zeebe.engine.Loggers;
import io.camunda.zeebe.engine.state.ZbColumnFamilies;
import io.camunda.zeebe.engine.state.mutable.MutableDeploymentState;
import io.camunda.zeebe.protocol.impl.record.value.deployment.DeploymentRecord;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.collections.MutableBoolean;
import org.agrona.collections.MutableLong;
import org.agrona.collections.MutableReference;
import org.slf4j.Logger;

public final class DbDeploymentState implements MutableDeploymentState {

  private static final Logger LOG = Loggers.STREAM_PROCESSING;

  private final DbLong deploymentKey;
  private final DbInt partitionKey;
  private final DbCompositeKey<DbLong, DbInt> deploymentPartitionKey;
  private final ColumnFamily<DbCompositeKey<DbLong, DbInt>, DbNil> pendingDeploymentColumnFamily;

  private final DeploymentRaw deploymentRaw;
  private final ColumnFamily<DbLong, DeploymentRaw> deploymentRawColumnFamily;

  public DbDeploymentState(
      final ZeebeDb<ZbColumnFamilies> zeebeDb, final TransactionContext transactionContext) {

    deploymentKey = new DbLong();
    partitionKey = new DbInt();
    deploymentPartitionKey = new DbCompositeKey<>(deploymentKey, partitionKey);
    pendingDeploymentColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.PENDING_DEPLOYMENT,
            transactionContext,
            deploymentPartitionKey,
            DbNil.INSTANCE);

    deploymentRaw = new DeploymentRaw();
    deploymentRawColumnFamily =
        zeebeDb.createColumnFamily(
            ZbColumnFamilies.DEPLOYMENT_RAW, transactionContext, deploymentKey, deploymentRaw);
  }

  @Override
  public synchronized void addPendingDeploymentDistribution(
      final long deploymentKey, final int partition) {
    this.deploymentKey.wrapLong(deploymentKey);
    partitionKey.wrapInt(partition);
    pendingDeploymentColumnFamily.put(deploymentPartitionKey, DbNil.INSTANCE);
  }

  @Override
  public synchronized void removePendingDeploymentDistribution(
      final long deploymentKey, final int partition) {
    this.deploymentKey.wrapLong(deploymentKey);
    partitionKey.wrapInt(partition);
    pendingDeploymentColumnFamily.delete(deploymentPartitionKey);
  }

  @Override
  public synchronized void storeDeploymentRecord(final long key, final DeploymentRecord value) {
    deploymentKey.wrapLong(key);
    deploymentRaw.setDeploymentRecord(value);
    deploymentRawColumnFamily.put(deploymentKey, deploymentRaw);
  }

  @Override
  public synchronized void removeDeploymentRecord(final long key) {
    deploymentKey.wrapLong(key);
    deploymentRawColumnFamily.delete(deploymentKey);
  }

  @Override
  public synchronized boolean hasPendingDeploymentDistribution(final long deploymentKey) {
    this.deploymentKey.wrapLong(deploymentKey);

    final var hasPending = new MutableBoolean();
    pendingDeploymentColumnFamily.whileEqualPrefix(
        this.deploymentKey,
        (dbLongDbIntDbCompositeKey, dbNil) -> {
          hasPending.set(true);
          return false;
        });

    return hasPending.get();
  }

  @Override
  public synchronized DeploymentRecord getStoredDeploymentRecord(final long key) {
    deploymentKey.wrapLong(key);

    final var storedDeploymentRaw = deploymentRawColumnFamily.get(deploymentKey);

    DeploymentRecord record = null;
    if (storedDeploymentRaw != null) {
      record = storedDeploymentRaw.getDeploymentRecord();
    }

    return record;
  }

  @Override
  public synchronized void foreachPendingDeploymentDistribution(
      final PendingDeploymentVisitor pendingDeploymentVisitor) {

    final MutableReference<DirectBuffer> lastDeployment = new MutableReference<>();
    final MutableLong lastDeploymentKey = new MutableLong(0);
    pendingDeploymentColumnFamily.forEach(
        (compositeKey, nil) -> {
          final var deploymentKey = compositeKey.getFirst().getValue();
          final var partitionId = compositeKey.getSecond().getValue();

          if (lastDeploymentKey.value != deploymentKey) {
            final var deploymentRaw = deploymentRawColumnFamily.get(compositeKey.getFirst());
            if (deploymentRaw == null) {
              LOG.warn(
                  "Expected to find a deployment with key {} for a pending partition {}, but none found. The state is inconsistent.",
                  deploymentKey,
                  partitionId);
              // we ignore this currently
              return;
            }
            lastDeployment.set(BufferUtil.createCopy(deploymentRaw.getDeploymentRecord()));
            lastDeploymentKey.set(deploymentKey);
          }

          pendingDeploymentVisitor.visit(deploymentKey, partitionId, lastDeployment.get());
        });
  }
}
