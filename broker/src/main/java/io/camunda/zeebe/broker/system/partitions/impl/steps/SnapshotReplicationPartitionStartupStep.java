/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.system.partitions.PartitionStartupContext;
import io.camunda.zeebe.broker.system.partitions.PartitionStartupStep;
import io.camunda.zeebe.broker.system.partitions.SnapshotReplication;
import io.camunda.zeebe.broker.system.partitions.impl.NoneSnapshotReplication;
import io.camunda.zeebe.broker.system.partitions.impl.StateReplication;
import io.camunda.zeebe.util.Either;
import java.util.function.Consumer;

public class SnapshotReplicationPartitionStartupStep implements PartitionStartupStep {

  @Override
  public String getName() {
    return "SnapshotReplication";
  }

  @Override
  public void startup(
      final PartitionStartupContext context,
      final Consumer<Either<Throwable, PartitionStartupContext>> callback) {

    try {
      final SnapshotReplication replication =
          shouldReplicateSnapshots(context)
              ? new StateReplication(
                  context.getMessagingService(), context.getPartitionId(), context.getNodeId())
              : new NoneSnapshotReplication();

      context.setSnapshotReplication(replication);
      callback.accept(Either.right(context));
    } catch (final Throwable t) {
      callback.accept(Either.left(t));
    }
  }

  @Override
  public void shutdown(
      final PartitionStartupContext context,
      final Consumer<Either<Throwable, PartitionStartupContext>> callback) {

    try {
      if (context.getSnapshotReplication() != null) {
        context.getSnapshotReplication().close();
      }

      callback.accept(Either.right(context));
    } catch (final Throwable t) {
      Loggers.SYSTEM_LOGGER.error(
          "Unexpected error closing state replication for partition {}",
          context.getPartitionId(),
          t);
      callback.accept(Either.left(t));
    } finally {
      context.setSnapshotReplication(null);
    }
  }

  private boolean shouldReplicateSnapshots(final PartitionStartupContext state) {
    return state.getBrokerCfg().getCluster().getReplicationFactor() > 1;
  }
}
