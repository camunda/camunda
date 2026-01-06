/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.it.client;

import static io.camunda.it.util.TestHelper.deployResource;
import static io.camunda.it.util.TestHelper.startProcessInstance;
import static io.camunda.it.util.TestHelper.waitForProcessInstancesToStart;
import static io.camunda.it.util.TestHelper.waitForProcessesToBeDeployed;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreActive;
import static io.camunda.it.util.TestHelper.waitUntilProcessInstanceHasIncidents;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.search.response.ProcessInstance;
import io.camunda.client.api.search.response.SearchResponse;
import io.camunda.qa.util.multidb.MultiDbTest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@MultiDbTest
class ProcessInstanceIncidentSearchTest {

  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  private static final int AMOUNT_OF_PROCESS_INSTANCES_WITH_INCIDENTS = 3;
  private static final int AMOUNT_OF_INCIDENTS = 2;

  private static List<ProcessInstance> processInstancesSortedByKey;
  private static List<Incident> incidentsSortedByKey;

  private static CamundaClient camundaClient;

  @BeforeAll
  static void beforeAll() {

    final var processes =
        List.of(
            "process_with_call_activity_1_v1.bpmn",
            "process_with_call_activity_2_and_incident_v1.bpmn",
            "process_with_incident_v1.bpmn");
    processes.forEach(
        process ->
            DEPLOYED_PROCESSES.addAll(
                deployResource(camundaClient, String.format("process/%s", process))
                    .getProcesses()));

    waitForProcessesToBeDeployed(camundaClient, 3);
    startProcessInstance(camundaClient, "process_with_call_activity_1_v1");
    waitForProcessInstancesToStart(camundaClient, 3);
    waitUntilProcessInstanceHasIncidents(camundaClient, AMOUNT_OF_PROCESS_INSTANCES_WITH_INCIDENTS);
    waitUntilIncidentsAreActive(camundaClient, AMOUNT_OF_INCIDENTS);

    processInstancesSortedByKey =
        camundaClient
            .newProcessInstanceSearchRequest()
            .sort(s -> s.processInstanceKey().asc())
            .send()
            .join()
            .items();
    incidentsSortedByKey =
        camundaClient
            .newIncidentSearchRequest()
            .sort(s -> s.incidentKey().asc())
            .send()
            .join()
            .items();
  }

  @AfterAll
  static void afterAll() {
    DEPLOYED_PROCESSES.clear();
  }

  @Test
  void shouldThrowNotFoundExceptionIfProcessInstanceKeyNotFound() {
    // given
    final var processInstanceKey = 1234567890L;

    // when
    final var exception =
        (ProblemException)
            assertThatThrownBy(
                    () ->
                        camundaClient
                            .newIncidentsByProcessInstanceSearchRequest(processInstanceKey)
                            .send()
                            .join())
                .isInstanceOf(ProblemException.class)
                .actual();

    // then
    assertThat(exception.getMessage()).startsWith("Failed with code 404");
    assertThat(exception.details()).isNotNull();
    assertThat(exception.details().getTitle()).isEqualTo("NOT_FOUND");
    assertThat(exception.details().getStatus()).isEqualTo(404);
    assertThat(exception.details().getDetail())
        .contains("Process Instance with key '%s' not found".formatted(processInstanceKey));
  }

  @ParameterizedTest
  @MethodSource("provideProcessInstanceKeyAndExpectedAmountOfIncidents")
  void testAllIncidentsAreFetched(
      final long processInstanceKey, final List<Incident> expectedIncidents) {
    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient.newIncidentsByProcessInstanceSearchRequest(processInstanceKey).send().join();

    // then
    assertThat(incidentsResult.items().size()).isEqualTo(expectedIncidents.size());
    assertThat(incidentsResult.items()).containsExactlyElementsOf(expectedIncidents);
  }

  @Test
  void shouldSortByIncidentKeyDescending() {
    // given
    final long processInstanceKey = processInstancesSortedByKey.getFirst().getProcessInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByProcessInstanceSearchRequest(processInstanceKey)
            .sort(s -> s.incidentKey().desc())
            .send()
            .join();

    // then
    assertThat(incidentsResult.items().size()).isEqualTo(incidentsSortedByKey.size());

    final var sortedIncidentsInDesc =
        incidentsSortedByKey.stream()
            .sorted(Comparator.comparingLong(inc -> -inc.getIncidentKey()))
            .toList();
    assertThat(incidentsResult.items()).containsExactlyElementsOf(sortedIncidentsInDesc);
  }

  @Test
  void shouldPaginateWithLimitAndCursor() {
    // given
    final var processInstanceKey = processInstancesSortedByKey.getFirst().getProcessInstanceKey();

    // when
    final var response1 =
        camundaClient
            .newIncidentsByProcessInstanceSearchRequest(processInstanceKey)
            .page(p -> p.limit(1))
            .send()
            .join();
    final var response2 =
        camundaClient
            .newIncidentsByProcessInstanceSearchRequest(processInstanceKey)
            .page(p -> p.after(response1.page().endCursor()))
            .send()
            .join();

    // then
    assertThat(response1.page().totalItems()).isEqualTo(incidentsSortedByKey.size());
    assertThat(response1.items()).containsExactly(incidentsSortedByKey.getFirst());

    assertThat(response2.page().totalItems()).isEqualTo(incidentsSortedByKey.size());
    assertThat(response2.items()).containsExactly(incidentsSortedByKey.get(1));
  }

