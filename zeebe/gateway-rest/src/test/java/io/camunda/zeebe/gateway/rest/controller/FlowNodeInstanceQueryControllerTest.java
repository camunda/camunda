/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.security.auth.Authentication;
import io.camunda.search.sort.FlowNodeInstanceSort;
import io.camunda.service.FlowNodeInstanceServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(
    value = FlowNodeInstanceQueryController.class,
    properties = "camunda.rest.query.enabled=true")
public class FlowNodeInstanceQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
              "items": [
                  {}
              ],
              "page": {
                  "totalItems": 1,
                  "firstSortValues": [],
                  "lastSortValues": [
                      "v"
                  ]
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
                      3L,
                      "2023-05-17",
                      "2023-05-23",
                      "flowNodeId",
                      "processInstanceKey/flowNodeId",
                      FlowNodeType.SERVICE_TASK,
                      FlowNodeState.ACTIVE,
                      false,
                      null,
                      null,
                      "bpmnProcessId",
                      "<default>")))
          .sortValues(new Object[] {"v"})
          .build();

  static final String EXPECTED_GET_RESPONSE =
      """
                 {
                   "flowNodeInstanceKey":23,
                   "processInstanceKey":5,
                   "processDefinitionKey":17,
                   "processDefinitionId":"complexProcess",
                   "startDate":"startDate",
                   "endDate":"endDate",
                   "flowNodeId":"startEvent_1",
                   "treePath":"5/23",
                   "type":"SERVICE_TASK",
                   "state":"ACTIVE",
                   "hasIncident":true,
                   "incidentKey":1234,
                   "tenantId":"tenantId"
                 }
          """;

  static final FlowNodeInstanceEntity GET_QUERY_RESULT =
      new FlowNodeInstanceEntity(
          23L,
          5L,
          17L,
          "startDate",
          "endDate",
          "startEvent_1",
          "5/23",
          FlowNodeType.SERVICE_TASK,
          FlowNodeState.ACTIVE,
          true,
          1234L,
          null,
          "complexProcess",
          "tenantId");

  static final String FLOW_NODE_INSTANCES_URL = "/v2/flownode-instances/";
  static final String FLOW_NODE_INSTANCES_SEARCH_URL = FLOW_NODE_INSTANCES_URL + "search";

  @MockBean FlowNodeInstanceServices flowNodeInstanceServices;

  @BeforeEach
  void setupServices() {
    when(flowNodeInstanceServices.withAuthentication(any(Authentication.class)))
        .thenReturn(flowNodeInstanceServices);
  }

  @Test
  void shouldSearchFlownodeInstancesWithEmptyBody() {
    // given
    when(flowNodeInstanceServices.search(any(FlowNodeInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(FLOW_NODE_INSTANCES_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(flowNodeInstanceServices).search(new FlowNodeInstanceQuery.Builder().build());
  }

  @Test
  void shouldSearchFlownodeInstancesWithEmptyQuery() {
    // given
    when(flowNodeInstanceServices.search(any(FlowNodeInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    final var request = "{}";
    webClient
        .post()
        .uri(FLOW_NODE_INSTANCES_SEARCH_URL)
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

    verify(flowNodeInstanceServices).search(new FlowNodeInstanceQuery.Builder().build());
  }

  @Test
  void shouldSearchFlownodeInstancesWithAllFilters() {
    // given
    when(flowNodeInstanceServices.search(any(FlowNodeInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    final var request =
        """
            {
              "filter":{
                "flowNodeInstanceKey": 2251799813685996,
                "processInstanceKey": 2251799813685989,
                "processDefinitionKey": 3,
                "processDefinitionId": "complexProcess",
                "state": "ACTIVE",
                "type": "SERVICE_TASK",
                "flowNodeId": "StartEvent_1",
                "flowNodeName": "name",
                "treePath": "2251799813685989/2251799813685996",
                "hasIncident": true,
                "incidentKey": 2251799813685320,
                "tenantId": "default"
              }
            }
            """;
    webClient
        .post()
        .uri(FLOW_NODE_INSTANCES_SEARCH_URL)
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

    verify(flowNodeInstanceServices)
        .search(
            new FlowNodeInstanceQuery.Builder()
                .filter(
                    new FlowNodeInstanceFilter.Builder()
                        .flowNodeInstanceKeys(2251799813685996L)
                        .processInstanceKeys(2251799813685989L)
                        .processDefinitionKeys(3L)
                        .processDefinitionIds("complexProcess")
                        .states(FlowNodeState.ACTIVE)
                        .types(FlowNodeType.SERVICE_TASK)
                        .flowNodeIds("StartEvent_1")
                        .treePaths("2251799813685989/2251799813685996")
                        .hasIncident(true)
                        .incidentKeys(2251799813685320L)
                        .tenantIds("default")
                        .build())
                .build());
  }

  @Test
  public void shouldSearchFlownodeInstancesWithFullSorting() {
    // given
    when(flowNodeInstanceServices.search(any(FlowNodeInstanceQuery.class)))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
             {
               "sort": [
                 { "field": "flowNodeInstanceKey", "order": "ASC" },
                 { "field": "processInstanceKey", "order": "ASC" },
                 { "field": "processDefinitionKey", "order": "ASC" },
                 { "field": "processDefinitionId", "order": "ASC" },
                 { "field": "startDate", "order": "DESC" },
                 { "field": "endDate", "order": "DESC" },
                 { "field": "flowNodeId", "order": "ASC" },
                 { "field": "type", "order": "ASC" },
                 { "field": "state", "order": "ASC" },
                 { "field": "incidentKey", "order": "ASC" },
                 { "field": "tenantId", "order": "ASC" }
               ]
             }
            """;
    webClient
        .post()
        .uri(FLOW_NODE_INSTANCES_SEARCH_URL)
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

    verify(flowNodeInstanceServices)
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
  void shouldGetFlowNodeInstanceByKey() {
    when(flowNodeInstanceServices.getByKey(any(Long.class))).thenReturn(GET_QUERY_RESULT);
    // when / then
    webClient
        .get()
        .uri(FLOW_NODE_INSTANCES_URL + "23")
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(EXPECTED_GET_RESPONSE);

    verify(flowNodeInstanceServices).getByKey(23L);
  }

  @Disabled("Enable when RestErrorMapper handling of not found is in place.")
  @Test
  void shouldThrowNotFoundIfKeyNotExistsForGetFlowNodeInstanceByKey() {
    when(flowNodeInstanceServices.getByKey(any(Long.class))).thenThrow(new NotFoundException(""));
    // when / then
    webClient
        .get()
        .uri(FLOW_NODE_INSTANCES_URL + "5")
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
                      "title":"Failed to execute Get Flow node instance by key.",
                      "status":404,
                      "instance":"/v2/flownode-instances/5"
                  }
                """);

    verify(flowNodeInstanceServices).getByKey(5L);
  }
}
