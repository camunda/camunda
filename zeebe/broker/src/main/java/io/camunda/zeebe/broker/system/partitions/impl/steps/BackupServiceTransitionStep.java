/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.management.BackupService;
import io.camunda.zeebe.backup.management.NoopBackupManager;
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
    if (backupManager != null
        && (shouldInstallOnTransition(targetRole, context.getCurrentRole())
            || targetRole == Role.INACTIVE)) {
      final var closeFuture = backupManager.closeAsync();
      closeFuture.onComplete(
          (ignore, error) -> {
            if (error == null) {
              context.setBackupManager(null);
              context.setCheckpointProcessor(null);
            }
          });
      return closeFuture;
    }
    return CompletableActorFuture.completed(null);
  }

  @Override
  public ActorFuture<Void> transitionTo(
      final PartitionTransitionContext context, final long term, final Role targetRole) {
    final ActorFuture<Void> installed = context.getConcurrencyControl().createFuture();

    if (shouldInstallOnTransition(targetRole, context.getCurrentRole())
        || (context.getBackupManager() == null && targetRole != Role.INACTIVE)) {

      final ActorFuture<Void> backupManagerInstalled;

      if (context.getBackupStore() == null) {
        backupManagerInstalled =
            installNoopBackupManager(
                context, "No BackupStore is configured. Backup operations cannot be executed.");
      } else if (targetRole == Role.LEADER) {
        backupManagerInstalled = installBackupManager(context);
      } else {
        backupManagerInstalled =
            installNoopBackupManager(
                context, "Broker is in follower role. Backup operations cannot be executed.");
      }
      backupManagerInstalled.onComplete(
          (ignore, error) -> {
            if (error == null) {
              installCheckpointProcessor(context, context.getBackupManager());
              installed.complete(null);
            } else {
              installed.completeExceptionally(error);
            }
          });

      return installed;
    }

    return context.getConcurrencyControl().createCompletedFuture();
  }

  @Override
  public String getName() {
    return "BackupManager";
  }

  private static ActorFuture<Void> installNoopBackupManager(
      final PartitionTransitionContext context, final String reasonForNoop) {
    final ActorFuture<Void> backupManagerInstalled;
    final var backupManager = new NoopBackupManager(reasonForNoop);
    context.setBackupManager(backupManager);
    backupManagerInstalled = context.getConcurrencyControl().createCompletedFuture();
    return backupManagerInstalled;
  }

  private ActorFuture<Void> installBackupManager(final PartitionTransitionContext context) {
    // Warn: Here we are assuming how and where segment files are stored. Ideally, we can use
    // SegmentedJournal to collect the segment files. But now SegmentedJournal is build inside Raft
    // and there is no easy way to expose it outside without breaking the abstraction. A preferred
    // solution is to build SegmentedJournal outside of Raft and inject it into Raft. This way we
    // can directly access SegmentedJournal here to find the segment files. But we cannot do that
    // now because SegmentedJournal requires some information from RaftContext in its builder. Until
    // we can refactor SegmentedJournal and build it outside of raft, we have to do this in this
    // hacky way.
    context.getRaftPartition().name();
    final BackupService backupManager =
        new BackupService(
            context.getNodeId(),
            context.getPartitionId(),
            context.getBrokerCfg().getCluster().getPartitionsCount(),
            context.getBackupStore(),
            context.getPersistedSnapshotStore(),
            context.getRaftPartition().dataDirectory().toPath(),
            index -> context.getRaftPartition().getServer().getTailSegments(index),
            context.getPartitionTransitionMeterRegistry(),
            context.getLogStorage(),
            context.getRaftPartition().name());

    final ActorFuture<Void> installed = context.getConcurrencyControl().createFuture();
    context
        .getActorSchedulingService()
        .submitActor(backupManager)
        .onComplete(
            (ignore, error) -> {
              if (error == null) {
                context.setBackupManager(backupManager);
                installed.complete(null);
              } else {
                installed.completeExceptionally(error);
              }
            });
    return installed;
  }

  private static void installCheckpointProcessor(
      final PartitionTransitionContext context, final BackupManager backupManager) {
    final CheckpointRecordsProcessor checkpointRecordsProcessor =
        new CheckpointRecordsProcessor(
            backupManager, context.getPartitionId(), context.getPartitionTransitionMeterRegistry());
    context.setCheckpointProcessor(checkpointRecordsProcessor);
  }

  private boolean shouldInstallOnTransition(final Role newRole, final Role currentRole) {
    return newRole == Role.LEADER
        || (newRole == Role.FOLLOWER && currentRole != Role.CANDIDATE)
        || (newRole == Role.CANDIDATE && currentRole != Role.FOLLOWER);
  }
}
