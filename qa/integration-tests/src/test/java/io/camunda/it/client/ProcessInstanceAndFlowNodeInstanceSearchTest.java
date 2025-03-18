/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.client.api.search.enums.ProcessInstanceState.ACTIVE;
import static io.camunda.client.api.search.enums.ProcessInstanceState.COMPLETED;
import static io.camunda.it.util.TestHelper.assertSorted;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForFlowNodeInstances;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilFlowNodeInstanceHasIncidents;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.FlowNodeInstanceFilter;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.FlowNodeInstance;
import io.camunda.client.api.search.response.FlowNodeInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.impl.ResponseMapper;
import io.camunda.client.protocol.rest.ProcessInstanceStateEnum;
import io.camunda.client.protocol.rest.ProcessInstanceVariableFilterRequest;
import io.camunda.client.protocol.rest.StringFilterProperty;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class ProcessInstanceAndFlowNodeInstanceSearchTest {

  public static final String EXPECTED_ERROR =
      "Expected result of the expression 'retriesA' to be 'NUMBER', but was 'STRING'.";
  static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  static final List<ProcessInstanceEvent> PROCESS_INSTANCES = new ArrayList<>();
  private static long processInstanceWithIncidentKey;
  private static FlowNodeInstance flowNodeInstance;
  private static FlowNodeInstance flowNodeInstanceWithIncident;
  private static CamundaClient camundaClient;

  @BeforeAll
  public static void beforeAll() {
    Objects.requireNonNull(camundaClient);
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
                deployResource(camundaClient, String.format("process/%s", process))
                    .getProcesses()));

    waitForProcessesToBeDeployed(camundaClient, DEPLOYED_PROCESSES.size());

    PROCESS_INSTANCES.add(
        startProcessInstance(camundaClient, "service_tasks_v1", "{\"xyz\":\"foo\"}"));
    PROCESS_INSTANCES.add(
        startProcessInstance(
            camundaClient, "service_tasks_v1", "{\"xyz\": \"bar\",\"abc\": \"mnp\"}"));
    PROCESS_INSTANCES.add(
        startProcessInstance(camundaClient, "service_tasks_v2", "{\"path\":222}"));
    PROCESS_INSTANCES.add(startProcessInstance(camundaClient, "manual_process"));
    final ProcessInstanceEvent processInstanceWithIncident =
        startProcessInstance(camundaClient, "incident_process_v1");
    processInstanceWithIncidentKey = processInstanceWithIncident.getProcessInstanceKey();
    PROCESS_INSTANCES.add(processInstanceWithIncident);
    PROCESS_INSTANCES.add(startProcessInstance(camundaClient, "parent_process_v1"));

    waitForProcessInstancesToStart(camundaClient, 7);
    waitForFlowNodeInstances(camundaClient, 22);
    waitUntilFlowNodeInstanceHasIncidents(camundaClient, 1);
    waitUntilProcessInstanceHasIncidents(camundaClient, 1);
    // store flow node instances for querying
    final var allFlowNodeInstances =
        camundaClient
            .newFlownodeInstanceSearchRequest()
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
    final var result = camundaClient.newProcessInstanceGetRequest(processInstanceKey).send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(result.getProcessDefinitionId()).isEqualTo(bpmnProcessId);
    assertThat(result.getProcessDefinitionName()).isEqualTo("Service tasks v1");
    assertThat(result.getProcessDefinitionVersion()).isEqualTo(1);
    assertThat(result.getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
    assertThat(result.getStartDate()).isNotNull();
    assertThat(result.getEndDate()).isNull();
    assertThat(result.getState()).isEqualTo(ACTIVE);
    assertThat(result.getHasIncident()).isFalse();
    assertThat(result.getTenantId()).isEqualTo("<default>");
  }

  @Test
  void testProcessInstanceWithIncident() {
    // when
    final var result =
        camundaClient.newProcessInstanceGetRequest(processInstanceWithIncidentKey).send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getState()).isEqualTo(ACTIVE);
    assertThat(result.getHasIncident()).isTrue();
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
                camundaClient
                    .newProcessInstanceGetRequest(invalidProcessInstanceKey)
                    .send()
                    .join());
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
    final var result = camundaClient.newProcessInstanceSearchRequest().send().join();

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
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey))
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

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processInstanceKey(b -> b.in(processInstanceKeys)))
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
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionId(b -> b.eq(bpmnProcessId)))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldQueryProcessInstancesByProcessDefinitionIdFilterIn() {
    // given
    final String bpmnProcessId = "service_tasks_v1";
    final Set<Long> processInstanceKeys =
        PROCESS_INSTANCES.stream()
            .filter(p -> Objects.equals(bpmnProcessId, p.getBpmnProcessId()))
            .map(ProcessInstanceEvent::getProcessInstanceKey)
            .collect(Collectors.toSet());

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionId(b -> b.in("not-found", bpmnProcessId)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(2);
    assertThat(result.items())
        .extracting("processInstanceKey")
        .containsExactlyInAnyOrderElementsOf(processInstanceKeys);
  }

  @Test
  void shouldRetrieveProcessInstancesByProcessDefinitionIdFilterLikeMultiple() {
    // given
    final String bpmnProcessId = "service_tasks";
    final List<Long> processInstanceKeys =
        PROCESS_INSTANCES.stream()
            .filter(p -> p.getBpmnProcessId().startsWith(bpmnProcessId))
            .map(ProcessInstanceEvent::getProcessInstanceKey)
            .toList();

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionId(b -> b.like(bpmnProcessId.replace("_", "?") + "*")))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(3);
    assertThat(result.items())
        .extracting("processInstanceKey")
        .containsExactlyInAnyOrder(processInstanceKeys.toArray());
  }

  @Test
  void shouldRetrieveProcessInstancesByStartDateFilterGtLt() {
    // given
    final var pi =
        camundaClient
            .newProcessInstanceSearchRequest()
            .page(p -> p.limit(1))
            .send()
            .join()
            .items()
            .getFirst();
    final var startDate = OffsetDateTime.parse(pi.getStartDate());

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.startDate(
                        b ->
                            b.gt(startDate.minus(1, ChronoUnit.MILLIS))
                                .lt(startDate.plus(1, ChronoUnit.MILLIS))))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getProcessInstanceKey())
        .isEqualTo(pi.getProcessInstanceKey());
  }

  @Test
  void shouldRetrieveProcessInstancesByExistEndDates() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.endDate(b -> b.exists(true)))
            .send()
            .join();

    // then
    // validate all end dates are not null
    assertThat(result.items()).allMatch(p -> p.getEndDate() != null);
  }

  @Test
  void shouldRetrieveProcessInstancesByNotExistEndDates() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.endDate(b -> b.exists(false)))
            .send()
            .join();

    // then
    // validate all end dates are not null
    assertThat(result.items()).allMatch(p -> p.getEndDate() == null);
  }

  @Test
  void shouldRetrieveProcessInstancesByEndDateFilterGteLte() {
    // given
    final var pi =
        camundaClient
            .newProcessInstanceSearchRequest()
            .page(p -> p.limit(1))
            .send()
            .join()
            .items()
            .getFirst();
    final var startDate = OffsetDateTime.parse(pi.getStartDate());

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.startDate(b -> b.gte(startDate).lte(startDate)))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getProcessInstanceKey())
        .isEqualTo(pi.getProcessInstanceKey());
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
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionKey(processDefinitionKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldQueryProcessInstancesByStateActive() {
    // when
    final var result =
        camundaClient.newProcessInstanceSearchRequest().filter(f -> f.state(ACTIVE)).send().join();

    // then
    assertThat(result.items().size()).isEqualTo(4);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrder(
            "service_tasks_v1", "service_tasks_v1", "service_tasks_v2", "incident_process_v1");
  }

  @Test
  void shouldQueryProcessInstancesByStateFilterLike() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.state(b -> b.like("ACT*")))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(4);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrder(
            "service_tasks_v1", "service_tasks_v1", "service_tasks_v2", "incident_process_v1");
  }

  @Test
  void shouldQueryProcessInstancesByStateFilterNeq() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.state(b -> b.neq(ProcessInstanceState.ACTIVE)))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items()).extracting("state").doesNotContain(ProcessInstanceStateEnum.ACTIVE);
  }

  @Test
  void shouldQueryProcessInstancesByStateCompleted() {
    Awaitility.await("process is completed")
        .untilAsserted(
            () -> {

              // when
              final var result =
                  camundaClient
                      .newProcessInstanceSearchRequest()
                      .filter(f -> f.state(COMPLETED))
                      .send()
                      .join();

              // then
              assertThat(result.items().size()).isEqualTo(3);
              assertThat(
                      result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
                  .containsExactlyInAnyOrder(
                      "parent_process_v1", "child_process_v1", "manual_process");
            });
  }

  @Test
  void shouldQueryProcessInstancesWithIncidents() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.hasIncident(true))
            .send()
            .join();

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
        camundaClient
            .newProcessInstanceSearchRequest()
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
        camundaClient
            .newProcessInstanceSearchRequest()
            .sort(s -> s.processDefinitionId().desc())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(7);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyElementsOf(expectedBpmnProcessIds);
  }

  @Test
  void shouldQueryProcessInstancesByVariableSingle() {
    // given
    final List<String> expectedBpmnProcessIds = List.of("service_tasks_v1");
    final List<ProcessInstanceVariableFilterRequest> variables =
        List.of(
            new ProcessInstanceVariableFilterRequest()
                .name("xyz")
                .value(new StringFilterProperty().$eq("\"bar\"")));

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.variables(ResponseMapper.fromProtocolList(variables)))
            .send()
            .join();
    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrderElementsOf(expectedBpmnProcessIds);
  }

  @Test
  void shouldQueryProcessInstancesByVariableSingleUsingMap() {
    // given
    final List<String> expectedBpmnProcessIds = List.of("service_tasks_v1");

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.variables(Map.of("xyz", "\"bar\"")))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrderElementsOf(expectedBpmnProcessIds);
  }

  @Test
  void shouldQueryProcessInstancesByVariableMulti() {
    // given
    final List<String> expectedBpmnProcessIds = List.of("service_tasks_v1");
    final List<ProcessInstanceVariableFilterRequest> variables =
        List.of(
            new ProcessInstanceVariableFilterRequest()
                .name("xyz")
                .value(new StringFilterProperty().$like("\"ba*\"")),
            new ProcessInstanceVariableFilterRequest()
                .name("abc")
                .value(new StringFilterProperty().add$InItem("\"mnp\"")));

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.variables(ResponseMapper.fromProtocolList(variables)))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrderElementsOf(expectedBpmnProcessIds);
  }

  @Test
  void shouldQueryProcessInstancesByVariableAllVariablesMustMatch() {
    // given
    final List<String> expectedBpmnProcessIds = List.of("service_tasks_v1");
    final List<ProcessInstanceVariableFilterRequest> variables =
        List.of(
            new ProcessInstanceVariableFilterRequest()
                .name("xyz")
                .value(new StringFilterProperty().$eq("\"bar\"")),
            new ProcessInstanceVariableFilterRequest()
                .name("abc")
                .value(new StringFilterProperty().add$InItem("\"foo\"")));

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.variables(ResponseMapper.fromProtocolList(variables)))
            .send()
            .join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldQueryProcessInstancesByVariableAdvancedFilterIn() {
    // given
    final List<String> expectedBpmnProcessIds = List.of("service_tasks_v1", "service_tasks_v1");
    final List<ProcessInstanceVariableFilterRequest> variables =
        List.of(
            new ProcessInstanceVariableFilterRequest()
                .name("xyz")
                .value(new StringFilterProperty().add$InItem("\"foo\"").add$InItem("\"bar\"")));
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.variables(ResponseMapper.fromProtocolList(variables)))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrderElementsOf(expectedBpmnProcessIds);
  }

  @Test
  void shouldQueryProcessInstancesByVariableAdvancedFilterLike() {
    // given
    final List<String> expectedBpmnProcessIds = List.of("service_tasks_v1");
    final List<ProcessInstanceVariableFilterRequest> variables =
        List.of(
            new ProcessInstanceVariableFilterRequest()
                .name("xyz")
                .value(new StringFilterProperty().$like("\"fo*\"")));
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.variables(ResponseMapper.fromProtocolList(variables)))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrderElementsOf(expectedBpmnProcessIds);
  }

  @Test
  void shouldSortProcessInstancesByProcessInstanceKey() {
    // when
    final var resultAsc =
        camundaClient
            .newProcessInstanceSearchRequest()
            .sort(s -> s.processInstanceKey().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newProcessInstanceSearchRequest()
            .sort(s -> s.processInstanceKey().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, ProcessInstance::getProcessInstanceKey);
  }

  @Test
  void shouldSortProcessInstancesByProcessDefinitionName() {
    // when
    final var resultAsc =
        camundaClient
            .newProcessInstanceSearchRequest()
            .sort(s -> s.processDefinitionName().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newProcessInstanceSearchRequest()
            .sort(s -> s.processDefinitionName().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, ProcessInstance::getProcessDefinitionName);
  }

  @Test
  void shouldSortProcessInstancesByProcessDefinitionKey() {
    // when
    final var resultAsc =
        camundaClient
            .newProcessInstanceSearchRequest()
            .sort(s -> s.processDefinitionKey().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newProcessInstanceSearchRequest()
            .sort(s -> s.processDefinitionKey().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, ProcessInstance::getProcessDefinitionKey);
  }

  @Test
  void shouldSortProcessInstancesByParentProcessInstanceKey() {
    // when
    final var resultAsc =
        camundaClient
            .newProcessInstanceSearchRequest()
            .sort(s -> s.parentProcessInstanceKey().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newProcessInstanceSearchRequest()
            .sort(s -> s.parentFlowNodeInstanceKey().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, ProcessInstance::getParentProcessInstanceKey);
  }

  @Test
  void shouldSortProcessInstancesByStartDate() {
    // when
    final var resultAsc =
        camundaClient
            .newProcessInstanceSearchRequest()
            .sort(s -> s.startDate().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newProcessInstanceSearchRequest()
            .sort(s -> s.startDate().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, ProcessInstance::getStartDate);
  }

  @Test
  void shouldSortProcessInstancesByState() {
    // when
    final var resultAsc =
        camundaClient.newProcessInstanceSearchRequest().sort(s -> s.state().asc()).send().join();
    final var resultDesc =
        camundaClient.newProcessInstanceSearchRequest().sort(s -> s.state().desc()).send().join();

    assertSorted(resultAsc, resultDesc, ProcessInstance::getState);
  }

  @Test
  public void shouldValidateProcessInstancePagination() {
    final var result =
        camundaClient.newProcessInstanceSearchRequest().page(p -> p.limit(2)).send().join();
    assertThat(result.items().size()).isEqualTo(2);
    final var key = result.items().getFirst().getProcessInstanceKey();
    // apply searchAfter
    final var resultAfter =
        camundaClient
            .newProcessInstanceSearchRequest()
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(6);
    final var keyAfter = resultAfter.items().getFirst().getProcessInstanceKey();
    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newProcessInstanceSearchRequest()
            .page(p -> p.searchBefore(Collections.singletonList(keyAfter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(resultBefore.items().getFirst().getProcessInstanceKey()).isEqualTo(key);
  }

  @Test
  public void shouldValidateFlowNodeInstancePagination() {
    final var result =
        camundaClient.newFlownodeInstanceSearchRequest().page(p -> p.limit(2)).send().join();
    assertThat(result.items().size()).isEqualTo(2);
    final var key = result.items().getFirst().getFlowNodeInstanceKey();
    // apply searchAfter
    final var resultAfter =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(21);
    final var keyAfter = resultAfter.items().getFirst().getFlowNodeInstanceKey();
    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newFlownodeInstanceSearchRequest()
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
        camundaClient
            .newFlownodeInstanceSearchRequest()
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
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .sort(s -> s.flowNodeInstanceKey().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newFlownodeInstanceSearchRequest()
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
        camundaClient
            .newFlownodeInstanceSearchRequest()
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
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .sort(s -> s.processInstanceKey().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newFlownodeInstanceSearchRequest()
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
    final var result =
        camundaClient.newFlowNodeInstanceGetRequest(flowNodeInstanceKey).send().join();

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
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .filter(f -> f.flowNodeId(flowNodeId))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getFlowNodeId()).isEqualTo(flowNodeId);
    assertThat(result.items().getFirst().getProcessDefinitionId()).isNotNull();
    assertThat(result.items().getFirst().getProcessDefinitionId())
        .isEqualTo(flowNodeInstance.getProcessDefinitionId());
  }

  @Test
  void shouldSortFlowNodeInstanceByFlowNodeId() {
    // when
    final var resultAsc =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .sort(s -> s.flowNodeId().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .sort(s -> s.flowNodeId().desc())
            .send()
            .join();
    assertSorted(resultAsc, resultDesc, FlowNodeInstance::getFlowNodeId);
  }

  @Test
  void shouldHaveCorrectFlowNodeInstanceFlowNodeName() {
    // when
    final var result =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .filter(f -> f.flowNodeId("noOpTask"))
            .send()
            .join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getFlowNodeName()).isEqualTo("No Op");
  }

  @Test
  void shouldUseFlowNodeInstanceFlowNodeIdIfNameNotSet() {
    // when
    final var result =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .filter(f -> f.flowNodeId("Event_1p0nsc7"))
            .send()
            .join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getFlowNodeName()).isEqualTo("Event_1p0nsc7");
  }

  @Test
  void shouldQueryFlowNodeInstanceByProcessDefinitionKey() {
    // given
    final var processDefinitionKey = flowNodeInstance.getProcessDefinitionKey();
    // when
    final var result =
        camundaClient
            .newFlownodeInstanceSearchRequest()
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
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .sort(s -> s.processDefinitionKey().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newFlownodeInstanceSearchRequest()
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
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .filter(f -> f.incidentKey(incidentKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getIncidentKey()).isEqualTo(incidentKey);
    assertThat(result.items().getFirst().getState()).isEqualTo(FlowNodeInstanceState.ACTIVE);
    assertThat(result.items().getFirst().getIncident()).isEqualTo(true);
  }

  @Test
  void shouldSortFlowNodeInstanceByIncidentKey() {
    // when
    final var resultAsc =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .sort(s -> s.incidentKey().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .sort(s -> s.incidentKey().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, FlowNodeInstance::getIncidentKey);
  }

  @Test
  void shouldQueryFlowNodeInstanceByState() {
    // given
    final var state = flowNodeInstance.getState();
    // when
    final var result =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .filter(f -> f.state(FlowNodeInstanceFilter.State.valueOf(state.name())))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(18);
    assertThat(result.items().getFirst().getState()).isEqualTo(state);
  }

  @Test
  void shouldSortFlowNodeInstanceByState() {
    // when
    final var resultAsc =
        camundaClient.newFlownodeInstanceSearchRequest().sort(s -> s.state().asc()).send().join();
    final var resultDesc =
        camundaClient.newFlownodeInstanceSearchRequest().sort(s -> s.state().desc()).send().join();

    assertSorted(resultAsc, resultDesc, flowNodeInstance -> flowNodeInstance.getState().name());
  }

  @Test
  void shouldQueryFlowNodeInstanceByIncident() {
    // given
    final var hasIncident = flowNodeInstanceWithIncident.getIncident();
    // when
    final var result =
        camundaClient
            .newFlownodeInstanceSearchRequest()
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
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .filter(f -> f.type(FlowNodeInstanceFilter.Type.valueOf(type.name())))
            .send()
            .join();

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
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .page(p -> p.limit(100))
            .sort(s -> s.type().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .page(p -> p.limit(100))
            .sort(s -> s.type().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, flowNodeInstance -> flowNodeInstance.getType().name());
  }

  @Test
  void shouldQueryFlowNodeInstanceByTenantId() {
    // given
    final var tenantId = flowNodeInstance.getTenantId();
    // when
    final var result =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .filter(f -> f.tenantId(tenantId))
            .send()
            .join();

    // then
    assertThat(result.page().totalItems()).isEqualTo(22);
    assertThat(result.items()).allMatch(f -> f.getTenantId().equals(tenantId));
  }

  @Test
  public void shouldQueryFlowNodeInstanceValidatePagination() {
    final var result =
        camundaClient.newFlownodeInstanceSearchRequest().page(p -> p.limit(2)).send().join();
    assertThat(result.items().size()).isEqualTo(2);
    final var key = result.items().getFirst().getFlowNodeInstanceKey();
    // apply searchAfter
    final var resultAfter =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(21);
    final var keyAfter = resultAfter.items().getFirst().getFlowNodeInstanceKey();
    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .page(p -> p.searchBefore(Collections.singletonList(keyAfter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(resultBefore.items().getFirst().getFlowNodeInstanceKey()).isEqualTo(key);
  }

  @Test
  void shouldSearchByFromWithLimit() {
    // when
    final var resultAll = camundaClient.newFlownodeInstanceSearchRequest().send().join();
    final var thirdKey = resultAll.items().get(2).getFlowNodeInstanceKey();

    final var resultSearchFrom =
        camundaClient
            .newFlownodeInstanceSearchRequest()
            .page(p -> p.limit(2).from(2))
            .send()
            .join();

    // then
    assertThat(resultSearchFrom.items().size()).isEqualTo(2);
    assertThat(resultSearchFrom.items().stream().findFirst().get().getFlowNodeInstanceKey())
        .isEqualTo(thirdKey);
  }

  @Test
  void shouldQueryProcessInstancesByErrorMessageEqual() {
    // when:
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(b -> b.errorMessage(f -> f.eq(EXPECTED_ERROR)))
            .send()
            .join();

    // then:
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getProcessDefinitionId()).isEqualTo("incident_process_v1");
  }

  @Test
  void shouldQueryProcessInstancesByErrorMessageNotEqual() {
    // when:
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(b -> b.errorMessage(f -> f.neq(EXPECTED_ERROR)))
            .send()
            .join();

    // then:
    assertThat(result.items().size()).isEqualTo(0);
  }

  @Test
  void shouldQueryProcessInstancesByErrorMessageExists() {
    // when:
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(b -> b.errorMessage(f -> f.exists(true)))
            .send()
            .join();

    // then:
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items()).extracting("processDefinitionId").contains("incident_process_v1");
  }

  @Test
  void shouldQueryProcessInstancesByErrorMessageNotExists() {
    // when:
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(b -> b.errorMessage(f -> f.exists(false)))
            .send()
            .join();

    // then:
    assertThat(result.items().size()).isEqualTo(0);
  }

  @Test
  void shouldQueryProcessInstancesByErrorMessageIn() {
    // when:
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(b -> b.errorMessage(f -> f.in(EXPECTED_ERROR, "foo")))
            .send()
            .join();

    // then:
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items()).extracting("processDefinitionId").contains("incident_process_v1");
  }

  @Test
  void shouldQueryProcessInstancesByErrorMessageNotIn() {
    // when:
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(b -> b.errorMessage(f -> f.in("foo", "bar")))
            .send()
            .join();

    // then:
    assertThat(result.items().size()).isEqualTo(0);
  }

  @Test
  void shouldQueryProcessInstancesByErrorMessageExistsLike() {
    // given:
    final String expectedError = "Expect*";

    // when:
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(b -> b.errorMessage(f -> f.like(expectedError)))
            .send()
            .join();

    // then:
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items()).extracting("processDefinitionId").contains("incident_process_v1");
  }

  @Test
  void shouldQueryProcessInstancesByErrorMessageLikeAndIn() {
    // given:
    final String expectedError = "Expect*";

    // when:
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(b -> b.errorMessage(f -> f.like(expectedError).in("foo", "bar")))
            .send()
            .join();

    // then:
    assertThat(result.items().size()).isEqualTo(0);
  }
}
