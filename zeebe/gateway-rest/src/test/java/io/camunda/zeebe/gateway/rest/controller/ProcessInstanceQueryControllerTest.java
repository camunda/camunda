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

import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.SearchQueryResult.Builder;
import io.camunda.service.search.sort.ProcessInstanceSort;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(
    value = ProcessInstanceQueryController.class,
    properties = "camunda.rest.query.enabled=true")
public class ProcessInstanceQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
      {
          "items": [
            {
              "key": "123",
              "processDefinitionName": "Demo Process",
              "processDefinitionVersion": 5,
              "bpmnProcessId": "demoProcess",
              "parentKey": "555",
              "parentFlowNodeInstanceKey": "789",
              "startDate": "2024-01-01T00:00:00Z",
              "state": "ACTIVE",
              "incident": false,
              "hasActiveOperation": false,
              "processDefinitionKey": "777",
              "tenantId": "default"
            }
          ],
          "page": {
              "totalItems": 1,
              "firstSortValues": [],
              "lastSortValues": [
                  "v"
              ]
          }
      }""";
  private static final String PROCESS_INSTANCES_SEARCH_URL = "/v2/process-instances/search";
  private static final SearchQueryResult<ProcessInstanceEntity> SEARCH_QUERY_RESULT =
      new Builder<ProcessInstanceEntity>()
          .total(1L)
          .items(
              List.of(
                  new ProcessInstanceEntity(
                      123L,
                      "Demo Process",
                      5,
                      "demoProcess",
                      555L,
                      789L,
                      "2024-01-01T00:00:00Z",
                      null,
                      "ACTIVE",
                      false,
                      false,
                      777L,
                      "default",
                      null,
                      null,
                      null)))
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
                        .bpmnProcessId()
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
}
