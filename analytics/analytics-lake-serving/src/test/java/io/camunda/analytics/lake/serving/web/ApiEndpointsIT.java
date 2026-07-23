/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.support.StepScenarioFixtures;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Real HTTP round trips (not just direct service calls) over {@code GET /api/registry}, {@code POST
 * /api/tools/series}, and {@code POST /api/investigate} -- proving the JSON wiring (including the
 * {@code CohortSpec} sealed-interface polymorphism used inside {@code toolParams}) actually
 * serializes end to end, not only that the underlying services compute the right numbers.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class ApiEndpointsIT {

  @TempDir private static Path warehouseDir;

  @Autowired private TestRestTemplate restTemplate;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    StepScenarioFixtures.build(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  @Test
  void shouldExposeTheRegistryOverHttp() {
    final ResponseEntity<Map> response = restTemplate.getForEntity("/api/registry", Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsKey("entities");
    assertThat(response.getBody()).containsKey("dimKinds");
  }

  @Test
  void shouldRunASeriesToolCallOverHttp() {
    final Map<String, Object> request =
        Map.of(
            "entity",
            "instances",
            "measure",
            "duration_ms",
            "filters",
            Map.of("process_id", StepScenarioFixtures.ORDER_PROCESS),
            "from",
            StepScenarioFixtures.FROM,
            "to",
            StepScenarioFixtures.TO,
            "grainMinutes",
            20);

    final ResponseEntity<Map> response =
        restTemplate.postForEntity("/api/tools/series", request, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(response.getBody()).containsKeys("points", "sql", "params");
    assertThat((List<?>) response.getBody().get("points")).hasSize(2);
  }

  @Test
  void shouldRunInvestigateOverHttpAndReturnAChangepointFindingFirst() {
    final Map<String, Object> request =
        Map.of(
            "entity",
            "instances",
            "measure",
            "duration_ms",
            "from",
            StepScenarioFixtures.FROM,
            "to",
            StepScenarioFixtures.TO);

    final ResponseEntity<Map> response =
        restTemplate.postForEntity("/api/investigate", request, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    final List<?> findings = (List<?>) response.getBody().get("findings");
    assertThat(findings).isNotEmpty();
    final Map<?, ?> first = (Map<?, ?>) findings.get(0);
    assertThat(first.get("kind")).isEqualTo("CHANGEPOINT");
  }

  @Test
  void shouldReturnBadRequestForAnUnknownEntity() {
    final Map<String, Object> request =
        Map.of(
            "entity",
            "does_not_exist",
            "from",
            StepScenarioFixtures.FROM,
            "to",
            StepScenarioFixtures.TO,
            "grainMinutes",
            1);

    final ResponseEntity<Map> response =
        restTemplate.postForEntity("/api/tools/series", request, Map.class);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(response.getBody()).containsKey("error");
  }
}
