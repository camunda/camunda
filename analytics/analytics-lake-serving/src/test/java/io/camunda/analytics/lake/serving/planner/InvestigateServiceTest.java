/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.planner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.camunda.analytics.lake.serving.planner.InvestigateService.InvestigateResult;
import io.camunda.analytics.lake.serving.support.ParquetFixtures;
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
 * {@code POST /api/investigate} end to end: the planted step ({@link StepScenarioFixtures}) plus a
 * raw {@code instances} table whose {@code ended_at}/{@code variant_hash} correlate with the same
 * step, so every rung has real ground truth to find.
 *
 * <table>
 *   <caption>raw instances ground truth (for the rung-3 {@code WindowSplit} at the changepoint)</caption>
 *   <tr><td>ended before the step (00:05)</td><td>35 x V1, 5 x V2</td></tr>
 *   <tr><td>ended at/after the step (00:25)</td><td>5 x V1, 35 x V2</td></tr>
 * </table>
 *
 * <p>-&gt; V2 is the slow-cohort-dominant variant (slowN=35, survives the default supportFloor=20;
 * V1's slowN=5 does not).
 */
@SpringBootTest
class InvestigateServiceTest {

  @TempDir private static Path warehouseDir;

  @Autowired private InvestigateService investigateService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    StepScenarioFixtures.build(warehouseDir);
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "instances",
        "SELECT "
            + "i AS key, 1 AS process_definition_key, '"
            + StepScenarioFixtures.ORDER_PROCESS
            + "' AS process_id, 1 AS version, 'default' AS tenant_id, 'COMPLETED' AS state, "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ) AS started_at, "
            + "CAST((CASE WHEN i < 35 THEN TIMESTAMP '2024-01-01 00:05:00' ELSE TIMESTAMP '2024-01-01 00:25:00' END) AS TIMESTAMPTZ) AS ended_at, "
            + "1000 AS duration_ms, CAST('{}' AS BLOB) AS vars_json, "
            + "(CASE WHEN i < 35 THEN 'V1' ELSE 'V2' END) AS variant_hash "
            + "FROM range(40) t(i) "
            + "UNION ALL SELECT "
            + "1000 + i, 1, '"
            + StepScenarioFixtures.ORDER_PROCESS
            + "', 1, 'default', 'COMPLETED', "
            + "CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ), "
            + "CAST((CASE WHEN i < 5 THEN TIMESTAMP '2024-01-01 00:05:00' ELSE TIMESTAMP '2024-01-01 00:25:00' END) AS TIMESTAMPTZ), "
            + "1000, CAST('{}' AS BLOB), (CASE WHEN i < 5 THEN 'V1' ELSE 'V2' END) "
            + "FROM range(40) t(i)");
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldLeadWithTheChangepointFinding() {
    final InvestigateResult result =
        investigateService.investigate(
            new InvestigateQuery(
                "instances",
                "duration_ms",
                null,
                null,
                StepScenarioFixtures.FROM,
                StepScenarioFixtures.TO));

    assertThat(result.findings()).isNotEmpty();
    final Finding first = result.findings().get(0);
    assertThat(first.kind()).isEqualTo("CHANGEPOINT");
    assertThat(first.rung()).isZero();
    assertThat(first.claim()).containsEntry("shape", "STEP");
    assertThat(first.claim()).containsEntry("before", 1166.6666666666667);
    assertThat(first.claim()).containsEntry("after", 2500.0);
  }

  @Test
  void shouldFindTheDominantDimensionDriver() {
    final InvestigateResult result =
        investigateService.investigate(
            new InvestigateQuery(
                "instances",
                "duration_ms",
                null,
                null,
                StepScenarioFixtures.FROM,
                StepScenarioFixtures.TO));

    final List<Finding> driverFindings =
        result.findings().stream().filter(f -> f.kind().equals("DIMENSION_DRIVER")).toList();
    assertThat(driverFindings).hasSize(1);
    assertThat(driverFindings.get(0).claim()).containsEntry("dim", "process_id");
    assertThat(driverFindings.get(0).claim())
        .containsEntry("value", StepScenarioFixtures.ORDER_PROCESS);
    assertThat((Double) driverFindings.get(0).claim().get("contributionShare"))
        .isCloseTo(1.0, within(1e-6));
  }

  @Test
  void shouldFindTheSurvivingCohortAttributeFinding() {
    final InvestigateResult result =
        investigateService.investigate(
            new InvestigateQuery(
                "instances",
                "duration_ms",
                null,
                null,
                StepScenarioFixtures.FROM,
                StepScenarioFixtures.TO));

    final List<Finding> cohortFindings =
        result.findings().stream().filter(f -> f.kind().equals("COHORT_ATTRIBUTE")).toList();
    assertThat(cohortFindings).hasSize(1);
    assertThat(cohortFindings.get(0).claim()).containsEntry("attribute", "variant_hash");
    assertThat(cohortFindings.get(0).claim()).containsEntry("bucket", "V2");
    assertThat(cohortFindings.get(0).exemplars()).isNotNull().isNotEmpty();
  }

  @Test
  void shouldMakeEveryFindingReRunnableViaItsOwnToolParams() {
    final InvestigateResult result =
        investigateService.investigate(
            new InvestigateQuery(
                "instances",
                "duration_ms",
                null,
                null,
                StepScenarioFixtures.FROM,
                StepScenarioFixtures.TO));

    assertThat(result.findings()).allSatisfy(f -> assertThat(f.tool()).isNotBlank());
    assertThat(result.findings()).allSatisfy(f -> assertThat(f.toolParams()).isNotNull());
    assertThat(result.findings()).allSatisfy(f -> assertThat(f.sql()).isNotNull());
  }
}
