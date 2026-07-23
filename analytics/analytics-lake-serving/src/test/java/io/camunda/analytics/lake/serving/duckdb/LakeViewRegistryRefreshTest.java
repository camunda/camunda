/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.duckdb;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.support.ParquetFixtures;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * A table appearing under the warehouse directory after startup isn't visible until something
 * triggers a rescan -- either the explicit {@code POST /api/refresh} endpoint, or a tool request
 * naming a still-missing view (see {@link LakeViewRegistry#ensureAvailable}).
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class LakeViewRegistryRefreshTest {

  @TempDir private static Path warehouseDir;

  @Autowired private LakeViewRegistry viewRegistry;
  @Autowired private TestRestTemplate restTemplate;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldNotSeeATableWrittenAfterStartupUntilRefreshed() {
    assertThat(viewRegistry.has("late_table")).isFalse();

    ParquetFixtures.writeTable(warehouseDir, "late_table", 3);
    assertThat(viewRegistry.has("late_table")).isFalse(); // still stale -- no rescan triggered yet

    final List<String> refreshed = viewRegistry.refresh();

    assertThat(refreshed).contains("late_table");
    assertThat(viewRegistry.has("late_table")).isTrue();
  }

  @Test
  void shouldRefreshExactlyOnceWhenEnsuringAMissingView() {
    ParquetFixtures.writeTable(warehouseDir, "another_late_table", 2);

    final boolean available = viewRegistry.ensureAvailable("another_late_table");

    assertThat(available).isTrue();
    assertThat(viewRegistry.has("another_late_table")).isTrue();
  }

  @Test
  void shouldReportFalseForAViewThatWillNeverExist() {
    final boolean available = viewRegistry.ensureAvailable("never_going_to_exist");

    assertThat(available).isFalse();
  }

  @Test
  void shouldExposeRefreshOverHttp() {
    ParquetFixtures.writeTable(warehouseDir, "http_refreshed_table", 1);

    final ResponseEntity<String[]> response =
        restTemplate.exchange("/api/refresh", HttpMethod.POST, null, String[].class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).contains("http_refreshed_table");
  }
}
