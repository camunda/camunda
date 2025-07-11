/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.variableSearchQuery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.cache.ProcessCache;
import io.camunda.service.cache.ProcessCacheItem;
import io.camunda.service.cache.ProcessCacheResult;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.Map;
import java.util.Set;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class UserTaskServiceTest {

  private UserTaskServices services;
  private UserTaskSearchClient client;
  private FormServices formServices;
  private ElementInstanceServices elementInstanceServices;
  private VariableServices variableServices;
  private ProcessCache processCache;
  private SecurityContextProvider securityContextProvider;
  private CamundaAuthentication authentication;

  @BeforeEach
  public void before() {
    client = mock(UserTaskSearchClient.class);
    formServices = mock(FormServices.class);
    elementInstanceServices = mock(ElementInstanceServices.class);
    variableServices = mock(VariableServices.class);
    processCache = mock(ProcessCache.class);
    securityContextProvider = mock(SecurityContextProvider.class);
    authentication = mock(CamundaAuthentication.class);
    services =
        new UserTaskServices(
            mock(BrokerClient.class),
            securityContextProvider,
            client,
            formServices,
            elementInstanceServices,
            variableServices,
            processCache,
            authentication);

    when(client.withSecurityContext(any())).thenReturn(client);
    when(formServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(formServices);
    when(elementInstanceServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(elementInstanceServices);
    when(variableServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(variableServices);
    when(processCache.getCacheItems(any())).thenReturn(ProcessCacheResult.EMPTY);
  }

  private void authorizeReadUserTasksForProcess(final boolean authorized, final String processId) {
    when(securityContextProvider.isAuthorized(
            processId, authentication, Authorization.of(a -> a.processDefinition().readUserTask())))
        .thenReturn(authorized);
  }

  @Nested
  class SearchUserTaskVariables {

    @Test
    public void shouldReturnUserTaskVariables() {
      // given
      final var entity = Instancio.create(UserTaskEntity.class);
      final var flowNodeInstanceEntity =
          Instancio.of(FlowNodeInstanceEntity.class)
              .set(field(FlowNodeInstanceEntity::flowNodeInstanceKey), entity.elementInstanceKey())
              .set(field(FlowNodeInstanceEntity::treePath), "1/2/3")
              .create();
      final var variable = Instancio.create(VariableEntity.class);

      when(client.getUserTaskByKey(any(Long.class))).thenReturn(entity);
      when(elementInstanceServices.getByKey(any(Long.class))).thenReturn(flowNodeInstanceEntity);
      when(variableServices.search(
              variableSearchQuery(q -> q.filter(f -> f.scopeKeys(1L, 2L, 3L)))))
          .thenReturn(SearchQueryResult.of(variable));
      authorizeReadUserTasksForProcess(true, entity.processDefinitionId());

      // when
      final SearchQueryResult<VariableEntity> searchQueryResult =
          services.searchUserTaskVariables(entity.userTaskKey(), variableSearchQuery().build());

      // then
      assertThat(searchQueryResult.items()).containsOnly(variable);
    }
  }

  @Nested
  class GetUserTaskForm {
    @Test
    public void shouldReturnUserTaskForm() {
      // given
      final var entity = Instancio.create(UserTaskEntity.class);
      final var form =
          Instancio.of(FormEntity.class).set(field(FormEntity::formKey), entity.formKey()).create();

      when(formServices.getByKey(any(Long.class))).thenReturn(form);
      when(client.getUserTaskByKey(any(Long.class))).thenReturn(entity);
      authorizeReadUserTasksForProcess(true, entity.processDefinitionId());

      // when
      final var result = services.getUserTaskForm(entity.userTaskKey());

      // then
      assertThat(result).isPresent();
      assertThat(result.get()).isEqualTo(form);
    }

    @Test
    public void shouldReturnEmptyWhenUserTaskHasNoFormKey() {
      // given
      final var entity =
          Instancio.of(UserTaskEntity.class).set(field(UserTaskEntity::formKey), null).create();

      when(client.getUserTaskByKey(any(Long.class))).thenReturn(entity);

      // when
      final var result = services.getUserTaskForm(1L);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  class GetByKey {

    @Test
    void shouldReturnUserTask() {
      final var entity = Instancio.create(UserTaskEntity.class);

      when(client.getUserTaskByKey(any(Long.class))).thenReturn(entity);

      final var searchQueryResult = services.getByKey(entity.userTaskKey());

      assertThat(searchQueryResult).isEqualTo(entity);
    }

    @Test
    void shouldReturnUserTaskWithCachedName() {
      final var entity =
          Instancio.of(UserTaskEntity.class).set(field(UserTaskEntity::name), null).create();

      when(client.getUserTaskByKey(any(Long.class))).thenReturn(entity);
      when(processCache.getCacheItem(entity.processDefinitionKey()))
          .thenReturn(new ProcessCacheItem(Map.of(entity.elementId(), "cached name")));

      final var foundEntity = services.getByKey(entity.userTaskKey());

      assertThat(foundEntity.name()).isEqualTo("cached name");
    }

    @Test
    void shouldReturnUserTaskWithElementIdAsDefaultName() {
      final var entity =
          Instancio.of(UserTaskEntity.class).set(field(UserTaskEntity::name), null).create();

      when(client.getUserTaskByKey(any(Long.class))).thenReturn(entity);
      when(processCache.getCacheItem(entity.processDefinitionKey()))
          .thenReturn(new ProcessCacheItem(Map.of("a", "b")));

      final var foundEntity = services.getByKey(entity.userTaskKey());

      assertThat(foundEntity.name()).isEqualTo(entity.elementId());
    }
  }

  @Nested
  class Search {

    @Test
    void shouldReturnUserTask() {
      final var entity = Instancio.create(UserTaskEntity.class);
      when(client.searchUserTasks(any())).thenReturn(SearchQueryResult.of(entity));

      final var searchQueryResult = services.search(UserTaskQuery.of(q -> q));

      assertThat(searchQueryResult.items()).contains(entity);
    }

    @Test
    void shouldReturnUserTaskWithCachedName() {
      final var entity =
          Instancio.of(UserTaskEntity.class).set(field(UserTaskEntity::name), null).create();
      when(client.searchUserTasks(any())).thenReturn(SearchQueryResult.of(entity));
      when(processCache.getCacheItems(Set.of(entity.processDefinitionKey())))
          .thenReturn(
              ProcessCacheResult.of(
                  entity.processDefinitionKey(), entity.elementId(), "cached name"));

      final var searchQueryResult = services.search(UserTaskQuery.of(q -> q));

      assertThat(searchQueryResult.items()).contains(entity.withName("cached name"));
    }

    @Test
    void shouldReturnUserTaskWithElementIdAsDefaultName() {
      final var entity =
          Instancio.of(UserTaskEntity.class).set(field(UserTaskEntity::name), null).create();
      when(client.searchUserTasks(any())).thenReturn(SearchQueryResult.of(entity));

      final var searchQueryResult = services.search(UserTaskQuery.of(q -> q));

      assertThat(searchQueryResult.items()).contains(entity.withName(entity.elementId()));
    }
  }
}
