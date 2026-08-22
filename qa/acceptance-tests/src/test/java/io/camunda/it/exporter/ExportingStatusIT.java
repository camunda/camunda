/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.exporter;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * End-to-end coverage for {@code GET /v2/exporting}, which reports the exporting phase of a
 * physical tenant so backup tooling can confirm a pause took effect instead of trusting its own
 * bookkeeping.
 *
 * <p>{@code DynamicConfigExportingStateControllerTest} and {@code ExportingControllerTest} stub the
 * underlying dependencies, so they only prove the status is submitted and aggregated correctly.
 * What they cannot prove is that the status read out of a running, multi-partition cluster is the
 * one pause and resume just applied via dynamic cluster configuration, including across a restart —
 * that is the whole point of the endpoint. Every phase assertion is therefore cross-checked against
 * the {@code partitions} actuator, which reads the phase by an independent path.
 *
 * <p>Several partitions are configured because the endpoint folds over all of them: with a single
 * partition, an aggregation that dropped every partition but the first would still pass.
 */
@ZeebeIntegration
final class ExportingStatusIT {

  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final int PARTITION_COUNT = 3;
  private static final ObjectMapper JSON = new ObjectMapper();

  @TestZeebe(partitionCount = PARTITION_COUNT)
  private static final TestStandaloneBroker BROKER =
      new TestStandaloneBroker()
          .withUnauthenticatedAccess()
          .withClusterConfig(cluster -> cluster.setPartitionCount(PARTITION_COUNT));

  @AfterEach
  void resumeExporting() throws Exception {
    // The phase is persisted per partition, so a test leaving exporting paused would decide the
    // outcome of the next one.
    post("/v2/exporting/resume");
  }

  @Test
  void shouldReportExportingWhenNotPaused() {
    // when - then
    assertThat(awaitSettledStatus()).isEqualTo("EXPORTING");
    assertPartitionsReport("EXPORTING");
  }

  @Test
  void shouldReportPausedAfterPausing() throws Exception {
    // when
    post("/v2/exporting/pause");

    // then
    assertThat(awaitSettledStatus()).isEqualTo("PAUSED");
    assertPartitionsReport("PAUSED");
  }

  @Test
  void shouldReportSoftPausedAfterSoftPausing() throws Exception {
    // when
    post("/v2/exporting/pause?soft=true");

    // then - a soft pause must be distinguishable from a hard one: tooling that cannot tell them
    // apart cannot tell whether exporting is still progressing
    assertThat(awaitSettledStatus()).isEqualTo("SOFT_PAUSED");
    assertPartitionsReport("SOFT_PAUSED");
  }

  @Test
  void shouldReportExportingAgainAfterResuming() throws Exception {
    // given
    post("/v2/exporting/pause");
    assertThat(awaitSettledStatus()).isEqualTo("PAUSED");

    // when
    post("/v2/exporting/resume");

    // then
    assertThat(awaitSettledStatus()).isEqualTo("EXPORTING");
    assertPartitionsReport("EXPORTING");
  }

  @Test
  void shouldStillReportPausedAfterRestart() throws Exception {
    // given
    post("/v2/exporting/pause");
    assertThat(awaitSettledStatus()).isEqualTo("PAUSED");

    // when - the phase is persisted, so a restart must not silently resume exporting and leave
    // tooling believing the log is still protected from compaction
    BROKER.stop();
    BROKER.start().awaitCompleteTopology(BROKER.unifiedConfig());

    // then
    assertThat(awaitSettledStatus()).isEqualTo("PAUSED");
    assertPartitionsReport("PAUSED");
  }

  /**
   * The unprefixed path resolves to the default physical tenant, so on a single-tenant cluster both
   * forms must answer identically.
   */
  @ParameterizedTest
  @ValueSource(strings = {"/v2/exporting", "/physical-tenants/default/v2/exporting"})
  void shouldAnswerOnBothTenantScopedPaths(final String path) throws Exception {
    // given
    post("/v2/exporting/pause");
    awaitSettledStatus();

    // when
    final var response = send("GET", path);

    // then - the body carries the status and nothing else
    assertThat(response.statusCode()).isEqualTo(200);
    assertThat(JSON.readTree(response.body()).properties())
        .singleElement()
        .satisfies(
            entry -> {
              assertThat(entry.getKey()).isEqualTo("status");
              assertThat(entry.getValue().asText()).isEqualTo("PAUSED");
            });
  }

  /**
   * Pause and resume answer once every partition has acknowledged, but a replica may still be
   * mid-transition, so the status is polled until it settles on a single phase rather than read
   * once.
   */
  private String awaitSettledStatus() {
    return Awaitility.await("until the exporting status settles on a single phase")
        .atMost(TIMEOUT)
        .until(ExportingStatusIT::readStatus, phase -> !"MIXED".equals(phase));
  }

  private static String readStatus() throws Exception {
    final var response = send("GET", "/v2/exporting");
    assertThat(response.statusCode()).isEqualTo(200);
    return JSON.readTree(response.body()).get("status").asText();
  }

  /**
   * Cross-checks the aggregated status against the {@code partitions} actuator, which reads the
   * exporter phase straight from each partition instead of through the endpoint under test.
   */
  private void assertPartitionsReport(final String expectedPhase) {
    Awaitility.await("until every partition reports " + expectedPhase)
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(PartitionsActuator.of(BROKER).query().values())
                    .hasSize(PARTITION_COUNT)
                    .allSatisfy(
                        partition ->
                            assertThat(partition.exporterPhase()).isEqualTo(expectedPhase)));
  }

  private static void post(final String path) throws Exception {
    final var response = send("POST", path);
    assertThat(response.statusCode())
        .as("POST %s should succeed, but got: %s", path, response.body())
        .isEqualTo(204);
  }

  private static HttpResponse<String> send(final String method, final String path)
      throws Exception {
    final var base = BROKER.restAddress().toString();
    final var root = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    final var request =
        HttpRequest.newBuilder(URI.create(root + path))
            .method(method, BodyPublishers.noBody())
            .header("Accept", "application/json")
            .build();
    return HttpClient.newHttpClient().send(request, BodyHandlers.ofString());
  }
}
