/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.transformers.filter;

import static io.camunda.search.clients.query.SearchQueryBuilders.and;
import static io.camunda.search.clients.query.SearchQueryBuilders.longTerms;
import static io.camunda.search.clients.query.SearchQueryBuilders.or;

import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.query.SearchQueryBuilders;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.service.search.filter.VariableFilter;
import io.camunda.service.search.filter.VariableValueFilter;
import io.camunda.search.transformers.ServiceTransformers;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class VariableFilterTransformer implements FilterTransformer<VariableFilter> {
  private final ServiceTransformers transformers;
  private final VariableValueFilterTransformer variableValueFilterTransformer;

  public VariableFilterTransformer(
      final ServiceTransformers transformers,
      final VariableValueFilterTransformer variableValueFilterTransformer) {
    this.transformers = transformers;
    this.variableValueFilterTransformer = variableValueFilterTransformer;
  }

  @Override
  public SearchQuery toSearchQuery(final VariableFilter filter) {
    final var variablesQuery = getVariablesQuery(filter.variableFilters(), filter.orConditions());
    final var scopeKeyQuery = getScopeKeyQuery(filter.scopeKeys());
    final var processInstanceKeyQuery = getProcessInstanceKeyQuery(filter.processInstanceKeys());

    return and(variablesQuery, scopeKeyQuery, processInstanceKeyQuery);
  }

  @Override
  public List<String> toIndices(final VariableFilter filter) {
    final boolean onlyRuntimeVariables = filter.onlyRuntimeVariables();
    if (onlyRuntimeVariables) {
      return Arrays.asList("operate-variable-8.3.0_");
    }
    return Arrays.asList("operate-variable-8.3.0_alias");
  }

  private SearchQuery getVariablesQuery(
      final List<VariableValueFilter> variableFilters, final boolean orConditions) {
    if (variableFilters != null && !variableFilters.isEmpty()) {
      final var queries =
          variableFilters.stream()
              .map(v -> variableValueFilterTransformer.toSearchQuery(v, "name", "value"))
              .collect(Collectors.toList());
      return orConditions ? or(queries) : and(queries);
    }
    return null;
  }

  private SearchQuery of(final Object value) {
    final var typedValue = TypedValue.toTypedValue(value);
    return SearchQueryBuilders.term().field("value").value(typedValue).build().toSearchQuery();
  }

  private SearchQuery getScopeKeyQuery(final List<Long> scopeKey) {
    return longTerms("scopeKey", scopeKey);
  }

  private SearchQuery getProcessInstanceKeyQuery(final List<Long> processInstanceKey) {
    return longTerms("processInstanceKey", processInstanceKey);
  }
}
