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
import static io.camunda.search.query.SearchQueryBuilders.userTaskSearchQuery;
import static io.camunda.search.query.SearchQueryBuilders.variableSearchQuery;
import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.USER_TASK_READ_AUTHORIZATION;

import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.clients.FormSearchClient;
import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.clients.VariableSearchClient;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.UserTaskQuery.Builder;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.service.cache.ProcessCache;
import io.camunda.service.cache.ProcessCacheItem;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskAssignmentRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskCompletionRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerUserTaskUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class UserTaskServices
    extends SearchQueryService<UserTaskServices, UserTaskQuery, UserTaskEntity> {

  private final UserTaskSearchClient userTaskSearchClient;
  private final FormSearchClient formSearchClient;
  private final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient;
  private final VariableSearchClient variableSearchClient;
  private final ProcessCache processCache;

  public UserTaskServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final UserTaskSearchClient userTaskSearchClient,
      final FormSearchClient formSearchClient,
      final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient,
      final VariableSearchClient variableSearchClient,
      final ProcessCache processCache,
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.userTaskSearchClient = userTaskSearchClient;
    this.formSearchClient = formSearchClient;
    this.flowNodeInstanceSearchClient = flowNodeInstanceSearchClient;
    this.variableSearchClient = variableSearchClient;
    this.processCache = processCache;
  }

  @Override
  public UserTaskServices withAuthentication(final CamundaAuthentication authentication) {
    return new UserTaskServices(
        brokerClient,
        securityContextProvider,
        userTaskSearchClient,
        formSearchClient,
        flowNodeInstanceSearchClient,
        variableSearchClient,
        processCache,
        authentication);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> search(final UserTaskQuery query) {
    return search(
        query,
        securityContextProvider.provideSecurityContext(
            authentication, USER_TASK_READ_AUTHORIZATION));
  }

  private SearchQueryResult<UserTaskEntity> search(
      final UserTaskQuery query, final SecurityContext securityContext) {
    final var result =
        executeSearchRequest(
            () -> userTaskSearchClient.withSecurityContext(securityContext).searchUserTasks(query));

    return toCacheEnrichedResult(result);
  }

  private SearchQueryResult<UserTaskEntity> toCacheEnrichedResult(
      final SearchQueryResult<UserTaskEntity> result) {

    final var processDefinitionKeys =
        result.items().stream()
            .filter(u -> !u.hasName())
            .map(UserTaskEntity::processDefinitionKey)
            .collect(Collectors.toSet());

    if (processDefinitionKeys.isEmpty()) {
      return result;
    }

    final var cacheResult = processCache.getCacheItems(processDefinitionKeys);

    return result.withItems(
        result.items().stream()
            .map(
                item ->
                    toCacheEnrichedUserTaskEntity(
                        item, cacheResult.getProcessItem(item.processDefinitionKey())))
            .collect(Collectors.toList()));
  }

  private UserTaskEntity toCacheEnrichedUserTaskEntity(
      final UserTaskEntity item, final ProcessCacheItem cachedItem) {
    return item.hasName() ? item : item.withName(cachedItem.getElementName(item.elementId()));
  }

  public SearchQueryResult<UserTaskEntity> search(
      final Function<Builder, ObjectBuilder<UserTaskQuery>> fn) {
    return search(userTaskSearchQuery(fn));
  }

  public CompletableFuture<UserTaskRecord> assignUserTask(
      final long userTaskKey,
      final String assignee,
      final String action,
      final boolean allowOverride) {
    return sendBrokerRequest(
        new BrokerUserTaskAssignmentRequest(
            userTaskKey,
            assignee,
            action,
            allowOverride ? UserTaskIntent.ASSIGN : UserTaskIntent.CLAIM));
  }

  public CompletableFuture<UserTaskRecord> completeUserTask(
      final long userTaskKey, final Map<String, Object> variables, final String action) {
    return sendBrokerRequest(
        new BrokerUserTaskCompletionRequest(userTaskKey, getDocumentOrEmpty(variables), action));
  }

  public CompletableFuture<UserTaskRecord> unassignUserTask(
      final long userTaskKey, final String action) {
    return sendBrokerRequest(
        new BrokerUserTaskAssignmentRequest(userTaskKey, "", action, UserTaskIntent.ASSIGN));
  }

  public CompletableFuture<UserTaskRecord> updateUserTask(
      final long userTaskKey, final UserTaskRecord changeset, final String action) {
    return sendBrokerRequest(new BrokerUserTaskUpdateRequest(userTaskKey, changeset, action));
  }

  public UserTaskEntity getByKey(final long userTaskKey) {
    final var result =
        executeSearchRequest(
            () ->
                userTaskSearchClient
                    .withSecurityContext(
                        securityContextProvider.provideSecurityContext(
                            authentication,
                            withAuthorization(
                                USER_TASK_READ_AUTHORIZATION, UserTaskEntity::processDefinitionId)))
                    .getUserTask(userTaskKey));

    final var cachedItem = processCache.getCacheItem(result.processDefinitionKey());
    return toCacheEnrichedUserTaskEntity(result, cachedItem);
  }

  public Optional<FormEntity> getUserTaskForm(final long userTaskKey) {
    final Long formKey = getByKey(userTaskKey).formKey();
    if (formKey == null) {
      return Optional.empty();
    }
    return Optional.of(
        executeSearchRequest(
                () ->
                    formSearchClient
                        .withSecurityContext(
                            securityContextProvider.provideSecurityContext(authentication))
                        .searchForms(
                            formSearchQuery(
                                q -> q.filter(f -> f.formKeys(formKey)).singleResult())))
            .items()
            .getFirst());
  }

  public SearchQueryResult<VariableEntity> searchUserTaskVariables(
      final long userTaskKey, final VariableQuery variableQuery) {

    // Fetch the user task by key
    final var userTask = getByKey(userTaskKey);

    // Retrieve the tree path for the flow node instance associated to the user task
    final String treePath = fetchFlowNodeTreePath(userTask.elementInstanceKey());

    // Convert the tree path to a list of scope keys
    final List<Long> treePathList =
        treePath != null
            ? Arrays.stream(treePath.split("/")).map(Long::valueOf).toList()
            : Collections.emptyList();

    // Create a variable query with an additional filter for the scope keys
    final var variableQueryWithTreePathFilter =
        variableSearchQuery(
            q ->
                q.filter(f -> f.copyFrom(variableQuery.filter()).scopeKeys(treePathList))
                    .sort(variableQuery.sort())
                    .page(variableQuery.page()));

    // Execute the search
    return executeSearchRequest(
        () ->
            variableSearchClient
                .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
                .searchVariables(variableQueryWithTreePathFilter));
  }

  private String fetchFlowNodeTreePath(final long flowNodeInstanceKey) {
    return executeSearchRequest(
            () ->
                flowNodeInstanceSearchClient
                    .withSecurityContext(
                        securityContextProvider.provideSecurityContext(authentication))
                    .searchFlowNodeInstances(
                        flownodeInstanceSearchQuery(
                            q ->
                                q.filter(f -> f.flowNodeInstanceKeys(flowNodeInstanceKey))
                                    .singleResult())))
        .items()
        .getFirst()
        .treePath();
  }
}
