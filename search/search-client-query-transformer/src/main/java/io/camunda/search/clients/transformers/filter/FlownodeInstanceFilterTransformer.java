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
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeState;
import io.camunda.search.entities.FlowNodeInstanceEntity.FlowNodeType;
import io.camunda.search.filter.FlowNodeInstanceFilter;
import java.util.List;

public class FlownodeInstanceFilterTransformer
    implements FilterTransformer<FlowNodeInstanceFilter> {

  @Override
  public SearchQuery toSearchQuery(final FlowNodeInstanceFilter filter) {
    return and(
        longTerms("key", filter.flowNodeInstanceKeys()),
        longTerms("processInstanceKey", filter.processInstanceKeys()),
        longTerms("processDefinitionKey", filter.processDefinitionKeys()),
        stringTerms("bpmnProcessId", filter.processDefinitionIds()),
        getStateQuery(filter.states()),
        getTypeQuery(filter.types()),
        stringTerms("flowNodeId", filter.flowNodeIds()),
        stringTerms("treePath", filter.treePaths()),
        filter.hasIncident() != null ? term("incident", filter.hasIncident()) : null,
        longTerms("incidentKey", filter.incidentKeys()),
        stringTerms("tenantId", filter.tenantIds()));
  }

  @Override
  public List<String> toIndices(final FlowNodeInstanceFilter filter) {
    return List.of("operate-flownode-instance-8.3.1_alias");
  }

  private SearchQuery getStateQuery(final List<FlowNodeState> states) {
    return stringTerms("state", states != null ? states.stream().map(Enum::name).toList() : null);
  }

  private SearchQuery getTypeQuery(final List<FlowNodeType> types) {
    return stringTerms("type", types != null ? types.stream().map(Enum::name).toList() : null);
  }
}
