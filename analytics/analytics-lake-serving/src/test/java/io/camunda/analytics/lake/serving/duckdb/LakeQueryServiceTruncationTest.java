/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.duckdb;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.duckdb.LakeQueryService.QueryResult;
import io.camunda.analytics.lake.serving.support.ParquetFixtures;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Directly exercises {@link LakeQueryService}'s row-cap-override path's overflow detection: {@code
 * truncated} must be {@code true} exactly when the underlying result actually had more rows than
 * the requested cap, never a false negative (which would let a caller like {@code
 * CohortCompareService} silently marginalize an incomplete scan) or false positive (which would
 * make an exact-fit result look truncated).
 */
@SpringBootTest
class LakeQueryServiceTruncationTest {

  @TempDir private static Path warehouseDir;

  @Autowired private LakeQueryService queryService;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    ParquetFixtures.writeTable(warehouseDir, "ten_rows", 10);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldReportTruncatedWhenMoreRowsExistThanTheCap() throws Exception {
    final QueryResult result = queryService.execute("SELECT * FROM ten_rows", 3);

    assertThat(result.rows()).hasSize(3);
    assertThat(result.truncated()).isTrue();
  }

  @Test
  void shouldNotReportTruncatedWhenTheCapExactlyFitsTheResult() throws Exception {
    final QueryResult result = queryService.execute("SELECT * FROM ten_rows", 10);

    assertThat(result.rows()).hasSize(10);
    assertThat(result.truncated()).isFalse();
  }

  @Test
  void shouldNotReportTruncatedWhenFewerRowsExistThanTheCap() throws Exception {
    final QueryResult result = queryService.execute("SELECT * FROM ten_rows", 500);

    assertThat(result.rows()).hasSize(10);
    assertThat(result.truncated()).isFalse();
  }

  @Test
  void shouldReportTruncatedForAZeroRowCapWhenRowsExist() throws Exception {
    final QueryResult result = queryService.execute("SELECT * FROM ten_rows", 0);

    assertThat(result.rows()).isEmpty();
    assertThat(result.truncated()).isTrue();
  }
}
