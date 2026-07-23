/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.support.StepScenarioFixtures;
import io.camunda.analytics.lake.serving.tools.ChangepointService.ChangepointResult;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** {@code POST /api/tools/changepoint} finds the planted step in {@link StepScenarioFixtures}. */
@SpringBootTest
class ChangepointServiceTest {

  @TempDir private static Path warehouseDir;

  @Autowired private ChangepointService changepointService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    StepScenarioFixtures.build(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldFindThePlantedStepChange() {
    final ChangepointResult result =
        changepointService.changepoint(
            new SeriesQuery(
                "instances",
                "duration_ms",
                null,
                Map.of("process_id", StepScenarioFixtures.ORDER_PROCESS),
                StepScenarioFixtures.FROM,
                StepScenarioFixtures.TO,
                1));

    assertThat(result.shape()).isEqualTo("STEP");
    assertThat(result.at()).isEqualTo(StepScenarioFixtures.STEP_AT);
    assertThat(result.before()).isEqualTo(StepScenarioFixtures.BEFORE_AVG);
    assertThat(result.after()).isEqualTo(StepScenarioFixtures.AFTER_AVG);
    assertThat(result.confidence()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
  }

  @Test
  void shouldReportNoneForTheFlatRefundProcess() {
    final ChangepointResult result =
        changepointService.changepoint(
            new SeriesQuery(
                "instances",
                "duration_ms",
                null,
                Map.of("process_id", StepScenarioFixtures.REFUND_PROCESS),
                StepScenarioFixtures.FROM,
                StepScenarioFixtures.TO,
                1));

    assertThat(result.shape()).isEqualTo("NONE");
    assertThat(result.at()).isNull();
  }
}
