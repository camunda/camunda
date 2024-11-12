/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.filter.DateValueFilterTransformer.DateFieldFilter;
import io.camunda.search.entities.IncidentEntity.ErrorType;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.filter.DateValueFilter;
import io.camunda.search.filter.IncidentFilter;
import java.util.List;

public class IncidentFilterTransformer implements FilterTransformer<IncidentFilter> {

  private final ServiceTransformers transformers;

  public IncidentFilterTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
  }

  @Override
  public SearchQuery toSearchQuery(final IncidentFilter filter) {
    final var keyQuery = getKeyQuery(filter.incidentKeys());
    final var processDefinitionKeyQuery =
        getProcessDefinitionKeyQuery(filter.processDefinitionKeys());
    final var processDefinitionIdQuery = getProcessDefinitionIds(filter.processDefinitionIds());
    final var processInstanceKeyQuery = getProcessInstanceKeyQuery(filter.processInstanceKeys());
    final var errorTypeQuery = getErrorTypeQuery(filter.errorTypes());
    final var errorMessageQuery = getErrorMessageQuery(filter.errorMessages());
    final var flowNodeIdQuery = getFlowNodeIdQuery(filter.flowNodeIds());
    final var flowNodeInstanceKeyQuery = getFlowNodeInstanceKeyQuery(filter.flowNodeInstanceKeys());
    final var creationTimeQuery = getCreationTimeQuery(filter.creationTime());
    final var stateQuery = getStateQuery(filter.states());
    final var jobKeyQuery = getJobKeyQuery(filter.jobKeys());
    final var treePathQuery = getTreePathQuery(filter.treePaths());
    final var tenantIdQuery = getTenantIdQuery(filter.tenantIds());

    return and(
        keyQuery,
        processDefinitionKeyQuery,
        processDefinitionIdQuery,
        processInstanceKeyQuery,
        errorTypeQuery,
        errorMessageQuery,
        flowNodeIdQuery,
        flowNodeInstanceKeyQuery,
        creationTimeQuery,
        stateQuery,
        jobKeyQuery,
        treePathQuery,
        tenantIdQuery);
  }

  @Override
  public List<String> toIndices(final IncidentFilter filter) {
    return List.of("operate-incident-8.3.1_alias");
  }

  private SearchQuery getTenantIdQuery(final List<String> tenantIds) {
    return stringTerms("tenantId", tenantIds);
  }

  private SearchQuery getJobKeyQuery(final List<Long> jobKeys) {
    return longTerms("jobKey", jobKeys);
  }

  private SearchQuery getStateQuery(final List<IncidentState> states) {
    return stringTerms("state", states != null ? states.stream().map(Enum::name).toList() : null);
  }

  private SearchQuery getCreationTimeQuery(final DateValueFilter filter) {
    if (filter != null) {
      final var transformer = transformers.getFilterTransformer(DateValueFilter.class);
      return transformer.apply(new DateFieldFilter("creationTime", filter));
    }
    return null;
  }

  private SearchQuery getProcessDefinitionIds(final List<String> bpmnProcessIds) {
    return stringTerms("bpmnProcessId", bpmnProcessIds);
  }

  private SearchQuery getFlowNodeInstanceKeyQuery(final List<Long> flowNodeInstanceKeys) {
    return longTerms("flowNodeInstanceKey", flowNodeInstanceKeys);
  }

  private SearchQuery getFlowNodeIdQuery(final List<String> flowNodeIds) {
    return stringTerms("flowNodeId", flowNodeIds);
  }

  private SearchQuery getErrorTypeQuery(final List<ErrorType> errorTypes) {
    return stringTerms(
        "errorType", errorTypes != null ? errorTypes.stream().map(Enum::name).toList() : null);
  }

  private SearchQuery getProcessInstanceKeyQuery(final List<Long> processInstanceKeys) {
    return longTerms("processInstanceKey", processInstanceKeys);
  }

  private SearchQuery getProcessDefinitionKeyQuery(final List<Long> processDefinitionKeys) {
    return longTerms("processDefinitionKey", processDefinitionKeys);
  }

  private SearchQuery getErrorMessageQuery(final List<String> errorMessages) {
    return stringTerms("errorMessage", errorMessages);
  }

  private SearchQuery getKeyQuery(final List<Long> keys) {
    return longTerms("key", keys);
  }

  private SearchQuery getTreePathQuery(final List<String> treePaths) {
    return stringTerms("treePath", treePaths);
  }
}
