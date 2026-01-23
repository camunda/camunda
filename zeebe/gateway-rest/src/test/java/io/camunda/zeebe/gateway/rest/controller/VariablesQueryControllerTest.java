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
import static org.springframework.http.MediaType.APPLICATION_JSON;

import io.camunda.search.entities.VariableEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.VariableSort;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.VariableServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = VariableController.class)
public class VariablesQueryControllerTest extends RestControllerTest {

  private static final Long VALID_VARIABLE_KEY = 0L;
  private static final Long VALID_TRUNCATED_VARIABLE_KEY = 1L;
  private static final Long INVALID_VARIABLE_KEY = 99L;

  private static final String EXPECT_SINGLE_VARIABLE_RESPONSE =
      """
          {
              "variableKey": "0",
              "name": "n",
              "value": "v",
              "scopeKey": "2",
              "processInstanceKey": "3",
              "tenantId": "<default>"
          }""";
  private static final String EXPECT_SINGLE_TRUNCATED_VARIABLE_RESPONSE =
      """
          {
              "variableKey": "1",
              "name": "ne",
              "value": "ve",
              "scopeKey": "2",
              "processInstanceKey": "3",
              "tenantId": "<default>"
          }""";
  private static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
              "items": [
                  {
                        "variableKey": "0",
                        "name": "n",
                        "value": "v",
                        "scopeKey": "2",
                        "processInstanceKey": "3",
                        "tenantId": "<default>",
                        "isTruncated": false
                  },
                  {
                        "variableKey": "1",
                        "name": "ne",
                        "value": "v",
                        "scopeKey": "2",
                        "processInstanceKey": "3",
                        "tenantId": "<default>",
                        "isTruncated": true
                  }
              ],
              "page": {
                  "totalItems": 2,
                  "startCursor": "0",
                  "endCursor": "1",
                  "hasMoreTotalItems": false
              }
          }""";

  private static final String EXPECTED_UNTRUNCATED_SEARCH_RESPONSE =
      """
          {
              "items": [
                  {
                        "variableKey": "0",
                        "name": "n",
                        "value": "v",
                        "scopeKey": "2",
                        "processInstanceKey": "3",
                        "tenantId": "<default>",
                        "isTruncated": false
                  },
                  {
                        "variableKey": "1",
                        "name": "ne",
                        "value": "ve",
                        "scopeKey": "2",
                        "processInstanceKey": "3",
                        "tenantId": "<default>",
                        "isTruncated": false
                  }
              ],
              "page": {
                  "totalItems": 2,
                  "startCursor": "0",
                  "endCursor": "1",
                  "hasMoreTotalItems": false
              }
          }""";

  private static final String VARIABLE_TASKS_SEARCH_URL = "/v2/variables/search";
  private static final SearchQueryResult<VariableEntity> SEARCH_QUERY_RESULT =
      new Builder<VariableEntity>()
          .total(2L)
          .items(
              List.of(
                  new VariableEntity(0L, "n", "v", null, false, 2L, 3L, 4L, "bpid", "<default>"),
                  new VariableEntity(1L, "ne", "v", "ve", true, 2L, 3L, 4L, "bpid", "<default>")))
          .startCursor("0")
          .endCursor("1")
          .build();
  @MockitoBean VariableServices variableServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @Captor ArgumentCaptor<VariableQuery> variableQueryCaptor;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(variableServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(variableServices);

    when(variableServices.getByKey(VALID_VARIABLE_KEY))
        .thenReturn(new VariableEntity(0L, "n", "v", null, false, 2L, 3L, 4L, "bpid", "<default>"));
    when(variableServices.getByKey(VALID_TRUNCATED_VARIABLE_KEY))
        .thenReturn(new VariableEntity(1L, "ne", "v", "ve", true, 2L, 3L, 4L, "bpid", "<default>"));

    when(variableServices.getByKey(INVALID_VARIABLE_KEY))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    String.format("Variable with key %d not found", INVALID_VARIABLE_KEY),
                    CamundaSearchException.Reason.NOT_FOUND)));

    when(variableServices.search(any(VariableQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
  }

  @Test
  void shouldSearchVariablesWithEmptyBody() {
    // when / then
    webClient
        .post()
        .uri(VARIABLE_TASKS_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(variableServices).search(new VariableQuery.Builder().build());
  }

  @Test
  void shouldSearchVariablesWithFullValues() {
    // when / then
    webClient
        .post()
        .uri(VARIABLE_TASKS_SEARCH_URL + "?truncateValues=false")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_UNTRUNCATED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(variableServices).search(new VariableQuery.Builder().build());
  }

  @Test
  void shouldSearchVariableWithEmptyQuery() {
    // when / then
    webClient
        .post()
        .uri(VARIABLE_TASKS_SEARCH_URL)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(variableServices).search(new VariableQuery.Builder().build());
  }

  @Test
  void shouldSearchVariableWithSorting() {
    // given
    when(variableServices.search(any(VariableQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
            {
                "sort": [
                    {
                        "field": "name",
                        "order": "DESC"
                    },
                    {
                        "field": "value",
                        "order": "asc"
                    }
                ]
            }""";
    // when / then
    webClient
        .post()
        .uri(VARIABLE_TASKS_SEARCH_URL)
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

    verify(variableServices)
        .search(
            new VariableQuery.Builder()
                .sort(new VariableSort.Builder().name().desc().value().asc().build())
                .build());
  }

  @Test
  void shouldInvalidateVariableSearchQueryWithBadSortOrder() {
    // given
    final var request =
        """
            {
                "sort": [
                    {
                        "field": "name",
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
            VARIABLE_TASKS_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(VARIABLE_TASKS_SEARCH_URL)
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

    verify(variableServices, never()).search(any(VariableQuery.class));
  }

  @Test
  void shouldInvalidateVariableSearchQueryWithMissingSortField() {
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
            VARIABLE_TASKS_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(VARIABLE_TASKS_SEARCH_URL)
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

    verify(variableServices, never()).search(any(VariableQuery.class));
  }

  @Test
  void shouldInvalidateVariableSearchQueryWithConflictingPagination() {
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
                  "title": "Bad Request",
                  "status": 400,
                  "detail": "Only one of [from, after, before] is allowed.",
                  "instance": "%s"
                }""",
            VARIABLE_TASKS_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(VARIABLE_TASKS_SEARCH_URL)
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

    verify(variableServices, never()).search(any(VariableQuery.class));
  }

  @Test
  void shouldInvalidateVariableSearchQueryWithConflictingPaginationTypes() {
    // given
    final var request =
        """
            {
                "page": {
                    "after": "a",
                    "from": 4
                }
            }""";
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "Bad Request",
                  "status": 400,
                  "detail": "Only one of [from, after, before] is allowed.",
                  "instance": "%s"
                }""",
            VARIABLE_TASKS_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(VARIABLE_TASKS_SEARCH_URL)
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

    verify(variableServices, never()).search(any(VariableQuery.class));
  }

  @Test
  void shouldGetVariableByKey() {
    // when / then
    webClient
        .get()
        .uri("/v2/variables/" + VALID_VARIABLE_KEY)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(APPLICATION_JSON)
        .expectBody()
        .json(EXPECT_SINGLE_VARIABLE_RESPONSE, JsonCompareMode.STRICT);

    verify(variableServices).getByKey(VALID_VARIABLE_KEY);
  }

  @Test
  void shouldGetTruncatedVariableByKey() {
    // when / then
    webClient
        .get()
        .uri("/v2/variables/" + VALID_TRUNCATED_VARIABLE_KEY)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(APPLICATION_JSON)
        .expectBody()
        .json(EXPECT_SINGLE_TRUNCATED_VARIABLE_RESPONSE, JsonCompareMode.STRICT);

    verify(variableServices).getByKey(VALID_TRUNCATED_VARIABLE_KEY);
  }

  @Test
  void shouldReturn404ForInvalidVariableTaskKey() {
    // when / then
    webClient
        .get()
        .uri("/v2/variables/" + INVALID_VARIABLE_KEY)
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(
            """
                {
                  "type": "about:blank",
                  "title": "NOT_FOUND",
                  "status": 404,
                  "detail": "Variable with key 99 not found",
                  "instance": "/v2/variables/99"
                }""",
            JsonCompareMode.STRICT);

    verify(variableServices).getByKey(INVALID_VARIABLE_KEY);
  }

  private static Stream<Arguments> provideAdvancedSearchParameters() {
    final var streamBuilder = Stream.<Arguments>builder();

    keyOperationTestCases(
        streamBuilder,
        "scopeKey",
        ops -> new VariableFilter.Builder().scopeKeyOperations(ops).build());
    keyOperationTestCases(
        streamBuilder,
        "processInstanceKey",
        ops -> new VariableFilter.Builder().processInstanceKeyOperations(ops).build());
    stringOperationTestCases(
        streamBuilder, "name", ops -> new VariableFilter.Builder().nameOperations(ops).build());
    stringOperationTestCases(
        streamBuilder, "value", ops -> new VariableFilter.Builder().valueOperations(ops).build());

    return streamBuilder.build();
  }

  @ParameterizedTest
  @MethodSource("provideAdvancedSearchParameters")
  void shouldSearchVariablesWithAdvancedFilter(
      final String filterString, final VariableFilter filter) {
    // given
    final var request =
        """
            {
                "filter": %s
            }"""
            .formatted(filterString);
    System.out.println("request = " + request);
    when(variableServices.search(variableQueryCaptor.capture())).thenReturn(SEARCH_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(VARIABLE_TASKS_SEARCH_URL)
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

    verify(variableServices).search(new VariableQuery.Builder().filter(filter).build());
  }
}
