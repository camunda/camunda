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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A warehouse that predates {@code open_instances_gauge} (only an unrelated table exists): {@link
 * GaugeSeriesService} must degrade to an empty point list, never an error -- same open/closed rule
 * as every other read here.
 */
@SpringBootTest
class GaugeSeriesServiceMissingViewTest {

  @TempDir private static Path warehouseDir;

  @Autowired private GaugeSeriesService gaugeSeriesService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    ParquetFixtures.writeTable(warehouseDir, "objects", 1);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldReturnEmptyPointsWhenTheGaugeViewDoesNotExistYet() {
    final SeriesResult result =
        gaugeSeriesService.series(
            new GaugeSeriesQuery(
                GaugeCohortFixtures.ORDER_PROCESS,
                GaugeCohortFixtures.FROM,
                GaugeCohortFixtures.TO,
                60));

    assertThat(result.points()).isEmpty();
  }
}
