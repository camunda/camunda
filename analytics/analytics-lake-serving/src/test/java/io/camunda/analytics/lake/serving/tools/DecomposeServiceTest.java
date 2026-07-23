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
import static org.assertj.core.api.Assertions.within;

import io.camunda.analytics.lake.serving.support.StepScenarioFixtures;
import io.camunda.analytics.lake.serving.tools.DecomposeService.DecomposeResult;
import io.camunda.analytics.lake.serving.tools.DecomposeService.DecomposeRow;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code POST /api/tools/decompose} attributes the step to {@code orderProcess} (which actually
 * moved) over {@code refundProcess} (flat the whole time) -- see {@link StepScenarioFixtures}'s
 * ground truth for the exact numbers this test's assertions are derived from.
 */
@SpringBootTest
class DecomposeServiceTest {

  @TempDir private static Path warehouseDir;

  @Autowired private DecomposeService decomposeService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    StepScenarioFixtures.build(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldAttributeTheStepToTheDimValueThatActuallyMoved() {
    final DecomposeResult result =
        decomposeService.decompose(
            new DecomposeQuery(
                "instances",
                "duration_ms",
                null,
                null,
                new TimeRange(StepScenarioFixtures.STEP_AT, StepScenarioFixtures.TO),
                new TimeRange(StepScenarioFixtures.FROM, StepScenarioFixtures.STEP_AT),
                "process_id"));

    assertThat(result.rows()).hasSize(2);
    final DecomposeRow top = result.rows().get(0);
    assertThat(top.value()).isEqualTo(StepScenarioFixtures.ORDER_PROCESS);
    assertThat(top.current()).isEqualTo(3000.0);
    assertThat(top.baseline()).isEqualTo(1000.0);
    assertThat(top.delta()).isEqualTo(2000.0);
    assertThat(top.contributionShare()).isCloseTo(1.0, within(1e-9));

    final DecomposeRow flat = result.rows().get(1);
    assertThat(flat.value()).isEqualTo(StepScenarioFixtures.REFUND_PROCESS);
    assertThat(flat.delta()).isEqualTo(0.0);
    assertThat(flat.contributionShare()).isCloseTo(0.0, within(1e-9));
  }

  @Test
  void shouldRejectAnUnknownDim() {
    assertThatThrownBy(
            () ->
                decomposeService.decompose(
                    new DecomposeQuery(
                        "instances",
                        "duration_ms",
                        null,
                        null,
                        new TimeRange(StepScenarioFixtures.STEP_AT, StepScenarioFixtures.TO),
                        new TimeRange(StepScenarioFixtures.FROM, StepScenarioFixtures.STEP_AT),
                        "not_a_dim")))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
