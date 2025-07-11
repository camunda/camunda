/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.with;
import static io.camunda.security.auth.Authorization.withResourceId;
import static io.camunda.service.authorization.Authorizations.ELEMENT_INSTANCE_READ_AUTHORIZATION;

import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.service.cache.ProcessCache;
import io.camunda.service.cache.ProcessCacheResult;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerSetVariablesRequest;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            authentication, with(ELEMENT_INSTANCE_READ_AUTHORIZATION)));
  }

  public FlowNodeInstanceEntity getByKey(final Long key) {
    final var elementInstance =
        flowNodeInstanceSearchClient
            .withSecurityContext(
                securityContextProvider.provideSecurityContext(
                    authentication,
                    withResourceId(
                        ELEMENT_INSTANCE_READ_AUTHORIZATION,
                        FlowNodeInstanceEntity::processDefinitionId)))
            .getFlowNodeInstanceByKey(key);

    final var cacheResult = getCacheResult(elementInstance.processDefinitionKey());
    return enrichElementInstance(elementInstance, cacheResult);
  }

  private SearchQueryResult<FlowNodeInstanceEntity> search(
      final FlowNodeInstanceQuery query, final SecurityContext securityContext) {
    final var result =
        flowNodeInstanceSearchClient
            .withSecurityContext(securityContext)
            .searchFlowNodeInstances(query);

    return toCacheEnrichedResult(result);
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

    final var cacheResult = getCacheResult(processDefinitionKeys);
    return result.withItems(
        result.items().stream()
            .map(i -> enrichElementInstance(i, cacheResult))
            .collect(Collectors.toList()));
  }

  private ProcessCacheResult getCacheResult(final Long processDefinitionKey) {
    return getCacheResult(new HashSet<>(List.of(processDefinitionKey)));
  }

  private ProcessCacheResult getCacheResult(final Set<Long> processDefinitionKeys) {
    return processCache.getCacheItems(processDefinitionKeys);
  }

  private FlowNodeInstanceEntity enrichElementInstance(
      final FlowNodeInstanceEntity elementInstance, final ProcessCacheResult cacheResult) {
    return elementInstance.hasFlowNodeName()
        ? elementInstance
        : elementInstance.withFlowNodeName(
            cacheResult
                .getProcessItem(elementInstance.processDefinitionKey())
                .getElementName(elementInstance.flowNodeId()));
  }

  public record SetVariablesRequest(
      long elementInstanceKey,
      Map<String, Object> variables,
      Boolean local,
      Long operationReference) {}
}
