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
import static io.camunda.client.api.search.enums.ProcessInstanceState.TERMINATED;
import static io.camunda.it.util.TestHelper.assertSorted;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilJobWorkerHasFailedJob;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.qa.util.compatibility.CompatibilityTest;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
@CompatibilityTest
public class ProcessInstanceSearchIT {

  public static final String INCIDENT_ERROR_MESSAGE_V1 =
      "Expected result of the expression 'retriesA' to be 'NUMBER', but was 'STRING'.";
  public static final String INCIDENT_ERROR_MESSAGE_V2 =
      "Expected result of the expression 'retriesB' to be 'NUMBER', but was 'STRING'.";
  public static final int INCIDENT_ERROR_HASH_CODE_V2 = 17551445;
  static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  static final List<ProcessInstanceEvent> PROCESS_INSTANCES = new ArrayList<>();
  private static long processInstanceWithIncidentKey;
  private static CamundaClient camundaClient;

  @BeforeAll
  public static void beforeAll() {
    Objects.requireNonNull(camundaClient);
    final List<String> processes =
        List.of(
            "service_tasks_v1.bpmn",
            "service_tasks_v2.bpmn",
            "incident_process_v1.bpmn",
            "incident_process_v2.bpmn",
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
    PROCESS_INSTANCES.add(startProcessInstance(camundaClient, "incident_process_v2"));
    processInstanceWithIncidentKey = processInstanceWithIncident.getProcessInstanceKey();
    PROCESS_INSTANCES.add(processInstanceWithIncident);
    PROCESS_INSTANCES.add(startProcessInstance(camundaClient, "parent_process_v1"));

    waitForProcessInstancesToStart(camundaClient, 8);
    waitUntilProcessInstanceHasIncidents(camundaClient, 2);
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
    assertThat(result.getRootProcessInstanceKey()).isEqualTo(processInstanceKey);
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
        (ProblemException)
            assertThatThrownBy(
                    () ->
                        camundaClient
                            .newProcessInstanceGetRequest(invalidProcessInstanceKey)
                            .send()
                            .join())
                .isInstanceOf(ProblemException.class)
                .actual();
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
    assertThat(exception.details().getDetail())
        .contains("Process Instance with key '%s' not found".formatted(invalidProcessInstanceKey));
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
  void shouldQueryRootProcessInstances() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.parentProcessInstanceKey(key -> key.exists(false)))
            .send()
            .join();

    // then we have exactly PROCESS_INSTANCES.size, since the one subprocess should not be there
    assertThat(result.items().size()).isEqualTo(PROCESS_INSTANCES.size());
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
  void shouldQueryProcessInstancesByKeyFilterNotIn() {
    // given
    final List<Long> processInstanceKeys =
        PROCESS_INSTANCES.subList(0, 2).stream()
            .map(ProcessInstanceEvent::getProcessInstanceKey)
            .toList();

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processInstanceKey(b -> b.notIn(processInstanceKeys)))
            .send()
            .join();

    final var numberOfExpectedProcessInstances =
        (PROCESS_INSTANCES.size() + 1) - 2; // includes 1 child process, minus 2 excluded

    // then
    assertThat(result.items().size()).isEqualTo(numberOfExpectedProcessInstances);
    assertThat(result.items()).extracting("processInstanceKey").doesNotContain(processInstanceKeys);
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
    final var startDate = pi.getStartDate();

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
    final var startDate = pi.getStartDate();

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
    assertThat(result.items().size()).isEqualTo(5);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrder(
            "service_tasks_v1",
            "service_tasks_v1",
            "service_tasks_v2",
            "incident_process_v1",
            "incident_process_v2");
  }

