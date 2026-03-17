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
import io.camunda.search.query.VariableQuery;
import io.camunda.search.sort.SortOptionBuilders;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.authorization.Authorizations;
import io.camunda.service.cache.ProcessCache;
import io.camunda.service.cache.ProcessCacheItem;
import io.camunda.service.cache.ProcessCacheResult;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
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
  private CamundaAuthentication authentication;

  @BeforeEach
  public void before() {
    client = mock(UserTaskSearchClient.class, RETURNS_SELF);
    formServices = mock(FormServices.class, RETURNS_SELF);
    elementInstanceServices = mock(ElementInstanceServices.class, RETURNS_SELF);
    variableServices = mock(VariableServices.class, RETURNS_SELF);
    auditLogServices = mock(AuditLogServices.class, RETURNS_SELF);
    processCache = mock(ProcessCache.class);
    authentication = mock(CamundaAuthentication.class);
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
              new ResourceAccessDeniedException(
                  Authorizations.PROCESS_DEFINITION_READ_USER_TASK_AUTHORIZATION));

      // when
      final ThrowingCallable executable =
          () -> services.searchUserTaskVariables(1L, variableSearchQuery().build(), authentication);

      // then
      final var exception =
          (ServiceException)
              assertThatThrownBy(executable).isInstanceOf(ServiceException.class).actual();
      assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
      verify(client).getUserTask(any(Long.class));
      verify(elementInstanceServices, never()).search(any(), any());
      verify(variableServices, never()).search(any(), any());
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
      final var variable =
          new VariableEntity(100L, "city", "Berlin", "Berlin", false, 3L, 1L, 1L, "proc", "t1");

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(elementInstanceServices.getByKey(
              eq(flowNodeInstanceEntity.flowNodeInstanceKey()), any()))
          .thenReturn(flowNodeInstanceEntity);
      when(variableServices.search(any(), any()))
          .thenReturn(new SearchQueryResult<>(1, false, List.of(variable), null, null));

      // when
      final SearchQueryResult<VariableEntity> searchQueryResult =
          services.searchUserTaskVariables(
              entity.userTaskKey(), variableSearchQuery().build(), authentication);

      // then
      assertThat(searchQueryResult.total()).isEqualTo(1);
      assertThat(searchQueryResult.items()).containsExactly(variable);
    }

    @Test
    public void shouldDeduplicateVariablesByScope() {
      // given — same variable "city" at root scope (1) and inner scope (3)
      final var entity = Instancio.create(UserTaskEntity.class);
      final var flowNodeInstanceEntity =
          Instancio.of(FlowNodeInstanceEntity.class)
              .set(field(FlowNodeInstanceEntity::flowNodeInstanceKey), entity.elementInstanceKey())
              .set(field(FlowNodeInstanceEntity::treePath), "1/2/3")
              .create();
      final var outerVar =
          new VariableEntity(100L, "city", "Munich", "Munich", false, 1L, 1L, 1L, "proc", "t1");
      final var innerVar =
          new VariableEntity(101L, "city", "Berlin", "Berlin", false, 3L, 1L, 1L, "proc", "t1");

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(elementInstanceServices.getByKey(
              eq(flowNodeInstanceEntity.flowNodeInstanceKey()), any()))
          .thenReturn(flowNodeInstanceEntity);
      when(variableServices.search(any(), any()))
          .thenReturn(new SearchQueryResult<>(2, false, List.of(outerVar, innerVar), null, null));

      // when
      final SearchQueryResult<VariableEntity> result =
          services.searchUserTaskVariables(
              entity.userTaskKey(), variableSearchQuery().build(), authentication);

      // then — innermost scope (3) wins, only one "city" returned
      assertThat(result.total()).isEqualTo(1);
      assertThat(result.items()).containsExactly(innerVar);
    }

    @Test
    public void shouldKeepInnermostScopeVariable() {
      // given — variable "x" at three scopes: root (1), middle (2), leaf (3)
      final var entity = Instancio.create(UserTaskEntity.class);
      final var flowNodeInstanceEntity =
          Instancio.of(FlowNodeInstanceEntity.class)
              .set(field(FlowNodeInstanceEntity::flowNodeInstanceKey), entity.elementInstanceKey())
              .set(field(FlowNodeInstanceEntity::treePath), "1/2/3")
              .create();
      final var rootVar =
          new VariableEntity(100L, "x", "root", "root", false, 1L, 1L, 1L, "proc", "t1");
      final var midVar =
          new VariableEntity(101L, "x", "mid", "mid", false, 2L, 1L, 1L, "proc", "t1");
      final var leafVar =
          new VariableEntity(102L, "x", "leaf", "leaf", false, 3L, 1L, 1L, "proc", "t1");

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(elementInstanceServices.getByKey(
              eq(flowNodeInstanceEntity.flowNodeInstanceKey()), any()))
          .thenReturn(flowNodeInstanceEntity);
      when(variableServices.search(any(), any()))
          .thenReturn(
              new SearchQueryResult<>(3, false, List.of(rootVar, midVar, leafVar), null, null));

      // when
      final SearchQueryResult<VariableEntity> result =
          services.searchUserTaskVariables(
              entity.userTaskKey(), variableSearchQuery().build(), authentication);

      // then — leaf (innermost) wins
      assertThat(result.total()).isEqualTo(1);
      assertThat(result.items()).containsExactly(leafVar);
      assertThat(result.items().getFirst().value()).isEqualTo("leaf");
    }

    @Test
    public void shouldReturnCorrectTotalAfterDedup() {
      // given — 3 unique names across scopes, but 5 total variable documents
      final var entity = Instancio.create(UserTaskEntity.class);
      final var flowNodeInstanceEntity =
          Instancio.of(FlowNodeInstanceEntity.class)
              .set(field(FlowNodeInstanceEntity::flowNodeInstanceKey), entity.elementInstanceKey())
              .set(field(FlowNodeInstanceEntity::treePath), "1/2/3")
              .create();
      final var vars =
          List.of(
              new VariableEntity(1L, "a", "v1", "v1", false, 1L, 1L, 1L, "p", "t"),
              new VariableEntity(2L, "a", "v2", "v2", false, 3L, 1L, 1L, "p", "t"),
              new VariableEntity(3L, "b", "v3", "v3", false, 1L, 1L, 1L, "p", "t"),
              new VariableEntity(4L, "b", "v4", "v4", false, 2L, 1L, 1L, "p", "t"),
              new VariableEntity(5L, "c", "v5", "v5", false, 1L, 1L, 1L, "p", "t"));

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(elementInstanceServices.getByKey(
              eq(flowNodeInstanceEntity.flowNodeInstanceKey()), any()))
          .thenReturn(flowNodeInstanceEntity);
      when(variableServices.search(any(), any()))
          .thenReturn(new SearchQueryResult<>(5, false, vars, null, null));

      // when
      final SearchQueryResult<VariableEntity> result =
          services.searchUserTaskVariables(
              entity.userTaskKey(), variableSearchQuery().build(), authentication);

      // then — 3 unique names after dedup, total reflects deduplicated count
      assertThat(result.total()).isEqualTo(3);
      assertThat(result.items()).hasSize(3);
      assertThat(result.items())
          .extracting(VariableEntity::name)
          .containsExactlyInAnyOrder("a", "b", "c");
      // "a" should be from scope 3 (innermost), "b" from scope 2, "c" from scope 1
      assertThat(result.items())
          .filteredOn(v -> v.name().equals("a"))
          .extracting(VariableEntity::value)
          .containsExactly("v2");
      assertThat(result.items())
          .filteredOn(v -> v.name().equals("b"))
          .extracting(VariableEntity::value)
          .containsExactly("v4");
    }

    @Test
    public void shouldSortDeduplicatedVariablesByNameAsc() {
      // given
      final var entity = Instancio.create(UserTaskEntity.class);
      final var flowNodeInstanceEntity =
          Instancio.of(FlowNodeInstanceEntity.class)
              .set(field(FlowNodeInstanceEntity::flowNodeInstanceKey), entity.elementInstanceKey())
              .set(field(FlowNodeInstanceEntity::treePath), "1")
              .create();
      final var vars =
          List.of(
              new VariableEntity(1L, "zebra", "z", "z", false, 1L, 1L, 1L, "p", "t"),
              new VariableEntity(2L, "apple", "a", "a", false, 1L, 1L, 1L, "p", "t"),
              new VariableEntity(3L, "mango", "m", "m", false, 1L, 1L, 1L, "p", "t"));

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(elementInstanceServices.getByKey(
              eq(flowNodeInstanceEntity.flowNodeInstanceKey()), any()))
          .thenReturn(flowNodeInstanceEntity);
      when(variableServices.search(any(), any()))
          .thenReturn(new SearchQueryResult<>(3, false, vars, null, null));

      // when — sort by name ASC
      final var query =
          VariableQuery.of(q -> q.sort(SortOptionBuilders.variable(s -> s.name().asc())));
      final SearchQueryResult<VariableEntity> result =
          services.searchUserTaskVariables(entity.userTaskKey(), query, authentication);

      // then
      assertThat(result.items())
          .extracting(VariableEntity::name)
          .containsExactly("apple", "mango", "zebra");
    }

    @Test
    public void shouldSortDeduplicatedVariablesByValueDesc() {
      // given
      final var entity = Instancio.create(UserTaskEntity.class);
      final var flowNodeInstanceEntity =
          Instancio.of(FlowNodeInstanceEntity.class)
              .set(field(FlowNodeInstanceEntity::flowNodeInstanceKey), entity.elementInstanceKey())
              .set(field(FlowNodeInstanceEntity::treePath), "1")
              .create();
      final var vars =
          List.of(
              new VariableEntity(1L, "a", "alpha", "alpha", false, 1L, 1L, 1L, "p", "t"),
              new VariableEntity(2L, "b", "charlie", "charlie", false, 1L, 1L, 1L, "p", "t"),
              new VariableEntity(3L, "c", "bravo", "bravo", false, 1L, 1L, 1L, "p", "t"));

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(elementInstanceServices.getByKey(
              eq(flowNodeInstanceEntity.flowNodeInstanceKey()), any()))
          .thenReturn(flowNodeInstanceEntity);
      when(variableServices.search(any(), any()))
          .thenReturn(new SearchQueryResult<>(3, false, vars, null, null));

      // when — sort by value DESC
      final var query =
          VariableQuery.of(q -> q.sort(SortOptionBuilders.variable(s -> s.value().desc())));
      final SearchQueryResult<VariableEntity> result =
          services.searchUserTaskVariables(entity.userTaskKey(), query, authentication);

      // then
      assertThat(result.items())
          .extracting(VariableEntity::value)
          .containsExactly("charlie", "bravo", "alpha");
    }

    @Test
    public void shouldApplyPaginationAfterDedup() {
      // given — 4 unique variables, request page with from=1, size=2
      final var entity = Instancio.create(UserTaskEntity.class);
      final var flowNodeInstanceEntity =
          Instancio.of(FlowNodeInstanceEntity.class)
              .set(field(FlowNodeInstanceEntity::flowNodeInstanceKey), entity.elementInstanceKey())
              .set(field(FlowNodeInstanceEntity::treePath), "1")
              .create();
      final var vars =
          List.of(
              new VariableEntity(1L, "a", "1", "1", false, 1L, 1L, 1L, "p", "t"),
              new VariableEntity(2L, "b", "2", "2", false, 1L, 1L, 1L, "p", "t"),
              new VariableEntity(3L, "c", "3", "3", false, 1L, 1L, 1L, "p", "t"),
              new VariableEntity(4L, "d", "4", "4", false, 1L, 1L, 1L, "p", "t"));

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(elementInstanceServices.getByKey(
              eq(flowNodeInstanceEntity.flowNodeInstanceKey()), any()))
          .thenReturn(flowNodeInstanceEntity);
      when(variableServices.search(any(), any()))
          .thenReturn(new SearchQueryResult<>(4, false, vars, null, null));

      // when — sort by name ASC, then paginate from=1, size=2
      final var query =
          VariableQuery.of(
              q ->
                  q.sort(SortOptionBuilders.variable(s -> s.name().asc()))
                      .page(p -> p.from(1).size(2)));
      final SearchQueryResult<VariableEntity> result =
          services.searchUserTaskVariables(entity.userTaskKey(), query, authentication);

      // then — total is 4 (all unique), but page returns items at index 1 and 2
      assertThat(result.total()).isEqualTo(4);
      assertThat(result.items()).hasSize(2);
      assertThat(result.items()).extracting(VariableEntity::name).containsExactly("b", "c");
    }

    @Test
    public void shouldReturnNullCursors() {
      // given — cursors are not supported for this endpoint because deduplication and sorting
      // are performed in-memory. The Tasklist UI uses offset-based pagination exclusively.
      final var entity = Instancio.create(UserTaskEntity.class);
      final var flowNodeInstanceEntity =
          Instancio.of(FlowNodeInstanceEntity.class)
              .set(field(FlowNodeInstanceEntity::flowNodeInstanceKey), entity.elementInstanceKey())
              .set(field(FlowNodeInstanceEntity::treePath), "1")
              .create();
      final var vars =
          List.of(
              new VariableEntity(10L, "alpha", "a", "a", false, 1L, 1L, 1L, "p", "t"),
              new VariableEntity(20L, "bravo", "b", "b", false, 1L, 1L, 1L, "p", "t"));

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(elementInstanceServices.getByKey(
              eq(flowNodeInstanceEntity.flowNodeInstanceKey()), any()))
          .thenReturn(flowNodeInstanceEntity);
      when(variableServices.search(any(), any()))
          .thenReturn(new SearchQueryResult<>(2, false, vars, null, null));

      // when
      final SearchQueryResult<VariableEntity> result =
          services.searchUserTaskVariables(
              entity.userTaskKey(), variableSearchQuery().build(), authentication);

      // then — items are returned but cursors are always null
      assertThat(result.total()).isEqualTo(2);
      assertThat(result.items()).hasSize(2);
      assertThat(result.startCursor()).isNull();
      assertThat(result.endCursor()).isNull();
    }
  }

  @Nested
  class SearchUserTaskAuditLog {

    @Test
    public void searchAuditLogsShouldThrowExceptionWhenNotAuthorized() {
      // given
      when(client.getUserTask(any(Long.class)))
          .thenThrow(
              new ResourceAccessDeniedException(
                  Authorizations.PROCESS_DEFINITION_READ_USER_TASK_AUTHORIZATION));

      // when
      final ThrowingCallable executable =
          () -> services.searchUserTaskAuditLogs(1L, auditLogSearchQuery().build(), authentication);

      // then
      final var exception =
          (ServiceException)
              assertThatThrownBy(executable).isInstanceOf(ServiceException.class).actual();
      assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
      verify(client).getUserTask(any(Long.class));
      verify(elementInstanceServices, never()).search(any(), any());
      verify(auditLogServices, never()).search(any(), any());
    }

    @Test
    public void shouldReturnUserTaskAuditLogs() {
      // given
      final var entity = Instancio.create(UserTaskEntity.class);
      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      final var outputEntity = Instancio.create(AuditLogEntity.class);
      when(auditLogServices.search(
              eq(auditLogSearchQuery(q -> q.filter(f -> f.userTaskKeys(entity.userTaskKey())))),
              any()))
          .thenReturn(SearchQueryResult.of(outputEntity));

      // when
      final SearchQueryResult<AuditLogEntity> searchQueryResult =
          services.searchUserTaskAuditLogs(
              entity.userTaskKey(), auditLogSearchQuery().build(), authentication);

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
      when(formServices.getByKey(eq(entity.formKey()), any())).thenReturn(form);

      // when
      final var result = services.getUserTaskForm(entity.userTaskKey(), authentication);

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
      final var result = services.getUserTaskForm(1L, authentication);

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

      final var searchQueryResult = services.getByKey(entity.userTaskKey(), authentication);

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

      final var foundEntity = services.getByKey(entity.userTaskKey(), authentication);

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

      final var foundEntity = services.getByKey(entity.userTaskKey(), authentication);

      assertThat(foundEntity.processName()).isEqualTo("ProcessName");
    }

    @Test
    void shouldReturnUserTaskWithElementIdAsDefaultName() {
      final var entity =
          Instancio.of(UserTaskEntity.class).set(field(UserTaskEntity::name), null).create();

      when(client.getUserTask(any(Long.class))).thenReturn(entity);
      when(processCache.getCacheItem(entity.processDefinitionKey()))
          .thenReturn(new ProcessCacheItem("ProcessName", Map.of("unknown-id", "cached name")));

      final var foundEntity = services.getByKey(entity.userTaskKey(), authentication);

      assertThat(foundEntity.name()).isEqualTo(entity.elementId());
      assertThat(foundEntity.processName()).isEqualTo(entity.processName());
    }

    @Test
    void shouldThrowExceptionWhenNotAuthorized() {
      final var entity = Instancio.create(UserTaskEntity.class);

      when(client.getUserTask(any(Long.class)))
          .thenThrow(
              new ResourceAccessDeniedException(
                  Authorizations.PROCESS_DEFINITION_READ_USER_TASK_AUTHORIZATION));

      final ThrowingCallable executable =
          () -> services.getByKey(entity.userTaskKey(), authentication);

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

      final var searchQueryResult = services.search(UserTaskQuery.of(q -> q), authentication);

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

      final var searchQueryResult = services.search(UserTaskQuery.of(q -> q), authentication);

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

      final var searchQueryResult = services.search(UserTaskQuery.of(q -> q), authentication);

      assertThat(searchQueryResult.items()).contains(entity.withProcessName("ProcessName"));
    }

    @Test
    void shouldReturnUserTaskWithElementIdAsDefaultName() {
      final var entity =
          Instancio.of(UserTaskEntity.class).set(field(UserTaskEntity::name), null).create();
      when(client.searchUserTasks(any())).thenReturn(SearchQueryResult.of(entity));

      final var searchQueryResult = services.search(UserTaskQuery.of(q -> q), authentication);

      assertThat(searchQueryResult.items()).contains(entity.withName(entity.elementId()));
    }
  }
}
