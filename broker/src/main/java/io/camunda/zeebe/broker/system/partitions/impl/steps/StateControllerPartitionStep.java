/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.camunda.zeebe.broker.Loggers;
import io.camunda.zeebe.broker.logstreams.state.StatePositionSupplier;
import io.camunda.zeebe.broker.system.partitions.PartitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionStep;
import io.camunda.zeebe.broker.system.partitions.impl.AtomixRecordEntrySupplierImpl;
import io.camunda.zeebe.broker.system.partitions.impl.StateControllerImpl;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class StateControllerPartitionStep implements PartitionStep {

  @Override
  public ActorFuture<Void> open(final PartitionContext context) {
    final var runtimeDirectory =
        context.getRaftPartition().dataDirectory().toPath().resolve("runtime");
    final var databaseCfg = context.getBrokerCfg().getExperimental().getRocksdb();

    final var stateController =
        new StateControllerImpl(
            context.getPartitionId(),
            DefaultZeebeDbFactory.defaultFactory(databaseCfg.createRocksDbConfiguration()),
            context
                .getSnapshotStoreSupplier()
                .getConstructableSnapshotStore(context.getPartitionId()),
            context.getSnapshotStoreSupplier().getReceivableSnapshotStore(context.getPartitionId()),
            runtimeDirectory,
            context.getSnapshotReplication(),
            new AtomixRecordEntrySupplierImpl(context.getRaftLogReader()),
            StatePositionSupplier::getHighestExportedPosition);

    context.setSnapshotController(stateController);
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> close(final PartitionContext context) {
    try {
      context.getSnapshotController().close();
    } catch (final Exception e) {
      Loggers.SYSTEM_LOGGER.error(
          "Unexpected error occurred while closing the state snapshot controller for partition {}.",
          context.getPartitionId(),
          e);
    } finally {
      context.setSnapshotController(null);
    }

    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "StateController";
  }
}
