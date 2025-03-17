/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.backup;

import io.camunda.webapps.backup.BackupException.*;
import io.camunda.webapps.backup.repository.BackupRepositoryProps;
import io.camunda.webapps.schema.descriptors.backup.BackupPriorities;
import io.camunda.webapps.schema.descriptors.backup.SnapshotIndexCollection;
import io.camunda.zeebe.util.VisibleForTesting;
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

  private final BackupRepository repository;

  public BackupServiceImpl(
      final Executor threadPoolTaskExecutor,
      final BackupPriorities backupPriorities,
      final BackupRepositoryProps backupProps,
      final BackupRepository repository) {
    this.threadPoolTaskExecutor = threadPoolTaskExecutor;
    this.backupPriorities = backupPriorities;
    this.repository = repository;
    this.backupProps = backupProps;
  }

  @Override
  public void deleteBackup(final Long backupId) {
    repository.validateRepositoryExists(backupProps.repositoryName());
    final String repositoryName = backupProps.repositoryName();
    // the number of parts can be dynamic
    final var backupMetadataOpt = repository.getMetadata(repositoryName, backupId);
    if (backupMetadataOpt.isEmpty()) {
      throw new ResourceNotFoundException(
          "Expected to find backup with ID '%d', but no metadata for it found".formatted(backupId));
    }
    final var backupMetadata = backupMetadataOpt.get();
    final int partCount = backupMetadata.partCount();
    for (int partIdx = 1; partIdx <= partCount; partIdx++) {
      final String snapshotName =
          repository.snapshotNameProvider().getSnapshotName(backupMetadata.withPart(partIdx));
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
    final var indexPatternsOrdered = getValidIndexPatterns();
    final int count = indexPatternsOrdered.size();
    final List<String> snapshotNames = new ArrayList<>();
    final String version = getCurrentVersion();
    for (int index = 0; index < indexPatternsOrdered.size(); index++) {
      final SnapshotIndexCollection indexCollection = indexPatternsOrdered.get(index);
      final var partNum = index + 1;
      final Metadata metadata = new Metadata(request.getBackupId(), version, partNum, count);
      final String snapshotName = repository.snapshotNameProvider().getSnapshotName(metadata);

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
                  nextRequest, this::scheduleNextSnapshot, requestsQueue::clear));
      LOGGER.debug("Snapshot picked for execution: {}", nextRequest);
    }
  }

  @VisibleForTesting
  List<SnapshotIndexCollection> getValidIndexPatterns() {
    final var list = new ArrayList<SnapshotIndexCollection>();
    final var missingIndicesList = new ArrayList<String>();
    for (final var indices : backupPriorities.indicesSplitBySnapshot().toList()) {
      final var foundIndices = repository.checkAllIndicesExist(indices.allIndices());
      final var missingNonRequiredIndices =
          indices.skippableIndices().stream().filter(idx -> !foundIndices.contains(idx)).toList();
      final var missingRequiredIndices =
          indices.requiredIndices().stream().filter(idx -> !foundIndices.contains(idx)).toList();
      if (!missingRequiredIndices.isEmpty()) {
        missingIndicesList.addAll(missingRequiredIndices);
        LOGGER.warn(
            "Missing required indices:{}. All indices found are {}",
            missingRequiredIndices,
            foundIndices);
      }
      // skip this part if there is no index, but they are not required
      if (!foundIndices.isEmpty()) {
        list.add(indices.removeSkippableIndices(missingNonRequiredIndices));
      }
    }
    if (!missingIndicesList.isEmpty()) {
      throw new IndexNotFoundException(missingIndicesList);
    }
    return list;
  }

  // TODO Keep in mind this bug: https://github.com/camunda/camunda/issues/20458
  private String getCurrentVersion() {
    return backupProps.version().toLowerCase();
  }
}
