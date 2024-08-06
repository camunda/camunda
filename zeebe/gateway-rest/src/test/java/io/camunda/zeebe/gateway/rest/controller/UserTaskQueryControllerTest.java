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

import io.camunda.service.UserTaskServices;
import io.camunda.service.entities.UserTaskEntity;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.SearchQueryResult.Builder;
import io.camunda.service.search.query.UserTaskQuery;
import io.camunda.service.search.sort.UserTaskSort;
import io.camunda.service.security.auth.Authentication;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(value = UserTaskQueryController.class, properties = "camunda.rest.query.enabled=true")
public class UserTaskQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
      {
          "items": [
              {
                  "tenantIds": "t",
                  "key": 0,
                  "processInstanceKey": 1,
                  "processDefinitionKey": 2,
                  "elementInstanceKey": 3,
                  "bpmnProcessId": "b",
                  "state": "s",
                  "assignee": "a",
                  "candidateUser": [],
                  "candidateGroup": [],
                  "formKey": 0,
                  "elementId": "e",
                  "creationDate": "00:00:00.000Z+00:00",
                  "completionDate": "00:00:00.000Z+00:00",
                  "dueDate": "00:00:00.000Z+00:00",
                  "followUpDate": "00:00:00.000Z+00:00",
                  "externalFormReference": "efr",
                  "processDefinitionVersion": 1,
                  "customHeaders": {}
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
  private static final String USER_TASKS_SEARCH_URL = "/v2/user-tasks/search";
  private static final SearchQueryResult<UserTaskEntity> SEARCH_QUERY_RESULT =
      new Builder<UserTaskEntity>()
          .total(1L)
          .items(
              List.of(
                  new UserTaskEntity(
                      0L, // key
                      "e", // flowNodeBpmnId
                      "b", // bpmnProcessId
                      "00:00:00.000Z+00:00", // creationTime
                      "00:00:00.000Z+00:00", // completionTime
                      "a", // assignee
                      "s", // state
                      0L, // formKey (adjusted to match expected value)
                      2L, // processDefinitionId
                      1L, // processInstanceId
                      3L, // flowNodeInstanceId
                      "t", // tenantId
                      "00:00:00.000Z+00:00", // dueDate
                      "00:00:00.000Z+00:00", // followUpDate
                      new ArrayList<>(), // candidateGroups
                      new ArrayList<>(), // candidateUsers
                      "efr", // externalFormReference
                      1, // processDefinitionVersion
                      Collections.emptyMap() // customHeaders
                      )))
          .sortValues(new Object[] {"v"})
          .build();
  @MockBean UserTaskServices userTaskServices;

  @BeforeEach
  void setupServices() {
    when(userTaskServices.withAuthentication(any(Authentication.class)))
        .thenReturn(userTaskServices);
  }

  @Test
  void shouldSearchUserTasksWithEmptyBody() {
    // given
    when(userTaskServices.search(any(UserTaskQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(USER_TASKS_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(userTaskServices).search(new UserTaskQuery.Builder().build());
  }

  @Test
  void shouldSearchUserTasksWithEmptyQuery() {
    // given
    when(userTaskServices.search(any(UserTaskQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
    final String request = "{}";
    // when / then
    webClient
        .post()
        .uri(USER_TASKS_SEARCH_URL)
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

    verify(userTaskServices).search(new UserTaskQuery.Builder().build());
  }

  @Test
  void shouldSearchUserTasksWithSorting() {
    // given
    when(userTaskServices.search(any(UserTaskQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
        {
            "sort": [
                {
                    "field": "creationTime",
                    "order": "desc"
                },
                {
                    "field": "completionTime",
                    "order": "asc"
                }
            ]
        }""";
    // when / then
    webClient
        .post()
        .uri(USER_TASKS_SEARCH_URL)
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

    verify(userTaskServices)
        .search(
            new UserTaskQuery.Builder()
                .sort(
                    new UserTaskSort.Builder().creationDate().desc().completionDate().asc().build())
                .build());
  }

  @Test
  void shouldInvalidateUserTasksSearchQueryWithBadSortOrder() {
    // given
    final var request =
        """
        {
            "sort": [
                {
                    "field": "creationTime",
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
            USER_TASKS_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(USER_TASKS_SEARCH_URL)
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

    verify(userTaskServices, never()).search(any(UserTaskQuery.class));
  }

  @Test
  void shouldInvalidateUserTasksSearchQueryWithBadSortField() {
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
            USER_TASKS_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(USER_TASKS_SEARCH_URL)
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

    verify(userTaskServices, never()).search(any(UserTaskQuery.class));
  }

  @Test
  void shouldInvalidateUserTasksSearchQueryWithMissingSortField() {
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
            USER_TASKS_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(USER_TASKS_SEARCH_URL)
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

    verify(userTaskServices, never()).search(any(UserTaskQuery.class));
  }

  @Test
  void shouldInvalidateUserTasksSearchQueryWithConflictingPagination() {
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
            USER_TASKS_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(USER_TASKS_SEARCH_URL)
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

    verify(userTaskServices, never()).search(any(UserTaskQuery.class));
  }
}
