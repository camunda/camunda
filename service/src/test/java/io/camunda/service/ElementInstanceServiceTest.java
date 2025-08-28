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
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.authorization.Authorizations;
import io.camunda.service.cache.ProcessCache;
import io.camunda.service.cache.ProcessCacheItem;
import io.camunda.service.cache.ProcessCacheResult;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public final class ElementInstanceServiceTest {

  private ElementInstanceServices services;
  private FlowNodeInstanceSearchClient client;
  private ProcessCache processCache;

  @BeforeEach
  public void before() {
    client = mock(FlowNodeInstanceSearchClient.class);
    processCache = mock(ProcessCache.class);
    services =
        new ElementInstanceServices(
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            client,
            processCache,
            null,
            mock(ApiServicesExecutorProvider.class));

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
      when(client.getFlowNodeInstance(any(Long.class))).thenReturn(entity);

      // when
      final var searchQueryResult = services.getByKey(entity.flowNodeInstanceKey());

      // then
      assertThat(searchQueryResult).isEqualTo(entity);
    }

    @Test
    public void getByKeyShouldThrowForbiddenExceptionIfNotAuthorized() {
      // given
      final var entity = Instancio.create(FlowNodeInstanceEntity.class);
      when(client.getFlowNodeInstance(any(Long.class)))
          .thenThrow(
              new ResourceAccessDeniedException(
                  Authorizations.ELEMENT_INSTANCE_READ_AUTHORIZATION));

      // when
      final ThrowingCallable executeGetByKey =
          () -> services.getByKey(entity.flowNodeInstanceKey());
      // then
      final var exception =
          (ServiceException)
              assertThatThrownBy(executeGetByKey).isInstanceOf(ServiceException.class).actual();
      assertThat(exception.getMessage())
          .isEqualTo(
              "Unauthorized to perform operation 'READ_PROCESS_INSTANCE' on resource 'PROCESS_DEFINITION'");
      assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
    }

    @Test
    public void shouldReturnFlowNodeInstanceWithCachedName() {
      // given
      final var entity =
          Instancio.of(FlowNodeInstanceEntity.class)
              .set(field(FlowNodeInstanceEntity::flowNodeName), null)
              .create();

      when(client.getFlowNodeInstance(any(Long.class))).thenReturn(entity);
      when(processCache.getCacheItem(entity.processDefinitionKey()))
          .thenReturn(new ProcessCacheItem(Map.of(entity.flowNodeId(), "cached name")));

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

      when(client.getFlowNodeInstance(any(Long.class))).thenReturn(entity);
      when(processCache.getCacheItem(entity.processDefinitionKey()))
          .thenReturn(new ProcessCacheItem(Map.of("unknown-id", "cached name")));

      // when
      final var foundEntity = services.getByKey(entity.flowNodeInstanceKey());

      // then
      assertThat(foundEntity.flowNodeName()).isEqualTo(entity.flowNodeId());
    }
  }
}
