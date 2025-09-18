/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.client.api.search.enums.IncidentState.ACTIVE;
import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreActive;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.ConsistencyPolicy;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.Incident;
import io.camunda.qa.util.multidb.MultiDbTest;
import io.camunda.webapps.schema.entities.incident.ErrorType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

@MultiDbTest
class IncidentSearchTest {

  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  private static final int AMOUNT_OF_INCIDENTS = 3;

  private static CamundaClient camundaClient;

  private static Incident incident;

  @BeforeAll
  static void beforeAll() {

    final var processes =
        List.of("service_tasks_v1.bpmn", "service_tasks_v2.bpmn", "incident_process_v1.bpmn");
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(camundaClient, String.format("process/%s", process))
                    .getProcesses()));

    waitForProcessesToBeDeployed(camundaClient, 3);

    startProcessInstance(camundaClient, "service_tasks_v1");
    startProcessInstance(camundaClient, "service_tasks_v2", "{\"path\":222}");
    startProcessInstance(camundaClient, "incident_process_v1");
    startProcessInstance(camundaClient, "incident_process_v1");
    startProcessInstance(camundaClient, "incident_process_v1");

    waitForProcessInstancesToStart(camundaClient, 5);
    waitUntilProcessInstanceHasIncidents(camundaClient, AMOUNT_OF_INCIDENTS);
    waitUntilIncidentsAreActive(camundaClient, AMOUNT_OF_INCIDENTS);

    incident =
        camundaClient
            .newIncidentSearchRequest()
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join()
            .items()
            .getFirst();
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
  }

  @Test
  void testIncidentsAreActive() {
    // given
    waitUntilIncidentsAreActive(camundaClient, AMOUNT_OF_INCIDENTS);

    // when
    final List<Incident> incidents =
        camundaClient
            .newIncidentSearchRequest()
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join()
            .items();

    // then incidents are updated by background task, PENDING state is changed on ACTIVE
    assertThat(incidents).extracting(Incident::getState).containsOnly(ACTIVE);
  }

  @Test
  void shouldGetIncidentByKey() {
    // given
    final var incidentKey = incident.getIncidentKey();
    // when
    final var result =
        camundaClient
            .newIncidentGetRequest(incidentKey)
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(result).isNotNull();
    assertThat(result.getIncidentKey()).isEqualTo(incidentKey);
  }

  @Test
  void shouldThrownExceptionIfNotFoundByKey() {
    // given
    final var invalidIncidentKey = 0xCAFE;

    // when / then
    final var exception =
        (ProblemException)
            assertThatThrownBy(
                    () ->
                        camundaClient
                            .newIncidentGetRequest(invalidIncidentKey)
                            .consistencyPolicy(ConsistencyPolicy.noWait())
                            .send()
                            .join())
                .isInstanceOf(ProblemException.class)
                .actual();
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
    assertThat(exception.details().getDetail())
        .contains("Incident with key '%s' not found".formatted(invalidIncidentKey));
  }

  @Test
  void shouldFilterByKey() {
    // given
    final var incidentKey = incident.getIncidentKey();

    // when
    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.incidentKey(incidentKey))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

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
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.processInstanceKey(processInstanceKey))
            .consistencyPolicy(ConsistencyPolicy.noWait())
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
    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.state(IncidentState.valueOf(state.name())))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().getFirst().getState()).isEqualTo(state);
  }

  @Test
  void shouldFilterByElementInstanceKey() {
    // given
    final var elementInstanceKey = incident.getElementInstanceKey();

    // when
    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.elementInstanceKey(elementInstanceKey))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(1);
    assertThat(result.items().getFirst().getElementInstanceKey()).isEqualTo(elementInstanceKey);
  }

  @Test
  void shouldFilterByProcessDefinitionKey() {
    // given
    final var processDefinitionKey = incident.getProcessDefinitionKey();

    // when
    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.processDefinitionKey(processDefinitionKey))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().getFirst().getProcessDefinitionKey()).isEqualTo(processDefinitionKey);
  }

  @Test
  void shouldFilterByProcessDefinitionId() {
    // given
    final var processDefinitionId = incident.getProcessDefinitionId();

    // when
    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.processDefinitionId(processDefinitionId))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().getFirst().getProcessDefinitionId()).isEqualTo(processDefinitionId);
  }

  @Test
  void shouldFilterByErrorType() {
    // given
    final var errorType = incident.getErrorType();

    // when
    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.errorType(IncidentErrorType.valueOf(errorType.name())))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().getFirst().getErrorType()).isEqualTo(errorType);
  }

  @ParameterizedTest(name = "Querying incidents with filter.errorType = ''{0}''")
  @EnumSource(value = ErrorType.class)
  void shouldRecognizeAllErrorTypesSupportedByOperateInIncidentQuery(final ErrorType errorType) {
    assertThatCode(
            () ->
                camundaClient
                    .newIncidentSearchRequest()
                    .filter(f -> f.errorType(IncidentErrorType.valueOf(errorType.name())))
                    .consistencyPolicy(ConsistencyPolicy.noWait())
                    .send()
                    .join())
        .describedAs(
            """
                Incident query should execute successfully for filter.errorType = '%1$s'.
                If it fails, ensure the following are updated:
                  - `client` module: `io.camunda.client.api.search.enums.IncidentErrorType` - '%1$s' type defined
                  - `search-domain` module: `io.camunda.client.api.search.enums.IncidentErrorType` - '%1$s' type defined
                  - `gateway-protocol` module: `zeebe/gateway-protocol/src/main/proto/rest-api.yaml` - '%1$s' `errorType` defined for `IncidentFilterRequestBase` document""",
            errorType)
        .doesNotThrowAnyException();
  }

  @Test
  void shouldFilterByElementId() {
    // given
    final var elementId = incident.getElementId();

    // when
    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.elementId(elementId))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().getFirst().getElementId()).isEqualTo(elementId);
  }

  @Test
  void shouldFilterByJobKey() {
    // given
    final var jobKey = incident.getJobKey();

    // when
    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.jobKey(jobKey))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().getFirst().getJobKey()).isEqualTo(jobKey);
  }

  @Test
  void shouldFilterByTenantId() {
    // given
    final var tenantId = incident.getTenantId();

    // when
    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .filter(f -> f.tenantId(tenantId))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(result.items().size()).isEqualTo(3);
    assertThat(result.items().getFirst().getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void shouldSortByIncidentKey() {
    final var resultAsc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.processInstanceKey().asc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.processInstanceKey().desc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

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
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.errorType().asc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.errorType().desc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

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
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.processDefinitionKey().asc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.processDefinitionKey().desc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

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
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.processInstanceKey().asc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.processInstanceKey().desc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

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
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.tenantId().asc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.tenantId().desc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    final var all = resultAsc.items().stream().map(Incident::getTenantId).toList();
    final var sortedAsc = all.stream().sorted().toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    assertThat(resultAsc.items().stream().map(Incident::getTenantId).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getTenantId).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortByElementInstanceId() {
    final var resultAsc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.elementInstanceKey().asc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.elementInstanceKey().desc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    final var all = resultAsc.items().stream().map(Incident::getElementInstanceKey).toList();
    final var sortedAsc = all.stream().sorted().toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    assertThat(resultAsc.items().stream().map(Incident::getElementInstanceKey).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getElementInstanceKey).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortByElementId() {
    final var resultAsc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.elementId().asc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.elementId().desc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    final var all = resultAsc.items().stream().map(Incident::getElementId).toList();
    final var sortedAsc = all.stream().sorted().toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    assertThat(resultAsc.items().stream().map(Incident::getElementId).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getElementId).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSortByState() {
    final var resultAsc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.state().asc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.state().desc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    final var all = resultAsc.items().stream().map(Incident::getState).toList();
    final var sortedAsc = all.stream().sorted().toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    assertThat(resultAsc.items().stream().map(Incident::getState).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getState).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  // TO-DO: Verify the following test case: the getJobKey is returning null, which is not really
  // sorting the incidents by job key
  @Test
  void shouldSortByJobKey() {
    // when
    final var resultAsc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.jobKey().asc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.jobKey().desc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    final var ascJobKeys =
        resultAsc.items().stream().map(Incident::getJobKey).filter(Objects::nonNull).toList();

    final var descJobKeys =
        resultDesc.items().stream().map(Incident::getJobKey).filter(Objects::nonNull).toList();

    final var expectedAscOrder = new ArrayList<>(ascJobKeys);
    expectedAscOrder.sort(Comparator.nullsFirst(Comparator.naturalOrder()));

    final var expectedDescOrder = new ArrayList<>(ascJobKeys);
    expectedDescOrder.sort(Comparator.nullsLast(Comparator.reverseOrder()));

    // then
    assertThat(ascJobKeys).containsExactlyElementsOf(expectedAscOrder);
    assertThat(descJobKeys).containsExactlyElementsOf(expectedDescOrder);
  }

  @Test
  void shouldSortByCreationTime() {
    final var resultAsc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.creationTime().asc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    final var resultDesc =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.creationTime().desc())
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    final var all = resultAsc.items().stream().map(Incident::getCreationTime).toList();
    final var sortedAsc = all.stream().sorted().toList();
    final var sortedDesc = all.stream().sorted(Comparator.reverseOrder()).toList();

    assertThat(resultAsc.items().stream().map(Incident::getCreationTime).toList())
        .containsExactlyElementsOf(sortedAsc);
    assertThat(resultDesc.items().stream().map(Incident::getCreationTime).toList())
        .containsExactlyElementsOf(sortedDesc);
  }

  @Test
  void shouldSearchAfterSecondItem() {
    // when
    final var resultAll =
        camundaClient
            .newIncidentSearchRequest()
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    final var thirdIncidentKey = resultAll.items().get(2).getIncidentKey();

    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .page(p -> p.limit(2))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    final var resultSearchAfter =
        camundaClient
            .newIncidentSearchRequest()
            .page(p -> p.limit(1).after(result.page().endCursor()))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(resultSearchAfter.items().stream().findFirst().get().getIncidentKey())
        .isEqualTo(thirdIncidentKey);
  }

  @Test
  void shouldSearchBeforeSecondItem() {
    // when
    final var resultAll =
        camundaClient
            .newIncidentSearchRequest()
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    final var firstIncidentKey = resultAll.items().get(0).getIncidentKey();

    final var result =
        camundaClient
            .newIncidentSearchRequest()
            .page(p -> p.limit(2).from(1))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    final var resultSearchBefore =
        camundaClient
            .newIncidentSearchRequest()
            .page(p -> p.limit(1).before(result.page().startCursor()))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(resultSearchBefore.items().stream().findFirst().get().getIncidentKey())
        .isEqualTo(firstIncidentKey);
  }

  @Test
  void shouldSearchByFromWithLimit() {
    // when
    final var resultAll =
        camundaClient
            .newIncidentSearchRequest()
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    final var resultWithLimit =
        camundaClient
            .newIncidentSearchRequest()
            .page(p -> p.limit(2))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();
    assertThat(resultWithLimit.items().size()).isEqualTo(2);

    final var thirdKey = resultAll.items().get(2).getIncidentKey();

    final var resultSearchFrom =
        camundaClient
            .newIncidentSearchRequest()
            .page(p -> p.limit(2).from(2))
            .consistencyPolicy(ConsistencyPolicy.noWait())
            .send()
            .join();

    // then
    assertThat(resultSearchFrom.items().stream().findFirst().get().getIncidentKey())
        .isEqualTo(thirdKey);
  }
}
