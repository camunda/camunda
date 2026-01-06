/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.management;

import io.camunda.zeebe.backup.api.BackupManager;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.common.BackupStatusImpl;
import io.camunda.zeebe.backup.metrics.BackupManagerMetrics;
import io.camunda.zeebe.logstreams.log.LogStreamWriter;
import io.camunda.zeebe.scheduler.Actor;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.snapshots.PersistedSnapshotStore;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Backup manager that takes and manages backup asynchronously */
public final class BackupService extends Actor implements BackupManager {
  private static final Logger LOG = LoggerFactory.getLogger(BackupService.class);
  private final String actorName;
  private final JournalInfoProvider journalInfoProvider;
  private final int nodeId;
  private final int partitionId;
  private final int numberOfPartitions;
  private final BackupServiceImpl internalBackupManager;
  private final PersistedSnapshotStore snapshotStore;
  private final Path segmentsDirectory;
  private final BackupManagerMetrics metrics;

  public BackupService(
      final int nodeId,
      final int partitionId,
      final int numberOfPartitions,
      final BackupStore backupStore,
      final PersistedSnapshotStore snapshotStore,
      final Path segmentsDirectory,
      final JournalInfoProvider raftMetadataProvider,
      final MeterRegistry partitionRegistry,
      final LogStreamWriter logStreamWriter) {
    this.nodeId = nodeId;
    this.partitionId = partitionId;
    this.numberOfPartitions = numberOfPartitions;
    this.snapshotStore = snapshotStore;
    this.segmentsDirectory = segmentsDirectory;
    metrics = new BackupManagerMetrics(partitionRegistry);
    internalBackupManager = new BackupServiceImpl(backupStore, logStreamWriter);
    actorName = buildActorName("BackupService", partitionId);
    journalInfoProvider = raftMetadataProvider;
  }

  @Override
  public String getName() {
    return actorName;
  }

  @Override
  protected void onActorClosing() {
    internalBackupManager.close();
    metrics.cancelInProgressOperations();
  }

  @Override
  public ActorFuture<Void> takeBackup(
      final long checkpointId, final long checkpointPosition, final int partitionCount) {
    final ActorFuture<Void> result = createFuture();
    actor.run(
        () -> {
          final InProgressBackupImpl inProgressBackup =
              new InProgressBackupImpl(
                  snapshotStore,
                  getBackupId(checkpointId),
                  checkpointPosition,
                  partitionCount,
                  actor,
                  segmentsDirectory,
                  journalInfoProvider);

          final var opMetrics = metrics.startTakingBackup();
          final var backupResult = internalBackupManager.takeBackup(inProgressBackup, actor);
          backupResult.onComplete(opMetrics::complete);

          backupResult.onComplete(
              (ignore, error) -> {
                if (error != null) {
                  LOG.warn(
                      "Failed to take backup {} at position {}",
                      inProgressBackup.checkpointId(),
                      inProgressBackup.checkpointPosition(),
                      error);
                } else {
                  LOG.info(
                      "Backup {} at position {} completed with {} partitions",
                      inProgressBackup.checkpointId(),
                      inProgressBackup.checkpointPosition(),
                      partitionCount);
                }
              });
          backupResult.onComplete(result);
        });
    return result;
  }

  @Override
  public ActorFuture<BackupStatus> getBackupStatus(final long checkpointId) {
    final var operationMetrics = metrics.startQueryingStatus();

    final var future = new CompletableActorFuture<BackupStatus>();
    internalBackupManager
        .getBackupStatus(partitionId, checkpointId, actor)
        .onComplete(
            (backupStatus, throwable) -> {
              if (throwable != null) {
                LOG.warn("Failed to query status of backup {}", checkpointId, throwable);
                future.completeExceptionally(throwable);
              } else {
                if (backupStatus.isEmpty()) {
                  future.complete(
                      new BackupStatusImpl(
                          getBackupId(checkpointId),
                          Optional.empty(),
                          BackupStatusCode.DOES_NOT_EXIST,
                          Optional.empty(),
                          Optional.empty(),
                          Optional.empty()));
                } else {
                  future.complete(backupStatus.get());
                }
              }
            });
    future.onComplete(operationMetrics::complete);
    return future;
  }

  @Override
  public ActorFuture<Collection<BackupStatus>> listBackups(final String pattern) {
    final var operationMetrics = metrics.startListingBackups();
    final var resultFuture = internalBackupManager.listBackups(partitionId, pattern, actor);
    resultFuture.onComplete(operationMetrics::complete);
    resultFuture.onComplete(
        (ignore, error) -> {
          if (error != null) {
            LOG.warn("Failed to list backups", error);
          }
        });
    return resultFuture;
  }

  @Override
  public ActorFuture<Void> deleteBackup(final long checkpointId) {
    final var operationMetrics = metrics.startDeleting();

    final var backupDeleted = internalBackupManager.deleteBackup(partitionId, checkpointId, actor);

    backupDeleted.onComplete(operationMetrics::complete);
    backupDeleted.onComplete(
        (ignore, error) -> {
          if (error != null) {
            LOG.warn("Failed to delete backup {}", checkpointId, error);
          }
        });

    return backupDeleted;
  }

  @Override
  public void failInProgressBackup(final long lastCheckpointId) {
    internalBackupManager.failInProgressBackups(partitionId, lastCheckpointId, actor);
  }

  @Override
  public void createFailedBackup(
      final long checkpointId, final long checkpointPosition, final String failureReason) {
    actor.run(
        () -> {
          final var backupId = getBackupId(checkpointId);
          internalBackupManager.createFailedBackup(
              backupId, checkpointPosition, failureReason, actor);
        });
  }

  private BackupIdentifierImpl getBackupId(final long checkpointId) {
    return new BackupIdentifierImpl(nodeId, partitionId, checkpointId);
  }
}
