/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.security.auth.Authentication;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.zeebe.gateway.rest.RestConfiguration;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

@Import(RestConfiguration.class)
@WebMvcTest(
    value = ProcessInstanceQueryController.class,
    properties = "camunda.rest.query.enabled=true")
public class ProcessInstanceQueryControllerTest extends RestControllerTest {

  private static final String PROCESS_INSTANCES_SEARCH_URL = "/v2/process-instances/search";
  private static final String PROCESS_INSTANCES_BY_KEY_URL =
      "/v2/process-instances/{processInstanceKey}";

  private static final ProcessInstanceEntity PROCESS_INSTANCE_ENTITY =
      new ProcessInstanceEntity(
          123L,
          "demoProcess",
          "Demo Process",
          5,
          "v5",
          789L,
          333L,
          777L,
          "PI_1/PI_2",
          "2024-01-01T00:00:00Z",
          null,
          ProcessInstanceEntity.ProcessInstanceState.ACTIVE,
          false,
          "tenant");

  private static final String PROCESS_INSTANCE_ENTITY_JSON =
      """
            {
            "processInstanceKey": 123,
            "processDefinitionId": "demoProcess",
            "processDefinitionName": "Demo Process",
            "processDefinitionVersion": 5,
            "processDefinitionVersionTag": "v5",
            "processDefinitionKey": 789,
            "parentProcessInstanceKey": 333,
            "parentFlowNodeInstanceKey": 777,
            "treePath": "PI_1/PI_2",
            "startDate": "2024-01-01T00:00:00Z",
            "state": "ACTIVE",
            "hasIncident": false,
            "tenantId": "tenant"
          }
          """;

