/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.broker.system.monitoring.detector;

import io.micrometer.core.instrument.Meter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/** Turns counters into how much they grew since the previous sample, per health tree child. */
final class CounterWindow {

  private final Map<Meter.Id, Double> previous = new HashMap<>();

  /**
   * @param counts the current count of each counter
   * @param child the health tree child of each counter; counters of the same child are added up
   * @return how much each child's counters grew since the previous call; a counter seen for the
   *     first time, or one that went down, contributes nothing to this sample
   */
  Map<String, Double> sample(
      final Map<Meter.Id, Double> counts, final Function<Meter.Id, String> child) {
    final var increases = new HashMap<String, Double>();
    counts.forEach(
        (id, count) -> {
          final var before = previous.get(id);
          final var increase = before == null || count < before ? 0 : count - before;
          increases.merge(child.apply(id), increase, Double::sum);
        });
    previous.clear();
    previous.putAll(counts);
    return increases;
  }
}
