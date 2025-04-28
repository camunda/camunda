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
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilElementInstanceHasIncidents;
import static io.camunda.it.util.TestHelper.waitUntilJobWorkerHasFailedJob;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.enums.ProcessInstanceState;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

@MultiDbTest
public class ProcessInstanceAndElementInstanceSearchTest {

  public static final String INCIDENT_ERROR_MESSAGE_V1 =
      "Expected result of the expression 'retriesA' to be 'NUMBER', but was 'STRING'.";
  public static final int INCIDENT_ERROR_HASH_CODE_V2 = 17551445;
  static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  static final List<ProcessInstanceEvent> PROCESS_INSTANCES = new ArrayList<>();
  private static long processInstanceWithIncidentKey;
  private static ElementInstance elementInstance;
  private static ElementInstance elementInstanceWithIncident;
  private static List<ElementInstance> allElementInstances;
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
    waitForElementInstances(camundaClient, 24);
    waitUntilElementInstanceHasIncidents(camundaClient, 2);
    waitUntilProcessInstanceHasIncidents(camundaClient, 2);
    // store element instances for querying
    allElementInstances =
        camundaClient
            .newElementInstanceSearchRequest()
            .page(p -> p.limit(100))
            .sort(s -> s.elementId().asc())
            .send()
            .join()
            .items();
    elementInstance = allElementInstances.getFirst();
    elementInstanceWithIncident =
        allElementInstances.stream()
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
  void shouldQueryAllProcessInstances() {
    // when
    final var result = camundaClient.newProcessInstanceSearchRequest().send().join();

    // then we have exactly PROCESS_INSTANCES.size + 1, since the one subprocess should also be
    // there
    assertThat(result.items().size()).isEqualTo(PROCESS_INSTANCES.size() + 1);
  }

