/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.FlowNodeInstanceSort;
import io.camunda.security.auth.Authentication;
import io.camunda.service.ElementInstanceServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.cache.ProcessCache;
import io.camunda.zeebe.gateway.rest.cache.ProcessCacheItem;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(value = ElementInstanceController.class)
public class ElementInstanceQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
              "items": [
                  {
                   "elementInstanceKey":"1",
                   "processInstanceKey":"2",
                   "processDefinitionKey":"3",
                   "processDefinitionId":"bpmnProcessId",
                   "startDate": "2023-05-17T00:00:00.000Z",
                   "endDate":"2023-05-23T00:00:00.000Z",
                   "elementId":"elementId",
                   "elementName":"elementName",
                   "type":"SERVICE_TASK",
                   "state":"ACTIVE",
                   "hasIncident":false,
                   "tenantId":"<default>"
                 }
              ],
              "page": {
                  "totalItems": 1,
                  "firstSortValues": ["f"],
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
                      OffsetDateTime.parse("2023-05-17T00:00:00Z"),
                      OffsetDateTime.parse("2023-05-23T00:00:00Z"),
                      "elementId",
                      null,
                      FlowNodeType.SERVICE_TASK,
                      FlowNodeState.ACTIVE,
                      false,
                      null,
                      "bpmnProcessId",
                      "<default>")))
          .firstSortValues(new Object[] {"f"})
          .lastSortValues(new Object[] {"v"})
          .build();

  static final String EXPECTED_GET_RESPONSE =
      """
                 {
                   "elementInstanceKey":"23",
                   "processInstanceKey":"5",
                   "processDefinitionKey":"17",
                   "processDefinitionId":"complexProcess",
                   "startDate": "2023-05-17T10:10:10.000Z",
                   "endDate":"2023-05-23T10:10:10.000Z",
                   "elementId":"startEvent_1",
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
          17L,
          OffsetDateTime.parse("2023-05-17T10:10:10Z"),
          OffsetDateTime.parse("2023-05-23T10:10:10Z"),
          "startEvent_1",
          null,
          FlowNodeType.SERVICE_TASK,
          FlowNodeState.ACTIVE,
          true,
          1234L,
          "complexProcess",
          "tenantId");

  static final String ELEMENT_INSTANCES_URL = "/v2/element-instances/";
  static final String ELEMENT_INSTANCES_SEARCH_URL = ELEMENT_INSTANCES_URL + "search";

  @MockBean ElementInstanceServices elementInstanceServices;
  @MockBean ProcessCache processCache;

  @BeforeEach
  void setupServices() {
    when(elementInstanceServices.withAuthentication(any(Authentication.class)))
        .thenReturn(elementInstanceServices);
    when(processCache.getElementName(any())).thenReturn("elementName");
    final var processCacheItem = mock(ProcessCacheItem.class);
    when(processCacheItem.getElementName(any())).thenReturn("elementName");
    final Map<Long, ProcessCacheItem> processDefinitionMap = mock(HashMap.class);
    when(processDefinitionMap.getOrDefault(any(), any())).thenReturn(processCacheItem);
    when(processCache.getElementNames(any())).thenReturn(processDefinitionMap);
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
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(elementInstanceServices).search(new FlowNodeInstanceQuery.Builder().build());
    verify(processCache).getElementNames(any());
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
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(elementInstanceServices).search(new FlowNodeInstanceQuery.Builder().build());
    verify(processCache).getElementNames(any());
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
                "tenantId": "default"
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
        .json(EXPECTED_SEARCH_RESPONSE);

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
                        .hasIncident(true)
                        .incidentKeys(2251799813685320L)
                        .tenantIds("default")
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
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(elementInstanceServices)
        .search(
            new FlowNodeInstanceQuery.Builder()
                .sort(
                    new FlowNodeInstanceSort.Builder()
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
    verify(processCache).getElementNames(any());
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
        .json(EXPECTED_GET_RESPONSE);

    verify(elementInstanceServices).getByKey(23L);
    verify(processCache).getElementName(any());
  }

  @Test
  void shouldThrowNotFoundIfKeyNotExistsForGetElementInstanceByKey() {
    when(elementInstanceServices.getByKey(any(Long.class)))
        .thenThrow(new CamundaSearchException("", CamundaSearchException.Reason.NOT_FOUND));
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
                """);

    verify(elementInstanceServices).getByKey(5L);
    verify(processCache, never()).getElementName(any());
  }
}
