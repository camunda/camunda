/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.camunda.service.ClusterRebalanceServices.ClusterRebalanceRequest;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.zeebe.rebalance.CancelRebalanceResponse;
import io.camunda.zeebe.rebalance.ClusterLeadershipStatus;
import io.camunda.zeebe.rebalance.PartitionLeadershipStatus;
import io.camunda.zeebe.rebalance.PartitionRebalance;
import io.camunda.zeebe.rebalance.PartitionRebalanceOutcome;
import io.camunda.zeebe.rebalance.PartitionRebalanceProgress;
import io.camunda.zeebe.rebalance.RebalanceErrorResponse;
import io.camunda.zeebe.rebalance.RebalanceErrorResponse.RebalanceErrorCode;
import io.camunda.zeebe.rebalance.RebalanceOutcome;
import io.camunda.zeebe.rebalance.RebalanceOverrides;
import io.camunda.zeebe.rebalance.RebalanceRequestSender;
import io.camunda.zeebe.rebalance.RebalanceStatus;
import io.camunda.zeebe.rebalance.TriggerRebalanceRequest;
import io.camunda.zeebe.util.Either;
import java.net.ConnectException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

final class ClusterRebalanceServicesTest {

  private final RebalanceRequestSender sender = mock(RebalanceRequestSender.class);
  private final ClusterRebalanceServices services = new ClusterRebalanceServices(sender);

  @Test
  void shouldTriggerWithConfiguredSettingsWhenNoOverrideIsGiven() {
    // given
    when(sender.triggerRebalance(new TriggerRebalanceRequest(RebalanceOverrides.none(), false)))
        .thenReturn(CompletableFuture.completedFuture(Either.right(RebalanceStatus.idle())));

    // when
    final var result =
        services.triggerRebalance(ClusterRebalanceRequest.withDefaultSettings(false)).join();

    // then
    assertThat(result.leadershipStatus().state()).isEqualTo(ClusterLeadershipStatus.State.BALANCED);
    assertThat(result.leadershipStatus().partitions()).isEmpty();
    assertThat(result.lastCompleted()).isNull();
  }

  @Test
  void shouldRejectANegativeReplicationLagThreshold() {
    // when / then
    assertThatThrownBy(
            () ->
                services
                    .triggerRebalance(new ClusterRebalanceRequest(false, -1L, null, null, null))
                    .join())
        .cause()
        .isInstanceOf(ServiceException.class)
        .extracting(t -> ((ServiceException) t).getStatus())
        .isEqualTo(Status.INVALID_ARGUMENT);
  }

  @Test
  void shouldRejectANonPositiveMaxTransferAttempts() {
    // when / then
    assertThatThrownBy(
            () ->
                services
                    .triggerRebalance(new ClusterRebalanceRequest(false, null, null, 0, null))
                    .join())
        .cause()
        .isInstanceOf(ServiceException.class)
        .extracting(t -> ((ServiceException) t).getStatus())
        .isEqualTo(Status.INVALID_ARGUMENT);
  }

  @Test
  void shouldKeepPartitionsOfDifferentTenantsWithTheSameNumericIdDistinct() {
    // given
    final var memberA = MemberId.from("0");
    final var memberB = MemberId.from("1");
    final var leadershipStatus =
        ClusterLeadershipStatus.aggregateOf(
            List.of(
                new PartitionLeadershipStatus(
                    "tenant-a", 1, memberA, memberB, PartitionLeadershipStatus.State.UNBALANCED),
                new PartitionLeadershipStatus(
                    "tenant-b", 1, memberB, memberB, PartitionLeadershipStatus.State.BALANCED)));
    final var completed =
        new RebalanceStatus.Completed(
            1L,
            RebalanceOutcome.COMPLETED,
            List.of(
                PartitionRebalance.pending("tenant-a", 1, memberA, memberB)
                    .completed(PartitionRebalanceOutcome.TRANSFERRED),
                PartitionRebalance.alreadyLeader("tenant-b", 1, memberB)),
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(1));
    givenRebalanceStatus(new RebalanceStatus(null, completed, leadershipStatus));

    // when
    final var result = services.getRebalanceStatus().join();

    // then
    assertThat(result.leadershipStatus().partitions())
        .extracting(p -> p.physicalTenantId() + "/" + p.partitionId(), p -> p.state())
        .containsExactlyInAnyOrder(
            tuple("tenant-a/1", PartitionLeadershipStatus.State.UNBALANCED),
            tuple("tenant-b/1", PartitionLeadershipStatus.State.BALANCED));
    assertThat(result.lastCompleted().partitions())
        .extracting(p -> p.physicalTenantId() + "/" + p.partitionId(), p -> p.outcome())
        .containsExactlyInAnyOrder(
            tuple("tenant-a/1", PartitionRebalanceOutcome.TRANSFERRED),
            tuple("tenant-b/1", PartitionRebalanceOutcome.ALREADY_LEADER));
  }

