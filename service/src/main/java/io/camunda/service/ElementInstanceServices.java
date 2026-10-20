/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.variableSearchQuery;
import static io.camunda.security.core.auth.RequiredAuthorization.withRequiredAuthorization;
import static io.camunda.service.authorization.Authorizations.ELEMENT_INSTANCE_READ_AUTHORIZATION;

import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.clients.WaitStateSearchClient;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.entities.WaitStateEntity;
import io.camunda.search.filter.Operation;
import io.camunda.search.page.SearchQueryPage;
import io.camunda.search.query.ElementInstanceWaitStateQuery;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableQuery;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.core.auth.SecurityContext;
import io.camunda.service.cache.ProcessCache;
import io.camunda.service.cache.ProcessCacheItem;
import io.camunda.service.cache.ProcessCacheResult;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerSetVariablesRequest;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class ElementInstanceServices
    extends SearchQueryService<
        ElementInstanceServices, FlowNodeInstanceQuery, FlowNodeInstanceEntity> {

  private static final String FNI_ELEMENT_INSTANCE_PATTERN = "*FNI_%d*";
  private final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient;
  private final WaitStateSearchClient waitStateSearchClient;
  private final ProcessCache processCache;
  private final IncidentServices incidentServices;
  private final VariableServices variableServices;

  public ElementInstanceServices(
      final String physicalTenantId,
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient,
      final WaitStateSearchClient waitStateSearchClient,
      final ProcessCache processCache,
      final IncidentServices incidentServices,
      final VariableServices variableServices,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        physicalTenantId,
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.flowNodeInstanceSearchClient = flowNodeInstanceSearchClient;
    this.waitStateSearchClient = waitStateSearchClient;
    this.processCache = processCache;
    this.incidentServices = incidentServices;
    this.variableServices = variableServices;
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> search(
      final FlowNodeInstanceQuery query, final CamundaAuthentication authentication) {
    return search(
        query,
        securityContextProvider.provideSecurityContext(
            authentication, ELEMENT_INSTANCE_READ_AUTHORIZATION));
  }

  public FlowNodeInstanceEntity getByKey(
      final Long key, final CamundaAuthentication authentication) {
    final var result =
        executeSearchRequest(
            () ->
                flowNodeInstanceSearchClient
                    .withSecurityContext(
                        securityContextProvider.provideSecurityContext(
                            authentication,
                            withRequiredAuthorization(
                                ELEMENT_INSTANCE_READ_AUTHORIZATION,
                                FlowNodeInstanceEntity::processDefinitionId)))
                    .getFlowNodeInstance(key));

    final var cachedItem =
        result.processDefinitionKey() == null
            ? ProcessCacheItem.EMPTY
            : processCache.getCacheItem(result.processDefinitionKey());
    return toCacheEnrichedFlowNodeInstanceEntity(result, cachedItem);
  }

  /**
   * Searches the variables that an element instance sees at runtime: the variables of its own scope
   * and of all parent scopes up to the process instance. Each name appears once; the innermost
   * scope wins, as for the effective variables of a user task.
   */
  public SearchQueryResult<VariableEntity> searchEffectiveVariables(
      final long elementInstanceKey,
      final VariableQuery variableQuery,
      final CamundaAuthentication authentication) {
    // Ensures that the element instance exists and that the caller may read its process instance.
    final var elementInstance = getByKey(elementInstanceKey, authentication);
    final List<Long> scopeKeys =
        elementInstance.treePath() == null
            ? List.of(elementInstanceKey)
            : Arrays.stream(elementInstance.treePath().split("/")).map(Long::valueOf).toList();

    final var unlimitedQuery =
        variableSearchQuery(
            q ->
                q.filter(f -> f.copyFrom(variableQuery.filter()).scopeKeys(scopeKeys))
                    .sort(variableQuery.sort())
                    .unlimited());
    final var allVariables =
        executeSearchRequest(
            () -> variableServices.search(unlimitedQuery, CamundaAuthentication.anonymous()));
    final var effectiveVariables = deduplicateVariablesByScope(allVariables.items(), scopeKeys);

    // Deduplication runs in memory, so this search supports offset pagination only.
    final var page = variableQuery.page();
    final int size = page.size() != null ? page.size() : SearchQueryPage.DEFAULT_SIZE;
    final int total = effectiveVariables.size();
    final int from = page.from() != null ? page.from() : 0;
    final List<VariableEntity> pageItems =
        from < total
            ? new ArrayList<>(effectiveVariables.subList(from, Math.min(from + size, total)))
            : List.of();

    return new SearchQueryResult<>(total, false, pageItems, null, null);
  }

  /**
   * Keeps one variable per name, the one from the innermost scope, in the order of the sorted
   * search result. The scope keys run from the process instance to the element instance.
   */
  private static List<VariableEntity> deduplicateVariablesByScope(
      final List<VariableEntity> variables, final List<Long> scopeKeys) {
    final Map<Long, Integer> scopeDepth = new HashMap<>();
    for (int depth = 0; depth < scopeKeys.size(); depth++) {
      scopeDepth.put(scopeKeys.get(depth), depth);
    }

    final var variablesByName = new LinkedHashMap<String, VariableEntity>();
    for (final VariableEntity variable : variables) {
      final var existing = variablesByName.get(variable.name());
      if (existing == null) {
        variablesByName.put(variable.name(), variable);
      } else if (scopeDepth.getOrDefault(variable.scopeKey(), -1)
          > scopeDepth.getOrDefault(existing.scopeKey(), -1)) {
        variablesByName.putLast(variable.name(), variable);
      }
    }

    return new ArrayList<>(variablesByName.values());
  }

  private SearchQueryResult<FlowNodeInstanceEntity> search(
      final FlowNodeInstanceQuery query, final SecurityContext securityContext) {

    final var result =
        executeSearchRequest(
            () ->
                flowNodeInstanceSearchClient
                    .withSecurityContext(securityContext)
                    .searchFlowNodeInstances(query));

    return toCacheEnrichedResult(result);
  }

  public CompletableFuture<VariableDocumentRecord> setVariables(
      final ElementInstanceServices.SetVariablesRequest request,
      final CamundaAuthentication authentication) {
    final var brokerRequest =
        new BrokerSetVariablesRequest()
            .setElementInstanceKey(request.elementInstanceKey())
            .setVariables(getDocumentOrEmpty(request.variables()))
            .setLocal(request.local());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest, authentication);
  }

  private SearchQueryResult<FlowNodeInstanceEntity> toCacheEnrichedResult(
      final SearchQueryResult<FlowNodeInstanceEntity> result) {
    final var processDefinitionKeys =
        result.items().stream()
            .filter(u -> !u.hasFlowNodeName())
            .map(FlowNodeInstanceEntity::processDefinitionKey)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

    if (result.items().stream().allMatch(FlowNodeInstanceEntity::hasFlowNodeName)) {
      return result;
    }

    final var cacheResult =
        processDefinitionKeys.isEmpty()
            ? ProcessCacheResult.EMPTY
            : processCache.getCacheItems(processDefinitionKeys);

    return result.withItems(
        result.items().stream()
            .map(
                item ->
                    toCacheEnrichedFlowNodeInstanceEntity(
                        item, cacheResult.getProcessItem(item.processDefinitionKey())))
            .collect(Collectors.toList()));
  }

  private FlowNodeInstanceEntity toCacheEnrichedFlowNodeInstanceEntity(
      final FlowNodeInstanceEntity item, final ProcessCacheItem cachedItem) {
    return item.hasFlowNodeName()
        ? item
        : item.withFlowNodeName(cachedItem.getElementName(item.flowNodeId()));
  }

  public SearchQueryResult<IncidentEntity> searchIncidents(
      final long elementInstanceKey,
      final IncidentQuery query,
      final CamundaAuthentication authentication) {
    final var elementInstance = getByKey(elementInstanceKey, authentication);
    return incidentServices.search(
        IncidentQuery.of(
            b ->
                b.filter(
                        query.filter().toBuilder()
                            .treePathOperations(
                                Operation.like(
                                    String.format(
                                        FNI_ELEMENT_INSTANCE_PATTERN,
                                        elementInstance.flowNodeInstanceKey())))
                            .build())
                    .sort(query.sort())
                    .page(query.page())),
        authentication);
  }

  public SearchQueryResult<WaitStateEntity> searchWaitStates(
      final ElementInstanceWaitStateQuery query, final CamundaAuthentication authentication) {
    return executeSearchRequest(
        () ->
            waitStateSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, ELEMENT_INSTANCE_READ_AUTHORIZATION))
                .searchWaitStates(query));
  }

  public record SetVariablesRequest(
      long elementInstanceKey,
      Map<String, Object> variables,
      Boolean local,
      Long operationReference) {}
}
