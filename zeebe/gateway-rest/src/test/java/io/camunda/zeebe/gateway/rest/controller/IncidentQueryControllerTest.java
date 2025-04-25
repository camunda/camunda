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

import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.DateValueFilter;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.IncidentSort;
import io.camunda.security.auth.Authentication;
import io.camunda.service.IncidentServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(value = IncidentController.class)
public class IncidentQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
              "items": [
                  {
                      "incidentKey": "5",
                      "processDefinitionKey": "23",
                      "processDefinitionId": "complexProcess",
                      "processInstanceKey": "42",
                      "errorType": "JOB_NO_RETRIES",
                      "errorMessage": "No retries left.",
                      "elementId": "elementId",
                      "elementInstanceKey": "17",
                      "creationTime": "2024-05-23T23:05:00.000Z",
                      "state": "ACTIVE",
                      "jobKey": "101",
                      "tenantId": "tenantId"
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

  static final SearchQueryResult<IncidentEntity> SEARCH_QUERY_RESULT =
      new Builder<IncidentEntity>()
          .total(1L)
          .items(
              List.of(
                  new IncidentEntity(
                      5L,
                      23L,
                      "complexProcess",
                      42L,
                      ErrorType.JOB_NO_RETRIES,
                      "No retries left.",
                      "elementId",
                      17L,
                      OffsetDateTime.parse("2024-05-23T23:05:00.000Z"),
                      IncidentState.ACTIVE,
                      101L,
                      "tenantId")))
          .firstSortValues(new Object[] {"f"})
          .lastSortValues(new Object[] {"v"})
          .build();

  static final String EXPECTED_GET_RESPONSE =
      """
            {
                          "incidentKey": "5",
                          "processDefinitionKey": "23",
                          "processDefinitionId": "complexProcess",
                          "processInstanceKey": "42",
                          "errorType": "JOB_NO_RETRIES",
                          "errorMessage": "No retries left.",
                          "elementId": "elementId",
                          "elementInstanceKey": "17",
                          "creationTime": "2024-05-23T23:05:00.000Z",
                          "state": "ACTIVE",
                          "jobKey": "101",
                          "tenantId": "tenantId"
                      }
          """;

  static final IncidentEntity GET_QUERY_RESULT =
      new IncidentEntity(
          5L,
          23L,
          "complexProcess",
          42L,
          ErrorType.JOB_NO_RETRIES,
          "No retries left.",
          "elementId",
          17L,
          OffsetDateTime.parse("2024-05-23T23:05:00.000Z"),
          IncidentState.ACTIVE,
          101L,
          "tenantId");

  static final String INCIDENT_URL = "/v2/incidents/";
  static final String INCIDENT_SEARCH_URL = INCIDENT_URL + "search";

  @MockBean IncidentServices incidentServices;

  @BeforeEach
  void setupIncidentServices() {
    when(incidentServices.withAuthentication(ArgumentMatchers.any(Authentication.class)))
        .thenReturn(incidentServices);
  }

  @Test
  void shouldSearchIncidentWithEmptyBody() {
    // given
    when(incidentServices.search(any(IncidentQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(INCIDENT_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(incidentServices).search(new IncidentQuery.Builder().build());
  }

  @Test
  void shouldSearchIncidentWithEmptyQuery() {
    // given
    when(incidentServices.search(any(IncidentQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
    final var request = "{}";
    // when / then
    webClient
        .post()
        .uri(INCIDENT_SEARCH_URL)
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

    verify(incidentServices).search(new IncidentQuery.Builder().build());
  }

  @Test
  void shouldSearchIncidentWithAllFilters() {
    when(incidentServices.search(any(IncidentQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
            {
              "filter":{
                "incidentKey": 5,
                "processDefinitionKey": 23,
                "processDefinitionId": "complexProcess",
                "processInstanceKey": 42,
                "errorType": "JOB_NO_RETRIES",
                "errorMessage": "No retries left.",
                "elementId": "elementId",
                "elementInstanceKey": 17,
                "creationTime": "2024-05-23T23:05:00.000Z",
                "state": "ACTIVE",
                "jobKey": 101,
                "tenantId": "tenantId"
              }
            }
            """;

    // when / then
    webClient
        .post()
        .uri(INCIDENT_SEARCH_URL)
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

    final var creationTime = OffsetDateTime.of(2024, 5, 23, 23, 5, 0, 0, ZoneOffset.UTC);

    verify(incidentServices)
        .search(
            new IncidentQuery.Builder()
                .filter(
                    new IncidentFilter.Builder()
                        .incidentKeys(5L)
                        .processDefinitionKeys(23L)
                        .processDefinitionIds("complexProcess")
                        .processInstanceKeys(42L)
                        .errorTypes(ErrorType.JOB_NO_RETRIES)
                        .errorMessages("No retries left.")
                        .flowNodeIds("elementId")
                        .flowNodeInstanceKeys(17L)
                        .creationTime(
                            new DateValueFilter.Builder()
                                .before(creationTime)
                                .after(creationTime)
                                .build())
                        .states(IncidentState.ACTIVE)
                        .jobKeys(101L)
                        .tenantIds("tenantId")
                        .build())
                .build());
  }

  @Test
  void shouldSearchIncidentWithFullSorting() {
    when(incidentServices.search(any(IncidentQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
            {
                "sort": [
                    {
                        "field": "incidentKey",
                        "order": "ASC"
                    }
                ]
            }
            """;
    // when / then
    webClient
        .post()
        .uri(INCIDENT_SEARCH_URL)
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

    verify(incidentServices)
        .search(
            new IncidentQuery.Builder()
                .sort(new IncidentSort.Builder().incidentKey().asc().build())
                .build());
  }

  @Test
  void shouldGetIncidentByKey() {
    when(incidentServices.getByKey(any(Long.class))).thenReturn(GET_QUERY_RESULT);
    // when / then
    webClient
        .get()
        .uri(INCIDENT_URL + "23")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_GET_RESPONSE);

    verify(incidentServices).getByKey(23L);
  }

  @Test
  void shouldThrowNotFoundIfKeyNotExistsForGetIncidentByKey() {
    when(incidentServices.getByKey(any(Long.class)))
        .thenThrow(new CamundaSearchException("", CamundaSearchException.Reason.NOT_FOUND));
    // when / then
    webClient
        .get()
        .uri(INCIDENT_URL + "5")
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
                      "instance":"/v2/incidents/5"
                  }
                """);

    verify(incidentServices).getByKey(5L);
  }
}
