/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gateway.protocol.model.ElementInstanceStateEnum;
import io.camunda.gateway.protocol.model.IncidentErrorTypeEnum;
import io.camunda.gateway.protocol.model.IncidentStateEnum;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.FlowNodeInstanceSort;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.ElementInstanceServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@ExtendWith(MockitoExtension.class)
@WebMvcTest(value = ElementInstanceController.class)
public class ElementInstanceQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
              "items": [
                  {
                   "elementInstanceKey":"1",
                   "processInstanceKey":"2",
                   "rootProcessInstanceKey":"37",
                   "processDefinitionKey":"3",
                   "processDefinitionId":"bpmnProcessId",
                   "startDate": "2023-05-17T00:00:00.000Z",
                   "endDate":"2023-05-23T00:00:00.000Z",
                   "elementId":"elementId",
                   "elementName":"elementName",
                   "type":"SERVICE_TASK",
                   "state":"ACTIVE",
                   "hasIncident":false,
                   "incidentKey": null,
                   "tenantId":"<default>"
                 }
              ],
              "page": {
                  "totalItems": 1,
                  "startCursor": "f",
                  "endCursor": "v",
                  "hasMoreTotalItems": false
              }
          }""";

  static final SearchQueryResult<FlowNodeInstanceEntity> SEARCH_QUERY_RESULT =
      new Builder<FlowNodeInstanceEntity>()
          .total(1L)
          .items(
              List.of(
                  new FlowNodeInstanceEntity(
                      1L,
                      2L,
                      37L,
                      3L,
                      OffsetDateTime.parse("2023-05-17T00:00:00Z"),
                      OffsetDateTime.parse("2023-05-23T00:00:00Z"),
                      "elementId",
                      "elementName",
                      null,
                      FlowNodeType.SERVICE_TASK,
                      FlowNodeState.ACTIVE,
                      false,
                      null,
                      "bpmnProcessId",
                      "<default>",
                      null)))
          .startCursor("f")
          .endCursor("v")
          .build();

  static final String EXPECTED_GET_RESPONSE =
      """
                 {
                   "elementInstanceKey":"23",
                   "processInstanceKey":"5",
                   "rootProcessInstanceKey":"37",
                   "processDefinitionKey":"17",
                   "processDefinitionId":"complexProcess",
                   "startDate": "2023-05-17T10:10:10.000Z",
                   "endDate":"2023-05-23T10:10:10.000Z",
                   "elementId":"startEvent_1",
                   "elementName":"StartEvent_1",
                   "type":"SERVICE_TASK",
                   "state":"ACTIVE",
                   "hasIncident":true,
                   "incidentKey":"1234",
                   "tenantId":"tenantId"
                 }
          """;

  static final FlowNodeInstanceEntity GET_QUERY_RESULT =
      new FlowNodeInstanceEntity(
          23L,
          5L,
          37L,
          17L,
          OffsetDateTime.parse("2023-05-17T10:10:10Z"),
          OffsetDateTime.parse("2023-05-23T10:10:10Z"),
          "startEvent_1",
          "StartEvent_1",
          null,
          FlowNodeType.SERVICE_TASK,
          FlowNodeState.ACTIVE,
          true,
          1234L,
          "complexProcess",
          "tenantId",
          null);

  static final IncidentEntity INCIDENT_ENTITY =
      new IncidentEntity(
          1234L,
          3L,
          "processDefId",
          2L,
          37L,
          IncidentEntity.ErrorType.JOB_NO_RETRIES,
          "error",
          "elementId",
          1L,
          OffsetDateTime.parse("2023-05-17T00:00:00Z"),
          IncidentEntity.IncidentState.ACTIVE,
          99L,
          "tenant1");

  static final String EXPECTED_INCIDENT_SEARCH_RESPONSE =
      """
      {
        "items": [
          {
            "incidentKey": "1234",
            "processDefinitionKey": "3",
            "processDefinitionId": "processDefId",
            "processInstanceKey": "2",
            "rootProcessInstanceKey": "37",
            "errorType": "JOB_NO_RETRIES",
            "errorMessage": "error",
            "elementId": "elementId",
            "elementInstanceKey": "1",
            "creationTime": "2023-05-17T00:00:00.000Z",
            "state": "ACTIVE",
            "jobKey": "99",
            "tenantId": "tenant1"
          }
        ],
        "page": {
          "totalItems": 1,
          "startCursor": null,
          "endCursor": null,
          "hasMoreTotalItems": false
        }
      }
      """;
  static final SearchQueryResult<IncidentEntity> INCIDENT_SEARCH_RESULT =
      SearchQueryResult.of(INCIDENT_ENTITY);
  static final String ELEMENT_INSTANCES_URL = "/v2/element-instances/";
  static final String ELEMENT_INSTANCES_SEARCH_URL = ELEMENT_INSTANCES_URL + "search";
  static final String INCIDENTS_SEARCH_URL = ELEMENT_INSTANCES_URL + "%d/incidents/search";
  private static final String EXPECTED_PROCESS_INSTANCE_INCIDENTS_SEARCH_RESPONSE =
      """
          {
              "items": [
                {
                  "incidentKey": "456",
                  "processDefinitionKey": "234",
                  "processDefinitionId": "Test_Process",
                  "errorMessage": "Process crashed",
                  "processInstanceKey": "789",
                  "rootProcessInstanceKey": "37",
                  "errorType": "CALLED_DECISION_ERROR",
                  "elementId": "elementId",
                  "elementInstanceKey": "123",
                  "creationTime": "2024-01-02T00:00:00.000Z",
                  "state": "ACTIVE",
                  "jobKey": "567",
                  "tenantId": "tenantId"
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
  private static final SearchQueryResult<IncidentEntity> SEARCH_INCIDENT_QUERY_RESULT =
      new io.camunda.search.query.SearchQueryResult.Builder<IncidentEntity>()
          .total(1L)
          .items(
              List.of(
                  new IncidentEntity(
                      456L,
                      234L,
                      "Test_Process",
                      789L,
                      37L,
                      ErrorType.CALLED_DECISION_ERROR,
                      "Process crashed",
                      "elementId",
                      123L,
                      OffsetDateTime.parse("2024-01-02T00:00:00.000Z"),
                      IncidentState.ACTIVE,
                      567L,
                      "tenantId")))
          .startCursor("f")
          .endCursor("v")
          .build();
  private static final String ELEMENT_INSTANCE_INCIDENTS_SEARCH_URL =
      "/v2/element-instances/{elementInstanceKey}/incidents/search";

  @MockitoBean ElementInstanceServices elementInstanceServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @Captor ArgumentCaptor<FlowNodeInstanceQuery> queryCaptor;
  @Captor ArgumentCaptor<IncidentQuery> incidentQueryCaptor;

  private static void assertJsonNonExtensible(final byte[] actualBytes) {
    try {
      JSONAssert.assertEquals(
          ElementInstanceQueryControllerTest.EXPECTED_PROCESS_INSTANCE_INCIDENTS_SEARCH_RESPONSE,
          new String(Objects.requireNonNull(actualBytes), StandardCharsets.UTF_8),
          JSONCompareMode.NON_EXTENSIBLE);
    } catch (final Exception e) {
      throw new AssertionError(e);
    }
  }

  @BeforeEach
  void setupServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(elementInstanceServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(elementInstanceServices);
  }

  @Test
  void shouldSearchElementInstancesWithEmptyBody() {
    // given
    when(elementInstanceServices.search(any(FlowNodeInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(ELEMENT_INSTANCES_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(elementInstanceServices).search(new FlowNodeInstanceQuery.Builder().build());
  }

  @Test
  void shouldSearchElementInstancesWithEmptyQuery() {
    // given
    when(elementInstanceServices.search(any(FlowNodeInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    final var request = "{}";
    webClient
        .post()
        .uri(ELEMENT_INSTANCES_SEARCH_URL)
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

    verify(elementInstanceServices).search(new FlowNodeInstanceQuery.Builder().build());
  }

  @Test
  void shouldSearchElementInstancesWithAllFilters() {
    // given
    when(elementInstanceServices.search(any(FlowNodeInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    final var request =
        """
            {
              "filter":{
                "elementInstanceKey": "2251799813685996",
                "processInstanceKey": "2251799813685989",
                "processDefinitionKey": "3",
                "processDefinitionId": "complexProcess",
                "state": "ACTIVE",
                "type": "SERVICE_TASK",
                "elementId": "StartEvent_1",
                "elementName": "name",
                "hasIncident": true,
                "incidentKey": "2251799813685320",
                "tenantId": "default",
                "startDate": "2023-05-17T10:10:10Z",
                "endDate": "2023-05-23T10:10:10.000Z",
                "elementInstanceScopeKey": "2251799813685979"
              }
            }
            """;
    webClient
        .post()
        .uri(ELEMENT_INSTANCES_SEARCH_URL)
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

    verify(elementInstanceServices)
        .search(
            new FlowNodeInstanceQuery.Builder()
                .filter(
                    new FlowNodeInstanceFilter.Builder()
                        .flowNodeInstanceKeys(2251799813685996L)
                        .processInstanceKeys(2251799813685989L)
                        .processDefinitionKeys(3L)
                        .processDefinitionIds("complexProcess")
                        .states(FlowNodeState.ACTIVE.name())
                        .types(FlowNodeType.SERVICE_TASK)
                        .flowNodeIds("StartEvent_1")
                        .flowNodeNames("name")
                        .hasIncident(true)
                        .incidentKeys(2251799813685320L)
                        .tenantIds("default")
                        .startDateOperations(
                            Operation.eq(OffsetDateTime.parse("2023-05-17T10:10:10Z")))
                        .endDateOperations(
                            Operation.eq(OffsetDateTime.parse("2023-05-23T10:10:10.000Z")))
                        .elementInstanceScopeKeys(2251799813685979L)
                        .build())
                .build());
  }

  @Test
  public void shouldSearchElementInstancesWithFullSorting() {
    // given
    when(elementInstanceServices.search(any(FlowNodeInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
             {
               "sort": [
                 { "field": "elementInstanceKey", "order": "ASC" },
                 { "field": "processInstanceKey", "order": "ASC" },
                 { "field": "processDefinitionKey", "order": "ASC" },
                 { "field": "processDefinitionId", "order": "ASC" },
                 { "field": "startDate", "order": "DESC" },
                 { "field": "endDate", "order": "DESC" },
                 { "field": "elementId", "order": "ASC" },
                 { "field": "type", "order": "ASC" },
                 { "field": "state", "order": "ASC" },
                 { "field": "incidentKey", "order": "ASC" },
                 { "field": "tenantId", "order": "ASC" }
               ]
             }
            """;
    webClient
        .post()
        .uri(ELEMENT_INSTANCES_SEARCH_URL)
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

    verify(elementInstanceServices)
        .search(
            new FlowNodeInstanceQuery.Builder()
                .sort(
                    new FlowNodeInstanceSort.Builder()
                        .flowNodeInstanceKey()
                        .asc()
                        .processInstanceKey()
                        .asc()
                        .processDefinitionKey()
                        .asc()
                        .processDefinitionId()
                        .asc()
                        .startDate()
                        .desc()
                        .endDate()
                        .desc()
                        .flowNodeId()
                        .asc()
                        .type()
                        .asc()
                        .state()
                        .asc()
                        .incidentKey()
                        .asc()
                        .tenantId()
                        .asc()
                        .build())
                .build());
  }

  @Test
  void shouldGetElementInstanceByKey() {
    when(elementInstanceServices.getByKey(any(Long.class))).thenReturn(GET_QUERY_RESULT);
    // when / then
    webClient
        .get()
        .uri(ELEMENT_INSTANCES_URL + "23")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_GET_RESPONSE, JsonCompareMode.STRICT);

    verify(elementInstanceServices).getByKey(23L);
  }

  @Test
  void shouldThrowNotFoundIfKeyNotExistsForGetElementInstanceByKey() {
    when(elementInstanceServices.getByKey(any(Long.class)))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException("", CamundaSearchException.Reason.NOT_FOUND)));
    // when / then
    webClient
        .get()
        .uri(ELEMENT_INSTANCES_URL + "5")
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE)
        .expectBody()
        .json(
            """
                  {
                      "type":"about:blank",
                      "title":"NOT_FOUND",
                      "status":404,
                      "instance":"/v2/element-instances/5"
                  }
                """,
            JsonCompareMode.STRICT);

    verify(elementInstanceServices).getByKey(5L);
  }

  private static Stream<Arguments> provideAdvancedSearchParameters() {
    final var streamBuilder = Stream.<Arguments>builder();

    customOperationTestCases(
        streamBuilder,
        "state",
        ops -> new FlowNodeInstanceFilter.Builder().stateOperations(ops).build(),
        List.of(
            List.of(Operation.eq(String.valueOf(ElementInstanceStateEnum.ACTIVE))),
            List.of(Operation.neq(String.valueOf(ElementInstanceStateEnum.COMPLETED))),
            List.of(
                Operation.in(
                    String.valueOf(ElementInstanceStateEnum.COMPLETED),
                    String.valueOf(ElementInstanceStateEnum.ACTIVE)),
                Operation.like("act"))),
        true);
    dateTimeOperationTestCases(
        streamBuilder,
        "startDate",
        ops -> new FlowNodeInstanceFilter.Builder().startDateOperations(ops).build());
    dateTimeOperationTestCases(
        streamBuilder,
        "endDate",
        ops -> new FlowNodeInstanceFilter.Builder().endDateOperations(ops).build());
    return streamBuilder.build();
  }

  @ParameterizedTest
  @MethodSource("provideAdvancedSearchParameters")
  void shouldSearchFlowNodeInstancesWithAdvancedFilter(
      final String filterString, final FlowNodeInstanceFilter filter) {
    // given
    final var request =
        """
            {
                "filter": %s
            }"""
            .formatted(filterString);
    System.out.println("request = " + request);
    when(elementInstanceServices.search(queryCaptor.capture())).thenReturn(SEARCH_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(ELEMENT_INSTANCES_SEARCH_URL)
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

    verify(elementInstanceServices)
        .search(new FlowNodeInstanceQuery.Builder().filter(filter).build());
  }

  @Test
  void shouldSearchIncidentsForElementInstance() {
    // given
    when(elementInstanceServices.searchIncidents(any(Long.class), any(IncidentQuery.class)))
        .thenReturn(INCIDENT_SEARCH_RESULT);
    // when / then
    webClient
        .post()
        .uri(String.format(INCIDENTS_SEARCH_URL, 1L))
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_INCIDENT_SEARCH_RESPONSE, JsonCompareMode.STRICT);
    verify(elementInstanceServices).searchIncidents(any(Long.class), any(IncidentQuery.class));
  }

  @Test
  void shouldSearchIncidentsForElementInstanceWithNullBody() {
    // given
    when(elementInstanceServices.searchIncidents(any(Long.class), any(IncidentQuery.class)))
        .thenReturn(INCIDENT_SEARCH_RESULT);
    // when / then
    webClient
        .post()
        .uri(String.format(INCIDENTS_SEARCH_URL, 1L))
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_INCIDENT_SEARCH_RESPONSE, JsonCompareMode.STRICT);
    verify(elementInstanceServices).searchIncidents(any(Long.class), any(IncidentQuery.class));
  }

  private static Stream<Arguments> provideAdvancedIncidentSearchParameters() {
    final var streamBuilder = Stream.<Arguments>builder();

    stringOperationTestCases(
        streamBuilder,
        "processDefinitionId",
        ops -> new IncidentFilter.Builder().processDefinitionIdOperations(ops).build());

    customOperationTestCases(
        streamBuilder,
        "errorType",
        ops -> new IncidentFilter.Builder().errorTypeOperations(ops).build(),
        List.of(
            List.of(Operation.eq(String.valueOf(IncidentErrorTypeEnum.CALLED_DECISION_ERROR))),
            List.of(Operation.neq(String.valueOf(IncidentErrorTypeEnum.FORM_NOT_FOUND))),
            List.of(
                Operation.in(
                    String.valueOf(IncidentErrorTypeEnum.CALLED_DECISION_ERROR),
                    String.valueOf(IncidentErrorTypeEnum.FORM_NOT_FOUND)),
                Operation.like("ERROR")),
            List.of(
                Operation.notIn(
                    String.valueOf(IncidentErrorTypeEnum.CALLED_DECISION_ERROR),
                    String.valueOf(IncidentErrorTypeEnum.FORM_NOT_FOUND)),
                Operation.like("ERROR"))),
        true);

    stringOperationTestCases(
        streamBuilder,
        "errorMessage",
        ops -> new IncidentFilter.Builder().errorMessageOperations(ops).build());

    stringOperationTestCases(
        streamBuilder,
        "elementId",
        ops -> new IncidentFilter.Builder().flowNodeIdOperations(ops).build());

    dateTimeOperationTestCases(
        streamBuilder,
        "creationTime",
        ops -> new IncidentFilter.Builder().creationTimeOperations(ops).build());

    customOperationTestCases(
        streamBuilder,
        "state",
        ops -> new IncidentFilter.Builder().stateOperations(ops).build(),
        List.of(
            List.of(Operation.eq(String.valueOf(IncidentStateEnum.PENDING))),
            List.of(Operation.neq(String.valueOf(IncidentStateEnum.RESOLVED))),
            List.of(
                Operation.in(
                    String.valueOf(IncidentStateEnum.PENDING),
                    String.valueOf(IncidentStateEnum.RESOLVED)),
                Operation.like("com")),
            List.of(
                Operation.notIn(
                    String.valueOf(IncidentStateEnum.PENDING),
                    String.valueOf(IncidentStateEnum.RESOLVED)),
                Operation.like("com"))),
        true);

    stringOperationTestCases(
        streamBuilder,
        "tenantId",
        ops -> new IncidentFilter.Builder().tenantIdOperations(ops).build());

    keyOperationTestCases(
        streamBuilder,
        "incidentKey",
        ops -> new IncidentFilter.Builder().incidentKeyOperations(ops).build());

    keyOperationTestCases(
        streamBuilder,
        "processDefinitionKey",
        ops -> new IncidentFilter.Builder().processDefinitionKeyOperations(ops).build());

    keyOperationTestCases(
        streamBuilder,
        "processInstanceKey",
        ops -> new IncidentFilter.Builder().processInstanceKeyOperations(ops).build());

    keyOperationTestCases(
        streamBuilder, "jobKey", ops -> new IncidentFilter.Builder().jobKeyOperations(ops).build());

    return streamBuilder.build();
  }

  @ParameterizedTest
  @MethodSource("provideAdvancedIncidentSearchParameters")
  void shouldSearchIncidentsForProcessInstance(
      final String filterString, final IncidentFilter filter) {
    // given
    final var request =
        """
            {
                "filter": %s
            }"""
            .formatted(filterString);
    when(elementInstanceServices.searchIncidents(eq(123L), incidentQueryCaptor.capture()))
        .thenReturn(SEARCH_INCIDENT_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(ELEMENT_INSTANCE_INCIDENTS_SEARCH_URL, 123L)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .consumeWith(result -> assertJsonNonExtensible(result.getResponseBody()));

    verify(elementInstanceServices)
        .searchIncidents(123L, new IncidentQuery.Builder().filter(filter).build());
  }
}
