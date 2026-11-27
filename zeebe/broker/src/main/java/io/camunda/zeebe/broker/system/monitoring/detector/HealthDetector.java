/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthIssue;
import io.camunda.zeebe.util.health.HealthMonitorable;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.jspecify.annotations.Nullable;

/**
 * A node of the health tree whose children are the things a detector watches, for example one per
 * partition or one per cluster member. A child is {@link HealthStatus#HEALTHY} or, while its
 * condition holds, {@link HealthStatus#NOTICE} or {@link HealthStatus#DEGRADED}; the node reports
 * the worst of its children. Listeners are notified only when the report changes, so a condition
 * that holds does not notify on every sample.
 *
 * <p>Thread safe: listener-driven detectors update children from other components' threads.
 */
public abstract class HealthDetector implements HealthMonitorable {

  private final String name;
  private final Map<String, HealthReport> children = new TreeMap<>();
  private final List<FailureListener> listeners = new CopyOnWriteArrayList<>();
  private volatile HealthReport report;

  protected HealthDetector(final String name) {
    this.name = name;
    report = new HealthReport(name, HealthStatus.HEALTHY, null, Map.of());
  }

  @Override
  public String componentName() {
    return name;
  }

  @Override
  public HealthReport getHealthReport() {
    return report;
  }

  @Override
  public void addFailureListener(final FailureListener failureListener) {
    listeners.add(failureListener);
  }

  @Override
  public void removeFailureListener(final FailureListener failureListener) {
    listeners.remove(failureListener);
  }

  protected void markHealthy(final String child) {
    update(() -> children.put(child, report(child, HealthStatus.HEALTHY, null)));
  }

  protected void markDegraded(final String child, final String message, final Instant since) {
    mark(child, HealthStatus.DEGRADED, message, since);
  }

  /**
   * Marks a child as having a condition.
   *
   * @param status {@link HealthStatus#NOTICE} for something unusual but harmless, {@link
   *     HealthStatus#DEGRADED} for something that affects users of the broker
   */
  protected void mark(
      final String child, final HealthStatus status, final String message, final Instant since) {
    update(() -> children.put(child, report(child, status, HealthIssue.of(message, since))));
  }

  protected void remove(final String child) {
    update(() -> children.remove(child));
  }

  /** Removes every child not in {@code current}, for example partitions that moved away. */
  protected void retainOnly(final Set<String> current) {
    update(() -> children.keySet().retainAll(current));
  }

  private void update(final Runnable change) {
    // listeners are notified under the lock so that they see reports in the order they were made;
    // health monitors only enqueue the report on their actor, so this does not block
    synchronized (children) {
      change.run();
      final var next =
          HealthReport.fromChildrenStatus(name, children)
              .orElseGet(() -> new HealthReport(name, HealthStatus.HEALTHY, null, Map.of()));
      if (next.equals(report)) {
        return;
      }
      report = next;
      listeners.forEach(listener -> listener.onHealthReport(next));
    }
  }

  private static HealthReport report(
      final String child, final HealthStatus status, final @Nullable HealthIssue issue) {
    return new HealthReport(child, status, issue, Map.of());
  }
}