  @Test
  void shouldMapTheRunningRebalancePlanAndProgress() {
    // given
    final var memberA = MemberId.from("0");
    final var memberB = MemberId.from("1");
    final var running =
        new RebalanceStatus.Running(
            1L,
            RebalanceOverrides.none(),
            false,
            true,
            List.of(
                PartitionRebalance.pending("default", 1, memberA, memberB),
                PartitionRebalance.alreadyLeader("default", 2, memberB)),
            Instant.EPOCH);
    givenRebalanceStatus(
        new RebalanceStatus(running, null, ClusterLeadershipStatus.aggregateOf(List.of())));

    // when
    final var result = services.getRebalanceStatus().join().running();

    // then
    assertThat(result.rebalanceId()).isEqualTo(1L);
    assertThat(result.dryRun()).isFalse();
    assertThat(result.cancelRequested()).isTrue();
    assertThat(result.partitions())
        .extracting(
            p -> p.partitionId(),
            p -> p.currentLeader(),
            p -> p.desiredLeader(),
            p -> p.progress(),
            p -> p.outcome())
        .containsExactly(
            tuple(1, memberA, memberB, PartitionRebalanceProgress.PENDING, null),
            tuple(
                2,
                memberB,
                memberB,
                PartitionRebalanceProgress.COMPLETED,
                PartitionRebalanceOutcome.ALREADY_LEADER));
  }

  @Test
  void shouldReportAPlannedPartitionWithNoOutcome() {
    // given
    final var memberA = MemberId.from("0");
    final var memberB = MemberId.from("1");
    final var completed =
        new RebalanceStatus.Completed(
            1L,
            RebalanceOutcome.COMPLETED,
            List.of(PartitionRebalance.pending("tenant-a", 1, memberA, memberB)),
            Instant.EPOCH,
            Instant.EPOCH.plusSeconds(1));
    givenRebalanceStatus(
        new RebalanceStatus(null, completed, ClusterLeadershipStatus.aggregateOf(List.of())));

    // when
    final var result = services.getRebalanceStatus().join();

    // then
    assertThat(result.lastCompleted().rebalanceId()).isEqualTo(1L);
    assertThat(result.lastCompleted().partitions().getFirst())
        .extracting(
            p -> p.currentLeader(), p -> p.desiredLeader(), p -> p.progress(), p -> p.outcome())
        .containsExactly(memberA, memberB, PartitionRebalanceProgress.PENDING, null);
  }

  @Test
  void shouldKeepCurrentLeaderAbsentWhenThePartitionHasNoLeader() {
    // given
    final var desired = MemberId.from("1");
    final var leadershipStatus =
        ClusterLeadershipStatus.aggregateOf(
            List.of(
                new PartitionLeadershipStatus(
                    "default", 1, null, desired, PartitionLeadershipStatus.State.UNBALANCED)));
    givenRebalanceStatus(new RebalanceStatus(null, null, leadershipStatus));

    // when
    final var result = services.getRebalanceStatus().join();

    // then
    assertThat(result.leadershipStatus().partitions().getFirst().currentLeader()).isNull();
    assertThat(result.leadershipStatus().partitions().getFirst().desiredLeader())
        .isEqualTo(desired);
  }

  @Test
  void shouldMapEveryCompletedRebalanceOutcome() {
    // given
    for (final var outcome : RebalanceOutcome.values()) {
      final var completed =
          new RebalanceStatus.Completed(
              1L, outcome, List.of(), Instant.EPOCH, Instant.EPOCH.plusSeconds(1));
      givenRebalanceStatus(
          new RebalanceStatus(null, completed, ClusterLeadershipStatus.aggregateOf(List.of())));

      // when
      final var result = services.getRebalanceStatus().join();

      // then
      assertThat(result.lastCompleted().outcome()).isEqualTo(outcome);
    }
  }

