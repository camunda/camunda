/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.ELEMENT_INSTANCE_READ_AUTHORIZATION;

import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.service.cache.ProcessCache;
import io.camunda.service.cache.ProcessCacheItem;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerSetVariablesRequest;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class ElementInstanceServices
    extends SearchQueryService<
        ElementInstanceServices, FlowNodeInstanceQuery, FlowNodeInstanceEntity> {

  private final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient;
  private final ProcessCache processCache;

  public ElementInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient,
      final ProcessCache processCache,
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.flowNodeInstanceSearchClient = flowNodeInstanceSearchClient;
    this.processCache = processCache;
  }

  @Override
  public ElementInstanceServices withAuthentication(final CamundaAuthentication authentication) {
    return new ElementInstanceServices(
        brokerClient,
        securityContextProvider,
        flowNodeInstanceSearchClient,
        processCache,
        authentication);
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> search(final FlowNodeInstanceQuery query) {
    return search(
        query,
        securityContextProvider.provideSecurityContext(
            authentication, ELEMENT_INSTANCE_READ_AUTHORIZATION));
  }

  public FlowNodeInstanceEntity getByKey(final Long key) {
    final var result =
        executeSearchRequest(
            () ->
                flowNodeInstanceSearchClient
                    .withSecurityContext(
                        securityContextProvider.provideSecurityContext(
                            authentication,
                            withAuthorization(
                                ELEMENT_INSTANCE_READ_AUTHORIZATION,
                                FlowNodeInstanceEntity::processDefinitionId)))
                    .getFlowNodeInstance(key));

    final var cachedItem = processCache.getCacheItem(result.processDefinitionKey());
    return toCacheEnrichedFlowNodeInstanceEntity(result, cachedItem);
  }

  private SearchQueryResult<FlowNodeInstanceEntity> search(
      final FlowNodeInstanceQuery query, final SecurityContext securityContext) {

    final var filter = query.filter();
    if (filter.scopeKeys() != null && !filter.scopeKeys().isEmpty()) {
      return searchWithScopeKeyFallback(query, securityContext);
    }

    final var result =
        executeSearchRequest(
            () ->
                flowNodeInstanceSearchClient
                    .withSecurityContext(securityContext)
                    .searchFlowNodeInstances(query));

    return toCacheEnrichedResult(result);
  }

  /**
   * Executes a two-step fallback search when the filter includes scopeKeys.
   *
   * <p>This approach is necessary because the {@code scopeKey} field is not guaranteed to be
   * populated for all data, particularly in datasets created before version 8.8. Therefore, we
   * first attempt to resolve the scopeKey to a flow node instance. If none is found, we assume it's
   * a process instance key and retrieve its immediate children using level-based filtering. If an
   * element instance is found, we then search for its children using the treePath and level values.
   *
   * <p>Once {@code scopeKey} is universally populated in the index, this fallback logic can be
   * removed.
   */
  private SearchQueryResult<FlowNodeInstanceEntity> searchWithScopeKeyFallback(
      final FlowNodeInstanceQuery query, final SecurityContext securityContext) {

    final var filter = query.filter();
    final var firstResult =
        flowNodeInstanceSearchClient
            .withSecurityContext(securityContext)
            .searchFlowNodeInstances(
                FlowNodeInstanceQuery.of(
                    b -> b.filter(f -> f.flowNodeInstanceKeys(filter.scopeKeys()))));

    if (firstResult == null || firstResult.items().isEmpty()) {
      final var processInstanceQuery =
          FlowNodeInstanceQuery.of(
              b ->
                  b.filter(
                          f -> f.copyFrom(filter).processInstanceKeys(filter.scopeKeys()).levels(1))
                      .sort(query.sort())
                      .page(query.page()));

      final var result =
          executeSearchRequest(
              () ->
                  flowNodeInstanceSearchClient
                      .withSecurityContext(securityContext)
                      .searchFlowNodeInstances(processInstanceQuery));

      return toCacheEnrichedResult(result);
    } else {
      final var childLevel = firstResult.items().getFirst().level() + 1;
      final var treePath = firstResult.items().getFirst().treePath();
      final var elementInstanceQuery =
          FlowNodeInstanceQuery.of(
              b ->
                  b.filter(
                          f ->
                              f.copyFrom(filter)
                                  .treePaths(treePath)
                                  .levels(childLevel)
                                  .useTreePathPrefix(true))
                      .sort(query.sort())
                      .page(query.page()));

      final var result =
          executeSearchRequest(
              () ->
                  flowNodeInstanceSearchClient
                      .withSecurityContext(securityContext)
                      .searchFlowNodeInstances(elementInstanceQuery));

      return toCacheEnrichedResult(result);
    }
  }

  public CompletableFuture<VariableDocumentRecord> setVariables(
      final ElementInstanceServices.SetVariablesRequest request) {
    final var brokerRequest =
        new BrokerSetVariablesRequest()
            .setElementInstanceKey(request.elementInstanceKey())
            .setVariables(getDocumentOrEmpty(request.variables()))
            .setLocal(request.local());

    if (request.operationReference() != null) {
      brokerRequest.setOperationReference(request.operationReference());
    }
    return sendBrokerRequest(brokerRequest);
  }

  private SearchQueryResult<FlowNodeInstanceEntity> toCacheEnrichedResult(
      final SearchQueryResult<FlowNodeInstanceEntity> result) {
    final var processDefinitionKeys =
        result.items().stream()
            .filter(u -> !u.hasFlowNodeName())
            .map(FlowNodeInstanceEntity::processDefinitionKey)
            .collect(Collectors.toSet());

    if (processDefinitionKeys.isEmpty()) {
      return result;
    }

    final var cacheResult = processCache.getCacheItems(processDefinitionKeys);

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

  public record SetVariablesRequest(
      long elementInstanceKey,
      Map<String, Object> variables,
      Boolean local,
      Long operationReference) {}
}