  @Test
  void shouldQueryBySingleOrConditionOnly() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.orFilters(List.of(f2 -> f2.state(ACTIVE))))
            .send()
            .join();

    // then
    assertThat(result.items()).size().isEqualTo(5);
  }

  @Test
  void shouldQueryByMultipleOrConditionsOnly() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.orFilters(List.of(f2 -> f2.state(ACTIVE), f3 -> f3.hasIncident(true))))
            .send()
            .join();

    // then
    assertThat(result.items()).size().isEqualTo(5);
  }

  @Test
  void shouldQueryBySingleAndSingleOrCondition() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.processDefinitionId(pid -> pid.eq("service_tasks_v1"))
                        .orFilters(List.of(f2 -> f2.hasIncident(false))))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items())
        .filteredOn(pi -> pi.getProcessDefinitionId().equals("service_tasks_v1"))
        .hasSize(2);
    assertThat(result.items()).filteredOn(pi -> !pi.getHasIncident()).hasSize(2);
  }

  @Test
  void shouldQueryByMultipleAndMultipleOrConditions() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.tenantId("<default>")
                        .processDefinitionId(
                            f2 ->
                                f2.in(
                                    "incident_process_v1",
                                    "parent_process_v1",
                                    "child_process_v1",
                                    "manual_process",
                                    "service_tasks_v1"))
                        .orFilters(
                            List.of(
                                f3 -> f3.state(ACTIVE).processDefinitionId("service_tasks_v1"),
                                (f4 -> f4.state(ACTIVE).hasIncident(true)))))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrder("service_tasks_v1", "service_tasks_v1", "incident_process_v1");
  }

  @Test
  void shouldQueryByElementIdAndMultipleOrConditions() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.elementId("taskA")
                        .orFilters(List.of(f3 -> f3.state(ACTIVE), f4 -> f4.state(COMPLETED))))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrder("service_tasks_v1", "service_tasks_v1");
  }

  @Test
  void shouldNotQueryIfNoOrConditionMatches() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.orFilters(
                        List.of(
                            f2 -> f2.state(TERMINATED),
                            f3 -> f3.processDefinitionId("non-existent"))))
            .send()
            .join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldQueryByOverlappingOrConditions() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.tenantId("<default>")
                        .orFilters(
                            List.of(
                                f2 -> f2.state(ACTIVE), f3 -> f3.state(ACTIVE).hasIncident(true))))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(5);
    assertThat(result.items()).filteredOn(pi -> "ACTIVE".equals(pi.getState().name())).hasSize(5);
    assertThat(result.items()).filteredOn(ProcessInstance::getHasIncident).hasSize(2);
  }

  @Test
  void shouldIgnoreConflictingNestedOrWithAndConditions() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.hasIncident(false)
                        .orFilters(List.of(f2 -> f2.state(ACTIVE).hasIncident(true))))
            .send()
            .join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldIgnoreDuplicateOrConditions() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.orFilters(
                        List.of(
                            f2 -> f2.state(ACTIVE),
                            f3 -> f3.state(ACTIVE),
                            f4 -> f4.state(ACTIVE))))
            .send()
            .join();
    // then
    assertThat(result.items().size()).isEqualTo(5);
    assertThat(result.items()).allMatch(pi -> pi.getState().name().equals("ACTIVE"));
  }

  @Test
  void shouldNotQueryIfAndConflictsWithOr() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.state(ACTIVE)
                        .orFilters(List.of(f2 -> f2.state(COMPLETED), f3 -> f3.state(TERMINATED))))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(0);
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
    assertThat(result.items().size()).isEqualTo(5);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrder(
            "service_tasks_v1",
            "service_tasks_v1",
            "service_tasks_v2",
            "incident_process_v1",
            "incident_process_v2");
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
    assertThat(result.items()).extracting("state").doesNotContain(ProcessInstanceState.ACTIVE);
  }

  @Test
  void shouldQueryProcessInstancesByStateCompleted() {
    await("process is completed")
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
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrder("incident_process_v1", "incident_process_v2");
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
    assertThat(result.items().getFirst().getRootProcessInstanceKey())
        .isEqualTo(parentProcessInstanceKey);
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
    assertThat(result.items().size()).isEqualTo(8);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyElementsOf(expectedBpmnProcessIds);
  }

  @Test
  void shouldQueryProcessInstancesByVariableSingle() {
    // given
    final List<String> expectedBpmnProcessIds = List.of("service_tasks_v1");

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.variables(List.of(vf -> vf.name("xyz").value(v -> v.eq("\"bar\"")))))
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

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.variables(
                        List.of(
                            vf -> vf.name("xyz").value(v -> v.like("\"ba*\"")),
                            vf -> vf.name("abc").value(v -> v.in("\"mnp\"")))))
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

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.variables(
                        List.of(
                            vf -> vf.name("xyz").value(v -> v.eq("\"bar\"")),
                            vf -> vf.name("abc").value(v -> v.in("\"foo\"")))))
            .send()
            .join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldQueryProcessInstancesByVariableAdvancedFilterIn() {
    // given
    final List<String> expectedBpmnProcessIds = List.of("service_tasks_v1", "service_tasks_v1");

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.variables(
                        List.of(vf -> vf.name("xyz").value(v -> v.in("\"foo\"", "\"bar\"")))))
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

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.variables(List.of(vf -> vf.name("xyz").value(v -> v.like("\"fo*\"")))))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrderElementsOf(expectedBpmnProcessIds);
  }

  @Test
  public void shouldThrowExceptionIfVariableValueNull() {

    // when
    final var exception =
        assertThatExceptionOfType(IllegalArgumentException.class)
            .isThrownBy(
                () ->
                    camundaClient
                        .newProcessInstanceSearchRequest()
                        .filter(f -> f.variables(Arrays.asList(vf -> vf.name("xyz"))))
                        .send()
                        .join())
            .actual();
    // then
    assertThat(exception.getMessage()).contains("Variable value cannot be null for variable 'xyz'");
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
            .sort(s -> s.parentElementInstanceKey().desc())
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
            .page(p -> p.after(result.page().endCursor()))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(6);
    final var keyAfter = resultAfter.items().getFirst().getProcessInstanceKey();
    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newProcessInstanceSearchRequest()
            .page(p -> p.before(resultAfter.page().startCursor()))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(resultBefore.items().getFirst().getProcessInstanceKey()).isEqualTo(key);
  }

  @Test
  void shouldQueryProcessInstancesByErrorMessageEqual() {
    // when:
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(b -> b.errorMessage(f -> f.eq(INCIDENT_ERROR_MESSAGE_V1)))
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
            .filter(b -> b.errorMessage(f -> f.neq(INCIDENT_ERROR_MESSAGE_V1)))
            .send()
            .join();

    // then:
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getProcessDefinitionId()).isEqualTo("incident_process_v2");
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
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items())
        .extracting("processDefinitionId")
        .containsExactlyInAnyOrder("incident_process_v1", "incident_process_v2");
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
            .filter(b -> b.errorMessage(f -> f.in(INCIDENT_ERROR_MESSAGE_V1, "foo")))
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
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items())
        .extracting("processDefinitionId")
        .containsExactlyInAnyOrder("incident_process_v1", "incident_process_v2");
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

  @Test
  void shouldQueryProcessInstancesByHasRetriesLeft() {
    // given
    try (final JobWorker ignored =
        camundaClient
            .newWorker()
            .jobType("taskC")
            .handler((client, job) -> client.newFailCommand(job).retries(1).send().join())
            .open()) {

      final Map<String, Object> variables = Map.of("path", 222);

      waitUntilJobWorkerHasFailedJob(camundaClient, variables, 1);

      // when
      final var result =
          camundaClient
              .newProcessInstanceSearchRequest()
              .filter(f -> f.hasRetriesLeft(true).variables(variables))
              .send()
              .join();

      // then
      assertThat(result.items().size()).isEqualTo(1);
      assertThat(result.items())
          .extracting("processDefinitionId")
          .containsExactlyInAnyOrder("service_tasks_v2");
    }
  }

  @Test
  void shouldQueryProcessInstancesByIncidentErrorHashCode() {

    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.incidentErrorHashCode(INCIDENT_ERROR_HASH_CODE_V2))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items()).extracting("processDefinitionId").contains("incident_process_v2");
  }

  @Test
  void shouldQueryProcessInstanceCallHierarchy() {
    // given
    final long childProcessInstanceKey =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionId("child_process_v1"))
            .send()
            .join()
            .items()
            .getFirst()
            .getProcessInstanceKey();

    // when
    final var result =
        camundaClient
            .newProcessInstanceGetCallHierarchyRequest(childProcessInstanceKey)
            .send()
            .join();

    // then
    assertThat(result)
        .isNotNull()
        .hasSize(2)
        .extracting("processDefinitionName")
        .containsExactly("Parent process v1", "Child process v1");
  }

  @Test
  void shouldReturnEmptyCallHierarchyForParentProcessInstance() {
    // given
    final long parentProcessInstanceKey =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionId("parent_process_v1"))
            .send()
            .join()
            .items()
            .getFirst()
            .getProcessInstanceKey();

    // when
    final var result =
        camundaClient
            .newProcessInstanceGetCallHierarchyRequest(parentProcessInstanceKey)
            .send()
            .join();

    // then
    assertThat(result).isNotNull().isEmpty();
  }

  @Test
  void shouldReturnResultWhenErrorMessageMatchesResolvedIncidentErrorHashCode() {
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.incidentErrorHashCode(INCIDENT_ERROR_HASH_CODE_V2)
                        .errorMessage(INCIDENT_ERROR_MESSAGE_V2))
            .send()
            .join();
    // then
    assertThat(result.items()).isNotEmpty();
  }

  @Test
  void shouldReturnNoResultWhenErrorMessageConflictsWithResolvedIncidentErrorHashCode() {
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.incidentErrorHashCode(INCIDENT_ERROR_HASH_CODE_V2)
                        .errorMessage(INCIDENT_ERROR_MESSAGE_V1))
            .send()
            .join();
    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldIncludeResolvedIncidentErrorHashCodeIfErrorMessageLikePatternMatches() {
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.incidentErrorHashCode(INCIDENT_ERROR_HASH_CODE_V2)
                        .errorMessage(f2 -> f2.like("Expected*")))
            .send()
            .join();
    // then
    assertThat(result.items()).isNotEmpty();
  }

  @Test
  void shouldIgnoreResolvedIncidentErrorHashCodeIfErrorMessageLikeDoesNotMatch() {
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.incidentErrorHashCode(INCIDENT_ERROR_HASH_CODE_V2)
                        .errorMessage(f2 -> f2.like("Failed*")))
            .send()
            .join();
    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldReturnResultWhenEitherErrorMessageOrResolvedIncidentErrorHashCodeMatch() {
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.orFilters(
                        List.of(
                            f1 -> f1.errorMessage(INCIDENT_ERROR_MESSAGE_V1),
                            f2 -> f2.incidentErrorHashCode(INCIDENT_ERROR_HASH_CODE_V2))))
            .send()
            .join();
    // then
    assertThat(result.items()).isNotEmpty();
  }

  @Test
  void shouldCombineResolvedAndProvidedErrorMessagesInInOperator() {
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.incidentErrorHashCode(INCIDENT_ERROR_HASH_CODE_V2)
                        .errorMessage(
                            f2 -> f2.in(List.of("Other message", INCIDENT_ERROR_MESSAGE_V2))))
            .send()
            .join();

    assertThat(result.items()).isNotEmpty();
  }

  @Test
  void shouldReturnWhenTopLevelHashAndOrClauseHasMatchingErrorMessage() {
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.incidentErrorHashCode(INCIDENT_ERROR_HASH_CODE_V2)
                        .orFilters(List.of(f1 -> f1.errorMessage(INCIDENT_ERROR_MESSAGE_V2))))
            .send()
            .join();

    assertThat(result.items()).isNotEmpty();
    assertThat(result.items())
        .allSatisfy(
            instance ->
                assertThat(instance.getProcessDefinitionId()).isEqualTo("incident_process_v2"));
  }

  @Test
  void shouldReturnWhenTopLevelErrorMessageAndOrClauseHasMatchingHashCode() {
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.errorMessage(INCIDENT_ERROR_MESSAGE_V2)
                        .orFilters(
                            List.of(f1 -> f1.incidentErrorHashCode(INCIDENT_ERROR_HASH_CODE_V2))))
            .send()
            .join();

    assertThat(result.items()).isNotEmpty();
    assertThat(result.items())
        .allSatisfy(
            instance ->
                assertThat(instance.getProcessDefinitionId()).isEqualTo("incident_process_v2"));
  }

  @Test
  void shouldReturnOnlyMatchingTopLevelWhenOrClauseHashConflicts() {
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.errorMessage(INCIDENT_ERROR_MESSAGE_V1)
                        .orFilters(
                            List.of(f1 -> f1.incidentErrorHashCode(INCIDENT_ERROR_HASH_CODE_V2))))
            .send()
            .join();

    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldReturnOnlyMatchingTopLevelWhenOrClauseErrorMessageConflicts() {
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.incidentErrorHashCode(INCIDENT_ERROR_HASH_CODE_V2)
                        .orFilters(List.of(f1 -> f1.errorMessage(INCIDENT_ERROR_MESSAGE_V1))))
            .send()
            .join();

    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldReturnEmptyResultForIncorrectIncidentErrorHashCode() {
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.incidentErrorHashCode(123456))
            .send()
            .join();

    assertThat(result.items()).isEmpty();
  }
}
