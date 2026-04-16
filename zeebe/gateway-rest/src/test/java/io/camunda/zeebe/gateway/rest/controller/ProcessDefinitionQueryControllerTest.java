/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.gateway.protocol.model.ProcessDefinitionSearchQuerySortRequest;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessDefinitionInstanceStatisticsEntity;
import io.camunda.search.entities.ProcessDefinitionInstanceVersionStatisticsEntity;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.FormServices;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import io.camunda.zeebe.gateway.rest.controller.adapter.DefaultProcessDefinitionServiceAdapter;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@ExtendWith(MockitoExtension.class)
@Import(DefaultProcessDefinitionServiceAdapter.class)
@WebMvcTest(value = ProcessDefinitionController.class)
public class ProcessDefinitionQueryControllerTest extends RestControllerTest {
  static final String PROCESS_DEFINITION_URL = "/v2/process-definitions/";
  static final String PROCESS_DEFINITION_SEARCH_URL = PROCESS_DEFINITION_URL + "search";
  static final String PROCESS_DEFINITION_VERSION_STATISTICS_URL =
      PROCESS_DEFINITION_URL + "statistics/process-instances-by-version";

  static final ProcessDefinitionEntity PROCESS_DEFINITION_ENTITY =
      new ProcessDefinitionEntity(
          23L,
          "Complex process",
          "complexProcess",
          "<xml/>",
          "complexProcess.bpmn",
          5,
          "alpha",
          "<default>",
          "formId");
  static final String PROCESS_DEFINITION_ENTITY_JSON =
      """
      {
          "processDefinitionKey": "23",
          "name": "Complex process",
          "processDefinitionId": "complexProcess",
          "resourceName": "complexProcess.bpmn",
          "version": 5,
          "versionTag": "alpha",
          "tenantId": "<default>",
          "hasStartForm": true
      }""";
  static final String EXPECTED_SEARCH_RESPONSE =
      """
      {
          "items": [
              {
                  "processDefinitionKey": "1",
                  "name": "Complex process",
                  "processDefinitionId": "complexProcess",
                  "resourceName": "complexProcess.bpmn",
                  "version": 5,
                  "versionTag": "alpha",
                  "tenantId": "<default>",
                  "hasStartForm": true
              }
          ],
          "page": {
              "totalItems": 1,
              "startCursor": "f",
              "endCursor": "v",
              "hasMoreTotalItems": false
          }
      }""";
  static final SearchQueryResult<ProcessDefinitionEntity> SEARCH_QUERY_RESULT =
      new Builder<ProcessDefinitionEntity>()
          .total(1L)
          .items(
              List.of(
                  new ProcessDefinitionEntity(
                      1L,
                      "Complex process",
                      "complexProcess",
                      "<xml/>",
                      "complexProcess.bpmn",
                      5,
                      "alpha",
                      "<default>",
                      "formId")))
          .startCursor("f")
          .endCursor("v")
          .build();
  private static final String FORM_ITEM_JSON =
      """
      {
        "formKey": "0",
        "tenantId": "tenant-1",
        "formId": "formId",
        "schema": "schema",
        "version": 1
      }
      """;
  @MockitoBean ProcessDefinitionServices processDefinitionServices;

  @MockitoBean FormServices formServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @Captor
  ArgumentCaptor<io.camunda.search.query.ProcessDefinitionInstanceStatisticsQuery>
      instanceStatsQueryCaptor;

  @Captor
  ArgumentCaptor<io.camunda.search.query.ProcessDefinitionInstanceVersionStatisticsQuery>
      instanceVersionStatsQueryCaptor;

  @BeforeEach
  void setupProcessDefinitionServices() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldSearchProcessDefinitionWithEmptyBody() {
    // given
    when(processDefinitionServices.search(any(ProcessDefinitionQuery.class), any()))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(processDefinitionServices)
        .search(eq(new ProcessDefinitionQuery.Builder().build()), any());
  }

