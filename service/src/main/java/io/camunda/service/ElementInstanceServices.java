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
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.filter.FilterBuilders;
import io.camunda.search.filter.Operation;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.service.cache.ProcessCache;
import io.camunda.service.cache.ProcessCacheItem;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerSetVariablesRequest;
import io.camunda.zeebe.protocol.impl.record.value.variable.VariableDocumentRecord;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class ElementInstanceServices
    extends SearchQueryService<
        ElementInstanceServices, FlowNodeInstanceQuery, FlowNodeInstanceEntity> {

  private static final String FNI_ELEMENT_INSTANCE_PATTERN = "*FNI_%d*";
  private final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient;
  private final ProcessCache processCache;
  private final IncidentServices incidentServices;
  private final JobServices<?> jobServices;

  public ElementInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient,
      final ProcessCache processCache,
      final IncidentServices incidentServices,
      final JobServices<?> jobServices,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.flowNodeInstanceSearchClient = flowNodeInstanceSearchClient;
    this.processCache = processCache;
    this.incidentServices = incidentServices;
    this.jobServices = jobServices;
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
                            withAuthorization(
                                ELEMENT_INSTANCE_READ_AUTHORIZATION,
                                FlowNodeInstanceEntity::processDefinitionId)))
                    .getFlowNodeInstance(key));

    final var cachedItem = processCache.getCacheItem(result.processDefinitionKey());
    return toCacheEnrichedFlowNodeInstanceEntity(result, cachedItem);
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

  public Map<String, Object> getElementInstanceInspection(
      final Long elementInstanceKey, final CamundaAuthentication authentication) {
    final var elementInstance = getByKey(elementInstanceKey, authentication);
    final List<Map<String, Object>> waitStates = new ArrayList<>();

    // Check if the element type is SERVICE_TASK (which maps to JOB type)
    if (elementInstance.type() == FlowNodeType.SERVICE_TASK) {
      // Search for jobs associated with this element instance
      final var jobQuery =
          JobQuery.of(
              b ->
                  b.filter(
                      FilterBuilders.job()
                          .elementInstanceKeyOperations(Operation.eq(elementInstanceKey))
                          .build()));

      final var jobs = jobServices.search(jobQuery, authentication);

      for (final JobEntity job : jobs.items()) {
        final Map<String, Object> details = new HashMap<>();
        details.put("jobKey", String.valueOf(job.jobKey()));
        details.put("jobType", job.type());
        details.put("jobKind", job.kind().name());

        final Map<String, Object> waitState = new HashMap<>();
        waitState.put("type", "JOB");
        waitState.put("details", details);

        waitStates.add(waitState);
      }
    }

    final Map<String, Object> result = new HashMap<>();
    result.put("elementInstanceKey", String.valueOf(elementInstanceKey));
    result.put("elementId", elementInstance.flowNodeId());
    result.put("elementType", elementInstance.type().name());
    result.put("waitStates", waitStates);

    return result;
  }

  public Map<String, Object> searchElementInstanceInspections(
      final List<Long> elementInstanceKeys, final CamundaAuthentication authentication) {
    final List<Map<String, Object>> items = new ArrayList<>();

    for (final Long key : elementInstanceKeys) {
      try {
        items.add(getElementInstanceInspection(key, authentication));
      } catch (final Exception e) {
        // Skip element instances that cannot be found or accessed
      }
    }

    final Map<String, Object> result = new HashMap<>();
    result.put("items", items);
    result.put("total", items.size());

    return result;
  }

  public record SetVariablesRequest(
      long elementInstanceKey,
      Map<String, Object> variables,
      Boolean local,
      Long operationReference) {}
}
