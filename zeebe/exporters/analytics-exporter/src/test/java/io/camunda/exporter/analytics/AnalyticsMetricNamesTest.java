/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.analytics;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/**
 * Pins the wire value of every analytics metric name and unit.
 *
 * <p>Metric names and units are a published contract, defined in the HDP product-telemetry data
 * contract: the analytics backend keys on the metric name and reads the unit. The rest of the suite
 * asserts against the {@link AnalyticsAttributes.Metric} constants rather than their values, which
 * means a rename or a unit change would otherwise pass unnoticed.
 *
 * <p>If this test fails you have changed or added a published metric name or unit. That is allowed,
 * but it is a contract change: update the expectations here, the README metric table, and the data
 * contract before merging.
 */
final class AnalyticsMetricNamesTest {

  private static final Map<String, String> EXPECTED_METRIC_CONSTANTS =
      Map.ofEntries(
          Map.entry("DECISION_INSTANCE_EVALUATED", "camunda.decision.instance.evaluated"),
          Map.entry("DECISION_INSTANCE_EVALUATED_UNIT", "{decision_instance}"),
          Map.entry("EXPORT_WINDOW", "camunda.metric.export_window"),
          Map.entry("EXPORT_WINDOW_UNIT", "{record}"));

  @Test
  void shouldNotChangePublishedMetricNamesOrUnits() throws IllegalAccessException {
    // when
    final Map<String, String> declared = declaredMetricConstants();

    // then
    assertThat(declared).containsExactlyInAnyOrderEntriesOf(EXPECTED_METRIC_CONSTANTS);
  }

  private static Map<String, String> declaredMetricConstants() throws IllegalAccessException {
    final Map<String, String> constants = new TreeMap<>();
    for (final Field field : AnalyticsAttributes.Metric.class.getDeclaredFields()) {
      final int modifiers = field.getModifiers();
      if (field.getType() == String.class
          && Modifier.isPublic(modifiers)
          && Modifier.isStatic(modifiers)) {
        constants.put(field.getName(), (String) field.get(null));
      }
    }
    return constants;
  }
}
