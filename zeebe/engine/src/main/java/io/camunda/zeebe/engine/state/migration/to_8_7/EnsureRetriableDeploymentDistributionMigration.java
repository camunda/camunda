/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.migration.to_8_7;

import io.camunda.zeebe.engine.state.distribution.DistributionQueue;
import io.camunda.zeebe.engine.state.migration.MigrationTask;
import io.camunda.zeebe.engine.state.migration.MigrationTaskContext;
import io.camunda.zeebe.engine.state.migration.MutableMigrationTaskContext;
import java.util.HashSet;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This migration is introduced to combat <a
 * href="https://github.com/camunda/camunda/issues/44908">this bug</a>
 */
public class EnsureRetriableDeploymentDistributionMigration implements MigrationTask {

  @Override
  public String getIdentifier() {
    return getClass().getSimpleName();
  }

  @Override
  public boolean needsToRun(final MigrationTaskContext context) {
    final var distributionState = context.processingState().getDistributionState();
    final var queueId = DistributionQueue.DEPLOYMENT.getQueueId();
    final var shouldRun = new AtomicBoolean(false);
    final var partitionsWithRetriableDistribution = new HashSet<Integer>();

    distributionState.foreachPendingDistribution(
        (distributionKey, record) -> {
          // Continue the loop if the distribution is not for the deployment queue.
          if (!Objects.equals(record.getQueueId(), queueId)) {
            return true;
          }

          final var partitionId = record.getPartitionId();

          // If the pending distribution is not queued, we should run the migration. We can stop the
          // loop early.
          if (!distributionState.hasQueuedDistribution(queueId, distributionKey, partitionId)) {
            shouldRun.set(true);
            return false;
          }

          // If the pending distribution is the first in the queue and there is no retriable
          // distribution for the distribution we must run the migration. We can stop the loop
          // early.
          // If there is a retriable distribution we should continue the loop in case other
          // distributions are not queued.
          final var isFirstInQueue = !partitionsWithRetriableDistribution.contains(partitionId);
          if (isFirstInQueue) {
            if (!distributionState.hasRetriableDistribution(distributionKey, partitionId)) {
              shouldRun.set(true);
              return false;
            } else {
              partitionsWithRetriableDistribution.add(partitionId);
              return true;
            }
          }

          return true;
        });

    return shouldRun.get();
  }

  @Override
  public void runMigration(final MutableMigrationTaskContext context) {
    context.processingState().getMigrationState().ensureRetriableDeploymentDistributions();
  }
}
