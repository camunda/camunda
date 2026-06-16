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

import io.camunda.search.entities.AgentInstanceHistoryEntity;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryCommitStatus;
import io.camunda.search.entities.AgentInstanceHistoryEntity.AgentInstanceHistoryRole;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem;
import io.camunda.search.entities.AgentInstanceHistoryEntity.ContentItem.ContentType;
import io.camunda.search.entities.AgentInstanceHistoryEntity.Metrics;
import io.camunda.search.filter.AgentInstanceHistoryFilter;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.AgentInstanceHistoryQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.context.CamundaAuthenticationProvider;
import io.camunda.service.AgentHistoryServices;
import io.camunda.service.AgentInstanceServices;
import io.camunda.service.registry.ServiceRegistry;
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
class AgentInstanceHistorySearchControllerTest extends RestControllerTest {

  private static final String AGENT_INSTANCES_URL = "/v2/agent-instances";
  private static final long AGENT_INSTANCE_KEY = 9007199254741017L;
  private static final long ELEMENT_INSTANCE_KEY = 2251799813685248L;
  private static final long JOB_KEY = 2251799813685249L;
  private static final long HISTORY_ITEM_KEY = 9007199254741018L;
  private static final OffsetDateTime PRODUCED_AT =
      OffsetDateTime.parse("2025-01-01T10:00:00+00:00");

  private static final AgentInstanceHistoryEntity HISTORY_ENTITY =
      new AgentInstanceHistoryEntity(
          HISTORY_ITEM_KEY,
          AGENT_INSTANCE_KEY,
          ELEMENT_INSTANCE_KEY,
          9007199254741001L,
          9007199254740992L,
          "myProcess",
          "<default>",
          JOB_KEY,
          "job-lease-1",
          1,
          AgentInstanceHistoryRole.USER,
          List.of(new ContentItem(ContentType.TEXT, "Hello agent", null, null)),
          List.of(),
          new Metrics(10L, 20L, 100L),
          AgentInstanceHistoryCommitStatus.COMMITTED,
          PRODUCED_AT);

  private static final String HISTORY_SEARCH_URL =
      AGENT_INSTANCES_URL + "/" + AGENT_INSTANCE_KEY + "/history/search";

  private static final String EXPECTED_SEARCH_RESPONSE =
      """
      {
        "items": [
          {
            "historyItemKey": "%d",
            "agentInstanceKey": "%d",
            "elementInstanceKey": "%d",
            "jobKey": "%d",
            "jobLease": "job-lease-1",
            "iteration": 1,
            "role": "USER",
            "content": [{ "contentType": "TEXT", "text": "Hello agent" }],
            "toolCalls": [],
            "metrics": { "inputTokens": 10, "outputTokens": 20, "durationMs": 100 },
            "commitStatus": "COMMITTED",
            "producedAt": "2025-01-01T10:00:00.000Z"
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
          .formatted(HISTORY_ITEM_KEY, AGENT_INSTANCE_KEY, ELEMENT_INSTANCE_KEY, JOB_KEY);

  @MockitoBean private AgentInstanceServices agentInstanceServices;
  @MockitoBean private AgentHistoryServices agentHistoryServices;
  @MockitoBean private CamundaAuthenticationProvider authenticationProvider;
  @MockitoBean private ServiceRegistry serviceRegistry;

  @BeforeEach
  void setUp() {
    when(serviceRegistry.agentInstanceServices(any())).thenReturn(agentInstanceServices);
    when(serviceRegistry.agentHistoryServices(any())).thenReturn(agentHistoryServices);
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
  }

  @Test
  void shouldSearchAgentHistoryWithEmptyBody() {
    // given
    when(agentHistoryServices.search(any(AgentInstanceHistoryQuery.class), any()))
        .thenReturn(
            new SearchQueryResult.Builder<AgentInstanceHistoryEntity>()
                .total(1)
                .startCursor("f")
                .endCursor("v")
                .items(List.of(HISTORY_ENTITY))
                .build());

    // when / then
    webClient
        .post()
        .uri(HISTORY_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.LENIENT);

    // verify that agentInstanceKey is injected and COMMITTED is applied as default
    verify(agentHistoryServices)
        .search(
            eq(
                new AgentInstanceHistoryQuery.Builder()
                    .filter(
                        new AgentInstanceHistoryFilter.Builder()
                            .agentInstanceKeyOperations(List.of(Operation.eq(AGENT_INSTANCE_KEY)))
                            .commitStatusOperations(List.of(Operation.eq("COMMITTED")))
                            .build())
                    .build()),
            any());
  }

  @Test
  void shouldSearchAgentHistoryWithRoleFilter() {
    // given
    when(agentHistoryServices.search(any(AgentInstanceHistoryQuery.class), any()))
        .thenReturn(
            new SearchQueryResult.Builder<AgentInstanceHistoryEntity>()
                .total(1)
                .startCursor("f")
                .endCursor("v")
                .items(List.of(HISTORY_ENTITY))
                .build());

    // when / then
    webClient
        .post()
        .uri(HISTORY_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "filter": {
                "role": { "$eq": "USER" }
              }
            }
            """)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.LENIENT);

    verify(agentHistoryServices)
        .search(
            eq(
                new AgentInstanceHistoryQuery.Builder()
                    .filter(
                        new AgentInstanceHistoryFilter.Builder()
                            .agentInstanceKeyOperations(List.of(Operation.eq(AGENT_INSTANCE_KEY)))
                            .commitStatusOperations(List.of(Operation.eq("COMMITTED")))
                            .roleOperations(List.of(Operation.eq("USER")))
                            .build())
                    .build()),
            any());
  }

  @Test
  void shouldSearchAgentHistoryWithExplicitCommitStatusFilter() {
    // given
    when(agentHistoryServices.search(any(AgentInstanceHistoryQuery.class), any()))
        .thenReturn(
            new SearchQueryResult.Builder<AgentInstanceHistoryEntity>()
                .total(1)
                .startCursor("f")
                .endCursor("v")
                .items(List.of(HISTORY_ENTITY))
                .build());

    // when / then
    webClient
        .post()
        .uri(HISTORY_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "filter": {
                "commitStatus": { "$eq": "PENDING" }
              }
            }
            """)
        .exchange()
        .expectStatus()
        .isOk();

    // verify that an explicit commitStatus overrides the COMMITTED default
    verify(agentHistoryServices)
        .search(
            eq(
                new AgentInstanceHistoryQuery.Builder()
                    .filter(
                        new AgentInstanceHistoryFilter.Builder()
                            .agentInstanceKeyOperations(List.of(Operation.eq(AGENT_INSTANCE_KEY)))
                            .commitStatusOperations(List.of(Operation.eq("PENDING")))
                            .build())
                    .build()),
            any());
  }
}
