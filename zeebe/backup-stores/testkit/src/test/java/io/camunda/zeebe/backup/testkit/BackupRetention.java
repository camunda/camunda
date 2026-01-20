/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.testkit;

import io.camunda.zeebe.backup.api.Backup;
import io.camunda.zeebe.backup.api.BackupIdentifier;
import io.camunda.zeebe.backup.api.BackupRangeMarker;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupIdentifierImpl;
import io.camunda.zeebe.backup.testkit.support.TestBackupProvider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Backup store acceptance tests related to cross-partition and cross-node backup deletion required
 * for backup retention mechanism.
 */
public interface BackupRetention {

  int PARTITION_COUNT = 3;
  int NODE_COUNT = 3;

  int maxDeleteBatchSize();

  BackupStore getStore();

  @Test
  default void backupsDoNotExistAfterDeleteAcrossPartitions() throws IOException {
    // given
    final var backups = provideBackups(PARTITION_COUNT, NODE_COUNT, 4, true);
    final var saveFutures =
        backups.parallelStream()
            .map(backup -> getStore().save(backup))
            .toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(saveFutures).join();

    // when
    final var ids = backups.stream().map(Backup::id).toList();
    getStore().delete(ids).join();

    // then
    ids.parallelStream()
        .forEach(
            id ->
                Assertions.assertThat(getStore().getStatus(id).join())
                    .returns(
                        BackupStatusCode.DOES_NOT_EXIST,
                        Assertions.from(BackupStatus::statusCode)));
  }

  @Test
  default void backupsDoNotExistAfterDeleteAcrossPartitionsBatching() throws IOException {
    // given
    final var backups =
        provideBackups(PARTITION_COUNT, NODE_COUNT, maxDeleteBatchSize() / PARTITION_COUNT, true);
    final var saveFutures =
        backups.stream().map(backup -> getStore().save(backup)).toArray(CompletableFuture[]::new);
    CompletableFuture.allOf(saveFutures).join();

    // when
    final var ids = backups.stream().map(Backup::id).toList();
    getStore().delete(ids).join();

    // then
    ids.parallelStream()
        .forEach(
            id ->
                Assertions.assertThat(getStore().getStatus(id).join())
                    .returns(
                        BackupStatusCode.DOES_NOT_EXIST,
                        Assertions.from(BackupStatus::statusCode)));
  }

  @Test
  default void rangeMarkersDoNotExistAfterBatchDeletion() throws IOException {
    // given
    final Map<Integer, List<BackupRangeMarker>> markers =
        provideBackups(PARTITION_COUNT, NODE_COUNT, 4, false).stream()
            .map(Backup::id)
            .collect(
                Collectors.groupingBy(
                    BackupIdentifier::partitionId,
                    Collectors.mapping(
                        id -> new BackupRangeMarker.Start(id.checkpointId()),
                        Collectors.toList())));

    markers.entrySet().parallelStream()
        .forEach(
            entry ->
                entry
                    .getValue()
                    .forEach(marker -> getStore().storeRangeMarker(entry.getKey(), marker).join()));

    // when
    markers.entrySet().parallelStream()
        .forEach(entry -> getStore().deleteRangeMarkers(entry.getKey(), entry.getValue()).join());

    // then
    markers.entrySet().parallelStream()
        .forEach(
            entry ->
                Assertions.assertThat(getStore().rangeMarkers(entry.getKey()).join()).isEmpty());
  }

  default List<Backup> provideBackups(
      final int partitionCount,
      final int nodeCount,
      final int numberOfBackups,
      final boolean withFiles)
      throws IOException {
    final List<Backup> backups = new ArrayList<>();
    for (int partitionId = 1; partitionId <= partitionCount; partitionId++) {
      for (int nodeId = 0; nodeId < nodeCount; nodeId++) {
        for (int checkpointId = 1; checkpointId <= numberOfBackups; checkpointId++) {
          final var backupId = new BackupIdentifierImpl(nodeId, partitionId, checkpointId);
          final var backup =
              withFiles
                  ? TestBackupProvider.simpleBackupWithId(backupId)
                  : TestBackupProvider.minimalBackupWithId(backupId);
          backups.add(backup);
        }
      }
    }
    return backups;
  }
}
