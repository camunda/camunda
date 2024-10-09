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

import io.camunda.search.entities.ProcessDefinitionEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.security.auth.Authentication;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;

@WebMvcTest(
    value = ProcessDefinitionQueryController.class,
    properties = "camunda.rest.query.enabled=true")
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
          "<default>");
  static final String PROCESS_DEFINITION_ENTITY_JSON =
      """
      {
          "processDefinitionKey": 23,
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
                  "processDefinitionKey": 1,
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
              "firstSortValues": [],
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
                      "<default>")))
          .sortValues(new Object[] {"v"})
          .build();

  @MockBean ProcessDefinitionServices processDefinitionServices;

  @BeforeEach
  void setupProcessDefinitionServices() {
    when(processDefinitionServices.withAuthentication(ArgumentMatchers.any(Authentication.class)))
        .thenReturn(processDefinitionServices);
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
        .thenThrow(new NotFoundException("Process Definition with key 17 not found"));
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
                      "detail": "Process Definition with key 17 not found"
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
}
