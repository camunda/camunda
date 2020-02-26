/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.util.health;

import io.zeebe.util.sched.ActorControl;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;

/** Healthy only if all components are healthy */
public class CriticalComponentsHealthMonitor implements HealthMonitor {
  private static final Duration HEALTH_MONITORING_PERIOD = Duration.ofSeconds(60);
  private final Map<String, HealthMonitorable> monitoredComponents = new HashMap<>();
  private final Map<String, HealthStatus> componentHealth = new HashMap<>();
  private volatile HealthStatus healthStatus = HealthStatus.UNHEALTHY;
  private final ActorControl actor;
  private final Logger log;
  private FailureListener failureListener;

  public CriticalComponentsHealthMonitor(final ActorControl actor, final Logger log) {
    this.actor = actor;
    this.log = log;
  }

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
          monitoredComponents.remove(componentName);
          componentHealth.remove(componentName);
        });
  }

  @Override
  public void registerComponent(final String componentName, final HealthMonitorable component) {
    actor.run(
        () -> {
          monitoredComponents.put(componentName, component);
          component.addFailureListener(new ComponentFailureListener(componentName));
          componentHealth.put(componentName, component.getHealthStatus());
          calculateHealth();
        });
  }

  @Override
  public HealthStatus getHealthStatus() {
    return healthStatus;
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    actor.run(() -> this.failureListener = failureListener);
  }

  private void onComponentFailure(final String componentName) {
    actor.run(
        () -> {
          log.error("{} failed, marking it as unhealthy", componentName);
          componentHealth.put(componentName, HealthStatus.UNHEALTHY);
          calculateHealth();
        });
  }

  private void onComponentRecovered(final String componentName) {
    actor.run(
        () -> {
          log.error("{} recovered, marking it as healthy", componentName);
          componentHealth.put(componentName, HealthStatus.HEALTHY);
        });
  }

  private void updateHealth() {
    componentHealth
        .keySet()
        .forEach(component -> componentHealth.put(component, getHealth(component)));
    calculateHealth();
  }

  private void calculateHealth() {
    final var status =
        componentHealth.containsValue(HealthStatus.UNHEALTHY)
            ? HealthStatus.UNHEALTHY
            : HealthStatus.HEALTHY;
    final var previousStatus = healthStatus;
    healthStatus = status;

    if (failureListener != null && previousStatus != status) {
      switch (status) {
        case HEALTHY:
          failureListener.onRecovered();
          break;
        case UNHEALTHY:
          failureListener.onFailure();
          break;
        default:
          log.warn("Unknown health status {}", status);
          break;
      }
    }
  }

  private HealthStatus getHealth(final String componentName) {
    final HealthMonitorable component = monitoredComponents.get(componentName);
    if (component != null) {
      return component.getHealthStatus();
    }
    return HealthStatus.UNHEALTHY;
  }

  class ComponentFailureListener implements FailureListener {
    private final String componentName;

    ComponentFailureListener(final String componentName) {
      this.componentName = componentName;
    }

    @Override
    public void onFailure() {
      onComponentFailure(componentName);
    }

    @Override
    public void onRecovered() {
      onComponentRecovered(componentName);
    }
  }
}
