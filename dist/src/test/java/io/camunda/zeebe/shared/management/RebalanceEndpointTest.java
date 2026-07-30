/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.atomix.cluster.messaging.MessagingException.NoSuchMemberException;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.rebalance.CancelRebalanceResponse;
import io.camunda.zeebe.dynamic.config.rebalance.PartitionRebalance;
import io.camunda.zeebe.dynamic.config.rebalance.PartitionRebalanceState;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceErrorResponse;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceErrorResponse.RebalanceErrorCode;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceOutcome;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceOverrides;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceRequestSender;
import io.camunda.zeebe.dynamic.config.rebalance.RebalanceStatus;
import io.camunda.zeebe.dynamic.config.rebalance.TriggerRebalanceRequest;
import io.camunda.zeebe.management.cluster.BrokerId;
import io.camunda.zeebe.management.cluster.CompletedRebalance;
import io.camunda.zeebe.management.cluster.Error;
import io.camunda.zeebe.management.cluster.PartitionRebalanceStatus;
import io.camunda.zeebe.management.cluster.RebalanceCancellationResponse;
import io.camunda.zeebe.management.cluster.RebalanceRequest;
import io.camunda.zeebe.management.cluster.RebalanceStatusResponse;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.ResponseEntity;

final class RebalanceEndpointTest {

  private static final String GROUP = "<default>";

  private final RebalanceRequestSender sender = mock(RebalanceRequestSender.class);
  private final ClusterEndpoint endpoint =
      new ClusterEndpoint(mock(ClusterConfigurationManagementRequestSender.class), sender);

  @Test
  void shouldAcceptARebalanceAndReportTheStatusItStartsFrom() {
    // given
    triggerAnswers(Either.right(running(7, RebalanceOverrides.none(), false)));

    // when
    final var response = endpoint.rebalance(null).join();

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(202);
    assertThat(status(response).getStatus()).isEqualTo(RebalanceStatusResponse.StatusEnum.RUNNING);
    assertThat(status(response).getRebalanceId()).isEqualTo(7);
  }

  @Test
  void shouldLeaveEverySettingConfiguredWhenTheRequestHasNoBody() {
    // given
    triggerAnswers(Either.right(RebalanceStatus.idle()));

    // when
    endpoint.rebalance(null).join();

    // then
    assertThat(triggered()).isEqualTo(TriggerRebalanceRequest.withConfiguredSettings());
  }

  @Test
  void shouldForwardEverySettingTheRequestOverrides() {
    // given
    triggerAnswers(Either.right(RebalanceStatus.idle()));
    final var body =
        new RebalanceRequest()
            .replicationLagThreshold(4096L)
            .replicationTimeout("PT30S")
            .maxTransferAttempts(5)
            .leaderWaitTimeout("PT2M")
            .dryRun(true);

    // when
    endpoint.rebalance(body).join();

    // then
    assertThat(triggered())
        .isEqualTo(
            new TriggerRebalanceRequest(
                new RebalanceOverrides(4096L, Duration.ofSeconds(30), 5, Duration.ofMinutes(2)),
                true));
  }

  @Test
  void shouldRejectARequestWhoseDurationIsNotIso8601() {
    // when
    final var response =
        endpoint.rebalance(new RebalanceRequest().replicationTimeout("30s")).join();

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(((Error) response.getBody()).getMessage()).contains("replicationTimeout");
  }

  @Test
  void shouldRejectARequestWhoseSettingIsOutOfRange() {
    // when
    final var response = endpoint.rebalance(new RebalanceRequest().maxTransferAttempts(0)).join();

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(400);
    assertThat(((Error) response.getBody()).getMessage()).contains("maxTransferAttempts");
  }

  @Test
  void shouldRefuseASecondRebalanceWithAConflict() {
    // given
    triggerAnswers(
        Either.left(
            new RebalanceErrorResponse(
                RebalanceErrorCode.REBALANCE_IN_PROGRESS, "rebalance 6 is already running")));

    // when
    final var response = endpoint.rebalance(null).join();

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(409);
    assertThat(((Error) response.getBody()).getMessage())
        .isEqualTo("rebalance 6 is already running");
  }

  @Test
  void shouldReportAMemberThatIsNoLongerTheCoordinatorAsUnavailable() {
    // given
    triggerAnswers(
        Either.left(new RebalanceErrorResponse(RebalanceErrorCode.NOT_COORDINATOR, "not me")));

    // when
    final var response = endpoint.rebalance(null).join();

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(503);
  }

