/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.tools;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.analytics.lake.serving.support.ParquetFixtures;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Regression test for the cohort-compare single-scan row cap ({@code SCAN_ROW_CAP}, 200,000):
 * plants 200,001 {@code instances} rows, each with its own distinct {@code variant_hash} (so {@code
 * GROUP BY cohort_flag, variant_hash} produces exactly 200,001 groups — one more than the cap), and
 * asserts the service refuses to marginalize a truncated scan into (silently wrong)
 * slowShare/fastShare/lift numbers, throwing instead of returning skewed rows.
 */
@SpringBootTest
class CohortCompareTruncationTest {

  @TempDir private static Path warehouseDir;

  @Autowired private CohortCompareService cohortCompareService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    ParquetFixtures.writeTableFromQuery(
        warehouseDir,
        "instances",
        "SELECT "
            + "i AS key, 1 AS process_definition_key, 'orderProcess' AS process_id, 1 AS version, "
            + "'default' AS tenant_id, 'COMPLETED' AS state, "
            + "CAST(TIMESTAMP '2024-01-01 00:10:00' AS TIMESTAMPTZ) AS started_at, "
            + "CAST(TIMESTAMP '2024-01-01 00:20:00' AS TIMESTAMPTZ) AS ended_at, "
            + "(CASE WHEN i % 2 = 0 THEN 6000 ELSE 100 END) AS duration_ms, "
            + "CAST('{}' AS BLOB) AS vars_json, "
            + "('V' || i) AS variant_hash "
            + "FROM range(200001) t(i)");
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldFailLoudlyRatherThanMarginalizeATruncatedScan() {
    assertThatThrownBy(
            () ->
                cohortCompareService.compare(
                    new CohortCompareQuery(
                        "instances",
                        new CohortSpec.Threshold("duration_ms", ">", 2000.0),
                        null,
                        "2024-01-01T00:00:00Z",
                        "2024-01-01T01:00:00Z",
                        List.of("variant_hash"),
                        1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("200000")
        .hasMessageContaining("narrow");
  }
}
