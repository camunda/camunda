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
import io.camunda.search.filter.UserTaskFilter;
import io.camunda.search.filter.UserTaskFilter.Builder;
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

public class UserTaskServiceTest {

  private UserTaskServices services;
  private UserTaskSearchClient client;
  private FormSearchClient formSearchClient;
  private FlowNodeInstanceSearchClient flowNodeInstanceSearchClient;
  private VariableSearchClient variableSearchClient;
  private SecurityContextProvider securityContextProvider;
  private Authentication authentication;

  @BeforeEach
  public void before() {
    client = mock(UserTaskSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    formSearchClient = mock(FormSearchClient.class);
    when(formSearchClient.withSecurityContext(any())).thenReturn(formSearchClient);
    flowNodeInstanceSearchClient = mock(FlowNodeInstanceSearchClient.class);
    when(flowNodeInstanceSearchClient.withSecurityContext(any()))
        .thenReturn(flowNodeInstanceSearchClient);
    variableSearchClient = mock(VariableSearchClient.class);
    when(variableSearchClient.withSecurityContext(any())).thenReturn(variableSearchClient);
    securityContextProvider = mock(SecurityContextProvider.class);
    authentication = mock(Authentication.class);
    services =
        new UserTaskServices(
            mock(BrokerClient.class),
            securityContextProvider,
            client,
            formSearchClient,
            flowNodeInstanceSearchClient,
            variableSearchClient,
            authentication);
  }

  @Test
  public void shouldReturnUserTasks() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchUserTasks(any())).thenReturn(result);

    final UserTaskFilter filter = new Builder().build();
    final var searchQuery = SearchQueryBuilders.userTaskSearchQuery((b) -> b.filter(filter));

    // when
    final SearchQueryResult<UserTaskEntity> searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnSingleUserTask() {
    // given
    final var entity = mock(UserTaskEntity.class);
    when(entity.bpmnProcessId()).thenReturn("bpid");
    when(client.searchUserTasks(any())).thenReturn(wrapWithSearchQueryResult(entity));
    authorizeReadUserTasksForProcess(true, "bpid");

    // when
    final var searchQueryResult = services.getByKey(1L);

    // then
    assertThat(searchQueryResult).isEqualTo(entity);
  }

  @Test
  public void shouldReturnUserTaskForm() {
    // given
    final long formKey = 123L;
    final var entity = mock(UserTaskEntity.class);
    when(entity.bpmnProcessId()).thenReturn("bpid");
    when(entity.formKey()).thenReturn(formKey);
    final var form = mock(FormEntity.class);
    when(formSearchClient.searchForms(formSearchQuery(q -> q.filter(f -> f.formKeys(formKey)))))
        .thenReturn(wrapWithSearchQueryResult(form));
    when(client.searchUserTasks(any())).thenReturn(wrapWithSearchQueryResult(entity));
    authorizeReadUserTasksForProcess(true, "bpid");

    // when
    final var result = services.getUserTaskForm(1L);

    // then
    assertThat(result).isPresent();
    assertThat(result.get()).isEqualTo(form);
  }

  @Test
  public void shouldReturnEmptyWhenUserTaskHasNoFormKey() {
    // given
    final var entity = mock(UserTaskEntity.class);
    when(entity.formKey()).thenReturn(null);
    when(entity.bpmnProcessId()).thenReturn("bpid");
    when(client.searchUserTasks(any())).thenReturn(wrapWithSearchQueryResult(entity));
    authorizeReadUserTasksForProcess(true, "bpid");

    // when
    final var result = services.getUserTaskForm(1L);

    // then
    assertThat(result).isEmpty();
  }

  @Test
  public void getByKeyShouldThrowExceptionWhenNotAuthorized() {
    // given
    final var entity = mock(UserTaskEntity.class);
    when(entity.bpmnProcessId()).thenReturn("bpid");
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array());
    when(client.searchUserTasks(any())).thenReturn(result);
    authorizeReadUserTasksForProcess(false, "bpid");

    // when
    final Executable executable = () -> services.getByKey(1L);

    // then
    assertThrows(ForbiddenException.class, executable);
    verify(client).searchUserTasks(any());
    verify(securityContextProvider)
        .isAuthorized(
            "bpid", authentication, Authorization.of(a -> a.processDefinition().readUserTask()));
  }

  @Test
  public void searchVariablesShouldThrowExceptionWhenNotAuthorized() {
    // given
    final var entity = mock(UserTaskEntity.class);
    when(entity.bpmnProcessId()).thenReturn("bpid");
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array());
    when(client.searchUserTasks(any())).thenReturn(result);
    authorizeReadUserTasksForProcess(false, "bpid");

    // when
    final Executable executable =
        () -> services.searchUserTaskVariables(1L, variableSearchQuery().build());

    // then
    assertThrows(ForbiddenException.class, executable);
    verify(client).searchUserTasks(any());
    verify(securityContextProvider)
        .isAuthorized(
            "bpid", authentication, Authorization.of(a -> a.processDefinition().readUserTask()));
    verify(flowNodeInstanceSearchClient, never()).searchFlowNodeInstances(any());
    verify(variableSearchClient, never()).searchVariables(any());
  }

  @Test
  public void shouldReturnUserTaskVariables() {
    // given
    final var entity = mock(UserTaskEntity.class);
    when(entity.bpmnProcessId()).thenReturn("bpid");
    final long flowNodeInstanceKey = 100L;
    when(entity.flowNodeInstanceId()).thenReturn(flowNodeInstanceKey);
    final var flowNodeInstanceEntity = mock(FlowNodeInstanceEntity.class);
    when(flowNodeInstanceEntity.treePath()).thenReturn("1/2/3");
    when(client.searchUserTasks(any())).thenReturn(wrapWithSearchQueryResult(entity));
    when(flowNodeInstanceSearchClient.searchFlowNodeInstances(
            flownodeInstanceSearchQuery(
                q -> q.filter(f -> f.flowNodeInstanceKeys(flowNodeInstanceKey)))))
        .thenReturn(wrapWithSearchQueryResult(flowNodeInstanceEntity));
    final var variable = mock(VariableEntity.class);
    when(variableSearchClient.searchVariables(
            variableSearchQuery(q -> q.filter(f -> f.scopeKeys(1L, 2L, 3L)))))
        .thenReturn(wrapWithSearchQueryResult(variable));
    authorizeReadUserTasksForProcess(true, "bpid");

    // when
    final SearchQueryResult<VariableEntity> searchQueryResult =
        services.searchUserTaskVariables(999L, variableSearchQuery().build());

    // then
    assertThat(searchQueryResult.items()).containsOnly(variable);
  }

  private void authorizeReadUserTasksForProcess(final boolean authorized, final String processId) {
    when(securityContextProvider.isAuthorized(
            processId, authentication, Authorization.of(a -> a.processDefinition().readUserTask())))
        .thenReturn(authorized);
  }

  private <T> SearchQueryResult<T> wrapWithSearchQueryResult(final T... entities) {
    return new SearchQueryResult<>(entities.length, List.of(entities), Arrays.array());
  }
}
