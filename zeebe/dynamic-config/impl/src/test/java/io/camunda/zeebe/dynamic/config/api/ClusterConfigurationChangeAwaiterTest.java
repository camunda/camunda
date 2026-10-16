/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse.ErrorCode;
import io.camunda.zeebe.dynamic.config.state.CompletedPhasedChange;
import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.GlobalConfiguration;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ExportingStateChangeOperation;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.GlobalPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlan.PartitionGroupPhase;
import io.camunda.zeebe.dynamic.config.state.PhasedChangePlanStatus;
import io.camunda.zeebe.dynamic.config.state.PhasedChangeState;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

final class ClusterConfigurationChangeAwaiterTest {

  private final ClusterConfigurationManagementRequestSender sender =
      mock(ClusterConfigurationManagementRequestSender.class);
  private final ClusterConfigurationChangeAwaiter awaiter =
      new ClusterConfigurationChangeAwaiter(sender, Duration.ofMillis(1), Duration.ofSeconds(10));

  @Test
  void shouldCompleteImmediatelyWhenPlanIsEmpty() {
    // when
    final var result = awaiter.awaitCompletion(emptyPlan());

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  void shouldCompleteWhenChangeCompletes() {
    // given
    when(sender.getTopology())
        .thenReturn(
            topology(nextId(), pending(), history(completed(7, PhasedChangePlanStatus.COMPLETED))));

    // when
    final var result = awaiter.awaitCompletion(plan(7));

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  void shouldPollUntilChangeCompletes() {
    // given
    when(sender.getTopology())
        .thenReturn(topology(nextId(), pending(pendingPlan(7)), history()))
        .thenReturn(topology(nextId(), pending(pendingPlan(7)), history()))
        .thenReturn(
            topology(nextId(), pending(), history(completed(7, PhasedChangePlanStatus.COMPLETED))));

    // when
    final var result = awaiter.awaitCompletion(plan(7));

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(1));
    verify(sender, atLeast(3)).getTopology();
  }

  @Test
  void shouldWaitWhenThisReplicaHasNotGossipedTheChangeYet() {
    // given — nextId has not caught up to changeId 7 on this replica yet
    when(sender.getTopology())
        .thenReturn(topology(1L, pending(), history()))
        .thenReturn(
            topology(nextId(), pending(), history(completed(7, PhasedChangePlanStatus.COMPLETED))));

    // when
    final var result = awaiter.awaitCompletion(plan(7));

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(1));
    verify(sender, atLeast(2)).getTopology();
  }

  @Test
  void shouldFailWithAnUnknownOutcomeWhenTheChangeHasAgedOutOfTheHistoryWindow() {
    // given — changeId 7 was issued (nextId has advanced past it) but neither pending nor
    // history mention it any more: it aged out of the bounded history window before we could
    // observe how it resolved
    when(sender.getTopology()).thenReturn(topology(nextId(), pending(), history()));

    // when
    final var result = awaiter.awaitCompletion(plan(7));

    // then — distinct from both a genuine failure and a genuine completion: we cannot tell which
    // one this was, so it must not be asserted as either
    assertThat(result)
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("unknown outcome");
  }

  @Test
  void shouldFailWhenChangeFailed() {
    // given
    when(sender.getTopology())
        .thenReturn(
            topology(nextId(), pending(), history(completed(7, PhasedChangePlanStatus.FAILED))));

    // when
    final var result = awaiter.awaitCompletion(plan(7));

    // then
    assertThat(result)
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableOfType(ExecutionException.class);
  }

  @Test
  void shouldFailWhenChangeWasCancelled() {
    // given
    when(sender.getTopology())
        .thenReturn(
            topology(nextId(), pending(), history(completed(7, PhasedChangePlanStatus.CANCELLED))));

    // when
    final var result = awaiter.awaitCompletion(plan(7));

    // then — distinct from a genuine failure, so an operator-triggered cancel isn't misreported
    // as the plan failing to apply
    assertThat(result)
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .withMessageContaining("cancelled");
  }

