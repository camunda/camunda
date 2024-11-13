/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

/**
 * A HealthMonitor keeps tracks of all components it should monitor and calculates aggregate health
 * status.
 */
public interface HealthMonitor extends HealthMonitorable {

  /**
   * Starts necessary services for monitoring. Typically implemented by a monitor to start periodic
   * monitoring.
   */
  void startMonitoring();

  /**
   * Add a component name to be monitored. The component will be marked not healthy until the
   * component is registered using {@link #registerComponent(HealthMonitorable)}.
   *
   * <p>The component name must be consistent with the value returned by the method {@link
   * HealthMonitorable#getName()}
   */
  void monitorComponent(String componentName);

  /**
   * Register the component to be monitored.
   *
   * <p>The implementation must use the {@link HealthMonitorable#getName()} field to identify a
   * component
   *
   * @param component to register
   */
  void registerComponent(final HealthMonitorable component);

  /**
   * Stop monitoring the component.
   *
   * <p>The implementation must use the {@link HealthMonitorable#getName()} field to identify a
   * component
   *
   * @param component to be removed
   */
  void removeComponent(final HealthMonitorable component);
}
