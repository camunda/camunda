/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.client.QueryTest.assertSorted;
import static io.camunda.it.client.QueryTest.deployResource;
import static io.camunda.it.client.QueryTest.startProcessInstance;
import static io.camunda.it.client.QueryTest.waitForFlowNodeInstances;
import static io.camunda.it.client.QueryTest.waitForProcessInstancesToStart;
import static io.camunda.it.client.QueryTest.waitForProcessesToBeDeployed;
import static io.camunda.it.client.QueryTest.waitUntilFlowNodeInstanceHasIncidents;
import static io.camunda.it.client.QueryTest.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.ProblemException;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.search.response.FlowNodeInstance;
import io.camunda.zeebe.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.client.protocol.rest.LongFilterProperty;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@ZeebeIntegration
public class ProcessInstanceAndFlowNodeInstanceQueryTest {

  static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  static final List<ProcessInstanceEvent> PROCESS_INSTANCES = new ArrayList<>();

  private static ZeebeClient zeebeClient;

  @TestZeebe(initMethod = "initTestStandaloneCamunda")
  private static TestStandaloneCamunda testStandaloneCamunda;

  private static FlowNodeInstance flowNodeInstance;
  private static FlowNodeInstance flowNodeInstanceWithIncident;

  @SuppressWarnings("unused")
  static void initTestStandaloneCamunda() {
    testStandaloneCamunda = new TestStandaloneCamunda();
  }

