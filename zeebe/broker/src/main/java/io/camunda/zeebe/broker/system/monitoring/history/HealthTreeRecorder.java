/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.history;

import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthIssue;
import io.camunda.zeebe.util.health.HealthReport;
import io.camunda.zeebe.util.health.HealthStatus;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Records every status change in the broker's health tree as a {@link #HEALTH_CHANGED} entry. It
 * listens to the root of the tree and compares each new tree with the previous one.
 *
 * <p>Only leaves are recorded: a parent's status is the worst of its children's, so recording it
 * too would repeat one change once per level. A leaf that appears with a status other than healthy,
 * or disappears while not healthy, is recorded as a change from or to {@code NONE}.
 */
public final class HealthTreeRecorder implements FailureListener {

  public static final String HEALTH_CHANGED = "HEALTH_CHANGED";
  private static final String NONE = "NONE";

  private final HealthHistory history;
  private @Nullable HealthReport previous;

  public HealthTreeRecorder(final HealthHistory history) {
    this.history = history;
  }

  @Override
  public void onFailure(final HealthReport report) {
    onHealthReport(report);
  }

  @Override
  public void onRecovered(final HealthReport report) {
    onHealthReport(report);
  }

  @Override
  public void onUnrecoverableFailure(final HealthReport report) {
    onHealthReport(report);
  }

  @Override
  public synchronized void onHealthReport(final HealthReport report) {
    if (report.equals(previous)) {
      return;
    }
    compare(report.componentName(), previous, report);
    previous = report;
  }

  private void compare(
      final String path, final @Nullable HealthReport before, final @Nullable HealthReport after) {
    final var beforeChildren = before == null ? Map.<String, HealthReport>of() : before.children();
    final var afterChildren = after == null ? Map.<String, HealthReport>of() : after.children();

    if (beforeChildren.isEmpty() && afterChildren.isEmpty()) {
      compareLeaf(path, before, after);
      return;
    }

    final var names = new LinkedHashSet<>(beforeChildren.keySet());
    names.addAll(afterChildren.keySet());
    for (final var name : names) {
      compare(path + "/" + name, beforeChildren.get(name), afterChildren.get(name));
    }
  }

  private void compareLeaf(
      final String path, final @Nullable HealthReport before, final @Nullable HealthReport after) {
    final var from = before == null ? null : before.status();
    final var to = after == null ? null : after.status();
    if (from == to) {
      return;
    }
    if (from == null && to == HealthStatus.HEALTHY || to == null && from == HealthStatus.HEALTHY) {
      return;
    }

    final var message =
        after != null && after.issue() != null
            ? describe(after.issue())
            : before != null && before.issue() != null
                ? "resolved: " + describe(before.issue())
                : null;
    final var details = new HashMap<String, String>();
    details.put("from", from == null ? NONE : from.name());
    details.put("to", to == null ? NONE : to.name());
    if (after != null && after.issue() != null) {
      // when the condition began; the entry's timestamp is when the broker noticed the change
      details.put("since", after.issue().since().toString());
    }
    history.recordAt(path, HEALTH_CHANGED, message, details);
  }

  private static @Nullable String describe(final HealthIssue issue) {
    if (issue.message() != null) {
      return issue.message();
    }
    if (issue.throwable() != null) {
      return issue.throwable().toString();
    }
    final var cause = issue.cause();
    return cause == null || cause.issue() == null ? null : describe(cause.issue());
  }
}
