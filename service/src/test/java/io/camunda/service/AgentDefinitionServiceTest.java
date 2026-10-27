/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.AgentDefinitionSearchClient;
import io.camunda.search.entities.AgentDefinitionEntity;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.query.AgentDefinitionQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.service.authorization.Authorizations;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class AgentDefinitionServiceTest {

  private static final String PHYSICAL_TENANT_ID = "test-tenant";
  private CamundaAuthentication authentication;
  private AgentDefinitionServices services;
  private AgentDefinitionSearchClient client;

  @BeforeEach
  public void before() {
    authentication = mock(CamundaAuthentication.class);
    client = mock(AgentDefinitionSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    services =
        new AgentDefinitionServices(
            PHYSICAL_TENANT_ID,
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            client,
            mock(ApiServicesExecutorProvider.class),
            null);
  }

  @Test
  public void shouldReturnAgentDefinitions() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchAgentDefinitions(any())).thenReturn(result);

    final AgentDefinitionQuery searchQuery =
        SearchQueryBuilders.agentDefinitionSearchQuery().build();

    // when
    final SearchQueryResult<AgentDefinitionEntity> searchQueryResult =
        services.search(searchQuery, authentication);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldGetAgentDefinitionByKey() {
    // given
    final var definitionEntity = mock(AgentDefinitionEntity.class);
    when(definitionEntity.agentDefinitionKey()).thenReturn(42L);
    when(definitionEntity.processDefinitionId()).thenReturn("processId");
    when(client.getAgentDefinition(eq(42L))).thenReturn(definitionEntity);

    // when
    final AgentDefinitionEntity agentDefinition = services.getByKey(42L, authentication);

    // then
    assertThat(agentDefinition.agentDefinitionKey()).isEqualTo(42L);
  }

  @Test
  void shouldGetByKeyThrowForbiddenExceptionOnUnauthorizedAgentDefinitionKey() {
    // given
    when(client.getAgentDefinition(any(Long.class)))
        .thenThrow(
            new ResourceAccessDeniedException(Authorizations.AGENT_DEFINITION_READ_AUTHORIZATION));

    // when
    final ThrowingCallable executable = () -> services.getByKey(1L, authentication);

    // then
    final var exception =
        assertThatExceptionOfType(ServiceException.class).isThrownBy(executable).actual();
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_DEFINITION' on resource 'PROCESS_DEFINITION'");
  }
}
