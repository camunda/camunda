/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

/** Failure Listener invoked by a {@link HealthMonitorable} component. */
public interface FailureListener {

  /** Invoked when the health status becomes unhealthy. */
  void onFailure(HealthReport report);

  /**
   * Invoked when health status becomes healthy after being unhealthy for some time. A component can
   * be marked unhealthy initially and set to healthy only after start up is complete. It is
   * expected to call {#onRecovered} when it is marked as healthy.
   */
  void onRecovered(HealthReport report);

  /**
   * Invoked when the health status becomes dead and the system can't become healthy again without
   * external intervention.
   */
  void onUnrecoverableFailure(HealthReport report);

  default void onHealthReport(final HealthReport healthReport) {
    switch (healthReport.getStatus()) {
      case HEALTHY -> onRecovered(healthReport);
      case UNHEALTHY -> onFailure(healthReport);
      case DEAD -> onUnrecoverableFailure(healthReport);
      default -> {}
    }
  }
}
