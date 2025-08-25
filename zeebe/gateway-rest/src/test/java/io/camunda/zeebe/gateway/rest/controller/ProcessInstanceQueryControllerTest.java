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

import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.Operation;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.ProcessInstanceSort;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.security.configuration.MultiTenancyConfiguration;
import io.camunda.service.ProcessInstanceServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.protocol.rest.ProcessInstanceStateEnum;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.util.ProcessInstanceStateConverter;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;
import org.json.JSONException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = ProcessInstanceController.class)
public class ProcessInstanceQueryControllerTest extends RestControllerTest {

  private static final String PROCESS_INSTANCES_SEARCH_URL = "/v2/process-instances/search";
  private static final String PROCESS_INSTANCES_BY_KEY_URL =
      "/v2/process-instances/{processInstanceKey}";
  private static final String PROCESS_INSTANCE_CALL_HIERARCHY_BY_KEY_URL =
      "/v2/process-instances/{processInstanceKey}/call-hierarchy";
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
          OffsetDateTime.parse("2024-01-01T00:00:00Z"),
          null,
          ProcessInstanceState.ACTIVE,
          false,
          "tenant",
          "PI_123",
          Set.of("tag1", "tag2"));
  private static final String PROCESS_INSTANCE_ENTITY_JSON =
      """
            {
            "processInstanceKey": "123",
            "processDefinitionId": "demoProcess",
            "processDefinitionName": "Demo Process",
            "processDefinitionVersion": 5,
            "processDefinitionVersionTag": "v5",
            "processDefinitionKey": "789",
            "parentProcessInstanceKey": "333",
            "parentElementInstanceKey": "777",
            "startDate": "2024-01-01T00:00:00.000Z",
            "state": "ACTIVE",
            "hasIncident": false,
            "tenantId": "tenant",
            "tags": ["tag1", "tag2"]
          }
          """;
  private static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
              "items": [
                {
                  "processInstanceKey": "123",
                  "processDefinitionId": "demoProcess",
                  "processDefinitionName": "Demo Process",
                  "processDefinitionVersion": 5,
                  "processDefinitionVersionTag": "v5",
                  "processDefinitionKey": "789",
                  "parentProcessInstanceKey": "333",
                  "parentElementInstanceKey": "777",
                  "startDate": "2024-01-01T00:00:00.000Z",
                  "state": "ACTIVE",
                  "hasIncident": false,
                  "tenantId": "tenant",
                  "tags": ["tag1", "tag2"]
                }
              ],
              "page": {
                  "totalItems": 1,
                  "startCursor": "f",
                  "endCursor": "v",
                  "hasMoreTotalItems": false
              }
          }
          """;
  private static final String EXPECTED_CALL_HIERARCHY =
      """
        [
          {
             "processInstanceKey": "123",
             "processDefinitionKey": "789",
             "processDefinitionName": "Demo Process"
          }
        ]
      """;

  private static final String EXPECTED_INVALID_TAGS_RESPONSE =
      """
      {
        "type":"about:blank",
        "title":"INVALID_ARGUMENT",
        "status":400,
        "detail":"The provided tag '1 invalid tag' is not valid. Tags must start with a letter (a-z, A-Z), followed by alphanumerics, underscores, minuses, colons, or periods. It must not be blank and must be 100 characters or less.",
        "instance":"/v2/process-instances/search"
      }
      """;

  private static final SearchQueryResult<ProcessInstanceEntity> SEARCH_QUERY_RESULT =
      new Builder<ProcessInstanceEntity>()
          .total(1L)
          .items(List.of(PROCESS_INSTANCE_ENTITY))
          .startCursor("f")
          .endCursor("v")
          .build();
  @MockitoBean ProcessInstanceServices processInstanceServices;
  @MockitoBean MultiTenancyConfiguration multiTenancyCfg;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @Captor ArgumentCaptor<ProcessInstanceQuery> queryCaptor;

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(processInstanceServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(processInstanceServices);
  }

  private static void assertJsonNonExtensible(final String expected, final byte[] actualBytes) {
    try {
      JSONAssert.assertEquals(
          expected,
          new String(Objects.requireNonNull(actualBytes), StandardCharsets.UTF_8),
          JSONCompareMode.NON_EXTENSIBLE);
    } catch (final JSONException e) {
      throw new AssertionError(e);
    }
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
        .consumeWith(
            result -> assertJsonNonExtensible(EXPECTED_SEARCH_RESPONSE, result.getResponseBody()));

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
        .consumeWith(
            result -> assertJsonNonExtensible(EXPECTED_SEARCH_RESPONSE, result.getResponseBody()));

    verify(processInstanceServices).search(new ProcessInstanceQuery.Builder().build());
  }

  @Test
  void shouldInvalidateProcessInstanceSearchQueryWithEmptyVariableFilter() {
    // given
    final var request =
        """
            {
                "filter": {
                    "variables": [
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
            PROCESS_INSTANCES_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_SEARCH_URL)
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

    verify(processInstanceServices, never()).search(any(ProcessInstanceQuery.class));
  }

  @Test
  void shouldInvalidateProcessInstanceSearchQueryWithMissingVariableFilter() {
    // given
    final var request =
        """
            {
                "filter": {
                    "variables": [
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
            PROCESS_INSTANCES_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(PROCESS_INSTANCES_SEARCH_URL)
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

    verify(processInstanceServices, never()).search(any(ProcessInstanceQuery.class));
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
                        "field": "processDefinitionId",
                        "order": "DESC"
                    },
                    {
                        "field": "processDefinitionKey",
                        "order": "ASC"
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
        .consumeWith(
            result -> assertJsonNonExtensible(EXPECTED_SEARCH_RESPONSE, result.getResponseBody()));

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
                        "field": "processDefinitionId",
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
        .json(expectedResponse, JsonCompareMode.STRICT);

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
                  "detail": "Unexpected value 'unknownField' for enum field 'field'. Use any of the following values: [processInstanceKey, processDefinitionId, processDefinitionName, processDefinitionVersion, processDefinitionVersionTag, processDefinitionKey, parentProcessInstanceKey, parentElementInstanceKey, startDate, endDate, state, hasIncident, tenantId]",
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
        .json(expectedResponse, JsonCompareMode.STRICT);

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
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(processInstanceServices, never()).search(any(ProcessInstanceQuery.class));
  }

  @Test
  void shouldInvalidateProcessInstancesSearchQueryWithConflictingPagination() {
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
        .json(expectedResponse, JsonCompareMode.STRICT);

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
        .consumeWith(
            result ->
                assertJsonNonExtensible(PROCESS_INSTANCE_ENTITY_JSON, result.getResponseBody()));

    // Verify that the service was called with the valid key
    verify(processInstanceServices).getByKey(validProcesInstanceKey);
  }

  @Test
  public void shouldReturn404ForInvalidProcessInstaceKey() {
    // given
    final var invalidProcesInstanceKey = 100L;
    when(processInstanceServices.getByKey(invalidProcesInstanceKey))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    String.format(
                        "Process Instance with key %d not found", invalidProcesInstanceKey),
                    CamundaSearchException.Reason.NOT_FOUND)));
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
                      "detail": "Process Instance with key 100 not found",
                      "instance": "/v2/process-instances/%s"
                    }
                """
                .formatted(invalidProcesInstanceKey),
            JsonCompareMode.STRICT);

    // Verify that the service was called with the invalid key
    verify(processInstanceServices).getByKey(invalidProcesInstanceKey);
  }

  private static Stream<Arguments> provideAdvancedSearchParameters() {
    final var streamBuilder = Stream.<Arguments>builder();

    keyOperationTestCases(
        streamBuilder,
        "processInstanceKey",
        ops -> new ProcessInstanceFilter.Builder().processInstanceKeyOperations(ops).build());
    keyOperationTestCases(
        streamBuilder,
        "processDefinitionKey",
        ops -> new ProcessInstanceFilter.Builder().processDefinitionKeyOperations(ops).build());
    keyOperationTestCases(
        streamBuilder,
        "parentProcessInstanceKey",
        ops -> new ProcessInstanceFilter.Builder().parentProcessInstanceKeyOperations(ops).build());
    keyOperationTestCases(
        streamBuilder,
        "parentElementInstanceKey",
        ops ->
            new ProcessInstanceFilter.Builder().parentFlowNodeInstanceKeyOperations(ops).build());
    integerOperationTestCases(
        streamBuilder,
        "processDefinitionVersion",
        ops -> new ProcessInstanceFilter.Builder().processDefinitionVersionOperations(ops).build());
    stringOperationTestCases(
        streamBuilder,
        "processDefinitionId",
        ops -> new ProcessInstanceFilter.Builder().processDefinitionIdOperations(ops).build());
    stringOperationTestCases(
        streamBuilder,
        "processDefinitionName",
        ops -> new ProcessInstanceFilter.Builder().processDefinitionNameOperations(ops).build());
    stringOperationTestCases(
        streamBuilder,
        "processDefinitionVersionTag",
        ops ->
            new ProcessInstanceFilter.Builder().processDefinitionVersionTagOperations(ops).build());
    customOperationTestCases(
        streamBuilder,
        "state",
        ops -> new ProcessInstanceFilter.Builder().stateOperations(ops).build(),
        List.of(
            List.of(Operation.eq(String.valueOf(ProcessInstanceStateEnum.ACTIVE))),
            List.of(Operation.neq(String.valueOf(ProcessInstanceStateEnum.COMPLETED))),
            List.of(
                Operation.in(
                    String.valueOf(ProcessInstanceStateEnum.COMPLETED),
                    String.valueOf(ProcessInstanceStateEnum.ACTIVE)),
                Operation.like("act"))),
        true);
    stringOperationTestCases(
        streamBuilder,
        "tenantId",
        ops -> new ProcessInstanceFilter.Builder().tenantIdOperations(ops).build());
    dateTimeOperationTestCases(
        streamBuilder,
        "startDate",
        ops -> new ProcessInstanceFilter.Builder().startDateOperations(ops).build());
    dateTimeOperationTestCases(
        streamBuilder,
        "endDate",
        ops -> new ProcessInstanceFilter.Builder().endDateOperations(ops).build());
    stringOperationTestCases(
        streamBuilder,
        "errorMessage",
        ops -> new ProcessInstanceFilter.Builder().errorMessageOperations(ops).build());
    return streamBuilder.build();
  }

  @ParameterizedTest
  @MethodSource("provideAdvancedSearchParameters")
  void shouldSearchProcessInstancesWithAdvancedFilter(
      final String filterString, final ProcessInstanceFilter filter) {
    // given
    final var request =
        """
            {
                "filter": %s
            }"""
            .formatted(filterString);
    System.out.println("request = " + request);
    when(processInstanceServices.search(queryCaptor.capture())).thenReturn(SEARCH_QUERY_RESULT);

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
        .consumeWith(
            result -> assertJsonNonExtensible(EXPECTED_SEARCH_RESPONSE, result.getResponseBody()));

    verify(processInstanceServices)
        .search(new ProcessInstanceQuery.Builder().filter(filter).build());
  }

  @ParameterizedTest
  @EnumSource(ProcessInstanceStateEnum.class)
  void shouldSearchProcessInstancesByState(final ProcessInstanceStateEnum state) {
    // given
    final var request =
        """
            {
                "filter": {"state": "%s"}
            }"""
            .formatted(state);
    System.out.println("request = " + request);
    final ProcessInstanceFilter filter =
        new ProcessInstanceFilter.Builder()
            .states(ProcessInstanceStateConverter.toInternalStateAsString(state))
            .build();

    // when
    when(processInstanceServices.search(queryCaptor.capture())).thenReturn(SEARCH_QUERY_RESULT);
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
        .consumeWith(
            result -> assertJsonNonExtensible(EXPECTED_SEARCH_RESPONSE, result.getResponseBody()));

    // then
    verify(processInstanceServices)
        .search(new ProcessInstanceQuery.Builder().filter(filter).build());
  }

  @Test
  void shouldSearchProcessInstancesWithOrOperator() {
    // given
    final var request =
        """
        {
          "filter": {
            "state": "ACTIVE",
            "tenantId": "tenant",
            "$or": [
              { "processDefinitionId": "process_v1" },
              { "processDefinitionId": "process_v2", "hasIncident": true }
            ]
          }
        }""";

    final var orFilters =
        List.of(
            new ProcessInstanceFilter.Builder().processDefinitionIds("process_v1").build(),
            new ProcessInstanceFilter.Builder()
                .processDefinitionIds("process_v2")
                .hasIncident(true)
                .build());

    final var expectedFilter =
        new ProcessInstanceFilter.Builder()
            .stateOperations(Operation.eq("ACTIVE"))
            .tenantIdOperations(Operation.eq("tenant"));
    orFilters.forEach(expectedFilter::addOrOperation);

    when(processInstanceServices.search(queryCaptor.capture())).thenReturn(SEARCH_QUERY_RESULT);

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
        .consumeWith(
            result -> assertJsonNonExtensible(EXPECTED_SEARCH_RESPONSE, result.getResponseBody()));

    verify(processInstanceServices)
        .search(new ProcessInstanceQuery.Builder().filter(expectedFilter.build()).build());
  }

  @Test
  public void shouldReturnCallHierarchyForGivenKey() {
    // given
    final var processInstanceKey = 123L;

    when(processInstanceServices.callHierarchy(processInstanceKey))
        .thenReturn(List.of(PROCESS_INSTANCE_ENTITY));

    // when / then
    webClient
        .get()
        .uri(PROCESS_INSTANCE_CALL_HIERARCHY_BY_KEY_URL, processInstanceKey)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_CALL_HIERARCHY, JsonCompareMode.STRICT);

    // Verify that the service was called with the valid key
    verify(processInstanceServices).callHierarchy(processInstanceKey);
  }

  @Test
  void shouldReturnBadRequestWhenTagsAreInvalid() {
    // given
    final var request =
        """
            {
                "filter": { "tags": ["1 invalid tag", "tag2"] }
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
        .isBadRequest()
        .expectBody()
        .consumeWith(
            result ->
                assertJsonNonExtensible(EXPECTED_INVALID_TAGS_RESPONSE, result.getResponseBody()));
  }
}
