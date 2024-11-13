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

import io.camunda.search.clients.VariableSearchClient;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.filter.VariableFilter;
import io.camunda.search.filter.VariableFilter.Builder;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public class VariableServiceTest {

  private VariableServices services;
  private VariableSearchClient client;
  private SecurityContextProvider securityContextProvider;
  private Authentication authentication;

  @BeforeEach
  public void before() {
    client = mock(VariableSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    securityContextProvider = mock(SecurityContextProvider.class);
    authentication = mock(Authentication.class);
    services =
        new VariableServices(
            mock(BrokerClient.class), securityContextProvider, client, authentication);
  }

  @Test
  public void shouldEmptyQueryReturnVariables() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchVariables(any())).thenReturn(result);

    final VariableFilter filter = new Builder().build();
    final var searchQuery = SearchQueryBuilders.variableSearchQuery((b) -> b.filter(filter));

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnSingleVariable() {
    // given
    final var entity = mock(VariableEntity.class);
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array());
    when(client.searchVariables(any())).thenReturn(result);
  }

  @Test
  public void shouldReturnSingleVariableForGet() {
    // given
    final var entity = mock(VariableEntity.class);
    final var processId = "processId";
    when(entity.bpmnProcessId()).thenReturn(processId);
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array());
    when(client.searchVariables(any())).thenReturn(result);
    when(securityContextProvider.isAuthorized(
            processId, authentication, Authorization.of(a -> a.processDefinition().readInstance())))
        .thenReturn(true);

    // when
    final var searchQueryResult = services.getByKey(1L);

    // then
    assertThat(searchQueryResult).isEqualTo(entity);
  }

  @Test
  public void getByKeyShouldThrowForbiddenExceptionIfNotAuthorized() {
    // given
    final var entity = mock(VariableEntity.class);
    final var processId = "processId";
    when(entity.bpmnProcessId()).thenReturn(processId);
    when(client.searchVariables(any()))
        .thenReturn(new SearchQueryResult<>(1, List.of(entity), null));
    when(securityContextProvider.isAuthorized(
            processId, authentication, Authorization.of(a -> a.processDefinition().readInstance())))
        .thenReturn(false);
    // when
    final Executable executeGetByKey = () -> services.getByKey(1L);
    // then
    final var exception = assertThrowsExactly(ForbiddenException.class, executeGetByKey);
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_INSTANCE' on resource 'PROCESS_DEFINITION'");
  }
}
