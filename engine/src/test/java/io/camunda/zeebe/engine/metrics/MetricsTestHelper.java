/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.metrics;

import io.prometheus.client.CollectorRegistry;
import java.util.Arrays;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;

final class MetricsTestHelper {

  /**
   * Reads the value of a metric based on its name and labels.
   *
   * <p>This uses the defaultRegistry which is inefficient, so this should only be used in testing.
   *
   * @param name the name of the metric
   * @param labels names and values for labels. This is useful for filtering the right value within
   *     the metric.
   * @return the given value or null if it doesn't exist
   */
  @SafeVarargs
  static Double readMetricValue(final String name, final Entry<String, String>... labels) {
    final List<String> labelNames =
        Arrays.stream(labels).map(Entry::getKey).collect(Collectors.toList());
    final List<String> labelValues =
        Arrays.stream(labels).map(Entry::getValue).collect(Collectors.toList());
    return CollectorRegistry.defaultRegistry.getSampleValue(
        name, labelNames.toArray(new String[] {}), labelValues.toArray(new String[] {}));
  }
}
