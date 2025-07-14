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
import static io.camunda.search.clients.query.SearchQueryBuilders.hasParentQuery;
import static io.camunda.search.clients.query.SearchQueryBuilders.longOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringMatchPhraseWithHasChildOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.term;
import static io.camunda.webapps.schema.descriptors.IndexDescriptor.TENANT_ID;
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
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.START_DATE;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.STATE;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.VARIABLES_JOIN_RELATION;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.VAR_NAME;
import static io.camunda.webapps.schema.descriptors.template.ListViewTemplate.VAR_VALUE;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.filter.ProcessDefinitionStatisticsFilter;
import io.camunda.search.filter.VariableValueFilter;
import io.camunda.security.auth.Authorization;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    queries.add(term(JOIN_RELATION, ACTIVITIES_JOIN_RELATION));
    queries.add(
        hasParentQuery(
            PROCESS_INSTANCE_JOIN_RELATION, term(PROCESS_KEY, filter.processDefinitionKey())));
    queries.addAll(toSearchQueryFields(filter));

    if (filter.orFilters() != null && !filter.orFilters().isEmpty()) {
      final var orQueries = new ArrayList<SearchQuery>();
      filter.orFilters().stream().map(f -> and(toSearchQueryFields(f))).forEach(orQueries::add);
      queries.add(or(orQueries));
    }

    return and(queries);
  }

  public ArrayList<SearchQuery> toSearchQueryFields(
      final ProcessDefinitionStatisticsFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    toProcessInstanceQueries(filter)
        .ifPresent(
            query -> queries.add(hasParentQuery(PROCESS_INSTANCE_JOIN_RELATION, and(query))));
    toFlowNodeInstanceQueries(filter).ifPresent(queries::addAll);
    return queries;
  }

  private Optional<ArrayList<SearchQuery>> toFlowNodeInstanceQueries(
      final ProcessDefinitionStatisticsFilter filter) {
    final var queries = new ArrayList<SearchQuery>();

    Optional.of(stringOperations(ACTIVITY_ID, filter.flowNodeIdOperations()))
        .ifPresent(queries::addAll);
    Optional.of(stringOperations(ACTIVITY_STATE, filter.flowNodeInstanceStateOperations()))
        .ifPresent(queries::addAll);
    ofNullable(filter.hasFlowNodeInstanceIncident())
        .ifPresent(value -> queries.add(term(INCIDENT, value)));

    return ofNullable(queries.isEmpty() ? null : queries);
  }

  private Optional<ArrayList<SearchQuery>> toProcessInstanceQueries(
      final ProcessDefinitionStatisticsFilter filter) {
    final var queries = new ArrayList<SearchQuery>();

    Optional.of(longOperations(KEY, filter.processInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    Optional.of(
            longOperations(
                PARENT_PROCESS_INSTANCE_KEY, filter.parentProcessInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    Optional.of(
            longOperations(
                PARENT_FLOW_NODE_INSTANCE_KEY, filter.parentFlowNodeInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    Optional.of(dateTimeOperations(START_DATE, filter.startDateOperations()))
        .ifPresent(queries::addAll);
    Optional.of(dateTimeOperations(END_DATE, filter.endDateOperations()))
        .ifPresent(queries::addAll);
    Optional.of(stringOperations(STATE, filter.stateOperations())).ifPresent(queries::addAll);
    ofNullable(filter.hasIncident()).ifPresent(value -> queries.add(term(INCIDENT, value)));
    Optional.of(stringOperations(TENANT_ID, filter.tenantIdOperations()))
        .ifPresent(queries::addAll);
    Optional.of(
            stringMatchPhraseWithHasChildOperations(
                ERROR_MSG, filter.errorMessageOperations(), ACTIVITIES_JOIN_RELATION))
        .ifPresent(queries::addAll);
    ofNullable(getProcessVariablesQuery(filter.variableFilters())).ifPresent(queries::add);
    queries.addAll(stringOperations(BATCH_OPERATION_IDS, filter.batchOperationIdOperations()));
    ofNullable(filter.hasRetriesLeft()).ifPresent(value -> queries.add(hasRetriesLeftQuery(value)));

    return ofNullable(queries.isEmpty() ? null : queries);
  }

  @Override
  protected SearchQuery toAuthorizationCheckSearchQuery(final Authorization authorization) {
    return stringTerms(BPMN_PROCESS_ID, authorization.resourceIds());
  }

  private static SearchQuery hasRetriesLeftQuery(final Boolean value) {
    return hasChildQuery(ACTIVITIES_JOIN_RELATION, term(JOB_FAILED_WITH_RETRIES_LEFT, value));
  }

  private SearchQuery getProcessVariablesQuery(final List<VariableValueFilter> variableFilters) {
    if (variableFilters != null && !variableFilters.isEmpty()) {
      final var transformer = getVariableValueFilterTransformer();
      final VariableValueFilterTransformer variableTransformer =
          (VariableValueFilterTransformer) transformer;
      final var queries =
          variableFilters.stream()
              .map(v -> variableTransformer.toSearchQuery(v, VAR_NAME, VAR_VALUE))
              .map((q) -> hasChildQuery(VARIABLES_JOIN_RELATION, q))
              .collect(Collectors.toList());
      return and(queries);
    }
    return null;
  }

  private FilterTransformer<VariableValueFilter> getVariableValueFilterTransformer() {
    return transformers.getFilterTransformer(VariableValueFilter.class);
  }
}
