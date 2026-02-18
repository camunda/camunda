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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.GlobalListenerSearchClient;
import io.camunda.search.entities.GlobalListenerEntity;
import io.camunda.search.entities.GlobalListenerSource;
import io.camunda.search.entities.GlobalListenerType;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.query.GlobalListenerQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.authorization.Authorizations;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.protocol.impl.record.value.globallistener.GlobalListenerRecord;
import io.camunda.zeebe.protocol.record.value.GlobalListenerSource.API;
import java.util.List;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class GlobalListenerServicesTest {

  private static final GlobalListenerEntity GLOBAL_LISTENER_ENTITY =
      new GlobalListenerEntity(
          "global-listener-1",
          "listener-id-123",
          "my.listener.Type",
          List.of("creating", "created"),
          3,
          true,
          10,
          GlobalListenerSource.API,
          GlobalListenerType.TASK_LISTENER);

  @Mock private BrokerClient brokerClient;
  @Mock private SecurityContextProvider securityContextProvider;
  @Mock private GlobalListenerSearchClient globalListenerSearchClient;
  @Mock private CamundaAuthentication authentication;
  @Mock private ApiServicesExecutorProvider executorProvider;
  @Mock private BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;

  @Mock private GlobalListenerQuery query;

  private GlobalListenerServices globalListenerServices;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    when(globalListenerSearchClient.withSecurityContext(any()))
        .thenReturn(globalListenerSearchClient);

    globalListenerServices =
        new GlobalListenerServices(
            brokerClient,
            securityContextProvider,
            globalListenerSearchClient,
            authentication,
            executorProvider,
            brokerRequestAuthorizationConverter);
  }

  @Test
  void shouldDelegateSearchToClient() {
    // given
    final var searchResult = SearchQueryResult.of(GLOBAL_LISTENER_ENTITY);

    when(globalListenerSearchClient.searchGlobalListeners(query)).thenReturn(searchResult);
    when(query.filter()).thenReturn(FilterBuilders.globalListener().build());

    // when
    final var result = globalListenerServices.search(query);

    // then
    assertThat(result).isEqualTo(searchResult);

    final var queryCaptor = ArgumentCaptor.forClass(GlobalListenerQuery.class);
    verify(globalListenerSearchClient).searchGlobalListeners(queryCaptor.capture());
    assertThat(queryCaptor.getValue()).isEqualTo(query);
  }

  @Test
  void shouldDelegateGetGlobalTaskListenerToClient() {
    // given
    final var request = new GlobalListenerRecord();
    request.setId("listener-id-123");
    request.setListenerType(
        io.camunda.zeebe.protocol.record.value.GlobalListenerType.TASK_LISTENER);

    when(globalListenerSearchClient.getGlobalListener(
            "listener-id-123", GlobalListenerType.TASK_LISTENER))
        .thenReturn(GLOBAL_LISTENER_ENTITY);

    // when
    final var result = globalListenerServices.getGlobalTaskListener(request);

    // then
    assertThat(result).isEqualTo(GLOBAL_LISTENER_ENTITY);
    verify(globalListenerSearchClient)
        .getGlobalListener("listener-id-123", GlobalListenerType.TASK_LISTENER);
  }

  @Test
  public void searchShouldThrowForbiddenExceptionIfNotAuthorized() {
    // given
    when(globalListenerSearchClient.searchGlobalListeners(query))
        .thenThrow(
            new ResourceAccessDeniedException(
                Authorizations.GLOBAL_TASK_LISTENER_READ_AUTHORIZATION));
    when(query.filter()).thenReturn(FilterBuilders.globalListener().build());

    // when
    final ThrowingCallable executeSearch = () -> globalListenerServices.search(query);

    // then
    final var exception =
        (ServiceException)
            assertThatThrownBy(executeSearch).isInstanceOf(ServiceException.class).actual();
    assertThat(exception.getMessage())
        .isEqualTo("Unauthorized to perform operation 'READ' on resource 'GLOBAL_LISTENER'");
    assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
  }

  @Test
  public void getGlobalTaskListenerShouldThrowForbiddenExceptionIfNotAuthorized() {
    // given
    final var request = new GlobalListenerRecord();
    request.setId("listener-id-123");
    request.setListenerType(
        io.camunda.zeebe.protocol.record.value.GlobalListenerType.TASK_LISTENER);

    when(globalListenerSearchClient.getGlobalListener(
            "listener-id-123", GlobalListenerType.TASK_LISTENER))
        .thenThrow(
            new ResourceAccessDeniedException(
                Authorizations.GLOBAL_TASK_LISTENER_READ_AUTHORIZATION));

    // when
    final ThrowingCallable executeGet = () -> globalListenerServices.getGlobalTaskListener(request);

    // then
    final var exception =
        (ServiceException)
            assertThatThrownBy(executeGet).isInstanceOf(ServiceException.class).actual();
    assertThat(exception.getMessage())
        .isEqualTo("Unauthorized to perform operation 'READ' on resource 'GLOBAL_LISTENER'");
    assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
  }
}
