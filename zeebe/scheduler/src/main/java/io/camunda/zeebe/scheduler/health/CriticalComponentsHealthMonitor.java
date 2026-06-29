/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.scheduler.health;

import io.camunda.zeebe.scheduler.ActorControl;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthMonitor;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthNodePosition;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthTreeListener;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;

/**
 * Healthy only if all components are healthy.
 *
 * <p>This is a pure aggregator: it computes its own health from its children and propagates changes
 * to its failure listeners. It owns no metric state. Whenever its set of children changes it tells
 * a single {@link HealthTreeListener} so that a projection of the tree (e.g. metrics) can stay in
 * sync without a parallel, hand-maintained graph. Each node carries its {@link HealthNodePosition},
 * so the listener derives the node's path and tenant/partition attribution from its position alone.
 */
public class CriticalComponentsHealthMonitor implements HealthMonitor {
  private static final Duration HEALTH_MONITORING_PERIOD = Duration.ofSeconds(60);
  private final Map<String, MonitoredComponent> monitoredComponents = new HashMap<>();
  private final Map<String, HealthReport> componentHealth = new HashMap<>();
  private final Set<FailureListener> failureListeners = new HashSet<>();
  private final ActorControl actor;
  private final Logger log;

  @SuppressWarnings("java:S3077") // allow volatile here, health is immutable
  private volatile HealthReport healthReport;

  private final String name;
  private final HealthNodePosition position;
  private final Duration monitoringInterval;
  private final HealthTreeListener treeListener;

  public CriticalComponentsHealthMonitor(
      final ActorControl actor,
      final HealthTreeListener treeListener,
      final HealthNodePosition position,
      final Logger log) {
    this(actor, treeListener, position, log, HEALTH_MONITORING_PERIOD);
  }

  public CriticalComponentsHealthMonitor(
      final ActorControl actor,
      final HealthTreeListener treeListener,
      final HealthNodePosition position,
      final Logger log,
      final Duration monitoringInterval) {
    name = position.name();
    this.actor = actor;
    this.log = log;
    this.treeListener = treeListener;
    this.position = position;
    this.monitoringInterval = monitoringInterval;
    healthReport =
        HealthReport.unhealthy(this)
            .withMessage("Components are not yet initialized", Instant.now());
  }

  @Override
  public void startMonitoring() {
    final var initialDelay = Math.max(5, monitoringInterval.toSeconds() / 5);
    actor.schedule(Duration.ofSeconds(initialDelay), this::updateHealth);
    actor.runAtFixedRate(monitoringInterval, this::updateHealth);
  }

  @Override
  public void monitorComponent(final String componentName) {
    actor.run(() -> componentHealth.put(componentName, HealthReport.unknown(componentName)));
  }

  @Override
  public void registerComponent(final HealthMonitorable component) {
    actor.run(
        () -> {
          final var componentName = component.componentName();
          final var monitoredComponent = new MonitoredComponent(componentName, component);
          monitoredComponents.put(componentName, monitoredComponent);
          componentHealth.put(componentName, component.getHealthReport());

          component.addFailureListener(monitoredComponent);
          calculateHealth();
          treeListener.onNodeRegistered(component, positionOf(component));
          log.trace("Registered component {}", componentName);
        });
  }

  @Override
  public void removeComponent(final HealthMonitorable component) {
    actor.run(
        () -> {
          final var componentName = component.componentName();
          final var monitoredComponent = monitoredComponents.remove(componentName);
          if (monitoredComponent != null) {
            componentHealth.remove(componentName);
            monitoredComponent.component.removeFailureListener(monitoredComponent);
            treeListener.onNodeRemoved(component);
            log.trace("Unregistered component {}", componentName);
            calculateHealth();
          }
        });
  }

  @Override
  public String componentName() {
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

  /** The position of this node within the health tree. */
  public HealthNodePosition position() {
    return position;
  }

  /**
   * Derives the position of a child node. An interior child (another monitor) already carries its
   * own position; a leaf inherits this node's tenant/partition attribution under this node's path.
   */
  private HealthNodePosition positionOf(final HealthMonitorable component) {
    if (component instanceof final CriticalComponentsHealthMonitor childMonitor) {
      return childMonitor.position;
    }
    return position.child(component.componentName());
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

    if (previousReport.equals(healthReport)) {
      return;
    }

    failureListeners.forEach(l -> l.onHealthReport(healthReport));
    logComponentStatus(healthReport);
  }

  private void logComponentStatus(final HealthReport status) {
    log.debug(
        "Detected '{}' components. The current health status of components: {}",
        status.getStatus(),
        componentHealth.values());
  }

  private HealthReport calculateStatus() {
    return HealthReport.fromChildrenStatus(name, componentHealth)
        .orElse(HealthReport.unknown(name));
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
    public void onRecovered(final HealthReport report) {
      actor.run(() -> onComponentRecovered(report));
    }

    @Override
    public void onUnrecoverableFailure(final HealthReport report) {
      actor.run(() -> onComponentDied(report));
    }

    private void onComponentFailure(final HealthReport report) {
      if (!monitoredComponents.containsKey(componentName)) {
        return;
      }

      log.warn("{} failed, marking it as unhealthy: {}", componentName, report);
      componentHealth.put(componentName, report);
      calculateHealth();
      failureListeners.forEach(l -> l.onFailure(getHealthReport()));
    }

    private void onComponentRecovered(final HealthReport healthReport) {
      if (!monitoredComponents.containsKey(componentName)) {
        return;
      }

      log.info("{} recovered, marking it as healthy", componentName);
      componentHealth.put(componentName, healthReport);
      calculateHealth();
      failureListeners.forEach(l -> l.onRecovered(getHealthReport()));
    }

    private void onComponentDied(final HealthReport report) {
      if (!monitoredComponents.containsKey(componentName)) {
        return;
      }

      log.error("{} failed, marking it as dead: {}", componentName, report);
      componentHealth.put(componentName, report);
      calculateHealth();
      failureListeners.forEach(l -> l.onUnrecoverableFailure(getHealthReport()));
    }
  }
}