  @BeforeAll
  public static void beforeAll() {

    zeebeClient = testStandaloneCamunda.newClientBuilder().build();

    final List<String> processes =
        List.of(
            "service_tasks_v1.bpmn",
            "service_tasks_v2.bpmn",
            "incident_process_v1.bpmn",
            "manual_process.bpmn",
            "parent_process_v1.bpmn",
            "child_process_v1.bpmn");
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(zeebeClient, String.format("process/%s", process)).getProcesses()));

    waitForProcessesToBeDeployed(zeebeClient, DEPLOYED_PROCESSES.size());

    PROCESS_INSTANCES.add(startProcessInstance(zeebeClient, "service_tasks_v1"));
    PROCESS_INSTANCES.add(startProcessInstance(zeebeClient, "service_tasks_v2", "{\"path\":222}"));
    PROCESS_INSTANCES.add(startProcessInstance(zeebeClient, "manual_process"));
    PROCESS_INSTANCES.add(startProcessInstance(zeebeClient, "incident_process_v1"));
    PROCESS_INSTANCES.add(startProcessInstance(zeebeClient, "parent_process_v1"));

    waitForProcessInstancesToStart(zeebeClient, 6);
    waitForFlowNodeInstances(zeebeClient, 20);
    waitUntilFlowNodeInstanceHasIncidents(zeebeClient, 1);
    waitUntilProcessInstanceHasIncidents(zeebeClient, 1);
    // store flow node instances for querying
    final var allFlowNodeInstances =
        zeebeClient
            .newFlownodeInstanceQuery()
            .page(p -> p.limit(100))
            .sort(s -> s.flowNodeId().asc())
            .send()
            .join()
            .items();
    flowNodeInstance = allFlowNodeInstances.getFirst();
    flowNodeInstanceWithIncident =
        allFlowNodeInstances.stream()
            .filter(f -> f.getIncidentKey() != null)
            .findFirst()
            .orElseThrow();
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
    PROCESS_INSTANCES.clear();
  }

  @Test
  void shouldGetProcessInstanceByKey() {
    // given
    final String bpmnProcessId = "service_tasks_v1";
    final ProcessInstanceEvent processInstanceEvent =
        PROCESS_INSTANCES.stream()
            .filter(p -> Objects.equals(bpmnProcessId, p.getBpmnProcessId()))
            .findFirst()
            .orElseThrow();
    final long processInstanceKey = processInstanceEvent.getProcessInstanceKey();
    final long processDefinitionKey = processInstanceEvent.getProcessDefinitionKey();

    // when
    final var result = zeebeClient.newProcessInstanceGetRequest(processInstanceKey).send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(result.getProcessDefinitionId()).isEqualTo(bpmnProcessId);
    assertThat(result.getProcessDefinitionName()).isEqualTo("Service tasks v1");
    assertThat(result.getProcessDefinitionVersion()).isEqualTo(1);
    assertThat(result.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(result.getTreePath()).isEqualTo("PI_" + processInstanceKey);
    assertThat(result.getStartDate()).isNotNull();
    assertThat(result.getEndDate()).isNull();
    assertThat(result.getState()).isEqualTo("ACTIVE");
    assertThat(result.getHasIncident()).isFalse();
    assertThat(result.getTenantId()).isEqualTo("<default>");
  }

  @Test
  void shouldThrownExceptionIfProcessInstanceNotFoundByKey() {
    // given
    final long invalidProcessInstanceKey = 100L;

    // when / then
    final var exception =
        assertThrowsExactly(
            ProblemException.class,
            () ->
                zeebeClient.newProcessInstanceGetRequest(invalidProcessInstanceKey).send().join());
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
    assertThat(exception.details().getDetail())
        .isEqualTo("Process instance with key 100 not found");
  }

  @Test
  void shouldQueryAllProcessInstancesByDefault() {
    // given
    final List<String> expectedBpmnProcessIds =
        new ArrayList<>(
            PROCESS_INSTANCES.stream().map(ProcessInstanceEvent::getBpmnProcessId).toList());
    expectedBpmnProcessIds.add("child_process_v1");

    // when
    final var result = zeebeClient.newProcessInstanceQuery().send().join();

    // then
    assertThat(result.page().totalItems()).isEqualTo(expectedBpmnProcessIds.size());
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrderElementsOf(expectedBpmnProcessIds);
  }

  @Test
  void shouldQueryProcessInstancesByKey() {
    // given
    final long processInstanceKey =
        PROCESS_INSTANCES.stream().findFirst().orElseThrow().getProcessInstanceKey();

    // when
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldQueryProcessInstancesByKeyFilterGtLt() {
    // given
    final long processInstanceKey =
        PROCESS_INSTANCES.stream().findFirst().orElseThrow().getProcessInstanceKey();
    final var longFilter = new LongFilterProperty();
    longFilter.set$Gt(processInstanceKey - 1);
    longFilter.set$Lt(processInstanceKey + 1);

    // when
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> f.processInstanceKey(longFilter))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldQueryProcessInstancesByKeyFilterGteLte() {
    // given
    final long processInstanceKey =
        PROCESS_INSTANCES.stream().findFirst().orElseThrow().getProcessInstanceKey();
    final var longFilter = new LongFilterProperty();
    longFilter.set$Gte(processInstanceKey);
    longFilter.set$Lte(processInstanceKey);

    // when
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> f.processInstanceKey(longFilter))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldQueryProcessInstancesByKeyFilterIn() {
    // given
    final List<Long> processInstanceKeys =
        PROCESS_INSTANCES.subList(0, 2).stream()
            .map(ProcessInstanceEvent::getProcessInstanceKey)
            .toList();
    final var longFilter = new LongFilterProperty();
    longFilter.set$In(processInstanceKeys);

    // when
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> f.processInstanceKey(longFilter))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items())
        .extracting("processInstanceKey")
        .containsExactlyInAnyOrderElementsOf(processInstanceKeys);
  }

  @Test
  void shouldQueryProcessInstancesByProcessDefinitionId() {
    // given
    final String bpmnProcessId = "service_tasks_v1";
    final long processInstanceKey =
        PROCESS_INSTANCES.stream()
            .filter(p -> Objects.equals(bpmnProcessId, p.getBpmnProcessId()))
            .findFirst()
            .orElseThrow()
            .getProcessInstanceKey();

    // when
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> f.processDefinitionId(bpmnProcessId))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldQueryProcessInstancesByProcessDefinitionKey() {
    // given
    final String bpmnProcessId = "service_tasks_v1";
    final ProcessInstanceEvent processInstanceEvent =
        PROCESS_INSTANCES.stream()
            .filter(p -> Objects.equals(bpmnProcessId, p.getBpmnProcessId()))
            .findFirst()
            .orElseThrow();
    final long processInstanceKey = processInstanceEvent.getProcessInstanceKey();
    final long processDefinitionKey = processInstanceEvent.getProcessDefinitionKey();

    // when
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> f.processDefinitionKey(processDefinitionKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldQueryProcessInstancesByStateActive() {
    // when
    final var result =
        zeebeClient.newProcessInstanceQuery().filter(f -> f.state("ACTIVE")).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrder("service_tasks_v1", "service_tasks_v2", "incident_process_v1");
  }

  @Test
  void shouldQueryProcessInstancesByStateCompleted() {
    // when
    final var result =
        zeebeClient.newProcessInstanceQuery().filter(f -> f.state("COMPLETED")).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrder("parent_process_v1", "child_process_v1", "manual_process");
  }

  @Test
  void shouldQueryProcessInstancesWithIncidents() {
    // when
    final var result =
        zeebeClient.newProcessInstanceQuery().filter(f -> f.hasIncident(true)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrder("incident_process_v1");
  }

  @Test
  void shouldQueryProcessInstancesByParentProcessInstanceKey() {
    // given
    final long parentProcessInstanceKey =
        PROCESS_INSTANCES.stream()
            .filter(p -> Objects.equals("parent_process_v1", p.getBpmnProcessId()))
            .findFirst()
            .orElseThrow()
            .getProcessInstanceKey();

    // when
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> f.parentProcessInstanceKey(parentProcessInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrder("child_process_v1");
  }

  @Test
  void shouldQueryProcessInstancesWithReverseSorting() {
    // given
    final List<String> expectedBpmnProcessIds =
        new ArrayList<>(
            PROCESS_INSTANCES.stream().map(ProcessInstanceEvent::getBpmnProcessId).toList());
    expectedBpmnProcessIds.add("child_process_v1");
    expectedBpmnProcessIds.sort(Comparator.reverseOrder());

    // when
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .sort(s -> s.processDefinitionId().desc())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(6);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyElementsOf(expectedBpmnProcessIds);
  }

  @Test
  void shouldSortProcessInstancesByProcessInstanceKey() {
    // when
    final var resultAsc =
        zeebeClient.newProcessInstanceQuery().sort(s -> s.processInstanceKey().asc()).send().join();
    final var resultDesc =
        zeebeClient
            .newProcessInstanceQuery()
            .sort(s -> s.processInstanceKey().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, ProcessInstance::getProcessInstanceKey);
  }

  @Test
  void shouldSortProcessInstancesByProcessDefinitionName() {
    // when
    final var resultAsc =
        zeebeClient
            .newProcessInstanceQuery()
            .sort(s -> s.processDefinitionName().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newProcessInstanceQuery()
            .sort(s -> s.processDefinitionName().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, ProcessInstance::getProcessDefinitionName);
  }

  @Test
  void shouldSortProcessInstancesByProcessDefinitionKey() {
    // when
    final var resultAsc =
        zeebeClient
            .newProcessInstanceQuery()
            .sort(s -> s.processDefinitionKey().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newProcessInstanceQuery()
            .sort(s -> s.processDefinitionKey().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, ProcessInstance::getProcessDefinitionKey);
  }

  @Test
  void shouldSortProcessInstancesByParentProcessInstanceKey() {
    // when
    final var resultAsc =
        zeebeClient
            .newProcessInstanceQuery()
            .sort(s -> s.parentProcessInstanceKey().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newProcessInstanceQuery()
            .sort(s -> s.parentFlowNodeInstanceKey().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, ProcessInstance::getParentProcessInstanceKey);
  }

  @Test
  void shouldSortProcessInstancesByStartDate() {
    // when
    final var resultAsc =
        zeebeClient.newProcessInstanceQuery().sort(s -> s.startDate().asc()).send().join();
    final var resultDesc =
        zeebeClient.newProcessInstanceQuery().sort(s -> s.startDate().desc()).send().join();

    assertSorted(resultAsc, resultDesc, ProcessInstance::getStartDate);
  }

  @Test
  void shouldSortProcessInstancesByState() {
    // when
    final var resultAsc =
        zeebeClient.newProcessInstanceQuery().sort(s -> s.state().asc()).send().join();
    final var resultDesc =
        zeebeClient.newProcessInstanceQuery().sort(s -> s.state().desc()).send().join();

    assertSorted(resultAsc, resultDesc, ProcessInstance::getState);
  }

  @Test
  public void shouldValidateProcessInstancePagination() {
    final var result = zeebeClient.newProcessInstanceQuery().page(p -> p.limit(2)).send().join();
    assertThat(result.items().size()).isEqualTo(2);
    final var key = result.items().getFirst().getProcessInstanceKey();
    // apply searchAfter
    final var resultAfter =
        zeebeClient
            .newProcessInstanceQuery()
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(5);
    final var keyAfter = resultAfter.items().getFirst().getProcessInstanceKey();
    // apply searchBefore
    final var resultBefore =
        zeebeClient
            .newProcessInstanceQuery()
            .page(p -> p.searchBefore(Collections.singletonList(keyAfter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(resultBefore.items().getFirst().getProcessInstanceKey()).isEqualTo(key);
  }

  @Test
  public void shouldValidateFlowNodeInstancePagination() {
    final var result = zeebeClient.newFlownodeInstanceQuery().page(p -> p.limit(2)).send().join();
    assertThat(result.items().size()).isEqualTo(2);
    final var key = result.items().getFirst().getFlowNodeInstanceKey();
    // apply searchAfter
    final var resultAfter =
        zeebeClient
            .newFlownodeInstanceQuery()
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(19);
    final var keyAfter = resultAfter.items().getFirst().getFlowNodeInstanceKey();
    // apply searchBefore
    final var resultBefore =
        zeebeClient
            .newFlownodeInstanceQuery()
            .page(p -> p.searchBefore(Collections.singletonList(keyAfter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(resultBefore.items().getFirst().getFlowNodeInstanceKey()).isEqualTo(key);
  }

  @Test
  void shouldQueryFlowNodeInstanceByFlowNodeInstanceKey() {
    // given
    final var key = flowNodeInstance.getFlowNodeInstanceKey();
    // when
    final var result =
        zeebeClient
            .newFlownodeInstanceQuery()
            .filter(f -> f.flowNodeInstanceKey(key))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst()).isEqualTo(flowNodeInstance);
  }

  @Test
  void shouldSortFlowNodeInstanceByFlowNodeInstanceKey() {
    // when
    final var resultAsc =
        zeebeClient
            .newFlownodeInstanceQuery()
            .sort(s -> s.flowNodeInstanceKey().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newFlownodeInstanceQuery()
            .sort(s -> s.flowNodeInstanceKey().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, FlowNodeInstance::getFlowNodeInstanceKey);
  }

  @Test
  void shouldQueryFlowNodeInstanceQueryByProcessInstanceKey() {
    // given
    final var processInstanceKey = flowNodeInstance.getProcessInstanceKey();
    // when
    final var result =
        zeebeClient
            .newFlownodeInstanceQuery()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(5);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(result.items().get(1).getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldSortFlowNodeInstanceByProcessInstanceKey() {
    // when
    final var resultAsc =
        zeebeClient
            .newFlownodeInstanceQuery()
            .sort(s -> s.processInstanceKey().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newFlownodeInstanceQuery()
            .sort(s -> s.processInstanceKey().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, FlowNodeInstance::getProcessInstanceKey);
  }

  @Test
  void shouldGetFlowNodeInstanceByKey() {
    // given
    final var flowNodeInstanceKey = flowNodeInstance.getFlowNodeInstanceKey();

    // when
    final var result = zeebeClient.newFlowNodeInstanceGetRequest(flowNodeInstanceKey).send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(flowNodeInstance);
  }

  @Test
  void shouldQueryFlowNodeInstanceByFlowNodeId() {
    // given
    final var flowNodeId = flowNodeInstance.getFlowNodeId();
    // when
    final var result =
        zeebeClient.newFlownodeInstanceQuery().filter(f -> f.flowNodeId(flowNodeId)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getFlowNodeId()).isEqualTo(flowNodeId);
    assertThat(result.items().get(0).getFlowNodeId()).isEqualTo(flowNodeId);
  }

  @Test
  void shouldSortFlowNodeInstanceByFlowNodeId() {
    // when
    final var resultAsc =
        zeebeClient.newFlownodeInstanceQuery().sort(s -> s.flowNodeId().asc()).send().join();
    final var resultDesc =
        zeebeClient.newFlownodeInstanceQuery().sort(s -> s.flowNodeId().desc()).send().join();
    assertSorted(resultAsc, resultDesc, FlowNodeInstance::getFlowNodeId);
  }

  @Test
  void shouldQueryFlowNodeInstanceByProcessDefinitionKey() {
    // given
    final var processDefinitionKey = flowNodeInstance.getProcessDefinitionKey();
    // when
    final var result =
        zeebeClient
            .newFlownodeInstanceQuery()
            .filter(f -> f.processDefinitionKey(processDefinitionKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(5);
    assertThat(result.items().getFirst().getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
  }

  @Test
  void shouldSortFlowNodeInstanceByProcessDefinitionKey() {
    // when
    final var resultAsc =
        zeebeClient
            .newFlownodeInstanceQuery()
            .sort(s -> s.processDefinitionKey().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newFlownodeInstanceQuery()
            .sort(s -> s.processDefinitionKey().desc())
            .send()
            .join();
    assertSorted(resultAsc, resultDesc, FlowNodeInstance::getProcessDefinitionKey);
  }

  @Test
  void shouldQueryFlowNodeInstanceByIncidentKey() {
    // given
    final var incidentKey = flowNodeInstanceWithIncident.getIncidentKey();
    // when
    final var result =
        zeebeClient
            .newFlownodeInstanceQuery()
            .filter(f -> f.incidentKey(incidentKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getIncidentKey()).isEqualTo(incidentKey);
  }

  @Test
  void shouldSortFlowNodeInstanceByIncidentKey() {
    // when
    final var resultAsc =
        zeebeClient.newFlownodeInstanceQuery().sort(s -> s.incidentKey().asc()).send().join();
    final var resultDesc =
        zeebeClient.newFlownodeInstanceQuery().sort(s -> s.incidentKey().desc()).send().join();

    assertSorted(resultAsc, resultDesc, FlowNodeInstance::getIncidentKey);
  }

  @Test
  void shouldQueryFlowNodeInstanceByState() {
    // given
    final var state = flowNodeInstance.getState();
    // when
    final var result =
        zeebeClient.newFlownodeInstanceQuery().filter(f -> f.state(state)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(17);
    assertThat(result.items().getFirst().getState()).isEqualTo(state);
  }

  @Test
  void shouldSortFlowNodeInstanceByState() {
    // when
    final var resultAsc =
        zeebeClient.newFlownodeInstanceQuery().sort(s -> s.state().asc()).send().join();
    final var resultDesc =
        zeebeClient.newFlownodeInstanceQuery().sort(s -> s.state().desc()).send().join();

    assertSorted(resultAsc, resultDesc, FlowNodeInstance::getState);
  }

  @Test
  void shouldQueryFlowNodeInstanceByIncident() {
    // given
    final var hasIncident = flowNodeInstanceWithIncident.getIncident();
    // when
    final var result =
        zeebeClient
            .newFlownodeInstanceQuery()
            .filter(f -> f.hasIncident(hasIncident))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getIncident()).isEqualTo(hasIncident);
  }

  @Test
  void shouldQueryFlowNodeInstanceByType() {
    // given
    final var type = flowNodeInstance.getType();
    // when
    final var result =
        zeebeClient.newFlownodeInstanceQuery().filter(f -> f.type(type)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().getFirst().getType()).isEqualTo(type);
    assertThat(result.items().get(1).getType()).isEqualTo(type);
    assertThat(result.items().get(2).getType()).isEqualTo(type);
  }

  @Test
  void shouldSortFlowNodeInstanceByType() {
    // when
    final var resultAsc =
        zeebeClient
            .newFlownodeInstanceQuery()
            .page(p -> p.limit(100))
            .sort(s -> s.type().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newFlownodeInstanceQuery()
            .page(p -> p.limit(100))
            .sort(s -> s.type().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, FlowNodeInstance::getType);
  }

  @Test
  void shouldQueryFlowNodeInstanceByTreePath() {
    // given
    final var treePath = flowNodeInstance.getTreePath();
    // when
    final var result =
        zeebeClient.newFlownodeInstanceQuery().filter(f -> f.treePath(treePath)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getTreePath()).isEqualTo(treePath);
  }

  @Test
  void shouldQueryFlowNodeInstanceByTenantId() {
    // given
    final var tenantId = flowNodeInstance.getTenantId();
    // when
    final var result =
        zeebeClient.newFlownodeInstanceQuery().filter(f -> f.tenantId(tenantId)).send().join();

    // then
    assertThat(result.page().totalItems()).isEqualTo(20);
    assertThat(result.items()).allMatch(f -> f.getTenantId().equals(tenantId));
  }

  @Test
  public void shouldQueryFlowNodeInstanceValidatePagination() {
    final var result = zeebeClient.newFlownodeInstanceQuery().page(p -> p.limit(2)).send().join();
    assertThat(result.items().size()).isEqualTo(2);
    final var key = result.items().getFirst().getFlowNodeInstanceKey();
    // apply searchAfter
    final var resultAfter =
        zeebeClient
            .newFlownodeInstanceQuery()
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(19);
    final var keyAfter = resultAfter.items().getFirst().getFlowNodeInstanceKey();
    // apply searchBefore
    final var resultBefore =
        zeebeClient
            .newFlownodeInstanceQuery()
            .page(p -> p.searchBefore(Collections.singletonList(keyAfter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(resultBefore.items().getFirst().getFlowNodeInstanceKey()).isEqualTo(key);
  }
}
