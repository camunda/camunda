/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.planner;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.planner.InvestigateService.InvestigateResult;
import io.camunda.analytics.lake.serving.support.StepScenarioFixtures;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * With {@code lake.serving.max-explain-scan-rows} pinned far below the {@code instances_metrics}
 * partials' own estimated row count (600 -- 40 rows x 10 cnt + 40 rows x 5 cnt, summing {@code
 * duration_ms_cnt} over the whole window), rung 3 must report {@code SCAN_DEFERRED} instead of
 * actually running {@code cohort-compare}.
 */
@SpringBootTest(properties = "lake.serving.max-explain-scan-rows=1")
class InvestigateServiceScanDeferredTest {

  @TempDir private static Path warehouseDir;

  @Autowired private InvestigateService investigateService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    StepScenarioFixtures.build(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldDeferTheCohortCompareScanAndReportTheEstimate() {
    final InvestigateResult result =
        investigateService.investigate(
            new InvestigateQuery(
                "instances",
                "duration_ms",
                null,
                null,
                StepScenarioFixtures.FROM,
                StepScenarioFixtures.TO));

    final List<Finding> deferred =
        result.findings().stream().filter(f -> f.kind().equals("SCAN_DEFERRED")).toList();
    assertThat(deferred).hasSize(1);
    assertThat(deferred.get(0).claim()).containsEntry("estimatedRows", 600L);
    assertThat(deferred.get(0).tool()).isEqualTo("cohort-compare");
    assertThat(deferred.get(0).toolParams()).isNotNull();
    // SCAN_DEFERRED always trails the ranked list.
    assertThat(result.findings().get(result.findings().size() - 1).kind())
        .isEqualTo("SCAN_DEFERRED");
    assertThat(result.findings().stream().filter(f -> f.kind().equals("COHORT_ATTRIBUTE")))
        .isEmpty();
  }
}
