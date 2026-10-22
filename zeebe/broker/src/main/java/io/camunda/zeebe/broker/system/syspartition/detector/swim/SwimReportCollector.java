/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.syspartition.detector.swim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the latest report of every system partition broker, and when it arrived by this broker's
 * monotonic clock. Only reports that arrived recently count: a broker that stopped reporting does
 * not keep claiming it sees others, nor that others are gone.
 *
 * <p>Not thread-safe; used on the failure detector's actor.
 */
final class SwimReportCollector {
  private final Set<String> observers;
  private final Map<String, Received> latest = new HashMap<>();

  /**
   * @param observers the brokers whose reports are accepted
   */
  SwimReportCollector(final Set<String> observers) {
    this.observers = observers;
  }

  /**
   * Accepts the report unless it is not from an observer, or an older one of the same run than the
   * one already held, as reports may arrive out of order.
   *
   * @return whether the report was accepted
   */
  boolean onReport(final SwimReport report, final long arrivalNanos) {
    if (!observers.contains(report.observerId())) {
      return false;
    }
    final var held = latest.get(report.observerId());
    if (held != null
        && held.report.epoch() == report.epoch()
        && held.report.sequence() >= report.sequence()) {
      return false;
    }
    latest.put(report.observerId(), new Received(report, arrivalNanos));
    return true;
  }

  /** Returns the reports that arrived within the timeout before now. */
  List<SwimReport> fresh(final long nowNanos, final long timeoutNanos) {
    final var fresh = new ArrayList<SwimReport>();
    latest.values().stream()
        .filter(received -> nowNanos - received.arrivalNanos <= timeoutNanos)
        .forEach(received -> fresh.add(received.report));
    return fresh;
  }

  private record Received(SwimReport report, long arrivalNanos) {}
}
