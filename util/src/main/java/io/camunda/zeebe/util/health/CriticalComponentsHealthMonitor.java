/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.health;

import io.camunda.zeebe.util.sched.ActorControl;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

/** Healthy only if all components are healthy */
public class CriticalComponentsHealthMonitor implements HealthMonitor {
  private static final Duration HEALTH_MONITORING_PERIOD = Duration.ofSeconds(60);
  private final Map<String, MonitoredComponent> monitoredComponents = new HashMap<>();
  private final Map<String, HealthStatus> componentHealth = new HashMap<>();
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private final ActorControl actor;
  private final Logger log;

  private volatile HealthStatus healthStatus = HealthStatus.UNHEALTHY;

  public CriticalComponentsHealthMonitor(final ActorControl actor, final Logger log) {
    this.actor = actor;
    this.log = log;
  }

  @Override
  public void startMonitoring() {
    actor.runAtFixedRate(HEALTH_MONITORING_PERIOD, this::updateHealth);
  }

  @Override
  public void monitorComponent(final String componentName) {
    actor.run(() -> componentHealth.put(componentName, HealthStatus.UNHEALTHY));
  }

  @Override
  public void removeComponent(final String componentName) {
    actor.run(
        () -> {
          final var monitoredComponent = monitoredComponents.remove(componentName);
          if (monitoredComponent != null) {
            componentHealth.remove(componentName);
            monitoredComponent.component.removeFailureListener(monitoredComponent);
          }
        });
  }

  @Override
  public void registerComponent(final String componentName, final HealthMonitorable component) {
    actor.run(
        () -> {
          final var monitoredComponent = new MonitoredComponent(componentName, component);
          monitoredComponents.put(componentName, monitoredComponent);
          componentHealth.put(componentName, component.getHealthStatus());

          component.addFailureListener(monitoredComponent);
          calculateHealth();
        });
  }

  @Override
  public HealthStatus getHealthStatus() {
    return healthStatus;
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    actor.run(() -> failureListeners.add(failureListener));
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    actor.run(() -> failureListeners.remove(failureListener));
  }

  private void updateHealth() {
    componentHealth
        .keySet()
        .forEach(component -> componentHealth.put(component, getHealth(component)));
    calculateHealth();
  }

  private void calculateHealth() {
    final var previousStatus = healthStatus;
    healthStatus = calculateStatus();

    if (previousStatus == healthStatus) {
      return;
    }

    switch (healthStatus) {
      case HEALTHY:
        failureListeners.forEach(FailureListener::onRecovered);
        break;

      case UNHEALTHY:
        failureListeners.forEach(FailureListener::onFailure);
        break;

      case DEAD:
        failureListeners.forEach(FailureListener::onUnrecoverableFailure);
        break;

      default:
        log.warn("Unknown health status {}", healthStatus);
        break;
    }

    logComponentStatus(healthStatus);
  }

  private void logComponentStatus(final HealthStatus status) {
    log.debug(
        "Detected '{}' components. The current health status of components: {}",
        status,
        componentHealth);
  }

  private HealthStatus calculateStatus() {
    if (componentHealth.containsValue(HealthStatus.DEAD)) {
      return HealthStatus.DEAD;
    } else if (componentHealth.containsValue(HealthStatus.UNHEALTHY)) {
      return HealthStatus.UNHEALTHY;
    }

    return HealthStatus.HEALTHY;
  }

  private HealthStatus getHealth(final String componentName) {
    final var monitoredComponent = monitoredComponents.get(componentName);
    if (monitoredComponent != null) {
      return monitoredComponent.component.getHealthStatus();
    }

    return HealthStatus.UNHEALTHY;
  }

  /**
   * All onComponent* methods must check if the component was not removed in between, as there can
   * be a race condition between enqueuing the callback, removing the component, and executing the
   * callback.
   */
  private final class MonitoredComponent implements FailureListener {
    private final String componentName;
    private final HealthMonitorable component;

    private MonitoredComponent(final String componentName, final HealthMonitorable component) {
      this.componentName = componentName;
      this.component = component;
    }

    @Override
    public void onFailure() {
      actor.run(this::onComponentFailure);
    }

    @Override
    public void onRecovered() {
      actor.run(this::onComponentRecovered);
    }

    @Override
    public void onUnrecoverableFailure() {
      actor.run(this::onComponentDied);
    }

    private void onComponentFailure() {
      if (!monitoredComponents.containsKey(componentName)) {
        return;
      }

      log.warn("{} failed, marking it as unhealthy", componentName);
      componentHealth.put(componentName, HealthStatus.UNHEALTHY);
      calculateHealth();
    }

    private void onComponentRecovered() {
      if (!monitoredComponents.containsKey(componentName)) {
        return;
      }

      log.info("{} recovered, marking it as healthy", componentName);
      componentHealth.put(componentName, HealthStatus.HEALTHY);
      calculateHealth();
    }

    private void onComponentDied() {
      if (!monitoredComponents.containsKey(componentName)) {
        return;
      }

      log.error("{} failed, marking it as dead", componentName);
      componentHealth.put(componentName, HealthStatus.DEAD);
      calculateHealth();
    }
  }
}
