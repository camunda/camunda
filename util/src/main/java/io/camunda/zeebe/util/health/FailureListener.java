/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.health;

/** Failure Listener invoked by a {@link HealthMonitorable} component. */
public interface FailureListener {

  /** Invoked when the health status becomes unhealthy. */
  void onFailure();

  /**
   * Invoked when health status becomes healthy after being unhealthy for some time. A component can
   * be marked unhealthy initially and set to healthy only after start up is complete. It is
   * expected to call {#onRecovered} when it is marked as healthy.
   */
  void onRecovered();

  /**
   * Invoked when the health status becomes dead and the system can't become healthy again without
   * external intervention.
   */
  void onUnrecoverableFailure();
}
