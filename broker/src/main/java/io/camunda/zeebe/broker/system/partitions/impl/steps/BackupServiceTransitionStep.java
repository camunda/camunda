/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.management.BackupService;
import io.camunda.zeebe.backup.processing.CheckpointRecordsProcessor;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;

public final class BackupServiceTransitionStep implements PartitionTransitionStep {

  @Override
  public ActorFuture<Void> prepareTransition(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final BackupManager backupManager = context.getBackupManager();
    if (backupManager != null) {
      final var closeFuture = backupManager.closeAsync();
      closeFuture.onComplete(
          (ignore, error) -> {
            if (error == null) {
              context.setBackupManager(null);
            }
          });
      return closeFuture;
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    if (targetRole != Role.INACTIVE) {
      return installBackupManager(context);
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public String getName() {
    return "BackupManager";
  }

  private ActorFuture<Void> installBackupManager(final PartitionTransitionContext context) {
    final BackupService backupManager =
        new BackupService(
            context.getNodeId(),
            context.getPartitionId(),
            context.getBrokerCfg().getCluster().getPartitionsCount(),
            context.getPersistedSnapshotStore());
    final ActorFuture<Void> installed = context.getConcurrencyControl().createFuture();
    context
        .getActorSchedulingService()
        .submitActor(backupManager)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                context.setBackupManager(backupManager);
                final CheckpointRecordsProcessor checkpointRecordsProcessor =
                    new CheckpointRecordsProcessor(backupManager);
                context.setCheckpointProcessor(checkpointRecordsProcessor);
                installed.complete(null);
              } else {
                installed.completeExceptionally(error);
              }
            });
    return installed;
  }
}
