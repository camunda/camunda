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
import io.prometheus.client.CollectorRegistry;
import io.prometheus.metrics.exporter.common.PrometheusHttpResponse;
import java.io.IOException;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class ExporterMetricsIT {

  @TestZeebe
  private final TestStandaloneBroker zeebe =
      new TestStandaloneBroker()
          .withExporter("foo", cfg -> cfg.setClassName(ExporterMetricsTestExporter.class.getName()))
          .withBean(
              "collectorRegistry", CollectorRegistry.defaultRegistry, CollectorRegistry.class);

  @Test
  public void shouldAddMeterToExporterMetrics() throws IOException {
    final var actuator = PrometheusActuator.of(zeebe);
    final PrometheusHttpResponse response = actuator.metrics();
  }
}
