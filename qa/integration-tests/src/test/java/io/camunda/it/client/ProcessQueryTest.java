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
import io.camunda.zeebe.client.api.response.*;
import io.camunda.zeebe.client.api.response.Process;
import io.camunda.zeebe.client.api.search.filter.ProcessInstanceFilter;
import io.camunda.zeebe.client.api.search.response.ProcessInstance;
import io.camunda.zeebe.client.protocol.rest.ProcessInstanceVariableFilterRequest;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration;
import io.camunda.zeebe.qa.util.junit.ZeebeIntegration.TestZeebe;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ZeebeIntegration
public class ProcessQueryTest {

  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessQueryTest.class);

  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  private static final List<ProcessInstanceEvent> PROCESS_INSTANCES = new ArrayList<>();

  @TestZeebe
  private static TestStandaloneCamunda testStandaloneCamunda = new TestStandaloneCamunda();

  private static ZeebeClient zeebeClient;

  @BeforeAll
  public static void setup() throws InterruptedException {

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
                deployResource(String.format("process/%s", process)).getProcesses()));
				
    waitForProcessesToBeDeployed();

    PROCESS_INSTANCES.add(startProcessInstance("service_tasks_v1"));
    PROCESS_INSTANCES.add(startProcessInstance("service_tasks_v2", "{\"path\":222}"));
    PROCESS_INSTANCES.add(startProcessInstance("manual_process"));
    PROCESS_INSTANCES.add(startProcessInstance("incident_process_v1"));
    PROCESS_INSTANCES.add(startProcessInstance("parent_process_v1"));

    waitForProcessInstancesToStart();
    waitForProcessInstancesToExecute();
  }

  @Test
  void shouldRetrieveAllProcessInstances() {
    // given
    final List<String> expectedBpmnProcessIds =
        new ArrayList<>(
            PROCESS_INSTANCES.stream().map(ProcessInstanceEvent::getBpmnProcessId).toList());
    expectedBpmnProcessIds.add("child_process_v1");

    // when
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(expectedBpmnProcessIds.size());
    assertThat(result.items().stream().map(ProcessInstance::getBpmnProcessId).toList())
        .containsExactlyInAnyOrderElementsOf(expectedBpmnProcessIds);
  }

  @Test
  void shouldRetrieveProcessInstancesByBpmnProcessId() {
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
            .filter(f -> matchAllProcessInstancesFilter(f).bpmnProcessId(bpmnProcessId))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().get(0).getKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldRetrieveProcessInstancesByStateRunning() {
    // when
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> f.running(true).active(true).incidents(true))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().stream().map(ProcessInstance::getBpmnProcessId).toList())
        .containsExactlyInAnyOrder("service_tasks_v1", "service_tasks_v2", "incident_process_v1");
  }

  @Test
  void shouldRetrieveProcessInstancesByVariable() {
    // given
    final ProcessInstanceVariableFilterRequest variableFilter =
        new ProcessInstanceVariableFilterRequest().name("path").addValuesItem("222");

    // when
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f).variable(variableFilter))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().stream().map(ProcessInstance::getBpmnProcessId).toList())
        .containsExactlyInAnyOrder("service_tasks_v2");
  }

  @Test
  void shouldRetrieveNoProcessInstanceOnVariableValueMismatch() {
    // given
    final ProcessInstanceVariableFilterRequest variableFilter =
        new ProcessInstanceVariableFilterRequest().name("path").addValuesItem("123");

    // when
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f).variable(variableFilter))
            .send()
            .join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldRetrieveNoProcessInstancesWhenNoFilter() {
    // when
    final var result = zeebeClient.newProcessInstanceQuery().send().join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldRetrieveProcessInstancesByStateIncidents() {
    // when
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> f.running(true).incidents(true))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().stream().map(ProcessInstance::getBpmnProcessId).toList())
        .containsExactlyInAnyOrder("incident_process_v1");
  }

  @Test
  void shouldRetrieveProcessInstancesByStateCompleted() {
    // when
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> f.completed(true).finished(true))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().stream().map(ProcessInstance::getBpmnProcessId).toList())
        .containsExactlyInAnyOrder("parent_process_v1", "child_process_v1", "manual_process");
  }

  @Test
  void shouldRetrieveProcessInstancesByParentProcessInstanceKey() {
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
            .filter(
                f ->
                    matchAllProcessInstancesFilter(f)
                        .parentProcessInstanceKey(parentProcessInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().stream().map(ProcessInstance::getBpmnProcessId).toList())
        .containsExactlyInAnyOrder("child_process_v1");
  }

  @Test
  void shouldRetrieveProcessInstancesWithReverseSorting() {
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
            .filter(f -> matchAllProcessInstancesFilter(f))
            .sort(s -> s.bpmnProcessId().desc())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(6);
    assertThat(result.items().stream().map(ProcessInstance::getBpmnProcessId).toList())
        .containsExactlyElementsOf(expectedBpmnProcessIds);
  }

  @Test
  void shouldSortProcessInstancesByProcessInstanceKey() {
    // when
    final var resultAsc =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .sort(s -> s.key().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .sort(s -> s.key().desc())
            .send()
            .join();

    final List<Long> allProcessInstanceKeys =
        resultAsc.items().stream().map(ProcessInstance::getKey).toList();
    final List<Long> sortedAsc =
        allProcessInstanceKeys.stream().sorted(Comparator.naturalOrder()).toList();
    final List<Long> sortedDesc =
        allProcessInstanceKeys.stream().sorted(Comparator.reverseOrder()).toList();

    // then
    assertThat(resultAsc.items().stream().map(ProcessInstance::getKey).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(ProcessInstance::getKey).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortProcessInstancesByProcessName() {
    // when
    final var resultAsc =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .sort(s -> s.processName().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .sort(s -> s.processName().desc())
            .send()
            .join();

    final List<String> allProcessNames =
        resultAsc.items().stream().map(ProcessInstance::getProcessName).toList();
    final List<String> sortedAsc =
        allProcessNames.stream().sorted(Comparator.naturalOrder()).toList();
    final List<String> sortedDesc =
        allProcessNames.stream().sorted(Comparator.reverseOrder()).toList();

    // then
    assertThat(resultAsc.items().stream().map(ProcessInstance::getProcessName).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(ProcessInstance::getProcessName).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortProcessInstancesByProcessDefinitionKey() {
    // when
    final var resultAsc =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .sort(s -> s.processDefinitionKey().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .sort(s -> s.processDefinitionKey().desc())
            .send()
            .join();

    final List<Long> allProcessDefinitionKeys =
        resultAsc.items().stream().map(ProcessInstance::getProcessDefinitionKey).toList();
    final List<Long> sortedAsc =
        allProcessDefinitionKeys.stream().sorted(Comparator.naturalOrder()).toList();
    final List<Long> sortedDesc =
        allProcessDefinitionKeys.stream().sorted(Comparator.reverseOrder()).toList();

    // then
    assertThat(resultAsc.items().stream().map(ProcessInstance::getProcessDefinitionKey).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(ProcessInstance::getProcessDefinitionKey).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortProcessInstancesByParentProcessInstanceKey() {
    // when
    final var resultAsc =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .sort(s -> s.parentProcessInstanceKey().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .sort(s -> s.parentFlowNodeInstanceKey().desc())
            .send()
            .join();

    final List<Long> allParentProcessInstanceKeys =
        resultAsc.items().stream().map(ProcessInstance::getParentProcessInstanceKey).toList();
    final List<Long> sortedAsc =
        allParentProcessInstanceKeys.stream()
            .sorted(Comparator.nullsLast(Comparator.naturalOrder()))
            .toList();
    final List<Long> sortedDesc =
        allParentProcessInstanceKeys.stream()
            .sorted(Comparator.nullsLast(Comparator.reverseOrder()))
            .toList();

    // then
    assertThat(
            resultAsc.items().stream().map(ProcessInstance::getParentProcessInstanceKey).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(
            resultDesc.items().stream().map(ProcessInstance::getParentProcessInstanceKey).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortProcessInstancesByStartDate() {
    // when
    final var resultAsc =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .sort(s -> s.startDate().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .sort(s -> s.startDate().desc())
            .send()
            .join();

    final List<String> allStartDates =
        resultAsc.items().stream().map(ProcessInstance::getStartDate).toList();
    final List<String> sortedAsc =
        allStartDates.stream().sorted(Comparator.naturalOrder()).toList();
    final List<String> sortedDesc =
        allStartDates.stream().sorted(Comparator.reverseOrder()).toList();

    // then
    assertThat(resultAsc.items().stream().map(ProcessInstance::getStartDate).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(ProcessInstance::getStartDate).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortProcessInstancesByState() {
    // when
    final var resultAsc =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .sort(s -> s.state().asc())
            .send()
            .join();
    final var resultDesc =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .sort(s -> s.state().desc())
            .send()
            .join();

    final List<String> allStates =
        resultAsc.items().stream().map(ProcessInstance::getState).toList();
    final List<String> sortedAsc = allStates.stream().sorted(Comparator.naturalOrder()).toList();
    final List<String> sortedDesc = allStates.stream().sorted(Comparator.reverseOrder()).toList();

    // then
    assertThat(resultAsc.items().stream().map(ProcessInstance::getState).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(ProcessInstance::getState).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  public void shouldValidatePagination() {
    final var result =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .page(p -> p.limit(2))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
    final var key = result.items().getFirst().getKey();
    // apply searchAfter
    final var resultAfter =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(5);
    final var keyAfter = resultAfter.items().getFirst().getKey();
    // apply searchBefore
    final var resultBefore =
        zeebeClient
            .newProcessInstanceQuery()
            .filter(f -> matchAllProcessInstancesFilter(f))
            .page(p -> p.searchBefore(Collections.singletonList(keyAfter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(resultBefore.items().getFirst().getKey()).isEqualTo(key);
  }

  private static void waitForProcessesToBeDeployed() throws InterruptedException {
    // Waiting here should be done with zeebeClient.newProcessQuery() but it is not implemented yet
    Thread.sleep(15000);
  }

  private static void waitForProcessInstancesToStart() {
    Awaitility.await("should start process instances and import in Operate")
        .atMost(Duration.ofSeconds(60))
        .ignoreExceptions() // Ignore exceptions and continue retrying
        .untilAsserted(
            () -> {
              final var result =
                  zeebeClient
                      .newProcessInstanceQuery()
                      .filter(f -> matchAllProcessInstancesFilter(f))
                      .send()
                      .join();
              assertThat(result.items().size()).isEqualTo(6);
            });
  }

  private static void waitForProcessInstancesToExecute() throws InterruptedException {
    Thread.sleep(15000);
  }

  private static ProcessInstanceFilter matchAllProcessInstancesFilter(ProcessInstanceFilter f) {
    return f.running(true)
        .active(true)
        .incidents(true)
        .finished(true)
        .completed(true)
        .canceled(true);
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
    LOGGER.debug("Process instance started for process [{}]", bpmnProcessId);
    return processInstanceEvent;
  }
}
