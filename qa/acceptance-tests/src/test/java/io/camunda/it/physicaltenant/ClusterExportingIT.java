/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.zeebe.qa.util.actuator.PartitionsActuator;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
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
import java.util.Base64;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.Test;

/**
 * End-to-end coverage for the cluster-wide exporting endpoints ({@code POST
 * /cluster/v2/exporting/pause}, {@code POST /cluster/v2/exporting/resume}, {@code GET
 * /cluster/v2/exporting}), which let an operator pause, resume and poll exporting across every
 * physical tenant in one call instead of once per tenant (ADR 003 D2).
 *
 * <p>More than one partition per tenant is configured because the fold is exercised across
 * partitions as well as tenants: with a single partition per tenant, a fold that drops all but the
 * first partition or tenant would still pass (see {@code ExportingStatusIT}, which documents the
 * same reasoning for the per-PT endpoint).
 *
 * <p>Every phase assertion is cross-checked against the {@code partitions} actuator, scoped per
 * physical tenant, which reads the phase by a fully independent path from the endpoint under test.
 */
@ZeebeIntegration
final class ClusterExportingIT {

  private static final String TENANT_B = "tenantb";
  private static final String CLUSTER_ADMIN_USER = "cluster-operator";
  private static final String CLUSTER_ADMIN_PASSWORD = "cluster-secret";
  private static final int DEFAULT_TENANT_PARTITION_COUNT = 2;
  private static final int TENANT_B_PARTITION_COUNT = 2;
  private static final Duration TIMEOUT = Duration.ofSeconds(30);
  private static final ObjectMapper JSON = new ObjectMapper();

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, Storage.none())
          .withTenant(TENANT_B, Storage.none())
          .build();

  @TestZeebe(partitionCount = DEFAULT_TENANT_PARTITION_COUNT, purgeAfterEach = false)
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker()
              .withUnauthenticatedAccess()
              .withProperty(
                  "camunda.security.cluster-admin.basic.users[0].name", CLUSTER_ADMIN_USER)
              .withProperty(
                  "camunda.security.cluster-admin.basic.users[0].password", CLUSTER_ADMIN_PASSWORD)
              .withClusterConfig(
                  cluster -> cluster.setPartitionCount(DEFAULT_TENANT_PARTITION_COUNT))
              .withPtConfig(
                  TENANT_B,
                  camunda -> camunda.getCluster().setPartitionCount(TENANT_B_PARTITION_COUNT)));

  @AutoClose private final HttpClient httpClient = HttpClient.newHttpClient();

  @AfterEach
  void resumeExporting() throws Exception {
    // The phase is persisted per partition, so a test leaving exporting paused would decide the
    // outcome of the next one.
    post("/cluster/v2/exporting/resume");
  }

  @Test
  void shouldPauseExportingOnEveryPhysicalTenant() throws Exception {
    // when
    post("/cluster/v2/exporting/pause");

    // then
    assertThat(awaitSettledClusterStatus()).isEqualTo("PAUSED");
    assertPartitionsReport(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, "PAUSED");
    assertPartitionsReport(TENANT_B, "PAUSED");
  }

  @Test
  void shouldSoftPauseExportingOnEveryPhysicalTenant() throws Exception {
    // when
    post("/cluster/v2/exporting/pause?soft=true");

    // then
    assertThat(awaitSettledClusterStatus()).isEqualTo("SOFT_PAUSED");
    assertPartitionsReport(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, "SOFT_PAUSED");
    assertPartitionsReport(TENANT_B, "SOFT_PAUSED");
  }

  @Test
  void shouldResumeExportingOnEveryPhysicalTenant() throws Exception {
    // given
    post("/cluster/v2/exporting/pause");
    assertThat(awaitSettledClusterStatus()).isEqualTo("PAUSED");

    // when
    post("/cluster/v2/exporting/resume");

    // then
    assertThat(awaitSettledClusterStatus()).isEqualTo("EXPORTING");
    assertPartitionsReport(PhysicalTenantsITHelper.DEFAULT_TENANT_ID, "EXPORTING");
    assertPartitionsReport(TENANT_B, "EXPORTING");
  }

  @Test
  void shouldReportFoldedStatusForTheWholeCluster() {
    // when / then
    assertThat(awaitSettledClusterStatus()).isEqualTo("EXPORTING");
  }

  /**
   * Pause and resume answer once every partition of every tenant has acknowledged, but a replica
   * may still be mid-transition, so the status is polled until it settles rather than read once.
   */
  private String awaitSettledClusterStatus() {
    return Awaitility.await("until the cluster-wide exporting status settles on a single phase")
        .atMost(TIMEOUT)
        .until(this::readClusterStatus, phase -> !"MIXED".equals(phase));
  }

  private String readClusterStatus() throws Exception {
    final var response = send("GET", "/cluster/v2/exporting");
    assertThat(response.statusCode()).isEqualTo(200);
    return JSON.readTree(response.body()).get("status").asText();
  }

  private void assertPartitionsReport(final String physicalTenantId, final String expectedPhase) {
    Awaitility.await(
            "until every partition of physical tenant '"
                + physicalTenantId
                + "' reports "
                + expectedPhase)
        .atMost(TIMEOUT)
        .untilAsserted(
            () ->
                assertThat(PartitionsActuator.of(broker).query(physicalTenantId).values())
                    .isNotEmpty()
                    .allSatisfy(
                        partition ->
                            assertThat(partition.exporterPhase()).isEqualTo(expectedPhase)));
  }

  private void post(final String path) throws Exception {
    final var response = send("POST", path);
    assertThat(response.statusCode())
        .as("POST %s should succeed, but got: %s", path, response.body())
        .isEqualTo(204);
  }

  private HttpResponse<String> send(final String method, final String path) throws Exception {
    final var request =
        HttpRequest.newBuilder(resolve(path))
            .header("Authorization", basicAuth(CLUSTER_ADMIN_USER, CLUSTER_ADMIN_PASSWORD))
            .method(method, BodyPublishers.noBody())
            .header("Accept", "application/json")
            .build();
    return httpClient.send(request, BodyHandlers.ofString());
  }

  private URI resolve(final String path) {
    final var base = broker.restAddress().toString();
    final var root = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
    return URI.create(root + path);
  }

  private static String basicAuth(final String user, final String password) {
    return "Basic " + Base64.getEncoder().encodeToString((user + ":" + password).getBytes());
  }
}
