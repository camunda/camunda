/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.dynamic.config.api;

import static io.camunda.zeebe.util.Unit.unit;

import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
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
   * submission failed, the change failed to apply, or the timeout elapses first.
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
    if (change.plannedChanges().isEmpty()) {
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
   * Determines the progress of the change we submitted, identified by {@code changeId}. {@code
   * lastChange} only advances once a change finishes, so it is authoritative: a completed change
   * with an id greater than or equal to ours means ours has finished. A newer change (higher id)
   * implies ours already completed before it, so we do not block on unrelated changes added
   * afterwards.
   */
  private static Progress progressOf(
      final ClusterConfiguration configuration, final long changeId) {
    final var lastChange = configuration.lastChange();
    if (lastChange.isEmpty() || lastChange.get().id() < changeId) {
      // Our change has not completed yet, keep waiting.
      return Progress.PENDING;
    }

    final var last = lastChange.get();
    if (last.id() == changeId) {
      return last.status() == Status.COMPLETED ? Progress.COMPLETED : Progress.FAILED;
    }
    // A newer change already completed, so ours finished before it.
    return Progress.COMPLETED;
  }

  private static Exception errorFrom(final ErrorResponse error) {
    return new IllegalStateException(error.code() + ": " + error.message());
  }

  private Executor delayedExecutor() {
    return CompletableFuture.delayedExecutor(pollInterval.toMillis(), TimeUnit.MILLISECONDS);
  }

  private enum Progress {
    PENDING,
    COMPLETED,
    FAILED
  }
}
