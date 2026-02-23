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
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByDefinitionEntity;
import io.camunda.search.entities.IncidentProcessInstanceStatisticsByErrorEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByDefinitionQuery;
import io.camunda.search.query.IncidentProcessInstanceStatisticsByErrorQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.IncidentSort;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.IncidentServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = IncidentController.class)
public class IncidentQueryControllerTest extends RestControllerTest {

  static final String INCIDENT_URL = "/v2/incidents/";
  static final String INCIDENT_SEARCH_URL = INCIDENT_URL + "search";

  static final String INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_ERROR_URL =
      INCIDENT_URL + "statistics/process-instances-by-error";
  static final String INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_URL =
      INCIDENT_URL + "statistics/process-instances-by-definition";
  static final Integer ERROR_HASH_CODE = 123456;

  static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
              "items": [
                  {
                      "incidentKey": "5",
                      "processDefinitionKey": "23",
                      "processDefinitionId": "complexProcess",
                      "processInstanceKey": "42",
                      "rootProcessInstanceKey": "37",
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
                  "startCursor": "f",
                  "endCursor": "v",
                  "hasMoreTotalItems": false
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
                      37L,
                      ErrorType.JOB_NO_RETRIES,
                      "No retries left.",
                      "elementId",
                      17L,
                      OffsetDateTime.parse("2024-05-23T23:05:00.000Z"),
                      IncidentState.ACTIVE,
                      101L,
                      "tenantId")))
          .startCursor("f")
          .endCursor("v")
          .build();

  static final String EXPECTED_GET_RESPONSE =
      """
            {
                          "incidentKey": "5",
                          "processDefinitionKey": "23",
                          "processDefinitionId": "complexProcess",
                          "processInstanceKey": "42",
                          "rootProcessInstanceKey": "37",
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
          37L,
          ErrorType.JOB_NO_RETRIES,
          "No retries left.",
          "elementId",
          17L,
          OffsetDateTime.parse("2024-05-23T23:05:00.000Z"),
          IncidentState.ACTIVE,
          101L,
          "tenantId");
  static final SearchQueryResult<IncidentProcessInstanceStatisticsByDefinitionEntity>
      INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_QUERY_RESULT =
          new SearchQueryResult.Builder<IncidentProcessInstanceStatisticsByDefinitionEntity>()
              .items(
                  List.of(
                      new IncidentProcessInstanceStatisticsByDefinitionEntity.Builder()
                          .processDefinitionId("order-process-id")
                          .processDefinitionKey(2251799813685249L)
                          .processDefinitionName("Order process")
                          .processDefinitionVersion(1)
                          .tenantId("<default>")
                          .activeInstancesWithErrorCount(3L)
                          .build()))
              .total(1L)
              .startCursor(null)
              .endCursor(null)
              .build();
  private static final String EXPECTED_INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_RESPONSE =
      """
          {
            "items": [
              {
                "processDefinitionId": "order-process-id",
                "processDefinitionKey": "2251799813685249",
                "processDefinitionName": "Order process",
                "processDefinitionVersion": 1,
                "tenantId": "<default>",
                "activeInstancesWithErrorCount": 3
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
  private static final String EXPECTED_INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_ERROR_RESPONSE =
      """
          {
            "items": [
              {
                "errorHashCode": 123456,
                "errorMessage": "This is an error message",
                "activeInstancesWithErrorCount": 10
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

  private static final SearchQueryResult<IncidentProcessInstanceStatisticsByErrorEntity>
      INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_ERROR_QUERY_RESULT =
          new SearchQueryResult.Builder<IncidentProcessInstanceStatisticsByErrorEntity>()
              .items(
                  List.of(
                      new IncidentProcessInstanceStatisticsByErrorEntity(
                          123456, "This is an error message", 10L)))
              .total(1L)
              .startCursor(null)
              .endCursor(null)
              .build();

  @MockitoBean IncidentServices incidentServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setupIncidentServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(incidentServices.withAuthentication(ArgumentMatchers.any(CamundaAuthentication.class)))
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
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

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
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(incidentServices).search(new IncidentQuery.Builder().build());
  }

  @Test
  void shouldSearchIncidentWithAllFilters() {
    when(incidentServices.search(any(IncidentQuery.class))).thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
            {
              "filter":{
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
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

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
                        .errorTypes(ErrorType.JOB_NO_RETRIES.name())
                        .errorMessages("No retries left.")
                        .flowNodeIds("elementId")
                        .flowNodeInstanceKeys(17L)
                        .creationTime(creationTime)
                        .states(IncidentState.ACTIVE.name())
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
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

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
        .json(EXPECTED_GET_RESPONSE, JsonCompareMode.STRICT);

    verify(incidentServices).getByKey(23L);
  }

  @Test
  void shouldThrowNotFoundIfKeyNotExistsForGetIncidentByKey() {
    when(incidentServices.getByKey(any(Long.class)))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException("", CamundaSearchException.Reason.NOT_FOUND)));
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
                """,
            JsonCompareMode.STRICT);

    verify(incidentServices).getByKey(5L);
  }

  @Test
  void shouldReturnIncidentProcessInstanceStatisticsByError() {
    when(incidentServices.incidentProcessInstanceStatisticsByError(
            any(IncidentProcessInstanceStatisticsByErrorQuery.class)))
        .thenReturn(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_ERROR_QUERY_RESULT);

    webClient
        .post()
        .uri(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_ERROR_URL)
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            EXPECTED_INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_ERROR_RESPONSE,
            JsonCompareMode.STRICT);

    verify(incidentServices)
        .incidentProcessInstanceStatisticsByError(
            new IncidentProcessInstanceStatisticsByErrorQuery.Builder().build());
  }

  @Test
  void shouldSortIncidentProcessInstanceStatisticsByError() {
    // given
    when(incidentServices.incidentProcessInstanceStatisticsByError(
            any(IncidentProcessInstanceStatisticsByErrorQuery.class)))
        .thenReturn(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_ERROR_QUERY_RESULT);

    final var request =
        """
        {
          "sort": [
            {
              "field": "errorMessage",
              "order": "asc"
            },
            {
              "field": "activeInstancesWithErrorCount",
              "order": "desc"
            }
          ]
        }
        """;

    // when/then
    webClient
        .post()
        .uri(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_ERROR_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            EXPECTED_INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_ERROR_RESPONSE,
            JsonCompareMode.STRICT);

    verify(incidentServices)
        .incidentProcessInstanceStatisticsByError(
            new IncidentProcessInstanceStatisticsByErrorQuery.Builder()
                .sort(s -> s.errorMessage().asc().activeInstancesWithErrorCount().desc())
                .build());
  }

  @Test
  void shouldPaginateIncidentProcessInstanceStatisticsByError() {
    // given
    when(incidentServices.incidentProcessInstanceStatisticsByError(
            any(IncidentProcessInstanceStatisticsByErrorQuery.class)))
        .thenReturn(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_ERROR_QUERY_RESULT);

    final var request =
        """
            {
              "page": { "from": 0, "limit": 5 }
            }
            """;

    // when/then
    webClient
        .post()
        .uri(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_ERROR_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            EXPECTED_INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_ERROR_RESPONSE,
            JsonCompareMode.STRICT);

    verify(incidentServices)
        .incidentProcessInstanceStatisticsByError(
            new IncidentProcessInstanceStatisticsByErrorQuery.Builder()
                .page(p -> p.from(0).size(5))
                .build());
  }

  @Test
  void shouldReturnIncidentProcessInstanceStatisticsByDefinition() {
    // given
    when(incidentServices.searchIncidentProcessInstanceStatisticsByDefinition(
            any(IncidentProcessInstanceStatisticsByDefinitionQuery.class)))
        .thenReturn(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_QUERY_RESULT);

    final var request =
        """
          {
            "filter": { "errorHashCode": %d }
          }
        """
            .formatted(ERROR_HASH_CODE);
    // when/then
    webClient
        .post()
        .uri(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            EXPECTED_INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_RESPONSE,
            JsonCompareMode.STRICT);

    final var result =
        new IncidentProcessInstanceStatisticsByDefinitionQuery.Builder()
            .filter(f -> f.errorHashCode(ERROR_HASH_CODE).state(IncidentState.ACTIVE.name()))
            .build();
    verify(incidentServices).searchIncidentProcessInstanceStatisticsByDefinition(result);
  }

  @Test
  void shouldRejectIncidentProcessInstanceStatisticsByDefinitionRequestWithoutBody() {
    // given
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "Bad Request",
                  "status": 400,
                  "detail": "Required request body is missing",
                  "instance": "%s"
                }""",
            INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_URL);
    // when/then
    webClient
        .post()
        .uri(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  void shouldRejectIncidentProcessInstanceStatisticsByDefinitionRequestWithoutFilter() {
    // given
    final var request =
        """
           {}
        """;

    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "No filter provided.",
                  "instance": "%s"
                }""",
            INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_URL);

    // when/then
    webClient
        .post()
        .uri(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_URL)
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
  }

  @Test
  void shouldRejectIncidentProcessInstanceStatisticsByDefinitionRequestWithEmptyFilter() {
    // given
    final var request =
        """
           {
             "filter": {}
           }
        """;

    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "At least one of filter criteria is required.",
                  "instance": "%s"
                }""",
            INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_URL);

    // when/then
    webClient
        .post()
        .uri(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_URL)
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
  }

  @Test
  void shouldSortIncidentProcessInstanceStatisticsByDefinition() {
    // given
    when(incidentServices.searchIncidentProcessInstanceStatisticsByDefinition(
            any(IncidentProcessInstanceStatisticsByDefinitionQuery.class)))
        .thenReturn(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_QUERY_RESULT);

    final var request =
        """
        {
          "filter": {
            "errorHashCode": 123456
          },
          "sort": [
            {
              "field": "processDefinitionKey",
              "order": "asc"
            },
            {
              "field": "tenantId",
              "order": "desc"
            },
            {
              "field": "activeInstancesWithErrorCount",
              "order": "asc"
            }
          ]
        }
        """;

    // when/then
    webClient
        .post()
        .uri(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            EXPECTED_INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_RESPONSE,
            JsonCompareMode.STRICT);

    verify(incidentServices)
        .searchIncidentProcessInstanceStatisticsByDefinition(
            new IncidentProcessInstanceStatisticsByDefinitionQuery.Builder()
                .filter(f -> f.errorHashCode(ERROR_HASH_CODE).state(IncidentState.ACTIVE.name()))
                .sort(
                    s ->
                        s.processDefinitionKey()
                            .asc()
                            .tenantId()
                            .desc()
                            .activeInstancesWithErrorCount()
                            .asc())
                .build());
  }

  @Test
  void shouldPaginateIncidentProcessInstanceStatisticsByDefinition() {
    // given
    when(incidentServices.searchIncidentProcessInstanceStatisticsByDefinition(
            any(IncidentProcessInstanceStatisticsByDefinitionQuery.class)))
        .thenReturn(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_QUERY_RESULT);

    final var request =
        """
            {
              "filter": { "errorHashCode": 123456 },
              "page": { "from": 0, "limit": 5 }
            }
            """;

    // when/then
    webClient
        .post()
        .uri(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            EXPECTED_INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_DEFINITION_RESPONSE,
            JsonCompareMode.STRICT);

    verify(incidentServices)
        .searchIncidentProcessInstanceStatisticsByDefinition(
            new IncidentProcessInstanceStatisticsByDefinitionQuery.Builder()
                .filter(f -> f.errorHashCode(ERROR_HASH_CODE).state(IncidentState.ACTIVE.name()))
                .page(p -> p.from(0).size(5))
                .build());
  }

  @Test
  void shouldRejectIncidentProcessInstanceStatisticsByErrorRequestWithFilter() {
    final var request =
        """
            {
              "filter": {
                "errorHashCode": 123456
              }
            }
            """;

    webClient
        .post()
        .uri(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_ERROR_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        // We only assert the stable bits here: 400 + instance path. The detail message for unknown
        // properties is produced by Jackson and may change between versions.
        .json(
            """
                {
                  "type": "about:blank",
                  "title": "Bad Request",
                  "status": 400,
                  "instance": "%s"
                }
                """
                .formatted(INCIDENT_PROCESS_INSTANCE_STATISTICS_BY_ERROR_URL),
            JsonCompareMode.LENIENT);
  }
}
