/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThat;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.RestoreStatus.PartitionRestoreState;
import io.camunda.zeebe.dynamic.config.state.ChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.CompletedOperation;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ClusterConfigurationChangeOperation;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.AwaitModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ModeChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionJoinOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionPreRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.PartitionChangeOperation.PartitionRestoreOperation;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.UpdateIncarnationNumberOperation;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;

final class RestoreStatusTest {

  // A restore always uses this sentinel id, but detection must not rely on it; some tests use a
  // different id to prove that.
  private static final long RESTORE_CHANGE_ID = -2L;
  private static final MemberId BROKER_1 = MemberId.from("1");
  private static final Instant STARTED_AT = Instant.parse("2024-01-01T10:00:00Z");

  @Test
  void shouldReturnEmptyWhenNoChange() {
    // given
    final var configuration = ClusterConfiguration.init();

    // when / then
    assertThat(RestoreStatus.of(configuration)).isEmpty();
  }

  @Test
  void shouldReturnEmptyWhenOnlyLastChangeIsPresent() {
    // given
    // even if the last completed change happens to carry the restore sentinel id, there is no
    // pending change plan, so no restore can be in progress.
    final var lastChange =
        new CompletedChange(
            RESTORE_CHANGE_ID, Status.COMPLETED, STARTED_AT, STARTED_AT.plusSeconds(60));
    final var configuration = configurationWith(Optional.of(lastChange), Optional.empty());

    // when / then
    assertThat(RestoreStatus.of(configuration)).isEmpty();
  }

  @Test
  void shouldReturnEmptyForUnrelatedPendingChange() {
    // given
    // an ordinary partition join, not part of a restore, must not be misdetected.
    final var plan =
        new ClusterChangePlan(
            42L,
            1,
            Status.IN_PROGRESS,
            STARTED_AT,
            List.of(),
            List.<ClusterConfigurationChangeOperation>of(
                new PartitionJoinOperation(BROKER_1, 1, 1, true)));
    final var configuration = configurationWith(Optional.empty(), Optional.of(plan));

    // when / then
    assertThat(RestoreStatus.of(configuration)).isEmpty();
  }

  @Test
  void shouldReturnEmptyForPendingModeTransitionWithoutRestoreOps() {
    // given
    // a plain mode transition to PROCESSING that never involved a restore operation.
    final var plan =
        new ClusterChangePlan(
            42L,
            1,
            Status.IN_PROGRESS,
            STARTED_AT,
            List.of(),
            List.<ClusterConfigurationChangeOperation>of(
                new ModeChangeOperation(BROKER_1, Mode.PROCESSING)));
    final var configuration = configurationWith(Optional.empty(), Optional.of(plan));

    // when / then
    assertThat(RestoreStatus.of(configuration)).isEmpty();
  }

  @Test
  void shouldMapInProgressRestoreWithPerPartitionDetail() {
    // given
    // broker 1: partition 1 fully restored, partition 2 pre-restored but restore still pending
    final var pre1 = new PartitionPreRestoreOperation(BROKER_1, 1);
    final var restore1 = new PartitionRestoreOperation(BROKER_1, 1, backups(10L, 11L));
    final var pre2 = new PartitionPreRestoreOperation(BROKER_1, 2);
    final var restore2 = new PartitionRestoreOperation(BROKER_1, 2, backups(20L));
    final var modeChange = new ModeChangeOperation(BROKER_1, Mode.PROCESSING);

    final var plan =
        new ClusterChangePlan(
            RESTORE_CHANGE_ID,
            5,
            Status.IN_PROGRESS,
            STARTED_AT,
            List.of(
                new CompletedOperation(pre1, STARTED_AT.plusSeconds(1)),
                new CompletedOperation(restore1, STARTED_AT.plusSeconds(2)),
                new CompletedOperation(pre2, STARTED_AT.plusSeconds(3))),
            List.<ClusterConfigurationChangeOperation>of(restore2, modeChange));
    final var configuration = configurationWith(Optional.empty(), Optional.of(plan));

    // when
    final var status = RestoreStatus.of(configuration).orElseThrow();

    // then
    assertThat(status.status()).isEqualTo(Status.IN_PROGRESS);
    assertThat(status.changeId()).isEqualTo(RESTORE_CHANGE_ID);
    assertThat(status.startedAt()).isEqualTo(STARTED_AT);
    assertThat(status.brokers()).hasSize(1);

    final var broker = status.brokers().getFirst();
    assertThat(broker.brokerId()).isEqualTo("1");
    assertThat(broker.partitionsRestored()).isEqualTo(1);
    assertThat(broker.partitionsToRestore()).isEqualTo(2);
    assertThat(broker.partitions()).hasSize(2);

    final var partition1 = broker.partitions().get(0);
    assertThat(partition1.partitionId()).isEqualTo(1);
    assertThat(partition1.state()).isEqualTo(PartitionRestoreState.RESTORED);
    assertThat(partition1.backupIds()).containsExactly(10L, 11L);
    assertThat(partition1.completedAt()).isEqualTo(STARTED_AT.plusSeconds(2));

    final var partition2 = broker.partitions().get(1);
    assertThat(partition2.partitionId()).isEqualTo(2);
    assertThat(partition2.state()).isEqualTo(PartitionRestoreState.RESTORING);
    assertThat(partition2.backupIds()).containsExactly(20L);
    assertThat(partition2.completedAt()).isNull();
  }

