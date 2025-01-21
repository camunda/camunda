/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup;

import io.camunda.webapps.backup.BackupException.*;
import io.camunda.webapps.backup.BackupService.SnapshotRequest;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.schema.descriptors.backup.BackupPriorities;
import io.camunda.webapps.schema.descriptors.backup.SnapshotIndexCollection;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BackupServiceImpl implements BackupService {

  private static final Logger LOGGER = LoggerFactory.getLogger(BackupServiceImpl.class);
  private final Executor threadPoolTaskExecutor;
  private final Queue<SnapshotRequest> requestsQueue = new ConcurrentLinkedQueue<>();

  private final BackupPriorities backupPriorities;
  private final BackupRepositoryProps backupProps;

  private final DynamicIndicesProvider dynamicIndicesProvider;

  private final BackupRepository repository;

  private final List<SnapshotIndexCollection> indexPatternsOrdered;

  public BackupServiceImpl(
      final Executor threadPoolTaskExecutor,
      final BackupPriorities backupPriorities,
      final BackupRepositoryProps backupProps,
      final BackupRepository repository,
      final DynamicIndicesProvider dynamicIndicesProvider) {
    this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    this.backupPriorities = backupPriorities;
    indexPatternsOrdered = backupPriorities.indicesSplitBySnapshot().toList();
    this.repository = repository;
    this.backupProps = backupProps;
    this.dynamicIndicesProvider = dynamicIndicesProvider;
  }

  @Override
  public void deleteBackup(final Long backupId) {
    repository.validateRepositoryExists(backupProps.repositoryName());
    final String repositoryName = backupProps.repositoryName();
    final int count = indexPatternsOrdered.size();
    final String version = getCurrentVersion();
    for (int index = 0; index < count; index++) {
      final String snapshotName =
          repository
              .snapshotNameProvider()
              .getSnapshotName(new Metadata(backupId, version, index + 1, count));
      repository.deleteSnapshot(repositoryName, snapshotName);
    }
  }

  @Override
  public TakeBackupResponseDto takeBackup(final TakeBackupRequestDto request) {
    repository.validateRepositoryExists(backupProps.repositoryName());
    repository.validateNoDuplicateBackupId(backupProps.repositoryName(), request.getBackupId());
    if (!requestsQueue.isEmpty()) {
      throw new InvalidRequestException("Another backup is running at the moment");
    } // TODO remove duplicate
    synchronized (requestsQueue) {
      if (!requestsQueue.isEmpty()) {
        throw new InvalidRequestException("Another backup is running at the moment");
      }
      return scheduleSnapshots(request);
    }
  }

  @Override
  public GetBackupStateResponseDto getBackupState(final Long backupId) {
    return repository.getBackupState(backupProps.repositoryName(), backupId);
  }

  @Override
  public List<GetBackupStateResponseDto> getBackups() {
    return repository.getBackups(backupProps.repositoryName());
  }

  TakeBackupResponseDto scheduleSnapshots(final TakeBackupRequestDto request) {
    final String repositoryName = backupProps.repositoryName();
    final int count = indexPatternsOrdered.size();
    final List<String> snapshotNames = new ArrayList<>();
    final String version = getCurrentVersion();
    var index = 1;
    for (var indexCollection : indexPatternsOrdered) {
      final Metadata metadata = new Metadata(request.getBackupId(), version, index++, count);
      final String snapshotName = repository.snapshotNameProvider().getSnapshotName(metadata);
      // Add all the dynamic indices in the last step
      if (index == count + 1) {
        indexCollection =
            indexCollection.addSkippableIndices(dynamicIndicesProvider.getAllDynamicIndices());
      }

      final SnapshotRequest snapshotRequest =
          new SnapshotRequest(repositoryName, snapshotName, indexCollection, metadata);

      LOGGER.debug(
          "Snapshot part {} contains indices {}", metadata.partNo(), indexCollection.allIndices());
      requestsQueue.offer(snapshotRequest);
      LOGGER.debug("Snapshot scheduled: {}", snapshotName);
      snapshotNames.add(snapshotName);
    }
    // schedule next snapshot
    scheduleNextSnapshot();
    return new TakeBackupResponseDto().setScheduledSnapshots(snapshotNames);
  }

  void scheduleNextSnapshot() {
    final SnapshotRequest nextRequest = requestsQueue.poll();
    if (nextRequest != null) {
      threadPoolTaskExecutor.execute(
          () ->
              repository.executeSnapshotting(
                  nextRequest, false, this::scheduleNextSnapshot, requestsQueue::clear));
      LOGGER.debug("Snapshot picked for execution: {}", nextRequest);
    }
  }

  // TODO Keep in mind this bug: https://github.com/camunda/camunda/issues/20458
  private String getCurrentVersion() {
    return backupProps.version().toLowerCase();
  }
}
