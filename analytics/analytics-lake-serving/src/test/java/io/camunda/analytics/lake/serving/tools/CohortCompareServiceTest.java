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

import io.camunda.analytics.lake.serving.support.CohortScenarioFixtures;
import io.camunda.analytics.lake.serving.tools.CohortCompareService.CohortCompareResult;
import io.camunda.analytics.lake.serving.tools.CohortCompareService.CohortCompareRow;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** {@code POST /api/tools/cohort-compare} against {@link CohortScenarioFixtures}' planted lift. */
@SpringBootTest
class CohortCompareServiceTest {

  @TempDir private static Path warehouseDir;

  @Autowired private CohortCompareService cohortCompareService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    CohortScenarioFixtures.build(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldFindThePlantedLiftWithALoweredSupportFloor() {
    final CohortCompareResult result =
        cohortCompareService.compare(
            new CohortCompareQuery(
                "instances",
                new CohortSpec.Threshold("duration_ms", ">", CohortScenarioFixtures.THRESHOLD),
                null,
                CohortScenarioFixtures.FROM,
                CohortScenarioFixtures.TO,
                List.of("variant_hash"),
                5));

    final Optional<CohortCompareRow> v1 = findBucket(result.rows(), "V1");
    final Optional<CohortCompareRow> v2 = findBucket(result.rows(), "V2");
    assertThat(v1).isPresent();
    assertThat(v2).isPresent();

    assertThat(v1.get().slowN()).isEqualTo(5);
    assertThat(v1.get().fastN()).isEqualTo(35);
    assertThat(v1.get().slowShare()).isCloseTo(0.125, within(1e-9));
    assertThat(v1.get().lift()).isCloseTo(1.0 / 7.0, within(1e-9));

    assertThat(v2.get().slowN()).isEqualTo(35);
    assertThat(v2.get().fastN()).isEqualTo(5);
    assertThat(v2.get().slowShare()).isCloseTo(0.875, within(1e-9));
    assertThat(v2.get().lift()).isCloseTo(7.0, within(1e-9));
  }

  @Test
  void shouldExcludeBucketsBelowTheDefaultSupportFloor() {
    final CohortCompareResult result =
        cohortCompareService.compare(
            new CohortCompareQuery(
                "instances",
                new CohortSpec.Threshold("duration_ms", ">", CohortScenarioFixtures.THRESHOLD),
                null,
                CohortScenarioFixtures.FROM,
                CohortScenarioFixtures.TO,
                List.of("variant_hash"),
                null));

    // default supportFloor is 20 -- V1's slowN (5) is excluded, only V2 (35) survives
    assertThat(findBucket(result.rows(), "V1")).isEmpty();
    assertThat(findBucket(result.rows(), "V2")).isPresent();
  }

  @Test
  void shouldDeriveAutoAttributesIncludingVariantHash() {
    final CohortCompareResult result =
        cohortCompareService.compare(
            new CohortCompareQuery(
                "instances",
                new CohortSpec.Threshold("duration_ms", ">", CohortScenarioFixtures.THRESHOLD),
                null,
                CohortScenarioFixtures.FROM,
                CohortScenarioFixtures.TO,
                null,
                5));

    assertThat(result.rows())
        .anySatisfy(row -> assertThat(row.attribute()).isEqualTo("variant_hash"));
  }

  @Test
  void shouldRejectAnUnsupportedEntity() {
    assertThatThrownBy(
            () ->
                cohortCompareService.compare(
                    new CohortCompareQuery(
                        "activities",
                        new CohortSpec.Threshold("duration_ms", ">", 1.0),
                        null,
                        CohortScenarioFixtures.FROM,
                        CohortScenarioFixtures.TO,
                        List.of(),
                        null)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  private static Optional<CohortCompareRow> findBucket(
      final List<CohortCompareRow> rows, final String bucket) {
    return rows.stream()
        .filter(r -> r.attribute().equals("variant_hash") && r.bucket().equals(bucket))
        .findFirst();
  }
}