  private static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
              "items": [
                {
                  "processInstanceKey": 123,
                  "processDefinitionId": "demoProcess",
                  "processDefinitionName": "Demo Process",
                  "processDefinitionVersion": 5,
                  "processDefinitionVersionTag": "v5",
                  "processDefinitionKey": 789,
                  "parentProcessInstanceKey": 333,
                  "parentFlowNodeInstanceKey": 777,
                  "treePath": "PI_1/PI_2",
                  "startDate": "2024-01-01T00:00:00Z",
                  "state": "ACTIVE",
                  "hasIncident": false,
                  "tenantId": "tenant"
                }
              ],
              "page": {
                  "totalItems": 1,
                  "firstSortValues": [],
                  "lastSortValues": [
                      "v"
                  ]
              }
          }
          """;

  private static final SearchQueryResult<ProcessInstanceEntity> SEARCH_QUERY_RESULT =
      new Builder<ProcessInstanceEntity>()
          .total(1L)
          .items(List.of(PROCESS_INSTANCE_ENTITY))
          .sortValues(new Object[] {"v"})
          .build();

  @MockBean ProcessInstanceServices processInstanceServices;

  @BeforeEach
  void setupServices() {
    when(processInstanceServices.withAuthentication(any(Authentication.class)))
        .thenReturn(processInstanceServices);
  }

  @Test
  void shouldSearchProcessInstancesWithEmptyBody() {
    // given
    when(processInstanceServices.search(any(ProcessInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(processInstanceServices).search(new ProcessInstanceQuery.Builder().build());
  }

  @Test
  void shouldSearchProcessInstancesWithEmptyQuery() {
    // given
    when(processInstanceServices.search(any(ProcessInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final String request = "{}";
    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(processInstanceServices).search(new ProcessInstanceQuery.Builder().build());
  }

  @Test
  void shouldSearchProcessInstancessWithSorting() {
    // given
    when(processInstanceServices.search(any(ProcessInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
            {
                "sort": [
                    {
                        "field": "bpmnProcessId",
                        "order": "desc"
                    },
                    {
                        "field": "processDefinitionKey",
                        "order": "asc"
                    }
                ]
            }""";
    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(processInstanceServices)
        .search(
            new ProcessInstanceQuery.Builder()
                .sort(
                    new ProcessInstanceSort.Builder()
                        .processDefinitionId()
                        .desc()
                        .processDefinitionKey()
                        .asc()
                        .build())
                .build());
  }

  @Test
  void shouldInvalidateProcessInstancesSearchQueryWithBadSortOrder() {
    // given
    final var request =
        """
            {
                "sort": [
                    {
                        "field": "bpmnProcessId",
                        "order": "dsc"
                    }
                ]
            }""";
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "Unknown sortOrder: dsc.",
                  "instance": "%s"
                }""",
            PROCESS_INSTANCES_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse);

    verify(processInstanceServices, never()).search(any(ProcessInstanceQuery.class));
  }

  @Test
  void shouldInvalidateProcessInstancesSearchQueryWithBadSortField() {
    // given
    final var request =
        """
            {
                "sort": [
                    {
                        "field": "unknownField",
                        "order": "asc"
                    }
                ]
            }""";
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "Unknown sortBy: unknownField.",
                  "instance": "%s"
                }""",
            PROCESS_INSTANCES_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse);

    verify(processInstanceServices, never()).search(any(ProcessInstanceQuery.class));
  }

  @Test
  void shouldInvalidateProcessInstancesSearchQueryWithMissingSortField() {
    // given
    final var request =
        """
            {
                "sort": [
                    {
                        "order": "asc"
                    }
                ]
            }""";
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "Sort field must not be null.",
                  "instance": "%s"
                }""",
            PROCESS_INSTANCES_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse);

    verify(processInstanceServices, never()).search(any(ProcessInstanceQuery.class));
  }

  @Test
  void shouldInvalidateProcessInstancesSearchQueryWithConflictingPagination() {
    // given
    final var request =
        """
            {
                "page": {
                    "searchAfter": ["a"],
                    "searchBefore": ["b"]
                }
            }""";
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "Both searchAfter and searchBefore cannot be set at the same time.",
                  "instance": "%s"
                }""",
            PROCESS_INSTANCES_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse);

    verify(processInstanceServices, never()).search(any(ProcessInstanceQuery.class));
  }

  @Test
  public void shouldReturnProcessInstanceForValidKey() {
    // given
    final var validProcesInstanceKey = 123L;
    when(processInstanceServices.getByKey(validProcesInstanceKey))
        .thenReturn(PROCESS_INSTANCE_ENTITY);

    // when / then
    webClient
        .get()
        .uri(PROCESS_INSTANCES_BY_KEY_URL, validProcesInstanceKey)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(PROCESS_INSTANCE_ENTITY_JSON);

    // Verify that the service was called with the valid key
    verify(processInstanceServices).getByKey(validProcesInstanceKey);
  }

  @Test
  public void shouldReturn404ForInvalidProcessInstaceKey() {
    // given
    final var invalidProcesInstanceKey = 100L;
    when(processInstanceServices.getByKey(invalidProcesInstanceKey))
        .thenThrow(
            new NotFoundException(
                String.format("Process Instance with key %d not found", invalidProcesInstanceKey)));
    // when / then
    webClient
        .get()
        .uri(PROCESS_INSTANCES_BY_KEY_URL, invalidProcesInstanceKey)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .json(
            """
                    {
                      "type": "about:blank",
                      "status": 404,
                      "title": "NOT_FOUND",
                      "detail": "Process Instance with key 100 not found"
                    }
                """);

    // Verify that the service was called with the invalid key
    verify(processInstanceServices).getByKey(invalidProcesInstanceKey);
  }

  @SafeVarargs
  private static Arguments processInstanceKeyArguments(
      final String filterValue, final Operation<Long>... operations) {
    final var filter = new ProcessInstanceFilter.Builder().processInstanceKeys(operations).build();
    return Arguments.of("processInstanceKey", filterValue, filter);
  }

  @SafeVarargs
  private static Arguments processDefinitionKeyArguments(
      final String filterValue, final Operation<Long>... operations) {
    final var filter =
        new ProcessInstanceFilter.Builder().processDefinitionKeys(operations).build();
    return Arguments.of("processDefinitionKey", filterValue, filter);
  }

  @SafeVarargs
  private static Arguments processDefinitionVersions(
      final String filterValue, final Operation<Integer>... operations) {
    final var filter =
        new ProcessInstanceFilter.Builder().processDefinitionVersions(operations).build();
    return Arguments.of("processDefinitionVersion", filterValue, filter);
  }

  @SafeVarargs
  private static Arguments processDefinitionIdArguments(
      final String filterValue, final Operation<String>... operations) {
    final var filter = new ProcessInstanceFilter.Builder().processDefinitionIds(operations).build();
    return Arguments.of("processDefinitionId", filterValue, filter);
  }

  private static Stream<Arguments> provideAdvancedSearchParameters() {
    final String inFilter = "\"$in\": [\"some\", \"thing\"]";
    final String likeFilter = "\"$like\": \"some*\"";
    final var inOperation = Operation.in("some", "thing");
    final var likeOperation = Operation.like("some*");
    return Stream.of(
        // processInstanceKeys
        processInstanceKeyArguments("10", Operation.eq(10L)),
        processInstanceKeyArguments("{\"$eq\": 1}", Operation.eq(1L)),
        processInstanceKeyArguments("{\"$neq\": 1}", Operation.neq(1L)),
        processInstanceKeyArguments("{\"$gt\": 5}", Operation.gt(5L)),
        processInstanceKeyArguments("{\"$gte\": 5}", Operation.gte(5L)),
        processInstanceKeyArguments("{\"$lt\": 5}", Operation.lt(5L)),
        processInstanceKeyArguments("{\"$lte\": 5}", Operation.lte(5L)),
        processInstanceKeyArguments(
            "{\"$gt\": 5, \"$lt\": 10}", Operation.gt(5L), Operation.lt(10L)),
        // processDefinitionKeys
        processDefinitionKeyArguments("10", Operation.eq(10L)),
        processDefinitionKeyArguments("{\"$eq\": 1}", Operation.eq(1L)),
        processDefinitionKeyArguments("{\"$neq\": 1}", Operation.neq(1L)),
        processDefinitionKeyArguments("{\"$gt\": 5}", Operation.gt(5L)),
        processDefinitionKeyArguments("{\"$gte\": 5}", Operation.gte(5L)),
        processDefinitionKeyArguments("{\"$lt\": 5}", Operation.lt(5L)),
        processDefinitionKeyArguments("{\"$lte\": 5}", Operation.lte(5L)),
        processDefinitionKeyArguments(
            "{\"$gt\": 5, \"$lt\": 10}", Operation.gt(5L), Operation.lt(10L)),
        // processInstanceKeys
        processDefinitionVersions("10", Operation.eq(10)),
        processDefinitionVersions("{\"$eq\": 1}", Operation.eq(1)),
        processDefinitionVersions("{\"$neq\": 1}", Operation.neq(1)),
        processDefinitionVersions("{\"$gt\": 5}", Operation.gt(5)),
        processDefinitionVersions("{\"$gte\": 5}", Operation.gte(5)),
        processDefinitionVersions("{\"$lt\": 5}", Operation.lt(5)),
        processDefinitionVersions("{\"$lte\": 5}", Operation.lte(5)),
        processDefinitionVersions("{\"$gt\": 5, \"$lt\": 10}", Operation.gt(5), Operation.lt(10)),
        // processDefinitionIds
        processDefinitionIdArguments("\"something\"", Operation.eq("something")),
        processDefinitionIdArguments("{\"$eq\": \"something\"}", Operation.eq("something")),
        processDefinitionIdArguments("{%s}".formatted(likeFilter), likeOperation),
        processDefinitionIdArguments("{%s}".formatted(inFilter), inOperation),
        processDefinitionIdArguments(
            "{%s, %s}".formatted(inFilter, likeFilter), inOperation, likeOperation));
  }

  @ParameterizedTest
  @MethodSource("provideAdvancedSearchParameters")
  void shouldSearchProcessInstancesWithAdvancedFilter(
      final String filterKey, final String filterValue, final ProcessInstanceFilter filter) {
    // given
    final var request =
        """
            {
                "filter": { "%s": %s }
            }"""
            .formatted(filterKey, filterValue);
    System.out.println("request = " + request);
    when(processInstanceServices.search(any(ProcessInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(processInstanceServices)
        .search(new ProcessInstanceQuery.Builder().filter(filter).build());
  }
}
