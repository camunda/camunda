/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.util.health;

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
   * component is registered using {@link #registerComponent(String, HealthMonitorable)}
   */
  void monitorComponent(String componentName);

  /**
   * Stop monitoring the component.
   *
   * @param componentName
   */
  void removeComponent(String componentName);

  /**
   * Register the component to be monitored
   *
   * @param componentName
   * @param component
   */
  void registerComponent(String componentName, HealthMonitorable component);
}
