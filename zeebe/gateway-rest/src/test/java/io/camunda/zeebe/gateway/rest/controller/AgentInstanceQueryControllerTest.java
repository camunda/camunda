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

import io.camunda.search.entities.AgentInstanceEntity;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceDefinition;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceLimits;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceMetrics;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceStatus;
import io.camunda.search.entities.AgentInstanceEntity.AgentInstanceTool;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.AgentInstanceFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.AgentInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.AgentInstanceServices;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(AgentInstanceController.class)
class AgentInstanceQueryControllerTest extends RestControllerTest {

  static final String AGENT_INSTANCES_URL = "/v2/agent-instances";
  static final String AGENT_INSTANCES_SEARCH_URL = AGENT_INSTANCES_URL + "/search";

  private static final long ELEMENT_INSTANCE_KEY = 2251799813685248L;
  private static final long AGENT_INSTANCE_KEY = 9007199254741017L;
  private static final long PROCESS_INSTANCE_KEY = 9007199254741001L;
  private static final long ROOT_PROCESS_INSTANCE_KEY = 9007199254741000L;
  private static final long PROCESS_DEFINITION_KEY = 9007199254740992L;
  private static final OffsetDateTime CREATION_DATE =
      OffsetDateTime.parse("2024-01-01T10:00:00+00:00");
  private static final OffsetDateTime LAST_UPDATED_DATE =
      OffsetDateTime.parse("2024-01-01T10:05:00+00:00");
  private static final OffsetDateTime COMPLETION_DATE =
      OffsetDateTime.parse("2024-01-01T10:05:00+00:00");

  private static final AgentInstanceEntity AGENT_INSTANCE_ENTITY =
      new AgentInstanceEntity(
          AGENT_INSTANCE_KEY,
          List.of(ELEMENT_INSTANCE_KEY),
          AgentInstanceStatus.COMPLETED,
          new AgentInstanceDefinition("gpt-4o", "openai", "You are a helpful assistant."),
          new AgentInstanceMetrics(100L, 200L, 3, 5),
          new AgentInstanceLimits(10000L, 10, 50),
          List.of(new AgentInstanceTool("search", "Search the web", "searchElement")),
          "AgentTask",
          PROCESS_INSTANCE_KEY,
          ROOT_PROCESS_INSTANCE_KEY,
          PROCESS_DEFINITION_KEY,
          "myProcessId",
          1,
          "v1",
          "<default>",
          CREATION_DATE,
          LAST_UPDATED_DATE,
          COMPLETION_DATE);

  private static final String EXPECTED_SEARCH_RESPONSE =
      """
      {
        "items": [
          {
            "agentInstanceKey": "%d",
            "status": "COMPLETED",
            "definition": {
              "model": "gpt-4o",
              "provider": "openai",
              "systemPrompt": "You are a helpful assistant."
            },
            "metrics": {
              "inputTokens": 100,
              "outputTokens": 200,
              "modelCalls": 3,
              "toolCalls": 5
            },
            "limits": {
              "maxModelCalls": 10,
              "maxTokens": 10000,
              "maxToolCalls": 50
            },
            "tools": [
              {
                "name": "search",
                "description": "Search the web",
                "elementId": "searchElement"
              }
            ],
            "elementId": "AgentTask",
            "processInstanceKey": "%d",
            "rootProcessInstanceKey": "%d",
            "processDefinitionKey": "%d",
            "processDefinitionId": "myProcessId",
            "processDefinitionVersion": 1,
            "processDefinitionVersionTag": "v1",
            "tenantId": "<default>",
            "creationDate": "2024-01-01T10:00:00.000Z",
            "lastUpdatedDate": "2024-01-01T10:05:00.000Z",
            "completionDate": "2024-01-01T10:05:00.000Z",
            "elementInstanceKeys": ["%d"]
          }
        ],
        "page": {
          "totalItems": 1,
          "startCursor": "f",
          "endCursor": "v",
          "hasMoreTotalItems": false
        }
      }
      """
          .formatted(
              AGENT_INSTANCE_KEY,
              PROCESS_INSTANCE_KEY,
              ROOT_PROCESS_INSTANCE_KEY,
              PROCESS_DEFINITION_KEY,
              ELEMENT_INSTANCE_KEY);

