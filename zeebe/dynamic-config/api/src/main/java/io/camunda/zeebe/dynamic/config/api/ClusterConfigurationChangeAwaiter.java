/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.util.Unit.unit;

import io.camunda.zeebe.dynamic.config.state.CurrentClusterConfiguration;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.jspecify.annotations.NullMarked;

/**
 * Awaits completion of a cluster configuration change submitted through a {@link
 * ClusterConfigurationManagementRequestSender}, polling the cluster topology until the change has
 * been fully applied. Useful for callers that need a synchronous request/response contract on top
 * of the otherwise asynchronous cluster configuration change mechanism.
 */
@NullMarked
public final class ClusterConfigurationChangeAwaiter {

  private final ClusterConfigurationManagementRequestSender requestSender;
  private final Duration pollInterval;
  private final Duration timeout;

  public ClusterConfigurationChangeAwaiter(
      final ClusterConfigurationManagementRequestSender requestSender,
      final Duration pollInterval,
      final Duration timeout) {
    this.requestSender = requestSender;
    this.pollInterval = pollInterval;
    this.timeout = timeout;
  }

  /**
   * Completes once the change submitted via {@code submission} has been fully applied. Fails if
   * submission failed, the change failed to apply or was cancelled, its outcome could not be
   * observed before it aged out of the bounded history window, or the timeout elapses first.
   */
  public CompletableFuture<Void> awaitCompletion(
      final CompletableFuture<Either<ErrorResponse, ClusterConfigurationChangeResponse>>
          submission) {
    final long deadlineNanos = System.nanoTime() + timeout.toNanos();
    return submission.thenCompose(response -> onSubmitted(response, deadlineNanos));
  }

  private CompletableFuture<Void> onSubmitted(
      final Either<ErrorResponse, ClusterConfigurationChangeResponse> response,
      final long deadlineNanos) {
    if (response.isLeft()) {
      return CompletableFuture.failedFuture(errorFrom(response.getLeft()));
    }

    final var change = response.get();
    // An empty plan means the cluster is already in the requested state: nothing to await.
    final var phases =
        change.response() != null
            ? change.response().phases()
            : change.legacyResponse().plannedChanges();
    if (phases.isEmpty()) {
      return CompletableFuture.completedFuture(null);
    }

    return awaitChange(change.changeId(), deadlineNanos);
  }

  private CompletableFuture<Void> awaitChange(final long changeId, final long deadlineNanos) {
    return requestSender
        .getTopology()
        .thenCompose(
            topology -> {
              if (topology.isLeft()) {
                return CompletableFuture.failedFuture(errorFrom(topology.getLeft()));
              }

              return switch (progressOf(topology.get(), changeId)) {
                case COMPLETED -> CompletableFuture.completedFuture(unit());
                case FAILED ->
                    CompletableFuture.failedFuture(
                        new IllegalStateException(
                            "Cluster configuration change " + changeId + " failed to apply"));
                case CANCELLED ->
                    CompletableFuture.failedFuture(
                        new IllegalStateException(
                            "Cluster configuration change " + changeId + " was cancelled"));
                case UNKNOWN ->
                    CompletableFuture.failedFuture(
                        new IllegalStateException(
                            "Cluster configuration change "
                                + changeId
                                + " has an unknown outcome: it aged out of the bounded history"
                                + " window before it could be observed"));
                case PENDING -> {
                  if (System.nanoTime() >= deadlineNanos) {
                    yield CompletableFuture.failedFuture(
                        new TimeoutException(
                            "Timed out waiting for cluster configuration change " + changeId));
                  }
                  yield CompletableFuture.supplyAsync(() -> null, delayedExecutor())
                      .thenCompose(ignored -> awaitChange(changeId, deadlineNanos));
                }
              };
            });
  }

  /**
   * Determines the progress of the change we submitted, identified by {@code changeId}, from the
   * {@link io.camunda.zeebe.dynamic.config.state.PhasedChangeState} of whichever member answered
   * this poll of {@link ClusterConfigurationManagementRequestSender#getTopology()} (normally the
   * coordinator, but a coordinator failover between submission and awaiting can change who that
   * is). Plans can now run concurrently (scoped to disjoint physical tenants), so a newer id
   * resolving does not imply ours has: unlike the single-pending-plan model, {@code changeId} must
   * be looked up directly rather than compared against "the last completed change".
   */
  private static Progress progressOf(
      final CurrentClusterConfiguration configuration, final long changeId) {
    final var phasedChangeState = configuration.phasedChangeState();
    if (phasedChangeState.pending().containsKey(changeId)) {
      return Progress.PENDING;
    }

    final var completed = phasedChangeState.historyFor(changeId);
    if (completed.isPresent()) {
      return switch (completed.get().status()) {
        case COMPLETED -> Progress.COMPLETED;
        case CANCELLED -> Progress.CANCELLED;
        case FAILED -> Progress.FAILED;
      };
    }

    if (!phasedChangeState.wasIssued(changeId)) {
      // The member we polled has not gossiped changeId yet: from its perspective the change has
      // not started, which is indistinguishable from still pending.
      return Progress.PENDING;
    }

    // Resolved (issued, not pending, no history entry): it aged out of the bounded history window
    // before we polled its outcome. The window retains at most 10 terminal records (across
    // COMPLETED, FAILED, and CANCELLED), so with concurrent per-tenant plans any of those can be
    // evicted before the next poll — we genuinely cannot tell which one this was, so report it as
    // UNKNOWN rather than asserting either COMPLETED (hides a real failure) or FAILED (asserts a
    // failure that may not have happened).
    return Progress.UNKNOWN;
  }

  private static Exception errorFrom(final ErrorResponse error) {
    return error.toException();
  }

  private Executor delayedExecutor() {
    return CompletableFuture.delayedExecutor(pollInterval.toMillis(), TimeUnit.MILLISECONDS);
  }

  private enum Progress {
    PENDING,
    COMPLETED,
    FAILED,
    CANCELLED,
    UNKNOWN
  }
}
