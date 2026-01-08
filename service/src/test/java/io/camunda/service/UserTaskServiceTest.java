/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.auditLogSearchQuery;
import static io.camunda.search.query.SearchQueryBuilders.variableSearchQuery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserTaskQuery;
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
  private AuditLogServices auditLogServices;
  private ProcessCache processCache;

  @BeforeEach
  public void before() {
    client = mock(UserTaskSearchClient.class, RETURNS_SELF);
    formServices = mock(FormServices.class, RETURNS_SELF);
    elementInstanceServices = mock(ElementInstanceServices.class, RETURNS_SELF);
    variableServices = mock(VariableServices.class, RETURNS_SELF);
    auditLogServices = mock(AuditLogServices.class, RETURNS_SELF);
    processCache = mock(ProcessCache.class);
    services =
        new UserTaskServices(
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            client,
            formServices,
            elementInstanceServices,
            variableServices,
            auditLogServices,
            processCache,
            null,
            mock(ApiServicesExecutorProvider.class),
            null);

    when(processCache.getCacheItems(any())).thenReturn(ProcessCacheResult.EMPTY);
  }

  @Nested
  class SearchUserTaskVariables {

    @Test
    public void searchVariablesShouldThrowExceptionWhenNotAuthorized() {
      // given
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
  class SearchUserTaskAuditLog {

    @Test
    public void searchAuditLogsShouldThrowExceptionWhenNotAuthorized() {
      // given
      when(client.getUserTask(any(Long.class)))
          .thenThrow(
              new ResourceAccessDeniedException(Authorizations.USER_TASK_READ_AUTHORIZATION));

      // when
      final ThrowingCallable executable =
          () -> services.searchUserTaskAuditLogs(1L, auditLogSearchQuery().build());

      // then
      final var exception =
          (ServiceException)
              assertThatThrownBy(executable).isInstanceOf(ServiceException.class).actual();
      assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
      verify(client).getUserTask(any(Long.class));
      verify(elementInstanceServices, never()).search(any());
      verify(auditLogServices, never()).search(any());
    }

    @Test
    public void shouldReturnUserTaskAuditLogs() {
      // given
      final var entity = Instancio.create(UserTaskEntity.class);
      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      final var outputEntity = Instancio.create(AuditLogEntity.class);
      when(auditLogServices.search(
              auditLogSearchQuery(q -> q.filter(f -> f.userTaskKeys(entity.userTaskKey())))))
          .thenReturn(SearchQueryResult.of(outputEntity));

      // when
      final SearchQueryResult<AuditLogEntity> searchQueryResult =
          services.searchUserTaskAuditLogs(entity.userTaskKey(), auditLogSearchQuery().build());

      // then
      assertThat(searchQueryResult.items()).containsOnly(outputEntity);
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
          Instancio.of(UserTaskEntity.class)
              .set(field(UserTaskEntity::name), null)
              .set(field(UserTaskEntity::processName), null)
              .create();

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(processCache.getCacheItem(entity.processDefinitionKey()))
          .thenReturn(
              new ProcessCacheItem("ProcessName", Map.of(entity.elementId(), "cached name")));

      final var foundEntity = services.getByKey(entity.userTaskKey());

      assertThat(foundEntity.name()).isEqualTo("cached name");
    }

    @Test
    void shouldReturnUserTaskWithProcessName() {
      final var entity =
          Instancio.of(UserTaskEntity.class).set(field(UserTaskEntity::processName), null).create();

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(processCache.getCacheItem(entity.processDefinitionKey()))
          .thenReturn(
              new ProcessCacheItem("ProcessName", Map.of(entity.elementId(), "cached name")));

      final var foundEntity = services.getByKey(entity.userTaskKey());

      assertThat(foundEntity.processName()).isEqualTo("ProcessName");
    }

    @Test
    void shouldReturnUserTaskWithElementIdAsDefaultName() {
      final var entity =
          Instancio.of(UserTaskEntity.class).set(field(UserTaskEntity::name), null).create();

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(processCache.getCacheItem(entity.processDefinitionKey()))
          .thenReturn(new ProcessCacheItem("ProcessName", Map.of("unknown-id", "cached name")));

      final var foundEntity = services.getByKey(entity.userTaskKey());

      assertThat(foundEntity.name()).isEqualTo(entity.elementId());
      assertThat(foundEntity.processName()).isEqualTo(entity.processName());
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
                  entity.processDefinitionKey(), "ProcessName", entity.elementId(), "cached name"));

      final var searchQueryResult = services.search(UserTaskQuery.of(q -> q));

      assertThat(searchQueryResult.items()).contains(entity.withName("cached name"));
    }

    @Test
    void shouldReturnUserTaskWithProcessName() {
      final var entity =
          Instancio.of(UserTaskEntity.class).set(field(UserTaskEntity::processName), null).create();
      when(client.searchUserTasks(any())).thenReturn(SearchQueryResult.of(entity));
      when(processCache.getCacheItems(Set.of(entity.processDefinitionKey())))
          .thenReturn(
              ProcessCacheResult.of(
                  entity.processDefinitionKey(), "ProcessName", entity.elementId(), "cached name"));

      final var searchQueryResult = services.search(UserTaskQuery.of(q -> q));

      assertThat(searchQueryResult.items()).contains(entity.withProcessName("ProcessName"));
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
