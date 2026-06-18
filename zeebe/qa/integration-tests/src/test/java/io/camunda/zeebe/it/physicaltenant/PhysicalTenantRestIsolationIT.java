/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.it.physicaltenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.client.CamundaClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper;
import io.camunda.zeebe.qa.util.cluster.PhysicalTenantsITHelper.Storage;
import io.camunda.zeebe.qa.util.cluster.TestStandaloneBroker;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
final class PhysicalTenantRestIsolationIT {

  private static final String DEFAULT = PhysicalTenantsITHelper.DEFAULT_TENANT_ID;
  private static final String TENANT_A = "tenanta";
  private static final String JOB_TYPE = "task";
  private static final String PROCESS_ID = "rest-isolation-process";

  // both tenants use an isolated in-memory RDBMS so the REST query endpoints (which read from
  // secondary storage) can serve process definitions and instances per tenant
  private static final PhysicalTenantsITHelper TENANTS =
      PhysicalTenantsITHelper.builder()
          .withTenant(DEFAULT, Storage.rdbmsH2("pt-default"))
          .withTenant(TENANT_A, Storage.rdbmsH2("pt-tenanta"))
          .build();

  @TestZeebe
  private final TestStandaloneBroker broker =
      TENANTS.configure(new TestStandaloneBroker().withUnauthenticatedAccess());

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void shouldIsolateProcessDefinitionsAndInstancesAcrossPhysicalTenantsOverRest() throws Exception {
    // given
    final BpmnModelInstance process =
        Bpmn.createExecutableProcess(PROCESS_ID)
            .startEvent()
            .serviceTask("task", t -> t.zeebeJobType(JOB_TYPE))
            .endEvent()
            .done();

    final long processDefinitionKey;
    final long processInstanceKey;
    try (final CamundaClient tenantAClient = TENANTS.newClientBuilder(broker, TENANT_A).build()) {
      processDefinitionKey =
          await("deployment to tenant A succeeds")
              .atMost(Duration.ofSeconds(30))
              .ignoreExceptions()
              .until(
                  () ->
                      tenantAClient
                          .newDeployResourceCommand()
                          .addProcessModel(process, PROCESS_ID + ".bpmn")
                          .send()
                          .join()
                          .getProcesses()
                          .get(0)
                          .getProcessDefinitionKey(),
                  key -> key > 0);

      processInstanceKey =
          tenantAClient
              .newCreateInstanceCommand()
              .bpmnProcessId(PROCESS_ID)
              .latestVersion()
              .send()
              .join()
              .getProcessInstanceKey();
    }

    // when
    await("tenant A's process definition and instance are queryable over REST")
        .atMost(Duration.ofSeconds(60))
        .pollInterval(Duration.ofMillis(500))
        .untilAsserted(
            () -> {
              assertThat(searchCount(TENANT_A, "process-definitions")).isEqualTo(1);
              assertThat(searchCount(TENANT_A, "process-instances")).isEqualTo(1);
            });

    // then - the process definition is readable in tenant A but invisible to the default tenant
    assertThat(getStatus(TENANT_A, "process-definitions/" + processDefinitionKey)).isEqualTo(200);
    assertThat(getStatus(DEFAULT, "process-definitions/" + processDefinitionKey)).isEqualTo(404);
    assertThat(searchCount(DEFAULT, "process-definitions")).isZero();

    // and - the process instance is readable in tenant A but invisible to the default tenant
    assertThat(getStatus(TENANT_A, "process-instances/" + processInstanceKey)).isEqualTo(200);
    assertThat(getStatus(DEFAULT, "process-instances/" + processInstanceKey)).isEqualTo(404);
    assertThat(searchCount(DEFAULT, "process-instances")).isZero();
  }

  private int getStatus(final String tenantId, final String path) throws Exception {
    final HttpResponse<Void> response =
        httpClient.send(
            HttpRequest.newBuilder(uri(tenantId, path)).GET().build(),
            HttpResponse.BodyHandlers.discarding());
    return response.statusCode();
  }

  private int searchCount(final String tenantId, final String resource) throws Exception {
    final HttpResponse<String> response =
        httpClient.send(
            HttpRequest.newBuilder(uri(tenantId, resource + "/search"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    final JsonNode items = objectMapper.readTree(response.body()).get("items");
    return items == null ? 0 : items.size();
  }

  private URI uri(final String tenantId, final String path) {
    return URI.create(TENANTS.restBaseFor(broker, tenantId) + "/" + path);
  }
}
