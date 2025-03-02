/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public final class ProcessInstanceServiceTest {

  private ProcessInstanceServices services;
  private ProcessInstanceSearchClient client;
  private SecurityContextProvider securityContextProvider;
  private Authentication authentication;

  @BeforeEach
  public void before() {
    client = mock(ProcessInstanceSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    securityContextProvider = mock(SecurityContextProvider.class);
    services =
        new ProcessInstanceServices(
            mock(BrokerClient.class), securityContextProvider, client, authentication);
  }

  @Test
  public void shouldReturnProcessInstance() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchProcessInstances(any())).thenReturn(result);

    final ProcessInstanceQuery searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery().build();

    // when
    final SearchQueryResult<ProcessInstanceEntity> searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnProcessInstanceByKey() {
    // given
    final var key = 123L;
    final var entity = mock(ProcessInstanceEntity.class);
    when(entity.processInstanceKey()).thenReturn(key);
    when(entity.processDefinitionId()).thenReturn("processId");
    when(client.searchProcessInstances(any()))
        .thenReturn(new SearchQueryResult(1, List.of(entity), null, null));
    authorizeProcessReadInstance(true, "processId");

    // when
    final var searchQueryResult = services.getByKey(key);

    // then
    assertThat(searchQueryResult.processInstanceKey()).isEqualTo(key);
  }

  @Test
  public void shouldThrownExceptionIfNotFoundByKey() {
    // given
    final var key = 100L;
    when(client.searchProcessInstances(any()))
        .thenReturn(new SearchQueryResult<>(0, List.of(), null, null));

    // when / then
    final var exception =
        assertThrowsExactly(CamundaSearchException.class, () -> services.getByKey(key));
    assertThat(exception.getMessage()).isEqualTo("Process instance with key 100 not found");
    assertThat(exception.getReason()).isEqualTo(CamundaSearchException.Reason.NOT_FOUND);
  }

  @Test
  public void shouldThrownExceptionIfDuplicateFoundByKey() {
    // given
    final var key = 200L;
    final var entity1 = mock(ProcessInstanceEntity.class);
    final var entity2 = mock(ProcessInstanceEntity.class);
    when(client.searchProcessInstances(any()))
        .thenReturn(new SearchQueryResult<>(2, List.of(entity1, entity2), null, null));

    // when / then
    final var exception =
        assertThrowsExactly(CamundaSearchException.class, () -> services.getByKey(key));
    assertThat(exception.getMessage())
        .isEqualTo("Found Process instance with key 200 more than once");
  }

  @Test
  public void getByKeyShouldThrowForbiddenExceptionIfNotAuthorized() {
    // given
    final var entity = mock(ProcessInstanceEntity.class);
    when(entity.processDefinitionId()).thenReturn("processId");
    when(client.searchProcessInstances(any()))
        .thenReturn(new SearchQueryResult<>(1, List.of(entity), null, null));
    authorizeProcessReadInstance(false, "processId");
    // when
    final Executable executeGetByKey = () -> services.getByKey(1L);
    // then
    final var exception = assertThrowsExactly(ForbiddenException.class, executeGetByKey);
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
  }

  private void authorizeProcessReadInstance(final boolean authorize, final String processId) {
    when(securityContextProvider.isAuthorized(
            processId,
            authentication,
            Authorization.of(a -> a.processDefinition().readProcessInstance())))
        .thenReturn(authorize);
  }
}
