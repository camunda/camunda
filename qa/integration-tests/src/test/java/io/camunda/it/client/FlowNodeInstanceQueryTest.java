/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.qa.util.cluster.TestStandaloneCamunda;
import io.camunda.zeebe.client.ZeebeClient;
import io.camunda.zeebe.client.api.command.CreateProcessInstanceCommandStep1;
import io.camunda.zeebe.client.api.response.DeploymentEvent;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.response.ProcessInstanceEvent;
import io.camunda.zeebe.client.api.search.response.FlowNodeInstance;
import io.camunda.zeebe.client.api.search.response.SearchQueryResponse;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ZeebeIntegration
public class FlowNodeInstanceQueryTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(FlowNodeInstanceQueryTest.class);
  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  private static final List<ProcessInstanceEvent> PROCESS_INSTANCES = new ArrayList<>();

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

    final var processes =
        List.of("service_tasks_v1.bpmn", "service_tasks_v2.bpmn", "incident_process_v1.bpmn");
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(String.format("process/%s", process)).getProcesses()));

    waitForProcessesToBeDeployed();

    PROCESS_INSTANCES.add(startProcessInstance("service_tasks_v1"));
    PROCESS_INSTANCES.add(startProcessInstance("service_tasks_v2", "{\"path\":222}"));
    PROCESS_INSTANCES.add(startProcessInstance("incident_process_v1"));
    waitForProcessInstancesToStart(PROCESS_INSTANCES.size());
    waitForFlowNodeInstances(7);

    // store a flow node instance for querying
    flowNodeInstance = zeebeClient.newFlownodeInstanceQuery().send().join().items().getFirst();
    flowNodeInstanceWithIncident =
        zeebeClient.newFlownodeInstanceQuery().send().join().items().stream()
            .filter(f -> f.getIncidentKey() != null)
            .findFirst()
            .orElse(null);
  }

  private static void waitForProcessesToBeDeployed() {
    Awaitility.await("should deploy processes and import in Operate")
        .atMost(Duration.ofSeconds(15))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = zeebeClient.newProcessDefinitionQuery().send().join();
              assertThat(result.items().size()).isEqualTo(DEPLOYED_PROCESSES.size());
            });
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
    PROCESS_INSTANCES.clear();
  }

  @Test
  void shouldGetFlowNodeInstanceByKey() {
    // given
    final var flowNodeInstanceKey = flowNodeInstance.getFlowNodeInstanceKey();

    // when
    final var result = zeebeClient.newFlowNodeInstanceGetRequest(flowNodeInstanceKey).send().join();

    // then
    assertThat(result).isNotNull();
  }

  @Test
  void shouldQueryByFlowNodeInstanceKey() {
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
  void shouldSortByFlowNodeInstanceKey() {
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
  void shouldQueryByProcessInstanceKey() {
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
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(result.items().get(1).getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldSortByProcessInstanceKey() {
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
  void shouldQueryByFlowNodeId() {
    // given
    final var flowNodeId = flowNodeInstance.getFlowNodeId();
    // when
    final var result =
        zeebeClient.newFlownodeInstanceQuery().filter(f -> f.flowNodeId(flowNodeId)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().getFirst().getFlowNodeId()).isEqualTo(flowNodeId);
    assertThat(result.items().get(1).getFlowNodeId()).isEqualTo(flowNodeId);
  }

  @Test
  void shouldSortByFlowNodeId() {
    // when
    final var resultAsc =
        zeebeClient.newFlownodeInstanceQuery().sort(s -> s.flowNodeId().asc()).send().join();
    final var resultDesc =
        zeebeClient.newFlownodeInstanceQuery().sort(s -> s.flowNodeId().desc()).send().join();

    final var all = resultAsc.items().stream().map(FlowNodeInstance::getFlowNodeId).toList();
    final var sortedAsc =
        all.stream().sorted(Comparator.nullsLast(Comparator.naturalOrder())).toList();
    final var sortedDesc =
        all.stream().sorted(Comparator.nullsLast(Comparator.reverseOrder())).toList();

    // then
    assertThat(resultAsc.items().stream().map(FlowNodeInstance::getFlowNodeId).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(FlowNodeInstance::getFlowNodeId).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldQueryByProcessDefinitionKey() {
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
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().getFirst().getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
  }

  @Test
  void shouldSortByProcessDefinitionKey() {
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

  private static <T extends Comparable<T>> void assertSorted(
      final SearchQueryResponse<FlowNodeInstance> resultAsc,
      final SearchQueryResponse<FlowNodeInstance> resultDesc,
      final Function<FlowNodeInstance, T> propertyExtractor) {
    final var all = resultAsc.items().stream().map(propertyExtractor).toList();
    final var sortedAsc =
        all.stream().sorted(Comparator.nullsLast(Comparator.naturalOrder())).toList();
    final var sortedDesc =
        all.stream().sorted(Comparator.nullsLast(Comparator.reverseOrder())).toList();

    // then
    assertThat(resultAsc.items().stream().map(propertyExtractor).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(propertyExtractor).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldQueryByIncidentKey() {
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
  void shouldSortByIncidentKey() {
    // when
    final var resultAsc =
        zeebeClient.newFlownodeInstanceQuery().sort(s -> s.incidentKey().asc()).send().join();
    final var resultDesc =
        zeebeClient.newFlownodeInstanceQuery().sort(s -> s.incidentKey().desc()).send().join();

    assertSorted(resultAsc, resultDesc, FlowNodeInstance::getIncidentKey);
  }

  @Test
  void shouldQueryByState() {
    // given
    final var state = flowNodeInstance.getState();
    // when
    final var result =
        zeebeClient.newFlownodeInstanceQuery().filter(f -> f.state(state)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(4);
    assertThat(result.items().getFirst().getState()).isEqualTo(state);
  }

  @Test
  void shouldSortByState() {
    // when
    final var resultAsc =
        zeebeClient.newFlownodeInstanceQuery().sort(s -> s.state().asc()).send().join();
    final var resultDesc =
        zeebeClient.newFlownodeInstanceQuery().sort(s -> s.state().desc()).send().join();

    assertSorted(resultAsc, resultDesc, FlowNodeInstance::getState);
  }

  @Test
  void shouldQueryByIncident() {
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
    assertThat(result.items().size()).isEqualTo(7);
    assertThat(result.items().getFirst().getIncident()).isEqualTo(hasIncident);
  }

  @Test
  void shouldQueryByType() {
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
  void shouldSortByType() {
    // when
    final var resultAsc =
        zeebeClient.newFlownodeInstanceQuery().sort(s -> s.type().asc()).send().join();
    final var resultDesc =
        zeebeClient.newFlownodeInstanceQuery().sort(s -> s.type().desc()).send().join();

    assertSorted(resultAsc, resultDesc, FlowNodeInstance::getType);
  }

  @Test
  void shouldQueryByTreePath() {
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
  void shouldQueryByTenantId() {
    // given
    final var tenantId = flowNodeInstance.getTenantId();
    // when
    final var result =
        zeebeClient.newFlownodeInstanceQuery().filter(f -> f.tenantId(tenantId)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(7);
    assertThat(result.items()).allMatch(f -> f.getTenantId().equals(tenantId));
  }

  @Test
  public void shouldValidatePagination() {
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

    assertThat(resultAfter.items().size()).isEqualTo(6);
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

  private static void waitForProcessInstancesToStart(final int expectedProcessInstances) {
    Awaitility.await("should start process instances and import in Operate")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = zeebeClient.newProcessInstanceQuery().send().join();
              assertThat(result.items().size()).isEqualTo(expectedProcessInstances);
            });
  }

  private static void waitForFlowNodeInstances(final int expectedFlowNodeInstances) {
    Awaitility.await("should wait until flow node instances are available")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result = zeebeClient.newFlownodeInstanceQuery().send().join();
              assertThat(result.items().size()).isEqualTo(expectedFlowNodeInstances);
            });
  }

  private static DeploymentEvent deployResource(final String resourceName) {
    return zeebeClient
        .newDeployResourceCommand()
        .addResourceFromClasspath(resourceName)
        .send()
        .join();
  }

  private static ProcessInstanceEvent startProcessInstance(final String bpmnProcessId) {
    return startProcessInstance(bpmnProcessId, null);
  }

  private static ProcessInstanceEvent startProcessInstance(
      final String bpmnProcessId, final String payload) {
    final CreateProcessInstanceCommandStep1.CreateProcessInstanceCommandStep3
        createProcessInstanceCommandStep3 =
            zeebeClient.newCreateInstanceCommand().bpmnProcessId(bpmnProcessId).latestVersion();
    if (payload != null) {
      createProcessInstanceCommandStep3.variables(payload);
    }
    final ProcessInstanceEvent processInstanceEvent =
        createProcessInstanceCommandStep3.send().join();
    LOGGER.info("Process instance started for process [{}]", bpmnProcessId);
    return processInstanceEvent;
  }
}
