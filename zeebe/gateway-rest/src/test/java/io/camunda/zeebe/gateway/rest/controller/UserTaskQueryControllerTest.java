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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.security.auth.Authentication;
import io.camunda.search.sort.UserTaskSort;
import io.camunda.service.FormServices;
import io.camunda.service.UserTaskServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(value = UserTaskQueryController.class, properties = "camunda.rest.query.enabled=true")
public class UserTaskQueryControllerTest extends RestControllerTest {

  private static final Long VALID_USER_TASK_KEY = 0L;
  private static final Long INVALID_USER_TASK_KEY = 999L;

  private static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
              "items": [
                  {
                      "tenantIds": "t",
                      "userTaskKey": 0,
                      "processInstanceKey": 1,
                      "processDefinitionKey": 2,
                      "elementInstanceKey": 3,
                      "processDefinitionId": "b",
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
                      "customHeaders": {},
                      "priority": 50
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

  private static final String USER_TASK_ITEM_JSON =
      """
          {
                      "tenantIds": "t",
                      "userTaskKey": 0,
                      "processInstanceKey": 1,
                      "processDefinitionKey": 2,
                      "elementInstanceKey": 3,
                      "processDefinitionId": "b",
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
                      "customHeaders": {},
                      "priority": 50
          }
          """;

  private static final Long VALID_FORM_KEY = 0L;
  private static final Long INVALID_FORM_KEY = 999L;
  private static final String FORM_ITEM_JSON =
      """
      {
        "formKey": 0,
        "tenantId": "tenant-1",
        "bpmnId": "bpmn-1",
        "schema": "schema",
        "version": 1
      }
      """;
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
                      Collections.emptyMap(), // customHeaders
                      50 // priority
                      )))
          .sortValues(new Object[] {"v"})
          .build();
  @MockBean UserTaskServices userTaskServices;

  @MockBean private FormServices formServices;

  @BeforeEach
  void setupServices() {

    when(formServices.getByKey(VALID_FORM_KEY))
        .thenReturn(new FormEntity("0", "tenant-1", "bpmn-1", "schema", 1L));

    when(formServices.getByKey(INVALID_FORM_KEY))
        .thenThrow(new NotFoundException("Form not found"));

    when(userTaskServices.withAuthentication(any(Authentication.class)))
        .thenReturn(userTaskServices);

    when(formServices.withAuthentication(any(Authentication.class))).thenReturn(formServices);

    // Mock the behavior of userTaskServices for a valid key
    when(userTaskServices.getByKey(VALID_USER_TASK_KEY))
        .thenReturn(
            new UserTaskEntity(
                0L,
                "e",
                "b",
                "00:00:00.000Z+00:00",
                "00:00:00.000Z+00:00",
                "a",
                "s",
                0L,
                2L,
                1L,
                3L,
                "t",
                "00:00:00.000Z+00:00",
                "00:00:00.000Z+00:00",
                List.of(),
                List.of(),
                "efr",
                1,
                Map.of(),
                50));
    // Mock the behavior for an invalid userTaskKey to throw NotFoundException
    when(userTaskServices.getByKey(INVALID_USER_TASK_KEY))
        .thenThrow(
            new NotFoundException(
                String.format("User Task with key %d not found", INVALID_USER_TASK_KEY)));
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
        .contentType(APPLICATION_JSON)
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
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(APPLICATION_JSON)
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
                        "field": "creationDate",
                        "order": "desc"
                    },
                    {
                        "field": "completionDate",
                        "order": "asc"
                    }
                ]
            }""";
    // when / then
    webClient
        .post()
        .uri(USER_TASKS_SEARCH_URL)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(APPLICATION_JSON)
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
                        "field": "creationDate",
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
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
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
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
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
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
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
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
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
  public void shouldReturnUserTaskForValidKey() {
    // when and then
    webClient
        .get()
        .uri("/v2/user-tasks/{userTaskKey}", VALID_USER_TASK_KEY)
        .accept(APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(USER_TASK_ITEM_JSON);

    // Verify that the service was called with the invalid userTaskKey
    verify(userTaskServices).getByKey(VALID_USER_TASK_KEY);
  }

  @Test
  public void shouldReturn404ForInvalidUserTaskKey() {
    // when and then
    webClient
        .get()
        .uri("/v2/user-tasks/{userTaskKey}", INVALID_USER_TASK_KEY)
        .accept(APPLICATION_JSON)
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
                      "detail": "User Task with key 999 not found"
                    }
                """);

    // Verify that the service was called with the invalid userTaskKey
    verify(userTaskServices).getByKey(INVALID_USER_TASK_KEY);
  }

  @Test
  public void shouldReturnFormItemForValidFormKey() throws Exception {
    webClient
        .get()
        .uri("/v2/user-tasks/{userTaskKey}/form", VALID_USER_TASK_KEY)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(FORM_ITEM_JSON);

    verify(formServices, times(1)).getByKey(VALID_FORM_KEY);
  }

  @Test
  public void shouldReturn404ForFormInvalidUserTaskKey() throws Exception {
    webClient
        .get()
        .uri("/v2/user-tasks/{userTaskKey}/form", INVALID_USER_TASK_KEY)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "title": "NOT_FOUND",
              "status": 404,
              "detail": "User Task with key 999 not found"
            }
            """);

    verify(formServices, times(0)).getByKey(INVALID_USER_TASK_KEY);
  }

  @Test
  public void shouldReturn500OnUnexpectedException() throws Exception {
    when(formServices.getByKey(VALID_FORM_KEY)).thenThrow(new RuntimeException("Unexpected error"));

    webClient
        .get()
        .uri("/v2/user-tasks/{userTaskKey}/form", VALID_USER_TASK_KEY)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectBody()
        .json(
            """
            {
              "type": "about:blank",
              "title": "java.lang.RuntimeException",
              "status": 500,
              "detail": "Unexpected error occurred during the request processing: Unexpected error",
              "instance": "/v2/user-tasks/0/form"
            }
            """);
  }
}
