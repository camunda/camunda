/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.shared.management;

import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationChangeResponse;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequest.ExportingStateChangeRequest;
import io.camunda.zeebe.dynamic.config.api.ClusterConfigurationManagementRequestSender;
import io.camunda.zeebe.dynamic.config.api.ErrorResponse;
import io.camunda.zeebe.dynamic.config.state.ClusterChangePlan.Status;
import io.camunda.zeebe.dynamic.config.state.ClusterConfiguration;
import io.camunda.zeebe.dynamic.config.state.ExportingState;
import io.camunda.zeebe.util.Either;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

/**
 * Controls exporting through dynamic cluster configuration. Each operation submits a single
 * cluster-wide {@code ExportingStateChangeRequest} and completes only once the resulting
 * configuration change has been fully applied, preserving the synchronous contract expected by the
 * exporting actuator endpoint.
 */
public class ClusterConfigExportingControlService {

  private static final Duration DEFAULT_POLL_INTERVAL = Duration.ofMillis(200);
  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(60);

  private final ClusterConfigurationManagementRequestSender requestSender;
  private final Duration pollInterval;
  private final Duration timeout;

  public ClusterConfigExportingControlService(
      final ClusterConfigurationManagementRequestSender requestSender) {
    this(requestSender, DEFAULT_POLL_INTERVAL, DEFAULT_TIMEOUT);
  }

  ClusterConfigExportingControlService(
      final ClusterConfigurationManagementRequestSender requestSender,
      final Duration pollInterval,
      final Duration timeout) {
    this.requestSender = requestSender;
    this.pollInterval = pollInterval;
    this.timeout = timeout;
  }

  public CompletableFuture<Void> pauseExporting(final String physicalTenantId) {
    return changeState(ExportingState.PAUSED);
  }

  public CompletableFuture<Void> softPauseExporting(final String physicalTenantId) {
    return changeState(ExportingState.SOFT_PAUSED);
  }

  public CompletableFuture<Void> resumeExporting(final String physicalTenantId) {
    return changeState(ExportingState.EXPORTING);
  }

  private CompletableFuture<Void> changeState(final ExportingState targetState) {
    final long deadlineNanos = System.nanoTime() + timeout.toNanos();
    return requestSender
        .changeExporterState(new ExportingStateChangeRequest(targetState, false))
        .thenCompose(response -> onChangeSubmitted(response, deadlineNanos));
  }

  private CompletableFuture<Void> onChangeSubmitted(
      final Either<ErrorResponse, ClusterConfigurationChangeResponse> response,
      final long deadlineNanos) {
    if (response.isLeft()) {
      return CompletableFuture.failedFuture(errorFrom(response.getLeft()));
    }

    final var change = response.get();
    // An empty plan means all exporters are already in the requested state: nothing to await.
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
                case COMPLETED -> CompletableFuture.completedFuture((Void) null);
                case FAILED ->
                    CompletableFuture.failedFuture(
                        new IllegalStateException(
                            "Exporter state change " + changeId + " failed to apply"));
                case PENDING -> {
                  if (System.nanoTime() >= deadlineNanos) {
                    yield CompletableFuture.failedFuture(
                        new java.util.concurrent.TimeoutException(
                            "Timed out waiting for exporter state change " + changeId));
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
