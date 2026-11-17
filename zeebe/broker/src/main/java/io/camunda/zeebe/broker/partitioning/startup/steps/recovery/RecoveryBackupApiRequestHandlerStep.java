/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps.recovery;

import static io.camunda.zeebe.scheduler.AsyncClosable.closeHelper;

import io.camunda.cluster.PartitionId;
import io.camunda.zeebe.broker.partitioning.RecoveryPartitionStartupContext;
import io.camunda.zeebe.broker.transport.backupapi.ReadOnlyBackupApiRequestHandler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;

/**
 * Registers the {@link ReadOnlyBackupApiRequestHandler} on the gateway-to-broker transport so the
 * partition can serve the read-only subset of the backup API while in recovery mode. The handler is
 * always installed; when no backup store is configured it responds with a "feature disabled" error.
 */
public record RecoveryBackupApiRequestHandlerStep(PartitionId partitionId)
    implements StartupStep<RecoveryPartitionStartupContext> {

  @Override
  public String getName() {
    return "Recovery Partition %d - Backup API Request Handler".formatted(partitionId.number());
  }

  @Override
  public ActorFuture<RecoveryPartitionStartupContext> startup(
      final RecoveryPartitionStartupContext context) {
    final var handler =
        new ReadOnlyBackupApiRequestHandler(
            context.getBackupService(),
            partitionId,
            context.getGatewayBrokerTransport(),
            context.getBackupStore() != null);

    return context
        .schedulingService()
        .submitActor(handler)
        .thenApply(
            ignored -> context.setBackupApiRequestHandler(handler),
            context.getConcurrencyControl());
  }

  @Override
  public ActorFuture<RecoveryPartitionStartupContext> shutdown(
      final RecoveryPartitionStartupContext context) {

    return closeHelper(context.getBackupApiRequestHandler())
        .thenApply(
            ignored -> context.setBackupApiRequestHandler(null), context.getConcurrencyControl());
  }
}
