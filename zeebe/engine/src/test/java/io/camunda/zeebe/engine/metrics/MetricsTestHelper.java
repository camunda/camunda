/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.metrics;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import java.util.HashSet;

final class MetricsTestHelper {

  /**
   * Reads the value of a metric based on its name and labels.
   *
   * <p>This uses the defaultRegistry which is inefficient, so this should only be used in testing.
   *
   * @param name the name of the metric
   * @param tags names and values for labels or tags. This is useful for filtering the right value
   *     within the metric.
   * @return the given value or null if it doesn't exist
   */
  //  @SafeVarargs
  static Double readMetricValue(final String name, final Iterable<Tag> tags) {
    final var metric = Metrics.globalRegistry.find(name);

    if (metric.meter() != null) {
      final HashSet<Tag> existingTags = new HashSet<>(metric.meter().getId().getTags());
      tags.forEach(existingTags::remove);

      if (existingTags.isEmpty() && metric.tags(tags).counter() != null) {
        return metric.tags(tags).counter().count();
      }
    }

    return null;
  }
}
