/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.service.search.filter.DateValueFilter;
import io.camunda.service.search.filter.IncidentFilter;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.service.transformers.filter.DateValueFilterTransformer.DateFieldFilter;
import java.util.List;

public class IncidentFilterTransformer implements FilterTransformer<IncidentFilter> {

  private final ServiceTransformers transformers;

  public IncidentFilterTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
  }

  @Override
  public SearchQuery toSearchQuery(final IncidentFilter filter) {
    final var keyQuery = getKeyQuery(filter.keys());
    final var processDefinitionKeyQuery =
        getProcessDefinitionKeyQuery(filter.processDefinitionKeys());
    final var processInstanceKeyQuery = getProcessInstanceKeyQuery(filter.processInstanceKeys());
    final var typeQuery = getTypeQuery(filter.types());
    final var flowNodeIdQuery = getFlowNodeIdQuery(filter.flowNodeIds());
    final var flowNodeInstanceIdQuery = getFlowNodeInstanceIdQuery(filter.flowNodeInstanceIds());
    final var creationTimeQuery = getCreationTimeQuery(filter.creationTimeFilter());
    final var stateQuery = getStateQuery(filter.states());
    final var jobKeyQuery = getJobKeyQuery(filter.jobKeys());
    final var tenantIdQuery = getTenantIdQuery(filter.tenantIds());
    final var hasActiveOperationQuery = getHasActiveOperationQuery(filter.hasActiveOperation());

    return and(
        keyQuery,
        processDefinitionKeyQuery,
        processInstanceKeyQuery,
        typeQuery,
        flowNodeIdQuery,
        flowNodeInstanceIdQuery,
        creationTimeQuery,
        stateQuery,
        jobKeyQuery,
        tenantIdQuery,
        hasActiveOperationQuery);
  }

  @Override
  public List<String> toIndices(final IncidentFilter filter) {
    return List.of("operate-incident-8.3.0_alias");
  }

  private SearchQuery getHasActiveOperationQuery(final boolean hasActiveOperation) {
    if (hasActiveOperation) {
      return term("hasActiveOperation", false);
    }
    return null;
  }

  private SearchQuery getTenantIdQuery(final List<String> tenantIds) {
    return stringTerms("tenantId", tenantIds);
  }

  private SearchQuery getJobKeyQuery(final List<Long> jobKeys) {
    return longTerms("jobKey", jobKeys);
  }

  private SearchQuery getStateQuery(final List<String> states) {
    return stringTerms("state", states);
  }

  private SearchQuery getCreationTimeQuery(final DateValueFilter creationTimeFilter) {
    if (creationTimeFilter != null) {
      final var transformer = getDateValueFilterTransformer();
      return transformer.apply(new DateFieldFilter("creationTime", creationTimeFilter));
    }
    return null;
  }

  private SearchQuery getFlowNodeInstanceIdQuery(final List<String> flowNodeInstanceIds) {
    return stringTerms("flowNodeInstanceId", flowNodeInstanceIds);
  }

  private SearchQuery getFlowNodeIdQuery(final List<String> flowNodeIds) {
    return stringTerms("flowNodeId", flowNodeIds);
  }

  private SearchQuery getTypeQuery(final List<String> types) {
    return stringTerms("type", types);
  }

  private SearchQuery getProcessInstanceKeyQuery(final List<Long> processInstanceKeys) {
    return longTerms("processInstanceKey", processInstanceKeys);
  }

  private SearchQuery getProcessDefinitionKeyQuery(final List<Long> processDefinitionKeys) {
    return longTerms("processDefinitionKey", processDefinitionKeys);
  }

  private SearchQuery getKeyQuery(final List<Long> keys) {
    return longTerms("key", keys);
  }

  private FilterTransformer<DateFieldFilter> getDateValueFilterTransformer() {
    return transformers.getFilterTransformer(DateValueFilter.class);
  }
}
