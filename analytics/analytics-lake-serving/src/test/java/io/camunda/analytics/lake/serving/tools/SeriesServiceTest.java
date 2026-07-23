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

import io.camunda.analytics.lake.serving.support.StepScenarioFixtures;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** {@code POST /api/tools/series} ground-truthed against {@link StepScenarioFixtures}. */
@SpringBootTest
class SeriesServiceTest {

  @TempDir private static Path warehouseDir;

  @Autowired private SeriesService seriesService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    StepScenarioFixtures.build(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldReturnBareRowCountWhenMeasureIsNull() {
    final SeriesResult result =
        seriesService.series(
            new SeriesQuery(
                "instance_starts",
                null,
                null,
                Map.of("process_id", StepScenarioFixtures.ORDER_PROCESS),
                StepScenarioFixtures.FROM,
                StepScenarioFixtures.TO,
                1));

    assertThat(result.points()).hasSize(40);
    assertThat(result.points().get(0).value()).isEqualTo(10.0);
    assertThat(result.sql()).isNotEmpty();
  }

  @Test
  void shouldTreatCntMeasureAsTheBareRowCount() {
    // given: the client names the bare row count as measure "cnt" (the registry's spelling)

    // when
    final SeriesResult result =
        seriesService.series(
            new SeriesQuery(
                "instance_starts",
                "cnt",
                null,
                Map.of("process_id", StepScenarioFixtures.ORDER_PROCESS),
                StepScenarioFixtures.FROM,
                StepScenarioFixtures.TO,
                1));

    // then: identical to the measure == null series
    assertThat(result.points()).hasSize(40);
    assertThat(result.points().get(0).value()).isEqualTo(10.0);
  }

  @Test
  void shouldReturnWeightedAverageForAMeasureWithoutAQuantile() {
    final SeriesResult result =
        seriesService.series(
            new SeriesQuery(
                "instances",
                "duration_ms",
                null,
                Map.of("process_id", StepScenarioFixtures.ORDER_PROCESS),
                StepScenarioFixtures.FROM,
                StepScenarioFixtures.TO,
                1));

    assertThat(result.points()).hasSize(40);
    assertThat(result.points().get(0).value()).isEqualTo(StepScenarioFixtures.BEFORE_AVG);
    assertThat(result.points().get(39).value()).isEqualTo(StepScenarioFixtures.AFTER_AVG);
  }

  @Test
  void shouldWalkTheHistogramForAQuantile() {
    final SeriesResult result =
        seriesService.series(
            new SeriesQuery(
                "instances",
                "duration_ms",
                0.5,
                Map.of("process_id", StepScenarioFixtures.ORDER_PROCESS),
                StepScenarioFixtures.FROM,
                StepScenarioFixtures.TO,
                1));

    assertThat(result.points()).hasSize(40);
    assertThat(result.points().get(0).value()).isEqualTo(StepScenarioFixtures.BEFORE_AVG);
    assertThat(result.points().get(39).value()).isEqualTo(StepScenarioFixtures.AFTER_AVG);
  }

  @Test
  void shouldReAggregateUpToACoarserGrain() {
    final SeriesResult result =
        seriesService.series(
            new SeriesQuery(
                "instances",
                "duration_ms",
                null,
                Map.of("process_id", StepScenarioFixtures.ORDER_PROCESS),
                StepScenarioFixtures.FROM,
                StepScenarioFixtures.TO,
                20));

    // 40 one-minute windows re-bucketed into 20-minute buckets -> exactly 2 points, one per regime
    assertThat(result.points()).hasSize(2);
    assertThat(result.points().get(0).value()).isEqualTo(StepScenarioFixtures.BEFORE_AVG);
    assertThat(result.points().get(1).value()).isEqualTo(StepScenarioFixtures.AFTER_AVG);
  }

  @Test
  void shouldRejectAnUnknownEntity() {
    assertThatThrownBy(
            () ->
                seriesService.series(
                    new SeriesQuery(
                        "nope",
                        null,
                        null,
                        Map.of(),
                        StepScenarioFixtures.FROM,
                        StepScenarioFixtures.TO,
                        1)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