  @Test
  void shouldReportACoordinatorThatCannotBeReachedAsUnavailable() {
    // given
    when(sender.triggerRebalance(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CompletionException(new NoSuchMemberException("no member 0"))));

    // when
    final var response = endpoint.rebalance(null).join();

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(503);
  }

  @Test
  void shouldReportACoordinatorThatDidNotAcknowledgeInTimeAsATimeout() {
    // given
    when(sender.triggerRebalance(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new CompletionException(new TimeoutException("no acknowledgement"))));

    // when
    final var response = endpoint.rebalance(null).join();

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(504);
  }

  @Test
  void shouldReportAnUnexpectedForwardingFailureAsABadGateway() {
    // given
    when(sender.triggerRebalance(any()))
        .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("garbled")));

    // when
    final var response = endpoint.rebalance(null).join();

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(502);
  }

  @Test
  void shouldReportAFailureTheCoordinatorNamesForItselfAsAnInternalError() {
    // given
    triggerAnswers(
        Either.left(new RebalanceErrorResponse(RebalanceErrorCode.INTERNAL_ERROR, "broke")));

    // when
    final var response = endpoint.rebalance(null).join();

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(500);
  }

  @Test
  void shouldReportAnIdleCoordinatorWithNoHistory() {
    // given
    when(sender.getRebalanceStatus())
        .thenReturn(CompletableFuture.completedFuture(Either.right(RebalanceStatus.idle())));

    // when
    final var response = endpoint.rebalanceStatus().join();

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(status(response).getStatus()).isEqualTo(RebalanceStatusResponse.StatusEnum.IDLE);
    assertThat(status(response).getPartitions()).isEmpty();
    assertThat(status(response).getLastCompletedRebalance()).isNull();
  }

  @Test
  void shouldReportWhereEachPartitionOfTheRunningRebalanceStands() {
    // given
    final var running =
        new RebalanceStatus.Running(
            7,
            RebalanceOverrides.none(),
            false,
            false,
            List.of(
                new PartitionRebalance(
                    GROUP,
                    1,
                    MemberId.from("1"),
                    MemberId.from("2"),
                    PartitionRebalanceState.TRANSFERRING),
                new PartitionRebalance(GROUP, 2, null, null, PartitionRebalanceState.SKIPPED)));
    when(sender.getRebalanceStatus())
        .thenReturn(
            CompletableFuture.completedFuture(Either.right(new RebalanceStatus(running, null))));

    // when
    final var partitions = status(endpoint.rebalanceStatus().join()).getPartitions();

    // then
    assertThat(partitions).hasSize(2);
    assertThat(partitions.getFirst().getId()).isEqualTo(1);
    assertThat(partitions.getFirst().getPhysicalTenantId()).isEqualTo(GROUP);
    assertThat(partitions.getFirst().getCurrentLeader()).isEqualTo(new BrokerId.Integer(1));
    assertThat(partitions.getFirst().getDesiredLeader()).isEqualTo(new BrokerId.Integer(2));
    assertThat(partitions.getFirst().getStatus())
        .isEqualTo(PartitionRebalanceStatus.StatusEnum.TRANSFERRING);
    assertThat(partitions.get(1).getCurrentLeader()).isNull();
    assertThat(partitions.get(1).getDesiredLeader()).isNull();
  }

  @Test
  void shouldReportWhyAPartitionCouldNotBeMoved() {
    // given
    final var completed =
        new RebalanceStatus.Completed(
            8,
            RebalanceOutcome.COMPLETED,
            false,
            List.of(
                new PartitionRebalance(
                    GROUP,
                    1,
                    MemberId.from("1"),
                    MemberId.from("2"),
                    PartitionRebalanceState.FAILED,
                    "its leader declined the transfer: NOT_REPLICATING")));
    when(sender.getRebalanceStatus())
        .thenReturn(
            CompletableFuture.completedFuture(Either.right(new RebalanceStatus(null, completed))));

    // when
    final var partitions =
        status(endpoint.rebalanceStatus().join()).getLastCompletedRebalance().getPartitions();

    // then
    assertThat(partitions.getFirst().getStatus())
        .isEqualTo(PartitionRebalanceStatus.StatusEnum.FAILED);
    assertThat(partitions.getFirst().getReason())
        .isEqualTo("its leader declined the transfer: NOT_REPLICATING");
  }

  @Test
  void shouldReportAPartitionTheOperatorCancelledBeforeItsTransferBegan() {
    // given
    final var completed =
        new RebalanceStatus.Completed(
            8,
            RebalanceOutcome.CANCELLED,
            false,
            List.of(
                new PartitionRebalance(
                    GROUP,
                    1,
                    MemberId.from("1"),
                    MemberId.from("2"),
                    PartitionRebalanceState.CANCELLED,
                    "the operator cancelled the rebalance before its transfer began")));
    when(sender.getRebalanceStatus())
        .thenReturn(
            CompletableFuture.completedFuture(Either.right(new RebalanceStatus(null, completed))));

    // when
    final var partitions =
        status(endpoint.rebalanceStatus().join()).getLastCompletedRebalance().getPartitions();

    // then
    assertThat(partitions.getFirst().getStatus())
        .isEqualTo(PartitionRebalanceStatus.StatusEnum.CANCELLED);
    assertThat(partitions.getFirst().getReason())
        .isEqualTo("the operator cancelled the rebalance before its transfer began");
  }

  @Test
  void shouldReportARebalanceAskedToStopAsCancelling() {
    // given
    final var running =
        new RebalanceStatus.Running(7, RebalanceOverrides.none(), false, true, List.of());
    when(sender.getRebalanceStatus())
        .thenReturn(
            CompletableFuture.completedFuture(Either.right(new RebalanceStatus(running, null))));

    // when - then
    assertThat(status(endpoint.rebalanceStatus().join()).getStatus())
        .isEqualTo(RebalanceStatusResponse.StatusEnum.CANCELLING);
  }

  @Test
  void shouldReportOnlyTheSettingsTheRebalanceOverrode() {
    // given
    final var overrides = new RebalanceOverrides(null, Duration.ofSeconds(15), null, null);
    when(sender.getRebalanceStatus())
        .thenReturn(CompletableFuture.completedFuture(Either.right(running(7, overrides, false))));

    // when
    final var settings = status(endpoint.rebalanceStatus().join()).getSettings();

    // then
    assertThat(settings.getReplicationTimeout()).isEqualTo("PT15S");
    assertThat(settings.getReplicationLagThreshold()).isNull();
    assertThat(settings.getMaxTransferAttempts()).isNull();
    assertThat(settings.getLeaderWaitTimeout()).isNull();
  }

  @Test
  void shouldReportTheLastRebalanceToFinish() {
    // given
    final var completed =
        new RebalanceStatus.Completed(
            6,
            RebalanceOutcome.CANCELLED,
            false,
            List.of(
                new PartitionRebalance(
                    GROUP,
                    1,
                    MemberId.from("2"),
                    MemberId.from("2"),
                    PartitionRebalanceState.TRANSFERRED)));
    when(sender.getRebalanceStatus())
        .thenReturn(
            CompletableFuture.completedFuture(Either.right(new RebalanceStatus(null, completed))));

    // when
    final var last = status(endpoint.rebalanceStatus().join()).getLastCompletedRebalance();

    // then
    assertThat(last.getRebalanceId()).isEqualTo(6);
    assertThat(last.getOutcome()).isEqualTo(CompletedRebalance.OutcomeEnum.CANCELLED);
    assertThat(last.getPartitions()).hasSize(1);
  }

  @Test
  void shouldReportThatACancellationStoppedARunningRebalance() {
    // given
    when(sender.cancelRebalance())
        .thenReturn(
            CompletableFuture.completedFuture(Either.right(new CancelRebalanceResponse(true))));

    // when
    final var response = endpoint.cancelRebalance().join();

    // then
    assertThat(response.getStatusCode().value()).isEqualTo(200);
    assertThat(((RebalanceCancellationResponse) response.getBody()).getWasRunning()).isTrue();
  }

  @Test
  void shouldReportThatACancellationFoundNothingToStop() {
    // given
    when(sender.cancelRebalance())
        .thenReturn(
            CompletableFuture.completedFuture(Either.right(new CancelRebalanceResponse(false))));

    // when - then
    assertThat(
            ((RebalanceCancellationResponse) endpoint.cancelRebalance().join().getBody())
                .getWasRunning())
        .isFalse();
  }

  private static RebalanceStatus running(
      final long id, final RebalanceOverrides overrides, final boolean dryRun) {
    return new RebalanceStatus(
        new RebalanceStatus.Running(id, overrides, dryRun, false, List.of()), null);
  }

  private void triggerAnswers(final Either<RebalanceErrorResponse, RebalanceStatus> answer) {
    when(sender.triggerRebalance(any())).thenReturn(CompletableFuture.completedFuture(answer));
  }

  private TriggerRebalanceRequest triggered() {
    final var captor = ArgumentCaptor.forClass(TriggerRebalanceRequest.class);
    verify(sender).triggerRebalance(captor.capture());
    return captor.getValue();
  }

  private static RebalanceStatusResponse status(final ResponseEntity<?> response) {
    return (RebalanceStatusResponse) response.getBody();
  }
}
