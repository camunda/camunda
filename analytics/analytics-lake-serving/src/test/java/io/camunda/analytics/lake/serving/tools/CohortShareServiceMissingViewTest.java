/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.support.GaugeCohortFixtures;
import io.camunda.analytics.lake.serving.support.ParquetFixtures;
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
 * A warehouse that predates {@code instance_cohorts_hist} (only an unrelated table exists): {@link
 * CohortShareService} must degrade to one empty-points {@link ShareSeries} per requested threshold,
 * never an error.
 */
@SpringBootTest
class CohortShareServiceMissingViewTest {

  @TempDir private static Path warehouseDir;

  @Autowired private CohortShareService cohortShareService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    ParquetFixtures.writeTable(warehouseDir, "objects", 1);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldReturnEmptySeriesPerThresholdWhenTheHistViewDoesNotExistYet() {
    final CohortShareResult result =
        cohortShareService.share(
            new CohortShareQuery(
                "instance_cohorts",
                List.of(GaugeCohortFixtures.WITHIN_1H_MS, GaugeCohortFixtures.WITHIN_1D_MS),
                Map.of(),
                GaugeCohortFixtures.FROM,
                GaugeCohortFixtures.TO,
                60));

    assertThat(result.series()).hasSize(2);
    assertThat(result.series().get(0).points()).isEmpty();
    assertThat(result.series().get(1).points()).isEmpty();
  }
}
