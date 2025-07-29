/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static io.camunda.search.query.SearchQueryBuilders.variableSearchQuery;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;

import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.UserTaskEntity.UserTaskState;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.sort.UserTaskSort;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.UserTaskServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = UserTaskController.class)
public class UserTaskQueryControllerTest extends RestControllerTest {

  private static final Long VALID_USER_TASK_KEY = 0L;
  private static final Long INVALID_USER_TASK_KEY = 999L;

  private static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
              "items": [
                  {
                      "tenantId": "t",
                      "userTaskKey": "0",
                      "processInstanceKey": "1",
                      "processDefinitionKey": "2",
                      "elementInstanceKey": "3",
                      "processDefinitionId": "b",
                      "state": "CREATED",
                      "assignee": "a",
                      "candidateUsers": [],
                      "candidateGroups": [],
                      "formKey": "0",
                      "elementId": "e",
                      "name": "name",
                      "creationDate": "2020-11-11T00:00:00.000Z",
                      "completionDate": "2020-11-11T00:00:00.000Z",
                      "dueDate": "2020-11-11T00:00:00.000Z",
                      "followUpDate": "2020-11-11T00:00:00.000Z",
                      "externalFormReference": "efr",
                      "processDefinitionVersion": 1,
                      "customHeaders": {},
                      "priority": 50
                  }
              ],
              "page": {
                  "totalItems": 1,
                  "startCursor": "f",
                  "endCursor": "v",
                  "hasMoreTotalItems": false
              }
          }""";

  private static final String EXPECTED_VARIABLE_RESULT_JSON =
      """
      {
        "items": [
          {
              "variableKey":"0",
              "name":"name",
              "value":"value",
              "scopeKey":"1",
              "processInstanceKey":"2",
              "tenantId":"<default>",
              "isTruncated":false
          },
          {
              "variableKey":"1",
              "name":"name2",
              "value":"value",
              "scopeKey":"1",
              "processInstanceKey":"2",
              "tenantId":"<default>",
              "isTruncated":true
          }
        ],
        "page": {
          "totalItems": 2,
          "startCursor": "0",
          "endCursor": "1",
          "hasMoreTotalItems": false
        }
      }
      """;

  private static final String USER_TASK_ITEM_JSON =
      """
          {
                      "tenantId": "t",
                      "userTaskKey": "0",
                      "processInstanceKey": "1",
                      "processDefinitionKey": "2",
                      "elementInstanceKey": "3",
                      "processDefinitionId": "b",
                      "state": "CREATED",
                      "assignee": "a",
                      "candidateUsers": [],
                      "candidateGroups": [],
                      "formKey": "0",
                      "elementId": "e",
                      "name": "name",
                      "creationDate": "2020-11-11T00:00:00.000Z",
                      "completionDate": "2020-11-11T00:00:00.000Z",
                      "dueDate": "2020-11-11T00:00:00.000Z",
                      "followUpDate": "2020-11-11T00:00:00.000Z",
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
        "formKey": "0",
        "tenantId": "tenant-1",
        "formId": "bpmn-1",
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
                      "e", // elementBpmnId
                      "name",
                      "b", // bpmnProcessId
                      OffsetDateTime.parse("2020-11-11T00:00:00.000Z"), // creationTime
                      OffsetDateTime.parse("2020-11-11T00:00:00.000Z"), // completionTime
                      "a", // assignee
                      UserTaskState.CREATED, // state
                      0L, // formKey (adjusted to match expected value)
                      2L, // processDefinitionId
                      1L, // processInstanceId
                      3L, // elementInstanceId
                      "t", // tenantId
                      OffsetDateTime.parse("2020-11-11T00:00:00.000Z"), // dueDate
                      OffsetDateTime.parse("2020-11-11T00:00:00.000Z"), // followUpDate
                      new ArrayList<>(), // candidateGroups
                      new ArrayList<>(), // candidateUsers
                      "efr", // externalFormReference
                      1, // processDefinitionVersion
                      Collections.emptyMap(), // customHeaders
                      50 // priority
                      )))
          .startCursor("f")
          .endCursor("v")
          .build();

  private static final SearchQueryResult<VariableEntity> SEARCH_VAR_QUERY_RESULT =
      new Builder<VariableEntity>()
          .total(2L)
          .items(
              List.of(
                  new VariableEntity(0L, "name", "value", null, false, 1L, 2L, "bpid", "<default>"),
                  new VariableEntity(
                      1L, "name2", "value", "valueLong", true, 1L, 2L, "bpid", "<default>")))
          .startCursor("0")
          .endCursor("1")
          .build();

  @MockitoBean UserTaskServices userTaskServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupServices() throws IOException {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(userTaskServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(userTaskServices);

    // Mock the behavior of userTaskServices for a valid key
    when(userTaskServices.getByKey(VALID_USER_TASK_KEY))
        .thenReturn(
            new UserTaskEntity(
                0L,
                "e",
                "name",
                "b",
                OffsetDateTime.parse("2020-11-11T00:00:00.000Z"),
                OffsetDateTime.parse("2020-11-11T00:00:00.000Z"),
                "a",
                UserTaskState.CREATED,
                0L,
                2L,
                1L,
                3L,
                "t",
                OffsetDateTime.parse("2020-11-11T00:00:00.000Z"),
                OffsetDateTime.parse("2020-11-11T00:00:00.000Z"),
                List.of(),
                List.of(),
                "efr",
                1,
                Map.of(),
                50));
    // Mock the behavior for an invalid userTaskKey to throw NotFoundException
    when(userTaskServices.getByKey(INVALID_USER_TASK_KEY))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    String.format("User Task with key %d not found", INVALID_USER_TASK_KEY),
                    CamundaSearchException.Reason.NOT_FOUND)));
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
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

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
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

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
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(userTaskServices)
        .search(
            new UserTaskQuery.Builder()
                .sort(
                    new UserTaskSort.Builder().creationDate().desc().completionDate().asc().build())
                .build());
  }

  @Test
  void shouldInvalidateUserTasksSearchQueryWithEmptyLocalVariableFilter() {
    // given
    final var request =
        """
            {
                "filter": {
                    "localVariables": [
                        {
                            "name": "creationDate",
                            "value": {}
                        }
                    ]
                }
            }""";
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "Variable value must not be null.",
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
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(userTaskServices, never()).search(any(UserTaskQuery.class));
  }

  @Test
  void shouldInvalidateUserTasksSearchQueryMissingLocalVariableFilter() {
    // given
    final var request =
        """
            {
                "filter": {
                    "localVariables": [
                        {
                            "name": "creationDate"
                        }
                    ]
                }
            }""";
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "Variable value must not be null.",
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
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(userTaskServices, never()).search(any(UserTaskQuery.class));
  }

  @Test
  void shouldInvalidateUserTasksSearchQueryWithEmptyProcessInstanceVariableFilter() {
    // given
    final var request =
        """
            {
                "filter": {
                    "processInstanceVariables": [
                        {
                            "name": "creationDate",
                            "value": {}
                        }
                    ]
                }
            }""";
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "Variable value must not be null.",
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
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(userTaskServices, never()).search(any(UserTaskQuery.class));
  }

  @Test
  void shouldInvalidateUserTasksSearchQueryMissingProcessInstanceVariableFilter() {
    // given
    final var request =
        """
            {
                "filter": {
                    "processInstanceVariables": [
                        {
                            "name": "creationDate"
                        }
                    ]
                }
            }""";
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "Variable value must not be null.",
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
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(userTaskServices, never()).search(any(UserTaskQuery.class));
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
                  "title": "Bad Request",
                  "status": 400,
                  "detail": "Unexpected value 'dsc' for enum field 'order'. Use any of the following values: [ASC, DESC]",
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
        .json(expectedResponse, JsonCompareMode.STRICT);

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
                        "order": "ASC"
                    }
                ]
            }""";
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "Bad Request",
                  "status": 400,
                  "detail": "Unexpected value 'unknownField' for enum field 'field'. Use any of the following values: [creationDate, completionDate, followUpDate, dueDate, priority, name]",
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
        .json(expectedResponse, JsonCompareMode.STRICT);

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
                        "order": "ASC"
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
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(userTaskServices, never()).search(any(UserTaskQuery.class));
  }

  @Test
  void shouldInvalidateUserTasksSearchQueryWithConflictingPagination() {
    // given
    final var request =
        """
            {
                "page": {
                    "after": "a",
                    "before": "b"
                }
            }""";
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "Both after and before cannot be set at the same time.",
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
        .json(expectedResponse, JsonCompareMode.STRICT);

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
        .json(USER_TASK_ITEM_JSON, JsonCompareMode.STRICT);

    // Verify that the service was called with the invalid userTaskKey
    verify(userTaskServices).getByKey(VALID_USER_TASK_KEY);
  }

  @Test
  public void shouldReturn404ForInvalidUserTaskKey() {
    // when and then
    final String uri = "/v2/user-tasks/" + INVALID_USER_TASK_KEY;
    webClient
        .get()
        .uri(uri)
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
                      "detail": "User Task with key 999 not found",
                      "instance": "%s"
                    }
                """
                .formatted(uri),
            JsonCompareMode.STRICT);

    // Verify that the service was called with the invalid userTaskKey
    verify(userTaskServices).getByKey(INVALID_USER_TASK_KEY);
  }

  @Test
  public void shouldReturnFormItemForValidFormKey() {
    when(userTaskServices.getUserTaskForm(VALID_FORM_KEY))
        .thenReturn(Optional.of(new FormEntity(0L, "tenant-1", "bpmn-1", "schema", 1L)));

    webClient
        .get()
        .uri("/v2/user-tasks/{userTaskKey}/form", VALID_USER_TASK_KEY)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(FORM_ITEM_JSON, JsonCompareMode.STRICT);

    verify(userTaskServices).getUserTaskForm(VALID_FORM_KEY);
  }

  @Test
  public void shouldReturn404ForFormInvalidUserTaskKey() {
    // given
    when(userTaskServices.getUserTaskForm(INVALID_USER_TASK_KEY))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    "User Task with key 999 not found", CamundaSearchException.Reason.NOT_FOUND)));
    final String formattedUri = "/v2/user-tasks/%s/form".formatted(INVALID_USER_TASK_KEY);
    final String expectedResponse =
        """
        {
          "type": "about:blank",
          "title": "NOT_FOUND",
          "status": 404,
          "detail": "User Task with key 999 not found",
          "instance": "%s"
        }
        """
            .formatted(formattedUri);
    // when/then
    webClient
        .get()
        .uri(formattedUri)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  public void shouldReturn500OnUnexpectedException() throws Exception {
    when(userTaskServices.getUserTaskForm(VALID_FORM_KEY))
        .thenThrow(new RuntimeException("Unexpected error"));

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
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  public void shouldReturnVariableForValidUserTaskKey() {
    final var request =
        """
            {
                "filter":
                    {
                        "name": "varName"
                    }

            }""";

    when(userTaskServices.searchUserTaskVariables(
            VALID_USER_TASK_KEY,
            variableSearchQuery().filter(f -> f.nameOperations(Operation.eq("varName"))).build()))
        .thenReturn(SEARCH_VAR_QUERY_RESULT);
    // when and then
    webClient
        .post()
        .uri("/v2/user-tasks/" + VALID_USER_TASK_KEY + "/variables/search")
        .accept(APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_VARIABLE_RESULT_JSON, JsonCompareMode.STRICT);

    verify(userTaskServices)
        .searchUserTaskVariables(
            VALID_USER_TASK_KEY,
            variableSearchQuery().filter(f -> f.nameOperations(Operation.eq("varName"))).build());
  }

  private static Stream<Arguments> provideAdvancedSearchParameters() {
    final var streamBuilder = Stream.<Arguments>builder();

    integerOperationTestCases(
        streamBuilder,
        "priority",
        ops -> new UserTaskFilter.Builder().priorityOperations(ops).build());
    stringOperationTestCases(
        streamBuilder,
        "candidateGroup",
        ops -> new UserTaskFilter.Builder().candidateGroupOperations(ops).build());
    stringOperationTestCases(
        streamBuilder,
        "candidateUser",
        ops -> new UserTaskFilter.Builder().candidateUserOperations(ops).build());
    stringOperationTestCases(
        streamBuilder,
        "assignee",
        ops -> new UserTaskFilter.Builder().assigneeOperations(ops).build());
    stringOperationTestCases(
        streamBuilder,
        "tenantId",
        ops -> new UserTaskFilter.Builder().tenantIdOperations(ops).build());

    return streamBuilder.build();
  }

  @ParameterizedTest
  @MethodSource("provideAdvancedSearchParameters")
  void shouldSearchTasksWithAdvancedFilter(final String filterString, final UserTaskFilter filter) {
    // given
    final var request =
        """
            {
                "filter": %s
            }"""
            .formatted(filterString);
    System.out.println("request = " + request);
    when(userTaskServices.search(any(UserTaskQuery.class))).thenReturn(SEARCH_QUERY_RESULT);

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
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(userTaskServices).search(new UserTaskQuery.Builder().filter(filter).build());
  }
}
