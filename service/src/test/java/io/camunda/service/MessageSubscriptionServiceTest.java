/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.MessageSubscriptionSearchClient;
import io.camunda.search.entities.MessageSubscriptionEntity;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.service.authorization.Authorizations;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MessageSubscriptionServiceTest {

  private MessageSubscriptionServices services;
  private MessageSubscriptionSearchClient client;
  private CamundaAuthentication authentication;

  @BeforeEach
  public void before() {
    client = mock(MessageSubscriptionSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    when(client.withPhysicalTenant(any())).thenReturn(client);
    authentication = mock(CamundaAuthentication.class);
    services =
        new MessageSubscriptionServices(
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            client,
            mock(ApiServicesExecutorProvider.class),
            null);
  }

  @Test
  void shouldReturnMessageSubscriptions() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchMessageSubscriptions(any())).thenReturn(result);

    final var searchQuery = SearchQueryBuilders.messageSubscriptionSearchQuery().build();

    // when
    final var searchQueryResult = services.search(searchQuery, authentication, "default");

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  void shouldReturnMessageSubscriptionByKey() {
    // given
    final var entity = Instancio.create(MessageSubscriptionEntity.class);
    when(client.getMessageSubscription(any(Long.class))).thenReturn(entity);

    // when
    final var result = services.getByKey(1L, authentication, "default");

    // then
    assertThat(result).isEqualTo(entity);
  }

  @Test
  void getByKeyShouldThrowForbiddenExceptionIfNotAuthorized() {
    // given
    when(client.getMessageSubscription(any(Long.class)))
        .thenThrow(
            new ResourceAccessDeniedException(
                Authorizations.MESSAGE_SUBSCRIPTION_READ_AUTHORIZATION));

    // when
    final ThrowingCallable executeGetByKey = () -> services.getByKey(1L, authentication, "default");

    // then
    final var exception =
        (ServiceException)
            assertThatThrownBy(executeGetByKey).isInstanceOf(ServiceException.class).actual();
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
    assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
  }
}
