/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.assertSorted;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForElementInstances;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilElementInstanceHasIncidents;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.response.ProcessInstanceEvent;
import io.camunda.client.api.search.enums.ElementInstanceState;
import io.camunda.client.api.search.enums.ElementInstanceType;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@MultiDbTest
public class ElementInstanceSearchTest {

  static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  static final List<ProcessInstanceEvent> PROCESS_INSTANCES = new ArrayList<>();
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
            "child_process_v1.bpmn",
            "ad_hoc_sub_process.bpmn",
            "subprocess_with_multi_instance.bpmn");
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
    PROCESS_INSTANCES.add(processInstanceWithIncident);
    PROCESS_INSTANCES.add(startProcessInstance(camundaClient, "parent_process_v1"));
    PROCESS_INSTANCES.add(startProcessInstance(camundaClient, "ad_hoc_sub_process"));
    PROCESS_INSTANCES.add(startProcessInstance(camundaClient, "subprocess_with_multi_instance"));

    waitForProcessInstancesToStart(camundaClient, 10);
    waitForElementInstances(camundaClient, 34);
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
  void shouldRetrieveElementInstanceByStartDateFilter() {
    // given
    final var ei =
        camundaClient
            .newElementInstanceSearchRequest()
            .page(p -> p.limit(1))
            .send()
            .join()
            .items()
            .getFirst();
    final var startDate = OffsetDateTime.parse(ei.getStartDate());
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            // also filtering by elementId to get a unique result since others may coincidentally
            // have been started at the same time
            .filter(f -> f.startDate(startDate).elementId(ei.getElementId()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getStartDate()).isEqualTo(ei.getStartDate());
  }

  @Test
  void shouldNotFindElementInstanceByStartDateFilter() {
    // given
    final var hourAgo = OffsetDateTime.now().minusHours(1);
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.startDate(hourAgo))
            .send()
            .join();
    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldRetrieveElementInstanceByEndDateFilter() {
    // given
    final var ei =
        camundaClient
            .newElementInstanceSearchRequest()
            .page(p -> p.limit(1))
            .send()
            .join()
            .items()
            .getFirst();
    final var endDate = OffsetDateTime.parse(ei.getEndDate());
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            // also filtering by elementId to get a unique result since others may coincidentally
            // have ended at the same time
            .filter(f -> f.endDate(endDate).elementId(ei.getElementId()))
            .send()
            .join();

    // then
    assertThat(result.items()).hasSize(1);
    assertThat(result.items().getFirst().getEndDate()).isEqualTo(ei.getEndDate());
  }

  @Test
  void shouldNotFindElementInstanceByEndDateFilter() {
    // given
    final var hourAgo = OffsetDateTime.now().minusHours(1);
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.endDate(hourAgo))
            .send()
            .join();
    // then
    assertThat(result.items()).isEmpty();
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
            .page(p -> p.after(result.page().endCursor()))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(32);
    final var keyAfter = resultAfter.items().getFirst().getElementInstanceKey();
    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newElementInstanceSearchRequest()
            .page(p -> p.before(resultAfter.page().startCursor()))
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

  @ParameterizedTest
  @CsvSource(
      value = {
        "service_tasks_v2,startEvent",
        "service_tasks_v2,exclusiveGateway",
        "service_tasks_v2,taskC",
        "parent_process_v1,call_activity",
        "incident_process_v1,taskAIncident",
        "manual_process,taskM",
        "ad_hoc_sub_process,adHocSubProcess"
      })
  void shouldGetElementInstanceByKey(final String processDefinitionId, final String elementId) {
    // given
    final var elementInstance =
        allElementInstances.stream()
            .filter(
                e ->
                    e.getProcessDefinitionId().equals(processDefinitionId)
                        && e.getElementId().equals(elementId))
            .findFirst()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Test element %s/%s was not found"
                            .formatted(processDefinitionId, elementId)));

    final var elementInstanceKey = elementInstance.getElementInstanceKey();

    // when
    final var result = camundaClient.newElementInstanceGetRequest(elementInstanceKey).send().join();

    // then
    assertThat(result).isNotNull().isEqualTo(elementInstance);
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
  void shouldQueryElementInstanceByElementName() {
    // given
    final var elementName = elementInstance.getElementName();
    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementName(elementName))
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getElementName()).isEqualTo(elementName);
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
  void shouldSortElementInstanceByElementName() {
    // when
    final var resultAsc =
        camundaClient
            .newElementInstanceSearchRequest()
            .sort(s -> s.elementName().asc())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newElementInstanceSearchRequest()
            .sort(s -> s.elementName().desc())
            .send()
            .join();
    assertSorted(resultAsc, resultDesc, ElementInstance::getElementName);
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
    assertThat(result.items().size()).isEqualTo(28);
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
    assertThat(result.items().size()).isEqualTo(5);
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
    assertThat(result.page().totalItems()).isEqualTo(34);
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
            .page(p -> p.after(result.page().endCursor()))
            .send()
            .join();

    assertThat(resultAfter.items().size()).isEqualTo(32);
    final var keyAfter = resultAfter.items().getFirst().getElementInstanceKey();
    // apply searchBefore
    final var resultBefore =
        camundaClient
            .newElementInstanceSearchRequest()
            .page(p -> p.before(resultAfter.page().startCursor()))
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
  void shouldQueryElementInstancesByScopeKeyAsParentProcessInstanceKey() {
    // given
    final var parentProcessInstanceKey =
        camundaClient
            .newProcessInstanceSearchRequest()
            .filter(f -> f.processDefinitionId("subprocess_with_multi_instance"))
            .send()
            .join()
            .singleItem()
            .getProcessInstanceKey();

    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementInstanceScopeKey(parentProcessInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items()).size().isEqualTo(3);
    assertThat(result.items().stream().map(ElementInstance::getElementId))
        .containsExactlyInAnyOrder("start_event", "sub_process", "end_event");
  }

  @Test
  void shouldQueryElementInstancesByScopeKeyAsSubProcessInstanceKey() {
    // given
    final var subProcessInstanceKey =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(
                f ->
                    f.processDefinitionId("subprocess_with_multi_instance")
                        .elementId("sub_process"))
            .send()
            .join()
            .singleItem()
            .getElementInstanceKey();

    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementInstanceScopeKey(subProcessInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items()).size().isEqualTo(3);
    assertThat(result.items().stream().map(ElementInstance::getElementId))
        .containsExactlyInAnyOrder("sub_start_event", "bar_mi_task", "sub_end_event");
  }

  @Test
  void shouldQueryElementInstancesByScopeKeyAsMultiInstanceElementInstanceKey() {
    // given
    final var multiInstanceElementInstanceKey =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(
                f ->
                    f.processDefinitionId("subprocess_with_multi_instance")
                        .elementId("bar_mi_task")
                        .type(ElementInstanceType.MULTI_INSTANCE_BODY))
            .send()
            .join()
            .items()
            .getFirst()
            .getElementInstanceKey();

    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementInstanceScopeKey(multiInstanceElementInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items()).size().isEqualTo(2);
    assertThat(result.items().stream().map(ElementInstance::getElementId))
        .containsExactlyInAnyOrder("bar_mi_task", "bar_mi_task");
  }

  @Test
  void shouldQueryElementInstancesByScopeKeyAsLeafElementInstanceKey() {
    // given
    final var leafElementInstanceKey =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(
                f ->
                    f.processDefinitionId("subprocess_with_multi_instance")
                        .elementId("start_event"))
            .send()
            .join()
            .singleItem()
            .getElementInstanceKey();

    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementInstanceScopeKey(leafElementInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items()).isEmpty();
  }

  @Test
  void shouldQueryElementInstancesByScopeKeyAsNonExistingElementInstanceKey() {
    // given
    final var invalidElementInstanceKey = 123456789L;

    // when
    final var result =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.elementInstanceScopeKey(invalidElementInstanceKey))
            .send()
            .join();

    // then
    assertThat(result.items()).isEmpty();
  }
}
