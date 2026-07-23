/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.support.ParquetFixtures;
import io.camunda.analytics.lake.serving.web.LakeController.QueryResponse;
import io.camunda.analytics.lake.serving.web.LakeController.TableInfo;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Exercises the proof-of-life REST surface end to end against a real, fixture-backed warehouse: one
 * small table with more rows than the configured {@code lake.serving.max-rows}, so the row cap is
 * actually observed, not merely configured.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class LakeControllerIT {

  private static final String TABLE = "instances";
  private static final int TOTAL_ROWS = 10;
  private static final int MAX_ROWS = 3;

  @TempDir private static Path warehouseDir;

  @Autowired private TestRestTemplate restTemplate;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    ParquetFixtures.writeTable(warehouseDir, TABLE, TOTAL_ROWS);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
    registry.add("lake.serving.max-rows", () -> String.valueOf(MAX_ROWS));
  }

  @Test
  void shouldReportHealthAsUp() {
    final ResponseEntity<Map> response = restTemplate.getForEntity("/api/health", Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsEntry("status", "UP");
  }

  @Test
  void shouldListDiscoveredTableWithRowCount() {
    final ResponseEntity<TableInfo[]> response =
        restTemplate.getForEntity("/api/tables", TableInfo[].class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody())
        .anySatisfy(
            table -> {
              assertThat(table.name()).isEqualTo(TABLE);
              assertThat(table.rowCount()).isEqualTo((long) TOTAL_ROWS);
            });
  }

  @Test
  void shouldEnforceRowLimitOnQuery() {
    final ResponseEntity<QueryResponse> response =
        restTemplate.exchange(
            "/api/query",
            HttpMethod.POST,
            plainTextBody("SELECT * FROM " + TABLE),
            QueryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().rows()).hasSize(MAX_ROWS);
    assertThat(response.getBody().columns()).contains("id", "name");
    // Silently capping is this endpoint's documented browse contract -- still 200, but now
    // observable via `truncated` (see LakeQueryService#execute(String, int)).
    assertThat(response.getBody().truncated()).isTrue();
  }

  @Test
  void shouldReportNotTruncatedWhenEveryRowFits() {
    final ResponseEntity<QueryResponse> response =
        restTemplate.exchange(
            "/api/query",
            HttpMethod.POST,
            plainTextBody("SELECT * FROM " + TABLE + " LIMIT 1"),
            QueryResponse.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).isNotNull();
    assertThat(response.getBody().truncated()).isFalse();
  }

  @Test
  void shouldReturnBadRequestForAFailingQuery() {
    final ResponseEntity<Map> response =
        restTemplate.exchange(
            "/api/query",
            HttpMethod.POST,
            plainTextBody("SELECT * FROM does_not_exist"),
            Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsKey("error");
  }

  @Test
  void shouldReturnBadRequestForAnEmptyQuery() {
    final ResponseEntity<Map> response =
        restTemplate.exchange("/api/query", HttpMethod.POST, plainTextBody(""), Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
  }

  private static HttpEntity<String> plainTextBody(final String body) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.TEXT_PLAIN);
    return new HttpEntity<>(body, headers);
  }
}
