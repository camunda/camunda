/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl.steps;

import io.atomix.cluster.MemberId;
import io.atomix.raft.RaftServer.Role;
import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.management.BackupService;
import io.camunda.zeebe.backup.processing.CheckpointRecordsProcessor;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionContext;
import io.camunda.zeebe.broker.system.partitions.PartitionTransitionStep;
import io.camunda.zeebe.journal.file.SegmentFile;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Predicate;

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
    // Warn: Here we are assuming how and where segment files are stored. Ideally, we can use
    // SegmentedJournal to collect the segment files. But now SegmentedJournal is build inside Raft
    // and there is no easy way to expose it outside without breaking the abstraction. A preferred
    // solution is to build SegmentedJournal outside of Raft and inject it into Raft. This way we
    // can directly access SegmentedJournal here to find the segment files. But we cannot do that
    // now because SegmentedJournal requires some information from RaftContext in its builder. Until
    // we can refactor SegmentedJournal and build it outside of raft, we have to do this in this
    // hacky way.
    final Predicate<Path> isSegmentsFile =
        path ->
            SegmentFile.isSegmentFile(context.getRaftPartition().name(), path.toFile().getName());
    final BackupService backupManager =
        new BackupService(
            context.getNodeId(),
            context.getPartitionId(),
            context.getBrokerCfg().getCluster().getPartitionsCount(),
            getPartitionMembers(context),
            context.getPersistedSnapshotStore(),
            isSegmentsFile,
            context.getRaftPartition().dataDirectory().toPath());
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

  // Brokers which are members of this partition's replication group
  private static List<Integer> getPartitionMembers(final PartitionTransitionContext context) {
    return context.getRaftPartition().members().stream()
        .map(MemberId::id)
        .map(Integer::parseInt)
        .toList();
  }
}
