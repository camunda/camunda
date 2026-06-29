/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.partitions;

import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * A partition's role-transition health, as an explicit leaf of the partition's health tree. It
 * reflects the role-transition lifecycle as a single concern:
 *
 * <ul>
 *   <li>whether the partition's services are installed,
 *   <li>the live health issue reported by the {@link PartitionTransition} (e.g. a transition that
 *       appears blocked), and
 *   <li>the sticky "dead" latch set on an unrecoverable failure ("once dead, stays dead").
 * </ul>
 *
 * Starts unhealthy ("services not installed") until a role transition completes successfully.
 */
final class PartitionTransitionHealth implements HealthMonitorable {

  static final String COMPONENT_NAME = "PartitionTransition";

  // getHealthReport re-evaluates state, so it is not a pure read; it runs both on the partition
  // actor (health probe, setters) and on the metric scrape thread. All access to the mutable state
  // below is therefore guarded by this monitor.
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private final PartitionTransition partitionTransition;
  private HealthReport healthReport;
  private boolean servicesInstalled;

  PartitionTransitionHealth(final PartitionTransition partitionTransition) {
    this.partitionTransition = partitionTransition;
  }

  @Override
  public String componentName() {
    return COMPONENT_NAME;
  }

  @Override
  public synchronized HealthReport getHealthReport() {
    // Re-evaluated on read so a transition that becomes blocked after the fact (its health issue is
    // surfaced by the transition itself) is reflected without an explicit notification.
    updateHealthStatus();
    return healthReport;
  }

  @Override
  public synchronized void addFailureListener(final FailureListener failureListener) {
    failureListeners.add(failureListener);
  }

  @Override
  public synchronized void removeFailureListener(final FailureListener failureListener) {
    failureListeners.remove(failureListener);
  }

  synchronized void setServicesInstalled(final boolean servicesInstalled) {
    this.servicesInstalled = servicesInstalled;
    updateHealthStatus();
  }

  synchronized void onUnrecoverableFailure(final Throwable error) {
    // Once dead, stays dead. The sticky latch lives on this leaf; a DEAD leaf makes the partition
    // monitor (and the rest of the tree) report DEAD under worst-wins aggregation.
    healthReport = HealthReport.dead(this).withIssue(error, Instant.now());
  }

  private void updateHealthStatus() {
    final var previousStatus = healthReport;
    if (previousStatus != null && previousStatus.isDead()) {
      return;
    }

    final var instant = Instant.now();
    final var partitionTransitionHealthIssue = partitionTransition.getHealthIssue();
    if (partitionTransitionHealthIssue != null) {
      healthReport = HealthReport.unhealthy(this).withIssue(partitionTransitionHealthIssue);
    } else if (!servicesInstalled) {
      healthReport = HealthReport.unhealthy(this).withMessage("Services not installed", instant);
    } else {
      healthReport = HealthReport.healthy(this);
    }

    if (!Objects.equals(previousStatus, healthReport)) {
      failureListeners.forEach(l -> l.onHealthReport(healthReport));
    }
  }
}
