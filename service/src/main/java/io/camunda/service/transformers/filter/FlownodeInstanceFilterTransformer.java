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
import io.camunda.service.search.filter.FlowNodeInstanceFilter;
import java.util.List;

public class FlownodeInstanceFilterTransformer
    implements FilterTransformer<FlowNodeInstanceFilter> {

  @Override
  public SearchQuery toSearchQuery(final FlowNodeInstanceFilter filter) {
    return and(
        longTerms("key", filter.flowNodeInstanceKeys()),
        longTerms("processInstanceKey", filter.processInstanceKeys()),
        longTerms("processDefinitionKey", filter.processDefinitionKeys()),
        stringTerms("state", filter.states()),
        stringTerms("type", filter.types()),
        stringTerms("flowNodeId", filter.flowNodeIds()),
        stringTerms("flowNodeName", filter.flowNodeNames()),
        stringTerms("treePath", filter.treePaths()),
        term("incident", filter.incident()),
        longTerms("incidentKey", filter.incidentKeys()),
        stringTerms("tenantId", filter.tenantIds()));
  }

  @Override
  public List<String> toIndices(final FlowNodeInstanceFilter filter) {
    return List.of("operate-flownode-instance-8.3.1_alias");
  }
}
