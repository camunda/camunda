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
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.security.auth.CamundaAuthentication;
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

public class UserTaskServiceTest {

  private UserTaskServices services;
  private UserTaskSearchClient client;
  private FormServices formServices;
  private ElementInstanceServices elementInstanceServices;
  private VariableServices variableServices;
  private ProcessCache processCache;

  @BeforeEach
  public void before() {
    client = mock(UserTaskSearchClient.class);
    formServices = mock(FormServices.class);
    elementInstanceServices = mock(ElementInstanceServices.class);
    variableServices = mock(VariableServices.class);
    processCache = mock(ProcessCache.class);
    services =
        new UserTaskServices(
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            client,
            formServices,
            elementInstanceServices,
            variableServices,
            processCache,
            null,
            mock(ApiServicesExecutorProvider.class));

    when(client.withSecurityContext(any())).thenReturn(client);
    when(formServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(formServices);
    when(elementInstanceServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(elementInstanceServices);
    when(variableServices.withAuthentication(any(CamundaAuthentication.class)))
        .thenReturn(variableServices);
    when(processCache.getCacheItems(any())).thenReturn(ProcessCacheResult.EMPTY);
  }

  @Nested
  class SearchUserTaskVariables {

    @Test
    public void searchVariablesShouldThrowExceptionWhenNotAuthorized() {
      // given
      final var entity = Instancio.create(UserTaskEntity.class);

      when(client.getUserTask(any(Long.class)))
          .thenThrow(
              new ResourceAccessDeniedException(Authorizations.USER_TASK_READ_AUTHORIZATION));

      // when
      final ThrowingCallable executable =
          () -> services.searchUserTaskVariables(1L, variableSearchQuery().build());

      // then
      final var exception =
          (ServiceException)
              assertThatThrownBy(executable).isInstanceOf(ServiceException.class).actual();
      assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
      verify(client).getUserTask(any(Long.class));
      verify(elementInstanceServices, never()).search(any());
      verify(variableServices, never()).search(any());
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

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(elementInstanceServices.getByKey(eq(flowNodeInstanceEntity.flowNodeInstanceKey())))
          .thenReturn(flowNodeInstanceEntity);
      when(variableServices.search(
              variableSearchQuery(q -> q.filter(f -> f.scopeKeys(1L, 2L, 3L)))))
          .thenReturn(SearchQueryResult.of(variable));

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

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(formServices.getByKey(eq(entity.formKey()))).thenReturn(form);

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

      when(client.getUserTask(any(Long.class))).thenReturn(entity);

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

      when(client.getUserTask(any(Long.class))).thenReturn(entity);

      final var searchQueryResult = services.getByKey(entity.userTaskKey());

      assertThat(searchQueryResult).isEqualTo(entity);
    }

    @Test
    void shouldReturnUserTaskWithCachedName() {
      final var entity =
          Instancio.of(UserTaskEntity.class).set(field(UserTaskEntity::name), null).create();

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(processCache.getCacheItem(entity.processDefinitionKey()))
          .thenReturn(new ProcessCacheItem(Map.of(entity.elementId(), "cached name")));

      final var foundEntity = services.getByKey(entity.userTaskKey());

      assertThat(foundEntity.name()).isEqualTo("cached name");
    }

    @Test
    void shouldReturnUserTaskWithElementIdAsDefaultName() {
      final var entity =
          Instancio.of(UserTaskEntity.class).set(field(UserTaskEntity::name), null).create();

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(processCache.getCacheItem(entity.processDefinitionKey()))
          .thenReturn(new ProcessCacheItem(Map.of("unknown-id", "cached name")));

      final var foundEntity = services.getByKey(entity.userTaskKey());

      assertThat(foundEntity.name()).isEqualTo(entity.elementId());
    }

    @Test
    void shouldThrowExceptionWhenNotAuthorized() {
      final var entity = Instancio.create(UserTaskEntity.class);

      when(client.getUserTask(any(Long.class)))
          .thenThrow(
              new ResourceAccessDeniedException(Authorizations.USER_TASK_READ_AUTHORIZATION));

      final ThrowingCallable executable = () -> services.getByKey(entity.userTaskKey());

      final var exception =
          (ServiceException)
              assertThatCode(executable).isInstanceOf(ServiceException.class).actual();
      assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
      verify(client).getUserTask(any(Long.class));
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
