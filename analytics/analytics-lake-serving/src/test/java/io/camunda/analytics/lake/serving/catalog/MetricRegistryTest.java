/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.lake.serving.support.ParquetFixtures;
import io.camunda.analytics.lake.serving.support.StepScenarioFixtures;
import java.nio.file.Path;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Exercises {@link MetricRegistry}'s catalog derivation against real Parquet-backed views: dims vs.
 * measures vs. counters, {@code hasCnt}, and {@code hasHist} cross-referenced from the {@code
 * _hist} view's own distinct {@code measure} column.
 */
@SpringBootTest
class MetricRegistryTest {

  @TempDir private static Path warehouseDir;

  @Autowired private MetricRegistry metricRegistry;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    StepScenarioFixtures.build(warehouseDir);
    // A named-counter entity (variable_profiles-shaped): `type_number_cnt` has no `type_number_sum`
    // sibling, so it must be classified as a counter, not folded into a measure.
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "variable_profiles_metrics",
        "SELECT CAST(TIMESTAMP '2024-01-01 00:00:00' AS TIMESTAMPTZ) AS window_start, "
            + "'orderProcess' AS process_id, 'amount' AS var_name, 3 AS cnt, "
            + "7 AS type_number_cnt, 2 AS type_string_cnt, "
            + "7 AS value_cnt, 700.0 AS value_sum, 50.0 AS value_min, 150.0 AS value_max");
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldDeriveDimsAndMeasureForInstances() {
    final EntityCatalog instances = metricRegistry.require("instances");

    assertThat(instances.dimNames()).containsExactly("process_id");
    assertThat(instances.hasCnt()).isFalse();
    assertThat(instances.counters()).isEmpty();
    assertThat(instances.measures()).hasSize(1);

    final MeasureCatalog durationMs = instances.measures().get(0);
    assertThat(durationMs.name()).isEqualTo("duration_ms");
    assertThat(durationMs.hasSum()).isTrue();
    assertThat(durationMs.hasMin()).isTrue();
    assertThat(durationMs.hasMax()).isTrue();
    assertThat(durationMs.hasHist()).isTrue();
    assertThat(instances.hasHistTable()).isTrue();
  }

  @Test
  void shouldReportNoHistForAMeasureWithNoHistView() {
    // given: variable_profiles' `value` measure ships metrics only in this fixture -- no _hist
    // twin (activities gained one when the fixture grew production-shaped histograms)
    final EntityCatalog profiles = metricRegistry.require("variable_profiles");

    // when / then
    assertThat(profiles.hasHistTable()).isFalse();
    assertThat(profiles.measure("value")).isPresent();
    assertThat(profiles.measure("value").orElseThrow().hasHist()).isFalse();
  }

  @Test
  void shouldClassifyABareCntSuffixWithNoSumSiblingAsACounterNotAMeasure() {
    final EntityCatalog profiles = metricRegistry.require("variable_profiles");

    assertThat(profiles.hasCnt()).isTrue();
    assertThat(profiles.counters()).containsExactlyInAnyOrder("type_number", "type_string");
    final Optional<MeasureCatalog> value = profiles.measure("value");
    assertThat(value).isPresent();
    assertThat(value.get().hasSum()).isTrue();
  }

  @Test
  void shouldOverlayDimKindsFromConfig() {
    assertThat(metricRegistry.dimKinds()).containsEntry("process_id", "PROCESS");
  }

  @Test
  void shouldThrowForAnUnknownEntity() {
    assertThatThrownBy(() -> metricRegistry.require("does_not_exist"))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
