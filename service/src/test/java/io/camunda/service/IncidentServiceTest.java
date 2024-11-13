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

import io.camunda.search.clients.IncidentSearchClient;
import io.camunda.search.entities.IncidentEntity;
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

public final class IncidentServiceTest {

  private IncidentServices services;
  private IncidentSearchClient client;
  private SecurityContextProvider securityContextProvider;
  private Authentication authentication;

  @BeforeEach
  public void before() {
    client = mock(IncidentSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    securityContextProvider = mock(SecurityContextProvider.class);
    authentication = mock(Authentication.class);
    services =
        new IncidentServices(
            mock(BrokerClient.class), securityContextProvider, client, authentication);
  }

  @Test
  public void shouldReturnIncident() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchIncidents(any())).thenReturn(result);

    final var searchQuery = SearchQueryBuilders.incidentSearchQuery().build();

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnIncidentByKey() {
    // given
    final var entity = mock(IncidentEntity.class);
    final var processId = "processId";
    when(entity.bpmnProcessId()).thenReturn(processId);
    when(client.searchIncidents(any()))
        .thenReturn(new SearchQueryResult<>(1, List.of(entity), null));
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
    final var entity = mock(IncidentEntity.class);
    final var processId = "processId";
    when(entity.bpmnProcessId()).thenReturn(processId);
    when(client.searchIncidents(any()))
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
