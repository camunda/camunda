/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.qa.util.actuator.PrometheusActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.prometheus.client.CollectorRegistry;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import org.hawkular.agent.prometheus.text.TextPrometheusMetricsProcessor;
import org.hawkular.agent.prometheus.types.MetricFamily;
import org.hawkular.agent.prometheus.walkers.CollectorPrometheusMetricsWalker;
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
    // given
    final var actuator = PrometheusActuator.of(zeebe);

    // when
    final var metrics = collectMetrics(actuator.metrics().body().asInputStream());
    final var addedCounter =
        metrics.stream()
            .filter(
                metric -> metric.getName().contains(ExporterMetricsTestExporter.REGISTERED_COUNTER))
            .findFirst();

    // then
    assertThat(addedCounter).isPresent();
  }

  private List<MetricFamily> collectMetrics(final InputStream metricsPlainText) {
    final var walker = new CollectorPrometheusMetricsWalker();
    final var processor = new TextPrometheusMetricsProcessor(metricsPlainText, walker);
    processor.walk();
    return walker.getAllMetricFamilies();
  }
}
