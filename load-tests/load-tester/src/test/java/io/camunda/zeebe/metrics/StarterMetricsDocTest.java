/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.zeebe.metrics.StarterMetricsDoc.StarterMetricKeyNames;
import io.micrometer.core.instrument.Meter.Type;
import org.junit.jupiter.api.Test;

class StarterMetricsDocTest {

  @Test
  void shouldDefineClientInfoGauge() {
    // given
    final var metric = StarterMetricsDoc.CLIENT_INFO;

    // when / then
    assertThat(metric.getName()).isEqualTo("client.info");
    assertThat(metric.getType()).isEqualTo(Type.GAUGE);
    assertThat(metric.getDescription()).isEqualTo("The information about the client.");
    assertThat(metric.getKeyNames())
        .containsExactly(
            StarterMetricKeyNames.NAME,
            StarterMetricKeyNames.PROCESS_ID,
            StarterMetricKeyNames.NB_THREADS);
  }
}
