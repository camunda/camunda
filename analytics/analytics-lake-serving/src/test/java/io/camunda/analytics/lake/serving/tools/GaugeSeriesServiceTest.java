/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.lake.serving.support.GaugeCohortFixtures;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** {@code POST /api/tools/gauge-series} ground-truthed against {@link GaugeCohortFixtures}. */
@SpringBootTest
class GaugeSeriesServiceTest {

  @TempDir private static Path warehouseDir;

  @Autowired private GaugeSeriesService gaugeSeriesService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    GaugeCohortFixtures.buildGauge(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldAverageOneProcessSamplesPerBucket() {
    final SeriesResult result =
        gaugeSeriesService.series(
            new GaugeSeriesQuery(
                GaugeCohortFixtures.ORDER_PROCESS,
                GaugeCohortFixtures.FROM,
                GaugeCohortFixtures.TO,
                60));

    assertThat(result.points()).hasSize(1);
    assertThat(result.points().get(0).value()).isEqualTo(GaugeCohortFixtures.ORDER_PROCESS_AVG);
    assertThat(result.sql()).isNotEmpty();
  }

  @Test
  void shouldAverageTheOtherProcessSamplesPerBucket() {
    final SeriesResult result =
        gaugeSeriesService.series(
            new GaugeSeriesQuery(
                GaugeCohortFixtures.REFUND_PROCESS,
                GaugeCohortFixtures.FROM,
                GaugeCohortFixtures.TO,
                60));

    assertThat(result.points()).hasSize(1);
    assertThat(result.points().get(0).value()).isEqualTo(GaugeCohortFixtures.REFUND_PROCESS_AVG);
  }

  @Test
  void shouldSumAcrossProcessesPerInstantThenAverageWhenNoProcessIdIsGiven() {
    final SeriesResult result =
        gaugeSeriesService.series(
            new GaugeSeriesQuery(null, GaugeCohortFixtures.FROM, GaugeCohortFixtures.TO, 60));

    assertThat(result.points()).hasSize(1);
    assertThat(result.points().get(0).value()).isEqualTo(GaugeCohortFixtures.UNFILTERED_AVG);
  }

  @Test
  void shouldRejectANonPositiveGrain() {
    assertThatThrownBy(
            () ->
                gaugeSeriesService.series(
                    new GaugeSeriesQuery(
                        null, GaugeCohortFixtures.FROM, GaugeCohortFixtures.TO, 0)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
