/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.cache.ProcessCache;
import io.camunda.service.cache.ProcessCacheResult;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.Set;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

public final class ElementInstanceServiceTest {

  private ElementInstanceServices services;
  private FlowNodeInstanceSearchClient client;
  private ProcessCache processCache;
  private SecurityContextProvider securityContextProvider;
  private CamundaAuthentication authentication;

  @BeforeEach
  public void before() {
    client = mock(FlowNodeInstanceSearchClient.class);
    processCache = mock(ProcessCache.class);
    securityContextProvider = mock(SecurityContextProvider.class);
    authentication = mock(CamundaAuthentication.class);
    services =
        new ElementInstanceServices(
            mock(BrokerClient.class),
            securityContextProvider,
            client,
            processCache,
            authentication);

    when(client.withSecurityContext(any())).thenReturn(client);
    when(processCache.getCacheItems(any())).thenReturn(ProcessCacheResult.EMPTY);
  }

  @Nested
  class Search {

    @Test
    public void shouldReturnElementInstance() {
      // given
      final var entity = Instancio.create(FlowNodeInstanceEntity.class);
      when(client.searchFlowNodeInstances(any())).thenReturn(SearchQueryResult.of(entity));

      // when
      final var searchQueryResult =
          services.search(SearchQueryBuilders.flownodeInstanceSearchQuery().build());

      // then
      assertThat(searchQueryResult.items()).contains(entity);
    }

    @Test
    void shouldReturnElementInstanceWithCachedName() {
      final var entity =
          Instancio.of(FlowNodeInstanceEntity.class)
              .set(field(FlowNodeInstanceEntity::flowNodeName), null)
              .create();
      when(client.searchFlowNodeInstances(any())).thenReturn(SearchQueryResult.of(entity));
      when(processCache.getCacheItems(Set.of(entity.processDefinitionKey())))
          .thenReturn(
              ProcessCacheResult.of(
                  entity.processDefinitionKey(), entity.flowNodeId(), "cached name"));

      final var searchQueryResult = services.search(FlowNodeInstanceQuery.of(q -> q));

      assertThat(searchQueryResult.items()).contains(entity.withFlowNodeName("cached name"));
    }

    @Test
    void shouldReturnElementInstanceWithElementIdAsDefaultName() {
      final var entity =
          Instancio.of(FlowNodeInstanceEntity.class)
              .set(field(FlowNodeInstanceEntity::flowNodeName), null)
              .create();

      when(client.searchFlowNodeInstances(any())).thenReturn(SearchQueryResult.of(entity));

      final var searchQueryResult = services.search(FlowNodeInstanceQuery.of(q -> q));

      assertThat(searchQueryResult.items()).contains(entity.withFlowNodeName(entity.flowNodeId()));
    }
  }

  @Nested
  class GetByKey {

    @Test
    public void shouldReturnFlowNodeInstanceByKey() {
      // given
      final var entity = Instancio.create(FlowNodeInstanceEntity.class);
      when(client.searchFlowNodeInstances(any())).thenReturn(SearchQueryResult.of(entity));
      when(securityContextProvider.isAuthorized(
              entity.processDefinitionId(),
              authentication,
              Authorization.of(a -> a.processDefinition().readProcessInstance())))
          .thenReturn(true);

      // when
      final var searchQueryResult = services.getByKey(entity.flowNodeInstanceKey());

      // then
      assertThat(searchQueryResult).isEqualTo(entity);
    }

    @Test
    public void getByKeyShouldThrowForbiddenExceptionIfNotAuthorized() {
      // given
      final var entity = Instancio.create(FlowNodeInstanceEntity.class);
      when(client.searchFlowNodeInstances(any())).thenReturn(SearchQueryResult.of(entity));
      when(securityContextProvider.isAuthorized(
              entity.processDefinitionId(),
              authentication,
              Authorization.of(a -> a.processDefinition().readProcessInstance())))
          .thenReturn(false);

      // when
      final Executable executeGetByKey = () -> services.getByKey(entity.flowNodeInstanceKey());
      // then
      final var exception = assertThrowsExactly(ForbiddenException.class, executeGetByKey);
      assertThat(exception.getMessage())
          .isEqualTo(
              "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
    }

    @Test
    public void shouldReturnFlowNodeInstanceWithCachedName() {
      // given
      final var entity =
          Instancio.of(FlowNodeInstanceEntity.class)
              .set(field(FlowNodeInstanceEntity::flowNodeName), null)
              .create();

      when(client.searchFlowNodeInstances(any())).thenReturn(SearchQueryResult.of(entity));
      when(securityContextProvider.isAuthorized(
              entity.processDefinitionId(),
              authentication,
              Authorization.of(a -> a.processDefinition().readProcessInstance())))
          .thenReturn(true);
      when(processCache.getCacheItems(Set.of(entity.processDefinitionKey())))
          .thenReturn(
              ProcessCacheResult.of(
                  entity.processDefinitionKey(), entity.flowNodeId(), "cached name"));

      // when
      final var foundEntity = services.getByKey(entity.flowNodeInstanceKey());

      // then
      assertThat(foundEntity.flowNodeName()).isEqualTo("cached name");
    }

    @Test
    public void shouldReturnFlowNodeInstanceWithElementIdAsDefaultName() {
      // given
      final var entity =
          Instancio.of(FlowNodeInstanceEntity.class)
              .set(field(FlowNodeInstanceEntity::flowNodeName), null)
              .create();

      when(client.searchFlowNodeInstances(any())).thenReturn(SearchQueryResult.of(entity));
      when(securityContextProvider.isAuthorized(
              entity.processDefinitionId(),
              authentication,
              Authorization.of(a -> a.processDefinition().readProcessInstance())))
          .thenReturn(true);

      // when
      final var foundEntity = services.getByKey(entity.flowNodeInstanceKey());

      // then
      assertThat(foundEntity.flowNodeName()).isEqualTo(entity.flowNodeId());
    }
  }
}
