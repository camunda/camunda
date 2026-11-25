/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.exporter.stream;

import static io.camunda.zeebe.broker.exporter.stream.CommonBackupPositionSource.commonBackupPosition;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.protocol.impl.encoding.CheckpointStateResponse.PartitionCheckpointState;
import io.camunda.zeebe.protocol.record.value.management.CheckpointType;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class CommonBackupPositionSourceTest {

  private static final List<Integer> PARTITIONS = List.of(1, 2, 3);

  @Test
  void shouldReturnOwnPositionWhenAllPartitionsHaveTheSameLatestBackup() {
    // given
    final var latestBackups = Set.of(backup(1, 5, 100), backup(2, 5, 250), backup(3, 5, 80));

    // when
    final var position = commonBackupPosition(latestBackups, PARTITIONS, 2);

    // then
    assertThat(position).hasValue(250);
  }

  @Test
  void shouldReturnEmptyWhileABackupIsStillInProgressOnSomePartitions() {
    // given partition 3 has not confirmed checkpoint 6 yet
    final var latestBackups = Set.of(backup(1, 6, 120), backup(2, 6, 270), backup(3, 5, 80));

    // when
    final var position = commonBackupPosition(latestBackups, PARTITIONS, 1);

    // then
    assertThat(position).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenAPartitionFailedTheLatestBackup() {
    // given partition 2 failed checkpoint 5 but confirmed checkpoint 6, which partition 1 has not
    final var latestBackups = Set.of(backup(1, 5, 100), backup(2, 6, 270), backup(3, 6, 90));

    // when
    final var position = commonBackupPosition(latestBackups, PARTITIONS, 1);

    // then
    assertThat(position).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenPartitionsHaveNoLatestBackupInCommon() {
    // given every partition reports a different latest backup
    final var latestBackups = Set.of(backup(1, 1, 10), backup(2, 2, 20), backup(3, 3, 30));

    // when
    final var position = commonBackupPosition(latestBackups, PARTITIONS, 1);

    // then
    assertThat(position).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenSomePartitionsHaveNoBackup() {
    // given
    final var latestBackups = Set.of(backup(1, 5, 100), backup(2, 5, 250));

    // when
    final var position = commonBackupPosition(latestBackups, PARTITIONS, 1);

    // then
    assertThat(position).isEmpty();
  }

  @Test
  void shouldReturnNothingBackedUpWhenNoPartitionHasABackup() {
    // when
    final var position = commonBackupPosition(Set.of(), PARTITIONS, 1);

    // then
    assertThat(position).hasValue(-1);
  }

  private static PartitionCheckpointState backup(
      final int partitionId, final long checkpointId, final long checkpointPosition) {
    return new PartitionCheckpointState(
        partitionId, checkpointId, CheckpointType.SCHEDULED_BACKUP, 0L, checkpointPosition, 1L);
  }
}
