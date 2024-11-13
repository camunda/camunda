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
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.filter.Operation;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

public final class DecisionInstanceFilterTransformer
    implements FilterTransformer<DecisionInstanceFilter> {

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

  @Override
  public List<String> toIndices(final DecisionInstanceFilter filter) {
    return List.of("operate-decision-instance-8.3.0_alias");
  }

  private SearchQuery getKeysQuery(final List<Long> keys) {
    return longTerms("key", keys);
  }

  private SearchQuery getIdsQuery(final List<String> ids) {
    return stringTerms("id", ids);
  }

  private SearchQuery getStatesQuery(final List<DecisionInstanceState> states) {
    return stringTerms("state", states != null ? states.stream().map(Enum::name).toList() : null);
  }

  private List<SearchQuery> getEvaluationDateQuery(
      final List<Operation<OffsetDateTime>> evaluationDateOperations) {
    return dateTimeOperations("evaluationDate", evaluationDateOperations);
  }

  private SearchQuery getEvaluationFailuresQuery(final List<String> evaluationFailures) {
    return stringTerms("evaluationFailure", evaluationFailures);
  }

  private SearchQuery getProcessDefinitionKeysQuery(final List<Long> processDefinitionKeys) {
    return longTerms("processDefinitionKey", processDefinitionKeys);
  }

  private SearchQuery getProcessInstanceKeysQuery(final List<Long> processInstanceKeys) {
    return longTerms("processInstanceKey", processInstanceKeys);
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
    return stringOperations("decisionDefinitionId", stringOperations);
  }

  private SearchQuery getDecisionDefinitionIdsQuery(final List<String> decisionDefinitionIds) {
    return stringTerms("decisionId", decisionDefinitionIds);
  }

  private SearchQuery getDecisionDefinitionNamesQuery(final List<String> decisionDefinitionNames) {
    return stringTerms("decisionName", decisionDefinitionNames);
  }

  private SearchQuery getDecisionDefinitionVersionsQuery(
      final List<Integer> decisionDefinitionVersions) {
    return intTerms("decisionVersion", decisionDefinitionVersions);
  }

  private SearchQuery getDecisionDefinitionTypesQuery(
      final List<DecisionDefinitionType> decisionTypes) {
    return stringTerms(
        "decisionType",
        decisionTypes != null ? decisionTypes.stream().map(Enum::name).toList() : null);
  }

  private SearchQuery getTenantIdsQuery(final List<String> tenantIds) {
    return stringTerms("tenantId", tenantIds);
  }
}
