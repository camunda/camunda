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
import static io.camunda.search.clients.query.SearchQueryBuilders.intTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringOperations;
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_NAME;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_TYPE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.DECISION_VERSION;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EVALUATION_DATE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.EVALUATION_FAILURE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.ID;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.STATE;
import static io.camunda.webapps.schema.descriptors.template.DecisionInstanceTemplate.TENANT_ID;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.Operation;
import io.camunda.webapps.schema.descriptors.IndexDescriptor;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public final class DecisionInstanceFilterTransformer
    extends IndexFilterTransformer<DecisionInstanceFilter> {

  public DecisionInstanceFilterTransformer(final IndexDescriptor indexDescriptor) {
    super(indexDescriptor);
  }

  @Override
  public SearchQuery toSearchQuery(final DecisionInstanceFilter filter) {
    final var queries = new ArrayList<SearchQuery>();
    ofNullable(getKeysQuery(filter.decisionInstanceKeys())).ifPresent(queries::add);
    ofNullable(getIdsQuery(filter.decisionInstanceIds())).ifPresent(queries::add);
    ofNullable(getStatesQuery(filter.states())).ifPresent(queries::add);
    ofNullable(getEvaluationDateQuery(filter.evaluationDateOperations()))
        .ifPresent(queries::addAll);
    ofNullable(getEvaluationFailuresQuery(filter.evaluationFailures())).ifPresent(queries::add);
    ofNullable(getProcessDefinitionKeysQuery(filter.processDefinitionKeys()))
        .ifPresent(queries::add);
    ofNullable(getProcessInstanceKeysQuery(filter.processInstanceKeys())).ifPresent(queries::add);
    ofNullable(getDecisionDefinitionKeysQuery(filter.decisionDefinitionKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(getDecisionDefinitionIdsQuery(filter.decisionDefinitionIds()))
        .ifPresent(queries::add);
    ofNullable(getDecisionDefinitionNamesQuery(filter.decisionDefinitionNames()))
        .ifPresent(queries::add);
    ofNullable(getDecisionDefinitionVersionsQuery(filter.decisionDefinitionVersions()))
        .ifPresent(queries::add);
    ofNullable(getDecisionDefinitionTypesQuery(filter.decisionTypes())).ifPresent(queries::add);
    ofNullable(getTenantIdsQuery(filter.tenantIds())).ifPresent(queries::add);
    return and(queries);
  }

  private SearchQuery getKeysQuery(final List<Long> keys) {
    return longTerms(KEY, keys);
  }

  private SearchQuery getIdsQuery(final List<String> ids) {
    return stringTerms(ID, ids);
  }

  private SearchQuery getStatesQuery(final List<DecisionInstanceState> states) {
    return stringTerms(STATE, states != null ? states.stream().map(Enum::name).toList() : null);
  }

  private List<SearchQuery> getEvaluationDateQuery(
      final List<Operation<OffsetDateTime>> evaluationDateOperations) {
    return dateTimeOperations(EVALUATION_DATE, evaluationDateOperations);
  }

  private SearchQuery getEvaluationFailuresQuery(final List<String> evaluationFailures) {
    return stringTerms(EVALUATION_FAILURE, evaluationFailures);
  }

  private SearchQuery getProcessDefinitionKeysQuery(final List<Long> processDefinitionKeys) {
    return longTerms(PROCESS_DEFINITION_KEY, processDefinitionKeys);
  }

  private SearchQuery getProcessInstanceKeysQuery(final List<Long> processInstanceKeys) {
    return longTerms(PROCESS_INSTANCE_KEY, processInstanceKeys);
  }

  private List<SearchQuery> getDecisionDefinitionKeysQuery(
      final List<Operation<Long>> decisionDefinitionKeyOperations) {
    final var stringOperations =
        decisionDefinitionKeyOperations.stream()
            .map(
                op -> {
                  final var values = op.values().stream().map(String::valueOf).toList();
                  return new Operation<>(op.operator(), values);
                })
            .toList();
    return stringOperations(DECISION_DEFINITION_ID, stringOperations);
  }

  private SearchQuery getDecisionDefinitionIdsQuery(final List<String> decisionDefinitionIds) {
    return stringTerms(DECISION_ID, decisionDefinitionIds);
  }

  private SearchQuery getDecisionDefinitionNamesQuery(final List<String> decisionDefinitionNames) {
    return stringTerms(DECISION_NAME, decisionDefinitionNames);
  }

  private SearchQuery getDecisionDefinitionVersionsQuery(
      final List<Integer> decisionDefinitionVersions) {
    return intTerms(DECISION_VERSION, decisionDefinitionVersions);
  }

  private SearchQuery getDecisionDefinitionTypesQuery(
      final List<DecisionDefinitionType> decisionTypes) {
    return stringTerms(
        DECISION_TYPE,
        decisionTypes != null ? decisionTypes.stream().map(Enum::name).toList() : null);
  }

  private SearchQuery getTenantIdsQuery(final List<String> tenantIds) {
    return stringTerms(TENANT_ID, tenantIds);
  }
}
