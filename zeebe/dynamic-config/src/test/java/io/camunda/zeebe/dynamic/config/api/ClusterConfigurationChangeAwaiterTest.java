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
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.CompletedChange;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.dynamic.config.state.PartitionGroupOperation.ExportingStateChangeOperation;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;

final class ClusterConfigurationChangeAwaiterTest {

  private final ClusterConfigurationManagementRequestSender sender =
      mock(ClusterConfigurationManagementRequestSender.class);
  private final ClusterConfigurationChangeAwaiter awaiter =
      new ClusterConfigurationChangeAwaiter(sender, Duration.ofMillis(1), Duration.ofSeconds(10));

  @Test
  void shouldCompleteImmediatelyWhenPlanIsEmpty() {
    // when - then
    assertThat(awaiter.awaitCompletion(emptyPlan())).succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  void shouldCompleteWhenChangeCompletes() {
    // given
    when(sender.getTopology())
        .thenReturn(topology(Optional.of(completed(7, Status.COMPLETED)), Optional.empty()));

    // when - then
    assertThat(awaiter.awaitCompletion(plan(7))).succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  void shouldPollUntilChangeCompletes() {
    // given
    when(sender.getTopology())
        .thenReturn(topology(Optional.empty(), Optional.of(pending(7))))
        .thenReturn(topology(Optional.empty(), Optional.of(pending(7))))
        .thenReturn(topology(Optional.of(completed(7, Status.COMPLETED)), Optional.empty()));

    // when - then
    assertThat(awaiter.awaitCompletion(plan(7))).succeedsWithin(Duration.ofSeconds(1));
    verify(sender, atLeast(3)).getTopology();
  }

  @Test
  void shouldCompleteWhenSupersededByNewerChange() {
    // given a newer change already completed -> our change finished before it
    when(sender.getTopology())
        .thenReturn(topology(Optional.of(completed(8, Status.COMPLETED)), Optional.empty()));

    // when - then
    assertThat(awaiter.awaitCompletion(plan(7))).succeedsWithin(Duration.ofSeconds(1));
  }

  @Test
  void shouldFailWhenChangeFailed() {
    // given
    when(sender.getTopology())
        .thenReturn(topology(Optional.of(completed(7, Status.FAILED)), Optional.empty()));

    // when - then
    assertThat(awaiter.awaitCompletion(plan(7)))
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableOfType(ExecutionException.class);
  }

  @Test
  void shouldFailWhenSubmitFails() {
    // given
    final var submission =
        CompletableFuture.completedFuture(
            Either.<ErrorResponse, ClusterConfigurationChangeResponse>left(
                new ErrorResponse(ErrorCode.INVALID_REQUEST, "nope")));

    // when - then
    assertThat(awaiter.awaitCompletion(submission))
        .failsWithin(Duration.ofSeconds(1))
        .withThrowableOfType(ExecutionException.class);
  }

  @Test
  void shouldFailWhenChangeNeverCompletes() {
    // given
    final var timingOutAwaiter =
        new ClusterConfigurationChangeAwaiter(sender, Duration.ofMillis(1), Duration.ofMillis(5));
    when(sender.getTopology()).thenReturn(topology(Optional.empty(), Optional.of(pending(7))));

    // when - then
    assertThat(timingOutAwaiter.awaitCompletion(plan(7)))
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
                null)));
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
                null)));
  }

  private CompletableFuture<Either<ErrorResponse, ClusterConfiguration>> topology(
      final Optional<CompletedChange> lastChange,
      final Optional<ClusterChangePlan> pendingChanges) {
    return CompletableFuture.completedFuture(
        Either.right(
            new ClusterConfiguration(
                1,
                Map.of(),
                lastChange,
                pendingChanges,
                Optional.empty(),
                Optional.empty(),
                0,
                Optional.empty())));
  }

  private ClusterChangePlan pending(final long changeId) {
    return ClusterChangePlan.init(changeId, List.of());
  }

  private CompletedChange completed(final long changeId, final Status status) {
    return new CompletedChange(changeId, status, Instant.now(), Instant.now());
  }
}
