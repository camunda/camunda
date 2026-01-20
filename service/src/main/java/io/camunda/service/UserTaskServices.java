/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.auditLogSearchQuery;
import static io.camunda.search.query.SearchQueryBuilders.userTaskSearchQuery;
import static io.camunda.search.query.SearchQueryBuilders.variableSearchQuery;
import static io.camunda.service.authorization.Authorizations.PROCESS_DEFINITION_READ_USER_TASK_AUTHORIZATION;
import static io.camunda.service.authorization.Authorizations.USER_TASK_READ_AUTHORIZATION;
import static io.camunda.service.authorization.Authorizations.USER_TASK_READ_BY_PROPERTIES_AUTHORIZATION;

import io.camunda.search.clients.UserTaskSearchClient;
import io.camunda.search.entities.AuditLogEntity;
import io.camunda.search.entities.FormEntity;
import io.camunda.search.entities.UserTaskEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.AuditLogQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserTaskQuery;
import io.camunda.search.query.UserTaskQuery.Builder;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.auth.condition.AuthorizationCondition;
import io.camunda.security.auth.condition.AuthorizationConditions;
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
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class UserTaskServices
    extends SearchQueryService<UserTaskServices, UserTaskQuery, UserTaskEntity> {

  private static final Predicate<UserTaskEntity> NEEDS_CACHE_ENRICHMENT =
      u -> !u.hasName() || !u.hasProcessName();

  private static final AuthorizationCondition USER_TASK_AUTHORIZATIONS =
      AuthorizationConditions.anyOf(
          PROCESS_DEFINITION_READ_USER_TASK_AUTHORIZATION.withResourceIdSupplier(
              UserTaskEntity::processDefinitionId),
          USER_TASK_READ_AUTHORIZATION.withResourceIdSupplier(
              ut -> String.valueOf(ut.userTaskKey())),
          USER_TASK_READ_BY_PROPERTIES_AUTHORIZATION);

  private final UserTaskSearchClient userTaskSearchClient;
  private final FormServices formServices;
  private final ElementInstanceServices elementInstanceServices;
  private final VariableServices variableServices;
  private final ProcessCache processCache;
  private final AuditLogServices auditLogServices;

  public UserTaskServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final UserTaskSearchClient userTaskSearchClient,
      final FormServices formServices,
      final ElementInstanceServices elementInstanceServices,
      final VariableServices variableServices,
      final AuditLogServices auditLogServices,
      final ProcessCache processCache,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.userTaskSearchClient = userTaskSearchClient;
    this.formServices = formServices;
    this.elementInstanceServices = elementInstanceServices;
    this.variableServices = variableServices;
    this.auditLogServices = auditLogServices;
    this.processCache = processCache;
  }

  @Override
  public UserTaskServices withAuthentication(final CamundaAuthentication authentication) {
    return new UserTaskServices(
        brokerClient,
        securityContextProvider,
        userTaskSearchClient,
        formServices,
        elementInstanceServices,
        variableServices,
        auditLogServices,
        processCache,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  @Override
  public SearchQueryResult<UserTaskEntity> search(final UserTaskQuery query) {
    return search(
        query,
        securityContextProvider.provideSecurityContext(authentication, USER_TASK_AUTHORIZATIONS));
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
            .filter(NEEDS_CACHE_ENRICHMENT)
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
      UserTaskEntity item, final ProcessCacheItem cachedItem) {

    if (!item.hasName()) {
      item = item.withName(cachedItem.getElementName(item.elementId()));
    }
    if (!item.hasProcessName()) {
      item = item.withProcessName(cachedItem.getProcessName());
    }

    return item;
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
                            authentication, USER_TASK_AUTHORIZATIONS))
                    .getUserTask(userTaskKey));

    final var cachedItem = processCache.getCacheItem(result.processDefinitionKey());
    return toCacheEnrichedUserTaskEntity(result, cachedItem);
  }

  public Optional<FormEntity> getUserTaskForm(final long userTaskKey) {
    return Optional.ofNullable(getByKey(userTaskKey))
        .map(UserTaskEntity::formKey)
        .map(
            formKey ->
                formServices
                    .withAuthentication(CamundaAuthentication.anonymous())
                    .getByKey(formKey));
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
            variableServices
                .withAuthentication(CamundaAuthentication.anonymous())
                .search(variableQueryWithTreePathFilter));
  }

  public SearchQueryResult<AuditLogEntity> searchUserTaskAuditLogs(
      final long userTaskKey, final AuditLogQuery auditLogQuery) {
    getByKey(userTaskKey); // Ensure user task exists and is accessible

    // Create an audit log query with user task key filter
    final var auditLogWithUserTaskKeyFilter =
        auditLogSearchQuery(
            q ->
                q.filter(auditLogQuery.filter().toBuilder().userTaskKeys(userTaskKey).build())
                    .sort(auditLogQuery.sort())
                    .page(auditLogQuery.page()));

    // Execute the search
    return executeSearchRequest(
        () ->
            auditLogServices
                // TODO: Implement authentication in issue:
                // https://github.com/camunda/camunda/issues/41205
                .withAuthentication(CamundaAuthentication.anonymous())
                .search(auditLogWithUserTaskKeyFilter));
  }

  private String fetchFlowNodeTreePath(final long flowNodeInstanceKey) {
    return executeSearchRequest(
            () ->
                elementInstanceServices
                    .withAuthentication(CamundaAuthentication.anonymous())
                    .getByKey(flowNodeInstanceKey))
        .treePath();
  }
}
