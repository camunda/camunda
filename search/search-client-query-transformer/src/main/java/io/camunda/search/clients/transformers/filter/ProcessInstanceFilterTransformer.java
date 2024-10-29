/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.dateTimeOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.intOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.longOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.filter.ProcessInstanceFilter;
import java.util.ArrayList;
import java.util.List;

public final class ProcessInstanceFilterTransformer
    implements FilterTransformer<ProcessInstanceFilter> {

  @Override
  public SearchQuery toSearchQuery(final ProcessInstanceFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    ofNullable(getIsProcessInstanceQuery()).ifPresent(queries::add);
    ofNullable(longOperations("key", filter.processInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(stringOperations("bpmnProcessId", filter.processDefinitionIdOperations()))
        .ifPresent(queries::addAll);
    ofNullable(stringOperations("processName", filter.processDefinitionNameOperations()))
        .ifPresent(queries::addAll);
    ofNullable(intOperations("processVersion", filter.processDefinitionVersionOperations()))
        .ifPresent(queries::addAll);
    ofNullable(
            stringOperations("processVersionTag", filter.processDefinitionVersionTagOperations()))
        .ifPresent(queries::addAll);
    ofNullable(longOperations("processDefinitionKey", filter.processDefinitionKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(
            longOperations("parentProcessInstanceKey", filter.parentProcessInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(
            longOperations(
                "parentFlowNodeInstanceKey", filter.parentFlowNodeInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(stringOperations("treePath", filter.treePathOperations()))
        .ifPresent(queries::addAll);
    ofNullable(dateTimeOperations("startDate", filter.startDateOperations()))
        .ifPresent(queries::addAll);
    ofNullable(dateTimeOperations("endDate", filter.endDateOperations()))
        .ifPresent(queries::addAll);
    ofNullable(stringOperations("state", filter.stateOperations())).ifPresent(queries::addAll);
    ofNullable(getIncidentQuery(filter.hasIncident())).ifPresent(queries::add);
    ofNullable(stringOperations("tenantId", filter.tenantIdOperations()))
        .ifPresent(queries::addAll);
    return and(queries);
  }

  @Override
  public List<String> toIndices(final ProcessInstanceFilter filter) {
    return List.of("operate-list-view-8.3.0_alias");
  }

  private SearchQuery getIsProcessInstanceQuery() {
    return term("joinRelation", "processInstance");
  }

  private SearchQuery getIncidentQuery(final Boolean hasIncident) {
    if (hasIncident != null) {
      return term("incident", hasIncident);
    }
    return null;
  }
}
