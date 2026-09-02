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
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.DependencyChangePlan;
import io.camunda.zeebe.dynamic.config.state.Mode;
import io.camunda.zeebe.dynamic.config.state.OperationGraph;
import io.camunda.zeebe.dynamic.config.state.OperationId;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupConfiguration;
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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.UnaryOperator;
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
    final var configuration = PartitionGroupConfiguration.empty(1);

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
    final var configuration =
        new PartitionGroupConfiguration(
            0, 0, Map.of(), Optional.empty(), Optional.empty(), Optional.of(lastChange));

    // when / then
    assertThat(RestoreStatus.of(configuration)).isEmpty();
  }

  @Test
  void shouldReturnEmptyForUnrelatedPendingChange() {
    // given
    // an ordinary partition join, not part of a restore, must not be misdetected.
    final var configuration =
        PartitionGroupConfiguration.empty(0)
            .startGraphConfigurationChange(
                OperationGraph.sequential(List.of(new PartitionJoinOperation(BROKER_1, 1, 1, true))));

    // when / then
    assertThat(RestoreStatus.of(configuration)).isEmpty();
  }

  @Test
  void shouldReturnEmptyForPendingModeTransitionWithoutRestoreOps() {
    // given
    // a plain mode transition to PROCESSING that never involved a restore operation.
    final var configuration =
        PartitionGroupConfiguration.empty(0)
            .startGraphConfigurationChange(
                OperationGraph.sequential(
                    List.of(new ModeChangeOperation(BROKER_1, Mode.PROCESSING))));

    // when / then
    assertThat(RestoreStatus.of(configuration)).isEmpty();
  }

  @Test
  void shouldMapInProgressRestoreWithPerPartitionDetail() {
    // given
    // broker 1: partition 1 fully restored, partition 2 pre-restored but restore still pending
    final var configuration =
        new PartitionGroupConfiguration(
            0,
            PartitionGroupConfiguration.INITIAL_INCARNATION_NUMBER,
            Map.of(),
            Optional.empty(),
            Optional.of(
                new DependencyChangePlan(
                    RESTORE_CHANGE_ID,
                    Status.IN_PROGRESS,
                    STARTED_AT,
                    OperationGraph.sequential(
                        List.of(
                            new PartitionPreRestoreOperation(BROKER_1, 1),
                            new PartitionRestoreOperation(BROKER_1, 1, backups(10L, 11L)),
                            new PartitionPreRestoreOperation(BROKER_1, 2),
                            new PartitionRestoreOperation(BROKER_1, 2, backups(20L)),
                            new ModeChangeOperation(BROKER_1, Mode.PROCESSING))),
                    new TreeMap<>(
                        Map.of(
                            new OperationId(0),
                            STARTED_AT.plusSeconds(1),
                            new OperationId(1),
                            STARTED_AT.plusSeconds(2),
                            new OperationId(2),
                            STARTED_AT.plusSeconds(3))))),
            Optional.empty());

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
    final var configuration =
        PartitionGroupConfiguration.empty(0)
            .startGraphConfigurationChange(OperationGraph.sequential(List.of(pre1, restore1)));

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
    final var configuration =
        PartitionGroupConfiguration.empty(0)
            .startGraphConfigurationChange(
                OperationGraph.sequential(
                    List.of(
                        new PartitionPreRestoreOperation(BROKER_1, 1),
                        new PartitionRestoreOperation(BROKER_1, 1, backups(10L)),
                        new ModeChangeOperation(BROKER_1, Mode.PROCESSING),
                        new AwaitModeChangeOperation(BROKER_1, Mode.PROCESSING))))
            .completeOperation(new OperationId(0), UnaryOperator.identity())
            .completeOperation(new OperationId(1), UnaryOperator.identity());

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
    final var configuration =
        new PartitionGroupConfiguration(
                0, 0, Map.of(), Optional.empty(), Optional.empty(), Optional.empty())
            .startGraphConfigurationChange(
                OperationGraph.sequential(
                    List.of(
                        new PartitionPreRestoreOperation(BROKER_1, 1),
                        new PartitionRestoreOperation(BROKER_1, 1, backups(10L)),
                        new ModeChangeOperation(BROKER_1, Mode.PROCESSING),
                        new AwaitModeChangeOperation(BROKER_1, Mode.PROCESSING),
                        new UpdateIncarnationNumberOperation(BROKER_1))))
            .completeOperation(new OperationId(0), UnaryOperator.identity())
            .completeOperation(new OperationId(1), UnaryOperator.identity())
            .completeOperation(new OperationId(2), UnaryOperator.identity())
            .completeOperation(new OperationId(3), UnaryOperator.identity());

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
}
