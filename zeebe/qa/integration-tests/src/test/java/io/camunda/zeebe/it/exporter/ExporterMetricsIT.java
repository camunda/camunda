/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.exporter;

import io.camunda.zeebe.qa.util.actuator.PrometheusActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class ExporterMetricsIT {

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker()
          .withExporter(
              "foo", cfg -> cfg.setClassName(ExporterMetricsTestExporter.class.getName()));

  @Test
  void shouldAddMeterToExporterMetrics() throws IOException {
    // given
    final var actuator = PrometheusActuator.of(zeebe);

    // when
    final var metricsPlainText = actuator.metrics();

    // then
    Assertions.assertThat(metricsPlainText.lines())
        .filteredOn(line -> line.startsWith(ExporterMetricsTestExporter.REGISTERED_COUNTER))
        .hasSize(1);
  }
}