  @MockitoBean private AgentInstanceServices agentInstanceServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUp() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldGetAgentInstance() {
    // given
    when(agentInstanceServices.getByKey(eq(AGENT_INSTANCE_KEY), any()))
        .thenReturn(AGENT_INSTANCE_ENTITY);

    // when / then
    webClient
        .get()
        .uri("%s/%d".formatted(AGENT_INSTANCES_URL, AGENT_INSTANCE_KEY))
        .accept(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(
            """
            {
              "agentInstanceKey": "%d",
              "status": "COMPLETED",
              "definition": {
                "model": "gpt-4o",
                "provider": "openai",
                "systemPrompt": "You are a helpful assistant."
              },
              "metrics": {
                "inputTokens": 100,
                "outputTokens": 200,
                "modelCalls": 3,
                "toolCalls": 5
              },
              "limits": {
                "maxModelCalls": 10,
                "maxTokens": 10000,
                "maxToolCalls": 50
              },
              "tools": [
                {
                  "name": "search",
                  "description": "Search the web",
                  "elementId": "searchElement"
                }
              ],
              "elementId": "AgentTask",
              "processInstanceKey": "%d",
              "rootProcessInstanceKey": "%d",
              "processDefinitionKey": "%d",
              "processDefinitionId": "myProcessId",
              "processDefinitionVersion": 1,
              "processDefinitionVersionTag": "v1",
              "tenantId": "<default>",
              "creationDate": "2024-01-01T10:00:00.000Z",
              "lastUpdatedDate": "2024-01-01T10:05:00.000Z",
              "completionDate": "2024-01-01T10:05:00.000Z",
              "elementInstanceKeys": ["%d"]
            }
            """
                .formatted(
                    AGENT_INSTANCE_KEY,
                    PROCESS_INSTANCE_KEY,
                    ROOT_PROCESS_INSTANCE_KEY,
                    PROCESS_DEFINITION_KEY,
                    ELEMENT_INSTANCE_KEY),
            JsonCompareMode.STRICT);

    verify(agentInstanceServices).getByKey(eq(AGENT_INSTANCE_KEY), any());
  }

  @Test
  void shouldReturnNotFoundForUnknownAgentInstanceKey() {
    // given
    final var path = "%s/%d".formatted(AGENT_INSTANCES_URL, AGENT_INSTANCE_KEY);
    when(agentInstanceServices.getByKey(eq(AGENT_INSTANCE_KEY), any()))
        .thenThrow(
            ErrorMapper.mapSearchError(
                new CamundaSearchException(
                    "agent instance not found", CamundaSearchException.Reason.NOT_FOUND)));

    // when / then
    webClient
        .get()
        .uri(path)
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
              "detail": "agent instance not found",
              "instance": "%s"
            }"""
                .formatted(path),
            JsonCompareMode.STRICT);
  }

  @Test
  void shouldSearchAgentInstancesWithEmptyBody() {
    // given
    when(agentInstanceServices.search(any(AgentInstanceQuery.class), any()))
        .thenReturn(
            new SearchQueryResult.Builder<AgentInstanceEntity>()
                .total(1)
                .startCursor("f")
                .endCursor("v")
                .items(List.of(AGENT_INSTANCE_ENTITY))
                .build());

    // when / then
    webClient
        .post()
        .uri(AGENT_INSTANCES_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(agentInstanceServices).search(eq(new AgentInstanceQuery.Builder().build()), any());
  }

  @Test
  void shouldSearchAgentInstancesWithStatusFilter() {
    // given
    when(agentInstanceServices.search(any(AgentInstanceQuery.class), any()))
        .thenReturn(
            new SearchQueryResult.Builder<AgentInstanceEntity>()
                .total(1)
                .startCursor("f")
                .endCursor("v")
                .items(List.of(AGENT_INSTANCE_ENTITY))
                .build());

    // when / then
    webClient
        .post()
        .uri(AGENT_INSTANCES_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "filter": {
                "status": { "$eq": "COMPLETED" }
              }
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(agentInstanceServices)
        .search(
            eq(
                new AgentInstanceQuery.Builder()
                    .filter(
                        new AgentInstanceFilter.Builder()
                            .statusOperations(List.of(Operation.eq("COMPLETED")))
                            .build())
                    .build()),
            any());
  }
}