  @Test
  void shouldFailWhenSubmitFails() {
    // given
    final var submission =
        CompletableFuture.completedFuture(
            Either.<ErrorResponse, ClusterConfigurationChangeResponse>left(
                new ErrorResponse(ErrorCode.INVALID_REQUEST, "nope")));

    // when
    final var result = awaiter.awaitCompletion(submission);

    // then
    assertThat(result)
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableOfType(ExecutionException.class);
  }

  @Test
  void shouldFallbackToLegacyResponseWhenCurrentConfigurationResponseIsMissing() {
    // given
    when(sender.getTopology())
        .thenReturn(
            topology(nextId(), pending(), history(completed(7, PhasedChangePlanStatus.COMPLETED))));
    final var submission =
        CompletableFuture.completedFuture(
            Either.<ErrorResponse, ClusterConfigurationChangeResponse>right(
                new ClusterConfigurationChangeResponse(
                    7,
                    new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
                        Map.of(),
                        Map.of(),
                        List.of(
                            new ExportingStateChangeOperation(
                                MemberId.from("0"), ExportingState.PAUSED))),
                    null)));

    // when
    final var result = awaiter.awaitCompletion(submission);

    // then
    assertThat(result).succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  void shouldFailWhenChangeNeverCompletes() {
    // given
    final var timingOutAwaiter =
        new ClusterConfigurationChangeAwaiter(sender, Duration.ofMillis(1), Duration.ofMillis(5));
    when(sender.getTopology()).thenReturn(topology(nextId(), pending(pendingPlan(7)), history()));

    // when
    final var result = timingOutAwaiter.awaitCompletion(plan(7));

    // then
    assertThat(result)
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableOfType(ExecutionException.class)
        .havingCause()
        .isInstanceOf(TimeoutException.class);
  }

  private CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> emptyPlan() {
    return CompletableFuture.completedFuture(
        Either.right(
            new ClusterConfigurationChangeResponse(
                0,
                new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
                    Map.of(), Map.of(), List.of()),
                new ClusterConfigurationChangeResponse.CurrentConfigurationChangeResponse(
                    CurrentClusterConfiguration.init(),
                    CurrentClusterConfiguration.init(),
                    List.of()))));
  }

  private CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>> plan(
      final long changeId) {
    return CompletableFuture.completedFuture(
        Either.right(
            new ClusterConfigurationChangeResponse(
                changeId,
                new ClusterConfigurationChangeResponse.LegacyConfigurationChangeResponse(
                    Map.of(),
                    Map.of(),
                    List.of(
                        new ExportingStateChangeOperation(
                            MemberId.from("0"), ExportingState.PAUSED))),
                new ClusterConfigurationChangeResponse.CurrentConfigurationChangeResponse(
                    CurrentClusterConfiguration.init(),
                    CurrentClusterConfiguration.init(),
                    List.of(
                        PartitionGroupPhase.sequential(
                            "default",
                            List.of(
                                new ExportingStateChangeOperation(
                                    MemberId.from("0"), ExportingState.PAUSED))))))));
  }

  private CompletableFuture<Either<ErrorResponse, CurrentClusterConfiguration>> topology(
      final long nextId,
      final Map<Long, PhasedChangePlan> pending,
      final List<CompletedPhasedChange> history) {
    return CompletableFuture.completedFuture(
        Either.right(
            new CurrentClusterConfiguration(
                CurrentClusterConfiguration.INITIAL_VERSION,
                GlobalConfiguration.init(),
                Map.of(),
                new PhasedChangeState(nextId, pending, history))));
  }

  private long nextId() {
    return 8L;
  }

  private Map<Long, PhasedChangePlan> pending(final PhasedChangePlan... plans) {
    return Arrays.stream(plans).collect(Collectors.toMap(PhasedChangePlan::id, p -> p));
  }

  private List<CompletedPhasedChange> history(final CompletedPhasedChange... changes) {
    return List.of(changes);
  }

  private PhasedChangePlan pendingPlan(final long changeId) {
    return PhasedChangePlan.init(changeId, List.of(new GlobalPhase(List.of())), Instant.now());
  }

  private CompletedPhasedChange completed(
      final long changeId, final PhasedChangePlanStatus status) {
    return new CompletedPhasedChange(changeId, status, Instant.now(), Instant.now());
  }
}
