/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.MessageSubscriptionSearchClient;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageSubscriptionServiceTest {

  private MessageSubscriptionServices services;
  private MessageSubscriptionSearchClient client;

  @BeforeEach
  public void before() {
    client = mock(MessageSubscriptionSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    services =
        new MessageSubscriptionServices(
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            client,
            null,
            mock(ApiServicesExecutorProvider.class));
  }

  @Test
  void shouldReturnMessageSubscriptions() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchMessageSubscriptions(any())).thenReturn(result);

    final var searchQuery = SearchQueryBuilders.messageSubscriptionSearchQuery().build();

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  void shouldReturnAuthenticatedInstance() {
    // given
    final var authentication = mock(CamundaAuthentication.class);

    // when
    final var authenticatedServices = services.withAuthentication(authentication);

    // then
    assertThat(authenticatedServices).isNotNull();
    assertThat(authenticatedServices).isInstanceOf(MessageSubscriptionServices.class);
  }
}
