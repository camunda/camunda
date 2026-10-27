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
import io.camunda.client.api.search.response.ProcessInstance;
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
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.lifecycle.Startables;

/**
 * Verifies that each physical tenant's {@code camundaexporter} — foreground writes and background
 * tasks — runs against that tenant's own Elasticsearch <em>cluster</em>: one broker, two tenants,
 * two clusters, mapped 1:1. Per tenant, a failed job raises an incident; the {@code incident} flag
 * on the process-instance document is only set by the background {@code IncidentUpdateTask}, so the
 * flag appearing through the tenant's own client proves both the writer path and the
 * background-task repositories targeted that tenant's cluster. The other tenant's cluster is
 * additionally checked directly for the absence of the documents, so leakage cannot be masked by
 * tenant-scoped readers. The shared-cluster index-prefix mode is covered by {@link
 * PhysicalTenantCamundaExporterBackgroundTaskIT}.
 */
@Timeout(240)
@ZeebeIntegration
final class PhysicalTenantSeparateSecondaryStorageIT {

  private static final String TENANT_A = "tenanta";
  private static final String JOB_TYPE = "fail-me";

  @SuppressWarnings("resource")
  private static final ElasticsearchContainer DEFAULT_ES =
      TestSearchContainers.createDefaultElasticsearchContainer();

  @SuppressWarnings("resource")
  private static final ElasticsearchContainer TENANT_A_ES =
      TestSearchContainers.createDefaultElasticsearchContainer();

  private static final String DEFAULT_ES_URL;
  private static final String TENANT_A_ES_URL;

  static {
    Startables.deepStart(DEFAULT_ES, TENANT_A_ES).join();
    DEFAULT_ES_URL = "http://" + DEFAULT_ES.getHttpHostAddress();
    TENANT_A_ES_URL = "http://" + TENANT_A_ES.getHttpHostAddress();
  }

  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(
              PhysicalTenantsITHelper.DEFAULT_TENANT_ID,
              Storage.elasticsearch(DEFAULT_ES_URL, "defaultprefix"))
          .withTenant(TENANT_A, Storage.elasticsearch(TENANT_A_ES_URL, "tenantaprefix"))
          .build();

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(
          new TestStandaloneBroker().withUnauthenticatedAccess().withCreateSchema(true));

  @AutoClose private CamundaClient tenantAClient;
  @AutoClose private CamundaClient defaultClient;

  @BeforeEach
  void setUp() {
    tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build();
    defaultClient =
        TENANTS.newClientBuilder(broker, PhysicalTenantsITHelper.DEFAULT_TENANT_ID).build();
  }

  @Test
  void shouldRouteWritesAndBackgroundTasksToEachTenantsOwnCluster() throws Exception {
    // given an incident in each tenant
    createIncident(defaultClient, "default-process");
    createIncident(tenantAClient, "tenanta-process");

    // then each tenant's instance is written to and incident-flagged in its own cluster ...
    Awaitility.await("each tenant's process instance is incident-flagged in its own cluster")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              assertThat(incidentFlaggedInstances(defaultClient, "default-process")).isNotEmpty();
              assertThat(incidentFlaggedInstances(tenantAClient, "tenanta-process")).isNotEmpty();
            });

    // ... and the other tenant's cluster never received its documents
    assertThat(countDocuments(DEFAULT_ES_URL, "tenanta-process")).isZero();
    assertThat(countDocuments(TENANT_A_ES_URL, "default-process")).isZero();
  }

  private static List<ProcessInstance> incidentFlaggedInstances(
      final CamundaClient client, final String processId) {
    return client
        .newProcessInstanceSearchRequest()
        .filter(f -> f.processDefinitionId(processId).hasIncident(true))
        .send()
        .join()
        .items();
  }

  /** Counts documents matching the process id across all indices of the given cluster. */
  private static long countDocuments(final String clusterUrl, final String processId)
      throws IOException, InterruptedException {
    final var query =
        URLEncoder.encode("bpmnProcessId:\"" + processId + "\"", StandardCharsets.UTF_8);
    final var request =
        HttpRequest.newBuilder(URI.create(clusterUrl + "/_all/_count?q=" + query)).GET().build();
    final var response = HTTP.send(request, BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return MAPPER.readTree(response.body()).path("count").asLong();
  }

  private static void createIncident(final CamundaClient client, final String processId) {
    final var process =
        Bpmn.createExecutableProcess(processId)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();
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

    Awaitility.await("a job is activated and failed for " + processId)
        .atMost(Duration.ofSeconds(30))
        .untilAsserted(
            () -> {
              final var jobs =
                  client
                      .newActivateJobsCommand()
                      .jobType(JOB_TYPE)
                      .maxJobsToActivate(1)
                      .send()
                      .join()
                      .getJobs();
              assertThat(jobs).isNotEmpty();
              client
                  .newFailCommand(jobs.getFirst().getKey())
                  .retries(0)
                  .errorMessage("expected failure to raise an incident")
                  .send()
                  .join();
            });
  }
}
