/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.health;

import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableMap;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;

/**
 * A health report of a {@link #getComponentName() component}. If the status is not healthy, the
 * report also contains a {@link #getIssue() issue}.
 *
 * @param componentName name of the component
 * @param status of the component if it does not have any childre, or "worst" HealthStatus of the
 *     children
 * @param issue null if status is HEALTHY
 * @param children immutable map that contains the health reports of the children of this component.
 */
public record HealthReport(
    String componentName,
    HealthStatus status,
    HealthIssue issue,
    ImmutableMap<String, HealthReport> children) {
  public static final Comparator<HealthReport> COMPARATOR =
      (a, b) -> HealthStatus.COMPARATOR.compare(a.status, b.status);

  public HealthReport {
    requireNonNull(componentName, "componentName cannot be null");
    requireNonNull(status, "status cannot be null");
    requireNonNull(children, "children cannot be null");
  }

  private HealthReport(
      final HealthMonitorable component,
      final HealthStatus status,
      final HealthIssue issue,
      final Map<String, HealthReport> children) {
    this(component.componentName(), status, issue, ImmutableMap.copyOf(children));
  }

  public static Optional<HealthReport> fromChildrenStatus(
      final String componentName, final Map<String, HealthReport> children) {
    // Create a defensive copy to avoid ConcurrentModificationException when
    // the original map is modified while iterating over the HealthReport
    final var childrenSnapshot = ImmutableMap.copyOf(children);
    final var worstReport = childrenSnapshot.values().stream().max(COMPARATOR);
    return worstReport.map(
        report ->
            new HealthReport(componentName, report.status(), report.issue(), childrenSnapshot));
  }

  public static HealthReport unknown(final String componentName) {
    return new HealthReport(componentName, HealthStatus.UNHEALTHY, null, ImmutableMap.of());
  }

  public static HealthReport healthy(final HealthMonitorable component) {
    return new HealthReport(component, HealthStatus.HEALTHY, null, ImmutableMap.of());
  }

  public static HealthReport unhealthy(final HealthMonitorable component) {
    return new HealthReport(component, HealthStatus.UNHEALTHY, null, ImmutableMap.of());
  }

  public static HealthReport dead(final HealthMonitorable component) {
    return new HealthReport(component, HealthStatus.DEAD, null, ImmutableMap.of());
  }

  public static HealthReport fromStatus(
      final HealthStatus status, final HealthMonitorable component) {
    return switch (status) {
      case HEALTHY -> HealthReport.healthy(component);
      case UNHEALTHY -> HealthReport.unhealthy(component);
      case DEAD -> HealthReport.dead(component);
      case null -> HealthReport.unknown(component.componentName());
    };
  }

  public boolean isHealthy() {
    return status == HealthStatus.HEALTHY;
  }

  public boolean isNotHealthy() {
    return status != HealthStatus.HEALTHY;
  }

  public boolean isUnhealthy() {
    return status == HealthStatus.UNHEALTHY;
  }

  public boolean isDead() {
    return status == HealthStatus.DEAD;
  }

  public String getComponentName() {
    return componentName;
  }

  public HealthStatus getStatus() {
    return status;
  }

  public HealthIssue getIssue() {
    return issue;
  }

  public HealthReport withIssue(final HealthIssue issue) {
    return new HealthReport(componentName, status, issue, children);
  }

  public HealthReport withMessage(final String message, final Instant since) {
    return new HealthReport(componentName, status, HealthIssue.of(message, since), children);
  }

  public HealthReport withIssue(final Throwable e, final Instant since) {
    return new HealthReport(componentName, status, HealthIssue.of(e, since), children);
  }

  public HealthReport withName(final String name) {
    return new HealthReport(name, status, issue, children);
  }
}
