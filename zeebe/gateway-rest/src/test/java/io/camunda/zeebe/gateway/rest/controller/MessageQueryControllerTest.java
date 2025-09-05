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

import io.camunda.search.entities.CorrelatedMessageEntity;
import io.camunda.search.query.CorrelatedMessageQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.MessageServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = MessageController.class)
public class MessageQueryControllerTest extends RestControllerTest {

  private static final String EXPECTED_SEARCH_RESPONSE =
      """
        {
            "items": [
                {
                  "correlationKey": "test",
                  "correlationTime": "2025-07-05T12:11:00.975Z",
                  "flowNodeId": "Activity_1ludhs2",
                  "flowNodeInstanceKey": "2251799813685853",
                  "messageKey": "2251799813685854",
                  "messageName": "Message_1f8cu1e",
                  "processDefinitionId": "gg_msg_receive_id",
                  "processDefinitionKey": "2251799813685848",
                  "processInstanceKey": "2251799813685849",
                  "subscriptionKey": "2251799813685860",
                  "tenantId": "test-tenant"
                }
            ],
            "page": {
                "totalItems": 1,
                "startCursor": "WzIyNTE3OTk4MTM2ODU4Mjld",
                "endCursor": "WzIyNTE3OTk4MTM2ODU4NjZd",
                "hasMoreTotalItems": false
            }
        }
      """;
  private static final String CORRELATED_MESSAGES_SEARCH_URL = "/v2/correlated-messages/search";
  private static final SearchQueryResult<CorrelatedMessageEntity> SEARCH_QUERY_RESULT =
      new SearchQueryResult.Builder<CorrelatedMessageEntity>()
          .total(1L)
          .items(
              List.of(
                  new CorrelatedMessageEntity.Builder()
                      .correlationKey("test")
                      .correlationTime(OffsetDateTime.parse("2025-07-05T12:11:00.975Z"))
                      .flowNodeId("Activity_1ludhs2")
                      .flowNodeInstanceKey(2251799813685853L)
                      .messageKey(2251799813685854L)
                      .messageName("Message_1f8cu1e")
                      .processDefinitionId("gg_msg_receive_id")
                      .processDefinitionKey(2251799813685848L)
                      .processInstanceKey(2251799813685849L)
                      .subscriptionKey(2251799813685860L)
                      .tenantId("test-tenant")
                      .build()))
          .startCursor("WzIyNTE3OTk4MTM2ODU4Mjld")
          .endCursor("WzIyNTE3OTk4MTM2ODU4NjZd")
          .build();

  @MockitoBean MessageServices services;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUp() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(services.withAuthentication(any(CamundaAuthentication.class))).thenReturn(services);
  }

  @Test
  public void shouldSearchCorrelatedMessagesWithEmptyBody() {
    // given
    when(services.searchCorrelatedMessages(any())).thenReturn(SEARCH_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(CORRELATED_MESSAGES_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(services).searchCorrelatedMessages(new CorrelatedMessageQuery.Builder().build());
  }

  @Test
  public void shouldSearchCorrelatedMessagesWithEmptyQuery() {
    // given
    when(services.searchCorrelatedMessages(any())).thenReturn(SEARCH_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(CORRELATED_MESSAGES_SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(services).searchCorrelatedMessages(new CorrelatedMessageQuery.Builder().build());
  }

  @Test
  public void shouldSearchCorrelatedMessagesWithFilters() {
    // given
    when(services.searchCorrelatedMessages(any())).thenReturn(SEARCH_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(CORRELATED_MESSAGES_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "filter": {
                "correlationKey": "test",
                "messageName": "test-message",
                "tenantId": "test-tenant"
              }
            }""")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(services)
        .searchCorrelatedMessages(
            new CorrelatedMessageQuery.Builder()
                .filter(
                    f ->
                        f.correlationKeys("test")
                            .messageNames("test-message")
                            .tenantIds("test-tenant"))
                .build());
  }

  @Test
  public void shouldSearchCorrelatedMessagesWithSorting() {
    // given
    when(services.searchCorrelatedMessages(any())).thenReturn(SEARCH_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(CORRELATED_MESSAGES_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "sort": [
                {
                  "field": "correlationKey",
                  "order": "asc"
                },
                {
                  "field": "messageName",
                  "order": "desc"
                },
                {
                  "field": "tenantId",
                  "order": "asc"
                }
              ]
            }""")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(services)
        .searchCorrelatedMessages(
            new CorrelatedMessageQuery.Builder()
                .sort(s -> s.correlationKey().asc().messageName().desc().tenantId().asc())
                .build());
  }
}
