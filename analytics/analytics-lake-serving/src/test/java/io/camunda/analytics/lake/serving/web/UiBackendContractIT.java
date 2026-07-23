/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.analytics.lake.serving.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.analytics.lake.serving.support.ObjectFabricFixtures;
import io.camunda.analytics.lake.serving.support.StepScenarioFixtures;
import java.nio.file.Path;
import java.time.Instant;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Replays the webapp's REAL request corpus against the backend — the exact bodies the client's
 * tiles, panels, and pages send (numeric epoch-millis timestamps, {@code "cnt"} as a measure name,
 * quantiles without a measure, registry-resolved dims) — so a UI↔backend contract mismatch fails
 * here, offline, instead of surfacing one stack-deployment at a time as a broken dashboard tile.
 * Every entry documents which UI element sends it. Responses only need the contract-level
 * guarantee: a 2xx with the agreed shape, or (for an entity the warehouse genuinely lacks) the
 * clean 4xx the tiles render as their empty state — never a 5xx.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class UiBackendContractIT {

  @TempDir private static Path warehouseDir;

  private static final long FROM = Instant.parse(StepScenarioFixtures.FROM).toEpochMilli();
  private static final long TO = Instant.parse(StepScenarioFixtures.TO).toEpochMilli();

  @Autowired private TestRestTemplate rest;

  @DynamicPropertySource
  static void lakeServingProperties(final DynamicPropertyRegistry registry) {
    StepScenarioFixtures.build(warehouseDir);
    ObjectFabricFixtures.build(warehouseDir);
    registry.add("lake.serving.warehouse-dir", () -> warehouseDir.toString());
  }

  private ResponseEntity<String> post(final String path, final String jsonBody) {
    final HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return rest.postForEntity(path, new HttpEntity<>(jsonBody, headers), String.class);
  }

  private void assertOk(final ResponseEntity<String> response, final String what) {
    assertThat(response.getStatusCode().is2xxSuccessful())
        .as("%s -> %s: %s", what, response.getStatusCode(), response.getBody())
        .isTrue();
  }

  @Test
  void shouldServeTheAppShellAndBootstrapCalls() {
    // given/when/then: the SPA's deep links (only when the frontend build actually packaged the
    // shell -- a -PskipFrontendBuild dev run has no index.html to forward to) and the two calls
    // AppDataProvider fires on load
    if (getClass().getResource("/static/index.html") != null) {
      assertOk(rest.getForEntity("/dashboards", String.class), "SPA deep link /dashboards");
      assertOk(rest.getForEntity("/objects", String.class), "SPA deep link /objects");
    }
    assertOk(rest.getForEntity("/api/registry", String.class), "GET /api/registry");
    assertOk(rest.getForEntity("/api/objects/types", String.class), "GET /api/objects/types");
    assertOk(rest.getForEntity("/api/tables", String.class), "GET /api/tables");
  }

  @Test
  void shouldAnswerTheKpiTabSeriesRequests() {
    // given/when: the "Started vs. completed" tile — measure "cnt", numeric millis
    final String startedBody =
        seriesBody("instance_starts", "\"cnt\"", "null", Map.of(), FROM, TO, 60);
    final String completedBody = seriesBody("instances", "\"cnt\"", "null", Map.of(), FROM, TO, 60);

    // then
    assertOk(post("/api/tools/series", startedBody), "KPI started series");
    assertOk(post("/api/tools/series", completedBody), "KPI completed series");
  }

  @Test
  void shouldAnswerThePerformanceTabRequests() {
    // given/when: "Duration percentiles" sends a quantile with NO measure (backend defaults to
    // the entity's first measure); "Per-element p95" decomposes with a quantile and no measure
    final String p50 = seriesBody("instances", "null", "0.5", Map.of(), FROM, TO, 60);
    final String p95 = seriesBody("instances", "null", "0.95", Map.of(), FROM, TO, 60);
    final long span = TO - FROM;
    final String perElementP95 =
        "{\"entity\":\"activities\",\"measure\":null,\"quantile\":0.95,"
            + "\"window\":{\"from\":"
            + FROM
            + ",\"to\":"
            + TO
            + "},\"baseline\":{\"from\":"
            + (FROM - span)
            + ",\"to\":"
            + FROM
            + "},\"dim\":\"element_id\"}";

    // then
    assertOk(post("/api/tools/series", p50), "performance p50 series");
    assertOk(post("/api/tools/series", p95), "performance p95 series");
    assertOk(post("/api/tools/decompose", perElementP95), "per-element p95 decompose");
  }

  @Test
  void shouldAnswerTheThroughputDecomposeWithBareCount() {
    // given/when: "Throughput by process" — decompose over counts (measure null), dim resolved
    // by the client's registry helper to process_id
    final long span = TO - FROM;
    final String body =
        "{\"entity\":\"instance_starts\",\"measure\":null,\"quantile\":null,"
            + "\"window\":{\"from\":"
            + FROM
            + ",\"to\":"
            + TO
            + "},\"baseline\":{\"from\":"
            + (FROM - span)
            + ",\"to\":"
            + FROM
            + "},\"dim\":\"process_id\"}";

    // then
    assertOk(post("/api/tools/decompose", body), "throughput decompose");
  }

  @Test
  void shouldRejectAMissingEntityWithACleanClientError() {
    // given/when: the object-perspective tiles fire against objects_born/object_cohorts, which
    // this warehouse doesn't have — the tile renders the error message as its empty state, so it
    // must be a 4xx with a message, never a 5xx
    final ResponseEntity<String> response =
        post(
            "/api/tools/series",
            seriesBody("objects_born", "\"cnt\"", "null", Map.of(), FROM, TO, 60));

    // then
    assertThat(response.getStatusCode().is4xxClientError())
        .as("missing entity -> %s: %s", response.getStatusCode(), response.getBody())
        .isTrue();
    assertThat(response.getBody()).contains("error");
  }

  @Test
  void shouldAnswerTheChangepointPanelRequest() {
    // given/when: the changepoint panel sends the series shape with an explicit measure
    final String body = seriesBody("instances", "\"duration_ms\"", "null", Map.of(), FROM, TO, 1);

    // then
    assertOk(post("/api/tools/changepoint", body), "changepoint");
  }

  @Test
  void shouldAnswerTheObjectsPages() {
    // given/when: the objects list page (status filter from the URL) and the detail page's journey
    final String listBody =
        "{\"type\":\""
            + ObjectFabricFixtures.OBJECT_TYPE
            + "\",\"status\":\"ALL\",\"limit\":50,\"offset\":0}";
    final String journeyBody =
        "{\"type\":\""
            + ObjectFabricFixtures.OBJECT_TYPE
            + "\",\"id\":\""
            + ObjectFabricFixtures.ROOT_OBJECT_ID
            + "\"}";

    // then
    assertOk(post("/api/objects/list", listBody), "objects list");
    assertOk(post("/api/objects/journey", journeyBody), "objects journey");
  }

  @Test
  void shouldAnswerTheInvestigateEntryForm() {
    // given/when: Explain's entry form — entity + measure + numeric window
    final String body =
        "{\"entity\":\"instances\",\"measure\":\"duration_ms\",\"quantile\":null,"
            + "\"filters\":{},\"from\":"
            + FROM
            + ",\"to\":"
            + TO
            + "}";

    // then
    assertOk(post("/api/investigate", body), "investigate");
  }

  private static String seriesBody(
      final String entity,
      final String measureJson,
      final String quantileJson,
      final Map<String, String> filters,
      final long from,
      final long to,
      final int grainMinutes) {
    final StringBuilder filterJson = new StringBuilder("{");
    filters.forEach(
        (k, v) -> {
          if (filterJson.length() > 1) {
            filterJson.append(',');
          }
          filterJson.append('"').append(k).append("\":\"").append(v).append('"');
        });
    filterJson.append('}');
    return "{\"entity\":\""
        + entity
        + "\",\"measure\":"
        + measureJson
        + ",\"quantile\":"
        + quantileJson
        + ",\"filters\":"
        + filterJson
        + ",\"from\":"
        + from
        + ",\"to\":"
        + to
        + ",\"grainMinutes\":"
        + grainMinutes
        + "}";
  }
}
