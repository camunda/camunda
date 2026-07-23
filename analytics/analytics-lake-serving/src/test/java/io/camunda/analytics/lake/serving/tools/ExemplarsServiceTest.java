/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.support.CohortScenarioFixtures;
import io.camunda.analytics.lake.serving.tools.ExemplarsService.ExemplarsResult;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** {@code POST /api/tools/exemplars} returns the slowest matching instances, ordered DESC. */
@SpringBootTest
class ExemplarsServiceTest {

  @TempDir private static Path warehouseDir;

  @Autowired private ExemplarsService exemplarsService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    CohortScenarioFixtures.build(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldReturnTheSlowestMatchingInstances() {
    final ExemplarsResult result =
        exemplarsService.exemplars(
            new ExemplarsQuery(
                "instances",
                new CohortSpec.Threshold("duration_ms", ">", CohortScenarioFixtures.THRESHOLD),
                3));

    assertThat(result.rows()).hasSize(3);
    result
        .rows()
        .forEach(
            row -> {
              assertThat(row.durationMs()).isEqualTo(CohortScenarioFixtures.SLOW_DURATION);
              assertThat(row.variantHash()).isIn("V1", "V2");
            });
  }

  @Test
  void shouldDefaultKToThree() {
    final ExemplarsResult result =
        exemplarsService.exemplars(
            new ExemplarsQuery(
                "instances",
                new CohortSpec.Threshold("duration_ms", ">", CohortScenarioFixtures.THRESHOLD),
                null));

    assertThat(result.rows()).hasSize(3);
  }
}
