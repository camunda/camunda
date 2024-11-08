/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.client.QueryTest.deployResource;
import static io.camunda.it.client.QueryTest.startProcessInstance;
import static io.camunda.it.client.QueryTest.waitForProcessInstancesToStart;
import static io.camunda.it.client.QueryTest.waitForProcessesToBeDeployed;
import static io.camunda.it.client.QueryTest.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.search.response.Incident;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
class IncidentQueryTest {

  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();

  private static ZeebeClient zeebeClient;

  @TestZeebe(initMethod = "initTestStandaloneCamunda")
  private static TestStandaloneCamunda testStandaloneCamunda;

  private static Incident incident;

  @SuppressWarnings("unused")
  static void initTestStandaloneCamunda() {
    testStandaloneCamunda = new TestStandaloneCamunda().withCamundaExporter();
  }

  @BeforeAll
  static void beforeAll() {
    zeebeClient = testStandaloneCamunda.newClientBuilder().build();

    final var processes =
        List.of("service_tasks_v1.bpmn", "service_tasks_v2.bpmn", "incident_process_v1.bpmn");
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(zeebeClient, String.format("process/%s", process)).getProcesses()));

    waitForProcessesToBeDeployed(zeebeClient, 3);

    startProcessInstance(zeebeClient, "service_tasks_v1");
    startProcessInstance(zeebeClient, "service_tasks_v2", "{\"path\":222}");
    startProcessInstance(zeebeClient, "incident_process_v1");

    waitForProcessInstancesToStart(zeebeClient, 3);
    waitUntilProcessInstanceHasIncidents(zeebeClient, 1);

    incident = zeebeClient.newIncidentQuery().send().join().items().getFirst();
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
  }

  @Test
  void shouldGetIncidentByKey() {
    // given
    final var incidentKey = incident.getIncidentKey();
    // when
    final var result = zeebeClient.newIncidentGetRequest(incidentKey).send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(incident);
  }

  @Test
  void shouldThrownExceptionIfNotFoundByKey() {
    // given
    final var invalidIncidentKey = 0xCAFE;

    // when / then
    final var exception =
        assertThrowsExactly(
            ProblemException.class,
            () -> zeebeClient.newIncidentGetRequest(invalidIncidentKey).send().join());
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
    assertThat(exception.details().getDetail()).isEqualTo("Incident with key 51966 not found");
  }

  @Test
  void shouldFilterByKey() {
    // given
    final var incidentKey = incident.getIncidentKey();

    // when
    final var result =
        zeebeClient.newIncidentQuery().filter(f -> f.incidentKey(incidentKey)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getIncidentKey()).isEqualTo(incidentKey);
  }

  @Test
  void shouldFilterByProcessInstanceKey() {
    // given
    final var processInstanceKey = incident.getProcessInstanceKey();

    // when
    final var result =
        zeebeClient
            .newIncidentQuery()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldFilterByState() {
    // given
    final var state = incident.getState();

    // when
    final var result = zeebeClient.newIncidentQuery().filter(f -> f.state(state)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getState()).isEqualTo(state);
  }

  @Test
  void shouldFilterByFlowNodeInstanceKey() {
    // given
    final var flowNodeInstanceKey = incident.getFlowNodeInstanceKey();

    // when
    final var result =
        zeebeClient
            .newIncidentQuery()
            .filter(f -> f.flowNodeInstanceKey(flowNodeInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getFlowNodeInstanceKey()).isEqualTo(flowNodeInstanceKey);
  }

  @Test
  void shouldFilterByProcessDefinitionKey() {
    // given
    final var processDefinitionKey = incident.getProcessDefinitionKey();

    // when
    final var result =
        zeebeClient
            .newIncidentQuery()
            .filter(f -> f.processDefinitionKey(processDefinitionKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
  }

  @Test
  void shouldFilterByProcessDefinitionId() {
    // given
    final var processDefinitionId = incident.getProcessDefinitionId();

    // when
    final var result =
        zeebeClient
            .newIncidentQuery()
            .filter(f -> f.processDefinitionId(processDefinitionId))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getProcessDefinitionId()).isEqualTo(processDefinitionId);
  }

  @Test
  void shouldFilterByErrorType() {
    // given
    final var errorType = incident.getErrorType();

    // when
    final var result =
        zeebeClient.newIncidentQuery().filter(f -> f.errorType(errorType)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getErrorType()).isEqualTo(errorType);
  }

  @Test
  void shouldFilterByFlowNodeId() {
    // given
    final var flowNodeId = incident.getFlowNodeId();

    // when
    final var result =
        zeebeClient.newIncidentQuery().filter(f -> f.flowNodeId(flowNodeId)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getFlowNodeId()).isEqualTo(flowNodeId);
  }

  @Test
  void shouldFilterByJobKey() {
    // given
    final var jobKey = incident.getJobKey();

    // when
    final var result = zeebeClient.newIncidentQuery().filter(f -> f.jobKey(jobKey)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getJobKey()).isEqualTo(jobKey);
  }

  @Test
  void shouldFilterByTreePath() {
    // given
    final var treePath = incident.getTreePath();

    // when
    final var result =
        zeebeClient.newIncidentQuery().filter(f -> f.treePath(treePath)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getTreePath()).isEqualTo(treePath);
  }

  @Test
  void shouldFilterByTenantId() {
    // given
    final var tenantId = incident.getTenantId();

    // when
    final var result =
        zeebeClient.newIncidentQuery().filter(f -> f.tenantId(tenantId)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void shouldSortByIncidentKey() {
    final var resultAsc =
        zeebeClient.newIncidentQuery().sort(s -> s.processInstanceKey().asc()).send().join();
    final var resultDesc =
        zeebeClient.newIncidentQuery().sort(s -> s.processInstanceKey().desc()).send().join();

    final var all = resultAsc.items().stream().map(Incident::getIncidentKey).toList();
    final var sortedAsc = all.stream().sorted(Comparator.naturalOrder()).toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    // then
    assertThat(resultAsc.items().stream().map(Incident::getIncidentKey).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getIncidentKey).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortByErrorType() {
    final var resultAsc =
        zeebeClient.newIncidentQuery().sort(s -> s.errorType().asc()).send().join();
    final var resultDesc =
        zeebeClient.newIncidentQuery().sort(s -> s.errorType().desc()).send().join();

    final var all = resultAsc.items().stream().map(Incident::getErrorType).toList();
    final var sortedAsc = all.stream().sorted().toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    assertThat(resultAsc.items().stream().map(Incident::getErrorType).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getErrorType).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortByProcessDefinitionKey() {
    final var resultAsc =
        zeebeClient.newIncidentQuery().sort(s -> s.processDefinitionKey().asc()).send().join();
    final var resultDesc =
        zeebeClient.newIncidentQuery().sort(s -> s.processDefinitionKey().desc()).send().join();

    final var all = resultAsc.items().stream().map(Incident::getProcessDefinitionKey).toList();
    final var sortedAsc = all.stream().sorted().toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    assertThat(resultAsc.items().stream().map(Incident::getProcessDefinitionKey).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getProcessDefinitionKey).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortByProcessInstanceKey() {
    final var resultAsc =
        zeebeClient.newIncidentQuery().sort(s -> s.processInstanceKey().asc()).send().join();
    final var resultDesc =
        zeebeClient.newIncidentQuery().sort(s -> s.processInstanceKey().desc()).send().join();

    final var all = resultAsc.items().stream().map(Incident::getProcessInstanceKey).toList();
    final var sortedAsc = all.stream().sorted().toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    assertThat(resultAsc.items().stream().map(Incident::getProcessInstanceKey).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getProcessInstanceKey).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortByTenantId() {
    final var resultAsc =
        zeebeClient.newIncidentQuery().sort(s -> s.tenantId().asc()).send().join();
    final var resultDesc =
        zeebeClient.newIncidentQuery().sort(s -> s.tenantId().desc()).send().join();

    final var all = resultAsc.items().stream().map(Incident::getTenantId).toList();
    final var sortedAsc = all.stream().sorted().toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    assertThat(resultAsc.items().stream().map(Incident::getTenantId).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getTenantId).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortByFlowNodeInstanceId() {
    final var resultAsc =
        zeebeClient.newIncidentQuery().sort(s -> s.flowNodeInstanceKey().asc()).send().join();
    final var resultDesc =
        zeebeClient.newIncidentQuery().sort(s -> s.flowNodeInstanceKey().desc()).send().join();

    final var all = resultAsc.items().stream().map(Incident::getFlowNodeInstanceKey).toList();
    final var sortedAsc = all.stream().sorted().toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    assertThat(resultAsc.items().stream().map(Incident::getFlowNodeInstanceKey).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getFlowNodeInstanceKey).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortByFlowNodeId() {
    final var resultAsc =
        zeebeClient.newIncidentQuery().sort(s -> s.flowNodeId().asc()).send().join();
    final var resultDesc =
        zeebeClient.newIncidentQuery().sort(s -> s.flowNodeId().desc()).send().join();

    final var all = resultAsc.items().stream().map(Incident::getFlowNodeId).toList();
    final var sortedAsc = all.stream().sorted().toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    assertThat(resultAsc.items().stream().map(Incident::getFlowNodeId).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getFlowNodeId).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortByState() {
    final var resultAsc = zeebeClient.newIncidentQuery().sort(s -> s.state().asc()).send().join();
    final var resultDesc = zeebeClient.newIncidentQuery().sort(s -> s.state().desc()).send().join();

    final var all = resultAsc.items().stream().map(Incident::getState).toList();
    final var sortedAsc = all.stream().sorted().toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    assertThat(resultAsc.items().stream().map(Incident::getState).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getState).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortByJobKey() {
    final var resultAsc = zeebeClient.newIncidentQuery().sort(s -> s.jobKey().asc()).send().join();
    final var resultDesc =
        zeebeClient.newIncidentQuery().sort(s -> s.jobKey().desc()).send().join();

    final var all = resultAsc.items().stream().map(Incident::getJobKey).toList();
    final var sortedAsc = all.stream().sorted().toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    assertThat(resultAsc.items().stream().map(Incident::getJobKey).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getJobKey).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortByCreationTime() {
    final var resultAsc =
        zeebeClient.newIncidentQuery().sort(s -> s.creationTime().asc()).send().join();
    final var resultDesc =
        zeebeClient.newIncidentQuery().sort(s -> s.creationTime().desc()).send().join();

    final var all = resultAsc.items().stream().map(Incident::getCreationTime).toList();
    final var sortedAsc = all.stream().sorted().toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    assertThat(resultAsc.items().stream().map(Incident::getCreationTime).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getCreationTime).toList())
        .containsExactlyElementsOf(sortedDesc);
  }
}
