/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.gateway.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.entities.ProcessFlowNodeStatisticsEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.FormServices;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.stream.Stream;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(value = ProcessDefinitionController.class)
public class ProcessDefinitionQueryControllerTest extends RestControllerTest {
  static final String PROCESS_DEFINITION_URL = "/v2/process-definitions/";
  static final String PROCESS_DEFINITION_SEARCH_URL = PROCESS_DEFINITION_URL + "search";

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
          "tenantId": "<default>"
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
                  "tenantId": "<default>"
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
          .firstSortValues(new Object[] {"f"})
          .lastSortValues(new Object[] {"v"})
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
  @MockBean ProcessDefinitionServices processDefinitionServices;

  @MockBean FormServices formServices;

  @BeforeEach
  void setupProcessDefinitionServices() {
    when(processDefinitionServices.withAuthentication(ArgumentMatchers.any(Authentication.class)))
        .thenReturn(processDefinitionServices);

    when(formServices.withAuthentication(ArgumentMatchers.any(Authentication.class)))
        .thenReturn(formServices);
  }

  @Test
  void shouldSearchProcessDefinitionWithEmptyBody() {
    // given
    when(processDefinitionServices.search(any(ProcessDefinitionQuery.class)))
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
        .json(EXPECTED_SEARCH_RESPONSE);

    verify(processDefinitionServices).search(new ProcessDefinitionQuery.Builder().build());
  }

  @Test
  public void shouldReturn404ForInvalidProcessDefinitionKey() {
    // given
    when(processDefinitionServices.getByKey(17L))
        .thenThrow(
            new CamundaSearchException(
                "Process definition with key 17 not found",
                CamundaSearchException.Reason.NOT_FOUND));
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
                      "detail": "Process definition with key 17 not found"
                    }
                """);

    // Verify that the service was called with the invalid key
    verify(processDefinitionServices).getByKey(17L);
  }

  @Test
  public void shouldReturnProcessDefinitionForValidKey() {
    // given
    when(processDefinitionServices.getByKey(23L)).thenReturn(PROCESS_DEFINITION_ENTITY);

    // when / then
    webClient
        .get()
        .uri(PROCESS_DEFINITION_URL + "23")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(PROCESS_DEFINITION_ENTITY_JSON);

    // Verify that the service was called with the valid key
    verify(processDefinitionServices).getByKey(23L);
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
        .thenThrow(new ForbiddenException(Authorization.of(a -> a.processDefinition().read())));
    // when / then
    webClient
        .get()
        .uri(url.formatted(processDefinitionKey))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .json(
            """
                    {
                      "type": "about:blank",
                      "status": 403,
                      "title": "io.camunda.service.exception.ForbiddenException",
                      "detail": "Unauthorized to perform operation 'READ' on resource 'PROCESS_DEFINITION'"
                    }
                """);

    // Verify that the service was called with the invalid key
    service.apply(verify(processDefinitionServices), processDefinitionKey);
  }

  private static Stream<Pair<String, BiFunction<ProcessDefinitionServices, Long, ?>>>
      getProcessDefinitionTestCasesParameters() {
    return Stream.of(
        Pair.of(PROCESS_DEFINITION_URL + "%d", ProcessDefinitionServices::getByKey),
        Pair.of(
            PROCESS_DEFINITION_URL + "%d/xml", ProcessDefinitionServices::getProcessDefinitionXml),
        Pair.of(PROCESS_DEFINITION_URL + "%d/form", ProcessDefinitionServices::getByKey));
  }

  @Test
  public void shouldGetFlowNodeStatistics() {
    // given
    final long processDefinitionKey = 1L;
    final var stats = List.of(new ProcessFlowNodeStatisticsEntity("node1", 1L, 1L, 1L, 1L));
    when(processDefinitionServices.flowNodeStatistics(any())).thenReturn(stats);
    final var request =
        """
            {
              "filter": {
                "hasIncident": true
              }
            }""";
    final var response =
        """
            {"items":[
              {
                "flowNodeId": "node1",
                "active": 1,
                "canceled": 1,
                "incidents": 1,
                "completed": 1
              }
            ]}""";

    // when / then
    webClient
        .post()
        .uri(PROCESS_DEFINITION_URL + "1/statistics/flownode-instances")
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(request)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(response);

    verify(processDefinitionServices)
        .flowNodeStatistics(
            new ProcessDefinitionStatisticsFilter.Builder(processDefinitionKey)
                .hasIncident(true)
                .build());
  }

  @Test
  public void shouldGetProcessDefinitionXml() {
    // given
    when(processDefinitionServices.getProcessDefinitionXml(23L)).thenReturn(Optional.of("<xml/>"));
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
    verify(processDefinitionServices).getProcessDefinitionXml(23L);
  }

  @Test
  public void shouldGetProcessDefinitionXmlHasNoXml() {
    // given
    when(processDefinitionServices.getProcessDefinitionXml(23L)).thenReturn(Optional.empty());
    // when / then
    webClient
        .get()
        .uri(PROCESS_DEFINITION_URL + "23/xml")
        .accept(MediaType.TEXT_XML)
        .exchange()
        .expectStatus()
        .isNoContent();
    // Verify that the service was called with the valid key
    verify(processDefinitionServices).getProcessDefinitionXml(23L);
  }

  @Test
  public void shouldReturnFormItemForValidFormKey() throws Exception {
    when(processDefinitionServices.getByKey(1L))
        .thenReturn(
            new ProcessDefinitionEntity(
                1L, "name", "id", "xml", "resource", 1, "tag", "tenant", "formId"));
    when(formServices.getLatestVersionByFormId("formId"))
        .thenReturn(Optional.of(new FormEntity(0L, "tenant-1", "formId", "schema", 1L)));

    webClient
        .get()
        .uri(PROCESS_DEFINITION_URL + "1/form")
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .json(FORM_ITEM_JSON);

    verify(processDefinitionServices, times(1)).getByKey(1L);
    verify(formServices, times(1)).getLatestVersionByFormId("formId");
  }

  @Test
  public void shouldReturn404ForFormInvaliProcessKey() throws Exception {
    when(processDefinitionServices.getByKey(999L))
        .thenThrow(
            new CamundaSearchException(
                "Process definition with key 999 not found",
                CamundaSearchException.Reason.NOT_FOUND));
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
              "detail": "Process definition with key 999 not found"
            }
            """);
  }

  @Test
  public void shouldReturn500OnUnexpectedException() throws Exception {
    when(processDefinitionServices.getByKey(1L))
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
            """);
  }
}