  @Test
  void shouldReportWhetherCancellationStoppedARunningRebalance() {
    // given
    when(sender.cancelRebalance())
        .thenReturn(
            CompletableFuture.completedFuture(Either.right(new CancelRebalanceResponse(true))));

    // when
    final var result = services.cancelRebalance().join();

    // then
    assertThat(result.wasRunning()).isTrue();
  }

  @Test
  void shouldMapRebalanceInProgressToAlreadyExists() {
    assertConflict(RebalanceErrorCode.REBALANCE_IN_PROGRESS);
  }

  @Test
  void shouldMapConfigurationChangeInProgressToAlreadyExists() {
    assertConflict(RebalanceErrorCode.CONFIGURATION_CHANGE_IN_PROGRESS);
  }

  private void assertConflict(final RebalanceErrorCode code) {
    // given
    when(sender.getRebalanceStatus())
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(new RebalanceErrorResponse(code, "conflict"))));

    // when / then
    assertThatThrownBy(() -> services.getRebalanceStatus().join())
        .cause()
        .isInstanceOf(ServiceException.class)
        .extracting(t -> ((ServiceException) t).getStatus())
        .isEqualTo(Status.ALREADY_EXISTS);
  }

  @Test
  void shouldMapNotCoordinatorToUnavailable() {
    // given
    when(sender.getRebalanceStatus())
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(
                    new RebalanceErrorResponse(
                        RebalanceErrorCode.NOT_COORDINATOR, "not the coordinator"))));

    // when / then
    assertThatThrownBy(() -> services.getRebalanceStatus().join())
        .cause()
        .isInstanceOf(ServiceException.class)
        .extracting(t -> ((ServiceException) t).getStatus())
        .isEqualTo(Status.UNAVAILABLE);
  }

  @Test
  void shouldMapCoordinatorInternalErrorToInternal() {
    // given
    when(sender.getRebalanceStatus())
        .thenReturn(
            CompletableFuture.completedFuture(
                Either.left(
                    new RebalanceErrorResponse(RebalanceErrorCode.INTERNAL_ERROR, "boom"))));

    // when / then
    assertThatThrownBy(() -> services.getRebalanceStatus().join())
        .cause()
        .isInstanceOf(ServiceException.class)
        .extracting(t -> ((ServiceException) t).getStatus())
        .isEqualTo(Status.INTERNAL);
  }

  @Test
  void shouldMapAConnectExceptionToUnavailable() {
    givenForwardingFailure(new ConnectException("connection refused"), Status.UNAVAILABLE);
  }

  @Test
  void shouldMapANoSuchMemberExceptionToUnavailable() {
    givenForwardingFailure(new NoSuchMemberException("gone"), Status.UNAVAILABLE);
  }

  @Test
  void shouldMapATimeoutExceptionToDeadlineExceeded() {
    givenForwardingFailure(new TimeoutException("too slow"), Status.DEADLINE_EXCEEDED);
  }

  @Test
  void shouldMapAnUnknownForwardingFailureToAborted() {
    givenForwardingFailure(new RuntimeException("mystery"), Status.ABORTED);
  }

  private void givenForwardingFailure(final Throwable cause, final Status expected) {
    // given
    when(sender.getRebalanceStatus()).thenReturn(CompletableFuture.failedFuture(cause));

    // when / then
    assertThatThrownBy(() -> services.getRebalanceStatus().join())
        .cause()
        .isInstanceOf(ServiceException.class)
        .extracting(t -> ((ServiceException) t).getStatus())
        .isEqualTo(expected);
  }

  @Test
  void shouldMapAMissingCoordinatorReplyToAborted() {
    // given
    when(sender.cancelRebalance()).thenReturn(CompletableFuture.completedFuture(null));

    // when / then
    assertThatThrownBy(() -> services.cancelRebalance().join())
        .cause()
        .isInstanceOf(ServiceException.class)
        .extracting(t -> ((ServiceException) t).getStatus())
        .isEqualTo(Status.ABORTED);
  }

  private void givenRebalanceStatus(final RebalanceStatus status) {
    when(sender.getRebalanceStatus())
        .thenReturn(CompletableFuture.completedFuture(Either.right(status)));
  }
}
