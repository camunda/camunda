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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.entities.AgentDefinitionEntity;
import io.camunda.search.entities.AgentDefinitionEntity.AgentType;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.AgentDefinitionFilter;
import io.camunda.search.query.AgentDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.SearchQueryResult.Builder;
import io.camunda.search.sort.AgentDefinitionSort;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.security.core.auth.RequiredAuthorization;
import io.camunda.service.AgentDefinitionServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.registry.ServiceRegistry;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = AgentDefinitionController.class)
public class AgentDefinitionQueryControllerTest extends RestControllerTest {

  static final String EXPECTED_SEARCH_RESPONSE =
      """
          {
              "items": [
                  {
                      "agentDefinitionKey": "1",
                      "agentType": "AI_AGENT_TASK",
                      "name": "name",
                      "elementId": "elementId",
                      "processDefinitionId": "processId",
                      "processDefinitionKey": "2",
                      "processDefinitionVersion": 1,
                      "processDefinitionVersionTag": "v1",
                      "tenantId": "t"
                  }
              ],
              "page": {
                  "totalItems": 1,
                  "startCursor": "f",
                  "endCursor": "v",
                  "hasMoreTotalItems": false
              }
          }""";

  static final AgentDefinitionEntity AGENT_DEFINITION_ENTITY =
      new AgentDefinitionEntity(
          1L, AgentType.AI_AGENT_TASK, "name", "elementId", "processId", 2L, 1, "v1", "t");

  static final SearchQueryResult<AgentDefinitionEntity> SEARCH_QUERY_RESULT =
      new Builder<AgentDefinitionEntity>()
          .total(1L)
          .items(List.of(AGENT_DEFINITION_ENTITY))
          .startCursor("f")
          .endCursor("v")
          .build();

  static final String AGENT_DEFINITIONS_SEARCH_URL = "/v2/agent-definitions/search";
  static final String AGENT_DEFINITIONS_GET_URL = "/v2/agent-definitions/%d";

  @MockitoBean AgentDefinitionServices agentDefinitionServices;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean ServiceRegistry serviceRegistry;

