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
import io.camunda.search.page.SearchQueryPage;
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
import io.camunda.zeebe.gateway.validation.VariableNameLengthValidator;
import io.camunda.zeebe.protocol.impl.record.value.usertask.UserTaskRecord;
import io.camunda.zeebe.protocol.record.intent.UserTaskIntent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
  private final int maxVariableNameLength;

  public UserTaskServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final UserTaskSearchClient userTaskSearchClient,
      final FormServices formServices,
      final ElementInstanceServices elementInstanceServices,
      final VariableServices variableServices,
      final AuditLogServices auditLogServices,
      final ProcessCache processCache,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    this(
        brokerClient,
        securityContextProvider,
        userTaskSearchClient,
        formServices,
        elementInstanceServices,
        variableServices,
        auditLogServices,
        processCache,
        executorProvider,
        brokerRequestAuthorizationConverter,
        VariableNameLengthValidator.DEFAULT_MAX_NAME_FIELD_LENGTH);
  }

  public UserTaskServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final UserTaskSearchClient userTaskSearchClient,
      final FormServices formServices,
      final ElementInstanceServices elementInstanceServices,
      final VariableServices variableServices,
      final AuditLogServices auditLogServices,
      final ProcessCache processCache,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter,
      final int maxVariableNameLength) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.userTaskSearchClient = userTaskSearchClient;
    this.formServices = formServices;
    this.elementInstanceServices = elementInstanceServices;
    this.variableServices = variableServices;
    this.auditLogServices = auditLogServices;
    this.processCache = processCache;
    this.maxVariableNameLength = maxVariableNameLength;
  }

  @Override
  public SearchQueryResult<UserTaskEntity> search(
      final UserTaskQuery query,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return search(
        query,
        securityContextProvider.provideSecurityContext(authentication, USER_TASK_AUTHORIZATIONS),
        physicalTenantId);
  }

  private SearchQueryResult<UserTaskEntity> search(
      final UserTaskQuery query,
      final SecurityContext securityContext,
      final String physicalTenantId) {
    final var result =
        executeSearchRequest(
            () ->
                userTaskSearchClient
                    .withSecurityContext(securityContext)
                    .withPhysicalTenant(physicalTenantId)
                    .searchUserTasks(query));

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
      final Function<Builder, ObjectBuilder<UserTaskQuery>> fn,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return search(userTaskSearchQuery(fn), authentication, physicalTenantId);
  }

  public CompletableFuture<UserTaskRecord> assignUserTask(
      final long userTaskKey,
      final String assignee,
      final String action,
      final boolean allowOverride,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return sendBrokerRequest(
        new BrokerUserTaskAssignmentRequest(
            userTaskKey,
            assignee,
            action,
            allowOverride ? UserTaskIntent.ASSIGN : UserTaskIntent.CLAIM),
        authentication);
  }

  public CompletableFuture<UserTaskRecord> completeUserTask(
      final long userTaskKey,
      final Map<String, Object> variables,
      final String action,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return sendBrokerRequest(
        new BrokerUserTaskCompletionRequest(
            userTaskKey, getDocumentOrEmpty(variables), action, maxVariableNameLength),
        authentication);
  }

  public CompletableFuture<UserTaskRecord> unassignUserTask(
      final long userTaskKey,
      final String action,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return sendBrokerRequest(
        new BrokerUserTaskAssignmentRequest(userTaskKey, "", action, UserTaskIntent.ASSIGN),
        authentication);
  }

  public CompletableFuture<UserTaskRecord> updateUserTask(
      final long userTaskKey,
      final UserTaskRecord changeset,
      final String action,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return sendBrokerRequest(
        new BrokerUserTaskUpdateRequest(userTaskKey, changeset, action), authentication);
  }

  public UserTaskEntity getByKey(
      final long userTaskKey,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    final var result =
        executeSearchRequest(
            () ->
                userTaskSearchClient
                    .withSecurityContext(
                        securityContextProvider.provideSecurityContext(
                            authentication, USER_TASK_AUTHORIZATIONS))
                    .withPhysicalTenant(physicalTenantId)
                    .getUserTask(userTaskKey));

    final var cachedItem = processCache.getCacheItem(result.processDefinitionKey());
    return toCacheEnrichedUserTaskEntity(result, cachedItem);
  }

  public Optional<FormEntity> getUserTaskForm(
      final long userTaskKey,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return Optional.ofNullable(getByKey(userTaskKey, authentication, physicalTenantId))
        .map(UserTaskEntity::formKey)
        .map(
            formKey ->
                formServices.getByKey(
                    formKey, CamundaAuthentication.anonymous(), physicalTenantId));
  }

  public SearchQueryResult<VariableEntity> searchUserTaskVariables(
      final long userTaskKey,
      final VariableQuery variableQuery,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {

    // Fetch the user task by key
    final var userTask = getByKey(userTaskKey, authentication, physicalTenantId);

    // Retrieve the tree path for the flow node instance associated to the user task
    final String treePath = fetchFlowNodeTreePath(userTask.elementInstanceKey(), physicalTenantId);

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
            variableServices.search(
                variableQueryWithTreePathFilter,
                CamundaAuthentication.anonymous(),
                physicalTenantId));
  }

  public SearchQueryResult<VariableEntity> searchUserTaskEffectiveVariables(
      final long userTaskKey,
      final VariableQuery variableQuery,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {

    final var userTask = getByKey(userTaskKey, authentication, physicalTenantId);

    final String treePath = fetchFlowNodeTreePath(userTask.elementInstanceKey(), physicalTenantId);

    final List<Long> treePathList =
        treePath != null
            ? Arrays.stream(treePath.split("/")).map(Long::valueOf).toList()
            : Collections.emptyList();

    // Early exit: if there is no tree path or it only contains the user task itself,
    // there is no hierarchy and no deduplication is needed. Delegate to the standard
    // searchUserTaskVariables, but strip cursors since this endpoint does not support
    // cursor-based pagination (in the general case, we perform in-memory deduplication).
    if (treePathList.size() <= 1) {
      final var result =
          searchUserTaskVariables(userTaskKey, variableQuery, authentication, physicalTenantId);
      return new SearchQueryResult<>(
          result.total(), result.hasMoreTotalItems(), result.items(), null, null);
    }

    final var unlimitedQuery =
        variableSearchQuery(
            q ->
                q.filter(f -> f.copyFrom(variableQuery.filter()).scopeKeys(treePathList))
                    .sort(variableQuery.sort())
                    .unlimited());

    final var allVariables =
        executeSearchRequest(
            () ->
                variableServices.search(
                    unlimitedQuery, CamundaAuthentication.anonymous(), physicalTenantId));

    // Deduplicate variables by name, keeping the one from the innermost scope.
    // Since this is a sorted result set by the user's criteria, we iterate through
    // it in order and use a LinkedHashMap to preserve that order while deduplicating.
    final List<VariableEntity> dedupedVariables =
        deduplicateVariablesByScope(allVariables.items(), treePathList);

    // Apply offset-based pagination to the deduplicated and sorted result.
    // Cursor-based pagination (after/before) is not supported for this endpoint because
    // deduplication and sorting are performed in-memory after fetching from the search backend.
    final var page = variableQuery.page();
    final int size = page.size() != null ? page.size() : SearchQueryPage.DEFAULT_SIZE;
    final int total = dedupedVariables.size();
    final int from = page.from() != null ? page.from() : 0;
    final int end = Math.min(from + size, total);
    final List<VariableEntity> pageItems =
        from < total ? new ArrayList<>(dedupedVariables.subList(from, end)) : List.of();

    return new SearchQueryResult<>(total, false, pageItems, null, null);
  }

  /**
   * Deduplicates variables by name, keeping the variable from the innermost scope (closest to the
   * user task). The variables list is already sorted according to the user's criteria. We iterate
   * through them in order, and for each variable name, we keep the one from the closest scope. If a
   * variable from a closer scope is encountered later, we replace the previous one and move it to
   * the end of the LinkedHashMap, preserving the relative order while ensuring innermost scope
   * wins.
   */
  private List<VariableEntity> deduplicateVariablesByScope(
      final List<VariableEntity> variables, final List<Long> treePathList) {

    // Create a map from scope key to its depth (distance from root).
    // Higher depth = closer to innermost (user task).
    final Map<Long, Integer> scopeDepth = new HashMap<>();
    for (int i = 0; i < treePathList.size(); i++) {
      scopeDepth.put(treePathList.get(i), i);
    }

    // Iterate through sorted variables and deduplicate by name,
    // keeping the one from the closest scope (highest depth).
    final var dedupedMap = new LinkedHashMap<String, VariableEntity>();

    for (final VariableEntity variable : variables) {
      final var currentDepth = scopeDepth.get(variable.scopeKey());
      final var existing = dedupedMap.get(variable.name());

      if (existing == null) {
        // First time seeing this variable name, add it
        dedupedMap.put(variable.name(), variable);
      } else {
        final var existingDepth = scopeDepth.get(existing.scopeKey());
        if (currentDepth > existingDepth) {
          // This variable is from a closer scope, replace the existing one and put it to the end of
          // the LinkedHashMap.
          dedupedMap.putLast(variable.name(), variable);
        }
        // else: existing variable is from a closer or equal scope, keep it
      }
    }

    return new ArrayList<>(dedupedMap.values());
  }

  public SearchQueryResult<AuditLogEntity> searchUserTaskAuditLogs(
      final long userTaskKey,
      final AuditLogQuery auditLogQuery,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    getByKey(
        userTaskKey, authentication, physicalTenantId); // Ensure user task exists and is accessible

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
            auditLogServices.search(
                auditLogWithUserTaskKeyFilter,
                CamundaAuthentication.anonymous(),
                physicalTenantId));
  }

  private String fetchFlowNodeTreePath(
      final long flowNodeInstanceKey, final String physicalTenantId) {
    return executeSearchRequest(
            () ->
                elementInstanceServices.getByKey(
                    flowNodeInstanceKey, CamundaAuthentication.anonymous(), physicalTenantId))
        .treePath();
  }
}
