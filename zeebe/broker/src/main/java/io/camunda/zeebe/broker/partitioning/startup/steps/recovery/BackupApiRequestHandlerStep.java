/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.partitioning.startup.steps.recovery;

import static io.camunda.zeebe.scheduler.AsyncClosable.closeHelper;

import io.camunda.zeebe.broker.partitioning.RecoveryPartitionStartupContext;
import io.camunda.zeebe.broker.transport.backupapi.RecoveryBackupApiRequestHandler;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;
import io.camunda.zeebe.transport.RequestType;

/**
 * Registers the {@link RecoveryBackupApiRequestHandler} on the gateway-to-broker transport so the
 * partition can serve the read-only subset of the backup API while in recovery mode. The handler is
 * always installed; when no backup store is configured it responds with a "feature disabled" error.
 */
public record BackupApiRequestHandlerStep(int partitionId)
    implements StartupStep<RecoveryPartitionStartupContext> {

  @Override
  public String getName() {
    return "Recovery Partition %d - Backup API Request Handler".formatted(partitionId);
  }

  @Override
  public ActorFuture<RecoveryPartitionStartupContext> startup(
      final RecoveryPartitionStartupContext context) {
    final var handler =
        new RecoveryBackupApiRequestHandler(
            context.getGatewayBrokerTransport(),
            context.getBackupService(),
            context.partitionId(),
            context.getBackupStore() != null);

    return context
        .getGatewayBrokerTransport()
        .subscribe(context.partitionId(), RequestType.BACKUP, handler)
        .andThen(
            ignored -> context.schedulingService().submitActor(handler),
            context.getConcurrencyControl())
        .thenApply(
            ignored -> context.setBackupApiRequestHandler(handler),
            context.getConcurrencyControl());
  }

  @Override
  public ActorFuture<RecoveryPartitionStartupContext> shutdown(
      final RecoveryPartitionStartupContext context) {

    return context
        .getGatewayBrokerTransport()
        .unsubscribe(context.partitionId(), RequestType.BACKUP)
        .thenApply(ignored -> closeHelper(context.getBackupApiRequestHandler()))
        .thenApply(
            ignored -> context.setBackupApiRequestHandler(null), context.getConcurrencyControl());
  }
}
