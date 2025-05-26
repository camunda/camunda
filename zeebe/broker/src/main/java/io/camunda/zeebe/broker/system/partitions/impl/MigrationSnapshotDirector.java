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
import io.camunda.zeebe.util.VisibleForTesting;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.time.Duration;
import java.time.Instant;
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
  private final boolean areMigrationsPerformed;
  private final Duration scheduleDelay;
  private final ConcurrencyControl control;
  private final HealthMonitor healthMonitor;

  public MigrationSnapshotDirector(
      final AsyncSnapshotDirector asyncSnapshotDirector,
      final boolean areMigrationsPerformed,
      final ConcurrencyControl control,
      final Duration scheduleDelay,
      final HealthMonitor healthMonitor) {
    snapshotDirector = asyncSnapshotDirector;
    this.areMigrationsPerformed = areMigrationsPerformed;
    this.control = control;
    this.scheduleDelay = scheduleDelay;
    this.healthMonitor = healthMonitor;
    if (areMigrationsPerformed) {
      healthReport = snapshotNotTaken();
      scheduleSnapshot();
    } else {
      healthReport = HealthReport.healthy(this);
    }
    healthMonitor.registerComponent(this);
    LOG.debug("Initialized migration snapshot director with status {}", healthReport);
  }

  @Override
  public void close() {
    cancelScheduledSnapshot();
    healthMonitor.removeComponent(this);
  }

  public void scheduleSnapshot() {
    if (areMigrationsPerformed && !snapshotTaken) {
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
    if (!snapshotTaken && runningSnapshot == null) {
      runningSnapshot =
          control.schedule(
              scheduleDelay,
              () -> {
                forceSnapshot()
                    .onComplete((ignored, error) -> forceSnapshotUntilSuccessful(), control);
              });
    }
  }

  private ActorFuture<Void> forceSnapshot() {
    if (!snapshotTaken) {
      return snapshotDirector
          .forceSnapshot()
          .thenApply(
              snapshot -> {
                if (snapshot != null) {
                  // reset the flag
                  snapshotTaken = true;
                  LOG.debug("Snapshot taken after migrations");
                  healthReport = HealthReport.healthy(this);
                  notifyListeners();
                } else {
                  LOG.debug("Snapshot not taken after migrations");
                }
                runningSnapshot = null;
                return null;
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
}
