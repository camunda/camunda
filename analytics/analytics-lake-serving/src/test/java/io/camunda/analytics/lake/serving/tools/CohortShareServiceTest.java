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
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * {@code POST /api/tools/cohort-share} ground-truthed against {@link GaugeCohortFixtures}'s
 * hist-only {@code instance_cohorts_hist} scenario.
 */
@SpringBootTest
class CohortShareServiceTest {

  @TempDir private static Path warehouseDir;

  @Autowired private CohortShareService cohortShareService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    GaugeCohortFixtures.buildCohortHist(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldComputeShareBelowEachThreshold() {
    final CohortShareResult result =
        cohortShareService.share(
            new CohortShareQuery(
                "instance_cohorts",
                List.of(GaugeCohortFixtures.WITHIN_1H_MS, GaugeCohortFixtures.WITHIN_1D_MS),
                Map.of("process_id", GaugeCohortFixtures.ORDER_PROCESS),
                GaugeCohortFixtures.FROM,
                GaugeCohortFixtures.TO,
                60));

    assertThat(result.series()).hasSize(2);

    final ShareSeries within1h = result.series().get(0);
    assertThat(within1h.thresholdMs()).isEqualTo(GaugeCohortFixtures.WITHIN_1H_MS);
    assertThat(within1h.points()).hasSize(1);
    assertThat(within1h.points().get(0).share()).isEqualTo(GaugeCohortFixtures.SHARE_WITHIN_1H);

    final ShareSeries within1d = result.series().get(1);
    assertThat(within1d.thresholdMs()).isEqualTo(GaugeCohortFixtures.WITHIN_1D_MS);
    assertThat(within1d.points()).hasSize(1);
    assertThat(within1d.points().get(0).share()).isEqualTo(GaugeCohortFixtures.SHARE_WITHIN_1D);

    assertThat(result.sql()).isNotEmpty();
  }

  @Test
  void shouldRejectAnEmptyThresholdList() {
    assertThatThrownBy(
            () ->
                cohortShareService.share(
                    new CohortShareQuery(
                        "instance_cohorts",
                        List.of(),
                        Map.of(),
                        GaugeCohortFixtures.FROM,
                        GaugeCohortFixtures.TO,
                        60)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void shouldRejectAnUnknownFilterDim() {
    assertThatThrownBy(
            () ->
                cohortShareService.share(
                    new CohortShareQuery(
                        "instance_cohorts",
                        List.of(GaugeCohortFixtures.WITHIN_1H_MS),
                        Map.of("nope", "x"),
                        GaugeCohortFixtures.FROM,
                        GaugeCohortFixtures.TO,
                        60)))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
