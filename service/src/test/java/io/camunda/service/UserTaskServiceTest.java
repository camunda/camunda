/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.flownodeInstanceSearchQuery;
import static io.camunda.search.query.SearchQueryBuilders.formSearchQuery;
import static io.camunda.search.query.SearchQueryBuilders.variableSearchQuery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.clients.FormSearchClient;
import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.clients.VariableSearchClient;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserTaskQuery;
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

public class UserTaskServiceTest {

  private UserTaskServices services;
  private UserTaskSearchClient client;
  private FormSearchClient formSearchClient;
  private FlowNodeInstanceSearchClient flowNodeInstanceSearchClient;
  private VariableSearchClient variableSearchClient;
  private ProcessCache processCache;
  private SecurityContextProvider securityContextProvider;
  private CamundaAuthentication authentication;

  @BeforeEach
  public void before() {
    client = mock(UserTaskSearchClient.class);
    formSearchClient = mock(FormSearchClient.class);
    flowNodeInstanceSearchClient = mock(FlowNodeInstanceSearchClient.class);
    variableSearchClient = mock(VariableSearchClient.class);
    processCache = mock(ProcessCache.class);
    securityContextProvider = mock(SecurityContextProvider.class);
    authentication = mock(CamundaAuthentication.class);
    services =
        new UserTaskServices(
            mock(BrokerClient.class),
            securityContextProvider,
            client,
            formSearchClient,
            flowNodeInstanceSearchClient,
            variableSearchClient,
            processCache,
            authentication);

    when(client.withSecurityContext(any())).thenReturn(client);
    when(formSearchClient.withSecurityContext(any())).thenReturn(formSearchClient);
    when(flowNodeInstanceSearchClient.withSecurityContext(any()))
        .thenReturn(flowNodeInstanceSearchClient);
    when(variableSearchClient.withSecurityContext(any())).thenReturn(variableSearchClient);
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
    public void searchVariablesShouldThrowExceptionWhenNotAuthorized() {
      // given
      final var entity = Instancio.create(UserTaskEntity.class);
      final var flowNodeInstanceEntity =
          Instancio.of(FlowNodeInstanceEntity.class)
              .set(field(FlowNodeInstanceEntity::flowNodeInstanceKey), entity.elementInstanceKey())
              .set(field(FlowNodeInstanceEntity::treePath), "1/2/3")
              .create();
      final var variable = Instancio.create(VariableEntity.class);

      when(client.searchUserTasks(any())).thenReturn(SearchQueryResult.of(entity));
      authorizeReadUserTasksForProcess(false, entity.processDefinitionId());

      // when
      final Executable executable =
          () -> services.searchUserTaskVariables(1L, variableSearchQuery().build());

      // then
      assertThrows(ForbiddenException.class, executable);
      verify(client).searchUserTasks(any());
      verify(securityContextProvider)
          .isAuthorized(
              entity.processDefinitionId(),
              authentication,
              Authorization.of(a -> a.processDefinition().readUserTask()));
      verify(flowNodeInstanceSearchClient, never()).searchFlowNodeInstances(any());
      verify(variableSearchClient, never()).searchVariables(any());
    }

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

      when(client.searchUserTasks(any())).thenReturn(SearchQueryResult.of(entity));
      when(flowNodeInstanceSearchClient.searchFlowNodeInstances(
              flownodeInstanceSearchQuery(
                  q ->
                      q.filter(
                              f ->
                                  f.flowNodeInstanceKeys(
                                      flowNodeInstanceEntity.flowNodeInstanceKey()))
                          .singleResult())))
          .thenReturn(SearchQueryResult.of(flowNodeInstanceEntity));
      when(variableSearchClient.searchVariables(
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

      when(formSearchClient.searchForms(
              formSearchQuery(q -> q.filter(f -> f.formKeys(entity.formKey())).singleResult())))
          .thenReturn(SearchQueryResult.of(form));
      when(client.searchUserTasks(any())).thenReturn(SearchQueryResult.of(entity));
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

      when(client.searchUserTasks(any())).thenReturn(SearchQueryResult.of(entity));
      authorizeReadUserTasksForProcess(true, entity.processDefinitionId());

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

      when(client.searchUserTasks(any())).thenReturn(SearchQueryResult.of(entity));
      authorizeReadUserTasksForProcess(true, entity.processDefinitionId());

      final var searchQueryResult = services.getByKey(entity.userTaskKey());

      assertThat(searchQueryResult).isEqualTo(entity);
    }

    @Test
    void shouldReturnUserTaskWithCachedName() {
      final var entity =
          Instancio.of(UserTaskEntity.class).set(field(UserTaskEntity::name), null).create();

      when(client.searchUserTasks(any())).thenReturn(SearchQueryResult.of(entity));
      authorizeReadUserTasksForProcess(true, entity.processDefinitionId());
      when(processCache.getCacheItems(Set.of(entity.processDefinitionKey())))
          .thenReturn(
              ProcessCacheResult.of(
                  entity.processDefinitionKey(), entity.elementId(), "cached name"));

      final var foundEntity = services.getByKey(entity.userTaskKey());

      assertThat(foundEntity.name()).isEqualTo("cached name");
    }

    @Test
    void shouldReturnUserTaskWithElementIdAsDefaultName() {
      final var entity =
          Instancio.of(UserTaskEntity.class).set(field(UserTaskEntity::name), null).create();

      when(client.searchUserTasks(any())).thenReturn(SearchQueryResult.of(entity));
      authorizeReadUserTasksForProcess(true, entity.processDefinitionId());

      final var foundEntity = services.getByKey(entity.userTaskKey());

      assertThat(foundEntity.name()).isEqualTo(entity.elementId());
    }

    @Test
    void shouldThrowExceptionWhenNotAuthorized() {
      final var entity = Instancio.create(UserTaskEntity.class);

      when(client.searchUserTasks(any())).thenReturn(SearchQueryResult.of(entity));
      authorizeReadUserTasksForProcess(false, entity.processDefinitionId());

      final Executable executable = () -> services.getByKey(entity.userTaskKey());

      assertThrows(ForbiddenException.class, executable);
      verify(client).searchUserTasks(any());
      verify(securityContextProvider)
          .isAuthorized(
              entity.processDefinitionId(),
              authentication,
              Authorization.of(a -> a.processDefinition().readUserTask()));
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
