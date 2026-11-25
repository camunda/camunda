/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.zeebe.backup.client.api.BackupApi;
import io.camunda.zeebe.backup.client.api.BackupStatus;
import io.camunda.zeebe.backup.client.api.PartitionBackupStatus;
import io.camunda.zeebe.backup.client.api.State;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse;
import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.management.BackupStatusCode;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;

final class CommonBackupPositionSourceTest {

  private static final String TENANT = "default";
  private static final List<Integer> PARTITIONS = List.of(1, 2, 3);
  private static final Duration WAIT = Duration.ofSeconds(5);

  private final BackupApi backupApi = mock(BackupApi.class);
  private final CommonBackupPositionSource source =
      new CommonBackupPositionSource(backupApi, TENANT, 2, () -> PARTITIONS);

  @Test
  void shouldReturnOwnPositionWhenAllPartitionsCompletedTheSameLatestBackup() {
    // given
    latestBackups(backup(1, 5, 100), backup(2, 5, 250), backup(3, 5, 80));
    backupStatus(5, State.COMPLETED, completed(1, 100), completed(2, 250), completed(3, 80));

    // when
    final var position = source.backedUpPosition();

    // then
    assertThat(position).succeedsWithin(WAIT).isEqualTo(OptionalLong.of(250));
  }

  @Test
  void shouldAdvanceWhenPartitionsNeverReportTheSameLatestBackup() {
    // given backups overlap, so every partition is at a different latest backup
    latestBackups(backup(1, 1, 10), backup(2, 2, 270), backup(3, 3, 30));
    // and the slowest partition's latest backup is completed on all of them
    backupStatus(1, State.COMPLETED, completed(1, 10), completed(2, 200), completed(3, 20));

    // when
    final var position = source.backedUpPosition();

    // then
    assertThat(position).succeedsWithin(WAIT).isEqualTo(OptionalLong.of(200));
  }

  @Test
  void shouldNotAdvanceWhenTheCandidateFailedOnAPartition() {
    // given partition 2 failed checkpoint 5 and already confirmed checkpoint 6
    latestBackups(backup(1, 5, 100), backup(2, 6, 270), backup(3, 6, 90));
    backupStatus(
        5,
        State.FAILED,
        completed(1, 100),
        status(2, BackupStatusCode.FAILED, OptionalLong.empty()),
        completed(3, 80));

    // when
    final var position = source.backedUpPosition();

    // then
    assertThat(position).succeedsWithin(WAIT).isEqualTo(OptionalLong.empty());
  }

  @Test
  void shouldNotAdvanceWhenSomePartitionsHaveNoBackup() {
    // given
    latestBackups(backup(1, 5, 100), backup(2, 5, 250));

    // when
    final var position = source.backedUpPosition();

    // then
    assertThat(position).succeedsWithin(WAIT).isEqualTo(OptionalLong.empty());
    verify(backupApi, never()).getStatus(anyString(), anyLong());
  }

  @Test
  void shouldReportNothingBackedUpWhenNoPartitionHasABackup() {
    // given
    latestBackups();

    // when
    final var position = source.backedUpPosition();

    // then
    assertThat(position).succeedsWithin(WAIT).isEqualTo(OptionalLong.of(-1));
  }

  @Test
  void shouldNotReadTheStatusAgainOnceTheCandidateIsConfirmed() {
    // given
    latestBackups(backup(1, 5, 100), backup(2, 5, 250), backup(3, 6, 80));
    backupStatus(5, State.COMPLETED, completed(1, 100), completed(2, 250), completed(3, 60));
    assertThat(source.backedUpPosition()).succeedsWithin(WAIT);

    // when
    final var position = source.backedUpPosition();

    // then
    assertThat(position).succeedsWithin(WAIT).isEqualTo(OptionalLong.of(250));
    verify(backupApi, times(1)).getStatus(TENANT, 5);
  }

  private void latestBackups(final PartitionCheckpointState... backups) {
    final var response = new CheckpointStateResponse();
    response.setBackupStates(Set.of(backups));
    when(backupApi.getCheckpointState(TENANT))
        .thenReturn(CompletableFuture.completedFuture(response));
  }

  private void backupStatus(
      final long backupId, final State state, final PartitionBackupStatus... partitions) {
    when(backupApi.getStatus(TENANT, backupId))
        .thenReturn(
            CompletableFuture.completedFuture(
                new BackupStatus(backupId, state, Optional.empty(), Arrays.asList(partitions))));
  }

  private static PartitionCheckpointState backup(
      final int partitionId, final long checkpointId, final long checkpointPosition) {
    return new PartitionCheckpointState(
        partitionId, checkpointId, CheckpointType.SCHEDULED_BACKUP, 0L, checkpointPosition, 1L);
  }

  private static PartitionBackupStatus completed(
      final int partitionId, final long checkpointPosition) {
    return status(partitionId, BackupStatusCode.COMPLETED, OptionalLong.of(checkpointPosition));
  }

  private static PartitionBackupStatus status(
      final int partitionId, final BackupStatusCode code, final OptionalLong checkpointPosition) {
    return new PartitionBackupStatus(
        partitionId,
        code,
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        Optional.empty(),
        OptionalLong.empty(),
        checkpointPosition,
        OptionalInt.empty(),
        Optional.empty());
  }
}
