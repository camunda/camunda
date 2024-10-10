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

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.filter.DateValueFilterTransformer.DateFieldFilter;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.filter.DateValueFilter;
import io.camunda.search.filter.DecisionInstanceFilter;
import java.util.List;

public final class DecisionInstanceFilterTransformer
    implements FilterTransformer<DecisionInstanceFilter> {

  private final ServiceTransformers transformers;

  public DecisionInstanceFilterTransformer(final ServiceTransformers transformers) {
    this.transformers = transformers;
  }

  @Override
  public SearchQuery toSearchQuery(final DecisionInstanceFilter filter) {
    final var keysQuery = getKeysQuery(filter.decisionInstanceKeys());
    final var statesQuery = getStatesQuery(filter.states());
    final var evaluationDateQuery = getEvaluationDateQuery(filter.evaluationDate());
    final var evaluationFailuresQuery = getEvaluationFailuresQuery(filter.evaluationFailures());
    final var processDefinitionKeysQuery =
        getProcessDefinitionKeysQuery(filter.processDefinitionKeys());
    final var processInstanceKeysQuery = getProcessInstanceKeysQuery(filter.processInstanceKeys());
    final var decisionKeysQuery = getDecisionDefinitionKeysQuery(filter.decisionDefinitionKeys());
    final var dmnDecisionIdsQuery = getDecisionDefinitionIdsQuery(filter.decisionDefinitionIds());
    final var dmnDecisionNamesQuery =
        getDecisionDefinitionNamesQuery(filter.decisionDefinitionNames());
    final var decisionVersionsQuery =
        getDecisionDefinitionVersionsQuery(filter.decisionDefinitionVersions());
    final var decisionTypesQuery = getDecisionDefinitionTypesQuery(filter.decisionTypes());
    final var tenantIdsQuery = getTenantIdsQuery(filter.tenantIds());

    return and(
        keysQuery,
        statesQuery,
        evaluationDateQuery,
        evaluationFailuresQuery,
        processDefinitionKeysQuery,
        processInstanceKeysQuery,
        decisionKeysQuery,
        dmnDecisionIdsQuery,
        dmnDecisionNamesQuery,
        decisionVersionsQuery,
        decisionTypesQuery,
        tenantIdsQuery);
  }

  @Override
  public List<String> toIndices(final DecisionInstanceFilter filter) {
    return List.of("operate-decision-instance-8.3.0_alias");
  }

  private SearchQuery getKeysQuery(final List<Long> keys) {
    return longTerms("key", keys);
  }

  private SearchQuery getStatesQuery(final List<DecisionInstanceState> states) {
    return stringTerms("state", states != null ? states.stream().map(Enum::name).toList() : null);
  }

  private SearchQuery getEvaluationDateQuery(final DateValueFilter filter) {
    if (filter != null) {
      final var transformer = transformers.getFilterTransformer(DateValueFilter.class);
      return transformer.apply(new DateFieldFilter("evaluationDate", filter));
    }
    return null;
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

  private SearchQuery getDecisionDefinitionKeysQuery(final List<Long> decisionDefinitionKeys) {
    return stringTerms(
        "decisionDefinitionId",
        decisionDefinitionKeys != null
            ? decisionDefinitionKeys.stream().map(String::valueOf).toList()
            : null);
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
