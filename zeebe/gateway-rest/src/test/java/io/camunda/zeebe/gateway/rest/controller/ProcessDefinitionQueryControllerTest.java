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
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.security.auth.Authentication;
import io.camunda.service.ProcessDefinitionServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
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
}
