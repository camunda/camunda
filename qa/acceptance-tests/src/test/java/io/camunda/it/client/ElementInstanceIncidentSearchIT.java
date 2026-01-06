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
import static io.camunda.it.util.TestHelper.waitUntilElementInstanceHasIncidents;
import static io.camunda.it.util.TestHelper.waitUntilIncidentsAreActive;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ProblemException;
import io.camunda.client.api.response.Process;
import io.camunda.client.api.search.enums.IncidentErrorType;
import io.camunda.client.api.search.enums.IncidentState;
import io.camunda.client.api.search.response.ElementInstance;
import io.camunda.client.api.search.response.Incident;
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
class ElementInstanceIncidentSearchIT {

  private static final List<Process> DEPLOYED_PROCESSES = new ArrayList<>();
  private static final int AMOUNT_OF_ELEMENT_INSTANCES_WITH_INCIDENTS = 4;
  private static final int AMOUNT_OF_INCIDENTS = 2;

  private static List<ElementInstance> elementInstancesSortedByKey;
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
    waitUntilElementInstanceHasIncidents(camundaClient, AMOUNT_OF_ELEMENT_INSTANCES_WITH_INCIDENTS);
    waitUntilIncidentsAreActive(camundaClient, AMOUNT_OF_INCIDENTS);

    elementInstancesSortedByKey =
        camundaClient
            .newElementInstanceSearchRequest()
            .filter(f -> f.hasIncident(true))
            .sort(s -> s.elementInstanceKey().asc())
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
  void shouldThrowNotFoundExceptionIfElementInstanceKeyNotFound() {
    // given
    final var elementInstanceKey = 1234567890L;

    // when
    final var exception =
        (ProblemException)
            assertThatThrownBy(
                    () ->
                        camundaClient
                            .newIncidentsByElementInstanceSearchRequest(elementInstanceKey)
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
        .contains("Element Instance with key '%s' not found".formatted(elementInstanceKey));
  }

  @ParameterizedTest
  @MethodSource("provideElementInstanceKeyAndExpectedAmountOfIncidents")
  void testAllIncidentsAreFetched(
      final long elementInstanceKey, final List<Incident> expectedIncidents) {
    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient.newIncidentsByElementInstanceSearchRequest(elementInstanceKey).send().join();

    // then
    assertThat(incidentsResult.items().size()).isEqualTo(expectedIncidents.size());
    assertThat(incidentsResult.items()).containsExactlyElementsOf(expectedIncidents);
  }

  @Test
  void shouldSortByIncidentKeyDescending() {
    // given
    final long elementInstanceKey = elementInstancesSortedByKey.getFirst().getElementInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByElementInstanceSearchRequest(elementInstanceKey)
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
    final var elementInstanceKey = elementInstancesSortedByKey.getFirst().getElementInstanceKey();

    // when
    final var response1 =
        camundaClient
            .newIncidentsByElementInstanceSearchRequest(elementInstanceKey)
            .page(p -> p.limit(1))
            .send()
            .join();
    final var response2 =
        camundaClient
            .newIncidentsByElementInstanceSearchRequest(elementInstanceKey)
            .page(p -> p.after(response1.page().endCursor()))
            .send()
            .join();

    // then
    assertThat(response1.page().totalItems()).isEqualTo(incidentsSortedByKey.size());
    assertThat(response1.items()).containsExactly(incidentsSortedByKey.getFirst());

    assertThat(response2.page().totalItems()).isEqualTo(incidentsSortedByKey.size());
    assertThat(response2.items()).containsExactly(incidentsSortedByKey.get(1));
  }

  private static Stream<Arguments> provideElementInstanceKeyAndExpectedAmountOfIncidents() {

    final var calledErrorIncident =
        incidentsSortedByKey.stream()
            .filter(f -> f.getErrorType().equals(IncidentErrorType.CALLED_ELEMENT_ERROR))
            .toList();

    return Stream.of(
        Arguments.of(
            elementInstancesSortedByKey.getFirst().getElementInstanceKey(), incidentsSortedByKey),
        Arguments.of(
            elementInstancesSortedByKey.get(2).getElementInstanceKey(), calledErrorIncident),
        Arguments.of(
            elementInstancesSortedByKey.get(3).getElementInstanceKey(), calledErrorIncident));
  }

  @Test
  void shouldFilterByErrorTypeOperationEqual() {
    // given
    final var elementInstanceKey = elementInstancesSortedByKey.getFirst().getElementInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByElementInstanceSearchRequest(elementInstanceKey)
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
    final var elementInstanceKey = elementInstancesSortedByKey.getFirst().getElementInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByElementInstanceSearchRequest(elementInstanceKey)
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
    final var elementInstanceKey = elementInstancesSortedByKey.getFirst().getElementInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByElementInstanceSearchRequest(elementInstanceKey)
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
    final var elementInstanceKey = elementInstancesSortedByKey.getFirst().getElementInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByElementInstanceSearchRequest(elementInstanceKey)
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
    final var elementInstanceKey = elementInstancesSortedByKey.getFirst().getElementInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByElementInstanceSearchRequest(elementInstanceKey)
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
    final var elementInstanceKey = elementInstancesSortedByKey.getFirst().getElementInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByElementInstanceSearchRequest(elementInstanceKey)
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
    final var elementInstanceKey = elementInstancesSortedByKey.getFirst().getElementInstanceKey();

    // when
    final SearchResponse<Incident> incidentsResult =
        camundaClient
            .newIncidentsByElementInstanceSearchRequest(elementInstanceKey)
            .filter(f -> f.errorType(e -> e.like("CALLED*")))
            .send()
            .join();

    // then
    assertThat(incidentsResult.items()).hasSize(1);
    assertThat(incidentsResult.items().getFirst().getErrorType())
        .isEqualTo(IncidentErrorType.CALLED_ELEMENT_ERROR);
  }
}
