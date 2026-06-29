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
import io.camunda.zeebe.broker.transport.backupapi.RecoveryBackupService;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.scheduler.startup.StartupStep;

/**
 * Registers the {@link RecoveryBackupService} that answers backup status, list and range queries
 * from the backup store while the partition is in recovery mode. Skipped when no backup store is
 * configured.
 */
public record BackupServiceStep(int partitionId)
    implements StartupStep<RecoveryPartitionStartupContext> {

  @Override
  public String getName() {
    return "Recovery Partition %d - Backup Service".formatted(partitionId);
  }

  @Override
  public ActorFuture<RecoveryPartitionStartupContext> startup(
      final RecoveryPartitionStartupContext context) {
    final var store = context.getBackupStore();
    if (store == null) {
      return CompletableActorFuture.completed(context.setBackupService(null));
    }
    final var service =
        new RecoveryBackupService(
            context.getBrokerInfo().getNodeId(),
            context.partitionId().id(),
            store,
            context.meterRegistry());
    return context
        .schedulingService()
        .submitActor(service)
        .thenApply(ignored -> context.setBackupService(service), context.getConcurrencyControl());
  }

  @Override
  public ActorFuture<RecoveryPartitionStartupContext> shutdown(
      final RecoveryPartitionStartupContext context) {
    return closeHelper(context.getBackupService())
        .thenApply(ignored -> context.setBackupService(null), context.getConcurrencyControl());
  }
}
