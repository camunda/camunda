/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.health;

/** Any component that can be monitored for health should implement this interface. */
public interface HealthMonitorable {

  /**
   * Used by a HealthMonitor to get the name of this component. Most `HealthMonitorable`s override
   * this method to return their actor name. If they don't override the method, the name of the
   * class is returned.
   *
   * @return the name of this component
   */
  default String getName() {
    return getClass().getSimpleName();
  }

  /**
   * Used by a HealthMonitor to get the health status of this component, typically invoked
   * periodically.
   *
   * @return health status
   */
  HealthReport getHealthReport();

  /**
   * Register a failure observer.
   *
   * @param failureListener failure observer to be invoked when a failure that affects the health
   *     status of this component occurs
   */
  void addFailureListener(FailureListener failureListener);

  /**
   * Removes a previously registered listener. Should do nothing if it was not previously
   * registered.
   *
   * @param failureListener the failure listener to remove
   */
  void removeFailureListener(FailureListener failureListener);
}
