/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * Verifies that a {@code camundaexporter} declared explicitly in the <b>root</b> configuration
 * (under {@code camunda.data.exporters}, pinning the root connection in its args — a documented
 * production pattern) does not override a physical tenant's autoconfigured exporter: the tenant's
 * exporter configuration is derived from the tenant's own secondary-storage properties (ADR-0008
 * §1), so the tenant's documents must land under the tenant's own index prefix, never the root's.
 * The autoconfigure-only variants of this isolation are covered by {@link
 * PhysicalTenantCamundaExporterBackgroundTaskIT} (shared cluster) and {@link
 * PhysicalTenantSeparateSecondaryStorageIT} (separate clusters).
 */
@Timeout(240)
@ZeebeIntegration
final class PhysicalTenantExplicitRootCamundaExporterIT {

  private static final String TENANT_A = "tenanta";
  private static final String DEFAULT_PREFIX = "defaultprefix";
  private static final String TENANT_A_PREFIX = "tenantaprefix";

  @SuppressWarnings("resource")
  private static final ElasticsearchContainer ES =
      TestSearchContainers.createDefaultElasticsearchContainer();

  private static final String ES_URL;

  static {
    ES.start();
    ES_URL = "http://" + ES.getHttpHostAddress();
  }

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(
              PhysicalTenantsITHelper.DEFAULT_TENANT_ID,
              Storage.elasticsearch(ES_URL, DEFAULT_PREFIX))
          .withTenant(TENANT_A, Storage.elasticsearch(ES_URL, TENANT_A_PREFIX))
          .build();

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS
          .configure(new TestStandaloneBroker().withUnauthenticatedAccess().withCreateSchema(true))
          .withExporter(
              "camundaexporter",
              cfg -> {
                cfg.setClassName("io.camunda.exporter.CamundaExporter");
                cfg.setArgs(
                    Map.of(
                        "connect",
                        Map.of(
                            "url",
                            ES_URL,
                            "type",
                            "elasticsearch",
                            "indexPrefix",
                            DEFAULT_PREFIX)));
              });

  @AutoClose private CamundaClient tenantAClient;
  @AutoClose private CamundaClient defaultClient;

  @BeforeEach
  void setUp() {
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();
    defaultClient =
        TENANTS.newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID).build();
  }

  @Test
  void shouldExportTenantDocumentsUnderTenantPrefixDespiteExplicitRootExporter() throws Exception {
    // given a process instance in each tenant
    deployAndStart(defaultClient, "default-process");
    deployAndStart(tenantAClient, "tenanta-process");

    // then each tenant's documents land under that tenant's own index prefix
    Awaitility.await("each tenant's documents are written under the tenant's own index prefix")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              assertThat(countDocuments(DEFAULT_PREFIX, "default-process")).isPositive();
              assertThat(countDocuments(TENANT_A_PREFIX, "tenanta-process")).isPositive();
            });

    // ... and the tenant's documents never leak under the root's prefix
    assertThat(countDocuments(DEFAULT_PREFIX, "tenanta-process")).isZero();
  }

  /** Counts documents matching the process id across all indices of the given prefix. */
  private static long countDocuments(final String indexPrefix, final String processId)
      throws IOException, InterruptedException {
    final var query =
        URLEncoder.encode("bpmnProcessId:\"" + processId + "\"", StandardCharsets.UTF_8);
    final var request =
        HttpRequest.newBuilder(URI.create(ES_URL + "/" + indexPrefix + "*/_count?q=" + query))
            .GET()
            .build();
    final var response = HTTP.send(request, BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return MAPPER.readTree(response.body()).path("count").asLong();
  }

  private static void deployAndStart(final CamundaClient client, final String processId) {
    final var process = Bpmn.createExecutableProcess(processId).startEvent().endEvent().done();
    Awaitility.await("process is deployed for " + processId)
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () ->
                assertThat(
                        client
                            .newDeployResourceCommand()
                            .addProcessModel(process, processId + ".bpmn")
                            .send()
                            .join()
                            .getProcesses())
                    .isNotEmpty());
    client.newCreateInstanceCommand().bpmnProcessId(processId).latestVersion().send().join();
  }
}
