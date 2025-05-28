/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.camunda.zeebe.scheduler.ConcurrencyControl;
import io.camunda.zeebe.scheduler.ScheduledTimer;
import io.camunda.zeebe.scheduler.future.ActorFuture;
import io.camunda.zeebe.scheduler.future.CompletableActorFuture;
import io.camunda.zeebe.util.CloseableSilently;
import io.camunda.zeebe.util.ExponentialBackoff;
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthIssue;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MigrationSnapshotDirector implements HealthMonitorable, CloseableSilently {
  public static final String COMPONENT_NAME = "MigrationSnapshotDirector";
  private static final Logger LOG = LoggerFactory.getLogger(MigrationSnapshotDirector.class);

  private volatile boolean snapshotTaken = false;
  private ScheduledTimer runningSnapshot = null;
  private final ConcurrentMap<FailureListener, Boolean> listeners = new ConcurrentHashMap<>();
  private final AsyncSnapshotDirector snapshotDirector;

  private volatile HealthReport healthReport;
  private final ConcurrencyControl control;
  private final HealthMonitor healthMonitor;
  private final RetryState retryState = new RetryState();
  private volatile boolean closed = false;

  public MigrationSnapshotDirector(
      final AsyncSnapshotDirector asyncSnapshotDirector,
      final ConcurrencyControl control,
      final HealthMonitor healthMonitor) {
    snapshotDirector = asyncSnapshotDirector;
    this.control = control;
    this.healthMonitor = healthMonitor;
    healthReport = snapshotNotTaken();
    healthMonitor.registerComponent(this);
    LOG.debug("Initialized migration snapshot director. Scheduling snapshot");
    scheduleSnapshot();
  }

  @Override
  public void close() {
    if (!closed) {
      cancelScheduledSnapshot();
      healthMonitor.removeComponent(this);
      closed = true;
    }
  }

  public void scheduleSnapshot() {
    if (!snapshotTaken) {
      LOG.debug("Scheduling snapshot for migration.");
      forceSnapshotUntilSuccessful();
    }
  }

  public void cancelScheduledSnapshot() {
    if (runningSnapshot != null) {
      runningSnapshot.cancel();
      runningSnapshot = null;
    }
  }

  @VisibleForTesting
  public boolean isSnapshotTaken() {
    return snapshotTaken;
  }

  @Override
  public String componentName() {
    return COMPONENT_NAME;
  }

  @Override
  public HealthReport getHealthReport() {
    return healthReport;
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    listeners.put(failureListener, Boolean.TRUE);
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    listeners.remove(failureListener);
  }

  private void forceSnapshotUntilSuccessful() {
    forceSnapshotUntilSuccessful(null);
  }

  private void forceSnapshotUntilSuccessful(final Throwable error) {
    if (!isRecoverableError(error)) {
      LOG.warn("Snapshot cannot be taken due to unrecoverable error", error);
      close();
      return;
    }
    if (!snapshotTaken && runningSnapshot == null) {
      final var nextDelay = retryState.nextDelay();
      final var issue =
          error == null
              ? HealthIssue.of(
                  String.format(
                      "Snapshot not taken yet, retryCount=%d, will retry in %s",
                      retryState.getRetryCount(), nextDelay),
                  Instant.now())
              : HealthIssue.of(error, Instant.now());
      healthReport = healthReport.withIssue(issue);
      notifyListeners();
      runningSnapshot =
          control.schedule(
              nextDelay,
              () -> {
                forceSnapshot()
                    .onComplete(
                        (ignored, ex) -> {
                          forceSnapshotUntilSuccessful(ex);
                        },
                        control);
              });
    }
  }

  private boolean isRecoverableError(final Throwable error) {
    return error == null
        || (error instanceof IOException
            || (error instanceof CompletionException && error.getCause() instanceof IOException));
  }

  private ActorFuture<Void> forceSnapshot() {
    if (!snapshotTaken) {
      return snapshotDirector
          .forceSnapshot()
          .andThen(
              (snapshot, error) -> {
                if (snapshot != null) {
                  // reset the flag
                  snapshotTaken = true;
                  LOG.debug("Snapshot taken after migrations: {}", snapshot.getId());
                  healthReport = HealthReport.healthy(this);
                  notifyListeners();
                }
                runningSnapshot = null;
                return error != null
                    ? CompletableActorFuture.completedExceptionally(error)
                    : CompletableActorFuture.completed(null);
              },
              control);
    } else {
      return CompletableActorFuture.completed(null);
    }
  }

  private void notifyListeners() {
    listeners.forEach(
        (listener, ignored) -> {
          final var report = getHealthReport();
          listener.onHealthReport(report);
        });
  }

  private HealthReport snapshotNotTaken() {
    return HealthReport.unhealthy(this).withMessage("No snapshot taken yet", Instant.now());
  }

  private static final class RetryState {
    private int retryCount = 0;
    private final ExponentialBackoff backoff = new ExponentialBackoff(5000, 500);
    private long lastDelay = 500;

    public void retry() {
      retryCount++;
    }

    public Duration nextDelay() {
      retry();
      lastDelay = backoff.supplyRetryDelay(lastDelay);
      return Duration.ofMillis(lastDelay);
    }

    int getRetryCount() {
      return retryCount;
    }
  }
}
