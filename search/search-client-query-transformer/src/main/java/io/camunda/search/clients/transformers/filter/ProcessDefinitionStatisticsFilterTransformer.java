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
import static io.camunda.search.clients.query.SearchQueryBuilders.hasChildQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.longOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.END_DATE;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.INCIDENT;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.PARENT_FLOW_NODE_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.PROCESS_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.START_DATE;
import static io.camunda.webapps.schema.descriptors.operate.template.ListViewTemplate.STATE;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ProcessDefinitionStatisticsFilterTransformer
    extends IndexFilterTransformer<ProcessDefinitionStatisticsFilter> {

  private final ServiceTransformers transformers;

  public ProcessDefinitionStatisticsFilterTransformer(
      final ServiceTransformers transformers, final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
    this.transformers = transformers;
  }

  @Override
  public SearchQuery toSearchQuery(final ProcessDefinitionStatisticsFilter filter) {

    final var queries = new ArrayList<SearchQuery>();
    queries.add(term(PROCESS_KEY, filter.processDefinitionKey()));
    ofNullable(getIsProcessInstanceQuery()).ifPresent(queries::add);
    ofNullable(longOperations(KEY, filter.processInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(
            longOperations(
                PARENT_PROCESS_INSTANCE_KEY, filter.parentProcessInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(
            longOperations(
                PARENT_FLOW_NODE_INSTANCE_KEY, filter.parentFlowNodeInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(dateTimeOperations(START_DATE, filter.startDateOperations()))
        .ifPresent(queries::addAll);
    ofNullable(dateTimeOperations(END_DATE, filter.endDateOperations())).ifPresent(queries::addAll);
    ofNullable(stringOperations(STATE, filter.stateOperations())).ifPresent(queries::addAll);
    ofNullable(getIncidentQuery(filter.hasIncident())).ifPresent(queries::add);
    ofNullable(stringOperations(TENANT_ID, filter.tenantIdOperations())).ifPresent(queries::addAll);

    if (filter.variableFilters() != null && !filter.variableFilters().isEmpty()) {
      final var processVariableQuery = getProcessVariablesQuery(filter.variableFilters());
      queries.add(processVariableQuery);
    }

    return and(queries);
  }

  private SearchQuery getIsProcessInstanceQuery() {
    return term(JOIN_RELATION, PROCESS_INSTANCE_JOIN_RELATION);
  }

  private SearchQuery getIncidentQuery(final Boolean hasIncident) {
    if (hasIncident != null) {
      return term(INCIDENT, hasIncident);
    }
    return null;
  }

  private SearchQuery getProcessVariablesQuery(final List<VariableValueFilter> variableFilters) {
    if (variableFilters != null && !variableFilters.isEmpty()) {
      final var transformer = getVariableValueFilterTransformer();
      final VariableValueFilterTransformer variableTransformer =
          (VariableValueFilterTransformer) transformer;
      final var queries =
          variableFilters.stream()
              .map(v -> variableTransformer.toSearchQuery(v, "varName", "varValue"))
              .map((q) -> hasChildQuery("variable", q))
              .collect(Collectors.toList());
      return and(queries);
    }
    return null;
  }

  private FilterTransformer<VariableValueFilter> getVariableValueFilterTransformer() {
    return transformers.getFilterTransformer(VariableValueFilter.class);
  }
}
