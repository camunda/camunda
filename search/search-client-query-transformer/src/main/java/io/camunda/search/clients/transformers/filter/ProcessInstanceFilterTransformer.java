/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.*;
import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
import static io.camunda.webapps.schema.descriptors.IndexTemplateDescriptor.PARTITION_ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITIES_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ACTIVITY_STATE;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.BATCH_OPERATION_IDS;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.BPMN_PROCESS_ID;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.END_DATE;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.ERROR_MSG;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.INCIDENT;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOB_FAILED_WITH_RETRIES_LEFT;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PARENT_FLOW_NODE_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PARENT_PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_INSTANCE_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_KEY;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_NAME;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_VERSION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.PROCESS_VERSION_TAG;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.START_DATE;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.STATE;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchMatchQuery.SearchMatchQueryOperator;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public final class ProcessInstanceFilterTransformer
    extends IndexFilterTransformer<ProcessInstanceFilter> {

  private final ServiceTransformers transformers;

  public ProcessInstanceFilterTransformer(
      final ServiceTransformers transformers, final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
    this.transformers = transformers;
  }

  public ArrayList<SearchQuery> toSearchQueryFields(final ProcessInstanceFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    ofNullable(longOperations(KEY, filter.processInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(stringOperations(BPMN_PROCESS_ID, filter.processDefinitionIdOperations()))
        .ifPresent(queries::addAll);
    ofNullable(stringOperations(PROCESS_NAME, filter.processDefinitionNameOperations()))
        .ifPresent(queries::addAll);
    ofNullable(intOperations(PROCESS_VERSION, filter.processDefinitionVersionOperations()))
        .ifPresent(queries::addAll);
    ofNullable(
            stringOperations(PROCESS_VERSION_TAG, filter.processDefinitionVersionTagOperations()))
        .ifPresent(queries::addAll);
    ofNullable(longOperations(PROCESS_KEY, filter.processDefinitionKeyOperations()))
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

    if (filter.errorMessageOperations() != null && !filter.errorMessageOperations().isEmpty()) {
      ofNullable(
              stringMatchWithHasChildOperations(
                  ERROR_MSG,
                  filter.errorMessageOperations(),
                  ACTIVITIES_JOIN_RELATION,
                  SearchMatchQueryOperator.AND))
          .ifPresent(queries::addAll);
    }

    ofNullable(stringOperations(BATCH_OPERATION_IDS, filter.batchOperationIdOperations()))
        .ifPresent(queries::addAll);

    ofNullable(getHasRetriesLeftQuery(filter.hasRetriesLeft())).ifPresent(queries::add);

    if (filter.flowNodeIdOperations() != null && !filter.flowNodeIdOperations().isEmpty()) {
      queries.add(getFlowNodeInstanceQuery(filter));
    }

    if (filter.partitionId() != null) {
      queries.add(term(PARTITION_ID, filter.partitionId()));
    }

    return queries;
  }

  @Override
  public SearchQuery toSearchQuery(final ProcessInstanceFilter filter) {

    final var queries = new ArrayList<SearchQuery>();
    ofNullable(getIsProcessInstanceQuery()).ifPresent(queries::add);
    queries.addAll(toSearchQueryFields(filter));

    if (filter.orFilters() != null && !filter.orFilters().isEmpty()) {
      final var orQueries = new ArrayList<SearchQuery>();
      filter.orFilters().stream().map(f -> and(toSearchQueryFields(f))).forEach(orQueries::add);
      queries.add(or(orQueries));
    }

    return and(queries);
  }

  private static SearchQuery getFlowNodeInstanceQuery(final ProcessInstanceFilter filter) {
    final var flowNodeInstanceQueries = new ArrayList<SearchQuery>();

    ofNullable(stringOperations(ACTIVITY_ID, filter.flowNodeIdOperations()))
        .ifPresent(flowNodeInstanceQueries::addAll);
    ofNullable(stringOperations(ACTIVITY_STATE, filter.flowNodeInstanceStateOperations()))
        .ifPresent(flowNodeInstanceQueries::addAll);
    ofNullable(filter.hasFlowNodeInstanceIncident())
        .ifPresent(incident -> flowNodeInstanceQueries.add((term(INCIDENT, incident))));

    return hasChildQuery(ACTIVITIES_JOIN_RELATION, and(flowNodeInstanceQueries));
  }

  private SearchQuery getHasRetriesLeftQuery(final Boolean hasRetriesLeft) {
    if (hasRetriesLeft != null) {
      return hasChildQuery(
          ACTIVITIES_JOIN_RELATION, term(JOB_FAILED_WITH_RETRIES_LEFT, hasRetriesLeft));
    }
    return null;
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
