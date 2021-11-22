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
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;

/** Healthy only if all components are healthy */
public class CriticalComponentsHealthMonitor implements HealthMonitor {
  private static final Duration HEALTH_MONITORING_PERIOD = Duration.ofSeconds(60);
  private final Map<String, MonitoredComponent> monitoredComponents = new HashMap<>();
  private final Map<String, HealthReport> componentHealth = new HashMap<>();
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private final ActorControl actor;
  private final Logger log;

  @SuppressWarnings("java:S3077") // allow volatile here, health is immutable
  private volatile HealthReport healthReport =
      HealthReport.unhealthy(this).withMessage("Components are not yet initialized");

  private final String name;

  public CriticalComponentsHealthMonitor(
      final String name, final ActorControl actor, final Logger log) {
    this.name = name;
    this.actor = actor;
    this.log = log;
  }

  @Override
  public void startMonitoring() {
    actor.runAtFixedRate(HEALTH_MONITORING_PERIOD, this::updateHealth);
  }

  @Override
  public void monitorComponent(final String componentName) {
    actor.run(() -> componentHealth.put(componentName, HealthReport.unknown(componentName)));
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
          componentHealth.put(componentName, component.getHealthReport());

          component.addFailureListener(monitoredComponent);
          calculateHealth();
        });
  }

  @Override
  public String getName() {
    return name;
  }

  @Override
  public HealthReport getHealthReport() {
    return healthReport;
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
    final var previousReport = healthReport;
    healthReport = calculateStatus();

    if (previousReport == healthReport) {
      return;
    }

    switch (healthReport.getStatus()) {
      case HEALTHY:
        failureListeners.forEach(FailureListener::onRecovered);
        break;

      case UNHEALTHY:
        failureListeners.forEach((l) -> l.onFailure(healthReport));
        break;

      case DEAD:
        failureListeners.forEach((l) -> l.onUnrecoverableFailure(healthReport));
        break;

      default:
        log.warn("Unknown health status {}", healthReport);
        break;
    }

    logComponentStatus(healthReport);
  }

  private void logComponentStatus(final HealthReport status) {
    log.debug(
        "Detected '{}' components. The current health status of components: {}",
        status.getStatus(),
        componentHealth.values());
  }

  private HealthReport calculateStatus() {
    final var componentByStatus =
        componentHealth.values().stream()
            .collect(Collectors.toMap(HealthReport::getStatus, Function.identity(), (l, r) -> l));
    final var deadReport = componentByStatus.get(HealthStatus.DEAD);
    final var unhealthyReport = componentByStatus.get(HealthStatus.UNHEALTHY);
    if (deadReport != null) {
      return HealthReport.dead(this).withIssue(deadReport);
    } else if (unhealthyReport != null) {
      return HealthReport.unhealthy(this).withIssue(unhealthyReport);
    } else {
      return HealthReport.healthy(this);
    }
  }

  private HealthReport getHealth(final String componentName) {
    final var monitoredComponent = monitoredComponents.get(componentName);
    if (monitoredComponent != null) {
      return monitoredComponent.component.getHealthReport();
    }

    return HealthReport.unknown(componentName);
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
    public void onFailure(final HealthReport report) {
      actor.run(() -> onComponentFailure(report));
    }

    @Override
    public void onRecovered() {
      actor.run(this::onComponentRecovered);
    }

    @Override
    public void onUnrecoverableFailure(final HealthReport report) {
      actor.run(() -> onComponentDied(report));
    }

    private void onComponentFailure(final HealthReport report) {
      if (!monitoredComponents.containsKey(componentName)) {
        return;
      }

      log.warn("{} failed, marking it as unhealthy: {}", componentName, healthReport);
      componentHealth.put(componentName, report);
      calculateHealth();
    }

    private void onComponentRecovered() {
      if (!monitoredComponents.containsKey(componentName)) {
        return;
      }

      log.info("{} recovered, marking it as healthy", componentName);
      componentHealth.put(componentName, HealthReport.healthy(component));
      calculateHealth();
    }

    private void onComponentDied(final HealthReport report) {
      if (!monitoredComponents.containsKey(componentName)) {
        return;
      }

      log.error("{} failed, marking it as dead: {}", componentName, report);
      componentHealth.put(componentName, report);
      calculateHealth();
    }
  }
}
