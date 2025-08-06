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

import io.camunda.search.clients.IncidentSearchClient;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.authorization.Authorizations;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class IncidentServiceTest {

  private IncidentServices services;
  private IncidentSearchClient client;

  @BeforeEach
  public void before() {
    client = mock(IncidentSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    services =
        new IncidentServices(
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            client,
            null,
            mock(ApiServicesExecutorProvider.class));
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
    when(entity.processDefinitionId()).thenReturn(processId);
    when(client.getIncident(any(Long.class))).thenReturn(entity);
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
    when(entity.processDefinitionId()).thenReturn(processId);
    when(client.getIncident(any(Long.class)))
        .thenThrow(new ResourceAccessDeniedException(Authorizations.INCIDENT_READ_AUTHORIZATION));
    // when
    final ThrowingCallable executeGetByKey = () -> services.getByKey(1L);
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