  @Test
  void shouldQueryRootProcessInstances() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.parentProcessInstanceKey(-1L))
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
                    f.endDate(d -> d.exists(false)).orFilters(List.of(f2 -> f2.hasIncident(false))))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items()).filteredOn(pi -> pi.getEndDate() == null).hasSize(3);
    assertThat(result.items()).filteredOn(pi -> !pi.getHasIncident()).hasSize(3);
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
        assertThrows(
            IllegalArgumentException.class,
            () ->
                camundaClient
                    .newProcessInstanceSearchRequest()
                    .filter(f -> f.variables(Arrays.asList(vf -> vf.name("xyz"))))
                    .send()
                    .join());
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
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(7);
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
  public void shouldValidateElementInstancePagination() {
    final var result =
        camundaClient.newElementInstanceSearchRequest().page(p -> p.limit(2)).send().join();
    assertThat(result.items().size()).isEqualTo(2);
    final var key = result.items().getFirst().getElementInstanceKey();
    // apply searchAfter
    final var resultAfter =
        camundaClient
            .newElementInstanceSearchRequest()
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(23);
    final var keyAfter = resultAfter.items().getFirst().getElementInstanceKey();
    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newElementInstanceSearchRequest()
            .page(p -> p.searchBefore(Collections.singletonList(keyAfter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(resultBefore.items().getFirst().getElementInstanceKey()).isEqualTo(key);
  }

  @Test
  void shouldQueryElementInstanceByElementInstanceKey() {
    // given
    final var key = elementInstance.getElementInstanceKey();
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementInstanceKey(key))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst()).isEqualTo(elementInstance);
  }

  @Test
  void shouldSortElementInstanceByElementInstanceKey() {
    // when
    final var resultAsc =
        camundaClient
            .newElementInstanceSearchRequest()
            .sort(s -> s.elementInstanceKey().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newElementInstanceSearchRequest()
            .sort(s -> s.elementInstanceKey().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, ElementInstance::getElementInstanceKey);
  }

  @Test
  void shouldQueryElementInstanceQueryByProcessInstanceKey() {
    // given
    final var processInstanceKey = elementInstance.getProcessInstanceKey();
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(5);
    assertThat(result.items().getFirst().getProcessInstanceKey()).isEqualTo(processInstanceKey);
    assertThat(result.items().get(1).getProcessInstanceKey()).isEqualTo(processInstanceKey);
  }

  @Test
  void shouldSortElementInstanceByProcessInstanceKey() {
    // when
    final var resultAsc =
        camundaClient
            .newElementInstanceSearchRequest()
            .sort(s -> s.processInstanceKey().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newElementInstanceSearchRequest()
            .sort(s -> s.processInstanceKey().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, ElementInstance::getProcessInstanceKey);
  }

  @Test
  void shouldGetElementInstanceByKey() {
    // given
    final var elementInstanceKey = elementInstance.getElementInstanceKey();

    // when
    final var result = camundaClient.newElementInstanceGetRequest(elementInstanceKey).send().join();

    // then
    assertThat(result).isNotNull();
    assertThat(result).isEqualTo(elementInstance);
  }

  @Test
  void shouldQueryElementInstanceByElementId() {
    // given
    final var elementId = elementInstance.getElementId();
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId(elementId))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getElementId()).isEqualTo(elementId);
    assertThat(result.items().getFirst().getProcessDefinitionId()).isNotNull();
    assertThat(result.items().getFirst().getProcessDefinitionId())
        .isEqualTo(elementInstance.getProcessDefinitionId());
  }

  @Test
  void shouldSortElementInstanceByElementId() {
    // when
    final var resultAsc =
        camundaClient
            .newElementInstanceSearchRequest()
            .sort(s -> s.elementId().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newElementInstanceSearchRequest()
            .sort(s -> s.elementId().desc())
            .send()
            .join();
    assertSorted(resultAsc, resultDesc, ElementInstance::getElementId);
  }

  @Test
  void shouldHaveCorrectElementInstanceElementName() {
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId("noOpTask"))
            .send()
            .join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getElementName()).isEqualTo("No Op");
  }

  @Test
  void shouldUseElementInstanceElementIdIfNameNotSet() {
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementId("Event_1p0nsc7"))
            .send()
            .join();
    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getElementName()).isEqualTo("Event_1p0nsc7");
  }

  @Test
  void shouldQueryElementInstanceByProcessDefinitionKey() {
    // given
    final var processDefinitionKey = elementInstance.getProcessDefinitionKey();
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.processDefinitionKey(processDefinitionKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(5);
    assertThat(result.items().getFirst().getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
  }

  @Test
  void shouldSortElementInstanceByProcessDefinitionKey() {
    // when
    final var resultAsc =
        camundaClient
            .newElementInstanceSearchRequest()
            .sort(s -> s.processDefinitionKey().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newElementInstanceSearchRequest()
            .sort(s -> s.processDefinitionKey().desc())
            .send()
            .join();
    assertSorted(resultAsc, resultDesc, ElementInstance::getProcessDefinitionKey);
  }

  @Test
  void shouldQueryElementInstanceByIncidentKey() {
    // given
    final var incidentKey = elementInstanceWithIncident.getIncidentKey();
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.incidentKey(incidentKey))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getIncidentKey()).isEqualTo(incidentKey);
    assertThat(result.items().getFirst().getState()).isEqualTo(ElementInstanceState.ACTIVE);
    assertThat(result.items().getFirst().getIncident()).isEqualTo(true);
  }

  @Test
  void shouldSortElementInstanceByIncidentKey() {
    // when
    final var resultAsc =
        camundaClient
            .newElementInstanceSearchRequest()
            .sort(s -> s.incidentKey().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newElementInstanceSearchRequest()
            .sort(s -> s.incidentKey().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, ElementInstance::getIncidentKey);
  }

  @Test
  void shouldQueryElementInstanceByState() {
    // given
    final var state = elementInstance.getState();
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.state(ElementInstanceState.valueOf(state.name())))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(19);
    assertThat(result.items().getFirst().getState()).isEqualTo(state);
  }

  @Test
  void shouldSortElementInstanceByState() {
    // when
    final var resultAsc =
        camundaClient.newElementInstanceSearchRequest().sort(s -> s.state().asc()).send().join();
    final var resultDesc =
        camundaClient.newElementInstanceSearchRequest().sort(s -> s.state().desc()).send().join();

    assertSorted(resultAsc, resultDesc, elementInstance -> elementInstance.getState().name());
  }

  @Test
  void shouldQueryElementInstanceByIncident() {
    // given
    final var hasIncident = elementInstanceWithIncident.getIncident();
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.hasIncident(hasIncident))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().getFirst().getIncident()).isEqualTo(hasIncident);
  }

  @Test
  void shouldQueryElementInstanceByType() {
    // given
    final var type = elementInstance.getType();
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.type(ElementInstanceType.valueOf(type.name())))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().getFirst().getType()).isEqualTo(type);
    assertThat(result.items().get(1).getType()).isEqualTo(type);
    assertThat(result.items().get(2).getType()).isEqualTo(type);
  }

  @Test
  void shouldSortElementInstanceByType() {
    // when
    final var resultAsc =
        camundaClient
            .newElementInstanceSearchRequest()
            .page(p -> p.limit(100))
            .sort(s -> s.type().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newElementInstanceSearchRequest()
            .page(p -> p.limit(100))
            .sort(s -> s.type().desc())
            .send()
            .join();

    assertSorted(resultAsc, resultDesc, elementInstance -> elementInstance.getType().name());
  }

  @Test
  void shouldQueryElementInstanceByTenantId() {
    // given
    final var tenantId = elementInstance.getTenantId();
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.tenantId(tenantId))
            .send()
            .join();

    // then
    assertThat(result.page().totalItems()).isEqualTo(24);
    assertThat(result.items()).allMatch(f -> f.getTenantId().equals(tenantId));
  }

  @Test
  public void shouldQueryElementInstanceValidatePagination() {
    final var result =
        camundaClient.newElementInstanceSearchRequest().page(p -> p.limit(2)).send().join();
    assertThat(result.items().size()).isEqualTo(2);
    final var key = result.items().getFirst().getElementInstanceKey();
    // apply searchAfter
    final var resultAfter =
        camundaClient
            .newElementInstanceSearchRequest()
            .page(p -> p.searchAfter(Collections.singletonList(key)))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(23);
    final var keyAfter = resultAfter.items().getFirst().getElementInstanceKey();
    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newElementInstanceSearchRequest()
            .page(p -> p.searchBefore(Collections.singletonList(keyAfter)))
            .send()
            .join();
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(resultBefore.items().getFirst().getElementInstanceKey()).isEqualTo(key);
  }

  @Test
  void shouldSearchByFromWithLimit() {
    // when
    final var resultAll = camundaClient.newElementInstanceSearchRequest().send().join();
    final var thirdKey = resultAll.items().get(2).getElementInstanceKey();

    final var resultSearchFrom =
        camundaClient.newElementInstanceSearchRequest().page(p -> p.limit(2).from(2)).send().join();

    // then
    assertThat(resultSearchFrom.items().size()).isEqualTo(2);
    assertThat(resultSearchFrom.items().stream().findFirst().get().getElementInstanceKey())
        .isEqualTo(thirdKey);
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

      waitUntilJobWorkerHasFailedJob(camundaClient, 1);

      // when
      final var result =
          camundaClient
              .newProcessInstanceSearchRequest()
              .filter(f -> f.hasRetriesLeft(true))
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
  void shouldQueryProcessInstancesByElementIdFilter() {
    // given
    final var random = RandomGenerator.getDefault();
    final var elementInstance = allElementInstances.get(random.nextInt(allElementInstances.size()));

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.processInstanceKey(elementInstance.getProcessInstanceKey())
                        .elementId(elementInstance.getElementId()))
            .send()
            .join();

    assertThat(result.items())
        .extracting("processInstanceKey")
        .containsExactly(elementInstance.getProcessInstanceKey());
  }

  @Test
  void shouldQueryProcessInstancesByElementIdAndElementInstanceStateFilter() {

    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.elementId("taskA").elementInstanceState(ElementInstanceState.ACTIVE))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
    assertThat(result.items().stream().map(ProcessInstance::getProcessDefinitionId).toList())
        .containsExactlyInAnyOrder("service_tasks_v1", "service_tasks_v1");
  }

  @Test
  void shouldQueryProcessInstancesByElementIdAndElementInstanceIncidentFilter() {
    // when
    final var result =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(
                f ->
                    f.elementId(elementInstanceWithIncident.getElementId())
                        .hasElementInstanceIncident(true))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(2);
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
}
