/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.intTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.filter.DateValueFilterTransformer.DateFieldFilter;
import io.camunda.search.filter.DateValueFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import java.util.List;

public final class ProcessInstanceFilterTransformer
    implements FilterTransformer<ProcessInstanceFilter> {

  private final ServiceTransformers transformers;

  public ProcessInstanceFilterTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
  }

  @Override
  public SearchQuery toSearchQuery(final ProcessInstanceFilter filter) {

    return and(
        getIsProcessInstanceQuery(),
        longTerms("key", filter.processInstanceKeys()),
        stringTerms("bpmnProcessId", filter.processDefinitionIds()),
        stringTerms("processName", filter.processDefinitionNames()),
        intTerms("processVersion", filter.processDefinitionVersions()),
        stringTerms("processVersionTag", filter.processDefinitionVersionTags()),
        longTerms("processDefinitionKey", filter.processDefinitionKeys()),
        longTerms("rootProcessInstanceKey", filter.rootProcessInstanceKeys()),
        longTerms("parentProcessInstanceKey", filter.parentProcessInstanceKeys()),
        longTerms("parentFlowNodeInstanceKey", filter.parentFlowNodeInstanceKeys()),
        stringTerms("treePath", filter.treePaths()),
        getDateQuery("startDate", filter.startDate()),
        getDateQuery("endDate", filter.endDate()),
        stringTerms("state", filter.states()),
        getIncidentQuery(filter.hasIncident()),
        stringTerms("tenantId", filter.tenantIds()));
  }

  @Override
  public List<String> toIndices(final ProcessInstanceFilter filter) {
    return List.of("operate-list-view-8.3.0_alias");
  }

  private SearchQuery getIsProcessInstanceQuery() {
    return term("joinRelation", "processInstance");
  }

  private SearchQuery getDateQuery(final String field, final DateValueFilter filter) {
    if (filter != null) {
      final var transformer = transformers.getFilterTransformer(DateValueFilter.class);
      return transformer.apply(new DateFieldFilter(field, filter));
    }
    return null;
  }

  private SearchQuery getIncidentQuery(final Boolean hasIncident) {
    if (hasIncident != null) {
      return term("incident", hasIncident);
    }
    return null;
  }
}