  private static Stream<Arguments> provideProcessInstanceKeyAndExpectedAmountOfIncidents() {
    final var processInstance1 = processInstancesSortedByKey.get(0);
    final var processInstance1Incidents = incidentsSortedByKey;

    final var processInstance2 = processInstancesSortedByKey.get(1);
    final var processInstance2Incidents = incidentsSortedByKey;

    final var processInstance3 = processInstancesSortedByKey.get(2);
    final var processInstance3Incidents = List.of(incidentsSortedByKey.get(1));

    return Stream.of(
        Arguments.of(processInstance1.getProcessInstanceKey(), processInstance1Incidents),
        Arguments.of(processInstance2.getProcessInstanceKey(), processInstance2Incidents),
        Arguments.of(processInstance3.getProcessInstanceKey(), processInstance3Incidents));
  }

  @Test
  void shouldFilterByErrorTypeOperationEqual() {
    // given
    final var processInstanceKey = processInstancesSortedByKey.getFirst().getProcessInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByProcessInstanceSearchRequest(processInstanceKey)
            .filter(f -> f.errorType(e -> e.eq(IncidentErrorType.CALLED_ELEMENT_ERROR)))
            .send()
            .join();

    // then
    assertThat(incidentsResult.items()).hasSize(1);
    assertThat(incidentsResult.items().getFirst().getErrorType())
        .isEqualTo(IncidentErrorType.CALLED_ELEMENT_ERROR);
  }

  @Test
  void shouldFilterByErrorTypeOperationNotEqual() {
    // given
    final var processInstanceKey = processInstancesSortedByKey.getFirst().getProcessInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByProcessInstanceSearchRequest(processInstanceKey)
            .filter(f -> f.errorType(e -> e.neq(IncidentErrorType.CALLED_ELEMENT_ERROR)))
            .send()
            .join();

    // then
    assertThat(incidentsResult.items()).hasSize(1);
    assertThat(incidentsResult.items().getFirst().getErrorType())
        .isNotEqualTo(IncidentErrorType.CALLED_ELEMENT_ERROR);
  }

  @Test
  void shouldFilterByErrorTypeOperationExists() {
    // given
    final var processInstanceKey = processInstancesSortedByKey.getFirst().getProcessInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByProcessInstanceSearchRequest(processInstanceKey)
            .filter(f -> f.errorType(e -> e.exists(true)))
            .send()
            .join();

    // then
    assertThat(incidentsResult.items()).hasSize(2);
    assertThat(incidentsResult.items())
        .extracting(Incident::getErrorType)
        .containsExactlyInAnyOrder(
            IncidentErrorType.CALLED_ELEMENT_ERROR, IncidentErrorType.FORM_NOT_FOUND);
  }

  @Test
  void shouldFilterByErrorTypeOperationIn() {
    // given
    final var processInstanceKey = processInstancesSortedByKey.getFirst().getProcessInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByProcessInstanceSearchRequest(processInstanceKey)
            .filter(
                f ->
                    f.errorType(
                        e ->
                            e.in(
                                List.of(
                                    IncidentErrorType.CALLED_ELEMENT_ERROR,
                                    IncidentErrorType.FORM_NOT_FOUND))))
            .send()
            .join();

    // then
    assertThat(incidentsResult.items()).hasSize(2);
    assertThat(incidentsResult.items())
        .extracting(Incident::getErrorType)
        .containsExactlyInAnyOrder(
            IncidentErrorType.CALLED_ELEMENT_ERROR, IncidentErrorType.FORM_NOT_FOUND);
  }

  @Test
  void shouldFilterByErrorTypeOperationNotIn() {
    // given
    final var processInstanceKey = processInstancesSortedByKey.getFirst().getProcessInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByProcessInstanceSearchRequest(processInstanceKey)
            .filter(
                f ->
                    f.errorType(
                        e ->
                            e.notIn(
                                List.of(
                                    IncidentErrorType.CALLED_ELEMENT_ERROR,
                                    IncidentErrorType.FORM_NOT_FOUND))))
            .send()
            .join();

    // then
    assertThat(incidentsResult.items()).hasSize(0);
  }

  @Test
  void shouldFilterByIncidentStateOperationNotIn() {
    // given
    final var processInstanceKey = processInstancesSortedByKey.getFirst().getProcessInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByProcessInstanceSearchRequest(processInstanceKey)
            .filter(
                f -> f.state(e -> e.notIn(List.of(IncidentState.RESOLVED, IncidentState.PENDING))))
            .send()
            .join();

    // then
    assertThat(incidentsResult.items()).hasSize(2);
  }

  @Test
  void shouldFilterByErrorTypeOperationLike() {
    // given
    final var processInstanceKey = processInstancesSortedByKey.getFirst().getProcessInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByProcessInstanceSearchRequest(processInstanceKey)
            .filter(f -> f.errorType(e -> e.like("CALLED*")))
            .send()
            .join();

    // then
    assertThat(incidentsResult.items()).hasSize(1);
    assertThat(incidentsResult.items().getFirst().getErrorType())
        .isEqualTo(IncidentErrorType.CALLED_ELEMENT_ERROR);
  }
}
