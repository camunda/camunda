/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import io.camunda.zeebe.test.util.testcontainers.TestSearchContainers;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.elasticsearch.ElasticsearchContainer;

/**
 * Verifies that the {@code camundaexporter}'s <em>background tasks</em> run against each physical
 * tenant's own storage. The incident-update task is the observable proxy: incident records are
 * written by an export handler, but the {@code incident} flag on the process-instance document is
 * only set afterwards by the background {@code IncidentUpdateTask} through the tenant's {@code
 * IncidentUpdateRepository}. Two tenants share one Elasticsearch cluster under distinct index
 * prefixes; each tenant's process instance must become incident-flagged in that tenant's indices
 * only — proving the background task ran with the tenant-scoped configuration.
 */
@Timeout(240)
@ZeebeIntegration
final class PhysicalTenantCamundaExporterBackgroundTaskIT {

  private static final String TENANT_A = "tenanta";
  private static final String JOB_TYPE = "fail-me";

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
              Storage.elasticsearch(ES_URL, "defaultprefix"))
          .withTenant(TENANT_A, Storage.elasticsearch(ES_URL, "tenantaprefix"))
          .build();

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
  void shouldRunIncidentUpdateTaskAgainstEachTenantsOwnStorage() {
    // given an incident in each tenant
    createIncident(defaultClient, "default-process");
    createIncident(tenantAClient, "tenanta-process");

    // then each tenant's background incident-update task flags that tenant's process instance ...
    Awaitility.await("each tenant's process instance is incident-flagged in its own storage")
        .atMost(Duration.ofSeconds(60))
        .untilAsserted(
            () -> {
              assertThat(incidentFlaggedInstances(defaultClient, "default-process")).isNotEmpty();
              assertThat(incidentFlaggedInstances(tenantAClient, "tenanta-process")).isNotEmpty();
            });

    // ... and never the other tenant's
    assertThat(incidentFlaggedInstances(defaultClient, "tenanta-process")).isEmpty();
    assertThat(incidentFlaggedInstances(tenantAClient, "default-process")).isEmpty();
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
