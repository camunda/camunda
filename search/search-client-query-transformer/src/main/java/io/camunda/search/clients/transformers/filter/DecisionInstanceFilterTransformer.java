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
import static io.camunda.search.clients.query.SearchQueryBuilders.stringTerms;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.DECISION_DEFINITION_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.DECISION_ID;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.DECISION_NAME;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.DECISION_TYPE;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.DECISION_VERSION;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.EVALUATION_DATE;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.EVALUATION_FAILURE;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.ID;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.PROCESS_DEFINITION_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.PROCESS_INSTANCE_KEY;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.STATE;
import static io.camunda.webapps.schema.descriptors.operate.template.DecisionInstanceTemplate.TENANT_ID;
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
    ofNullable(getKeysQuery(filter.decisionInstanceKeyOperations())).ifPresent(queries::addAll);
    ofNullable(getIdsQuery(filter.decisionInstanceIdOperations())).ifPresent(queries::addAll);
    ofNullable(getStatesQuery(filter.states())).ifPresent(queries::add);
    ofNullable(getEvaluationDateQuery(filter.evaluationDateOperations()))
        .ifPresent(queries::addAll);
    ofNullable(getEvaluationFailuresQuery(filter.evaluationFailureOperations()))
        .ifPresent(queries::addAll);
    ofNullable(getProcessDefinitionKeysQuery(filter.processDefinitionKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(getProcessInstanceKeysQuery(filter.processInstanceKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(getDecisionDefinitionKeysQuery(filter.decisionDefinitionKeyOperations()))
        .ifPresent(queries::addAll);
    ofNullable(getDecisionDefinitionIdsQuery(filter.decisionDefinitionIdOperations()))
        .ifPresent(queries::addAll);
    ofNullable(getDecisionDefinitionNamesQuery(filter.decisionDefinitionNameOperations()))
        .ifPresent(queries::addAll);
    ofNullable(getDecisionDefinitionVersionsQuery(filter.decisionDefinitionVersionOperations()))
        .ifPresent(queries::addAll);
    ofNullable(getDecisionDefinitionTypesQuery(filter.decisionTypes())).ifPresent(queries::add);
    ofNullable(getTenantIdsQuery(filter.tenantIdOperations())).ifPresent(queries::addAll);
    return and(queries);
  }

  private List<SearchQuery> getKeysQuery(final List<Operation<Long>> keys) {
    return longOperations(KEY, keys);
  }

  private List<SearchQuery> getIdsQuery(final List<Operation<String>> ids) {
    return stringOperations(ID, ids);
  }

  private SearchQuery getStatesQuery(final List<DecisionInstanceState> states) {
    return stringTerms(STATE, states != null ? states.stream().map(Enum::name).toList() : null);
  }

  private List<SearchQuery> getEvaluationDateQuery(
      final List<Operation<OffsetDateTime>> evaluationDateOperations) {
    return dateTimeOperations(EVALUATION_DATE, evaluationDateOperations);
  }

  private List<SearchQuery> getEvaluationFailuresQuery(
      final List<Operation<String>> evaluationFailures) {
    return stringOperations(EVALUATION_FAILURE, evaluationFailures);
  }

  private List<SearchQuery> getProcessDefinitionKeysQuery(
      final List<Operation<Long>> processDefinitionKeys) {
    return longOperations(PROCESS_DEFINITION_KEY, processDefinitionKeys);
  }

  private List<SearchQuery> getProcessInstanceKeysQuery(
      final List<Operation<Long>> processInstanceKeys) {
    return longOperations(PROCESS_INSTANCE_KEY, processInstanceKeys);
  }

  private List<SearchQuery> getDecisionDefinitionKeysQuery(
      final List<Operation<Long>> decisionDefinitionKeyOperations) {
    return longOperations(DECISION_DEFINITION_ID, decisionDefinitionKeyOperations);
  }

  private List<SearchQuery> getDecisionDefinitionIdsQuery(
      final List<Operation<String>> decisionDefinitionIds) {
    return stringOperations(DECISION_ID, decisionDefinitionIds);
  }

  private List<SearchQuery> getDecisionDefinitionNamesQuery(
      final List<Operation<String>> decisionDefinitionNames) {
    return stringOperations(DECISION_NAME, decisionDefinitionNames);
  }

  private List<SearchQuery> getDecisionDefinitionVersionsQuery(
      final List<Operation<Integer>> decisionDefinitionVersions) {
    return intOperations(DECISION_VERSION, decisionDefinitionVersions);
  }

  private SearchQuery getDecisionDefinitionTypesQuery(
      final List<DecisionDefinitionType> decisionTypes) {
    return stringTerms(
        DECISION_TYPE,
        decisionTypes != null ? decisionTypes.stream().map(Enum::name).toList() : null);
  }

  private List<SearchQuery> getTenantIdsQuery(final List<Operation<String>> tenantIds) {
    return stringOperations(TENANT_ID, tenantIds);
  }
}