  @Test
  void shouldMarkPartitionPendingWhenPreRestoreNotDone() {
    // given
    final var pre1 = new PartitionPreRestoreOperation(BROKER_1, 1);
    final var restore1 = new PartitionRestoreOperation(BROKER_1, 1, backups(10L));
    final var plan =
        new ClusterChangePlan(
            RESTORE_CHANGE_ID,
            1,
            Status.IN_PROGRESS,
            STARTED_AT,
            List.of(),
            List.<ClusterConfigurationChangeOperation>of(pre1, restore1));
    final var configuration = configurationWith(Optional.empty(), Optional.of(plan));

    // when
    final var status = RestoreStatus.of(configuration).orElseThrow();

    // then
    final var partition = status.brokers().getFirst().partitions().getFirst();
    assertThat(partition.state()).isEqualTo(PartitionRestoreState.PENDING);
    assertThat(partition.completedAt()).isNull();
    assertThat(status.brokers().getFirst().partitionsRestored()).isZero();
  }

  @Test
  void shouldDetectInProgressRestoreWhileFinishingModeTransition() {
    // given
    // all restore operations completed; only the trailing mode-transition-to-PROCESSING remains
    // pending. This must still be reported as an in-progress restore.
    final var pre1 = new PartitionPreRestoreOperation(BROKER_1, 1);
    final var restore1 = new PartitionRestoreOperation(BROKER_1, 1, backups(10L));
    final var plan =
        new ClusterChangePlan(
            RESTORE_CHANGE_ID,
            3,
            Status.IN_PROGRESS,
            STARTED_AT,
            List.of(
                new CompletedOperation(pre1, STARTED_AT.plusSeconds(1)),
                new CompletedOperation(restore1, STARTED_AT.plusSeconds(2))),
            List.<ClusterConfigurationChangeOperation>of(
                new ModeChangeOperation(BROKER_1, Mode.PROCESSING),
                new AwaitModeChangeOperation(BROKER_1, Mode.PROCESSING)));
    final var configuration = configurationWith(Optional.empty(), Optional.of(plan));

    // when
    final var status = RestoreStatus.of(configuration).orElseThrow();

    // then
    assertThat(status.brokers()).hasSize(1);
    final var partition = status.brokers().getFirst().partitions().getFirst();
    assertThat(partition.state()).isEqualTo(PartitionRestoreState.RESTORED);
  }

  @Test
  void shouldDetectInProgressRestoreWhileFinishingIncarnationNumberUpdate() {
    // given
    // all restore and mode-transition operations completed; only the trailing
    // UpdateIncarnationNumberOperation remains pending. This must still be reported as an
    // in-progress restore.
    final var pre1 = new PartitionPreRestoreOperation(BROKER_1, 1);
    final var restore1 = new PartitionRestoreOperation(BROKER_1, 1, backups(10L));
    final var modeChange = new ModeChangeOperation(BROKER_1, Mode.PROCESSING);
    final var awaitModeChange = new AwaitModeChangeOperation(BROKER_1, Mode.PROCESSING);
    final var plan =
        new ClusterChangePlan(
            RESTORE_CHANGE_ID,
            5,
            Status.IN_PROGRESS,
            STARTED_AT,
            List.of(
                new CompletedOperation(pre1, STARTED_AT.plusSeconds(1)),
                new CompletedOperation(restore1, STARTED_AT.plusSeconds(2)),
                new CompletedOperation(modeChange, STARTED_AT.plusSeconds(3)),
                new CompletedOperation(awaitModeChange, STARTED_AT.plusSeconds(4))),
            List.<ClusterConfigurationChangeOperation>of(
                new UpdateIncarnationNumberOperation(BROKER_1)));
    final var configuration = configurationWith(Optional.empty(), Optional.of(plan));

    // when
    final var status = RestoreStatus.of(configuration).orElseThrow();

    // then
    assertThat(status.brokers()).hasSize(1);
    final var partition = status.brokers().getFirst().partitions().getFirst();
    assertThat(partition.state()).isEqualTo(PartitionRestoreState.RESTORED);
  }

  private static TreeSet<Long> backups(final Long... ids) {
    return new TreeSet<>(List.of(ids));
  }

  private static ClusterConfiguration configurationWith(
      final Optional<CompletedChange> lastChange, final Optional<ChangePlan> pendingChanges) {
    return new ClusterConfiguration(
        2,
        Map.of(),
        lastChange,
        pendingChanges,
        Optional.empty(),
        Optional.empty(),
        0,
        Optional.empty());
  }
}
