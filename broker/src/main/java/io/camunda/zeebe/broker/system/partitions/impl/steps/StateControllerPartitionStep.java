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
import io.camunda.zeebe.broker.system.partitions.PartitionStartupContext;
import io.camunda.zeebe.broker.system.partitions.PartitionStartupStep;
import io.camunda.zeebe.broker.system.partitions.impl.AtomixRecordEntrySupplierImpl;
import io.camunda.zeebe.broker.system.partitions.impl.StateControllerImpl;
import io.camunda.zeebe.engine.state.DefaultZeebeDbFactory;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;

public class StateControllerPartitionStep implements PartitionStartupStep {

  @Override
  public String getName() {
    return "StateController";
  }

  @Override
  public ActorFuture<PartitionStartupContext> startup(
      final PartitionStartupContext partitionStartupContext) {
    final var runtimeDirectory =
        partitionStartupContext.getRaftPartition().dataDirectory().toPath().resolve("runtime");
    final var databaseCfg = partitionStartupContext.getBrokerCfg().getExperimental().getRocksdb();

    final var stateController =
        new StateControllerImpl(
            partitionStartupContext.getPartitionId(),
            DefaultZeebeDbFactory.defaultFactory(databaseCfg.createRocksDbConfiguration()),
            partitionStartupContext.getConstructableSnapshotStore(),
            partitionStartupContext.getReceivableSnapshotStore(),
            runtimeDirectory,
            new AtomixRecordEntrySupplierImpl(
                partitionStartupContext.getRaftPartition().getServer()),
            StatePositionSupplier::getHighestExportedPosition);

    partitionStartupContext.setStateController(stateController);
    return CompletableActorFuture.completed(partitionStartupContext);
  }

  @Override
  public ActorFuture<PartitionStartupContext> shutdown(
      final PartitionStartupContext partitionStartupContext) {
    try {
      partitionStartupContext.getStateController().close();
    } catch (final Exception e) {
      Loggers.SYSTEM_LOGGER.error(
          "Unexpected error occurred while closing the state snapshot controller for partition {}.",
          partitionStartupContext.getPartitionId(),
          e);
    } finally {
      partitionStartupContext.setStateController(null);
    }

    return CompletableActorFuture.completed(partitionStartupContext);
  }
}