  @Test
  public void shouldReturn404ForInvalidProcessDefinitionKey() {
    // given
    when(processDefinitionServices.getByKey(eq(17L), any()))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    "Process definition with key 17 not found",
                    CamundaSearchException.Reason.NOT_FOUND)));
    // when / then
    webClient
        .get()
        .uri(PROCESS_DEFINITION_URL + "17")
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
                      "detail": "Process definition with key 17 not found",
                      "instance": "/v2/process-definitions/17"
                    }
                """,
            JsonCompareMode.STRICT);

    // Verify that the service was called with the invalid key
    verify(processDefinitionServices).getByKey(eq(17L), any());
  }

  @Test
  public void shouldReturnProcessDefinitionForValidKey() {
    // given
    when(processDefinitionServices.getByKey(eq(23L), any())).thenReturn(PROCESS_DEFINITION_ENTITY);

    // when / then
    webClient
        .get()
        .uri(PROCESS_DEFINITION_URL + "23")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(PROCESS_DEFINITION_ENTITY_JSON, JsonCompareMode.STRICT);

    // Verify that the service was called with the valid key
    verify(processDefinitionServices).getByKey(eq(23L), any());
  }

  @ParameterizedTest
  @MethodSource("getProcessDefinitionTestCasesParameters")
  public void shouldReturn403ForForbiddenProcessDefinitionKey(
      final Pair<String, BiFunction<ProcessDefinitionServices, Long, ?>> testParameter) {
    // given
    final var url = testParameter.getLeft();
    final var service = testParameter.getRight();
    final long processDefinitionKey = 17L;
    when(service.apply(processDefinitionServices, processDefinitionKey))
        .thenThrow(
            ErrorMapper.createForbiddenException(
                Authorization.of(a -> a.processDefinition().read())));
    // when / then
    final String formattedUrl = url.formatted(processDefinitionKey);
    webClient
        .get()
        .uri(formattedUrl)
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .json(
            """
                    {
                      "type": "about:blank",
                      "status": 403,
                      "title": "FORBIDDEN",
                      "detail": "Unauthorized to perform operation 'READ' on resource 'PROCESS_DEFINITION'",
                      "instance": "%s"
                    }
                """
                .formatted(formattedUrl),
            JsonCompareMode.STRICT);

    // Verify that the service was called with the invalid key
    service.apply(verify(processDefinitionServices), processDefinitionKey);
  }

  private static Stream<Pair<String, BiFunction<ProcessDefinitionServices, Long, ?>>>
      getProcessDefinitionTestCasesParameters() {
    return Stream.of(
        Pair.of(PROCESS_DEFINITION_URL + "%d", (service, key) -> service.getByKey(eq(key), any())),
        Pair.of(
            PROCESS_DEFINITION_URL + "%d/xml",
            (service, key) -> service.getProcessDefinitionXml(eq(key), any())),
        Pair.of(
            PROCESS_DEFINITION_URL + "%d/form",
            (service, key) -> service.getProcessDefinitionStartForm(eq(key), any())));
  }

  @Test
  public void shouldGetElementStatistics() {
    // given
    final long processDefinitionKey = 1L;
    final var stats = List.of(new ProcessFlowNodeStatisticsEntity("node1", 1L, 1L, 1L, 1L));
    when(processDefinitionServices.elementStatistics(any(), any())).thenReturn(stats);
    final var request =
        """
            {
              "filter": {
                "hasIncident": true
              }
            }""";

    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_URL + "1/statistics/element-instances")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk();

    verify(processDefinitionServices)
        .elementStatistics(
            eq(
                new ProcessDefinitionStatisticsFilter.Builder(processDefinitionKey)
                    .hasIncident(true)
                    .build()),
            any());
  }

  @Test
  public void shouldGetElementStatisticsWithOrOperator() {
    // given
    final long processDefinitionKey = 1L;
    final var stats = List.of(new ProcessFlowNodeStatisticsEntity("node1", 1L, 1L, 1L, 1L));
    when(processDefinitionServices.elementStatistics(any(), any())).thenReturn(stats);
    final var request =
        """
            {
              "filter": {
                "state": "ACTIVE",
                "$or": [
                  { "elementId": "elementId" },
                  { "processInstanceKey": "123", "hasElementInstanceIncident": true }
                ]
              }
            }""";
    final var response =
        """
            {"items":[
              {
                "elementId": "node1",
                "active": 1,
                "canceled": 1,
                "incidents": 1,
                "completed": 1
              }
            ]}""";

    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_URL + "1/statistics/element-instances")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(response, JsonCompareMode.STRICT);

    verify(processDefinitionServices)
        .elementStatistics(
            eq(
                new ProcessDefinitionStatisticsFilter.Builder(processDefinitionKey)
                    .states("ACTIVE")
                    .addOrOperation(
                        new ProcessDefinitionStatisticsFilter.Builder(processDefinitionKey)
                            .flowNodeIds("elementId")
                            .build())
                    .addOrOperation(
                        new ProcessDefinitionStatisticsFilter.Builder(processDefinitionKey)
                            .processInstanceKeys(123L)
                            .hasFlowNodeInstanceIncident(true)
                            .build())
                    .build()),
            any());
  }

  @Test
  public void shouldGetProcessDefinitionXml() {
    // given
    when(processDefinitionServices.getProcessDefinitionXml(eq(23L), any()))
        .thenReturn(Optional.of("<xml/>"));
    // when / then
    webClient
        .get()
        .uri(PROCESS_DEFINITION_URL + "23/xml")
        .accept(MediaType.TEXT_XML)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .xml("<xml/>");
    // Verify that the service was called with the valid key
    verify(processDefinitionServices).getProcessDefinitionXml(eq(23L), any());
  }

  @Test
  public void shouldGetProcessDefinitionXmlHasNoXml() {
    // given
    when(processDefinitionServices.getProcessDefinitionXml(eq(23L), any()))
        .thenReturn(Optional.empty());
    // when / then
    webClient
        .get()
        .uri(PROCESS_DEFINITION_URL + "23/xml")
        .accept(MediaType.TEXT_XML)
        .exchange()
        .expectStatus()
        .isNoContent();
    // Verify that the service was called with the valid key
    verify(processDefinitionServices).getProcessDefinitionXml(eq(23L), any());
  }

  @Test
  public void shouldReturnFormItemForValidFormKey() {
    when(processDefinitionServices.getProcessDefinitionStartForm(eq(1L), any()))
        .thenReturn(Optional.of(new FormEntity(0L, "tenant-1", "formId", "schema", 1L)));

    webClient
        .get()
        .uri(PROCESS_DEFINITION_URL + "1/form")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(FORM_ITEM_JSON, JsonCompareMode.STRICT);

    verify(processDefinitionServices, times(1)).getProcessDefinitionStartForm(eq(1L), any());
  }

  @Test
  public void shouldReturn404ForFormInvaliProcessKey() {
    when(processDefinitionServices.getProcessDefinitionStartForm(eq(999L), any()))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    "Process definition with key 999 not found",
                    CamundaSearchException.Reason.NOT_FOUND)));
    webClient
        .get()
        .uri(PROCESS_DEFINITION_URL + "999/form")
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
              "detail": "Process definition with key 999 not found",
              "instance": "/v2/process-definitions/999/form"
            }
            """,
            JsonCompareMode.STRICT);
  }

  @Test
  public void shouldReturn500OnUnexpectedException() {
    when(processDefinitionServices.getProcessDefinitionStartForm(eq(1L), any()))
        .thenThrow(new RuntimeException("Unexpected error"));

    webClient
        .get()
        .uri(PROCESS_DEFINITION_URL + "1/form")
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
              "instance": "/v2/process-definitions/1/form"
            }
            """,
            JsonCompareMode.STRICT);
  }

  private static Stream<Arguments> provideAdvancedSearchParameters() {
    final var streamBuilder = Stream.<Arguments>builder();

    stringOperationTestCases(
        streamBuilder,
        "name",
        ops -> new ProcessDefinitionFilter.Builder().nameOperations(ops).build());

    stringOperationTestCases(
        streamBuilder,
        "processDefinitionId",
        ops -> new ProcessDefinitionFilter.Builder().processDefinitionIdOperations(ops).build());

    return streamBuilder.build();
  }

  @ParameterizedTest
  @MethodSource("provideAdvancedSearchParameters")
  void shouldSearchProcessWithAdvancedFilter(
      final String filterString, final ProcessDefinitionFilter filter) {
    // given
    final var request =
        """
            {
                "filter": %s
            }"""
            .formatted(filterString);
    when(processDefinitionServices.search(any(ProcessDefinitionQuery.class), any()))
        .thenReturn(SEARCH_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_SEARCH_URL)
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

    verify(processDefinitionServices)
        .search(eq(new ProcessDefinitionQuery.Builder().filter(filter).build()), any());
  }

  @Test
  public void shouldRejectProcessDefinitionInstanceStatisticsQueryWithFilter() {
    // given
    final var statsResult =
        new io.camunda.search.query.SearchQueryResult.Builder<
                ProcessDefinitionInstanceStatisticsEntity>()
            .total(0L)
            .items(List.of())
            .startCursor(null)
            .endCursor(null)
            .build();
    when(processDefinitionServices.getProcessDefinitionInstanceStatistics(any(), any()))
        .thenReturn(statsResult);

    final var request = "{ \"filter\": {} }";
    final var expectedBody =
        """
        {
          "type":"about:blank",
          "title":"Bad Request",
          "status":400,"detail":"Request property [filter] cannot be parsed","instance":"/v2/process-definitions/statistics/process-instances"}
        """;

    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_URL + "statistics/process-instances")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedBody, JsonCompareMode.STRICT);
  }

  @Test
  public void shouldGetProcessDefinitionInstanceStatisticsWithMissingFilter() {
    // given
    final var statsResult =
        new io.camunda.search.query.SearchQueryResult.Builder<
                ProcessDefinitionInstanceStatisticsEntity>()
            .total(0L)
            .items(List.of())
            .startCursor(null)
            .endCursor(null)
            .build();
    when(processDefinitionServices.getProcessDefinitionInstanceStatistics(any(), any()))
        .thenReturn(statsResult);

    final var request = "{}";
    final var response =
        """
        {
          "items": [],
          "page": {
            "totalItems": 0,
            "startCursor": null,
            "endCursor": null,
            "hasMoreTotalItems": false
          }
        }
        """;

    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_URL + "statistics/process-instances")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(response, JsonCompareMode.STRICT);

    verify(processDefinitionServices)
        .getProcessDefinitionInstanceStatistics(instanceStatsQueryCaptor.capture(), any());
    final var capturedQuery = instanceStatsQueryCaptor.getValue();
    assertThat(capturedQuery).isNotNull();
    final var filter = capturedQuery.filter();
    assertThat(filter).isNotNull();
    assertThat(filter.orFilters() == null || filter.orFilters().isEmpty()).isTrue();
  }

  @Test
  public void shouldGetProcessDefinitionInstanceStatisticsWithNoRequestBody() {
    // given
    final var statsResult =
        new io.camunda.search.query.SearchQueryResult.Builder<
                ProcessDefinitionInstanceStatisticsEntity>()
            .total(0L)
            .items(List.of())
            .startCursor(null)
            .endCursor(null)
            .build();
    when(processDefinitionServices.getProcessDefinitionInstanceStatistics(any(), any()))
        .thenReturn(statsResult);

    final var response =
        """
        {
          "items": [],
          "page": {
            "totalItems": 0,
            "startCursor": null,
            "endCursor": null,
            "hasMoreTotalItems": false
          }
        }
        """;

    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_URL + "statistics/process-instances")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(response, JsonCompareMode.STRICT);

    verify(processDefinitionServices)
        .getProcessDefinitionInstanceStatistics(instanceStatsQueryCaptor.capture(), any());
    final var capturedQuery = instanceStatsQueryCaptor.getValue();
    assertThat(capturedQuery).isNotNull();
    final var filter = capturedQuery.filter();
    assertThat(filter).isNotNull();
    assertThat(filter.stateOperations()).isNotEmpty();
    assertThat(filter.stateOperations().getFirst().values()).containsExactly("ACTIVE");
  }

  @Test
  public void shouldGetProcessDefinitionInstanceVersionStatistics() {
    // given
    final String processDefinitionId = "process_definition_id";
    final var statsEntity =
        new ProcessDefinitionInstanceVersionStatisticsEntity(
            "process_definition_id", 2L, 3, "process_definition_name", "<default>", 4L, 0L);
    final var statsResult =
        new Builder<ProcessDefinitionInstanceVersionStatisticsEntity>()
            .total(1L)
            .items(List.of(statsEntity))
            .startCursor(null)
            .endCursor(null)
            .build();
    when(processDefinitionServices.searchProcessDefinitionInstanceVersionStatistics(
            any(ProcessDefinitionInstanceVersionStatisticsQuery.class), any()))
        .thenReturn(statsResult);
    final var request =
        """
            {
              "filter": {
                "processDefinitionId": "process_definition_id"
              },
              "sort": [
                {
                  "field": "activeInstancesWithoutIncidentCount",
                  "order": "DESC"
                }
              ]
            }""";
    final var response =
        """
            {"items":[
              {
                "processDefinitionId": "process_definition_id",
                "processDefinitionKey": "2",
                "processDefinitionName": "process_definition_name",
                "tenantId": "<default>",
                "processDefinitionVersion": 3,
                "activeInstancesWithoutIncidentCount": 4,
                "activeInstancesWithIncidentCount": 0
              }
            ],
            "page": {
              "totalItems": 1,
              "startCursor": null,
              "endCursor": null,
              "hasMoreTotalItems": false
              }
            }""";

    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_VERSION_STATISTICS_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(response, JsonCompareMode.STRICT);

    verify(processDefinitionServices)
        .searchProcessDefinitionInstanceVersionStatistics(
            instanceVersionStatsQueryCaptor.capture(), any());
    final var capturedQuery = instanceVersionStatsQueryCaptor.getValue();
    assertThat(capturedQuery).isNotNull();
    assertThat(capturedQuery.filter().processDefinitionId()).isEqualTo(processDefinitionId);
    final var sort = capturedQuery.sort();
    assertThat(sort).isNotNull();
    assertThat(sort.getFieldSortings().size()).isEqualTo(1);
    assertThat("activeInstancesWithoutIncidentCount")
        .isEqualTo(sort.getFieldSortings().getFirst().field());
    assertThat(io.camunda.search.sort.SortOrder.DESC)
        .isEqualTo(sort.orderings().getFirst().order());
  }

  @Test
  void shouldRejectVersionStatisticsRequestWithoutBody() {
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
            PROCESS_DEFINITION_VERSION_STATISTICS_URL);
    // when/then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_VERSION_STATISTICS_URL)
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
  void shouldRejectVersionStatisticsRequestWithEmptyBody() {
    // given
    final var request = "{}";

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
            PROCESS_DEFINITION_VERSION_STATISTICS_URL);

    // when/then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_VERSION_STATISTICS_URL)
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
  void shouldRejectVersionStatisticsRequestWithEmptyFilter() {
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
                  "detail": "No processDefinitionId provided.",
                  "instance": "%s"
                }""",
            PROCESS_DEFINITION_VERSION_STATISTICS_URL);

    // when/then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_VERSION_STATISTICS_URL)
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
  void shouldInvalidateProcessDefinitionSearchQueryWithNegativeLimit() {
    // given
    final var request =
        """
            {
                "page": {
                    "limit": -1
                }
            }""";
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "The value for page.limit is '-1' but must be a non-negative number.",
                  "instance": "%s"
                }""",
            PROCESS_DEFINITION_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_SEARCH_URL)
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

    verify(processDefinitionServices, never()).search(any(ProcessDefinitionQuery.class), any());
  }

  @Test
  void shouldRejectBeforePaginationWhenIsLatestVersionIsTrue() {
    // given
    final var request =
        """
            {
                "filter": {
                    "isLatestVersion": true
                },
                "page": {
                    "before": "someCursor"
                }
            }""";
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "When using isLatestVersion filter, pagination is limited to forward pagination using 'after' and 'limit'. The field 'before' is not supported.",
                  "instance": "%s"
                }""",
            PROCESS_DEFINITION_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_SEARCH_URL)
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

    verify(processDefinitionServices, never()).search(any(ProcessDefinitionQuery.class), any());
  }

  @Test
  void shouldRejectFromPaginationWhenIsLatestVersionIsTrue() {
    // given
    final var request =
        """
            {
                "filter": {
                    "isLatestVersion": true
                },
                "page": {
                    "from": 10
                }
            }""";
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "When using isLatestVersion filter, pagination is limited to forward pagination using 'after' and 'limit'. The field 'from' is not supported.",
                  "instance": "%s"
                }""",
            PROCESS_DEFINITION_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_SEARCH_URL)
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

    verify(processDefinitionServices, never()).search(any(ProcessDefinitionQuery.class), any());
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessDefinitionSearchQuerySortRequest.FieldEnum.class,
      names = {"PROCESS_DEFINITION_ID", "TENANT_ID"},
      mode = EnumSource.Mode.EXCLUDE)
  void shouldRejectUnsupportedSortFieldWhenIsLatestVersionIsTrue(
      final ProcessDefinitionSearchQuerySortRequest.FieldEnum unsupportedField) {
    // given
    final var request =
        String.format(
            """
            {
                "filter": {
                    "isLatestVersion": true
                },
                "sort": [
                    {
                        "field": "%s",
                        "order": "ASC"
                    }
                ]
            }""",
            unsupportedField.getValue());
    final var expectedResponse =
        String.format(
            """
                {
                  "type": "about:blank",
                  "title": "INVALID_ARGUMENT",
                  "status": 400,
                  "detail": "When using isLatestVersion filter, sorting is limited to 'processDefinitionId' and 'tenantId' fields only. The field '%s' is not supported.",
                  "instance": "%s"
                }""",
            unsupportedField.getValue(), PROCESS_DEFINITION_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_SEARCH_URL)
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

    verify(processDefinitionServices, never()).search(any(ProcessDefinitionQuery.class), any());
  }

  @Test
  void shouldAllowAfterPaginationWhenIsLatestVersionIsTrue() {
    // given
    final var request =
        """
            {
                "filter": {
                    "isLatestVersion": true
                },
                "page": {
                    "after": "someCursor",
                    "limit": 10
                }
            }""";
    when(processDefinitionServices.search(any(ProcessDefinitionQuery.class), any()))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_SEARCH_URL)
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

    verify(processDefinitionServices).search(any(ProcessDefinitionQuery.class), any());
  }

  @ParameterizedTest
  @EnumSource(
      value = ProcessDefinitionSearchQuerySortRequest.FieldEnum.class,
      names = {"PROCESS_DEFINITION_ID", "TENANT_ID"})
  void shouldAllowSupportedSortFieldsWhenIsLatestVersionIsTrue(
      final ProcessDefinitionSearchQuerySortRequest.FieldEnum supportedField) {
    // given
    final var request =
        String.format(
            """
            {
                "filter": {
                    "isLatestVersion": true
                },
                "sort": [
                    {
                        "field": "%s",
                        "order": "ASC"
                    }
                ]
            }""",
            supportedField.getValue());
    when(processDefinitionServices.search(any(ProcessDefinitionQuery.class), any()))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_SEARCH_URL)
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

    verify(processDefinitionServices).search(any(ProcessDefinitionQuery.class), any());
  }
}
