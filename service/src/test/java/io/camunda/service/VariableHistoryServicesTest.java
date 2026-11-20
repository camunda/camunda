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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.VariableHistorySearchClient;
import io.camunda.search.entities.VariableHistoryItemEntity;
import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemActorType;
import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemOperationType;
import io.camunda.search.entities.VariableHistoryItemEntity.VariableHistoryItemVariableOperationType;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableHistoryItemQuery;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.core.auth.SecurityContext;
import io.camunda.service.authorization.Authorizations;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class VariableHistoryServicesTest {

  private static final String PHYSICAL_TENANT_ID = "test-tenant";
  private static final VariableHistoryItemEntity ENTITY =
      new VariableHistoryItemEntity(
          1L,
          2L,
          "orderTotal",
          "42",
          null,
          false,
          3L,
          null,
          null,
          5L,
          null,
          7L,
          "process",
          "<default>",
          OffsetDateTime.parse("2024-01-01T00:00:00Z"),
          VariableHistoryItemOperationType.CREATE,
          VariableHistoryItemVariableOperationType.API,
          VariableHistoryItemActorType.USER,
          null,
          null);
  private static final VariableHistoryItemQuery QUERY = VariableHistoryItemQuery.of(q -> q);

  @Mock private BrokerClient brokerClient;
  @Mock private SecurityContextProvider securityContextProvider;
  @Mock private VariableHistorySearchClient variableHistorySearchClient;
  @Mock private CamundaAuthentication authentication;
  @Mock private ApiServicesExecutorProvider executorProvider;
  @Mock private BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;

  private VariableHistoryServices variableHistoryServices;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);

    when(variableHistorySearchClient.withSecurityContext(any()))
        .thenReturn(variableHistorySearchClient);

    variableHistoryServices =
        new VariableHistoryServices(
            PHYSICAL_TENANT_ID,
            brokerClient,
            securityContextProvider,
            variableHistorySearchClient,
            executorProvider,
            brokerRequestAuthorizationConverter);
  }

  @Test
  void shouldDelegateSearchToClient() {
    // given
    final var searchResult = SearchQueryResult.of(ENTITY);
    when(variableHistorySearchClient.searchVariableHistoryItems(QUERY)).thenReturn(searchResult);

    // when
    final var result = variableHistoryServices.search(QUERY, authentication);

    // then
    assertThat(result).isEqualTo(searchResult);
  }

  @Test
  void shouldRequireReadProcessInstanceOnProcessDefinition() {
    // given
    final var securityContext = SecurityContext.of(b -> b.withAuthentication(authentication));
    final var scopedClient = mock(VariableHistorySearchClient.class);
    final var searchResult = SearchQueryResult.of(ENTITY);
    when(securityContextProvider.provideSecurityContext(
            authentication, Authorizations.VARIABLE_HISTORY_READ_AUTHORIZATION))
        .thenReturn(securityContext);
    when(variableHistorySearchClient.withSecurityContext(securityContext)).thenReturn(scopedClient);
    when(scopedClient.searchVariableHistoryItems(QUERY)).thenReturn(searchResult);

    // when
    final var result = variableHistoryServices.search(QUERY, authentication);

    // then
    assertThat(result).isEqualTo(searchResult);
    verify(variableHistorySearchClient, never()).searchVariableHistoryItems(any());
  }

  @Test
  void shouldThrowForbiddenExceptionIfNotAuthorized() {
    // given
    when(variableHistorySearchClient.searchVariableHistoryItems(QUERY))
        .thenThrow(
            new ResourceAccessDeniedException(Authorizations.VARIABLE_HISTORY_READ_AUTHORIZATION));

    // when / then
    final var exception =
        (ServiceException)
            assertThatThrownBy(() -> variableHistoryServices.search(QUERY, authentication))
                .isInstanceOf(ServiceException.class)
                .actual();
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource"
                + " 'PROCESS_DEFINITION'");
    assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
  }
}