  @BeforeEach
  void setupServices() {
    when(serviceRegistry.agentDefinitionServices(any())).thenReturn(agentDefinitionServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldSearchAgentDefinitionsWithEmptyBody() {
    // given
    when(agentDefinitionServices.search(any(AgentDefinitionQuery.class), any()))
        .thenReturn(SEARCH_QUERY_RESULT);
    // when / then
    webClient
        .post()
        .uri(AGENT_DEFINITIONS_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(agentDefinitionServices).search(eq(new AgentDefinitionQuery.Builder().build()), any());
  }

  @Test
  void shouldSearchAgentDefinitionsWithEmptyQuery() {
    // given
    when(agentDefinitionServices.search(any(AgentDefinitionQuery.class), any()))
        .thenReturn(SEARCH_QUERY_RESULT);
    final String request = "{}";
    // when / then
    webClient
        .post()
        .uri(AGENT_DEFINITIONS_SEARCH_URL)
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

    verify(agentDefinitionServices).search(eq(new AgentDefinitionQuery.Builder().build()), any());
  }

  @Test
  void shouldSearchAgentDefinitionsWithAllFilters() {
    // given
    when(agentDefinitionServices.search(any(AgentDefinitionQuery.class), any()))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
            {
              "filter":{
                "agentDefinitionKey": "1",
                "agentType": "AI_AGENT_TASK",
                "name": "name",
                "elementId": "elementId",
                "processDefinitionId": "processId",
                "processDefinitionKey": "2",
                "processDefinitionVersion": 1,
                "processDefinitionVersionTag": "v1",
                "tenantId": "t"
              }
            }""";

    // when / then
    webClient
        .post()
        .uri(AGENT_DEFINITIONS_SEARCH_URL)
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

    verify(agentDefinitionServices)
        .search(
            eq(
                new AgentDefinitionQuery.Builder()
                    .filter(
                        new AgentDefinitionFilter.Builder()
                            .agentDefinitionKeys(1L)
                            .agentTypes("AI_AGENT_TASK")
                            .names("name")
                            .elementIds("elementId")
                            .processDefinitionIds("processId")
                            .processDefinitionKeys(2L)
                            .processDefinitionVersions(1)
                            .processDefinitionVersionTags("v1")
                            .tenantIds("t")
                            .build())
                    .build()),
            any());
  }

  @Test
  void shouldSearchAgentDefinitionsWithFullSorting() {
    // given
    when(agentDefinitionServices.search(any(AgentDefinitionQuery.class), any()))
        .thenReturn(SEARCH_QUERY_RESULT);
    final var request =
        """
            {
                "sort": [
                    {
                        "field": "agentDefinitionKey",
                        "order": "ASC"
                    },
                    {
                        "field": "agentType",
                        "order": "DESC"
                    },
                    {
                         "field": "name"
                    },
                    {
                         "field": "elementId"
                    },
                    {
                         "field": "processDefinitionId"
                    },
                    {
                         "field": "processDefinitionKey"
                    },
                    {
                         "field": "processDefinitionVersion"
                    },
                    {
                         "field": "processDefinitionVersionTag"
                    },
                    {
                         "field": "tenantId"
                    }
                ]
            }""";
    // when / then
    webClient
        .post()
        .uri(AGENT_DEFINITIONS_SEARCH_URL)
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

    verify(agentDefinitionServices)
        .search(
            eq(
                new AgentDefinitionQuery.Builder()
                    .sort(
                        new AgentDefinitionSort.Builder()
                            .agentDefinitionKey()
                            .asc()
                            .agentType()
                            .desc()
                            .name()
                            .asc()
                            .elementId()
                            .asc()
                            .processDefinitionId()
                            .asc()
                            .processDefinitionKey()
                            .asc()
                            .processDefinitionVersion()
                            .asc()
                            .processDefinitionVersionTag()
                            .asc()
                            .tenantId()
                            .asc()
                            .build())
                    .build()),
            any());
  }

  @Test
  void shouldInvalidateAgentDefinitionsSearchQueryWithBadSortField() {
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
        """
            {
              "type": "about:blank",
              "title": "Bad Request",
              "status": 400,
              "detail": "Unexpected value 'unknownField' for enum field 'field'. Use any of the following values: [agentDefinitionKey, agentType, name, elementId, processDefinitionId, processDefinitionKey, processDefinitionVersion, processDefinitionVersionTag, tenantId]",
              "instance": "%s"
            }"""
            .formatted(AGENT_DEFINITIONS_SEARCH_URL);
    // when / then
    webClient
        .post()
        .uri(AGENT_DEFINITIONS_SEARCH_URL)
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

    verify(agentDefinitionServices, never()).search(any(AgentDefinitionQuery.class), any());
  }

  @Test
  public void shouldGetAgentDefinitionByKey() {
    // given
    final Long agentDefinitionKey = 1L;
    when(agentDefinitionServices.getByKey(eq(agentDefinitionKey), any()))
        .thenReturn(AGENT_DEFINITION_ENTITY);
    final var expectedResponse =
        """
            {
              "agentDefinitionKey": "1",
              "agentType": "AI_AGENT_TASK",
              "name": "name",
              "elementId": "elementId",
              "processDefinitionId": "processId",
              "processDefinitionKey": "2",
              "processDefinitionVersion": 1,
              "processDefinitionVersionTag": "v1",
              "tenantId": "t"
            }""";
    // when/then
    webClient
        .get()
        .uri(AGENT_DEFINITIONS_GET_URL.formatted(agentDefinitionKey))
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  public void shouldReturn404ForNotFoundAgentDefinitionByKey() {
    // given
    final Long agentDefinitionKey = 1L;
    when(agentDefinitionServices.getByKey(eq(agentDefinitionKey), any()))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    "Agent definition with key 1 was not found.",
                    CamundaSearchException.Reason.NOT_FOUND)));

    // when/then
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "NOT_FOUND",
              "status": 404,
              "detail": "Agent definition with key 1 was not found.",
              "instance": "%s"
            }"""
            .formatted(AGENT_DEFINITIONS_GET_URL.formatted(agentDefinitionKey));
    webClient
        .get()
        .uri(AGENT_DEFINITIONS_GET_URL.formatted(agentDefinitionKey))
        .exchange()
        .expectStatus()
        .isNotFound()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  public void shouldReturn500ForInternalErrorGetAgentDefinitionByKey() {
    // given
    final Long agentDefinitionKey = 1L;
    when(agentDefinitionServices.getByKey(eq(agentDefinitionKey), any()))
        .thenThrow(new RuntimeException("Failed to get agent definition."));

    // when/then
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "title": "java.lang.RuntimeException",
              "status": 500,
              "detail": "Unexpected error occurred during the request processing: Failed to get agent definition.",
              "instance": "%s"
            }"""
            .formatted(AGENT_DEFINITIONS_GET_URL.formatted(agentDefinitionKey));
    webClient
        .get()
        .uri(AGENT_DEFINITIONS_GET_URL.formatted(agentDefinitionKey))
        .exchange()
        .expectStatus()
        .is5xxServerError()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }

  @Test
  public void shouldReturn403ForForbiddenAgentDefinitionByKey() {
    // given
    final Long agentDefinitionKey = 1L;
    when(agentDefinitionServices.getByKey(eq(agentDefinitionKey), any()))
        .thenThrow(
            ErrorMapper.createForbiddenException(
                RequiredAuthorization.of(a -> a.processDefinition().readProcessDefinition())));

    // when/then
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "status": 403,
              "title": "FORBIDDEN",
              "detail": "Unauthorized to perform operation 'READ_PROCESS_DEFINITION' on resource 'PROCESS_DEFINITION'",
              "instance": "%s"
            }"""
            .formatted(AGENT_DEFINITIONS_GET_URL.formatted(agentDefinitionKey));
    webClient
        .get()
        .uri(AGENT_DEFINITIONS_GET_URL.formatted(agentDefinitionKey))
        .exchange()
        .expectStatus()
        .isForbidden()
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);

    verify(agentDefinitionServices).getByKey(eq(agentDefinitionKey), any());
  }

  @Test
  public void shouldReturn400ForInvalidKey() {
    // given
    final String agentDefinitionKey = "invalidKey";

    // when/then
    final var expectedResponse =
        """
            {
              "type": "about:blank",
              "status": 400,
              "title": "Bad Request",
              "detail": "Failed to convert 'agentDefinitionKey' with value: 'invalidKey'",
              "instance": "/v2/agent-definitions/invalidKey"
            }""";

    webClient
        .get()
        .uri("/v2/agent-definitions/%s".formatted(agentDefinitionKey))
        .exchange()
        .expectStatus()
        .isBadRequest()
        .expectHeader()
        .contentType(MediaType.APPLICATION_PROBLEM_JSON)
        .expectBody()
        .json(expectedResponse, JsonCompareMode.STRICT);
  }
}
