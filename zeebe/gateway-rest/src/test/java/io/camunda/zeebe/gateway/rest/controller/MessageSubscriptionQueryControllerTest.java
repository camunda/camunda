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

import io.camunda.search.entities.CorrelatedMessageSubscriptionEntity;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.query.CorrelatedMessageSubscriptionQuery;
import io.camunda.search.query.MessageSubscriptionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.CamundaAuthenticationProvider;
import io.camunda.service.MessageSubscriptionServices;
import io.camunda.zeebe.gateway.rest.RestControllerTest;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.json.JsonCompareMode;

@WebMvcTest(value = MessageSubscriptionController.class)
public class MessageSubscriptionQueryControllerTest extends RestControllerTest {

  private static final String EXPECTED_SEARCH_RESPONSE =
      """
        {
            "items": [
                {
                  "messageSubscriptionKey": "2251799813685854",
                  "processDefinitionId": "gg_msg_receive_id",
                  "processInstanceKey": "2251799813685849",
                  "elementId": "Activity_1ludhs2",
                  "elementInstanceKey": "2251799813685853",
                  "messageSubscriptionState": "CREATED",
                  "lastUpdatedDate": "2025-07-05T12:11:00.975Z",
                  "messageName": "Message_1f8cu1e",
                  "correlationKey": "test",
                  "tenantId": "test-tenant",
                  "processDefinitionKey": null,
                  "rootProcessInstanceKey": null
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
  private static final String MESSAGE_SUBSCRIPTIONS_SEARCH_URL = "/v2/message-subscriptions/search";
  private static final SearchQueryResult<MessageSubscriptionEntity> SEARCH_QUERY_RESULT =
      new SearchQueryResult.Builder<MessageSubscriptionEntity>()
          .total(1L)
          .items(
              List.of(
                  new MessageSubscriptionEntity.Builder()
                      .messageSubscriptionKey(2251799813685854L)
                      .processDefinitionId("gg_msg_receive_id")
                      .processInstanceKey(2251799813685849L)
                      .flowNodeId("Activity_1ludhs2")
                      .flowNodeInstanceKey(2251799813685853L)
                      .messageSubscriptionState(
                          MessageSubscriptionEntity.MessageSubscriptionState.CREATED)
                      .dateTime(OffsetDateTime.parse("2025-07-05T12:11:00.975Z"))
                      .messageName("Message_1f8cu1e")
                      .correlationKey("test")
                      .tenantId("test-tenant")
                      .build()))
          .startCursor("WzIyNTE3OTk4MTM2ODU4Mjld")
          .endCursor("WzIyNTE3OTk4MTM2ODU4NjZd")
          .build();

  private static final String EXPECTED_CORRELATED_SEARCH_RESPONSE =
      """
        {
            "items": [
                {
                  "correlationKey": "test",
                  "correlationTime": "2025-07-05T12:11:00.975Z",
                  "elementId": "Activity_1ludhs2",
                  "elementInstanceKey": "2251799813685853",
                  "messageKey": "2251799813685854",
                  "messageName": "Message_1f8cu1e",
                  "partitionId": 3,
                  "processDefinitionId": "gg_msg_receive_id",
                  "processDefinitionKey": "2251799813685848",
                  "processInstanceKey": "2251799813685849",
                  "subscriptionKey": "2251799813685860",
                  "tenantId": "test-tenant",
                  "rootProcessInstanceKey": null
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
  private static final String CORRELATED_MESSAGE_SUBSCRIPTIONS_SEARCH_URL =
      "/v2/correlated-message-subscriptions/search";
  private static final SearchQueryResult<CorrelatedMessageSubscriptionEntity>
      SEARCH_CORRELATED_QUERY_RESULT =
          new SearchQueryResult.Builder<CorrelatedMessageSubscriptionEntity>()
              .total(1L)
              .items(
                  List.of(
                      new CorrelatedMessageSubscriptionEntity.Builder()
                          .correlationKey("test")
                          .correlationTime(OffsetDateTime.parse("2025-07-05T12:11:00.975Z"))
                          .flowNodeId("Activity_1ludhs2")
                          .flowNodeInstanceKey(2251799813685853L)
                          .messageKey(2251799813685854L)
                          .messageName("Message_1f8cu1e")
                          .partitionId(3)
                          .processDefinitionId("gg_msg_receive_id")
                          .processDefinitionKey(2251799813685848L)
                          .processInstanceKey(2251799813685849L)
                          .subscriptionKey(2251799813685860L)
                          .tenantId("test-tenant")
                          .build()))
              .startCursor("WzIyNTE3OTk4MTM2ODU4Mjld")
              .endCursor("WzIyNTE3OTk4MTM2ODU4NjZd")
              .build();

  @MockitoBean MessageSubscriptionServices services;
  @MockitoBean CamundaAuthenticationProvider authenticationProvider;

  @BeforeEach
  void setUp() {
    when(authenticationProvider.getCamundaAuthentication())
        .thenReturn(AUTHENTICATION_WITH_DEFAULT_TENANT);
    when(services.withAuthentication(any(CamundaAuthentication.class))).thenReturn(services);
  }

  @Test
  public void shouldSearchMessageSubscriptionsWithEmptyBody() {
    // given
    when(services.search(any())).thenReturn(SEARCH_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(MESSAGE_SUBSCRIPTIONS_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(services).search(new MessageSubscriptionQuery.Builder().build());
  }

  @Test
  public void shouldSearchMessageSubscriptionsWithEmptyQuery() {
    // given
    when(services.search(any())).thenReturn(SEARCH_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(MESSAGE_SUBSCRIPTIONS_SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(services).search(new MessageSubscriptionQuery.Builder().build());
  }

  @Test
  public void shouldSearchMessageSubscriptionsWithFilters() {
    // given
    when(services.search(any())).thenReturn(SEARCH_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(MESSAGE_SUBSCRIPTIONS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "filter": {
                "messageSubscriptionKey": "123",
                "processDefinitionId": "gg_msg_receive_id",
                "processInstanceKey": "2251799813685849",
                "elementId": "Activity_1ludhs2",
                "elementInstanceKey": "2251799813685853",
                "messageSubscriptionState": "CREATED",
                "lastUpdatedDate": "2025-07-05T12:11:00.975Z",
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
        .search(
            new MessageSubscriptionQuery.Builder()
                .filter(
                    f ->
                        f.messageSubscriptionKeys(123L)
                            .processDefinitionIds("gg_msg_receive_id")
                            .processInstanceKeys(2251799813685849L)
                            .flowNodeIds("Activity_1ludhs2")
                            .flowNodeInstanceKeys(2251799813685853L)
                            .messageSubscriptionStates(
                                MessageSubscriptionEntity.MessageSubscriptionState.CREATED.name())
                            .dateTimes(OffsetDateTime.parse("2025-07-05T12:11:00.975Z"))
                            .correlationKeys("test")
                            .messageNames("test-message")
                            .tenantIds("test-tenant"))
                .build());
  }

  @Test
  public void shouldSearchMessageSubscriptionsWithSorting() {
    // given
    when(services.search(any())).thenReturn(SEARCH_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(MESSAGE_SUBSCRIPTIONS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "sort": [
                {
                  "field": "messageSubscriptionKey",
                  "order": "asc"
                },
                {
                  "field": "processDefinitionId",
                  "order": "desc"
                },
                {
                  "field": "processInstanceKey",
                  "order": "desc"
                },
                {
                  "field": "elementId",
                  "order": "asc"
                },
                {
                  "field": "elementInstanceKey",
                  "order": "desc"
                },
                {
                  "field": "messageSubscriptionState",
                  "order": "asc"
                },
                {
                  "field": "lastUpdatedDate",
                  "order": "desc"
                },
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
        .search(
            new MessageSubscriptionQuery.Builder()
                .sort(
                    s ->
                        s.messageSubscriptionKey()
                            .asc()
                            .processDefinitionId()
                            .desc()
                            .processInstanceKey()
                            .desc()
                            .flowNodeId()
                            .asc()
                            .flowNodeInstanceKey()
                            .desc()
                            .messageSubscriptionState()
                            .asc()
                            .dateTime()
                            .desc()
                            .correlationKey()
                            .asc()
                            .messageName()
                            .desc()
                            .tenantId()
                            .asc())
                .build());
  }

  @Test
  public void shouldSearchCorrelatedWithEmptyBody() {
    // given
    when(services.searchCorrelated(any())).thenReturn(SEARCH_CORRELATED_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(CORRELATED_MESSAGE_SUBSCRIPTIONS_SEARCH_URL)
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_CORRELATED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(services).searchCorrelated(new CorrelatedMessageSubscriptionQuery.Builder().build());
  }

  @Test
  public void shouldSearchCorrelatedWithEmptyQuery() {
    // given
    when(services.searchCorrelated(any())).thenReturn(SEARCH_CORRELATED_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(CORRELATED_MESSAGE_SUBSCRIPTIONS_SEARCH_URL)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("{}")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_CORRELATED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(services).searchCorrelated(new CorrelatedMessageSubscriptionQuery.Builder().build());
  }

  @Test
  public void shouldSearchCorrelatedWithFilters() {
    // given
    when(services.searchCorrelated(any())).thenReturn(SEARCH_CORRELATED_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(CORRELATED_MESSAGE_SUBSCRIPTIONS_SEARCH_URL)
        .accept(MediaType.APPLICATION_JSON)
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(
            """
            {
              "filter": {
                "correlationKey": "test",
                  "correlationTime": "2025-07-05T12:11:00.975Z",
                  "elementId": "Activity_1ludhs2",
                  "elementInstanceKey": "2251799813685853",
                  "messageKey": "2251799813685854",
                  "messageName": "Message_1f8cu1e",
                  "partitionId": 3,
                  "processDefinitionId": "gg_msg_receive_id",
                  "processDefinitionKey": "2251799813685848",
                  "processInstanceKey": "2251799813685849",
                  "subscriptionKey": "2251799813685860",
                  "tenantId": "test-tenant"
              }
            }""")
        .exchange()
        .expectStatus()
        .isOk()
        .expectHeader()
        .contentType(MediaType.APPLICATION_JSON)
        .expectBody()
        .json(EXPECTED_CORRELATED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(services)
        .searchCorrelated(
            new CorrelatedMessageSubscriptionQuery.Builder()
                .filter(
                    f ->
                        f.correlationKeys("test")
                            .correlationTimes(OffsetDateTime.parse("2025-07-05T12:11:00.975Z"))
                            .flowNodeIds("Activity_1ludhs2")
                            .flowNodeInstanceKeys(2251799813685853L)
                            .messageKeys(2251799813685854L)
                            .messageNames("Message_1f8cu1e")
                            .partitionIds(3)
                            .processDefinitionIds("gg_msg_receive_id")
                            .processDefinitionKeys(2251799813685848L)
                            .processInstanceKeys(2251799813685849L)
                            .subscriptionKeys(2251799813685860L)
                            .tenantIds("test-tenant"))
                .build());
  }

  @Test
  public void shouldSearchCorrelatedWithSorting() {
    // given
    when(services.searchCorrelated(any())).thenReturn(SEARCH_CORRELATED_QUERY_RESULT);

    // when / then
    webClient
        .post()
        .uri(CORRELATED_MESSAGE_SUBSCRIPTIONS_SEARCH_URL)
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
                  "field": "correlationTime",
                  "order": "desc"
                },
                {
                  "field": "elementId",
                  "order": "asc"
                },
                {
                  "field": "elementInstanceKey",
                  "order": "desc"
                },
                {
                  "field": "messageKey",
                  "order": "asc"
                },
                {
                  "field": "messageName",
                  "order": "desc"
                },
                {
                  "field": "partitionId",
                  "order": "asc"
                },
                {
                  "field": "processDefinitionId",
                  "order": "desc"
                },
                {
                  "field": "processDefinitionKey",
                  "order": "asc"
                },
                {
                  "field": "processInstanceKey",
                  "order": "desc"
                },
                {
                  "field": "subscriptionKey",
                  "order": "asc"
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
        .json(EXPECTED_CORRELATED_SEARCH_RESPONSE, JsonCompareMode.STRICT);

    verify(services)
        .searchCorrelated(
            new CorrelatedMessageSubscriptionQuery.Builder()
                .sort(
                    s ->
                        s.correlationKey()
                            .asc()
                            .correlationTime()
                            .desc()
                            .flowNodeId()
                            .asc()
                            .flowNodeInstanceKey()
                            .desc()
                            .messageKey()
                            .asc()
                            .messageName()
                            .desc()
                            .partitionId()
                            .asc()
                            .processDefinitionId()
                            .desc()
                            .processDefinitionKey()
                            .asc()
                            .processInstanceKey()
                            .desc()
                            .subscriptionKey()
                            .asc()
                            .tenantId()
                            .asc())
                .build());
  }
}
