/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.analytics.lake.serving.support.StepScenarioFixtures;
import io.camunda.analytics.lake.serving.tools.ScreenService.ScreenResult;
import io.camunda.analytics.lake.serving.tools.ScreenService.ScreenRow;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code POST /api/tools/screen}: {@code activities}'s own step (planted at the same window as
 * {@code instances}'s) must surface as the top-correlated auto candidate at shift 0.
 */
@SpringBootTest
class ScreenServiceTest {

  @TempDir private static Path warehouseDir;

  @Autowired private ScreenService screenService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    StepScenarioFixtures.build(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldFindTheCorrelatedActivitiesCandidateAtShiftZero() {
    final SeriesQuery target =
        new SeriesQuery(
            "instances",
            "duration_ms",
            null,
            Map.of("process_id", StepScenarioFixtures.ORDER_PROCESS),
            StepScenarioFixtures.FROM,
            StepScenarioFixtures.TO,
            1);

    final ScreenResult result =
        screenService.screen(
            target, new TimeRange(StepScenarioFixtures.FROM, StepScenarioFixtures.TO), null);

    assertThat(result.rows()).isNotEmpty();
    final ScreenRow top = result.rows().get(0);
    assertThat(top.series().entity()).isEqualTo("activities");
    assertThat(top.shiftSlots()).isZero();
    assertThat(top.correlation()).isCloseTo(1.0, within(1e-6));
    assertThat(top.movedAt()).isEqualTo(StepScenarioFixtures.STEP_AT);
  }

  @Test
  void shouldUseExplicitCandidatesWhenGiven() {
    final SeriesQuery target =
        new SeriesQuery(
            "instances",
            "duration_ms",
            null,
            Map.of("process_id", StepScenarioFixtures.ORDER_PROCESS),
            StepScenarioFixtures.FROM,
            StepScenarioFixtures.TO,
            1);
    final SeriesQuery explicitCandidate =
        new SeriesQuery(
            "activities",
            "duration_ms",
            null,
            Map.of("process_id", StepScenarioFixtures.ORDER_PROCESS),
            StepScenarioFixtures.FROM,
            StepScenarioFixtures.TO,
            1);

    final ScreenResult result =
        screenService.screen(
            target,
            new TimeRange(StepScenarioFixtures.FROM, StepScenarioFixtures.TO),
            List.of(explicitCandidate));

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).correlation()).isCloseTo(1.0, within(1e-6));
  }
}
